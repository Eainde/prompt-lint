package com.eainde.prompt.quality.config;

/** Controls warnings for external-only lexicon loading ({@link Lexicon#from}). */
public enum Strictness {
    /** Warn on stderr listing categories the file leaves empty (default). */
    WARN,
    /** No warning. */
    SILENT
}
