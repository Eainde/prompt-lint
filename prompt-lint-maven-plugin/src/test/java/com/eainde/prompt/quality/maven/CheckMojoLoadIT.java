package com.eainde.prompt.quality.maven;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.report.PromptQualityReport;
import com.eainde.prompt.quality.sources.LoadResult;
import com.eainde.prompt.quality.sources.SourceLoaders;
import com.eainde.prompt.quality.sources.SourceMapping;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CheckMojoLoadIT {

    @Test
    void loadsSqlSeedAndProducesReport() {
        Path seed = Path.of("src/test/resources/e2e-seed.sql");
        var mapping = new SourceMapping(
                "agent_id", "system_text", "user_text", "agent_type",
                null, null, null, "prompts");

        LoadResult loaded = SourceLoaders.forType("sql").load(seed, mapping);
        assertThat(loaded.prompts()).hasSize(1);

        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityReport report = analyzer.analyze(loaded.prompts().get(0).prompt());

        assertThat(report.agentName()).isEqualTo("good-extractor");
        assertThat(report.overallScore()).isBetween(0.0, 1.0);
    }
}
