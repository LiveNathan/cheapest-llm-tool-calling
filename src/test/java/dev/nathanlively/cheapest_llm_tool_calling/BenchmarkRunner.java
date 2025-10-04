package dev.nathanlively.cheapest_llm_tool_calling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StopWatch;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class BenchmarkRunner {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);
    private static final long RATE_LIMIT_DELAY_MS = 10_000;
    private static final int MAX_RETRIES = 3;
    private static final String HEADER_MESSAGE =
            String.format("%-35s %10s %10s %10s %12s %10s %10s",
                    "Provider/Model", "Avg Time", "Success", "Accuracy", "Avg Cost", "Tokens", "Calls");

    private final List<LlmProvider> providers;
    private final int iterations;

    private final int timeoutSeconds;

    public BenchmarkRunner(List<LlmProvider> providers, int iterations, int timeoutSeconds) {
        this.providers = providers;
        this.iterations = iterations;
        this.timeoutSeconds = timeoutSeconds;
    }

    public BenchmarkResults runBenchmark(TestScenario scenario) {
        logger.info("\n=== BENCHMARK: {} ===", scenario.getName());

        BenchmarkResults results = new BenchmarkResults(scenario.getName());

        for (LlmProvider provider : providers) {
            if (!provider.isAvailable()) {
                logger.warn("Skipping {} - API key not configured ({})",
                        provider.getName(), "Set " + provider.apiKeyEnvVar);
                continue;
            }

            for (String model : provider.getSupportedModels()) {
                String fullModelName = provider.getFullModelName(model);

                var pricing = provider.getPricing(model);
                if (pricing == null) {
                    logger.warn("Skipping {} - pricing not configured", fullModelName);
                    continue;
                }
                if (!pricing.supportsToolCalling()) {
                    logger.info("Skipping {} - does not support tool calling", fullModelName);
                    continue;
                }

                TestResults modelResults = runTestIterations(provider, model, scenario);
                results.addResult(fullModelName, modelResults);
            }
        }

        return results;
    }

    private TestResults runTestIterations(LlmProvider provider, String model, TestScenario scenario) {
        String fullModelName = provider.getFullModelName(model);
        logger.info("Testing: {}", fullModelName);
        TestResults results = new TestResults(fullModelName);

        for (int i = 0; i < iterations; i++) {
            logger.info("  Iteration {}/{}", i + 1, iterations);

            TestRun run = null;
            int backoffMs = 1000;

            for (int retry = 0; retry < MAX_RETRIES; retry++) {
                try {
                    run = executeSingleTest(provider, model, scenario);
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

            // Reset tool service state if it has a reset method
            resetToolService(scenario.getToolService());

            if (i < iterations - 1) {
                try {
                    logger.info("  Waiting {} seconds before next iteration...", RATE_LIMIT_DELAY_MS / 1000);
                    Thread.sleep(RATE_LIMIT_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return results;
    }

    private TestRun executeSingleTest(LlmProvider provider, String model, TestScenario scenario) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<TestRun> future = executor.submit(() -> {
            TestRun run = new TestRun();

            try {
                ChatClient chatClient = provider.createChatClient(model, scenario);

                StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                // Remove async for debugging
                ChatClientResponse lastResponse = null;
                for (int i = 0; i < scenario.getPrompts().size(); i++) {
                    String prompt = scenario.getPrompts().get(i);
                    logger.info("    Sending prompt {}/{}: {}",
                            i + 1, scenario.getPrompts().size(),
                            prompt.substring(0, Math.min(50, prompt.length())));

                    try {
                        lastResponse = chatClient.prompt()
                                .system(scenario.getSystemPrompt())
                                .user(prompt)
                                .tools(scenario.getToolService())
                                .call()
                                .chatClientResponse();

                        logger.info("    Received response for prompt {}", i + 1);

                        // Log tool calls made
                        int callsMade = getToolCallCount(scenario.getToolService());
                        logger.info("    Tool calls so far: {}", callsMade);

                    } catch (Exception e) {
                        logger.error("    Error on prompt {}: {}", i + 1, e.getMessage());
                        throw e;
                    }
                }

                stopWatch.stop();
                run.executionTimeMs = stopWatch.getTotalTimeMillis();

                ChatResponse chatResponse = Objects.requireNonNull(lastResponse).chatResponse();

                if (chatResponse != null && chatResponse.getMetadata().getUsage() != null) {
                    var usage = chatResponse.getMetadata().getUsage();
                    run.promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                    run.completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

                    var pricing = provider.getPricing(model);
                    run.cost = pricing.calculateCost(run.promptTokens, run.completionTokens);
                }

                run.accuracyScore = scenario.getValidation().validate();
                run.success = run.accuracyScore > 0;
                run.toolCallsMade = getToolCallCount(scenario.getToolService());

            } catch (Exception e) {
                logger.error("Error in test run: {}", e.getMessage());
                run.success = false;
                run.error = e.getMessage();
            }

            return run;
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); // Interrupt the task
            logger.error("Test run timed out after {} seconds", timeoutSeconds);
            TestRun run = new TestRun();
            run.success = false;
            run.error = "Timeout after " + timeoutSeconds + " seconds";
            return run;
        } catch (Exception e) {
            TestRun run = new TestRun();
            run.success = false;
            run.error = e.getMessage();
            return run;
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean isRateLimitError(String error) {
        return error != null && (
                error.contains("rate_limit_exceeded") ||
                error.contains("429") ||
                error.contains("Too Many Requests")
        );
    }

    private void resetToolService(Object toolService) {
        try {
            var resetMethod = toolService.getClass().getMethod("reset");
            resetMethod.invoke(toolService);
        } catch (Exception e) {
            // Tool service doesn't have a reset method, that's OK
        }
    }

    private int getToolCallCount(Object toolService) {
        try {
            var method = toolService.getClass().getMethod("getTotalCallCount");
            return (int) method.invoke(toolService);
        } catch (Exception e) {
            return 0;
        }
    }

    public static class BenchmarkResults {
        private final String scenarioName;
        private final Map<String, TestResults> results = new LinkedHashMap<>();

        public BenchmarkResults(String scenarioName) {
            this.scenarioName = scenarioName;
        }

        public void addResult(String modelName, TestResults testResults) {
            results.put(modelName, testResults);
        }

        public void printReport() {
            logger.info("\n========================================");
            logger.info("BENCHMARK REPORT: {}", scenarioName);
            logger.info("========================================\n");

            logger.info(HEADER_MESSAGE);
            logger.info("{}", "-".repeat(100));

            for (var entry : results.entrySet()) {
                TestResults tr = entry.getValue();
                logger.info("{} {}ms {}% {}% ${} {} {}",
                        String.format("%-35s", tr.modelName),
                        String.format("%9.0f", tr.getAverageTime()),
                        String.format("%9.0f", tr.getSuccessRate() * 100),
                        String.format("%9.0f", tr.getAverageAccuracy() * 100),
                        String.format("%11.6f", tr.getAverageCost()),
                        String.format("%10.0f", tr.getAverageTokens()),
                        String.format("%10.0f", tr.getAverageToolCalls())
                );

                if (!tr.errors.isEmpty()) {
                    logger.info("    Errors: {}", String.join(", ", tr.errors));
                }
            }
        }

        public void determineWinner() {
            logger.info("\n=== WINNER DETERMINATION ===");

            Map<String, TestResults> viableModels = results.entrySet().stream()
                    .filter(entry -> entry.getValue().getSuccessRate() > 0)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (viableModels.isEmpty()) {
                logger.info("\n❌ NO WINNER: All models failed completely");
                return;
            }

            Map<String, Double> scores = new HashMap<>();
            Map<String, String> scoreBreakdowns = new HashMap<>();

            for (var entry : viableModels.entrySet()) {
                String model = entry.getKey();
                TestResults tr = entry.getValue();

                double reliabilityScore = tr.getSuccessRate() * 50;
                double accuracyScore = tr.getAverageAccuracy() * 30;
                double speedScore = tr.getAverageTime() > 0 ?
                        Math.min(15.0, 15000.0 / tr.getAverageTime()) : 0;
                double costScore = tr.getAverageCost() > 0 ?
                        Math.min(5.0, 0.05 / tr.getAverageCost()) : 5.0;

                double totalScore = reliabilityScore + accuracyScore + speedScore + costScore;
                scores.put(model, totalScore);

                scoreBreakdowns.put(model, String.format(
                        "reliability=%.1f, accuracy=%.1f, speed=%.1f, cost=%.1f",
                        reliabilityScore, accuracyScore, speedScore, costScore));
            }

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

            Set<String> disqualified = results.keySet().stream()
                    .filter(model -> !viableModels.containsKey(model))
                    .collect(Collectors.toSet());

            if (!disqualified.isEmpty()) {
                logger.info("\n❌ Disqualified (0% success): {}", String.join(", ", disqualified));
            }
        }

        public Map<String, TestResults> getResults() {
            return results;
        }
    }
}