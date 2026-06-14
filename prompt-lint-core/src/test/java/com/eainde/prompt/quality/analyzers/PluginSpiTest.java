package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.config.LexiconAware;
import com.eainde.prompt.quality.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.List;
import java.util.Map;
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

    @Test
    void lexicon_with_plugin_category_configurable_from_file(@TempDir java.nio.file.Path dir)
            throws Exception {
        var lex = Lexicon.defaults()
                .withCategory("custom.my-words", List.of("foo"));
        var file = dir.resolve("lex.json");
        java.nio.file.Files.writeString(file, "{ \"custom.my-words\": { \"extend\": [\"bar\"] } }");
        var merged = lex.mergeFrom(file);
        assertThat(merged.keywords("custom.my-words")).containsExactly("foo", "bar");
    }

    @Test
    void lexiconAware_plugin_receives_lexicon_via_builder() {
        var lex = Lexicon.defaults();
        var plugin = new LexiconAwarePlugin();
        var prepared = PromptQualityAnalyzer.prepareLexiconForPlugins(lex, List.of(plugin));
        assertThat(prepared.keywords("custom-check.markers")).containsExactly("alpha", "beta");
        assertThat(plugin.received).isNotNull();
        assertThat(plugin.received.hasCategory("custom-check.markers")).isTrue();
    }

    @DimensionMeta(name = "CUSTOM_CHECK2", defaultWeight = 0.10, description = "LexiconAware test analyzer")
    static class LexiconAwarePlugin implements PromptDimensionAnalyzer, LexiconAware {
        Lexicon received;
        @Override
        public Map<String, List<String>> declaredCategories() {
            return Map.of("custom-check.markers", List.of("alpha", "beta"));
        }
        @Override
        public void setLexicon(Lexicon lexicon) {
            this.received = lexicon;
        }
        @Override
        public String dimensionName() { return "CUSTOM_CHECK2"; }
        @Override
        public DimensionResult analyze(PromptUnderTest prompt) {
            return new DimensionResult("CUSTOM_CHECK2", 1.0, 1.0, List.of(), List.of());
        }
    }
}
