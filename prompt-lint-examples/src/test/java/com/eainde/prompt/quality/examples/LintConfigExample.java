package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.Severity;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: LintConfig lets you reshape the issue stream without touching scores:
 * remap a rule's severity, suppress a rule entirely, or baseline-filter known
 * issues so only NEW ones surface. Suppression and baseline never change the score.
 */
class LintConfigExample {

    private final PromptUnderTest flawed = Examples.flawedDraft();

    private static String firstRuleId(PromptQualityReport report) {
        return report.allIssues().get(0).ruleId();
    }

    @Test
    void suppressARuleHidesItButKeepsTheScore() {
        PromptQualityReport baseReport = PromptQualityAnalyzer.create().analyze(flawed);
        String ruleToHide = firstRuleId(baseReport);

        PromptQualityReport suppressed = PromptQualityAnalyzer.builder()
                .suppress(ruleToHide)
                .build()
                .analyze(flawed);
        Examples.print(suppressed);

        assertThat(suppressed.allIssues()).noneSatisfy(
                i -> assertThat(i.ruleId()).isEqualTo(ruleToHide));
        assertThat(suppressed.overallScore()).isEqualTo(baseReport.overallScore());
    }

    @Test
    void severityOverrideRemapsARule() {
        PromptQualityReport baseReport = PromptQualityAnalyzer.create().analyze(flawed);
        String rule = firstRuleId(baseReport);

        PromptQualityReport overridden = PromptQualityAnalyzer.builder()
                .severityOverride(rule, Severity.CRITICAL)
                .build()
                .analyze(flawed);

        assertThat(overridden.allIssues())
                .filteredOn(i -> i.ruleId().equals(rule))
                .allSatisfy(i -> assertThat(i.severity()).isEqualTo(Severity.CRITICAL));
    }

    @Test
    void baselineFiltersKnownIssues(@TempDir Path dir) throws IOException {
        PromptQualityReport baseReport = PromptQualityAnalyzer.create().analyze(flawed);
        String known = firstRuleId(baseReport);

        // Baseline file is JSON: {"version":1,"entries":["agentName:ruleId", ...]}
        Path baseline = dir.resolve("baseline.json");
        Files.writeString(baseline,
                "{\"version\":1,\"entries\":[\"" + flawed.agentName() + ":" + known + "\"]}");

        PromptQualityReport filtered = PromptQualityAnalyzer.builder()
                .baseline(baseline)
                .build()
                .analyze(flawed);

        assertThat(filtered.allIssues()).noneSatisfy(
                i -> assertThat(i.ruleId()).isEqualTo(known));
        assertThat(filtered.overallScore()).isEqualTo(baseReport.overallScore());
    }
}
