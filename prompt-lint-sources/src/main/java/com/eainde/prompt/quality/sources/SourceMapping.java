package com.eainde.prompt.quality.sources;

/**
 * Declares how a seed record's fields map onto {@link com.eainde.prompt.quality.model.PromptUnderTest}.
 * Every field is the NAME of a source field, not a value.
 *
 * @param nameField      source field holding the prompt id (null → use a generated fallback id)
 * @param systemPrompt   source field holding the system prompt (REQUIRED; record skipped if missing/blank)
 * @param userPrompt     source field holding the user prompt (null → empty string)
 * @param agentType      source field holding the agent-type profile name (null/unknown → DEFAULT)
 * @param inputs         source field holding comma-separated declared inputs (null → empty set)
 * @param outputs        source field holding the declared output key (null → null)
 * @param responseSchema source field holding the JSON response schema (null → null)
 * @param table          for SQL only: restrict to INSERTs into this table (null → all tables)
 */
public record SourceMapping(
        String nameField,
        String systemPrompt,
        String userPrompt,
        String agentType,
        String inputs,
        String outputs,
        String responseSchema,
        String table) {

    /** Mapping that reads only a system prompt from the given field name. */
    public static SourceMapping systemOnly(String systemPromptField) {
        return new SourceMapping(null, systemPromptField, null, null, null, null, null, null);
    }
}
