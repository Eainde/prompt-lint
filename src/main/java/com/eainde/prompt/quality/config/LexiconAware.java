package com.eainde.prompt.quality.config;

import java.util.List;
import java.util.Map;

/**
 * Optional interface for ServiceLoader plugin analyzers that use keyword
 * categories. Declared categories are registered (with their defaults) before
 * the analyzer runs, making them valid targets in lexicon config files merged
 * AFTER registration (register via {@link Lexicon#withCategory} before
 * {@link Lexicon#mergeFrom} when configuring plugin categories from files).
 */
public interface LexiconAware {

    /** Category ids this plugin defines, with their default keyword lists. */
    Map<String, List<String>> declaredCategories();

    /** Called once at build time with the fully-resolved lexicon. */
    void setLexicon(Lexicon lexicon);
}
