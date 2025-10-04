package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.Map;

public class LlmPricing {

    public static final Map<String, ModelPricing> PRICING = Map.ofEntries(
            // Groq models
            Map.entry("groq/llama-3.1-8b-instant", new ModelPricing(0.05, 0.08, true, 840)),
            Map.entry("groq/llama-3.3-70b-versatile", new ModelPricing(0.59, 0.79, true, 394)),
            Map.entry("groq/llama-3-8b-tool-use-preview", new ModelPricing(0.05, 0.08, true, 1345)),
            Map.entry("groq/llama-4-scout-preview", new ModelPricing(0.11, 0.34, true, 594)),
            Map.entry("groq/qwen3-32b-preview", new ModelPricing(0.29, 0.59, true, 662)),

            // Mistral models (pricing from Mistral's website as of Jan 2025)
            Map.entry("mistral/mistral-small-latest", new ModelPricing(0.20, 0.60, true, 200)),
            Map.entry("mistral/mistral-medium-latest", new ModelPricing(2.70, 8.10, true, 150)),
            Map.entry("mistral/mistral-large-latest", new ModelPricing(3.00, 9.00, true, 120)),
            Map.entry("mistral/pixtral-12b-2409", new ModelPricing(0.15, 0.15, false, 180)),
            Map.entry("mistral/codestral-2405", new ModelPricing(1.00, 3.00, true, 100)),

            Map.entry("deepseek/deepseek-chat", new ModelPricing(0.27, 1.10, true, 100)),

            Map.entry("google/gemini-2.0-flash", new ModelPricing(0.10, 0.40, true, 150)),
            Map.entry("google/gemini-2.0-flash-lite", new ModelPricing(0.075, 0.30, true, 150))
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