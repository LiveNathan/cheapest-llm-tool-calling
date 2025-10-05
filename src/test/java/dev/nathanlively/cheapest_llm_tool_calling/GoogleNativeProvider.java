package dev.nathanlively.cheapest_llm_tool_calling;

import com.google.genai.Client;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import java.util.List;

public class GoogleNativeProvider extends LlmProvider {
    private static final String flash = GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue();
    private static final String flashLite = GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH_LIGHT.getValue();
    private static final List<String> GEMINI_MODELS = List.of(flash, flashLite);

    public GoogleNativeProvider() {
        super("GoogleNative", "GEMINI_API_KEY", GEMINI_MODELS);
    }

    @Override
    public ChatClient createChatClient(String model, TestScenario scenario) {
        String apiKey = System.getenv(apiKeyEnvVar);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API key not found for " + name + ". Set environment variable: " + apiKeyEnvVar);
        }

        Client genAiClient = Client.builder()
                .apiKey(System.getenv("GEMINI_API_KEY"))
                .build();
        final GoogleGenAiChatOptions chatOptions = GoogleGenAiChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .build();
        GoogleGenAiChatModel chatModel = GoogleGenAiChatModel.builder()
                .genAiClient(genAiClient)
                .defaultOptions(chatOptions)
                .build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build(),
                        new SimpleLoggerAdvisor()).build();
    }

    public String getFullModelName(String model) {
        return "google-native/" + model;
    }
}
