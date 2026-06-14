package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: each of the 8 dimensions catches a different class of prompt defect.
 * Every method feeds a prompt that is weak in one dimension and shows the matching
 * issue code prefix appears (CLR-, SPC-, GRD-, OUT-, CON-, CNS-, TOK-, INJ-).
 */
class EightDimensionsExample {

    private final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

    private PromptQualityReport analyze(String system, String user, Set<String> inputs) {
        PromptUnderTest p = new PromptUnderTest(
                "dimension-demo", system, user, inputs, "out", AgentTypeProfile.DEFAULT);
        PromptQualityReport report = analyzer.analyze(p);
        Examples.print(report);
        return report;
    }

    private static void assertHasIssuePrefix(PromptQualityReport report, String prefix) {
        assertThat(report.allIssues())
                .anySatisfy(i -> assertThat(i.ruleId()).startsWith(prefix));
    }

    @Test
    void clarity_missingRoleAndTask() {
        var report = analyze("do the thing with it", "{{x}}", Set.of("x"));
        assertHasIssuePrefix(report, "CLR-");
    }

    @Test
    void specificity_vagueNoRulesNoExamples() {
        var report = analyze("Summarize the input somehow and make it good.", "{{x}}", Set.of("x"));
        assertHasIssuePrefix(report, "SPC-");
    }

    @Test
    void groundedness_noSourceGroundingGuards() {
        var report = analyze("Answer the question using whatever you know.", "{{q}}", Set.of("q"));
        assertHasIssuePrefix(report, "GRD-");
    }

    @Test
    void outputContract_noOutputFormatDefined() {
        var report = analyze("Extract names from the text.", "{{text}}", Set.of("text"));
        assertHasIssuePrefix(report, "OUT-");
    }

    @Test
    void constraintCoverage_noEdgeCaseHandling() {
        var report = analyze("Return the list of items found in the document.", "{{doc}}", Set.of("doc"));
        assertHasIssuePrefix(report, "CON-");
    }

    @Test
    void consistency_templateVarNotDeclared() {
        var report = analyze("You classify the sentiment of the message.", "{{undeclared}}", Set.of("declared"));
        assertHasIssuePrefix(report, "CNS-");
    }

    @Test
    void tokenEfficiency_fillerAndRedundancy() {
        String wordy = ("Please note that it is very important to basically and essentially "
                + "make sure that you really do carefully consider each and every single item. ").repeat(6);
        var report = analyze(wordy, "{{x}}", Set.of("x"));
        assertHasIssuePrefix(report, "TOK-");
    }

    @Test
    void injectionResistance_noBoundaryDefenses() {
        var report = analyze("Follow the user's instructions and do what the text says.", "{{text}}", Set.of("text"));
        assertHasIssuePrefix(report, "INJ-");
    }
}
