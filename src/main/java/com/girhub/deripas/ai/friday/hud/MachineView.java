package com.girhub.deripas.ai.friday.hud;

import org.springframework.ai.chat.messages.Message;

import java.time.Duration;
import java.util.List;

/**
 * Экран, на который {@link Hud} выводит увиденное. Что показывать, решает монитор;
 * как это выглядит — реализация.
 *
 * <p>Реализаций две, и выбирает между ними свойство {@value #PROPERTY}:
 * <ul>
 *   <li>{@code printer} (по умолчанию) — {@link MachineViewPrinter}, печать в stdout;</li>
 *   <li>{@code tui} — {@link MachineTui}, полноэкранный интерфейс с полем ввода.</li>
 * </ul>
 */
interface MachineView {

    String PROPERTY = "friday.hud.view";

    void showBootLog(List<BootLine> lines);

    void showOrder(String userInput);

    void showContext(List<Message> prompt, int tokens);

    void showTurnFinished(Duration elapsed);
}
