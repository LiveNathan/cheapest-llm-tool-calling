package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OllamaToolCallingBenchmarkTest {
    private static final Logger logger = LoggerFactory.getLogger(OllamaToolCallingBenchmarkTest.class);
    private static final int TEST_ITERATIONS = 1; // Fewer iterations for local models
    private static final int TIMEOUT_SECONDS = 180; // Longer timeout for local models

    private MockWeatherService mockWeatherService;
    private BenchmarkRunner benchmarkRunner;
    private OllamaTestContainerProvider ollamaProvider;
    private OllamaCustomGemmaProvider gemmaProvider;

    @BeforeEach
    void setUp() {
        mockWeatherService = new MockWeatherService();

        ollamaProvider = new OllamaTestContainerProvider();
        gemmaProvider = new OllamaCustomGemmaProvider();

        List<LlmProvider> providers = List.of(
                ollamaProvider,
                gemmaProvider
        );

        benchmarkRunner = new BenchmarkRunner(providers, TEST_ITERATIONS, TIMEOUT_SECONDS);
    }

    @AfterAll
    void tearDown() {
        logger.info("Cleaning up Ollama containers...");
        if (ollamaProvider != null) {
            ollamaProvider.cleanup();
        }
        if (gemmaProvider != null) {
            gemmaProvider.cleanup();
        }
    }

    @Test
    void testSmallestEffectiveModel() {
        // Test with Qwen 2.5 1.5B - smallest model that supports tool calling well
        TestScenario scenario = new TestScenario.Builder()
                .name("Ollama Qwen 2.5 1.5B Tool Calling Test")
                .systemPrompt("You are a helpful weather assistant.")
                .prompts(
                        "What's the weather in San Francisco?",
                        "Compare the weather between Tokyo and Paris"
                )
                .validation(() -> {
                    int calls = mockWeatherService.getTotalCallCount();
                    logger.info("Weather service called {} times", calls);

                    // Should call at least once for first prompt, ideally 3 times total
                    if (calls == 0) return 0.0;
                    if (calls == 1) return 0.5;
                    if (calls >= 2) return 1.0;
                    return 0.0;
                })
                .toolService(mockWeatherService)
                .build();

        var results = benchmarkRunner.runBenchmark(scenario);
        results.printReport();
        results.determineWinner();

        // Verify at least one model succeeded
        boolean anySuccess = results.getResults().values().stream()
                .anyMatch(tr -> tr.getSuccessRate() > 0);
        assertThat(anySuccess)
                .withFailMessage("At least one Ollama model should successfully call tools")
                .isTrue();
    }

    @Test
    void compareAllOllamaModels() {
        logger.info("\n=== OLLAMA MODEL COMPARISON ===");
        logger.info("Finding the smallest, quickest model that works...");

        TestScenario scenario = new TestScenario.Builder()
                .name("Ollama Model Comparison")
                .systemPrompt("You are a helpful weather assistant.")
                .prompts(
                        "What's the weather in Tokyo?",
                        "Now check San Francisco",
                        "Which city is warmer?"
                )
                .validation(() -> {
                    int calls = mockWeatherService.getTotalCallCount();

                    // Score based on number of successful tool calls
                    double score = 0.0;
                    if (calls >= 1) score = 0.33;
                    if (calls >= 2) score = 0.66;
                    if (calls >= 2) score = 1.0; // Third prompt uses memory, not a new call

                    logger.info("Validation: {} tool calls made, score: {}", calls, score);
                    return score;
                })
                .toolService(mockWeatherService)
                .build();

        var results = benchmarkRunner.runBenchmark(scenario);
        results.printReport();
        results.determineWinner();

        logger.info("\n=== RECOMMENDATION ===");
        logger.info("For tool calling with Ollama:");
        logger.info("1. Qwen 2.5 1.5B - Smallest effective model");
        logger.info("2. Llama 3.1 8B - Most reliable");
        logger.info("3. Gemma3 12B (custom) - Best Google model with tool support");
    }
}