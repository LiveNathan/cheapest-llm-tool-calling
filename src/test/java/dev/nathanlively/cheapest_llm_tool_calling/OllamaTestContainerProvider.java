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
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.testcontainers.ollama.OllamaContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OllamaTestContainerProvider extends LlmProvider {
    private static final Logger logger = LoggerFactory.getLogger(OllamaTestContainerProvider.class);
    private static final String OLLAMA_IMAGE = "ollama/ollama";

    // Models to test - ordered from smallest to largest
    private static final List<String> OLLAMA_MODELS = new ArrayList<>(List.of(
//            "qwen3:0.6b",        // Smallest effective model for tool calling
//            "qwen3:1.7b",
//            "qwen3:4b",
//            "qwen3:8b",
//            "llama3.2:1b",
//            "llama3.2:3b",
//            "llama3.1:8b",
            "orieg/gemma3-tools:1b",
//            "orieg/gemma3-tools:4b",
//            "orieg/gemma3-tools:1b-it-qat",
//            "orieg/gemma3-tools:4b-it-qat",
            "okamototk/gemma3-tools:1b",
//            "okamototk/gemma3-tools:4b",
            "phi4-mini:3.8b"
    ));

    @Nullable
    private static OllamaContainer container;
    @Nullable
    private static OllamaApi ollamaApi;
    @Nullable
    private static OllamaModelManager modelManager;

    public OllamaTestContainerProvider() {
        super("Ollama", "OLLAMA_LOCAL", OLLAMA_MODELS);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private void ensureContainerStarted() {
        if (container == null) {
            logger.info("Starting Ollama container...");
            container = new OllamaContainer(OLLAMA_IMAGE);
            container.withReuse(true);
            container.start();

            String baseUrl = container.getEndpoint();
            ollamaApi = OllamaApi.builder().baseUrl(baseUrl).build();
            modelManager = new OllamaModelManager(ollamaApi);

            logger.info("Ollama container started at: {}", baseUrl);
        }
    }

    @Override
    public ChatClient createChatClient(String model, TestScenario scenario) {
        ensureContainerStarted();

        logger.info("Ensuring model {} is available...", model);
        Objects.requireNonNull(modelManager).pullModel(model, PullModelStrategy.WHEN_MISSING);

        OllamaChatOptions chatOptions = OllamaChatOptions.builder()
                .model(model)
                .temperature(0.1)
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

    public static void cleanup() {
        if (container != null && container.isRunning()) {
            logger.info("Stopping Ollama container...");
            container.stop();
            container = null;
            ollamaApi = null;
            modelManager = null;
        }
    }

    @Override
    public String getFullModelName(String model) {
        return "ollama/" + model.replace(":", "-");
    }
}