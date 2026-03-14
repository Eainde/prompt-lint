package com.eainde.prompt.quality;

import com.eainde.prompt.quality.analyzers.*;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for prompt quality analysis.
 *
 * <p>Runs all dimension analyzers against a prompt and produces a weighted
 * composite quality report. No LLM calls — all analysis is rule-based.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * var analyzer = PromptQualityAnalyzer.create();
 *
 * var prompt = new PromptUnderTest(
 *     "csm-candidate-extractor",
 *     systemPromptText,
 *     userPromptText,
 *     Set.of("sourceText", "fileNames"),
 *     "rawNames",
 *     AgentTypeProfile.EXTRACTION
 * );
 *
 * PromptQualityReport report = analyzer.analyze(prompt);
 *
 * // Assert in test
 * assertThat(report.overallScore()).isGreaterThanOrEqualTo(0.75);
 * assertThat(report.hasCriticalIssues()).isFalse();
 * </pre>
 */
public class PromptQualityAnalyzer {

    private final List<PromptDimensionAnalyzer> analyzers;

    private PromptQualityAnalyzer(List<PromptDimensionAnalyzer> analyzers) {
        this.analyzers = List.copyOf(analyzers);
    }

    /**
     * Creates an analyzer with all 8 standard dimension analyzers.
     */
    public static PromptQualityAnalyzer create() {
        return new PromptQualityAnalyzer(List.of(
                new ClarityAnalyzer(),
                new SpecificityAnalyzer(),
                new GroundednessAnalyzer(),
                new OutputContractAnalyzer(),
                new ConstraintCoverageAnalyzer(),
                new ConsistencyAnalyzer(),
                new TokenEfficiencyAnalyzer(),
                new InjectionResistanceAnalyzer()
        ));
    }

    /**
     * Creates an analyzer with a custom set of dimension analyzers.
     * Useful for testing only specific dimensions.
     */
    public static PromptQualityAnalyzer withAnalyzers(PromptDimensionAnalyzer... analyzers) {
        return new PromptQualityAnalyzer(List.of(analyzers));
    }

    /**
     * Analyzes the prompt across all registered dimensions and produces
     * a weighted composite report.
     *
     * @param prompt the prompt to analyze
     * @return complete quality report with scores, issues, and suggestions
     */
    public PromptQualityReport analyze(PromptUnderTest prompt) {
        List<DimensionResult> results = new ArrayList<>();

        for (PromptDimensionAnalyzer analyzer : analyzers) {
            DimensionResult result = analyzer.analyze(prompt);
            results.add(result);
        }

        double overallScore = computeWeightedScore(results, prompt.agentTypeProfile());

        return new PromptQualityReport(
                prompt.agentName(),
                prompt.agentTypeProfile(),
                results,
                overallScore,
                LocalDateTime.now()
        );
    }

    /**
     * Analyzes multiple prompts and returns all reports.
     * Useful for CI pipeline that validates all agent prompts at once.
     */
    public List<PromptQualityReport> analyzeAll(List<PromptUnderTest> prompts) {
        return prompts.stream()
                .map(this::analyze)
                .toList();
    }

    private double computeWeightedScore(List<DimensionResult> results,
                                         AgentTypeProfile profile) {
        double weightedSum = 0;
        double totalWeight = 0;

        for (DimensionResult result : results) {
            double weight = profile.weightFor(result.dimension());
            weightedSum += result.score() * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0;
    }
}
