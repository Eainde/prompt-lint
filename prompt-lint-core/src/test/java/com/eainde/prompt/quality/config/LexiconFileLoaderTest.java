package com.eainde.prompt.quality.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexiconFileLoaderTest {

    @TempDir
    Path dir;

    private Path write(String name, String content) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    @Test
    void json_object_form_parses_extend_remove_replace() throws Exception {
        Path f = write("lex.json", """
                {
                  "clarity.vague-words": { "extend": ["leverage"], "remove": ["consider"] },
                  "groundedness.citation-requirements": { "replace": ["cite para id"] }
                }
                """);
        var ops = LexiconFileLoader.load(f);
        assertThat(ops.get("clarity.vague-words").extend()).containsExactly("leverage");
        assertThat(ops.get("clarity.vague-words").remove()).containsExactly("consider");
        assertThat(ops.get("clarity.vague-words").replace()).isNull();
        assertThat(ops.get("groundedness.citation-requirements").replace())
                .containsExactly("cite para id");
    }

    @Test
    void json_array_form_means_replace() throws Exception {
        Path f = write("lex.json", """
                { "clarity.role-starters": ["you are", "act as"] }
                """);
        var ops = LexiconFileLoader.load(f);
        assertThat(ops.get("clarity.role-starters").replace())
                .containsExactly("you are", "act as");
    }

    @Test
    void json_replace_combined_with_extend_throws() throws Exception {
        Path f = write("lex.json", """
                { "clarity.vague-words": { "replace": ["a"], "extend": ["b"] } }
                """);
        assertThatThrownBy(() -> LexiconFileLoader.load(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replace");
    }

    @Test
    void json_unknown_op_key_throws() throws Exception {
        Path f = write("lex.json", """
                { "clarity.vague-words": { "extends": ["a"] } }
                """);
        assertThatThrownBy(() -> LexiconFileLoader.load(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extends");
    }

    @Test
    void malformed_json_throws_unchecked_io_with_path() throws Exception {
        Path f = write("lex.json", "{ not json");
        assertThatThrownBy(() -> LexiconFileLoader.load(f))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("lex.json");
    }

    @Test
    void unknown_extension_throws() throws Exception {
        Path f = write("lex.txt", "x");
        assertThatThrownBy(() -> LexiconFileLoader.load(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".txt");
    }

    @Test
    void properties_form_parses_ops_with_comma_separated_values() throws Exception {
        Path f = write("lex.properties", """
                clarity.vague-words.extend=leverage,synergize
                clarity.vague-words.remove=consider
                groundedness.citation-requirements.replace=cite para id,quote source line
                """);
        var ops = LexiconFileLoader.load(f);
        assertThat(ops.get("clarity.vague-words").extend())
                .containsExactly("leverage", "synergize");
        assertThat(ops.get("clarity.vague-words").remove()).containsExactly("consider");
        assertThat(ops.get("groundedness.citation-requirements").replace())
                .containsExactly("cite para id", "quote source line");
    }

    @Test
    void properties_values_are_trimmed() throws Exception {
        Path f = write("lex.properties",
                "clarity.vague-words.extend= leverage , synergize \n");
        var ops = LexiconFileLoader.load(f);
        assertThat(ops.get("clarity.vague-words").extend())
                .containsExactly("leverage", "synergize");
    }

    @Test
    void properties_key_without_op_suffix_throws() throws Exception {
        Path f = write("lex.properties", "clarity.vague-words=leverage\n");
        assertThatThrownBy(() -> LexiconFileLoader.load(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clarity.vague-words");
    }

    @Test
    void properties_replace_combined_with_extend_throws() throws Exception {
        Path f = write("lex.properties", """
                clarity.vague-words.replace=a
                clarity.vague-words.extend=b
                """);
        assertThatThrownBy(() -> LexiconFileLoader.load(f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replace");
    }

    @Test
    void yaml_form_parses_ops_and_arrays() throws Exception {
        Path f = write("lex.yaml", """
                clarity.vague-words:
                  extend: [leverage, synergize]   # style guide 2026
                  remove: [consider]
                clarity.role-starters: [you are, act as]
                """);
        var ops = LexiconFileLoader.load(f);
        assertThat(ops.get("clarity.vague-words").extend())
                .containsExactly("leverage", "synergize");
        assertThat(ops.get("clarity.vague-words").remove()).containsExactly("consider");
        assertThat(ops.get("clarity.role-starters").replace())
                .containsExactly("you are", "act as");
    }

    @Test
    void yml_extension_also_accepted() throws Exception {
        Path f = write("lex.yml", "clarity.role-starters: [you are]\n");
        var ops = LexiconFileLoader.load(f);
        assertThat(ops.get("clarity.role-starters").replace()).containsExactly("you are");
    }
}
