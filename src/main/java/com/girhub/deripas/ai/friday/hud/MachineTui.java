package com.girhub.deripas.ai.friday.hud;

import com.girhub.deripas.ai.friday.hud.tui.PromptView;
import com.girhub.deripas.ai.friday.hud.tui.TextPane;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.shell.jline.tui.component.message.ShellMessageBuilder;
import org.springframework.shell.jline.tui.component.view.TerminalUI;
import org.springframework.shell.jline.tui.component.view.control.BoxView;
import org.springframework.shell.jline.tui.component.view.control.GridView;
import org.springframework.shell.jline.tui.component.view.control.View;
import org.springframework.shell.jline.tui.component.view.event.EventLoop;
import org.springframework.shell.jline.tui.component.view.event.KeyEvent;
import org.springframework.shell.jline.tui.component.view.event.KeyEvent.Key;
import org.springframework.shell.jline.tui.component.view.event.KeyEvent.KeyMask;
import org.springframework.shell.jline.tui.component.view.event.MouseEvent;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Полноэкранный красный интерфейс: журнал, боковые панели, поле ввода и строка состояния.
 *
 * <pre>
 * ┌ ЖУРНАЛ ───────────────────┐┌ КОНТЕКСТ ХОДА ──┐
 * │ boot-лог, приказы, ответы ││ что ушло в      │
 * │                           ││ нейропроцессор  │
 * └───────────────────────────┘└─────────────────┘
 * ┌ ПРИКАЗ ─────────────────────────────────────┐
 * │ > _                                         │
 * └─────────────────────────────────────────────┘
 *  ● ГОТОВ · цикл 840 мс · токенов ~57    Enter — приказ · …
 * </pre>
 *
 * <p>Как и {@link MachineViewPrinter}, решений о содержании не принимает. Вид boot-лога и
 * приказа берёт у него же: печатник пишет не в stdout, а в буфер, и готовые ANSI-строки
 * уходят в журнал. Контекст вместо печати в журнал живёт в боковой панели и заменяется
 * на каждом ходу.
 *
 * <p>Новый элемент интерфейса — это ещё одна {@link TextPane} в {@link #sidebar}: панели
 * делят колонку поровну. В узком окне боковая колонка прячется целиком.
 *
 * <p>Потоки: интерфейс крутится в потоке, вызвавшем {@link #run}; приказы исполняются по
 * одному в отдельном потоке, чтобы ввод и прокрутка не замирали, пока модель думает.
 * {@code show*} приходят из потока агента и только дописывают строки и просят перерисовать.
 */
@Component
@ConditionalOnProperty(name = MachineView.PROPERTY, havingValue = "tui")
class MachineTui implements MachineView {

    /**
     * Уже этой ширины окна боковые панели не показываются: журналу не осталось бы места.
     */
    private static final int SIDEBAR_MIN_WIDTH = 110;

    private static final int SCROLL_STEP = 3;

    private static final AttributedStyle RED = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
    private static final AttributedStyle BRIGHT_RED = AttributedStyle.BOLD.foreground(AttributedStyle.RED + AttributedStyle.BRIGHT);
    private static final AttributedStyle DIM = RED.faint();

    private static final String KEYS = "Enter — приказ · ↑↓ и колесо — журнал · Ctrl+Q — выход";

    private final ByteArrayOutputStream painted = new ByteArrayOutputStream();
    private final MachineViewPrinter painter = new MachineViewPrinter(new PrintStream(painted, true, StandardCharsets.UTF_8));

    private final TextPane log = new TextPane(" ЖУРНАЛ ");
    private final TextPane context = new TextPane(" КОНТЕКСТ ХОДА ");
    private final List<TextPane> sidebar = List.of(context);
    private final BoxView status = new BoxView();

    private final ExecutorService orders = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("friday-orders").factory());
    private final AtomicInteger pending = new AtomicInteger();

    private volatile String lastTurn = "";
    private volatile @Nullable TerminalUI ui;

    @Override
    public void showBootLog(List<BootLine> lines) {
        log.append(paint(printer -> printer.showBootLog(lines)));
        refresh();
    }

    @Override
    public void showOrder(String userInput) {
        log.append(paint(printer -> printer.showOrder(userInput)));
        refresh();
    }

    @Override
    public void showContext(List<Message> prompt, int tokens) {
        final List<AttributedString> lines = new ArrayList<>();
        lines.add(new AttributedString("сообщений: %d · токенов: ~%d".formatted(prompt.size(), tokens), DIM));

        int number = 0;
        for (Message message : prompt) {
            final String role = MachineViewPrinter.role(message.getMessageType());
            final String head = message.getMessageType() == MessageType.SYSTEM
                ? role
                : "[%d] %s".formatted(++number, role);
            lines.add(AttributedString.EMPTY);
            lines.add(new AttributedString(head, RED));
            lines.addAll(plain(message.getText()));
        }

        context.replace(lines);
        lastTurn = "токенов ~" + tokens;
        refresh();
    }

    /**
     * В журнал не пишется: длительность цикла — показание прибора, ей место в строке состояния.
     */
    @Override
    public void showTurnFinished(Duration elapsed) {
        lastTurn = "цикл %d мс · %s".formatted(elapsed.toMillis(), lastTurn);
        refresh();
    }

    /**
     * Показывает интерфейс и возвращается, когда из него вышли (Ctrl+Q или Ctrl+C).
     *
     * @param brain исполнитель приказа: получает набранный текст, возвращает ответ для журнала
     */
    void run(Function<String, String> brain) {
        try (Terminal terminal = openTerminal()) {
            final TerminalUI ui = new TerminalUI(terminal);
            final PromptView prompt = new PromptView(" ПРИКАЗ ", order -> submit(brain, order));
            status.setDrawFunction((screen, rect) -> {
                screen.writerBuilder().build().text(statusLine(), rect.x() + 1, rect.y());
                return rect;
            });

            final GridView side = new GridView();
            final GridView root = layout(side, prompt);
            final List<View> views = new ArrayList<>(List.of(root, side, log, prompt, status));
            views.addAll(sidebar);
            views.forEach(ui::configure);
            ui.setRoot(root, true);

            final EventLoop loop = ui.getEventLoop();
            loop.onDestroy(loop.keyEvents().subscribe(event -> onKey(loop, prompt, event)));
            loop.onDestroy(loop.mouseEvents().subscribe(this::onMouse));

            this.ui = ui;
            try {
                ui.run();
            } finally {
                this.ui = null;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Terminal openTerminal() throws IOException {
        try {
            return TerminalBuilder.builder().system(true).dumb(false).build();
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                MachineView.PROPERTY + "=tui needs a real terminal; in an IDE console or a pipe use " + MachineView.PROPERTY + "=printer", e);
        }
    }

    /**
     * Сетка: журнал и боковая колонка, под ними поле ввода и строка состояния на всю ширину.
     *
     * <p>Журнал и колонка добавлены дважды: {@link GridView} берёт размещение с наибольшим
     * подходящим порогом ширины, так что в узком окне журнал занимает обе колонки,
     * а боковая (нулевой размах) не рисуется.
     */
    private GridView layout(GridView side, PromptView prompt) {
        for (int i = 0; i < sidebar.size(); i++) {
            side.addItem(sidebar.get(i), i, 0, 1, 1, 0, 0);
        }

        final GridView root = new GridView();
        root.setRowSize(0, 3, 1);
        root.setColumnSize(-3, -2);
        root.addItem(log, 0, 0, 1, 2, 0, 0);
        root.addItem(side, 0, 1, 0, 0, 0, 0);
        root.addItem(log, 0, 0, 1, 1, 0, SIDEBAR_MIN_WIDTH);
        root.addItem(side, 0, 1, 1, 1, 0, SIDEBAR_MIN_WIDTH);
        root.addItem(prompt, 1, 0, 1, 2, 0, 0);
        root.addItem(status, 2, 0, 1, 2, 0, 0);
        return root;
    }

    /**
     * Перерисовывать после клавиши не нужно: цикл интерфейса делает это сам после каждого события.
     */
    private void onKey(EventLoop loop, PromptView prompt, KeyEvent event) {
        switch (event.key()) {
            case Key.q | KeyMask.CtrlMask -> loop.dispatch(ShellMessageBuilder.ofInterrupt());
            case Key.CursorUp -> log.scroll(SCROLL_STEP);
            case Key.CursorDown -> log.scroll(-SCROLL_STEP);
            default -> prompt.type(event);
        }
    }

    private void onMouse(MouseEvent event) {
        if (event.has(MouseEvent.Button.WheelUp)) {
            log.scroll(SCROLL_STEP);
        } else if (event.has(MouseEvent.Button.WheelDown)) {
            log.scroll(-SCROLL_STEP);
        }
    }

    private void submit(Function<String, String> brain, String order) {
        pending.incrementAndGet();
        orders.execute(() -> obey(brain, order));
    }

    private void obey(Function<String, String> brain, String order) {
        try {
            final String reply = brain.apply(order);
            final List<AttributedString> lines = new ArrayList<>();
            lines.add(new AttributedString(MachineViewPrinter.role(MessageType.ASSISTANT) + ":", BRIGHT_RED));
            lines.addAll(plain(reply));
            lines.add(AttributedString.EMPTY);
            log.append(lines);
        } catch (RuntimeException e) {
            log.append(List.of(new AttributedString("> СБОЙ: " + oneLine(e), BRIGHT_RED), AttributedString.EMPTY));
        } finally {
            pending.decrementAndGet();
            refresh();
        }
    }

    private AttributedString statusLine() {
        final int waiting = pending.get();
        final AttributedStringBuilder line = new AttributedStringBuilder();
        if (waiting == 0) {
            line.append("● ГОТОВ", RED);
        } else {
            line.append("◌ НЕЙРОПРОЦЕССОР ЗАНЯТ", BRIGHT_RED);
        }
        if (waiting > 1) {
            line.append(" · в очереди: " + (waiting - 1), DIM);
        }
        if (!lastTurn.isEmpty()) {
            line.append(" · " + lastTurn, DIM);
        }
        line.append("    " + KEYS, DIM);
        return line.toAttributedString();
    }

    /**
     * Прогоняет печатника и забирает напечатанное строками. Блокировка — потому что буфер один,
     * а boot-лог и приказ приходят из разных потоков.
     */
    private List<AttributedString> paint(Consumer<MachineViewPrinter> block) {
        synchronized (painted) {
            block.accept(painter);
            final String ansi = painted.toString(StandardCharsets.UTF_8);
            painted.reset();
            return ansi.lines().map(line -> AttributedString.fromAnsi(printable(line))).toList();
        }
    }

    private void refresh() {
        final TerminalUI current = ui;
        if (current != null) {
            current.redraw();
        }
    }

    private static List<AttributedString> plain(@Nullable String text) {
        return text == null
            ? List.of()
            : text.lines().map(line -> new AttributedString(printable(line))).toList();
    }

    /**
     * Экран Spring Shell кладёт один {@code char} в одну ячейку. Табуляция и символы вне BMP
     * (эмодзи) сдвинули бы всё правее себя, поэтому заменяются на то, что занимает ровно ячейку.
     */
    private static String printable(String line) {
        final StringBuilder out = new StringBuilder(line.length());
        line.codePoints().forEach(codePoint -> {
            if (codePoint == '\t') {
                out.append("    ");
            } else if (Character.isSupplementaryCodePoint(codePoint)) {
                out.append('◆');
            } else {
                out.append((char) codePoint);
            }
        });
        return out.toString();
    }

    private static String oneLine(RuntimeException e) {
        final String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.lines().findFirst().orElse(e.getClass().getSimpleName());
    }
}
