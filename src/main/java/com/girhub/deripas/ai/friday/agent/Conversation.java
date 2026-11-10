package com.girhub.deripas.ai.friday.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class Conversation {
    private final String systemPrompt;
    private final List<Message> messages = new ArrayList<>();

    public void addUserMessage(String userMessage) {
        messages.add(new UserMessage(userMessage));
    }

    public void addAssistantMessage(AssistantMessage assistantMessage) {
        messages.add(assistantMessage);
    }

    public List<Message> toPromptMessages() {
        final List<Message> prompt = new ArrayList<>(messages.size() + 1);
        prompt.add(new SystemMessage(systemPrompt));
        prompt.addAll(messages);
        return prompt;
    }

}




