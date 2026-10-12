package org.parkar.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder client used when the LLM is disabled. Always fails, causing the
 * {@link LlmSummaryService} to fall back to its deterministic template.
 */
public class NoOpClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpClient.class);

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        log.debug("LLM disabled: summary fallback will be used");
        throw new LlmException("LLM is disabled in settings");
    }

    @Override
    public String provider() {
        return "Disabled";
    }
}
