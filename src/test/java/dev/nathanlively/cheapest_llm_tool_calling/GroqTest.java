package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.StopWatch;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
public class GroqTest {
    private static final Logger logger = LoggerFactory.getLogger(GroqTest.class);
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai";
    private static final String HEADER_MESSAGE =
            String.format("%-25s %10s %10s %10s %12s %10s %10s",
                    "Model", "Avg Time", "Success", "Accuracy", "Avg Cost", "Tokens", "Calls");
    private static final String[] TEST_MODELS = {
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile"
    };
    private static final int TEST_ITERATIONS = 1;
    private MockMixingConsoleService mockConsoleService;
    private static final String SYSTEM_PROMPT = """
            You are controlling a mixing console API that uses 0-based indexing.
            When users refer to "channel 1", you must use index 0 in the API (ch.0).
            When users refer to "channel 2", you must use index 1 in the API (ch.1).
            Always subtract 1 from the user's channel numbers to get the correct API index.
            
            Examples:
            - User says "channel 1" → use ch.0 in API
            - User says "channels 1-7" → use ch.0 through ch.6 in API
            - User says "channel 12" → use ch.11 in API
            """;

    @BeforeEach
    void setUp() {
        mockConsoleService = new MockMixingConsoleService();
    }

    @Test
    void weatherServiceSmokeTest() {
        MockWeatherService weatherService = new MockWeatherService();
        String model = "llama-3.1-8b-instant";

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(GROQ_BASE_URL)
                        .apiKey(System.getenv("GROQ_API_KEY"))
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
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

    @Test
    void simpleChannelRenamingTest() {
        logger.info("=== SIMPLE TEST: Channel Renaming with Memory ===");
        String prompt1 = "Rename channel 1 to Kick and channel 2 to Snare";
        String prompt2 = "Now rename channel 3 to Hat and channel 4 to Tom";
        Map<String, TestResults> results = new HashMap<>();
        for (String model : TEST_MODELS) {
            TestResults modelResults = runTestIterations(
                    model,
                    List.of(prompt1, prompt2),
                    this::validateSimpleChannelRenaming
            );
            results.put(model, modelResults);
        }

        printComparisonReport("Simple Channel Renaming", results);
        determineWinner(results);
    }

    @Test
    void complexBandSetupTest() {
        logger.info("=== COMPLEX TEST: Full Band Setup ===");

        List<String> prompts = List.of(
                // Turn 1: Drums setup
                """
                        Set up drums on channels 1-7:
                        - Kick (ch 1), Snare (ch 2), Hi-hat (ch 3), Tom 1 (ch 4), Tom 2 (ch 5), Overheads L/R (ch 6-7)
                        - Apply compression to kick and snare
                        - High-pass all drum channels at 80Hz
                        """,

                // Turn 2: Rhythm section and instruments
                """
                        Now add the rhythm section and other instruments:
                        - Bass on ch 8: DI input, compression, high-pass at 40Hz
                        - Electric Guitar on ch 9: Amp mic, high-pass at 80Hz
                        - Keys stereo on ch 10-11: DI, high-pass at 40Hz
                        """,

                // Turn 3: Vocals
                """
                        Add vocals:
                        - Lead Vocal on ch 12: Compression, high-pass at 100Hz
                        - Backing Vocal on ch 13: High-pass at 100Hz
                        """,

                // Turn 4: Routing and buses
                """
                        Set up routing for the band we just configured:
                - Create drum bus (bus 1) with all drums
                - Send vocals to reverb (fx bus 1)
                - Bass and lead vocal to wedge monitors (bus 2-3)
                - Guitar and keys to IEMs (bus 4-5)
                        - All channels to main mix""",

                // Turn 5: Color coding
                """
                        Apply color coding to everything:
                        - Drums: red (1)
                        - Bass: green (2)
                        - Guitar: blue (3)
                        - Keys: purple (4)
                        - Vocals: yellow (5)
                        """
        );

        Map<String, TestResults> results = new HashMap<>();

        for (String model : TEST_MODELS) {
            if (LlmPricing.getPricing(model) == null) {
                logger.warn("Skipping model {} - pricing not configured", model);
                continue;
            }

            TestResults modelResults = runTestIterations(model, prompts, this::validateComplexBandSetup);
            results.put(model, modelResults);
        }

        printComparisonReport("Complex Band Setup", results);
        determineWinner(results);
    }

    private static final long RATE_LIMIT_DELAY_MS = 10_000; // 10 seconds between tests
    private static final int MAX_RETRIES = 3;

    private TestResults runTestIterations(String model, List<String> prompts, ValidationCallback validation) {
        logger.info("Testing model: {}", model);
        TestResults results = new TestResults(model);

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            logger.info("  Iteration {}/{}", i + 1, TEST_ITERATIONS);

            TestRun run = null;
            int backoffMs = 1000;
            for (int retry = 0; retry < MAX_RETRIES; retry++) {
                try {
                    run = executeSingleTest(model, prompts, validation);
                    if (run.success || !isRateLimitError(run.error)) {
                        break;
                    }

                    logger.warn("Rate limit hit, waiting {} seconds before retry {}/{}",
                            backoffMs / 1000.0, retry + 1, MAX_RETRIES);
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            results.addRun(run);

            mockConsoleService.reset();
            try {
                logger.info("  Waiting {} seconds before next iteration...", RATE_LIMIT_DELAY_MS / 1000);
                Thread.sleep(RATE_LIMIT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return results;
    }

    private TestRun executeSingleTest(String model, List<String> prompts, ValidationCallback validation) {
        TestRun run = new TestRun();

        try {
            var chatModel = OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl(GROQ_BASE_URL)
                            .apiKey(System.getenv("GROQ_API_KEY"))
                            .build())
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model)
                            .build())
                    .build();
            ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
            ChatClient chatClient = ChatClient.builder(chatModel)
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .build();
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            ChatClientResponse lastResponse = null;
            for (String prompt : prompts) {
                lastResponse = chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(prompt)
                        .tools(mockConsoleService)
                        .call()
                        .chatClientResponse();
            }

            stopWatch.stop();
            run.executionTimeMs = stopWatch.getTotalTimeMillis();
            ChatResponse chatResponse = Objects.requireNonNull(lastResponse).chatResponse();

            if (chatResponse != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                run.promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                run.completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

                // Calculate cost
                var pricing = LlmPricing.getPricing(model);
                run.cost = pricing.calculateCost(run.promptTokens, run.completionTokens);
            }

            // Validate the actual API calls made
            run.accuracyScore = validation.validate();
            run.success = run.accuracyScore > 0;
            run.toolCallsMade = mockConsoleService.getTotalCallCount();

        } catch (Exception e) {
            logger.error("Error in test run: {}", e.getMessage());
            run.success = false;
            run.error = e.getMessage();

            // Check if it's a rate limit error
            if (isRateLimitError(e.getMessage())) {
                logger.warn("Rate limit exceeded for model: {}", model);
            }
        }

        return run;
    }

    private boolean isRateLimitError(String error) {
        return error != null && (
                error.contains("rate_limit_exceeded") ||
                error.contains("429") ||
                error.contains("Too Many Requests")
        );
    }

    private double validateSimpleChannelRenaming() {
        var calls = mockConsoleService.getCapturedApiCalls();
        double score = 0.0;
        double maxScore = 4.0;

        // Check if correct channels were renamed
        if (calls.contains(new ApiCall("ch.0.cfg.name", "Kick"))) score += 1;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "Snare"))) score += 1;
        if (calls.contains(new ApiCall("ch.2.cfg.name", "Hat"))) score += 1;
        if (calls.contains(new ApiCall("ch.3.cfg.name", "Tom"))) score += 1;

