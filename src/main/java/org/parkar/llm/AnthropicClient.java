package org.parkar.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Client for Anthropic's Messages API. */
public class AnthropicClient extends AbstractHttpLlmClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String baseUrl;

    public AnthropicClient(String baseUrl, String model, String apiKey,
                           Duration timeout, double temperature, int maxTokens) {
        super(model, apiKey, timeout, temperature, maxTokens);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    protected String endpoint() {
        return (baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1") + "/messages";
    }

    @Override
    protected String modelsEndpoint() {
        return (baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1") + "/models";
    }

    @Override
    protected List<String> parseModels(String responseBody) throws JsonProcessingException {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode data = root.path("data");
        List<String> names = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode model : data) {
                String id = readPath(model, "id");
                if (id != null && !names.contains(id)) {
                    names.add(id);
                }
            }
        }
        return names;
    }

    @Override
    protected void applyHeaders(HttpRequest.Builder builder) {
        requireApiKey();
        builder.header("x-api-key", apiKey);
        builder.header("anthropic-version", ANTHROPIC_VERSION);
    }

    @Override
    protected String buildBody(String systemPrompt, String userPrompt) throws JsonProcessingException {
        var payload = object();
        payload.put("model", model);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", temperature);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            payload.put("system", systemPrompt);
        }
        var messages = mapper.createArrayNode();
        messages.addObject().put("role", "user").put("content", userPrompt);
        payload.set("messages", messages);
        return mapper.writeValueAsString(payload);
    }

    @Override
    protected String parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode content = root.path("content");
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    sb.append(block.path("text").asText());
                }
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        }
        JsonNode error = root.path("error").path("message");
        if (!error.isMissingNode()) {
            throw new LlmException("Anthropic error: " + error.asText());
        }
        return null;
    }

    @Override
    public String provider() {
        return "Anthropic (" + model + ")";
    }
}
