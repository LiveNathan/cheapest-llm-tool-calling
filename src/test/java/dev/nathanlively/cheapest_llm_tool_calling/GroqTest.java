package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
public class GroqTest {
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai";
    private static final String DEFAULT_GROQ_MODEL = "llama-3.1-8b-instant";
    private OpenAiChatModel chatModel;

    @BeforeEach
    void setUp() {
        chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(GROQ_BASE_URL)
                        .apiKey(System.getenv("GROQ_API_KEY"))
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(DEFAULT_GROQ_MODEL)
                        .build()).build();
    }

    @Test
    void streamRoleTest() {
        UserMessage userMessage = new UserMessage(
                "Tell me about 3 famous pirates from the Golden Age of Piracy and what they did.");
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate("""
                You are a helpful AI assistant. Your name is {name}.
                You are an AI assistant that helps people find information.
                Your name is {name}
                You should reply to the user's request with your name and also in the style of a {voice}.""");
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
        Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
        Flux<ChatResponse> flux = this.chatModel.stream(prompt);

        List<ChatResponse> responses = flux.collectList().block();
        assertThat(Objects.requireNonNull(responses).size()).isGreaterThan(1);

        String stitchedResponseContent = responses.stream()
                .map(ChatResponse::getResults)
                .flatMap(List::stream)
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .collect(Collectors.joining());

        assertThat(stitchedResponseContent).contains("Blackbeard");
    }

}
