package com.girhub.deripas.ai.friday.hud.tui;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jspecify.annotations.Nullable;
import org.springframework.shell.jline.tui.component.view.control.BoxView;
import org.springframework.shell.jline.tui.component.view.event.KeyEvent;
import org.springframework.shell.jline.tui.component.view.event.KeyEvent.Key;
import org.springframework.shell.jline.tui.component.view.event.KeyEvent.KeyMask;
import org.springframework.shell.jline.tui.component.view.screen.Screen;
import org.springframework.shell.jline.tui.geom.Position;
import org.springframework.shell.jline.tui.geom.Rectangle;

import java.util.function.Consumer;

/**
 * Однострочное поле ввода: набор, правка, отправка по Enter.
 *
 * <p>Штатный {@code InputView} из Spring Shell для этого не годится: он теряет пробелы,
 * его нельзя очистить после отправки и он не прокручивает строку длиннее поля.
 *
 * <p>Клавиши поле получает напрямую через {@link #type(KeyEvent)}, а не через фокус:
 * щелчок мышью по соседней панели увёл бы фокус, и набор молча перестал бы работать.
 */
public class PromptView extends BoxView {

    private static final AttributedString MARKER =
        new AttributedString("> ", AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));

    private final StringBuilder text = new StringBuilder();
    private final Consumer<String> onSubmit;

    private int cursor;

    /**
     * Первый видимый символ, когда текст длиннее поля.
     */
    private int offset;

    public PromptView(String title, Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
        setShowBorder(true);
        setTitle(title);
    }

    public void type(KeyEvent event) {
        final String submitted = edit(event);
        if (submitted != null) {
            // Вне блокировки: обработчик чужой и может работать сколько угодно.
            onSubmit.accept(submitted);
        }
    }

    @Override
    protected synchronized void drawInternal(Screen screen) {
        super.drawInternal(screen);
        final Rectangle inner = getInnerRect();
        // Последняя ячейка остаётся под курсор в конце строки.
        final int room = inner.width() - MARKER.length() - 1;
        if (room <= 0 || inner.height() <= 0) {
            return;
        }

        offset = Math.clamp(offset, Math.max(0, cursor - room), cursor);
        final String visible = text.substring(offset, Math.min(text.length(), offset + room));

        final Screen.Writer writer = screen.writerBuilder().layer(getLayer()).build();
        writer.text(MARKER, inner.x(), inner.y());
        writer.text(visible, inner.x() + MARKER.length(), inner.y());
        screen.setShowCursor(true);
        screen.setCursorPosition(new Position(inner.x() + MARKER.length() + cursor - offset, inner.y()));
    }

    /**
     * Возвращает отправленный текст, если клавиша завершила ввод, иначе {@code null}.
     */
    private synchronized @Nullable String edit(KeyEvent event) {
        if (event.isKey()) {
            insert(String.valueOf((char) event.getPlainKey()));
            return null;
        }
        if (event.isKey(Key.Unicode)) {
            if (event.data() != null) {
                insert(event.data());
            }
            return null;
        }
        switch (event.key()) {
            case Key.Enter -> {
                final String submitted = text.toString().strip();
                clear();
                return submitted.isEmpty() ? null : submitted;
            }
            case Key.Backspace -> {
                if (cursor > 0) {
                    text.deleteCharAt(--cursor);
                }
            }
            case Key.Delete -> {
                if (cursor < text.length()) {
                    text.deleteCharAt(cursor);
                }
            }
            case Key.CursorLeft -> cursor = Math.max(0, cursor - 1);
            case Key.CursorRight -> cursor = Math.min(text.length(), cursor + 1);
            case Key.a | KeyMask.CtrlMask -> cursor = 0;
            case Key.e | KeyMask.CtrlMask -> cursor = text.length();
            case Key.u | KeyMask.CtrlMask -> clear();
            default -> {
            }
        }
        return null;
    }

    private void insert(String typed) {
        text.insert(cursor, typed);
        cursor += typed.length();
    }

    private void clear() {
        text.setLength(0);
        cursor = 0;
        offset = 0;
    }
}
