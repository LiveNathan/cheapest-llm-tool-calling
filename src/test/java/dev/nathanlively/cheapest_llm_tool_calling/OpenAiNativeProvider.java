package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

public class OpenAiNativeProvider extends LlmProvider {
    private static final String fourOmini = OpenAiApi.ChatModel.GPT_4_O_MINI.getValue();
    private static final String fourO1nano = OpenAiApi.ChatModel.GPT_4_1_NANO.getValue();
    private static final List<String> OPENAI_MODELS = List.of(fourOmini, fourO1nano);

    public OpenAiNativeProvider() {
        super("OpenAiNative", "OPENAI_API_KEY", OPENAI_MODELS);
    }

    @Override
    public ChatClient createChatClient(String model, TestScenario scenario) {
        String apiKey = System.getenv(apiKeyEnvVar);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API key not found for " + name + ". Set environment variable: " + apiKeyEnvVar);
        }

        OpenAiApi openAiApi = OpenAiApi.builder().apiKey(apiKey).build();
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(fourOmini)
                .temperature(0.1)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();

        ChatClient.Builder builder = ChatClient.builder(chatModel);

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder().build();
        builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor());

        return builder.build();
    }

    public String getFullModelName(String model) {
        return "openai-native/" + model;
    }
}
