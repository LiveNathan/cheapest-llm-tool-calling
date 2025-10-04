package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MISTRALAI_API_KEY", matches = ".+")
public class MistralTest {

    @Test
    void weatherServiceSmokeTest() {
        MockWeatherService weatherService = new MockWeatherService();
        MistralAiApi mistralAiApi = new MistralAiApi(System.getenv("MISTRALAI_API_KEY"));
        final MistralAiChatOptions mistralAiChatOptions = MistralAiChatOptions.builder()
                .model(MistralAiApi.ChatModel.LARGE.getValue())
                .temperature(0.1)
                .build();
        MistralAiChatModel chatModel = MistralAiChatModel.builder()
                .mistralAiApi(mistralAiApi)
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