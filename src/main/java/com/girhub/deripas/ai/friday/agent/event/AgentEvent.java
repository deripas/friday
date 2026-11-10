package com.girhub.deripas.ai.friday.agent.event;

import java.time.Duration;

public sealed interface AgentEvent {

    record TurnStarted(String userMessage) implements AgentEvent { }
    record TurnFinished(Duration duration) implements AgentEvent { }
    record AgentSpoke(String assistantMessage) implements AgentEvent { }

}
