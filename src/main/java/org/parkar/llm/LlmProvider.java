package org.parkar.llm;

import java.util.Locale;

/** Supported (configurable) LLM backends. */
public enum LlmProvider {
    OLLAMA("Ollama (local)", false),
    OPENAI("OpenAI-compatible", true),
    AZURE_OPENAI("Azure OpenAI", true),
    ANTHROPIC("Anthropic Claude", true),
    CUSTOM("Custom (OpenAI-compatible)", true);

    private final String label;
    private final boolean requiresApiKey;

    LlmProvider(String label, boolean requiresApiKey) {
        this.label = label;
        this.requiresApiKey = requiresApiKey;
    }

    public String label() {
        return label;
    }

    public boolean requiresApiKey() {
        return requiresApiKey;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LlmProvider from(String value) {
        if (value == null || value.isBlank()) {
            return OLLAMA;
        }
        String v = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "ollama", "local" -> OLLAMA;
            case "openai", "open_ai", "openai_compatible", "openrouter", "groq", "together" -> OPENAI;
            case "azure", "azure_openai", "azureopenai" -> AZURE_OPENAI;
            case "anthropic", "claude" -> ANTHROPIC;
            case "custom" -> CUSTOM;
            default -> OLLAMA;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
