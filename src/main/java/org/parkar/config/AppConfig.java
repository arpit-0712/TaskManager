package org.parkar.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.parkar.llm.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Application configuration. Persisted as JSON under {@code ~/.taskmanager/config.json}.
 *
 * <p>The LLM backend is fully configurable: a local Ollama server (default) or an
 * external provider (OpenAI-compatible, Azure OpenAI, Anthropic). Secrets/URLs can be
 * supplied through the config file or environment variables.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** One of: ollama, openai, azure_openai, anthropic, custom. */
    public String llmProvider = "ollama";
    public boolean llmEnabled = true;

    /** Optional explicit overrides. Blank means "use provider default / env". */
    public String baseUrl = "";
    public String model = "";
    public String apiKey = "";
    /** Azure OpenAI API version (only used by the azure_openai provider). */
    public String apiVersion = "2024-06-01";

    public double temperature = 0.3;
    public int maxTokens = 700;
    public int requestTimeoutSeconds = 120;
    public String outputDirectory = "output";

    /**
     * Optional reference PowerPoint template. When set, the deck is generated from
     * this template (tokens cloned/filled) instead of the built-in layout.
     */
    public String pptTemplatePath = "";

    public static Path defaultLocation() {
        return Path.of(System.getProperty("user.home"), ".taskmanager", "config.json");
    }

    public static AppConfig load() {
        return load(defaultLocation());
    }

    public static AppConfig load(Path path) {
        if (path != null && Files.exists(path)) {
            try {
                AppConfig cfg = MAPPER.readValue(path.toFile(), AppConfig.class);
                log.info("Loaded configuration from {}", path);
                return cfg;
            } catch (IOException e) {
                log.warn("Could not read {} ({}); using defaults", path, e.getMessage());
            }
        }
        return new AppConfig();
    }

    public void save() {
        save(defaultLocation());
    }

    public void save(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(path.toFile(), this);
            log.info("Saved configuration to {}", path);
        } catch (IOException e) {
            log.error("Failed to save configuration to {}", path, e);
        }
    }

    public LlmProvider provider() {
        return LlmProvider.from(llmProvider);
    }

    /** Base URL, resolved from explicit config, then env, then provider default. */
    public String resolvedBaseUrl() {
        String value = firstNonBlank(baseUrl, providerEnv("BASE_URL"), providerDefaultBaseUrl());
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    /** Model/deployment name, resolved from explicit config, then env, then provider default. */
    public String resolvedModel() {
        return firstNonBlank(model, providerEnv("MODEL"), providerDefaultModel());
    }

    /** API key resolved from explicit config, then env (generic or provider-specific). */
    public String resolvedApiKey() {
        return firstNonBlank(apiKey, env("LLM_API_KEY"), providerApiKeyEnv());
    }

    private String providerEnv(String suffix) {
        String var = switch (provider()) {
            case OLLAMA -> "OLLAMA_" + suffix;
            case OPENAI -> "OPENAI_" + suffix;
            case AZURE_OPENAI -> "AZURE_OPENAI_" + suffix;
            case ANTHROPIC -> "ANTHROPIC_" + suffix;
            case CUSTOM -> "LLM_" + suffix;
        };
        return env(var);
    }

    private String providerApiKeyEnv() {
        return switch (provider()) {
            case OPENAI -> env("OPENAI_API_KEY");
            case AZURE_OPENAI -> env("AZURE_OPENAI_API_KEY");
            case ANTHROPIC -> env("ANTHROPIC_API_KEY");
            default -> null;
        };
    }

    private String providerDefaultBaseUrl() {
        return switch (provider()) {
            case OLLAMA -> "http://localhost:11434";
            case OPENAI -> "https://api.openai.com/v1";
            case AZURE_OPENAI -> env("AZURE_OPENAI_ENDPOINT");
            case ANTHROPIC -> "https://api.anthropic.com";
            case CUSTOM -> "";
        };
    }

    private String providerDefaultModel() {
        return switch (provider()) {
            case OLLAMA -> "llama3.1";
            case OPENAI -> "gpt-4o-mini";
            case AZURE_OPENAI -> firstNonBlank(env("AZURE_OPENAI_DEPLOYMENT"), "gpt-4o-mini");
            case ANTHROPIC -> "claude-3-5-sonnet-latest";
            case CUSTOM -> "";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
