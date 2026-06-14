package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.analyzers.DimensionMeta;
import com.eainde.prompt.quality.analyzers.PromptDimensionAnalyzer;
import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.config.LexiconAware;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: add your own quality dimension. Implement PromptDimensionAnalyzer,
 * annotate it with @DimensionMeta, optionally implement LexiconAware to take
 * keyword lists from the shared Lexicon. Register it with withAdditionalAnalyzers()
 * (or, for ServiceLoader auto-discovery, list it in
 * META-INF/services/com.eainde.prompt.quality.analyzers.PromptDimensionAnalyzer).
 */
class CustomDimensionPluginExample {

    @Test
    void registerAndRunACustomDimension() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create()
                .withAdditionalAnalyzers(new PoliteToneAnalyzer());

        PromptUnderTest rude = new PromptUnderTest(
                "rude-bot", "Give me the answer now.", "{{q}}",
                java.util.Set.of("q"), "answer",
                com.eainde.prompt.quality.model.AgentTypeProfile.DEFAULT);

        PromptQualityReport report = analyzer.analyze(rude);
        Examples.print(report);

        assertThat(report.resultFor("POLITE_TONE")).isNotNull();
        assertThat(report.allIssues())
                .anySatisfy(i -> assertThat(i.ruleId()).isEqualTo("PLT-001"));
    }

    @DimensionMeta(name = "POLITE_TONE", defaultWeight = 0.05,
            description = "Checks the prompt uses courteous language")
    static final class PoliteToneAnalyzer implements PromptDimensionAnalyzer, LexiconAware {

        private List<String> politeWords = List.of("please", "thank you", "kindly");

        @Override
        public Map<String, List<String>> declaredCategories() {
            return Map.of("politeness.markers", List.of("please", "thank you", "kindly"));
        }

        @Override
        public void setLexicon(Lexicon lexicon) {
            if (lexicon.hasCategory("politeness.markers")) {
                politeWords = lexicon.keywords("politeness.markers");
            }
        }

        @Override
        public String dimensionName() {
            return "POLITE_TONE";
        }

        @Override
        public DimensionResult analyze(PromptUnderTest prompt) {
            String text = prompt.combinedPrompt().toLowerCase();
            boolean polite = politeWords.stream().anyMatch(text::contains);
            if (polite) {
                return new DimensionResult("POLITE_TONE", 1.0, 1.0, List.of(), List.of());
            }
            return new DimensionResult("POLITE_TONE", 0.0, 1.0,
                    List.of(QualityIssue.info("POLITE_TONE",
                            "Prompt could use more courteous language", "PLT-001")),
                    List.of("Add a polite phrase such as 'please'."));
        }
    }
}