        return score / maxScore;
    }

    private double validateComplexBandSetup() {
        var calls = mockConsoleService.getCapturedApiCalls();
        double score = 0.0;
        double maxScore = 20.0; // Adjust based on validation criteria

        // Check channel names
        if (calls.contains(new ApiCall("ch.0.cfg.name", "Kick"))) score += 1;
        if (calls.contains(new ApiCall("ch.1.cfg.name", "Snare"))) score += 1;
        if (calls.contains(new ApiCall("ch.7.cfg.name", "Bass"))) score += 1;
        if (calls.contains(new ApiCall("ch.11.cfg.name", "Lead Vocal"))) score += 1;

        // Check colors
        if (calls.stream().anyMatch(c -> c.path().startsWith("ch.0.cfg.color") && c.value().equals(1))) score += 1;
        if (calls.stream().anyMatch(c -> c.path().startsWith("ch.7.cfg.color") && c.value().equals(2))) score += 1;

        // Check compression (dynamics)
        if (calls.contains(new ApiCall("ch.0.dyn.on", true))) score += 1;
        if (calls.contains(new ApiCall("ch.1.dyn.on", true))) score += 1;
        if (calls.contains(new ApiCall("ch.11.dyn.on", true))) score += 1;

        // Check high-pass filters
        if (calls.stream().anyMatch(c -> c.path().equals("ch.0.peq.bands.0.freq") &&
                                         c.value().equals(80))) score += 1;
        if (calls.stream().anyMatch(c -> c.path().equals("ch.7.peq.bands.0.freq") &&
                                         c.value().equals(40))) score += 1;

        // Check routing (sends)
        if (calls.stream().anyMatch(c -> c.path().contains("ch.11.mix.sends") &&
                                         c.path().contains(".on"))) score += 2; // Vocal to reverb
        if (calls.stream().anyMatch(c -> c.path().contains("ch.7.mix.sends") &&
                                         c.path().contains(".on"))) score += 2; // Bass to monitor

        // Check bus configuration
        long busConfigCalls = calls.stream()
                .filter(c -> c.path().contains(".mix.sends."))
                .count();
        if (busConfigCalls > 10) score += 5; // Significant routing setup

        return score / maxScore;
    }

    private void printComparisonReport(String testName, Map<String, TestResults> results) {
        logger.info("\n========================================");
        logger.info("COMPARISON REPORT: {}", testName);
        logger.info("========================================\n");

        // Print header
        logger.info(HEADER_MESSAGE);
        logger.info("{}", "-".repeat(90));

        // Print results for each model
        for (var entry : results.entrySet()) {
            TestResults tr = entry.getValue();
            logger.info("{} {}ms {}% {}% ${} {} {}",
                    String.format("%-25s", tr.modelName),
                    String.format("%9.0f", tr.getAverageTime()),
                    String.format("%9.0f", tr.getSuccessRate() * 100),
                    String.format("%9.0f", tr.getAverageAccuracy() * 100),
                    String.format("%11.6f", tr.getAverageCost()),
                    String.format("%10.0f", tr.getAverageTokens()),
                    String.format("%10.0f", tr.getAverageToolCalls())
            );            // Print any errors
            if (!tr.errors.isEmpty()) {
                logger.info("    Errors: {}", String.join(", ", tr.errors));
            }
        }
    }

    private void determineWinner(Map<String, TestResults> results) {
        logger.info("\n=== WINNER DETERMINATION ===");

        // Filter out models with a 0% success rate - they cannot win
        Map<String, TestResults> viableModels = results.entrySet().stream()
                .filter(entry -> entry.getValue().getSuccessRate() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (viableModels.isEmpty()) {
            logger.info("\n❌ NO WINNER: All models failed completely");
            logger.info("\nAll models had 0% success rate. Showing failure analysis:");
            results.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(
                            Comparator.comparingDouble(TestResults::getAverageTime)))
                    .forEach(e -> {
                        TestResults tr = e.getValue();
                        logger.info("  {}: 0% success, {} errors, avg time: {}ms",
                                e.getKey(), tr.errors.size(), String.format("%.0f", tr.getAverageTime()));
                    });
            return;
        }

        // Score each viable model on different criteria
        Map<String, Double> scores = new HashMap<>();
        Map<String, String> scoreBreakdowns = new HashMap<>();

        for (var entry : viableModels.entrySet()) {
            String model = entry.getKey();
            TestResults tr = entry.getValue();

            // RELIABILITY FIRST (50% weight) - most important for tool calling
            double reliabilityScore = tr.getSuccessRate() * 50;

            // ACCURACY (30% weight) - quality of successful calls
            double accuracyScore = tr.getAverageAccuracy() * 30;

            // SPEED (15% weight) - performance bonus, normalized to reasonable range
            double speedScore = tr.getAverageTime() > 0 ?
                    Math.min(15.0, 15000.0 / tr.getAverageTime()) : 0;

            // COST EFFICIENCY (5% weight) - minor factor, capped to prevent dominance
            double costScore = tr.getAverageCost() > 0 ?
                    Math.min(5.0, 0.05 / tr.getAverageCost()) : 5.0;

            double totalScore = reliabilityScore + accuracyScore + speedScore + costScore;
            scores.put(model, totalScore);

            // Create a breakdown for debugging
            scoreBreakdowns.put(model, String.format(
                    "reliability=%.1f, accuracy=%.1f, speed=%.1f, cost=%.1f",
                    reliabilityScore, accuracyScore, speedScore, costScore));
        }

        // Find winner
        String winner = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No winner");

        logger.info("\n🏆 WINNER: {}", winner);
        logger.info("\nFinal Scores (max 100):");
        scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> logger.info("  {}: {} ({})",
                        e.getKey(), String.format("%.2f", e.getValue()), scoreBreakdowns.get(e.getKey())));

        // Show models that were disqualified
        Set<String> disqualified = results.keySet().stream()
                .filter(model -> !viableModels.containsKey(model))
                .collect(Collectors.toSet());
        if (!disqualified.isEmpty()) {
            logger.info("\n❌ Disqualified (0% success): {}", String.join(", ", disqualified));
        }
    }

    @FunctionalInterface
    private interface ValidationCallback {
        double validate();
    }

}