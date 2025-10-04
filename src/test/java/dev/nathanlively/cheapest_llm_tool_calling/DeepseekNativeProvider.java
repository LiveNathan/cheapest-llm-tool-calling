package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.util.List;

public class DeepseekNativeProvider extends LlmProvider {
    private static final String chatValue = DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue();
    private static final List<String> DEEPSEEK_MODELS = List.of(chatValue);

    public DeepseekNativeProvider() {
        super("DeepseekNative", "DEEPSEEK_API_KEY", DEEPSEEK_MODELS);
    }

    @Override
    public ChatClient createChatClient(String model, TestScenario scenario) {
        String apiKey = System.getenv(apiKeyEnvVar);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API key not found for " + name + ". Set environment variable: " + apiKeyEnvVar);
        }

        DeepSeekApi deepSeekApi = DeepSeekApi.builder().apiKey(apiKey).build();
        DeepSeekChatOptions chatOptions = DeepSeekChatOptions.builder()
                .model(chatValue)
                .temperature(0.1)
                .build();

        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(chatOptions)
                .build();

        ChatClient.Builder builder = ChatClient.builder(chatModel);

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();
        builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor());

        return builder.build();
    }

    public String getFullModelName(String model) {
        return "deepseek-native/" + model;
    }
}
