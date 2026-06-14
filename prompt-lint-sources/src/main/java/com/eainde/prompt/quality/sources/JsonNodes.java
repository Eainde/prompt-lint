package com.eainde.prompt.quality.sources;

import com.fasterxml.jackson.databind.JsonNode;

/** Dotted-path text extraction over a Jackson node. */
final class JsonNodes {

    private JsonNodes() {}

    /**
     * Resolves a dotted key (e.g. {@code "prompt.system"}) to a text value.
     * Returns null if any path segment is missing or the leaf is not a value node.
     */
    static String textAt(JsonNode node, String dottedKey) {
        if (node == null || dottedKey == null) {
            return null;
        }
        JsonNode current = node;
        for (String segment : dottedKey.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        if (current == null || current.isNull() || current.isContainerNode()) {
            return null;
        }
        return current.asText();
    }
}
