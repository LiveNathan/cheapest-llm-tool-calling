package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class MockWeatherService {

    @Tool(description = "Get weather information for a specific location")
    public WeatherResponse getWeather(
            @ToolParam(description = "The city and state e.g. San Francisco, CA", required = true)
            String location,
            @ToolParam(description = "Temperature unit", required = true)
            Unit unit) {

        double temperature = 0;
        if (location.contains("Paris")) {
            temperature = 15;
        } else if (location.contains("Tokyo")) {
            temperature = 10;
        } else if (location.contains("San Francisco")) {
            temperature = 30;
        }

        return new WeatherResponse(temperature, 15, 20, 2, 53, 45, unit);
    }

    /**
     * Temperature units.
     */
    public enum Unit {

        /**
         * Celsius.
         */
        C("metric"),
        /**
         * Fahrenheit.
         */
        F("imperial");

        /**
         * Human readable unit name.
         */
        public final String unitName;

        Unit(String text) {
            this.unitName = text;
        }
    }

    /**
     * Weather Function response.
     */
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