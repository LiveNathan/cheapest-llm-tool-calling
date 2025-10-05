package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.tool.annotation.ToolParam;

public record ApiCall(@ToolParam(description = "API path in format ch.{N}.cfg.name") String path,
                      @ToolParam(description = "Value to set for the API path") Object value) {
    @Override
    public String toString() {
        return String.format("ApiCall{path='%s', value=%s}", path, value);
    }
}
