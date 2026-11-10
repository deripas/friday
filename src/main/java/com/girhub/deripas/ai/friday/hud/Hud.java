package com.girhub.deripas.ai.friday.hud;

import com.girhub.deripas.ai.friday.agent.Agent;
import com.girhub.deripas.ai.friday.agent.event.AgentEvent;
import com.girhub.deripas.ai.friday.context.TokenEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.girhub.deripas.ai.friday.hud.BodyPart.DIAGNOSTIC_BUS;
import static com.girhub.deripas.ai.friday.hud.BodyPart.MEMORY_BANK;
import static com.girhub.deripas.ai.friday.hud.BodyPart.NEUROPROCESSOR;
import static com.girhub.deripas.ai.friday.hud.BodyPart.POWER_CELL;
import static com.girhub.deripas.ai.friday.hud.LanguageUtils.plural;

@Component
@RequiredArgsConstructor
public class Hud {

    private final MachineView machinePrinter;
    private final Agent agent;

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted(ApplicationStartedEvent event) {
        machinePrinter.showBootLog(collectBootLines(event.getApplicationContext().getBeanDefinitionCount()));
    }

    @EventListener
    public void onTurnStarted(AgentEvent.TurnStarted event) {
        String userInput = event.userMessage();
        machinePrinter.showOrder(userInput);
        List<Message> promptMessages = agent.getConversation().toPromptMessages();
        machinePrinter.showContext(promptMessages, TokenEstimator.estimate(promptMessages));

    }

    @EventListener
    public void onTurnFinished(AgentEvent.TurnFinished event) {
        machinePrinter.showTurnFinished(event.duration());
    }

//    @EventListener
    public void onAgentSpokeEvent(AgentEvent.AgentSpoke event) {
        String assistantMessage = event.assistantMessage();
        boolean containsRussian = java.util.regex.Pattern
                .compile("[А-Яа-яЁё]")
                .matcher(assistantMessage)
                .find();

        if (containsRussian) {
            System.err.println("НЕ ГОВОРИ СО МНОЙ ПО РУССКИ");
            throw new RuntimeException(assistantMessage);
        }
    }


    private List<BootLine> collectBootLines(int beanDefinitionCount) {
        final List<BootLine> lines = new ArrayList<>();
        lines.add(BootLine.of(POWER_CELL, BootStatus.ONLINE,
            "спринг контекст " + plural(beanDefinitionCount, "бин", "бина", "бинов"))) ;
        lines.add(BootLine.of(NEUROPROCESSOR,BootStatus.ONLINE, agent.getChatModel().getOptions().getModel()));
        lines.add(BootLine.of(MEMORY_BANK, BootStatus.ONLINE, agent.getConversation().getClass().getSimpleName()));
        lines.add(BootLine.of(DIAGNOSTIC_BUS,BootStatus.ONLINE,"Agent Events: "+ AgentEvent.class.getPermittedSubclasses().length));
        return lines;

    }
}
