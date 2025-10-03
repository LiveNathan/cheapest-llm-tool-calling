package dev.nathanlively.cheapest_llm_tool_calling;

class TestRun {
    long executionTimeMs;
    boolean success;
    String error;
    int promptTokens;
    int completionTokens;
    double cost;
    int toolCallsMade;
    double accuracyScore;
}
