package com.girhub.deripas.ai.friday.hud;

import com.girhub.deripas.ai.friday.agent.Agent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Соединяет поле ввода с агентом: приказ, набранный в {@link MachineTui}, уходит в {@link Agent#run}.
 *
 * <p>Экран про агента не знает и получает его как функцию «приказ → ответ». Запуск занимает
 * главный поток до выхода из интерфейса.
 *
 * <p>Выход из интерфейса — это выход из приложения, и завершается оно явно: сам по себе
 * процесс не остановится, пока жив хоть один не-daemon поток, а такой в зависимостях есть.
 * Без явного завершения после Ctrl+Q остался бы пустой терминал с висящим процессом.
 */
@Component
@ConditionalOnProperty(name = MachineView.PROPERTY, havingValue = "tui")
@RequiredArgsConstructor
class TuiRunner implements ApplicationRunner {

    private final MachineTui tui;
    private final Agent agent;
    private final ApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        tui.run(agent::run);
        System.exit(SpringApplication.exit(context));
    }
}
