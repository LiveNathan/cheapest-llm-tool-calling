package dev.nathanlively.cheapest_llm_tool_calling;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;
@Testcontainers
@SpringBootTest
public class OllamaTestcontainerTest {
    @Bean
    OllamaContainer mongoDbContainer() {
        return new OllamaContainer(DockerImageName.parse("ollama/ollama"));
    }

    @Container
    static OllamaContainer<?> neo4j = new OllamaContainer<>("neo4j:5");
}
