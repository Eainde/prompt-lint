package com.eainde.prompt.quality.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexiconTest {

    @Test
    void defaults_contains_all_28_categories() {
        assertThat(Lexicon.categoryIds()).hasSize(28);
        assertThat(Lexicon.categoryIds()).contains(
                "clarity.vague-words", "groundedness.citation-requirements",
                "injection.privilege-escalation-patterns", "token-efficiency.filler-phrases");
    }

    @Test
    void defaults_match_legacy_analyzer_lists() {
        var lex = Lexicon.defaults();
        // spot-checks: one list per analyzer, exact contents
        assertThat(lex.keywords("clarity.role-starters")).containsExactly(
                "you are a", "you are an", "your role is", "act as a", "act as an");
        assertThat(lex.keywords("specificity.vague-verbs")).containsExactly(
                "handle", "process", "deal with", "manage", "take care of");
        assertThat(lex.keywords("consistency.formal-markers")).containsExactly(
                "shall", "hereby", "therefore", "henceforth", "pursuant");
        assertThat(lex.keywords("token-efficiency.filler-phrases")).hasSize(10);
        assertThat(lex.keywords("constraints.negative-instructions")).contains("do not", "never");
        assertThat(lex.keywords("groundedness.grounding-instructions")).contains("verbatim");
        assertThat(lex.keywords("injection.risky-echo-patterns")).contains("repeat back");
        // space-significant keywords preserved verbatim
        assertThat(lex.keywords("clarity.ambiguous-pronouns")).containsExactly(
                " it ", " this ", " that ", " they ", " them ");
    }

    @Test
    void keywords_unknown_category_throws_with_valid_ids() {
        assertThatThrownBy(() -> Lexicon.defaults().keywords("clarity.vauge-words"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clarity.vauge-words")
                .hasMessageContaining("clarity.vague-words"); // lists valid ids
    }

    @Test
    void keywords_returns_immutable_list() {
        List<String> words = Lexicon.defaults().keywords("clarity.vague-words");
        assertThatThrownBy(() -> words.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasCategory_reports_known_and_unknown() {
        assertThat(Lexicon.defaults().hasCategory("clarity.vague-words")).isTrue();
        assertThat(Lexicon.defaults().hasCategory("nope")).isFalse();
    }

    @Test
    void extend_adds_keywords_and_preserves_original() {
        var base = Lexicon.defaults();
        var lex = base.extend("clarity.vague-words", "leverage", "synergize");
        assertThat(lex.keywords("clarity.vague-words"))
                .contains("leverage", "synergize", "try to");
        assertThat(base.keywords("clarity.vague-words"))
                .doesNotContain("leverage"); // immutable: base unchanged
    }

    @Test
    void extend_deduplicates() {
        var lex = Lexicon.defaults().extend("clarity.vague-words", "maybe", "maybe");
        long count = lex.keywords("clarity.vague-words").stream()
                .filter("maybe"::equals).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void remove_drops_keywords() {
        var lex = Lexicon.defaults().remove("clarity.vague-words", "consider");
        assertThat(lex.keywords("clarity.vague-words")).doesNotContain("consider");
    }

    @Test
    void replace_swaps_entire_list_and_allows_empty() {
        var lex = Lexicon.defaults()
                .replace("groundedness.citation-requirements", List.of("cite para id"));
        assertThat(lex.keywords("groundedness.citation-requirements"))
                .containsExactly("cite para id");
        var disabled = lex.replace("clarity.vague-words", List.of());
        assertThat(disabled.keywords("clarity.vague-words")).isEmpty();
    }

    @Test
    void ops_on_unknown_category_throw() {
        var lex = Lexicon.defaults();
        assertThatThrownBy(() -> lex.extend("bogus", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lex.remove("bogus", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lex.replace("bogus", List.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withCategory_registers_new_category() {
        var lex = Lexicon.defaults().withCategory("myplugin.my-words", List.of("foo", "bar"));
        assertThat(lex.keywords("myplugin.my-words")).containsExactly("foo", "bar");
        assertThat(lex.hasCategory("myplugin.my-words")).isTrue();
    }

    @Test
    void withCategory_duplicate_id_throws() {
        assertThatThrownBy(() -> Lexicon.defaults()
                .withCategory("clarity.vague-words", List.of("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clarity.vague-words");
    }

    @TempDir
    Path dir;

    private Path write(String name, String content) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    @Test
    void mergeFrom_applies_file_ops_on_top_of_defaults() throws Exception {
        Path f = write("lex.json", """
                {
                  "clarity.vague-words": { "extend": ["leverage"], "remove": ["consider"] },
                  "groundedness.citation-requirements": { "replace": ["cite para id"] }
                }
                """);
        var lex = Lexicon.defaults().mergeFrom(f);
        assertThat(lex.keywords("clarity.vague-words"))
                .contains("leverage", "try to").doesNotContain("consider");
        assertThat(lex.keywords("groundedness.citation-requirements"))
                .containsExactly("cite para id");
        // untouched category keeps defaults
        assertThat(lex.keywords("specificity.vague-verbs")).contains("handle");
    }

    @Test
    void mergeFrom_unknown_category_in_file_throws() throws Exception {
        Path f = write("lex.json", "{ \"clarity.bogus\": [\"x\"] }");
        assertThatThrownBy(() -> Lexicon.defaults().mergeFrom(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clarity.bogus");
    }

    @Test
    void from_starts_empty_and_file_defines_lists() throws Exception {
        Path f = write("lex.json", "{ \"clarity.vague-words\": [\"foo\"] }");
        var lex = Lexicon.from(f, Strictness.SILENT);
        assertThat(lex.keywords("clarity.vague-words")).containsExactly("foo");
        // category the file omitted is EMPTY, not defaulted
        assertThat(lex.keywords("clarity.role-starters")).isEmpty();
    }

    @Test
    void from_with_default_strictness_works() throws Exception {
        Path f = write("lex.json", "{ \"clarity.vague-words\": [\"foo\"] }");
        var lex = Lexicon.from(f); // WARN mode: logs to stderr, doesn't throw
        assertThat(lex.keywords("clarity.vague-words")).containsExactly("foo");
    }
}
