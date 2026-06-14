package com.eainde.prompt.quality.sources;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Turns one source record (a field-name → value accessor) into a {@link NamedPrompt}. */
final class RecordMapper {

    private RecordMapper() {}

    /**
     * @param get        resolves a source field name (possibly dotted) to its string value or null
     * @param mapping    field-name mapping
     * @param fallbackId id to use when {@code mapping.nameField()} is null or absent
     * @param warnings   collector; a skipped record appends exactly one warning
     * @return the mapped prompt, or null if the record was skipped
     */
    static NamedPrompt toNamedPrompt(Function<String, String> get,
                                     SourceMapping mapping,
                                     String fallbackId,
                                     List<String> warnings) {
        String id = fallbackId;
        if (mapping.nameField() != null) {
            String n = get.apply(mapping.nameField());
            if (n != null && !n.isBlank()) {
                id = n;
            }
        }

        String system = mapping.systemPrompt() == null ? null : get.apply(mapping.systemPrompt());
        if (system == null || system.isBlank()) {
            warnings.add("Skipped record '" + id + "': no system prompt (mapped field '"
                    + mapping.systemPrompt() + "')");
            return null;
        }

        String user = orEmpty(value(get, mapping.userPrompt()));

        AgentTypeProfile profile = AgentTypeProfile.DEFAULT;
        String typeName = value(get, mapping.agentType());
        if (typeName != null && !typeName.isBlank()) {
            profile = AgentTypeProfile.fromName(typeName);
        }

        Set<String> inputs = splitToSet(value(get, mapping.inputs()));
        String output = value(get, mapping.outputs());
        String schema = value(get, mapping.responseSchema());

        PromptUnderTest prompt = new PromptUnderTest(
                id, system, user, inputs, output, profile, schema);
        return new NamedPrompt(id, prompt);
    }

    private static String value(Function<String, String> get, String field) {
        return field == null ? null : get.apply(field);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Set<String> splitToSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }
}
