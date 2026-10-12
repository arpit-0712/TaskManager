package org.parkar.llm;

import org.parkar.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/** Builds the appropriate {@link LlmClient} from configuration. */
public final class LlmClientFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmClientFactory.class);

    private LlmClientFactory() {
    }

    public static LlmClient create(AppConfig config) {
        if (config == null || !config.llmEnabled) {
            log.info("LLM is disabled; using NoOpClient (summary fallback)");
            return new NoOpClient();
        }
        Duration timeout = Duration.ofSeconds(Math.max(5, config.requestTimeoutSeconds));
        String baseUrl = config.resolvedBaseUrl();
        String model = config.resolvedModel();
        String apiKey = config.resolvedApiKey();

        LlmClient client = switch (config.provider()) {
            case OLLAMA -> new OllamaClient(baseUrl, model, timeout, config.temperature, config.maxTokens);
            case OPENAI, CUSTOM ->
                    new OpenAiCompatibleClient(baseUrl, model, apiKey, timeout, config.temperature, config.maxTokens);
            case AZURE_OPENAI -> new AzureOpenAiClient(
                    baseUrl, model, apiKey, config.apiVersion, timeout, config.temperature, config.maxTokens);
            case ANTHROPIC ->
                    new AnthropicClient(baseUrl, model, apiKey, timeout, config.temperature, config.maxTokens);
        };
        log.info("Created LLM client: provider={}, baseUrl={}, model={}",
                config.provider().label(), baseUrl == null || baseUrl.isBlank() ? "(default)" : baseUrl, model);
        return client;
    }

    /**
     * Queries the configured backend for the models it exposes. Works for both the
     * local/internal Ollama server and external providers. Returns an empty list
     * when the LLM is disabled or the provider exposes no model list.
     *
     * @throws LlmException when listing is supported but the request fails
     */
    public static List<String> detectModels(AppConfig config) {
        if (config == null || !config.llmEnabled) {
            return List.of();
        }
        return create(config).availableModels();
    }
}
