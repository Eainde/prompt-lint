package com.eainde.prompt.quality.report;

import com.eainde.prompt.quality.fix.PromptFix;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.model.Severity;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Complete quality report for a single agent's prompt.
 *
 * <p>Produced by {@link com.eainde.prompt.quality.PromptQualityAnalyzer#analyze}.
 * Contains per-dimension scores, all detected issues sorted by severity,
 * and actionable fix suggestions.</p>
 *
 * @param agentName       identifier of the agent whose prompt was analyzed
 * @param profile         agent type profile used for weight calculation
 * @param dimensionResults per-dimension analysis results (scores + issues)
 * @param overallScore    weighted composite score (0.0–1.0)
 * @param analyzedAt      timestamp of analysis
 * @param suggestedFixes  auto-generated fix suggestions from analyzers implementing {@link com.eainde.prompt.quality.fix.FixGenerator}
 */
public record PromptQualityReport(
        String agentName,
        AgentTypeProfile profile,
        List<DimensionResult> dimensionResults,
        double overallScore,
        LocalDateTime analyzedAt,
        List<PromptFix> suggestedFixes) {

    public PromptQualityReport(String agentName, AgentTypeProfile profile,
                               List<DimensionResult> dimensionResults,
                               double overallScore, LocalDateTime analyzedAt) {
        this(agentName, profile, dimensionResults, overallScore, analyzedAt, List.of());
    }

    /** Returns true if overall score meets or exceeds the threshold. */
    public boolean passes(double threshold) {
        return overallScore >= threshold;
    }

    /** Returns true if any dimension has CRITICAL severity issues. */
    public boolean hasCriticalIssues() {
        return dimensionResults.stream().anyMatch(DimensionResult::hasCriticalIssues);
    }

    /** All issues across all dimensions, sorted by severity (CRITICAL first). */
    public List<QualityIssue> allIssues() {
        return dimensionResults.stream()
                .flatMap(d -> d.issues().stream())
                .sorted(Comparator.comparing(QualityIssue::severity))
                .toList();
    }

    /** Filters issues to only those matching the given severity level. */
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

    /** Returns the dimension with the lowest score — the weakest area to improve. */
    public DimensionResult lowestScoringDimension() {
        return dimensionResults.stream()
                .min(Comparator.comparingDouble(DimensionResult::score))
                .orElse(null);
    }

    /** Looks up the result for a specific dimension by name. Returns null if not found. */
    public DimensionResult resultFor(String dimension) {
        return dimensionResults.stream()
                .filter(d -> d.dimension().equals(dimension))
                .findFirst()
                .orElse(null);
    }
}
