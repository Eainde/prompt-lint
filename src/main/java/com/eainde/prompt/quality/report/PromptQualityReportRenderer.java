package com.eainde.prompt.quality.report;

import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.model.Severity;

import java.util.List;

/**
 * Renders {@link PromptQualityReport} as formatted text for console or CI logs.
 */
public class PromptQualityReportRenderer {

    private static final String HORIZONTAL_LINE =
            "═══════════════════════════════════════════════════════════════";

    /**
     * Renders a single report as formatted text.
     */
    public String render(PromptQualityReport report, double threshold) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append('\n').append(HORIZONTAL_LINE).append('\n');
        sb.append("  PROMPT QUALITY REPORT: ").append(report.agentName()).append('\n');
        sb.append("  Profile: ").append(report.profile().agentType()).append('\n');
        sb.append(HORIZONTAL_LINE).append('\n');
        sb.append('\n');

        // Overall score
        boolean passed = report.passes(threshold);
        sb.append(String.format("  Overall Score: %.2f / 1.00  %s (threshold: %.2f)%n",
                report.overallScore(),
                passed ? "✓ PASS" : "✗ FAIL",
                threshold));
        sb.append('\n');

        // Dimension table
        sb.append("  ┌────────────────────────┬───────┬────────┬─────────┐\n");
        sb.append("  │ Dimension              │ Score │ Weight │ Contrib │\n");
        sb.append("  ├────────────────────────┼───────┼────────┼─────────┤\n");

        for (DimensionResult dim : report.dimensionResults()) {
            double weight = report.profile().weightFor(dim.dimension());
            double contrib = dim.score() * weight;
            String indicator = dim.score() >= 0.8 ? "  " :
                    dim.score() >= 0.5 ? "⚠ " : "✗ ";
            sb.append(String.format("  │ %s%-20s │ %5.2f │  %4.2f  │  %5.3f  │%n",
                    indicator,
                    truncate(dim.dimension(), 20),
                    dim.score(),
                    weight,
                    contrib));
        }
        sb.append("  └────────────────────────┴───────┴────────┴─────────┘\n");

        // Lowest dimension
        DimensionResult lowest = report.lowestScoringDimension();
        if (lowest != null && lowest.score() < 0.8) {
            sb.append(String.format("%n  ⚡ Weakest dimension: %s (%.2f)%n",
                    lowest.dimension(), lowest.score()));
        }

        // Issues
        List<QualityIssue> criticals = report.issuesBySeverity(Severity.CRITICAL);
        List<QualityIssue> warnings = report.issuesBySeverity(Severity.WARNING);
        List<QualityIssue> infos = report.issuesBySeverity(Severity.INFO);

        if (!report.allIssues().isEmpty()) {
            sb.append(String.format("%n  Issues Found: %d total (%d critical, %d warning, %d info)%n",
                    report.totalIssueCount(),
                    criticals.size(), warnings.size(), infos.size()));

            for (QualityIssue issue : criticals) {
                sb.append(String.format("    ✗ CRITICAL [%s] %s%n",
                        issue.ruleId(), issue.message()));
            }
            for (QualityIssue issue : warnings) {
                sb.append(String.format("    ⚠ WARNING  [%s] %s%n",
                        issue.ruleId(), issue.message()));
            }
            for (QualityIssue issue : infos) {
                sb.append(String.format("    ℹ INFO     [%s] %s%n",
                        issue.ruleId(), issue.message()));
            }
        } else {
            sb.append("\n  ✓ No issues found.\n");
        }

        // Suggestions
        List<String> suggestions = report.allSuggestions();
        if (!suggestions.isEmpty()) {
            sb.append("\n  Suggestions:\n");
            for (int i = 0; i < suggestions.size(); i++) {
                sb.append(String.format("    %d. %s%n", i + 1, suggestions.get(i)));
            }
        }

        sb.append('\n').append(HORIZONTAL_LINE).append('\n');
        return sb.toString();
    }

    /**
     * Renders a summary for multiple reports (CI pipeline output).
     */
    public String renderSummary(List<PromptQualityReport> reports, double threshold) {
        StringBuilder sb = new StringBuilder();

        sb.append('\n').append(HORIZONTAL_LINE).append('\n');
        sb.append("  PROMPT QUALITY SUMMARY — ").append(reports.size()).append(" agents\n");
        sb.append(HORIZONTAL_LINE).append('\n');
        sb.append('\n');

        int passed = 0;
        int failed = 0;
        int criticalCount = 0;

        sb.append("  ┌──────────────────────────────────┬───────┬────────┬──────────┐\n");
        sb.append("  │ Agent                            │ Score │ Issues │  Status  │\n");
        sb.append("  ├──────────────────────────────────┼───────┼────────┼──────────┤\n");

        for (PromptQualityReport report : reports) {
            boolean pass = report.passes(threshold);
            if (pass) passed++;
            else failed++;

            int criticals = report.issuesBySeverity(Severity.CRITICAL).size();
            criticalCount += criticals;

            sb.append(String.format("  │ %-32s │ %5.2f │   %3d  │ %s │%n",
                    truncate(report.agentName(), 32),
                    report.overallScore(),
                    report.totalIssueCount(),
                    pass ? "  PASS  " : "  FAIL  "));
        }
        sb.append("  └──────────────────────────────────┴───────┴────────┴──────────┘\n");

        sb.append(String.format("%n  Results: %d passed, %d failed, %d critical issues%n",
                passed, failed, criticalCount));

        if (failed > 0) {
            sb.append(String.format("  ✗ OVERALL: FAIL (threshold: %.2f)%n", threshold));

            // List failed agents
            sb.append("\n  Failed agents:\n");
            for (PromptQualityReport report : reports) {
                if (!report.passes(threshold)) {
                    sb.append(String.format("    ✗ %s (%.2f) — weakest: %s%n",
                            report.agentName(),
                            report.overallScore(),
                            report.lowestScoringDimension() != null
                                    ? report.lowestScoringDimension().dimension()
                                    : "N/A"));
                }
            }
        } else {
            sb.append(String.format("  ✓ OVERALL: PASS (threshold: %.2f)%n", threshold));
        }

        sb.append('\n').append(HORIZONTAL_LINE).append('\n');
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }
}
