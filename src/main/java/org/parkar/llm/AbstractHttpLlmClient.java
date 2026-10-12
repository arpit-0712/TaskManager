package org.parkar.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Base class for HTTP chat-completion providers. Subclasses describe the
 * endpoint, auth headers, request body and response parsing.
 */
public abstract class AbstractHttpLlmClient extends JsonSupport implements LlmClient {

    protected static final Logger log = LoggerFactory.getLogger(AbstractHttpLlmClient.class);

    protected final String model;
    protected final String apiKey;
    protected final Duration timeout;
    protected final double temperature;
    protected final int maxTokens;
    private final HttpClient http;

    protected AbstractHttpLlmClient(String model, String apiKey, Duration timeout,
                                    double temperature, int maxTokens) {
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** Absolute request URL. */
    protected abstract String endpoint();

    /** Add provider-specific headers (auth) to the request. */
    protected abstract void applyHeaders(HttpRequest.Builder builder);

    /** Build the JSON request payload. */
    protected abstract String buildBody(String systemPrompt, String userPrompt) throws JsonProcessingException;

    /** Extract the assistant text from the JSON response. */
    protected abstract String parseResponse(String responseBody) throws JsonProcessingException;

    /**
     * URL of the provider's model-listing endpoint, or {@code null} when the
     * provider does not expose one.
     */
    protected String modelsEndpoint() {
        return null;
    }

    /** Extract model identifiers from the model-listing response. */
    protected List<String> parseModels(String responseBody) throws JsonProcessingException
    {
        return List.of();
    }

    @Override
    public List<String> availableModels() {
        String url = modelsEndpoint();
        if (url == null || url.isBlank()) {
            log.debug("{} exposes no model-list endpoint", provider());
            return List.of();
        }
        log.debug("{}: listing models from {}", provider(), url);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .GET();
            applyHeaders(builder);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            log.debug("{}: model list returned HTTP {}", provider(), response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException(provider() + " model list returned HTTP " + response.statusCode() + ": "
                        + snippet(response.body()));
            }
            List<String> models = parseModels(response.body());
            log.debug("{}: discovered {} model(s)", provider(), models.size());
            return models;
        } catch (LlmException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(provider() + " model list request interrupted", e);
        } catch (Exception e) {
            throw new LlmException("Could not list models from " + provider() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String url = endpoint();
        log.debug("{}: POST {} (model={})", provider(), url, model);
        try {
            String body = buildBody(systemPrompt, userPrompt);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint()))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            applyHeaders(builder);

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            log.debug("{}: responded HTTP {} ({} bytes)", provider(), response.statusCode(), response.body().length());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException(provider() + " returned HTTP " + response.statusCode() + ": "
                        + snippet(response.body()));
            }
            String content = parseResponse(response.body());
            if (content == null || content.isBlank()) {
                throw new LlmException(provider() + " returned an empty response");
            }
            return content.trim();
        } catch (LlmException e) {
            log.warn("{} request failed: {}", provider(), e.getMessage());
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(provider() + " request interrupted", e);
        } catch (Exception e) {
            log.error("{} request error: {}", provider(), e.getMessage(), e);
            throw new LlmException("Could not complete request to " + provider() + ": " + e.getMessage(), e);
        }
    }

    /** Ensures an API key is present before sending a request. */
    protected void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException(provider() + " requires an API key (set it in Settings or environment)");
        }
    }

    private static String snippet(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 500 ? body.substring(0, 500) + "..." : body;
    }
}
