package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.List;

public class OllamaCustomGemmaProvider extends OllamaTestContainerProvider {

    private static final List<String> CUSTOM_GEMMA_MODELS = List.of(
            "orieg/gemma3-tools:4b",
            "orieg/gemma3-tools:12b"
    );

    public OllamaCustomGemmaProvider() {
        super();
        // Override the models list
        this.supportedModels.clear();
        this.supportedModels.addAll(CUSTOM_GEMMA_MODELS);
    }

    @Override
    public String getFullModelName(String model) {
        // Format the model name for pricing lookup
        return "ollama/" + model.replace(":", "-").replace("/", "-");
    }
}