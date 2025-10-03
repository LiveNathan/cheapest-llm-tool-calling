package dev.nathanlively.cheapest_llm_tool_calling;

import java.util.List;

public class TestScenario {
    private final String name;
    private final List<String> prompts;
    private final ValidationCallback validation;
    private final Object toolService;
    private final String systemPrompt;

    public TestScenario(String name, List<String> prompts, ValidationCallback validation,
                        Object toolService, String systemPrompt) {
        this.name = name;
        this.prompts = prompts;
        this.validation = validation;
        this.toolService = toolService;
        this.systemPrompt = systemPrompt;
    }

    public String getName() {
        return name;
    }

    public List<String> getPrompts() {
        return prompts;
    }

    public ValidationCallback getValidation() {
        return validation;
    }

    public Object getToolService() {
        return toolService;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    @FunctionalInterface
    public interface ValidationCallback {
        double validate();
    }

    public static class Builder {
        private String name;
        private List<String> prompts;
        private ValidationCallback validation;
        private Object toolService;
        private String systemPrompt = "";

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder prompts(String... prompts) {
            this.prompts = List.of(prompts);
            return this;
        }

        public Builder prompts(List<String> prompts) {
            this.prompts = prompts;
            return this;
        }

        public Builder validation(ValidationCallback validation) {
            this.validation = validation;
            return this;
        }

        public Builder toolService(Object toolService) {
            this.toolService = toolService;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public TestScenario build() {
            return new TestScenario(name, prompts, validation, toolService, systemPrompt);
        }
    }
}