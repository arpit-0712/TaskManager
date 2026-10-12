package org.parkar.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared helpers for chat-style LLM providers. */
abstract class JsonSupport {

    protected final ObjectMapper mapper = new ObjectMapper();

    /** OpenAI-style messages array: [{role, content}, ...]. */
    protected ArrayNode chatMessages(String systemPrompt, String userPrompt) {
        ArrayNode messages = mapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", userPrompt);
        return messages;
    }

    protected ObjectNode object() {
        return mapper.createObjectNode();
    }

    protected String readPath(JsonNode root, String... path) {
        JsonNode node = root;
        for (String p : path) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
            if (node.isArray()) {
                try {
                    node = node.get(Integer.parseInt(p));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                node = node.path(p);
            }
        }
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
