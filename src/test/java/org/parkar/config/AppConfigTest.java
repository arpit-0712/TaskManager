package org.parkar.config;

import org.parkar.llm.LlmProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {

    @Test
    void defaultsToOllama() {
        AppConfig cfg = new AppConfig();
        assertEquals(LlmProvider.OLLAMA, cfg.provider());
        assertEquals("http://localhost:11434", cfg.resolvedBaseUrl());
        assertEquals("llama3.1", cfg.resolvedModel());
    }

    @Test
    void openAiDefaults() {
        AppConfig cfg = new AppConfig();
        cfg.llmProvider = "openai";
        assertEquals("https://api.openai.com/v1", cfg.resolvedBaseUrl());
        assertEquals("gpt-4o-mini", cfg.resolvedModel());
    }

    @Test
    void anthropicDefaults() {
        AppConfig cfg = new AppConfig();
        cfg.llmProvider = "anthropic";
        assertEquals("https://api.anthropic.com", cfg.resolvedBaseUrl());
        assertEquals("claude-3-5-sonnet-latest", cfg.resolvedModel());
    }

    @Test
    void explicitOverridesWin() {
        AppConfig cfg = new AppConfig();
        cfg.llmProvider = "custom";
        cfg.baseUrl = "https://gateway.internal/v1/";
        cfg.model = "my-model";
        cfg.apiKey = "secret";
        assertEquals("https://gateway.internal/v1", cfg.resolvedBaseUrl());
        assertEquals("my-model", cfg.resolvedModel());
        assertEquals("secret", cfg.resolvedApiKey());
    }

    @Test
    void providerAliases() {
        assertEquals(LlmProvider.OPENAI, LlmProvider.from("groq"));
        assertEquals(LlmProvider.OPENAI, LlmProvider.from("openrouter"));
        assertEquals(LlmProvider.AZURE_OPENAI, LlmProvider.from("azure"));
        assertEquals(LlmProvider.ANTHROPIC, LlmProvider.from("claude"));
        assertEquals(LlmProvider.OLLAMA, LlmProvider.from("something-unknown"));
    }
}
