package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.List;

public class DeepseekProvider extends LlmProvider {
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final List<String> DEEPSEEK_MODELS = List.of("deepseek-chat");

    public DeepseekProvider() {
        super("Deepseek", DEEPSEEK_BASE_URL, "DEEPSEEK_API_KEY", DEEPSEEK_MODELS);
    }
}