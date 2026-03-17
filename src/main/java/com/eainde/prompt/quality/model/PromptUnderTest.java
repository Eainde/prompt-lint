package com.eainde.prompt.quality.model;

import java.util.Set;

/**
 * Wraps a prompt for quality analysis.
 *
 * @param agentName       agent identifier (e.g., "csm-candidate-extractor")
 * @param systemPrompt    the system instruction text
 * @param userPrompt      the user message template (with {{variables}})
 * @param declaredInputs  input keys declared on the AgentSpec
 * @param declaredOutputKey output key declared on the AgentSpec
 * @param agentTypeProfile weight profile for scoring
 * @param responseSchema   optional JSON schema for structured output (nullable)
 */
public record PromptUnderTest(
        String agentName,
        String systemPrompt,
        String userPrompt,
        Set<String> declaredInputs,
        String declaredOutputKey,
        AgentTypeProfile agentTypeProfile,
        String responseSchema) {

    /** Backward-compatible constructor without responseSchema. */
    public PromptUnderTest(
            String agentName, String systemPrompt, String userPrompt,
            Set<String> declaredInputs, String declaredOutputKey,
            AgentTypeProfile agentTypeProfile) {
        this(agentName, systemPrompt, userPrompt, declaredInputs,
                declaredOutputKey, agentTypeProfile, null);
    }

    public String combinedPrompt() {
        return systemPrompt + "\n" + userPrompt;
    }

    public int estimatedTokens() {
        return combinedPrompt().length() / 4;
    }
}
