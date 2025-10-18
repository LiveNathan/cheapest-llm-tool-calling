package dev.nathanlively.cheapest_llm_tool_calling;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class OllamaTestContainerProvider extends LlmProvider {
    private static final Logger logger = LoggerFactory.getLogger(OllamaTestContainerProvider.class);
    private static final String DEFAULT_OLLAMA_IMAGE = "ollama/ollama";

    // Models to test - ordered from smallest to largest
    private static final List<String> OLLAMA_MODELS = new ArrayList<>(List.of(
            "qwen2.5:1.5b"        // Smallest effective model for tool calling
//            "llama3.1:8b",         // Best overall for tool calling
//            "qwen2.5:3b",
//            "qwen2.5:7b",
//            "gemma3:4b",
//            "gemma3:12b"
    ));

    private static final Map<String, OllamaContainer> containerCache = new ConcurrentHashMap<>();
    @Nullable
    private OllamaContainer activeContainer;

    public OllamaTestContainerProvider() {
        super("Ollama", "OLLAMA_LOCAL", OLLAMA_MODELS);
    }

    @Override
    public boolean isAvailable() {
        // Always available since we're using testcontainers
        return true;
    }

    @Override
    public ChatClient createChatClient(String model, TestScenario scenario) {
        ensureContainerRunning(model);

        String baseUrl = Objects.requireNonNull(activeContainer).getEndpoint();
        logger.info("Creating Ollama chat client for model {} at {}", model, baseUrl);

        OllamaApi ollamaApi = OllamaApi.builder().baseUrl(baseUrl).build();
        OllamaChatOptions chatOptions = OllamaChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .numCtx(8192)  // Context window size
                .build();

        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(chatOptions)
                .build();

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId("test-" + System.currentTimeMillis())
                                .build(),
                        new SimpleLoggerAdvisor()
                )
                .build();
    }

    private void ensureContainerRunning(String model) {

        if (containerCache.containsKey(model)) {
            activeContainer = containerCache.get(model);
            if (!activeContainer.isRunning()) {
                activeContainer.start();
            }
            return;
        }

        logger.info("Starting Ollama container for model: {}", model);

        OllamaContainer container = new OllamaContainer(
                DockerImageName.parse(DEFAULT_OLLAMA_IMAGE)
                        .asCompatibleSubstituteFor("ollama/ollama")
        );

        container.withStartupTimeout(Duration.ofMinutes(5));
        container.start();

        // Pull the model
        pullModel(container, model);

        containerCache.put(model, container);
        activeContainer = container;
    }

    protected void pullModel(OllamaContainer container, String model) {
        logger.info("Pulling model {} - this may take several minutes...", model);

        try {
            var result = container.execInContainer("ollama", "pull", model);
            if (result.getExitCode() != 0) {
                logger.error("Failed to pull model {}: {}", model, result.getStderr());
                throw new RuntimeException("Failed to pull model: " + model);
            }
            logger.info("Successfully pulled model: {}", model);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error pulling model: " + model, e);
        }
    }

    public void cleanup() {
        containerCache.values().forEach(container -> {
            if (container.isRunning()) {
                logger.info("Stopping Ollama container");
                container.stop();
            }
        });
        containerCache.clear();
    }

    @Override
    public String getFullModelName(String model) {
        return "ollama/" + model.replace(":", "-");
    }
}