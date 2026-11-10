package com.girhub.deripas.ai.friday;

import com.girhub.deripas.ai.friday.agent.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Scripted demo for the printing HUD. With {@code friday.hud.view=tui} orders come from the terminal UI instead.
 */
@Component
@ConditionalOnProperty(name = "friday.hud.view", havingValue = "printer", matchIfMissing = true)
@RequiredArgsConstructor
public class DemoRunner implements CommandLineRunner {

    private final Agent agent;

    @Override
    public void run(String... args) {
        System.out.println(agent.run("Кто ты? Ответь одним предложением."));
        System.out.println(agent.run("Повтори что ты сказал"));
    }
}
