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
            @ToolParam(description = "API path to get (e.g., ch.0.cfg.name)")
            String path) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        callCount.incrementAndGet();
        Object value = consoleState.get(path);
        return new Response(path, value, "SUCCESS");
    }

    @Tool(description = "Make a single API call to the Mixing Station console. Use 0-based channel indexing.")
    public Response setSingleParameter(
            @ToolParam(description = "API call with path and value")
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
            @ToolParam(description = "List of API calls to execute in order")
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

    public void reset() {
        callCount.set(0);
        capturedApiCalls.clear();
        consoleState.clear();
    }

    // Response record for tool results
    public record Response(String path, Object value, String status) {
    }
}

