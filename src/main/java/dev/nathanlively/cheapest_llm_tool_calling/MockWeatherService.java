package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
public class MockWeatherService {
    private final AtomicInteger callCount = new AtomicInteger(0);

    @Tool(description = "Get weather information for a specific location")
    public WeatherResponse getWeather(
            @ToolParam(description = "The city and state e.g. San Francisco, CA")
            String location,
            @ToolParam(description = "Temperature unit")
            Unit unit) {

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        callCount.incrementAndGet();

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

    public void reset() {
        callCount.set(0);
    }

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