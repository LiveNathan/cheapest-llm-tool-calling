package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.List;

public class GroqProxyProvider extends OpenAiProxyProvider {
    private static final String GROQ_BASE_URL = "https://api.groq.com/openai";
    private static final List<String> GROQ_MODELS = List.of(
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile"
    );

    public GroqProxyProvider() {
        super("Groq", GROQ_BASE_URL, "GROQ_API_KEY", GROQ_MODELS);
    }
}
