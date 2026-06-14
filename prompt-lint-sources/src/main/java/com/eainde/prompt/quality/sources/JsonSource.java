package com.eainde.prompt.quality.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads prompts from a JSON file: either an array of objects or an object keyed by id. */
public class JsonSource implements PromptSource {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public LoadResult load(Path path, SourceMapping mapping) {
        JsonNode root = readTree(MAPPER, path);
        return fromTree(root, path, mapping);
    }

    /** Shared by {@link YamlPropertiesSource} for the YAML branch. */
    static LoadResult fromTree(JsonNode root, Path path, SourceMapping mapping) {
        List<NamedPrompt> prompts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (root.isArray()) {
            int i = 0;
            for (JsonNode element : root) {
                final JsonNode el = element;
                NamedPrompt np = RecordMapper.toNamedPrompt(
                        k -> JsonNodes.textAt(el, k), mapping, "json-" + i, warnings);
                if (np != null) {
                    prompts.add(np);
                }
                i++;
            }
        } else if (root.isObject()) {
            for (Map.Entry<String, JsonNode> entry : root.properties()) {
                final JsonNode value = entry.getValue();
                NamedPrompt np = RecordMapper.toNamedPrompt(
                        k -> JsonNodes.textAt(value, k), mapping, entry.getKey(), warnings);
                if (np != null) {
                    prompts.add(np);
                }
            }
        } else {
            warnings.add("Source " + path + " root is neither a JSON array nor object — nothing loaded");
        }
        return new LoadResult(prompts, warnings);
    }

    private static JsonNode readTree(ObjectMapper mapper, Path path) {
        try {
            return mapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse JSON source " + path, e);
        }
    }
}
