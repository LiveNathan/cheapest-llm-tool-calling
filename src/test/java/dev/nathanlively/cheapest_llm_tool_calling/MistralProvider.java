package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.List;

public class MistralProvider extends LlmProvider {
    private static final String MISTRAL_BASE_URL = "https://api.mistral.ai";
    private static final List<String> MISTRAL_MODELS = List.of(
            "mistral-small-latest"
    );

    public MistralProvider() {
        super("Mistral", MISTRAL_BASE_URL, "MISTRALAI_API_KEY", MISTRAL_MODELS);
    }
}