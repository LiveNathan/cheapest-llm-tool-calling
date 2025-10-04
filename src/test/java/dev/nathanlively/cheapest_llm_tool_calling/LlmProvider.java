package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

public abstract class LlmProvider {
    protected final String name;
    protected final String baseUrl;
    protected final String apiKeyEnvVar;
    protected final List<String> supportedModels;

    protected LlmProvider(String name, String baseUrl, String apiKeyEnvVar, List<String> supportedModels) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.apiKeyEnvVar = apiKeyEnvVar;
        this.supportedModels = supportedModels;
    }

    public String getName() {
        return name;
    }

    public List<String> getSupportedModels() {
        return supportedModels;
    }

    public String getFullModelName(String model) {
        return name.toLowerCase() + "/" + model;
    }

    public boolean isAvailable() {
        String apiKey = System.getenv(apiKeyEnvVar);
        return apiKey != null && !apiKey.isBlank();
    }

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

    public LlmPricing.ModelPricing getPricing(String model) {
        String fullModelName = getFullModelName(model);
        return LlmPricing.PRICING.get(fullModelName);
    }
}