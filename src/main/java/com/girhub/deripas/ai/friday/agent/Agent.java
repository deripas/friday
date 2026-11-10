package com.girhub.deripas.ai.friday.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class Agent {

    private static final String SYSTEM_PROMPT = """
            You are 'Friday', a coding agent that works in a local repository.
            Answer concisely.
            """;

    private final Conversation conversation = new Conversation(SYSTEM_PROMPT);
    private final ChatModel chatModel;

    public String run(String userMessage) {
        conversation.addUserMessage(userMessage);
        final AssistantMessage assistantMessage = chatModel.call(new Prompt(conversation.toPromptMessages())).getResult().getOutput();
        conversation.addAssistantMessage(assistantMessage);
        return assistantMessage.getText();
    }
}
