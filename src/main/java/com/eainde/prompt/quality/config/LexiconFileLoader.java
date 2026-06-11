package com.eainde.prompt.quality.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Internal parser turning a lexicon config file (JSON/properties/YAML)
 * into per-category operations. Format chosen by file extension.
 *
 * <p>File shape (same semantics in all formats): a category value is either
 * an array (= replace the full list) or an object with {@code extend} /
 * {@code remove} / {@code replace} keys.</p>
 */
final class LexiconFileLoader {

    /** Parsed ops for one category; null field = op not present. */
    record CategoryOps(List<String> replace, List<String> extend, List<String> remove) {}

    private LexiconFileLoader() {}

    static Map<String, CategoryOps> load(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return parseTree(readTree(new ObjectMapper(), path), path);
        }
        if (name.endsWith(".properties")) {
            return parseProperties(path);
        }
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return parseYaml(path);
        }
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : "(none)";
        throw new IllegalArgumentException("Unsupported lexicon file extension '" + ext
                + "' for " + path + ". Use .json, .properties, .yaml or .yml");
    }

    private static JsonNode readTree(ObjectMapper mapper, Path path) {
        try {
            return mapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse lexicon file " + path, e);
        }
    }

    private static Map<String, CategoryOps> parseTree(JsonNode root, Path path) {
        Map<String, CategoryOps> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.properties().iterator();
        while (fields.hasNext()) {
            var entry = fields.next();
            String category = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isArray()) {
                result.put(category, new CategoryOps(toList(value), null, null));
            } else if (value.isObject()) {
                List<String> replace = null, extend = null, remove = null;
                Iterator<Map.Entry<String, JsonNode>> ops = value.properties().iterator();
                while (ops.hasNext()) {
                    var op = ops.next();
                    switch (op.getKey()) {
                        case "replace" -> replace = toList(op.getValue());
                        case "extend"  -> extend  = toList(op.getValue());
                        case "remove"  -> remove  = toList(op.getValue());
                        default -> throw new IllegalArgumentException(
                                "Unknown op '" + op.getKey() + "' for category '" + category
                                        + "' in " + path + ". Valid ops: replace, extend, remove");
                    }
                }
                if (replace != null && (extend != null || remove != null)) {
                    throw new IllegalArgumentException("Category '" + category + "' in " + path
                            + " combines 'replace' with 'extend'/'remove' — not allowed");
                }
                result.put(category, new CategoryOps(replace, extend, remove));
            } else {
                throw new IllegalArgumentException("Category '" + category + "' in " + path
                        + " must be an array or an object with extend/remove/replace");
            }
        }
        return result;
    }

    private static List<String> toList(JsonNode array) {
        if (!array.isArray()) {
            throw new IllegalArgumentException("Expected an array of keywords, got: " + array);
        }
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.asText()));
        return list;
    }

    private static Map<String, CategoryOps> parseProperties(Path path) {
        var props = new Properties();
        try (var in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse lexicon file " + path, e);
        }
        // category -> op -> values
        Map<String, Map<String, List<String>>> raw = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            int lastDot = key.lastIndexOf('.');
            String op = lastDot >= 0 ? key.substring(lastDot + 1) : "";
            if (!op.equals("extend") && !op.equals("remove") && !op.equals("replace")) {
                throw new IllegalArgumentException("Property key '" + key + "' in " + path
                        + " must end with .extend, .remove or .replace");
            }
            String category = key.substring(0, lastDot);
            List<String> values = Arrays.stream(props.getProperty(key).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            raw.computeIfAbsent(category, k -> new LinkedHashMap<>()).put(op, values);
        }
        Map<String, CategoryOps> result = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            var ops = entry.getValue();
            List<String> replace = ops.get("replace");
            if (replace != null && (ops.containsKey("extend") || ops.containsKey("remove"))) {
                throw new IllegalArgumentException("Category '" + entry.getKey() + "' in " + path
                        + " combines 'replace' with 'extend'/'remove' — not allowed");
            }
            result.put(entry.getKey(),
                    new CategoryOps(replace, ops.get("extend"), ops.get("remove")));
        }
        return result;
    }

    private static Map<String, CategoryOps> parseYaml(Path path) {
        // YAMLFactory FQN is kept here (not imported at top-level) so the
        // NoClassDefFoundError handler is co-located with the optional-class reference.
        ObjectMapper yamlMapper;
        try {
            yamlMapper = new ObjectMapper(
                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException("YAML lexicon files require the optional dependency "
                    + "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml on the classpath "
                    + "(requested file: " + path + ")", e);
        }
        return parseTree(readTree(yamlMapper, path), path);
    }
}
