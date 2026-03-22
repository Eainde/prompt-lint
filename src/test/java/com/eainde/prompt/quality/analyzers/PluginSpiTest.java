package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginSpiTest {

    @DimensionMeta(name = "CUSTOM_CHECK", defaultWeight = 0.10, description = "Test custom analyzer")
    static class CustomAnalyzer implements PromptDimensionAnalyzer {
        @Override
        public String dimensionName() { return "CUSTOM_CHECK"; }
        @Override
        public DimensionResult analyze(PromptUnderTest prompt) {
            return new DimensionResult("CUSTOM_CHECK", 0.90, 1.0, List.of(), List.of("Custom suggestion"));
        }
    }

    @Test
    void withAdditionalAnalyzers_adds_custom_dimension() {
        var analyzer = PromptQualityAnalyzer.create().withAdditionalAnalyzers(new CustomAnalyzer());
        var prompt = new PromptUnderTest("test", "You are a specialist. Your task is to extract data.",
                "{{sourceText}}", Set.of("sourceText"), "result", AgentTypeProfile.DEFAULT);
        var report = analyzer.analyze(prompt);
        assertThat(report.dimensionResults()).anyMatch(r -> r.dimension().equals("CUSTOM_CHECK"));
    }

    @Test
    void custom_analyzer_uses_default_weight_from_annotation() {
        var custom = new CustomAnalyzer();
        var meta = custom.getClass().getAnnotation(DimensionMeta.class);
        assertThat(meta).isNotNull();
        assertThat(meta.defaultWeight()).isEqualTo(0.10);
    }

    @Test
    void duplicate_dimension_name_throws() {
        var duplicate = new PromptDimensionAnalyzer() {
            @Override public String dimensionName() { return "CLARITY"; }
            @Override public DimensionResult analyze(PromptUnderTest p) { return null; }
        };
        assertThatThrownBy(() -> PromptQualityAnalyzer.create().withAdditionalAnalyzers(duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLARITY");
    }

    @Test
    void withCustomWeight_adds_weight_to_profile() {
        var profile = AgentTypeProfile.DEFAULT.withCustomWeight("CUSTOM_CHECK", 0.15);
        assertThat(profile.weightFor("CUSTOM_CHECK")).isEqualTo(0.15);
        assertThat(profile.weightFor("CLARITY")).isEqualTo(0.125);
    }

    @Test
    void custom_analyzer_exception_scores_zero() {
        var failing = new PromptDimensionAnalyzer() {
            @Override public String dimensionName() { return "FAILING"; }
            @Override public DimensionResult analyze(PromptUnderTest p) { throw new RuntimeException("boom"); }
        };
        var analyzer = PromptQualityAnalyzer.create().withAdditionalAnalyzers(failing);
        var prompt = new PromptUnderTest("test", "You are a specialist. Your task is to extract.",
                "{{sourceText}}", Set.of("sourceText"), "result", AgentTypeProfile.DEFAULT);
        var report = analyzer.analyze(prompt);
        var failingResult = report.resultFor("FAILING");
        assertThat(failingResult).isNotNull();
        assertThat(failingResult.score()).isEqualTo(0.0);
    }
}
