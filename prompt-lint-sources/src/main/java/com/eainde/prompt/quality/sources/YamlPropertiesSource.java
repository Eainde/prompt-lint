package com.eainde.prompt.quality.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Loads prompts from a YAML file (object keyed by id) or a .properties file
 *  using the {@code prompts.<id>.<field>} convention. Format chosen by extension. */
public class YamlPropertiesSource implements PromptSource {

    @Override
    public LoadResult load(Path path, SourceMapping mapping) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".properties")) {
            return loadProperties(path, mapping);
        }
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return loadYaml(path, mapping);
        }
        throw new IllegalArgumentException("YamlPropertiesSource cannot handle " + path
                + " — expected .yaml, .yml or .properties");
    }

    private LoadResult loadYaml(Path path, SourceMapping mapping) {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        JsonNode root;
        try {
            root = yaml.readTree(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse YAML source " + path, e);
        }
        return JsonSource.fromTree(root, path, mapping);
    }

    private LoadResult loadProperties(Path path, SourceMapping mapping) {
        Properties props = new Properties();
        try (var in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse properties source " + path, e);
        }

        Map<String, Map<String, String>> byId = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("prompts.")) {
                continue;
            }
            String rest = key.substring("prompts.".length());
            int dot = rest.indexOf('.');
            if (dot < 0) {
                warnings.add("Ignored property '" + key + "': expected prompts.<id>.<field>");
                continue;
            }
            String id = rest.substring(0, dot);
            String field = rest.substring(dot + 1);
            byId.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(field, props.getProperty(key));
        }

        List<NamedPrompt> prompts = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : byId.entrySet()) {
            Map<String, String> fields = entry.getValue();
            NamedPrompt np = RecordMapper.toNamedPrompt(
                    fields::get, mapping, entry.getKey(), warnings);
            if (np != null) {
                prompts.add(np);
            }
        }
        return new LoadResult(prompts, warnings);
    }
}
