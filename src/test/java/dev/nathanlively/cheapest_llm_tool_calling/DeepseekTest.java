package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
public class DeepseekTest {

    @Test
    void weatherServiceSmokeTest() {
        MockWeatherService weatherService = new MockWeatherService();
        final DeepSeekChatOptions mistralAiChatOptions = DeepSeekChatOptions.builder()
                .model(DeepSeekApi.ChatModel.DEEPSEEK_CHAT.getValue())
                .temperature(0.1)
                .build();
        final DeepSeekApi deepseekApiKey = DeepSeekApi.builder().apiKey(System.getenv("DEEPSEEK_API_KEY")).build();
        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(deepseekApiKey)
                .defaultOptions(mistralAiChatOptions)
                .build();
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        String response = chatClient.prompt()
                .user("What's the weather in Tokyo?")
                .tools(weatherService)
                .call()
                .content();

        assertThat(response).contains("10");
        assertThat(weatherService.getTotalCallCount()).isEqualTo(1);
    }

}