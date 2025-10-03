package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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

    // Models to test
    private static final String[] TEST_MODELS = {
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile"
    };

    private static final int TEST_ITERATIONS = 3; // Run each test multiple times for reliability

    private MockMixingConsoleService mockConsoleService;

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
        logger.info("=== SIMPLE TEST: Channel Renaming ===");
        String prompt = "Rename channels 1-4 to Kick, Snare, Hat, and Tom";

        Map<String, TestResults> results = new HashMap<>();

        for (String model : TEST_MODELS) {
            if (!LlmPricing.PRICING.containsKey(model)) {
                logger.warn("Skipping model {} - pricing not configured", model);
                continue;
            }

            TestResults modelResults = runTestIterations(model, prompt, this::validateSimpleChannelRenaming);
            results.put(model, modelResults);
        }

        printComparisonReport("Simple Channel Renaming", results);
        determineWinner(results);
    }

    @Test
    void complexBandSetupTest() {
        logger.info("=== COMPLEX TEST: Full Band Setup ===");
        String prompt = """
                Set up a 5-piece band with the following configuration:
                - Drums: Kick (ch 1), Snare (ch 2), Hi-hat (ch 3), Tom 1 (ch 4), Tom 2 (ch 5),
                  Overheads L/R (ch 6-7). Apply compression to kick and snare. High-pass all at 80Hz.
                - Bass (ch 8): DI input, compression, high-pass at 40Hz
                - Electric Guitar (ch 9): Amp mic, high-pass at 80Hz
                - Keys stereo (ch 10-11): DI, high-pass at 40Hz
                - Lead Vocal (ch 12): Compression, high-pass at 100Hz
                - Backing Vocal (ch 13): High-pass at 100Hz
                
                Routing:
                - Create drum bus (bus 1) with all drums
                - Send vocals to reverb (fx bus 1)
                - Bass and lead vocal to wedge monitors (bus 2-3)
                - Guitar and keys to IEMs (bus 4-5)
                - All channels to main mix
                
                Use color coding: drums=red(1), bass=green(2), guitar=blue(3),
                keys=purple(4), vocals=yellow(5)
                """;

        Map<String, TestResults> results = new HashMap<>();

        for (String model : TEST_MODELS) {
            if (!LlmPricing.PRICING.containsKey(model)) {
                logger.warn("Skipping model {} - pricing not configured", model);
                continue;
            }

            TestResults modelResults = runTestIterations(model, prompt, this::validateComplexBandSetup);
            results.put(model, modelResults);
        }

        printComparisonReport("Complex Band Setup", results);
        determineWinner(results);
    }

    private TestResults runTestIterations(String model, String prompt, ValidationCallback validation) {
        logger.info("Testing model: {}", model);
        TestResults results = new TestResults(model);

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            logger.info("  Iteration {}/{}", i + 1, TEST_ITERATIONS);
            TestRun run = executeSingleTest(model, prompt, validation);
            results.addRun(run);

            // Reset service between runs
            mockConsoleService.reset();

            // Add a delay to avoid rate limiting
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return results;
    }

    private TestRun executeSingleTest(String model, String prompt, ValidationCallback validation) {
        TestRun run = new TestRun();

        try {
            // Create model-specific client
            var chatModel = OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl(GROQ_BASE_URL)
                            .apiKey(System.getenv("GROQ_API_KEY"))
                            .build())
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model)
                            .build())
                    .build();

            var chatClient = ChatClient.builder(chatModel).build();

            // Start timing
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            // Execute request
            var response = chatClient.prompt()
                    .user(prompt)
                    .tools(mockConsoleService)
                    .call()
                    .chatClientResponse();

            stopWatch.stop();
            run.executionTimeMs = stopWatch.getTotalTimeMillis();

            // Validate response
            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse != null && chatResponse.getMetadata().getUsage() != null) {
                var usage = chatResponse.getMetadata().getUsage();
                run.promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                run.completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

                // Calculate cost
                var pricing = LlmPricing.PRICING.get(model);
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
        }

        return run;
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

        // Filter out models with 0% success rate - they cannot win
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

            // Create breakdown for debugging
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

    // Helper classes
    private static class TestResults {
        final String modelName;
        final List<TestRun> runs = new ArrayList<>();
        final Set<String> errors = new HashSet<>();

        TestResults(String modelName) {
            this.modelName = modelName;
        }

        void addRun(TestRun run) {
            runs.add(run);
            if (run.error != null) {
                errors.add(run.error);
            }
        }

        double getAverageTime() {
            return runs.stream()
                    .mapToLong(r -> r.executionTimeMs)
                    .average()
                    .orElse(0);
        }

        double getSuccessRate() {
            long successful = runs.stream().filter(r -> r.success).count();
            return runs.isEmpty() ? 0 : (double) successful / runs.size();
        }

        double getAverageAccuracy() {
            return runs.stream()
                    .filter(r -> r.success)
                    .mapToDouble(r -> r.accuracyScore)
                    .average()
                    .orElse(0);
        }

        double getAverageCost() {
            return runs.stream()
                    .mapToDouble(r -> r.cost)
                    .average()
                    .orElse(0);
        }

        double getAverageTokens() {
            return runs.stream()
                    .mapToInt(r -> r.promptTokens + r.completionTokens)
                    .average()
                    .orElse(0);
        }

        double getAverageToolCalls() {
            return runs.stream()
                    .mapToInt(r -> r.toolCallsMade)
                    .average()
                    .orElse(0);
        }
    }

    private static class TestRun {
        long executionTimeMs;
        boolean success;
        String error;
        int promptTokens;
        int completionTokens;
        double cost;
        int toolCallsMade;
        double accuracyScore;
    }
}