package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.List;

public class MistralProxyProvider extends OpenAiProxyProvider {
    private static final String MISTRAL_BASE_URL = "https://api.mistral.ai";
    private static final List<String> MISTRAL_MODELS = List.of(
            "mistral-small-latest"
    );

    public MistralProxyProvider() {
        super("MistralProxy", MISTRAL_BASE_URL, "MISTRALAI_API_KEY", MISTRAL_MODELS);
    }

    @Override
    public String getFullModelName(String model) {
        return "mistral/" + model;  // Keep same pricing key
    }
}
