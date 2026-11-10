package com.girhub.deripas.ai.friday;

import com.girhub.deripas.ai.friday.agent.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoRunner implements CommandLineRunner {

    private final Agent agent;

    @Override
    public void run(String... args) {
        log.info(agent.run("Кто ты? Ответь одним предложением."));
    }
}
