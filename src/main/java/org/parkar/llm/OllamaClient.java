package org.parkar.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to a local <a href="https://ollama.com">Ollama</a> server using its
 * {@code /api/chat} endpoint. No API key is required.
 */
public class OllamaClient extends AbstractHttpLlmClient {

    private final String baseUrl;

    public OllamaClient(String baseUrl, String model, Duration timeout, double temperature, int maxTokens) {
        super(model, null, timeout, temperature, maxTokens);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    protected String endpoint() {
        return baseUrl + "/api/chat";
    }

    @Override
    protected String modelsEndpoint() {
        return baseUrl + "/api/tags";
    }

    @Override
    protected List<String> parseModels(String responseBody) throws JsonProcessingException {
        JsonNode root = mapper.readTree(responseBody);
        JsonNode models = root.path("models");
        List<String> names = new ArrayList<>();
        if (models.isArray()) {
            for (JsonNode model : models) {
                String name = readPath(model, "name");
                if (name == null) {
                    name = readPath(model, "model");
                }
                if (name != null && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    @Override
    protected void applyHeaders(HttpRequest.Builder builder) {
        // no authentication for a local server
    }

    @Override
    protected String buildBody(String systemPrompt, String userPrompt) throws JsonProcessingException {
        var payload = object();
        payload.put("model", model);
        payload.put("stream", false);
        payload.set("messages", chatMessages(systemPrompt, userPrompt));
        var options = payload.putObject("options");
        options.put("temperature", temperature);
        options.put("num_predict", maxTokens);
        return mapper.writeValueAsString(payload);
    }

    @Override
    protected String parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = mapper.readTree(responseBody);
        String content = readPath(root, "message", "content");
        if (content == null) {
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                throw new LlmException("Ollama error: " + error.asText());
            }
        }
        return content;
    }

    @Override
    public String provider() {
        return "Ollama (" + model + ")";
    }
}
