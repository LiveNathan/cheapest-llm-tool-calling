package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.StopWatch;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
public class GroqTest {
    private static final Logger logger = LoggerFactory.getLogger(GroqTest.class);
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai";
    private static final String DEFAULT_GROQ_MODEL = "llama-3.1-8b-instant";

    private ChatClient chatClient;
    private MockWeatherService mockWeatherService;

    @BeforeEach
    void setUp() {
        mockWeatherService = new MockWeatherService();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(GROQ_BASE_URL)
                        .apiKey(System.getenv("GROQ_API_KEY"))
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(DEFAULT_GROQ_MODEL)
                        .build())
                .build();
        chatClient = ChatClient.builder(chatModel).build();
    }

    @Test
    void streamFunctionCallWithMetricsTest() {
        // Reset the service counter
        mockWeatherService.reset();

        // Start timing
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("Tool Call Execution");

        // Execute the request
        final Flux<ChatClientResponse> chatClientResponseFlux = chatClient.prompt()
                .system("You are a helpful AI assistant.")
                .user("What's the weather like in San Francisco, Tokyo, and Paris? Return the temperature in Celsius.")
                .tools(mockWeatherService)
                .stream()
                .chatClientResponse();

        // Collect responses
        List<ChatClientResponse> responses = chatClientResponseFlux.collectList().block(Duration.ofSeconds(30));
        stopWatch.stop();

        assertThat(responses).isNotNull().isNotEmpty();

        // Extract content
        String content = responses.stream()
                .map(ChatClientResponse::chatResponse)
                .filter(Objects::nonNull)
                .map(ChatResponse::getResults)
                .flatMap(List::stream)
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());

        // Get the last response with metadata
        ChatResponse finalResponse = responses.stream()
                .map(ChatClientResponse::chatResponse)
                .filter(Objects::nonNull)
                .reduce((_, second) -> second)
                .orElse(null);

        assertThat(finalResponse).isNotNull();

        // 1. SPEED METRICS
        long executionTimeMs = stopWatch.getTotalTimeMillis();
        logger.info("Execution time: {} ms", executionTimeMs);
        assertThat(executionTimeMs).isLessThan(10000); // Should complete within 10 seconds

        // 2. ACCURACY METRICS
        assertThat(content).contains("30", "10", "15");
        assertThat(mockWeatherService.getTotalCallCount()).isEqualTo(3);
        // After line 85: List<ChatClientResponse> responses = ...
        logger.info("Total tool calls: {}", mockWeatherService.getTotalCallCount());
        logger.info("Location call counts: {}", mockWeatherService.getLocationCallCounts());
        var locationCounts = mockWeatherService.getLocationCallCounts();
        assertThat(locationCounts.keySet())
                .as("Should have called tool for all three cities")
                .hasSize(3);
        assertThat(locationCounts.values())
                .as("Each location should be called at least once")
                .allMatch(count -> count > 0);

        // 3. COST METRICS
        var usage = finalResponse.getMetadata().getUsage();
        assertThat(usage).isNotNull();

        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

        var modelPricing = LlmPricing.PRICING.get(DEFAULT_GROQ_MODEL);
        double cost = modelPricing.calculateCost(promptTokens, completionTokens);

        logger.info("Token usage - Prompt: {}, Completion: {}, Total: {}",
                promptTokens, completionTokens, usage.getTotalTokens());
        logger.info("Estimated cost: ${}", String.format("%.6f", cost));

        // 4. RELIABILITY METRICS
        assertThat(responses).allMatch(r -> r.chatResponse() == null ||
                                            !r.chatResponse().hasFinishReasons(java.util.Set.of("error", "failure")));
    }

    @Test
    void compareMultipleModelsTest() {
        String[] models = {"llama-3.1-8b-instant", "llama-3.3-70b-versatile"};

        for (String model : models) {
            if (!LlmPricing.PRICING.containsKey(model)) {
                logger.warn("Skipping model {} - pricing not configured", model);
                continue;
            }

            logger.info("Testing model: {}", model);

            // Create a model-specific client
            var modelSpecificChatModel = OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl(GROQ_BASE_URL)
                            .apiKey(System.getenv("GROQ_API_KEY"))
                            .build())
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model)
                            .build())
                    .build();

            var modelSpecificClient = ChatClient.builder(modelSpecificChatModel).build();
            mockWeatherService.reset();

            StopWatch stopWatch = new StopWatch();
            stopWatch.start(model);

            try {
                var response = modelSpecificClient.prompt()
                        .user("What's the weather in Tokyo?")
                        .tools(mockWeatherService)
                        .call()
                        .chatClientResponse();

                stopWatch.stop();

                boolean hallucinated = mockWeatherService.getTotalCallCount() == 0;

                var usage = Objects.requireNonNull(response.chatResponse()).getMetadata().getUsage();
                var pricing = LlmPricing.PRICING.get(model);
                double cost = pricing.calculateCost(
                        usage.getPromptTokens() != null ? usage.getPromptTokens() : 0,
                        usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0
                );

                logger.info("Model: {} - Time: {}ms, Cost: ${}, Tokens: {}, Hallucinated: {}",
                        model,
                        stopWatch.lastTaskInfo().getTimeMillis(),
                        String.format("%.6f", cost),
                        usage.getTotalTokens(),
                        hallucinated);

            } catch (Exception e) {
                logger.error("Error testing model {}: {}", model, e.getMessage());
                logger.info("Model: {} - FAILED (tool hallucination or error)", model);
            }
        }
    }
}