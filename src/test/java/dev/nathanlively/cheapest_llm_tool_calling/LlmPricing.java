package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.Map;

public class LlmPricing {

    public static final Map<String, ModelPricing> PRICING = Map.of(
            "llama-3.1-8b-instant", new ModelPricing(0.05, 0.08, true, 840),
            "llama-3-8b-tool-use-preview", new ModelPricing(0.05, 0.08, true, 1345),
            "llama-4-scout-preview", new ModelPricing(0.11, 0.34, true, 594),
            "llama-3.3-70b-versatile", new ModelPricing(0.59, 0.79, true, 394),
            "qwen3-32b-preview", new ModelPricing(0.29, 0.59, true, 662)
    );

    public record ModelPricing(
            double inputPricePerMillion,
            double outputPricePerMillion,
            boolean supportsToolCalling,
            Integer tokensPerSecond) {

        public double calculateCost(int inputTokens, int outputTokens) {
            return (inputTokens * inputPricePerMillion / 1_000_000.0) +
                   (outputTokens * outputPricePerMillion / 1_000_000.0);
        }
    }
}