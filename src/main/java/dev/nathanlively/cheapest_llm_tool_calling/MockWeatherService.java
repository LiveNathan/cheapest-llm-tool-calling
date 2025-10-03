package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MockWeatherService {
    private final AtomicInteger callCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Integer> locationCallCounts = new ConcurrentHashMap<>();

    @Tool(description = "Get weather information for a specific location")
    public WeatherResponse getWeather(
            @ToolParam(description = "The city and state e.g. San Francisco, CA", required = true)
            String location,
            @ToolParam(description = "Temperature unit", required = true)
            Unit unit) {

        callCount.incrementAndGet();
        locationCallCounts.merge(location, 1, Integer::sum);

        double temperature = 0;
        if (location.contains("Paris")) {
            temperature = 15;
        } else if (location.contains("Tokyo")) {
            temperature = 10;
        } else if (location.contains("San Francisco")) {
            temperature = 30;
        }

        return new WeatherResponse(temperature, 15, 8, 12, 53, 45, unit);
    }

    public int getTotalCallCount() {
        return callCount.get();
    }

    public int getCallCountForLocation(String location) {
        return locationCallCounts.getOrDefault(location, 0);
    }

    public void reset() {
        callCount.set(0);
        locationCallCounts.clear();
    }

    public Map<String, Integer> getLocationCallCounts() {
        return new HashMap<>(locationCallCounts);
    }

    // Keep existing enums and records unchanged
    public enum Unit {
        C("metric"),
        F("imperial");
        public final String unitName;
        Unit(String text) {
            this.unitName = text;
        }
    }

    public record WeatherResponse(
            double temp,
            double feels_like,
            double temp_min,
            double temp_max,
            int pressure,
            int humidity,
            Unit unit) {
    }
}