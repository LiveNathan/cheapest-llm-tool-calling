package dev.nathanlively.cheapest_llm_tool_calling;

import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import dev.nathanlively.cheapest_llm_tool_calling.embabel.WeatherAgent;
import dev.nathanlively.cheapest_llm_tool_calling.embabel.WeatherAgent.FormattedWeatherReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify Embabel Weather Agent integration works.
 * This demonstrates GOAP (Goal-Oriented Action Planning) in action.
 */
@SpringBootTest(classes = WeatherAgentSimpleTest.TestConfig.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class WeatherAgentSimpleTest {
    private static final Logger logger = LoggerFactory.getLogger(WeatherAgentSimpleTest.class);

    @Autowired
    private AgentPlatform agentPlatform;

    @Autowired
    private MockWeatherService weatherService;

    @BeforeEach
    void setUp() {
        weatherService.reset();
    }

    @Test
    void testSimpleWeatherQuery() {
        logger.info("\n=== Testing Simple Weather Query with GOAP ===");

        // User input
        String userQuery = "What's the weather in Tokyo?";
        UserInput userInput = new UserInput(userQuery, Instant.now());

        // Create agent invocation
        AgentInvocation<FormattedWeatherReport> invocation =
            AgentInvocation.create(agentPlatform, FormattedWeatherReport.class);

        // Let GOAP plan and execute the actions
        logger.info("Starting GOAP planning for: {}", userQuery);
        FormattedWeatherReport result = invocation.invoke(userInput);

        // Verify results
        assertThat(result).isNotNull();
        assertThat(result.report()).isNotBlank();

        // Check that the weather service was actually called
        int toolCalls = weatherService.getTotalCallCount();
        logger.info("Weather service was called {} times", toolCalls);
        assertThat(toolCalls).isGreaterThan(0);

        // Check the formatted report
        logger.info("Weather report: {}", result.report());
        assertThat(result.toolsUsed()).isTrue();
        assertThat(result.report()).containsIgnoringCase("tokyo");
    }

    @Test
    void testWeatherWithExplicitUnit() {
        logger.info("\n=== Testing Weather Query with Explicit Unit ===");

        String userQuery = "Tell me the weather in Paris in Celsius";
        UserInput userInput = new UserInput(userQuery, Instant.now());

        AgentInvocation<FormattedWeatherReport> invocation =
            AgentInvocation.create(agentPlatform, FormattedWeatherReport.class);

        FormattedWeatherReport result = invocation.invoke(userInput);

        assertThat(result).isNotNull();
        assertThat(result.report()).isNotBlank();

        logger.info("Report: {}", result.report());
        assertThat(result.report()).containsIgnoringCase("paris");
        assertThat(result.toolsUsed()).isTrue();
    }

    @Test
    void testQuickPathForKnownLocation() {
        logger.info("\n=== Testing Quick Path for San Francisco ===");

        String userQuery = "weather in san francisco";
        UserInput userInput = new UserInput(userQuery, Instant.now());

        AgentInvocation<FormattedWeatherReport> invocation =
            AgentInvocation.create(agentPlatform, FormattedWeatherReport.class);

        FormattedWeatherReport result = invocation.invoke(userInput);

        assertThat(result).isNotNull();
        assertThat(result.report()).isNotBlank();

        // Should still call the weather service
        assertThat(weatherService.getTotalCallCount()).isGreaterThan(0);
    }

    @Test
    void testToolEnforcementValidation() {
        logger.info("\n=== Testing Tool Enforcement ===");

        // This test verifies that our agent always uses tools for weather
        for (int i = 0; i < 3; i++) {
            weatherService.reset();

            UserInput userInput = new UserInput("Weather in Berlin?", Instant.now());

            AgentInvocation<FormattedWeatherReport> invocation =
                AgentInvocation.create(agentPlatform, FormattedWeatherReport.class);

            FormattedWeatherReport result = invocation.invoke(userInput);

            assertThat(result).isNotNull();
            assertThat(result.report()).isNotBlank();

            // Every successful weather query MUST call the weather service
            int toolCalls = weatherService.getTotalCallCount();
            assertThat(toolCalls)
                    .as("Iteration %d: Weather service must be called", i)
                    .isGreaterThan(0);

            logger.info("Iteration {}: {} tool calls", i, toolCalls);
        }
    }

    /**
     * Spring configuration for the test.
     * Enables Embabel agents and provides necessary beans.
     */
    @Configuration
    @EnableAgents
    static class TestConfig {

        @Bean
        public MockWeatherService mockWeatherService() {
            return new MockWeatherService();
        }

        @Bean
        public WeatherAgent weatherAgent(MockWeatherService weatherService) {
            return new WeatherAgent(weatherService, true);
        }
    }
}
