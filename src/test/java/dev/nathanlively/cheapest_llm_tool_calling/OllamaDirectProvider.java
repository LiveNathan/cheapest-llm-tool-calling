package dev.nathanlively.cheapest_llm_tool_calling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.OllamaModelManager;
import org.springframework.ai.ollama.management.PullModelStrategy;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OllamaDirectProvider extends LlmProvider {
    private static final Logger logger = LoggerFactory.getLogger(OllamaDirectProvider.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private static final List<String> TOOL_CALLING_MODELS = List.of(
            "qwen2.5:0.5b",
            "qwen2.5:1.5b-instruct",
            "qwen3:0.6b",
            "qwen3:1.7b",
            "qwen3:4b",
            "qwen3:1.7b-q4_k_m",
            "qwen3:8b",
            "qwen2.5:3b-instruct",
            "llama3.2:1b",
            "llama3.2:1b-instruct-q8_0",
            "llama3.2:3b",
            "llama3.2:3b-instruct-q4_0",
            "llama3.1:8b",
            "orieg/gemma3-tools:1b",
            "orieg/gemma3-tools:4b",
            "okamototk/gemma3-tools:1b",
            "okamototk/gemma3-tools:4b",
            "phi4-mini:3.8b",
            "mistral:7b-instruct",
            "hermes3:8b",
            "nous-hermes2:10.7b-solar-q4_0",
            "command-r:35b",         // Command-R model with tool support
            "firefunction-v2:70b"    // Firefunction v2 - specialized for function calling
    );

    private final String baseUrl;
    private final PullModelStrategy pullStrategy;
    private final Set<String> pulledModels = new HashSet<>();
    private final Set<String> preExistingModels = new HashSet<>();
    private final boolean cleanupModelsAfterTest;
    private OllamaModelManager modelManager;

    public OllamaDirectProvider() {
        this(DEFAULT_BASE_URL, PullModelStrategy.WHEN_MISSING, true);
    }

    public OllamaDirectProvider(String baseUrl, PullModelStrategy pullStrategy) {
        this(baseUrl, pullStrategy, true);
    }

    public OllamaDirectProvider(String baseUrl, PullModelStrategy pullStrategy, boolean cleanupModelsAfterTest) {
        super("OllamaDirect", "OLLAMA_LOCAL", TOOL_CALLING_MODELS);
        this.baseUrl = baseUrl;
        this.pullStrategy = pullStrategy;
        this.cleanupModelsAfterTest = cleanupModelsAfterTest;
        logger.info("Initializing Ollama Direct Provider with base URL: {} (cleanup: {})",
                baseUrl, cleanupModelsAfterTest);

        // Initialize model manager and track existing models
        try {
            OllamaApi api = OllamaApi.builder().baseUrl(baseUrl).build();
            this.modelManager = new OllamaModelManager(api);

            // Track which models already exist (so we don't delete them)
            for (String model : TOOL_CALLING_MODELS) {
                if (modelManager.isModelAvailable(model)) {
                    preExistingModels.add(model);
                    logger.debug("Model {} already exists, will not delete after test", model);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not initialize model manager: {}", e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check if Ollama is running by attempting to create an API connection
            OllamaApi api = OllamaApi.builder()
                    .baseUrl(baseUrl)
                    .build();

            // Try to list models to verify connection
            api.listModels();
            logger.info("Successfully connected to Ollama at {}", baseUrl);
            return true;
        } catch (Exception e) {
            logger.warn("Ollama not available at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public ChatClient createChatClient(String model, TestScenario scenario) {
        logger.info("Creating Ollama chat client for model: {}", model);

        // Track if we need to pull this model
        boolean modelExistedBefore = preExistingModels.contains(model);

        // Create Ollama API connection
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        // Configure model management for auto-pulling models
        ModelManagementOptions modelManagementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(pullStrategy)
                .timeout(Duration.ofMinutes(2))  // 2 minutes timeout for pulling models
                .maxRetries(2)
                .build();

        // Configure chat options
        OllamaChatOptions chatOptions = OllamaChatOptions.builder()
                .model(model)
                .temperature(0.1)  // Low temperature for consistent results
                .numPredict(2048)  // Max tokens to generate
                .build();

        // Build the OllamaChatModel
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(chatOptions)
                .modelManagementOptions(modelManagementOptions)
                .build();

        // Track if model was pulled (only if it didn't exist before)
        if (!modelExistedBefore && modelManager != null && modelManager.isModelAvailable(model)) {
            pulledModels.add(model);
            logger.info("Model {} was pulled during test, will be cleaned up", model);
        }

        // Create chat memory for multi-turn conversations
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)  // Keep last 10 messages in memory
                .build();

        // Build the ChatClient with advisors
        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId("test-" + System.currentTimeMillis())
                                .build(),
                        new SimpleLoggerAdvisor()
                );

        // Add system prompt if provided
        if (scenario.getSystemPrompt() != null && !scenario.getSystemPrompt().isEmpty()) {
            builder.defaultSystem(scenario.getSystemPrompt());
        }

        return builder.build();
    }

    @Override
    public String getFullModelName(String model) {
        return "ollama-direct/" + model.replace(":", "-");
    }

    @Override
    public LlmPricing.ModelPricing getPricing(String model) {
        // Ollama runs locally, so no API costs
        // Only consideration is compute resources
        return new LlmPricing.ModelPricing(
                0.0,   // No input cost
                0.0,   // No output cost
                true,  // Supports tool calling (for models in our list)
                estimateTokensPerSecond(model)
        );
    }

    private Integer estimateTokensPerSecond(String model) {
        // Rough estimates based on model size
        // Actual performance depends on hardware
        if (model.contains("0.5b") || model.contains("1b")) {
            return 100;  // Small models are fast
        } else if (model.contains("1.5b") || model.contains("3b")) {
            return 75;   // Medium-small models
        } else if (model.contains("7b") || model.contains("8b")) {
            return 50;   // Medium models
        } else if (model.contains("35b") || model.contains("70b")) {
            return 20;   // Large models are slower
        }
        return 40;  // Default estimate
    }

    /**
     * Clean up models that were pulled during testing.
     * Only deletes models that were pulled by this test run, not pre-existing ones.
     */
    public void cleanup() {
        if (!cleanupModelsAfterTest) {
            logger.info("Model cleanup disabled, keeping all models");
            return;
        }

        if (modelManager == null) {
            logger.warn("Model manager not initialized, cannot clean up models");
            return;
        }

        if (pulledModels.isEmpty()) {
            logger.info("No models to clean up (none were pulled during this test)");
            return;
        }

        logger.info("Cleaning up {} models that were pulled during testing", pulledModels.size());

        for (String model : pulledModels) {
            try {
                logger.info("Deleting model: {}", model);
                modelManager.deleteModel(model);
                logger.info("Successfully deleted model: {}", model);
            } catch (Exception e) {
                logger.error("Failed to delete model {}: {}", model, e.getMessage());
            }
        }

        pulledModels.clear();
    }

    /**
     * Get the size of a model in GB (approximate).
     * Useful for understanding disk space requirements.
     */
    public double getModelSizeGB(String model) {
        // Rough estimates based on model name
        if (model.contains("0.5b")) return 0.3;
        if (model.contains("1b")) return 0.65;
        if (model.contains("1.5b")) return 1.0;
        if (model.contains("3b")) return 2.0;
        if (model.contains("7b")) return 4.5;
        if (model.contains("8b")) return 5.0;
        if (model.contains("35b")) return 20.0;
        if (model.contains("70b")) return 40.0;
        return 3.0; // Default estimate
    }

    /**
     * Calculate total disk space that would be freed by cleanup.
     */
    public double getTotalCleanupSizeGB() {
        return pulledModels.stream()
                .mapToDouble(this::getModelSizeGB)
                .sum();
    }
}