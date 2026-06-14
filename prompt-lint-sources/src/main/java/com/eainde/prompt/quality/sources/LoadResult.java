package com.eainde.prompt.quality.sources;

import java.util.List;

/**
 * Result of loading one seed file: successfully mapped prompts plus
 * human-readable warnings (skipped records, unparseable statements).
 * Loaders never log; the caller decides how to surface warnings.
 */
public record LoadResult(List<NamedPrompt> prompts, List<String> warnings) {

    public static LoadResult empty() {
        return new LoadResult(List.of(), List.of());
    }
}
