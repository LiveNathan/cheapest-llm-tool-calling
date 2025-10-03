package dev.nathanlively.cheapest_llm_tool_calling;

// API Call record - matches your production structure
public record ApiCall(String path, Object value) {
    @Override
    public String toString() {
        return String.format("ApiCall{path='%s', value=%s}", path, value);
    }
}
