package com.eainde.prompt.quality.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable registry of keyword categories driving the dimension analyzers.
 *
 * <p>Matching semantics: analyzers lowercase the prompt text and check
 * {@code contains(keyword)}. Keywords are matched verbatim — leading/trailing
 * spaces are significant (e.g. {@code " it "} enforces word boundaries).</p>
 *
 * <p>All mutating operations return a new instance.</p>
 */
public final class Lexicon {

    private final Map<String, List<String>> categories;

    private Lexicon(Map<String, List<String>> categories) {
        this.categories = categories;
    }

    /** Built-in keyword lists — identical to the analyzers' historical behavior. */
    public static Lexicon defaults() {
        return Lexicon.of(DefaultLexicon.all());
    }

    /** All built-in (default) category ids. A customized instance may carry additional registered categories. */
    public static Set<String> categoryIds() {
        return DefaultLexicon.all().keySet();
    }

    /** Keywords for a category. @throws IllegalArgumentException unknown id */
    public List<String> keywords(String categoryId) {
        requireKnown(categoryId);
        return categories.get(categoryId);
    }

    public boolean hasCategory(String categoryId) {
        return categories.containsKey(categoryId);
    }

    /** Returns a new Lexicon with the words appended to the category (deduplicated). */
    public Lexicon extend(String categoryId, String... words) {
        requireKnown(categoryId);
        var updated = new ArrayList<>(categories.get(categoryId));
        for (String w : words) {
            if (!updated.contains(w)) {
                updated.add(w);
            }
        }
        return withList(categoryId, updated);
    }

    /** Returns a new Lexicon with the words removed from the category. */
    public Lexicon remove(String categoryId, String... words) {
        requireKnown(categoryId);
        var updated = new ArrayList<>(categories.get(categoryId));
        updated.removeAll(Arrays.asList(words));
        return withList(categoryId, updated);
    }

    /** Returns a new Lexicon with the category's list fully replaced. Empty disables it. */
    public Lexicon replace(String categoryId, List<String> words) {
        requireKnown(categoryId);
        return withList(categoryId, words);
    }

    /**
     * Registers a NEW category (e.g. for plugin analyzers).
     * @throws IllegalArgumentException if the id already exists
     */
    public Lexicon withCategory(String categoryId, List<String> words) {
        if (categories.containsKey(categoryId)) {
            throw new IllegalArgumentException(
                    "Category '" + categoryId + "' already exists");
        }
        var copy = new LinkedHashMap<>(categories);
        copy.put(categoryId, List.copyOf(words));
        return new Lexicon(copy);
    }

    /**
     * External-only mode: starts from an EMPTY lexicon (all categories present,
     * no keywords) and applies the file. Categories the file does not define
     * stay empty — their checks won't match anything. Warns on stderr unless
     * {@link Strictness#SILENT}.
     */
    public static Lexicon from(Path path) {
        return from(path, Strictness.WARN);
    }

    public static Lexicon from(Path path, Strictness strictness) {
        var empty = new LinkedHashMap<String, List<String>>();
        DefaultLexicon.all().keySet().forEach(id -> empty.put(id, List.of()));
        Lexicon lex = new Lexicon(empty).apply(LexiconFileLoader.load(path));
        if (strictness == Strictness.WARN) {
            var undefined = lex.categories.entrySet().stream()
                    .filter(e -> e.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .toList();
            if (!undefined.isEmpty()) {
                System.err.println("[prompt-lint] WARNING: external-only lexicon " + path
                        + " leaves these categories empty (their checks match nothing): "
                        + undefined);
            }
        }
        return lex;
    }

    /** Applies file ops (extend/remove/replace) on top of this lexicon. */
    public Lexicon mergeFrom(Path path) {
        return apply(LexiconFileLoader.load(path));
    }

    private Lexicon apply(Map<String, LexiconFileLoader.CategoryOps> ops) {
        Lexicon result = this;
        for (var entry : ops.entrySet()) {
            String category = entry.getKey();
            requireKnown(category);
            var op = entry.getValue();
            if (op.replace() != null) {
                result = result.replace(category, op.replace());
            }
            if (op.extend() != null) {
                result = result.extend(category, op.extend().toArray(String[]::new));
            }
            if (op.remove() != null) {
                result = result.remove(category, op.remove().toArray(String[]::new));
            }
        }
        return result;
    }

    private Lexicon withList(String categoryId, List<String> words) {
        var copy = new LinkedHashMap<>(categories);
        copy.put(categoryId, List.copyOf(words));
        return new Lexicon(copy);
    }

    private void requireKnown(String categoryId) {
        if (!categories.containsKey(categoryId)) {
            throw new IllegalArgumentException("Unknown lexicon category '" + categoryId
                    + "'. Valid categories: " + categories.keySet());
        }
    }

    static Lexicon of(Map<String, List<String>> categories) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        categories.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return new Lexicon(copy);
    }
}
