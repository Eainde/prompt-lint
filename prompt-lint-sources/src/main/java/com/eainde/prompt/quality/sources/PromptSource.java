package com.eainde.prompt.quality.sources;

import java.nio.file.Path;

/** Loads prompts from a single seed file of one format. */
public interface PromptSource {

    /**
     * Parses the file and maps each record to a {@link NamedPrompt} using the mapping.
     * IO/parse failures that make the whole file unreadable throw
     * {@link java.io.UncheckedIOException}; recoverable per-record problems are
     * reported as warnings in the result.
     */
    LoadResult load(Path path, SourceMapping mapping);
}
