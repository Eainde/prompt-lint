package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.report.PromptQualityReport;
import com.eainde.prompt.quality.sources.LoadResult;
import com.eainde.prompt.quality.sources.SourceLoaders;
import com.eainde.prompt.quality.sources.SourceMapping;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: prompts that live in seed files (not Java code) can be loaded and linted
 * directly — this is exactly what the prompt-lint:check Maven goal does internally.
 * Shows all four formats via SourceLoaders, then analyzes a loaded prompt.
 *
 * To run the same thing as a CI build gate, wire prompt-lint-maven-plugin into your
 * pom — see prompt-lint-examples/README.md for the <plugin> snippet.
 */
class SeedFileLoadingExample {

    private final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

    private static Path seed(String name) {
        return Path.of("src/test/resources/seeds", name);
    }

    @Test
    void loadFromSqlAndAnalyze() {
        SourceMapping mapping = new SourceMapping(
                "agent_id", "system_text", "user_text", "agent_type",
                null, null, null, "prompts");

        LoadResult loaded = SourceLoaders.forType("sql").load(seed("prompts.sql"), mapping);
        assertThat(loaded.prompts()).hasSize(1);

        PromptQualityReport report = analyzer.analyze(loaded.prompts().get(0).prompt());
        Examples.print(report);
        assertThat(report.agentName()).isEqualTo("sql-extractor");
    }

    @Test
    void loadFromJsonYamlAndProperties() {
        SourceMapping json = new SourceMapping("name", "system", "user", "type", null, null, null, null);
        assertThat(SourceLoaders.forType("json").load(seed("prompts.json"), json).prompts())
                .hasSize(1)
                .allSatisfy(np -> assertThat(np.id()).isEqualTo("json-classifier"));

        SourceMapping yaml = new SourceMapping(null, "system", null, "type", null, null, null, null);
        assertThat(SourceLoaders.forType("yaml").load(seed("prompts.yaml"), yaml).prompts())
                .hasSize(1)
                .allSatisfy(np -> assertThat(np.id()).isEqualTo("yaml-formatter"));

        SourceMapping props = new SourceMapping(null, "system", null, "type", null, null, null, null);
        assertThat(SourceLoaders.forType("properties").load(seed("prompts.properties"), props).prompts())
                .hasSize(1)
                .allSatisfy(np -> assertThat(np.id()).isEqualTo("props-reviewer"));

        assertThat(SourceLoaders.inferType(seed("prompts.yaml"))).isEqualTo("yaml");
    }
}
