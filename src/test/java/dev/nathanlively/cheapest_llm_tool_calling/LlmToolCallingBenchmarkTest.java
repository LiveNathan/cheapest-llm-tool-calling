package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MISTRALAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
public class LlmToolCallingBenchmarkTest {
    private static final Logger logger = LoggerFactory.getLogger(LlmToolCallingBenchmarkTest.class);
    private static final int TEST_ITERATIONS = 5;
    private static final int TIMEOUT_SECONDS = 60 * 3;

    private static final String MIXING_CONSOLE_SYSTEM_PROMPT = """
            - API uses 0-based indexing (ch.0, ch.1, ch.2...)
            - Humans use 1-based indexing (Channel 1, Channel 2, Channel 3...)
            - You must translate human/user requests from 1-based to 0-based.
            
            Examples: Human "Channel 1" → API ch.0 | Human "Channels 1-4" → API ch.0-ch.3""";

    private MockMixingConsoleService mockConsoleService;
    private MockWeatherService mockWeatherService;
    private BenchmarkRunner benchmarkRunner;

    static Stream<String> apiKeyProvider() {
        return Stream.of(
                "GROQ_API_KEY",
                "MISTRALAI_API_KEY",
                "DEEPSEEK_API_KEY",
                "GEMINI_API_KEY",
                "OPENAI_API_KEY"
        );
    }

    @BeforeEach
    void setUp() {
        mockConsoleService = new MockMixingConsoleService();
        mockWeatherService = new MockWeatherService();

        List<LlmProvider> providers = List.of(
                // OpenAI Proxy implementations
                new GroqProxyProvider(),
//                new MistralProxyProvider(),  // always fails with error "Unexpected role 'system' after role 'assistant'"
                new DeepseekProxyProvider(),

                // Native Spring AI implementations
//                new MistralNativeProvider(),
                new DeepseekNativeProvider(),
                new GoogleNativeProvider(),
                new OpenAiNativeProvider()
        );

        benchmarkRunner = new BenchmarkRunner(providers, TEST_ITERATIONS, TIMEOUT_SECONDS);
    }

    @ParameterizedTest
    @MethodSource("apiKeyProvider")
    void debugApiKey(String keyName) {
        String apiKey = System.getenv(keyName);
        System.out.println(keyName + " present: " + (apiKey != null));
        System.out.println(keyName + " length: " + (apiKey != null ? apiKey.length() : "null"));
        System.out.println(keyName + " first 8 chars: " + (apiKey != null ? apiKey.substring(0, Math.min(8, apiKey.length())) : "null"));

        assertThat(apiKey).isNotNull();
        assertThat(apiKey).isNotEmpty();
    }

    @Test
    void weatherServiceSmokeTest() {
        TestScenario scenario = new TestScenario.Builder()
                .name("Weather Service Smoke Test")
                .systemPrompt("You are a helpful assistant for the weather service.")
                .prompts("What's the weather in Tokyo?")
                .validation(() -> {
                    // Simple validation - did it call the weather service?
                    return mockWeatherService.getTotalCallCount() > 0 ? 1.0 : 0.0;
                })
                .toolService(mockWeatherService)
                .build();

        var results = benchmarkRunner.runBenchmark(scenario);
        results.printReport();

        // Verify at least one model succeeded
        boolean anySuccess = results.getResults().values().stream()
                .anyMatch(tr -> tr.getSuccessRate() > 0);
        assertThat(anySuccess).isTrue();
    }

    @Test
    void masterCheapestLlmBenchmark() {
        logger.info("\n" + "=".repeat(60));
        logger.info("MASTER BENCHMARK: Finding Cheapest LLM for Tool Calling");
        logger.info("=".repeat(60));

        // Run all scenarios
        TestScenario[] scenarios = {
                createSimpleScenario(),
                createComplexScenario()
        };

        HashMap<String, Double> allResults = new HashMap<>();

        for (TestScenario scenario : scenarios) {
            var results = benchmarkRunner.runBenchmark(scenario);
            results.printReport();

            // Aggregate scores
            for (var entry : results.getResults().entrySet()) {
                String model = entry.getKey();
                TestResults tr = entry.getValue();

                double score = calculateOverallScore(tr);
                allResults.merge(model, score, Double::sum);
            }
        }

        // Print overall winner across all scenarios
        logger.info("\n" + "=".repeat(60));
        logger.info("OVERALL WINNER ACROSS ALL SCENARIOS");
        logger.info("=".repeat(60));

        allResults.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> logger.info("{}: {}", e.getKey(), String.format("%.2f", e.getValue())));

        String overallWinner = allResults.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No winner");

