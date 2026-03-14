package com.eainde.prompt.quality;

import com.eainde.prompt.quality.model.Severity;
import com.eainde.prompt.quality.report.PromptQualityReport;
import com.eainde.prompt.quality.report.PromptQualityReportRenderer;

/**
 * Custom AssertJ assertion for {@link PromptQualityReport}.
 *
 * <h3>Usage:</h3>
 * <pre>
 * import static com.db.clm.kyc.ai.testing.quality.PromptQualityAssert.assertThat;
 *
 * PromptQualityReport report = analyzer.analyze(prompt);
 *
 * assertThat(report)
 *     .passesThreshold(0.75)
 *     .hasNoCriticalIssues()
 *     .dimensionScoreAbove("GROUNDEDNESS", 0.80)
 *     .dimensionScoreAbove("OUTPUT_CONTRACT", 0.70)
 *     .hasFewerIssuesThan(10);
 * </pre>
 */
public class PromptQualityAssert extends AbstractAssert<PromptQualityAssert, PromptQualityReport> {

    private static final PromptQualityReportRenderer renderer = new PromptQualityReportRenderer();

    private PromptQualityAssert(PromptQualityReport actual) {
        super(actual, PromptQualityAssert.class);
    }

    public static PromptQualityAssert assertThat(PromptQualityReport report) {
        return new PromptQualityAssert(report);
    }

    /**
     * Asserts the overall weighted score is at or above the threshold.
     * On failure, prints the full quality report for debugging.
     */
    public PromptQualityAssert passesThreshold(double threshold) {
        isNotNull();
        if (actual.overallScore() < threshold) {
            failWithMessage(
                    "Prompt quality score %.2f is below threshold %.2f for agent '%s'.%n%s",
                    actual.overallScore(), threshold, actual.agentName(),
                    renderer.render(actual, threshold));
        }
        return this;
    }

    /**
     * Asserts there are no CRITICAL severity issues.
     */
    public PromptQualityAssert hasNoCriticalIssues() {
        isNotNull();
        var criticals = actual.issuesBySeverity(Severity.CRITICAL);
        if (!criticals.isEmpty()) {
            failWithMessage(
                    "Agent '%s' has %d critical issue(s):%n%s%n%s",
                    actual.agentName(), criticals.size(),
                    criticals.stream()
                            .map(i -> "  ✗ [" + i.ruleId() + "] " + i.message())
                            .reduce("", (a, b) -> a + "\n" + b),
                    renderer.render(actual, 0.75));
        }
        return this;
    }

    /**
     * Asserts there are no CRITICAL or WARNING severity issues.
     */
    public PromptQualityAssert hasNoWarnings() {
        isNotNull();
        hasNoCriticalIssues();
        var warnings = actual.issuesBySeverity(Severity.WARNING);
        if (!warnings.isEmpty()) {
            failWithMessage(
                    "Agent '%s' has %d warning(s):%n%s",
                    actual.agentName(), warnings.size(),
                    warnings.stream()
                            .map(i -> "  ⚠ [" + i.ruleId() + "] " + i.message())
                            .reduce("", (a, b) -> a + "\n" + b));
        }
        return this;
    }

    /**
     * Asserts a specific dimension score is at or above the given minimum.
     */
    public PromptQualityAssert dimensionScoreAbove(String dimension, double minimum) {
        isNotNull();
        var result = actual.resultFor(dimension);
        if (result == null) {
            failWithMessage("Dimension '%s' not found in report for agent '%s'.",
                    dimension, actual.agentName());
        } else if (result.score() < minimum) {
            failWithMessage(
                    "Dimension '%s' scored %.2f, below minimum %.2f for agent '%s'.%n  Issues: %s",
                    dimension, result.score(), minimum, actual.agentName(),
                    result.issues());
        }
        return this;
    }

    /**
     * Asserts the total number of issues is below a maximum.
     */
    public PromptQualityAssert hasFewerIssuesThan(int maxIssues) {
        isNotNull();
        if (actual.totalIssueCount() >= maxIssues) {
            failWithMessage(
                    "Agent '%s' has %d issues (max allowed: %d).",
                    actual.agentName(), actual.totalIssueCount(), maxIssues);
        }
        return this;
    }

    /**
     * Asserts a specific issue rule ID is NOT present (useful for regression).
     */
    public PromptQualityAssert doesNotHaveIssue(String ruleId) {
        isNotNull();
        boolean found = actual.allIssues().stream()
                .anyMatch(i -> i.ruleId().equals(ruleId));
        if (found) {
            failWithMessage(
                    "Agent '%s' still has issue [%s] — expected it to be resolved.",
                    actual.agentName(), ruleId);
        }
        return this;
    }

    /**
     * Prints the full report to stdout (useful for debugging in IDE).
     * Does NOT fail the test — call this before other assertions.
     */
    public PromptQualityAssert printReport(double threshold) {
        isNotNull();
        System.out.println(renderer.render(actual, threshold));
        return this;
    }
}
