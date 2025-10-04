package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

public abstract class OpenAiProxyProvider extends LlmProvider {
    protected final String baseUrl;

    protected OpenAiProxyProvider(String name, String baseUrl, String apiKeyEnvVar, List<String> supportedModels) {
        super(name, apiKeyEnvVar, supportedModels);
        this.baseUrl = baseUrl;
    }

    @Override
    public ChatClient createChatClient(String model, boolean withMemory, TestScenario scenario) {
        String apiKey = System.getenv(apiKeyEnvVar);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API key not found for " + name + ". Set environment variable: " + apiKeyEnvVar);
        }

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(0.1)
                        .build())
                .build();

        ChatClient.Builder builder = ChatClient.builder(chatModel);

        if (withMemory) {
            MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor());
        }

        return builder.build();
    }
}
