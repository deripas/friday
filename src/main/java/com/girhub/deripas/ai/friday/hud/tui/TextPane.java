package com.girhub.deripas.ai.friday.hud.tui;

import org.jline.utils.AttributedString;
import org.springframework.shell.jline.tui.component.view.control.BoxView;
import org.springframework.shell.jline.tui.component.view.screen.Screen;
import org.springframework.shell.jline.tui.geom.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Панель текста в рамке: хранит строки, переносит их по ширине и прокручивается.
 *
 * <p>В Spring Shell такого view нет: {@code ListView} — это список выбора, он не переносит
 * длинные строки и не держится за последнюю. Здесь панель прижата к низу, как лог: новые
 * строки видны сразу, пока её не прокрутили назад.
 *
 * <p>Методы синхронизированы: строки дописывает поток агента, а рисует поток интерфейса.
 */
public class TextPane extends BoxView {

    private final List<AttributedString> lines = new ArrayList<>();

    /**
     * На сколько экранных строк панель отмотана назад от конца. Ноль — следит за последней строкой.
     */
    private int scrollBack;

    public TextPane(String title) {
        setShowBorder(true);
        setTitle(title);
    }

    public synchronized void append(List<AttributedString> added) {
        lines.addAll(added);
    }

    public synchronized void replace(List<AttributedString> replacement) {
        lines.clear();
        lines.addAll(replacement);
        scrollBack = 0;
    }

    /**
     * Прокрутка: положительное число — назад, к началу; отрицательное — к концу.
     */
    public synchronized void scroll(int delta) {
        scrollBack = Math.max(0, scrollBack + delta);
    }

    @Override
    protected synchronized void drawInternal(Screen screen) {
        super.drawInternal(screen);
        final Rectangle inner = getInnerRect();
        if (inner.width() <= 0 || inner.height() <= 0) {
            return;
        }

        final List<AttributedString> wrapped = new ArrayList<>(lines.size());
        for (AttributedString line : lines) {
            wrap(line, inner.width(), wrapped);
        }

        // Ширина окна могла измениться, а вместе с ней и число экранных строк: держим прокрутку в границах.
        scrollBack = Math.min(scrollBack, Math.max(0, wrapped.size() - inner.height()));
        final int first = Math.max(0, wrapped.size() - inner.height() - scrollBack);
        final int last = Math.min(wrapped.size(), first + inner.height());

        final Screen.Writer writer = screen.writerBuilder().layer(getLayer()).build();
        for (int i = first; i < last; i++) {
            writer.text(wrapped.get(i), inner.x(), inner.y() + i - first);
        }
    }

    /**
     * Перенос по словам; слово длиннее ширины режется. Ширина считается в символах, как и в самом
     * {@link Screen}: один {@code char} — одна ячейка.
     */
    private static void wrap(AttributedString line, int width, List<AttributedString> out) {
        int start = 0;
        while (line.length() - start > width) {
            final int space = lastSpace(line, start, start + width);
            if (space > start) {
                out.add(line.subSequence(start, space));
                start = space + 1;
            } else {
                out.add(line.subSequence(start, start + width));
                start += width;
            }
        }
        out.add(line.subSequence(start, line.length()));
    }

    private static int lastSpace(AttributedString line, int from, int to) {
        for (int i = to; i > from; i--) {
            if (line.charAt(i) == ' ') {
                return i;
            }
        }
        return -1;
    }
}
