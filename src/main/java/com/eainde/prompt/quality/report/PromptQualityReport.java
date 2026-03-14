package com.eainde.prompt.quality.report;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.model.Severity;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Complete quality report for a single agent's prompt.
 */
public record PromptQualityReport(
        String agentName,
        AgentTypeProfile profile,
        List<DimensionResult> dimensionResults,
        double overallScore,
        LocalDateTime analyzedAt) {

    public boolean passes(double threshold) {
        return overallScore >= threshold;
    }

    public boolean hasCriticalIssues() {
        return dimensionResults.stream().anyMatch(DimensionResult::hasCriticalIssues);
    }

    public List<QualityIssue> allIssues() {
        return dimensionResults.stream()
                .flatMap(d -> d.issues().stream())
                .sorted(Comparator.comparing(QualityIssue::severity))
                .toList();
    }

    public List<QualityIssue> issuesBySeverity(Severity severity) {
        return allIssues().stream()
                .filter(i -> i.severity() == severity)
                .toList();
    }

    public List<String> allSuggestions() {
        return dimensionResults.stream()
                .flatMap(d -> d.suggestions().stream())
                .toList();
    }

    public int totalIssueCount() {
        return allIssues().size();
    }

    public DimensionResult lowestScoringDimension() {
        return dimensionResults.stream()
                .min(Comparator.comparingDouble(DimensionResult::score))
                .orElse(null);
    }

    public DimensionResult resultFor(String dimension) {
        return dimensionResults.stream()
                .filter(d -> d.dimension().equals(dimension))
                .findFirst()
                .orElse(null);
    }
}
