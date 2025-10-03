package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MockMixingConsoleService {
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final List<ApiCall> capturedApiCalls = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, Object> consoleState = new ConcurrentHashMap<>();

    @Tool(description = "Get current value of a mixer parameter")
    public Response getParameter(
            @ToolParam(description = "API path to get (e.g., ch.0.cfg.name)", required = true)
            String path) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        callCount.incrementAndGet();
        Object value = consoleState.get(path);
        return new Response(path, value, value != null ? "SUCCESS" : "NOT_FOUND");
    }

    @Tool(description = "Make a single API call to the Mixing Station console. Use 0-based channel indexing.")
    public Response setSingleParameter(
            @ToolParam(description = "API call with path and value", required = true)
            ApiCall apiCall) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        callCount.incrementAndGet();
        capturedApiCalls.add(apiCall);
        consoleState.put(apiCall.path(), apiCall.value());
        return new Response(apiCall.path(), apiCall.value(), "SUCCESS");
    }

    @Tool(description = "Make multiple API calls in sequence for complex mixer setup. Use 0-based channel indexing for all paths.")
    public List<Response> setMultipleParameters(
            @ToolParam(description = "List of API calls to execute in order", required = true)
            List<ApiCall> apiCalls) {

        List<Response> responses = new ArrayList<>();
        for (ApiCall call : apiCalls) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            callCount.incrementAndGet();
            capturedApiCalls.add(call);
            consoleState.put(call.path(), call.value());
            responses.add(new Response(call.path(), call.value(), "SUCCESS"));
        }
        return responses;
    }

    // Test helpers
    public int getTotalCallCount() {
        return callCount.get();
    }

    public List<ApiCall> getCapturedApiCalls() {
        return new ArrayList<>(capturedApiCalls);
    }

    public Map<String, Object> getConsoleState() {
        return new HashMap<>(consoleState);
    }

    public void reset() {
        callCount.set(0);
        capturedApiCalls.clear();
        consoleState.clear();
    }

    public boolean hasCall(String path, Object value) {
        return capturedApiCalls.contains(new ApiCall(path, value));
    }

    public long countCallsMatching(String pathPattern) {
        return capturedApiCalls.stream()
                .filter(call -> call.path().matches(pathPattern))
                .count();
    }

    // Response record for tool results
    public record Response(String path, Object value, String status) {
    }
}

// API Call record - matches your production structure
record ApiCall(String path, Object value) {
    @Override
    public String toString() {
        return String.format("ApiCall{path='%s', value=%s}", path, value);
    }
}