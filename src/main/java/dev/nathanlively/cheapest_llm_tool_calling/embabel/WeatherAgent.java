package dev.nathanlively.cheapest_llm_tool_calling.embabel;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import dev.nathanlively.cheapest_llm_tool_calling.MockWeatherService;
import dev.nathanlively.cheapest_llm_tool_calling.MockWeatherService.Unit;
import dev.nathanlively.cheapest_llm_tool_calling.MockWeatherService.WeatherResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Simple weather agent using Embabel's GOAP (Goal-Oriented Action Planning).
 * <p>
 * The agent breaks down weather queries into discrete actions that GOAP can plan:
 * 1. Extract location from user input
 * 2. Determine temperature unit preference
 * 3. Call weather service
 * 4. Format the response
 * <p>
 * GOAP will dynamically determine the action sequence based on available data.
 */
@Agent(
        name = "WeatherAgent",
        description = "Get weather information for any location",
        beanName = "weatherAgent"
)
public class WeatherAgent {
    private static final Logger logger = LoggerFactory.getLogger(WeatherAgent.class);

    private final MockWeatherService weatherService;
    private final boolean enforceToolUse;

    public WeatherAgent(MockWeatherService weatherService,
                        @Value("${weather.agent.enforce.tools:true}") boolean enforceToolUse) {
        this.weatherService = weatherService;
        this.enforceToolUse = enforceToolUse;
        logger.info("WeatherAgent initialized with tool enforcement: {}", Boolean.valueOf(enforceToolUse));
    }

    // Domain objects for type-safe data flow
    public record LocationQuery(String location, String originalRequest) {
    }

    public record UnitPreference(Unit unit, String reasoning) {
    }

    public record WeatherData(
            String location,
            double temperature,
            Unit unit,
            String conditions,
            int humidity
    ) {
    }

    public record FormattedWeatherReport(
            String report,
            boolean toolsUsed,
            int toolCallCount
    ) {
    }

    /**
     * Extract location from user input.
     * This is a precondition for getting weather.
     */
    @Action
    public LocationQuery extractLocation(UserInput userInput, OperationContext context) {
        logger.info("Extracting location from: {}", userInput.getContent());

        return context.ai()
                .withAutoLlm()
                .createObject("""
                                Extract the location from this user input.
                                If no specific location is mentioned, infer a reasonable default.

                                User input: %s

                                Provide the location in format "City, State" or "City, Country""".formatted(userInput.getContent()),
                        LocationQuery.class);
    }

    /**
     * Determine temperature unit preference.
     * Can be based on location or explicit user preference.
     */
    @Action
    public UnitPreference determineUnit(LocationQuery location, OperationContext context) {
        logger.info("Determining unit preference for: {}", location.location());

        return context.ai()
                .withAutoLlm()
                .createObject("""
                                Determine the appropriate temperature unit for this location.
                                Use Celsius for most countries, Fahrenheit for US locations.

                                Location: %s
                                Original request: %s

                                If the user specified a unit preference, honor that.""".formatted(location.location(), location.originalRequest()),
                        UnitPreference.class);
    }

    /**
     * Alternative action: User explicitly specifies Celsius
     */
    @Action
    public UnitPreference useCelsius(UserInput userInput) {
        if (userInput.getContent().toLowerCase().contains("celsius") ||
            userInput.getContent().toLowerCase().contains("metric")) {
            logger.info("User explicitly requested Celsius");
            return new UnitPreference(Unit.C, "User requested Celsius");
        }
        return null; // Action not applicable
    }

    /**
     * Alternative action: User explicitly specifies Fahrenheit
     */
    @Action
    public UnitPreference useFahrenheit(UserInput userInput) {
        if (userInput.getContent().toLowerCase().contains("fahrenheit") ||
            userInput.getContent().toLowerCase().contains("imperial")) {
            logger.info("User explicitly requested Fahrenheit");
            return new UnitPreference(Unit.F, "User requested Fahrenheit");
        }
        return null; // Action not applicable
    }

    /**
     * Call the weather service with location and unit.
     * This action requires both LocationQuery and UnitPreference as preconditions.
     */
    @Action
    public WeatherData fetchWeather(LocationQuery location, UnitPreference unitPref) {
        logger.info("Fetching weather for {} in {}", location.location(), unitPref.unit());

        int callCountBefore = weatherService.getTotalCallCount();

        // Call the actual weather service
        WeatherResponse response = weatherService.getWeather(
                location.location(),
                unitPref.unit()
        );

        int callCountAfter = weatherService.getTotalCallCount();
        logger.info("Weather service called. Total calls: {} -> {}", Integer.valueOf(callCountBefore), Integer.valueOf(callCountAfter));

        // Convert to our domain object
        return new WeatherData(
                location.location(),
                response.temp(),
                response.unit(),
                determineConditions(response),
                response.humidity()
        );
    }

    /**
     * Format the weather data into a user-friendly report.
     * This achieves the goal of providing weather information.
     */
    @AchievesGoal(description = "Weather information has been retrieved and formatted")
    @Action
    public FormattedWeatherReport formatWeatherReport(
            WeatherData weather,
            UserInput userInput,
            OperationContext context) {

        logger.info("Formatting weather report for {}", weather.location());

        // Use AI to create a natural language response

        String report = context.ai()
                .withAutoLlm()
                .generateText("""
                        Create a friendly weather report based on this data:

                        Location: %s
                        Temperature: %.1f°%s
                        Conditions: %s
                        Humidity: %d%%

                        Original user request: %s

                        Keep it concise and natural.
                        """.formatted(
                        weather.location(),
                        Double.valueOf(weather.temperature()),
                        weather.unit() == Unit.C ? "C" : "F",
                        weather.conditions(),
                        Integer.valueOf(weather.humidity()),
                        userInput.getContent()
                ));
        // Track if tools were used (they always should be for weather)
        int toolCalls = weatherService.getTotalCallCount();
        boolean toolsUsed = toolCalls > 0;

        if (enforceToolUse && !toolsUsed) {
            logger.warn("No weather service calls detected! This shouldn't happen.");
        }

        return new FormattedWeatherReport(report, toolsUsed, toolCalls);
    }

    /**
     * Alternative goal achievement: Quick weather for known locations.
     * This demonstrates GOAP can choose different paths.
     */
    @AchievesGoal(description = "Quick weather for common locations")
    @Action
    public FormattedWeatherReport quickWeatherForKnownLocation(UserInput userInput) {
        // Shortcut for very common queries
        String input = userInput.getContent().toLowerCase();

        if (input.contains("weather in san francisco")) {
            logger.info("Using quick path for San Francisco weather");

            WeatherResponse response = weatherService.getWeather("San Francisco, CA", Unit.F);

            return new FormattedWeatherReport(
                    String.format("The weather in San Francisco is currently %.0f°F with %d%% humidity.",
                            Double.valueOf(response.temp()), Integer.valueOf(response.humidity())),
                    true,
                    weatherService.getTotalCallCount()
            );
        }

        return null; // Action not applicable
    }

    private String determineConditions(WeatherResponse response) {
        // Simple logic to determine conditions based on temperature
        if (response.temp() < 10) {
            return "Cold";
        } else if (response.temp() < 20) {
            return "Cool";
        } else if (response.temp() < 30) {
            return "Warm";
        } else {
            return "Hot";
        }
    }
}