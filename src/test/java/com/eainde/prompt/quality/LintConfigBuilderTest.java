package com.eainde.prompt.quality;

import com.eainde.prompt.quality.config.Baseline;
import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LintConfigBuilderTest {

    @TempDir
    Path dir;

    // Prompt that triggers CLR-005 (CRITICAL: no output section, no schema)
    private PromptUnderTest promptMissingOutputSection() {
        return new PromptUnderTest("agent-x",
                "You are a tester. Your task is to extract names."
                        + " Extract, classify, return the data."
                        + " Use ONLY information from the provided documents."
                        + " Do not use external knowledge. Never fabricate.",
                "--- DOCUMENT ---\n{{sourceText}}\n--- END ---\nReturn the data.",
                Set.of("sourceText"), "out", AgentTypeProfile.DEFAULT);
    }

    @Test
    void builder_build_equals_create_behavior() {
        var prompt = promptMissingOutputSection();
        var fromCreate = PromptQualityAnalyzer.create().analyze(prompt);
        var fromBuilder = PromptQualityAnalyzer.builder().build().analyze(prompt);
        assertThat(fromBuilder.overallScore()).isEqualTo(fromCreate.overallScore());
        assertThat(issueIds(fromBuilder)).isEqualTo(issueIds(fromCreate));
    }

    @Test
    void custom_lexicon_flows_into_analyzers() {
        var lex = Lexicon.defaults().extend("clarity.output-section-markers", "antwort format");
        var analyzer = PromptQualityAnalyzer.builder().lexicon(lex).build();
        var base = promptMissingOutputSection();
        var prompt = new PromptUnderTest("agent-x",
                base.systemPrompt() + " Antwort Format: json",
                base.userPrompt(), Set.of("sourceText"), "out", AgentTypeProfile.DEFAULT);
        var report = analyzer.analyze(prompt);
        assertThat(issueIds(report)).doesNotContain("CLR-005");
    }

    @Test
    void severity_override_beats_default() {
        var analyzer = PromptQualityAnalyzer.builder()
                .severityOverride("CLR-005", Severity.WARNING)
                .build();
        var report = analyzer.analyze(promptMissingOutputSection());
        var clr005 = report.dimensionResults().stream()
                .flatMap(r -> r.issues().stream())
                .filter(i -> i.ruleId().equals("CLR-005"))
                .findFirst().orElseThrow();
        assertThat(clr005.severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void severity_override_beats_profile_calibration() {
        // SeverityCalibrator downgrades INJ-001 to INFO for FORMATTING;
        // explicit user override to CRITICAL must win.
        var analyzer = PromptQualityAnalyzer.builder()
                .severityOverride("INJ-001", Severity.CRITICAL)
                .build();
        var prompt = new PromptUnderTest("agent-x",
                "You are a formatter. Your task is to format the data."
                        + " Format, return, produce output. ## Output Format: json",
                "{{data}}", Set.of("data"), "out", AgentTypeProfile.FORMATTING);
        var report = analyzer.analyze(prompt);
        var inj001 = report.dimensionResults().stream()
                .flatMap(r -> r.issues().stream())
                .filter(i -> i.ruleId().equals("INJ-001"))
                .findFirst().orElseThrow();
        assertThat(inj001.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void suppress_hides_issue_but_keeps_score() {
        var prompt = promptMissingOutputSection();
        var unsuppressed = PromptQualityAnalyzer.create().analyze(prompt);
        var suppressed = PromptQualityAnalyzer.builder()
                .suppress("CLR-005")
                .build().analyze(prompt);
        assertThat(issueIds(suppressed)).doesNotContain("CLR-005");
        assertThat(suppressed.overallScore()).isEqualTo(unsuppressed.overallScore());
    }

    @Test
    void suppress_with_reason_works() {
        var report = PromptQualityAnalyzer.builder()
                .suppress("CLR-005", "legacy prompt, ticket PL-42")
                .build().analyze(promptMissingOutputSection());
        assertThat(issueIds(report)).doesNotContain("CLR-005");
    }

    @Test
    void baseline_filters_known_issues_only() {
        var prompt = promptMissingOutputSection();
        var first = PromptQualityAnalyzer.create().analyze(prompt);
        Path baselineFile = dir.resolve("baseline.json");
        Baseline.fromReport(first).writeTo(baselineFile);

        var analyzer = PromptQualityAnalyzer.builder().baseline(baselineFile).build();
        var second = analyzer.analyze(prompt);
        assertThat(issueIds(second)).isEmpty(); // all known
        assertThat(second.overallScore()).isEqualTo(first.overallScore()); // score honest

        // different agent name → fingerprints don't match → issues surface
        var otherAgent = new PromptUnderTest("agent-y", prompt.systemPrompt(),
                prompt.userPrompt(), prompt.declaredInputs(),
                prompt.declaredOutputKey(), prompt.agentTypeProfile());
        assertThat(issueIds(analyzer.analyze(otherAgent))).isNotEmpty();
    }

    @Test
    void missing_baseline_file_fails_at_build() {
        assertThatThrownBy(() -> PromptQualityAnalyzer.builder()
                .baseline(dir.resolve("missing.json")).build())
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void null_config_values_rejected() {
        assertThatThrownBy(() -> PromptQualityAnalyzer.builder().lexicon(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PromptQualityAnalyzer.builder().severityOverride("X", null))
                .isInstanceOf(NullPointerException.class);
    }

    private static java.util.List<String> issueIds(
            com.eainde.prompt.quality.report.PromptQualityReport report) {
        return report.dimensionResults().stream()
                .flatMap(r -> r.issues().stream())
                .map(QualityIssue::ruleId)
                .toList();
    }
}
