package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;

import java.util.List;

public class MistralNativeProvider extends LlmProvider {
    private static final String smallValue = MistralAiApi.ChatModel.SMALL.getValue();
    private static final List<String> MISTRAL_MODELS = List.of(smallValue);

    public MistralNativeProvider() {
        super("MistralNative", "MISTRALAI_API_KEY", MISTRAL_MODELS);
    }

    @Override
    public ChatClient createChatClient(String model, boolean withMemory, TestScenario scenario) {
        String apiKey = System.getenv(apiKeyEnvVar);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API key not found for " + name + ". Set environment variable: " + apiKeyEnvVar);
        }

        MistralAiApi mistralAiApi = new MistralAiApi(apiKey);
        MistralAiChatOptions chatOptions = MistralAiChatOptions.builder()
                .model(smallValue)
                .temperature(0.1)
                .build();

        MistralAiChatModel chatModel = MistralAiChatModel.builder()
                .mistralAiApi(mistralAiApi)
                .defaultOptions(chatOptions)
                .build();

        ChatClient.Builder builder = ChatClient.builder(chatModel);

        if (withMemory) {
            MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();
            builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor());
        }

        return builder.build();
    }

    @Override
    public String getFullModelName(String model) {
        return "mistral-native/" + model;
    }
}
