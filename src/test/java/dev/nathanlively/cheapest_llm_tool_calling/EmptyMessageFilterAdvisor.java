// New file: src/test/java/dev/nathanlively/cheapest_llm_tool_calling/EmptyMessageFilterAdvisor.java
package dev.nathanlively.cheapest_llm_tool_calling;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EmptyMessageFilterAdvisor implements CallAdvisor, StreamAdvisor {
    private static final Logger logger = LoggerFactory.getLogger(EmptyMessageFilterAdvisor.class);

    @NotNull
    @Override
    public String getName() {
        return "EmptyMessageFilterAdvisor";
    }

    @Override
    public int getOrder() {
        return 0; // Execute early in the chain
    }

    @NotNull
    @Override
    public ChatClientResponse adviseCall(@NotNull ChatClientRequest request, CallAdvisorChain chain) {
        // Process the request normally
        ChatClientResponse response = chain.nextCall(request);

        // Filter out empty assistant messages from the response
        List<Generation> filteredResults = Objects.requireNonNull(response.chatResponse()).getResults().stream()
                .filter(generation -> {
                    AssistantMessage assistantMessage = generation.getOutput();
                    String text = assistantMessage.getText();
                    boolean isEmpty = text == null || text.trim().isEmpty();
                    if (isEmpty) {
                        logger.debug("Filtering out empty assistant message");
                    }
                    return !isEmpty;
                })
                .collect(Collectors.toList());

        // If we filtered out results, create a new response with only valid results
        if (filteredResults.size() != response.chatResponse().getResults().size()) {
            ChatResponse filteredChatResponse = new ChatResponse(
                    filteredResults,
                    response.chatResponse().getMetadata()
            );
            return ChatClientResponse.builder()
                    .chatResponse(filteredChatResponse)
                    .build();
        }

        return response;
    }

    @NotNull
    @Override
    public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest request, StreamAdvisorChain chain) {
        // For streaming, filter empty messages as they come
        return chain.nextStream(request)
                .filter(response -> {
                    if (response.chatResponse() != null) {
                        response.chatResponse().getResult();
                        AssistantMessage assistantMessage = response.chatResponse().getResult().getOutput();
                        String text = assistantMessage.getText();
                        return text != null && !text.trim().isEmpty();
                    }
                    return true;
                });
    }
}