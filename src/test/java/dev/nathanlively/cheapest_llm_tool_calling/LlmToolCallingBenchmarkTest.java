package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MISTRALAI_API_KEY", matches = ".+")
public class LlmToolCallingBenchmarkTest {
    private static final Logger logger = LoggerFactory.getLogger(LlmToolCallingBenchmarkTest.class);
    private static final int TEST_ITERATIONS = 1;
    private static final int TIMEOUT_SECONDS = 60;

    private static final String MIXING_CONSOLE_SYSTEM_PROMPT = """
            You are controlling a mixing console API that uses 0-based indexing.
            When users refer to "channel 1", you must use index 0 in the API (ch.0).
            When users refer to "channel 2", you must use index 1 in the API (ch.1).
            Always subtract 1 from the user's channel numbers to get the correct API index.
            
            Examples:
            - User says "channel 1" → use ch.0 in API
            - User says "channels 1-7" → use ch.0 through ch.6 in API
            - User says "channel 12" → use ch.11 in API
            """;

    private MockMixingConsoleService mockConsoleService;
    private MockWeatherService mockWeatherService;
    private BenchmarkRunner benchmarkRunner;

    static Stream<String> apiKeyProvider() {
        return Stream.of(
                "GROQ_API_KEY",
                "MISTRALAI_API_KEY",
                "DEEPSEEK_API_KEY"
        );
    }