        logger.info("\n🏆🏆🏆 OVERALL CHEAPEST RELIABLE LLM: {} 🏆🏆🏆", overallWinner);
    }

    // Helper methods
    private TestScenario createSimpleScenario() {
        return new TestScenario.Builder()
                .name("Simple Channel Renaming with Memory")
                .prompts(
                        "Rename channel 1 to Kick and channel 2 to Snare",
                        "What did you just name channel 1?",  // Tests memory
                        "Now change the first channel you renamed to Kick-In and the second to Snare-Top",  // Tests memory of order
                        "Rename channel 3 to the same as channel 1 but with 'Backup-' prefix"  // Tests read + memory
                )
                .validation(this::validateSimpleChannelRenaming)
                .toolService(mockConsoleService)
                .systemPrompt(MIXING_CONSOLE_SYSTEM_PROMPT)
                .build();
    }

    private double validateSimpleChannelRenaming() {
        List<ApiCall> calls = mockConsoleService.getCapturedApiCalls();
        logger.info("Simple validation - Captured calls: {}", calls);

        double score = 0.0;
        double maxScore = 10.0;

        // First prompt: Initial naming
        if (calls.contains(new ApiCall("ch.0.cfg.name", "Kick"))) score += 2;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "Snare"))) score += 2;

        // Second prompt: Should use getParameter to check (not required but good)
        // We can't validate this directly but it shouldn't create new calls

        // Third prompt: Renaming based on memory
        if (calls.contains(new ApiCall("ch.0.cfg.name", "Kick-In"))) score += 2;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "Snare-Top"))) score += 2;

        // Fourth prompt: Read ch.0 and apply prefix
        if (calls.contains(new ApiCall("ch.2.cfg.name", "Backup-Kick-In"))) score += 2;

        logger.info("Simple validation score: {}/{}", score, maxScore);
        return score / maxScore;
    }

    private TestScenario createComplexScenario() {
        return new TestScenario.Builder()
                .name("Complex Band Setup with Memory")
                .prompts(
                        "Name channels 1-7: Kick, Snare, Hi-hat, Tom 1, Tom 2, Overheads L, Overheads R",
                        "Add bass on channel 8 and guitar on channel 9",
                        "What's on channel 6? Now swap it with what's on channel 9",  // Tests read + swap
                        "Add lead vocal on channel 12, backing vocals on 13-14",
                        "Change all drum channels (the first 7 you set up) to have 'DR-' prefix",  // Tests memory of what's drums
                        "Rename the Kick channel specifically to 'DR-Kick-In'"  // Tests finding and updating specific channel
                )
                .validation(this::validateComplexBandSetup)
                .toolService(mockConsoleService)
                .systemPrompt(MIXING_CONSOLE_SYSTEM_PROMPT)
                .build();
    }

    private double validateComplexBandSetup() {
        var calls = mockConsoleService.getCapturedApiCalls();
        logger.info("Complex validation - Total calls made: {}", calls.size());
        logger.info("Complex validation - Captured calls: {}", calls);

        double score = 0.0;
        double maxScore = 22.0;

        // First prompt: Initial drum setup (7 points)
        if (calls.contains(new ApiCall("ch.0.cfg.name", "Kick"))) score += 1;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "Snare"))) score += 1;
        if (calls.contains(new ApiCall("ch.2.cfg.name", "Hi-hat"))) score += 1;
        if (calls.contains(new ApiCall("ch.3.cfg.name", "Tom 1"))) score += 1;
        if (calls.contains(new ApiCall("ch.4.cfg.name", "Tom 2"))) score += 1;
        if (calls.contains(new ApiCall("ch.5.cfg.name", "Overheads L"))) score += 1;
        if (calls.contains(new ApiCall("ch.6.cfg.name", "Overheads R"))) score += 1;

        // Second prompt: Bass and guitar (2 points)
        if (calls.contains(new ApiCall("ch.7.cfg.name", "bass"))) score += 1;
        if (calls.contains(new ApiCall("ch.8.cfg.name", "guitar"))) score += 1;

        // Third prompt: Swap ch.6 (Overheads R) with ch.8 (guitar) (2 points)
        if (calls.contains(new ApiCall("ch.5.cfg.name", "guitar"))) score += 1;
        if (calls.contains(new ApiCall("ch.8.cfg.name", "Overheads R"))) score += 1;

        // Fourth prompt: Vocals (3 points)
        if (calls.contains(new ApiCall("ch.11.cfg.name", "lead vocal"))) score += 1;
        if (calls.contains(new ApiCall("ch.12.cfg.name", "backing vocals"))) score += 1;
        if (calls.contains(new ApiCall("ch.13.cfg.name", "backing vocals"))) score += 1;

        // Fifth prompt: DR- prefix for all drums (7 points)
        // Note: After swap, drums are on channels 0-4, 5 (guitar now), 6, 8 (OH-R now)
        if (calls.contains(new ApiCall("ch.0.cfg.name", "DR-Kick"))) score += 1;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "DR-Snare"))) score += 1;
        if (calls.contains(new ApiCall("ch.2.cfg.name", "DR-Hi-hat"))) score += 1;
        if (calls.contains(new ApiCall("ch.3.cfg.name", "DR-Tom 1"))) score += 1;
        if (calls.contains(new ApiCall("ch.4.cfg.name", "DR-Tom 2"))) score += 1;
        if (calls.contains(new ApiCall("ch.5.cfg.name", "DR-Overheads L"))) score += 1;
        if (calls.contains(new ApiCall("ch.8.cfg.name", "DR-Overheads R"))) score += 1;

        // Sixth prompt: Specific kick rename (1 point)
        if (calls.contains(new ApiCall("ch.0.cfg.name", "DR-Kick-In"))) score += 1;

        logger.info("Complex validation score: {}/{}", score, maxScore);
        return score / maxScore;
    }

    private double calculateOverallScore(TestResults tr) {
        double reliabilityScore = tr.getSuccessRate() * 50;
        double accuracyScore = tr.getAverageAccuracy() * 30;
        double speedScore = tr.getAverageTime() > 0 ?
                Math.min(15.0, 15000.0 / tr.getAverageTime()) : 0;
        double costScore = tr.getAverageCost() > 0 ?
                Math.min(5.0, 0.05 / tr.getAverageCost()) : 5.0;

        return reliabilityScore + accuracyScore + speedScore + costScore;
    }
}