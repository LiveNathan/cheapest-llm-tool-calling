package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Helper classes
class TestResults {
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