    @BeforeEach
    void setUp() {
        mockConsoleService = new MockMixingConsoleService();
        mockWeatherService = new MockWeatherService();

        List<LlmProvider> providers = List.of(
                // OpenAI Proxy implementations
                new GroqProxyProvider(),
                new MistralProxyProvider(),
                new DeepseekProxyProvider(),

                // Native Spring AI implementations
                new MistralNativeProvider(),
                new DeepseekNativeProvider()
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
    void simpleChannelRenamingBenchmark() {
        TestScenario scenario = new TestScenario.Builder()
                .name("Simple Channel Renaming with Memory")
                .prompts("Rename channel 1 to Kick and channel 2 to Snare",
                        "Now change Kick to Kick-In and Snare to Snare-Top")
                .validation(this::validateSimpleChannelRenaming)
                .toolService(mockConsoleService)
                .systemPrompt(MIXING_CONSOLE_SYSTEM_PROMPT)
                .build();

        var results = benchmarkRunner.runBenchmark(scenario);
        results.printReport();
        results.determineWinner();
    }

    @Test
    void complexBandSetupBenchmark() {
        TestScenario scenario = new TestScenario.Builder()
                .name("Complex Band Setup")
                .prompts(List.of(
                        """
                                Set up drums on channels 1-7:
                                - Kick (ch 1), Snare (ch 2), Hi-hat (ch 3), Tom 1 (ch 4), Tom 2 (ch 5), Overheads L/R (ch 6-7)
                                - Apply compression to kick and snare
                                - High-pass all drum channels at 80Hz
                                """,
                        """
                                Now add the rhythm section and other instruments:
                                - Bass on ch 8: DI input, compression, high-pass at 40Hz
                                - Electric Guitar on ch 9: Amp mic, high-pass at 80Hz
                                - Keys stereo on ch 10-11: DI, high-pass at 40Hz
                                """,
                        """
                                Add vocals:
                                - Lead Vocal on ch 12: Compression, high-pass at 100Hz
                                - Backing Vocal on ch 13: High-pass at 100Hz
                                """,
                        """
                                Set up routing for the band we just configured:
                                - Create drum bus (bus 1) with all drums
                                - Send vocals to reverb (fx bus 1)
                                - Bass and lead vocal to wedge monitors (bus 2-3)
                                - Guitar and keys to IEMs (bus 4-5)
                                - All channels to main mix
                                """,
                        """
                                Apply color coding to everything:
                                - Drums: red (1)
                                - Bass: green (2)
                                - Guitar: blue (3)
                                - Keys: purple (4)
                                - Vocals: yellow (5)
                                """
                ))
                .validation(this::validateComplexBandSetup)
                .toolService(mockConsoleService)
                .systemPrompt(MIXING_CONSOLE_SYSTEM_PROMPT)
                .build();

        var results = benchmarkRunner.runBenchmark(scenario);
        results.printReport();
        results.determineWinner();
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

        var allResults = new java.util.HashMap<String, Double>();

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
    private double validateSimpleChannelRenaming() {
        List<ApiCall> calls = mockConsoleService.getCapturedApiCalls();
        logger.info("Calls: {}", calls);
        double score = 0.0;
        double maxScore = 4.0;

        if (calls.contains(new ApiCall("ch.0.cfg.name", "Kick"))) score += 1;
        if (calls.contains(new ApiCall("ch.0.cfg.name", "Kick-In"))) score += 1;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "Snare"))) score += 1;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "Snare-Top"))) score += 1;

        return score / maxScore;
    }

    private double validateComplexBandSetup() {
        var calls = mockConsoleService.getCapturedApiCalls();
        double score = 0.0;
        double maxScore = 20.0;

        // Channel names
        if (calls.contains(new ApiCall("ch.0.cfg.name", "Kick"))) score += 1;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "Snare"))) score += 1;
        if (calls.contains(new ApiCall("ch.7.cfg.name", "Bass"))) score += 1;
        if (calls.contains(new ApiCall("ch.11.cfg.name", "Lead Vocal"))) score += 1;

        // Colors
        if (calls.stream().anyMatch(c -> c.path().startsWith("ch.0.cfg.color") && c.value().equals(1))) score += 1;
        if (calls.stream().anyMatch(c -> c.path().startsWith("ch.7.cfg.color") && c.value().equals(2))) score += 1;

        // Compression
        if (calls.contains(new ApiCall("ch.0.dyn.on", true))) score += 1;
        if (calls.contains(new ApiCall("ch.1.dyn.on", true))) score += 1;
        if (calls.contains(new ApiCall("ch.11.dyn.on", true))) score += 1;

        // High-pass filters
        if (calls.stream().anyMatch(c -> c.path().equals("ch.0.peq.bands.0.freq") &&
                                         c.value().equals(80))) score += 1;
        if (calls.stream().anyMatch(c -> c.path().equals("ch.7.peq.bands.0.freq") &&
                                         c.value().equals(40))) score += 1;

        // Routing
        if (calls.stream().anyMatch(c -> c.path().contains("ch.11.mix.sends") &&
                                         c.path().contains(".on"))) score += 2;
        if (calls.stream().anyMatch(c -> c.path().contains("ch.7.mix.sends") &&
                                         c.path().contains(".on"))) score += 2;

        long busConfigCalls = calls.stream()
                .filter(c -> c.path().contains(".mix.sends."))
                .count();
        if (busConfigCalls > 10) score += 5;

        return score / maxScore;
    }

    private TestScenario createSimpleScenario() {
        return new TestScenario.Builder()
                .name("Simple Channel Renaming")
                .prompts(
                        "Rename channel 1 to Kick and channel 2 to Snare",
                        "Now rename channel 3 to Hat and channel 4 to Tom"
                )
                .validation(this::validateSimpleChannelRenaming)
                .toolService(mockConsoleService)
                .systemPrompt(MIXING_CONSOLE_SYSTEM_PROMPT)
                .build();
    }

    private TestScenario createComplexScenario() {
        return new TestScenario.Builder()
                .name("Complex Band Setup")
                .prompts(
                        "Set up drums on channels 1-7: Kick, Snare, Hi-hat, Tom 1, Tom 2, Overheads L/R",
                        "Add bass on ch 8 and guitar on ch 9",
                        "Add lead vocal on ch 12"
                )
                .validation(this::validateComplexBandSetup)
                .toolService(mockConsoleService)
                .systemPrompt(MIXING_CONSOLE_SYSTEM_PROMPT)
                .build();
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