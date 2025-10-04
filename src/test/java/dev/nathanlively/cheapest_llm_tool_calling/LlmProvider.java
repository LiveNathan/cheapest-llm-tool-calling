package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

public abstract class LlmProvider {
    protected final String name;
    protected final String apiKeyEnvVar;
    protected final List<String> supportedModels;

    protected LlmProvider(String name, String apiKeyEnvVar, List<String> supportedModels) {
        this.name = name;
        this.apiKeyEnvVar = apiKeyEnvVar;
        this.supportedModels = supportedModels;
    }

    public String getName() {
        return name;
    }

    public List<String> getSupportedModels() {
        return supportedModels;
    }

    public String getFullModelName(String model) {
        return name.toLowerCase() + "/" + model;
    }

    public boolean isAvailable() {
        String apiKey = System.getenv(apiKeyEnvVar);
        return apiKey != null && !apiKey.isBlank();
    }

    public abstract ChatClient createChatClient(String model, TestScenario scenario);

    public LlmPricing.ModelPricing getPricing(String model) {
        String fullModelName = getFullModelName(model);
        var pricing = LlmPricing.PRICING.get(fullModelName);
        if (pricing != null) {
            return pricing;
        }
        String proxyModelName = fullModelName.replace("-native", "");
        return LlmPricing.PRICING.get(proxyModelName);
    }
}
