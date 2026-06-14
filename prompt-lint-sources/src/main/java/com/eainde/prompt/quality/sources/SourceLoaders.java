package com.eainde.prompt.quality.sources;

import java.nio.file.Path;
import java.util.Locale;

/** Resolves a {@link PromptSource} by explicit type name or by file extension. */
public final class SourceLoaders {

    private SourceLoaders() {}

    public static PromptSource forType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "sql" -> new SqlSeedSource();
            case "json" -> new JsonSource();
            case "yaml", "yml", "properties" -> new YamlPropertiesSource();
            default -> throw new IllegalArgumentException(
                    "Unknown source type '" + type + "'. Use sql, json, yaml, yml or properties");
        };
    }

    public static String inferType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".sql")) return "sql";
        if (name.endsWith(".json")) return "json";
        if (name.endsWith(".yaml") || name.endsWith(".yml")) return "yaml";
        if (name.endsWith(".properties")) return "properties";
        throw new IllegalArgumentException("Cannot infer source type from file name '"
                + path.getFileName() + "'. Set <type> explicitly.");
    }
}
