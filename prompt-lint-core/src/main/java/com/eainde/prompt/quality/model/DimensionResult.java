package com.eainde.prompt.quality.model;

import java.util.List;

/**
 * Result of a single quality dimension analysis.
 *
 * @param dimension   name of the dimension (e.g., "CLARITY", "GROUNDEDNESS")
 * @param score       0.0 to 1.0 score for this dimension
 * @param maxScore    maximum possible score (always 1.0, for display)
 * @param issues      issues found during analysis
 * @param suggestions improvement suggestions
 */
public record DimensionResult(
        String dimension,
        double score,
        double maxScore,
        List<QualityIssue> issues,
        List<String> suggestions) {

    public boolean hasCriticalIssues() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.CRITICAL);
    }
}
