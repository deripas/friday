package com.girhub.deripas.ai.friday.agent;

import com.girhub.deripas.ai.friday.agent.event.AgentEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseErrorHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class Agent {

    private static final String SYSTEM_PROMPT = """
        You are 'Friday', a coding agent that works in a local repository.
        Answer concisely.
        """;

    private final ApplicationEventPublisher publisher;
    @Getter
    private final Conversation conversation = new Conversation(SYSTEM_PROMPT);
    @Getter
    private final ChatModel chatModel;
    private final ResponseErrorHandler responseErrorHandler;

    public String run(String userMessage) {
        return wrap(chat()).apply(userMessage);
    }

    private Function<String, String> chat() {
        return (request) -> {
            conversation.addUserMessage(request);
            final AssistantMessage assistantMessage = chatModel.call(new Prompt(conversation.toPromptMessages())).getResult().getOutput();
            conversation.addAssistantMessage(assistantMessage);
            return assistantMessage.toString();
        };
    }

    private Function<String, String> wrap(Function<String, String> chat) {
        return (request) -> {
            final Instant startTime = Instant.now();
            publisher.publishEvent(new AgentEvent.TurnStarted(request));
            final String response = chat.apply(request);

            publisher.publishEvent(new AgentEvent.AgentSpoke(response));
            publisher.publishEvent(new AgentEvent.TurnFinished(Duration.between(startTime, Instant.now())));
            return response;
        };
    }
}
