package org.parkar.llm;

import java.util.List;

/**
 * Abstraction over a configurable LLM backend. Implementations must be safe to
 * call from the UI thread's background workers.
 */
public interface LlmClient {

    /**
     * Sends a single-turn completion request.
     *
     * @param systemPrompt system/instruction prompt
     * @param userPrompt   user content
     * @return the assistant's text response
     * @throws LlmException if the request fails or the backend is unavailable
     */
    String complete(String systemPrompt, String userPrompt);

    /**
     * Lists the models available from the backend so the application can detect a
     * suitable model automatically (local Ollama or an external provider).
     *
     * @return the model identifiers, or an empty list when detection is not
     *         supported or the backend is unreachable
     * @throws LlmException if listing is supported but the request fails
     */
    default List<String> availableModels() {
        return List.of();
    }

    /** Human-readable provider name for diagnostics. */
    default String provider() {
        return getClass().getSimpleName();
    }
}
