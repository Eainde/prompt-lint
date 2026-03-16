package com.eainde.prompt.quality.api;

import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.model.Severity;
import com.eainde.prompt.quality.report.PromptQualityReport;

import java.util.List;

/**
 * Structured result object for prompt quality analysis.
 * Designed for consumption by REST endpoints and non-technical users.
 */
public record PromptQualityResult(
        String agentName,
        String profile,
        double overallScore,
        double threshold,
        boolean passed,
        boolean hasCriticalIssues,
        String analyzedAt,
        WeakestDimension weakestDimension,
        List<DimensionDetail> dimensions,
        IssueSummary issueSummary,
        List<IssueDetail> issues,
        List<String> suggestions) {

    public record WeakestDimension(String name, double score) {}

    public record DimensionDetail(
            String dimension,
            double score,
            double maxScore,
            double weight,
            double contribution) {}

    public record IssueSummary(int total, int critical, int warning, int info) {}

    public record IssueDetail(
            String dimension,
            String severity,
            String message,
            String ruleId) {}

    public static PromptQualityResult from(PromptQualityReport report, double threshold) {
        var profile = report.profile();

        DimensionResult lowest = report.lowestScoringDimension();
        WeakestDimension weakest = lowest != null
                ? new WeakestDimension(lowest.dimension(), lowest.score())
                : null;

        List<DimensionDetail> dimensions = report.dimensionResults().stream()
                .map(d -> {
                    double weight = profile.weightFor(d.dimension());
                    return new DimensionDetail(
                            d.dimension(),
                            d.score(),
                            d.maxScore(),
                            weight,
                            d.score() * weight);
                })
                .toList();

        List<QualityIssue> allIssues = report.allIssues();
        int critical = (int) allIssues.stream().filter(i -> i.severity() == Severity.CRITICAL).count();
        int warning = (int) allIssues.stream().filter(i -> i.severity() == Severity.WARNING).count();
        int info = (int) allIssues.stream().filter(i -> i.severity() == Severity.INFO).count();

        List<IssueDetail> issues = allIssues.stream()
                .map(i -> new IssueDetail(
                        i.dimension(),
                        i.severity().name(),
                        i.message(),
                        i.ruleId()))
                .toList();

        return new PromptQualityResult(
                report.agentName(),
                profile.agentType(),
                report.overallScore(),
                threshold,
                report.passes(threshold),
                report.hasCriticalIssues(),
                report.analyzedAt().toString(),
                weakest,
                dimensions,
                new IssueSummary(allIssues.size(), critical, warning, info),
                issues,
                report.allSuggestions());
    }
}
