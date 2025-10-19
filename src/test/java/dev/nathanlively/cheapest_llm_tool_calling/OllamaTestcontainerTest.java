package dev.nathanlively.cheapest_llm_tool_calling;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.ollama.OllamaContainer;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
public class OllamaTestcontainerTest {

    @Container
    @ServiceConnection
    static OllamaContainer ollamaContainer = new OllamaContainer("ollama/ollama");

    @Test
    void autoPullModelTest() {
        OllamaModel ollamaModel = OllamaModel.QWEN_2_5_3B;
        final String modelName = ollamaModel.getName();
        final String baseUrl = ollamaContainer.getEndpoint();
        final OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
        OllamaModelManager modelManager = new OllamaModelManager(api);
        modelManager.pullModel(modelName, PullModelStrategy.WHEN_MISSING);
        assertThat(modelManager.isModelAvailable(modelName)).isTrue();
        final OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(api)
                .build();
        String joke = ChatClient.create(chatModel)
                .prompt("Tell me a joke")
                .options(OllamaChatOptions.builder().model(ollamaModel).build())
                .call()
                .content();

        assertThat(joke).isNotEmpty();

        modelManager.deleteModel(modelName);
    }
}
