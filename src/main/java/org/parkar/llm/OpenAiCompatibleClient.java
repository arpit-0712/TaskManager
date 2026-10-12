package org.parkar.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for any OpenAI Chat Completions compatible endpoint
 * (OpenAI, Azure-compatible gateways, Groq, OpenRouter, Together,
 * LM Studio, vLLM, LocalAI, ...).
 */
public class OpenAiCompatibleClient extends AbstractHttpLlmClient {

    private final String baseUrl;

    public OpenAiCompatibleClient(String baseUrl, String model, String apiKey,
                                  Duration timeout, double temperature, int maxTokens) {
        super(model, apiKey, timeout, temperature, maxTokens);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    protected String endpoint() {
        return baseUrl + "/chat/completions";
    }

    @Override
    protected String modelsEndpoint() {
        return baseUrl + "/models";
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
        builder.header("Authorization", "Bearer " + apiKey);
    }

    @Override
    protected String buildBody(String systemPrompt, String userPrompt) throws JsonProcessingException {
        var payload = object();
        payload.put("model", model);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        payload.put("stream", false);
        payload.set("messages", chatMessages(systemPrompt, userPrompt));
        return mapper.writeValueAsString(payload);
    }

    @Override
    protected String parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = mapper.readTree(responseBody);
        String content = readPath(root, "choices", "0", "message", "content");
        if (content == null) {
            JsonNode error = root.path("error").path("message");
            if (!error.isMissingNode()) {
                throw new LlmException("Provider error: " + error.asText());
            }
        }
        return content;
    }

    @Override
    public String provider() {
        return "OpenAI-compatible (" + model + ")";
    }
}
