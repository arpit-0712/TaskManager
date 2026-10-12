package org.parkar.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for Azure OpenAI. Uses the deployment-specific URL and the {@code api-key}
 * header (deployment name is provided as the model).
 */
public class AzureOpenAiClient extends AbstractHttpLlmClient {

    private final String endpoint;
    private final String apiVersion;

    public AzureOpenAiClient(String endpoint, String deployment, String apiKey, String apiVersion,
                             Duration timeout, double temperature, int maxTokens) {
        super(deployment, apiKey, timeout, temperature, maxTokens);
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.apiVersion = apiVersion == null || apiVersion.isBlank() ? "2024-06-01" : apiVersion;
    }

    @Override
    protected String endpoint() {
        return endpoint + "/openai/deployments/" + model
                + "/chat/completions?api-version=" + apiVersion;
    }

    @Override
    protected String modelsEndpoint() {
        return endpoint + "/openai/models?api-version=" + apiVersion;
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
        builder.header("api-key", apiKey);
    }

    @Override
    protected String buildBody(String systemPrompt, String userPrompt) throws JsonProcessingException {
        var payload = object();
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
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
                throw new LlmException("Azure OpenAI error: " + error.asText());
            }
        }
        return content;
    }

    @Override
    public String provider() {
        return "Azure OpenAI (" + model + ")";
    }
}
