package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.List;

public class DeepseekProxyProvider extends OpenAiProxyProvider {
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final List<String> DEEPSEEK_MODELS = List.of("deepseek-chat");

    public DeepseekProxyProvider() {
        super("DeepseekProxy", DEEPSEEK_BASE_URL, "DEEPSEEK_API_KEY", DEEPSEEK_MODELS);
    }

    @Override
    public String getFullModelName(String model) {
        return "deepseek/" + model;  // Keep same pricing key
    }
}
