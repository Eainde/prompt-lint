package com.eainde.prompt.quality;

import com.eainde.prompt.quality.analyzers.*;
import com.eainde.prompt.quality.api.PromptQualityResult;
import com.eainde.prompt.quality.fix.FixGenerator;
import com.eainde.prompt.quality.fix.PromptFix;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.model.Severity;
import com.eainde.prompt.quality.model.SeverityCalibrator;
import com.eainde.prompt.quality.report.PromptQualityReport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

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

    PromptQualityAnalyzer(List<PromptDimensionAnalyzer> analyzers) {
        this.analyzers = List.copyOf(analyzers);
    }

    /**
     * Creates an analyzer with all 8 standard dimension analyzers.
     */
    public static PromptQualityAnalyzer create() {
        var analyzers = new ArrayList<PromptDimensionAnalyzer>();
        analyzers.addAll(List.of(
                new ClarityAnalyzer(),
                new SpecificityAnalyzer(),
                new GroundednessAnalyzer(),
                new OutputContractAnalyzer(),
                new ConstraintCoverageAnalyzer(),
                new ConsistencyAnalyzer(),
                new TokenEfficiencyAnalyzer(),
                new InjectionResistanceAnalyzer()
        ));
        ServiceLoader.load(PromptDimensionAnalyzer.class).forEach(plugin -> {
            var meta = plugin.getClass().getAnnotation(DimensionMeta.class);
            if (meta == null) {
                System.err.println("[prompt-lint] WARNING: Skipping plugin "
                        + plugin.getClass().getName() + " — missing @DimensionMeta annotation");
                return;
            }
            analyzers.add(plugin);
        });
        return new PromptQualityAnalyzer(analyzers);
    }

    /**
     * Creates an analyzer with a custom set of dimension analyzers.
     * Useful for testing only specific dimensions.
     */
    public static PromptQualityAnalyzer withAnalyzers(PromptDimensionAnalyzer... analyzers) {
        return new PromptQualityAnalyzer(List.of(analyzers));
    }

    /**
     * Returns a new analyzer with additional custom dimensions appended.
     * Duplicate dimension names are rejected.
     */
    public PromptQualityAnalyzer withAdditionalAnalyzers(PromptDimensionAnalyzer... additional) {
        var allAnalyzers = new ArrayList<>(this.analyzers);
        var existingNames = this.analyzers.stream()
                .map(PromptDimensionAnalyzer::dimensionName)
                .collect(Collectors.toSet());
        for (var analyzer : additional) {
            if (existingNames.contains(analyzer.dimensionName())) {
                throw new IllegalArgumentException(
                        "Duplicate dimension name: " + analyzer.dimensionName());
            }
            existingNames.add(analyzer.dimensionName());
            allAnalyzers.add(analyzer);
        }
        return new PromptQualityAnalyzer(allAnalyzers);
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
            try {
                results.add(analyzer.analyze(prompt));
            } catch (Exception e) {
                results.add(new DimensionResult(
                        analyzer.dimensionName(), 0.0, 1.0,
                        List.of(QualityIssue.warning(analyzer.dimensionName(),
                                "Analyzer failed: " + e.getMessage(),
                                analyzer.dimensionName() + "-ERR")),
                        List.of()
                ));
            }
        }

        // Calibrate severities based on agent profile
        results = results.stream().map(r -> {
            List<QualityIssue> calibrated = r.issues().stream()
                    .map(issue -> {
                        Severity cal = SeverityCalibrator.calibrate(issue, prompt.agentTypeProfile());
                        if (cal != issue.severity()) {
                            return new QualityIssue(issue.dimension(), cal, issue.message(), issue.ruleId());
                        }
                        return issue;
                    })
                    .toList();
            return calibrated.equals(r.issues()) ? r
                    : new DimensionResult(r.dimension(), r.score(), r.maxScore(), calibrated, r.suggestions());
        }).toList();

        double overallScore = computeWeightedScore(results, prompt.agentTypeProfile());

        List<PromptFix> allFixes = new ArrayList<>();
        for (var analyzer : analyzers) {
            if (analyzer instanceof FixGenerator fg) {
                results.stream()
                        .filter(r -> r.dimension().equals(analyzer.dimensionName()))
                        .findFirst()
                        .ifPresent(r -> allFixes.addAll(fg.suggestFixes(prompt, r)));
            }
        }

        return new PromptQualityReport(
                prompt.agentName(),
                prompt.agentTypeProfile(),
                results,
                overallScore,
                LocalDateTime.now(),
                allFixes
        );
    }

    /**
     * Analyzes the prompt and returns a structured result object.
     */
    public PromptQualityResult analyzeAndReport(PromptUnderTest prompt, double threshold) {
        PromptQualityReport report = analyze(prompt);
        return PromptQualityResult.from(report, threshold);
    }

    /**
     * Convenience method for REST controllers — accepts raw inputs and resolves the profile.
     *
     * <p>Profile resolution: customWeights (if non-empty) > profileName > DEFAULT.</p>
     */
    public PromptQualityResult analyzeAndReport(
            String agentName, String systemPrompt, String userPrompt,
            Set<String> declaredInputs, String declaredOutputKey,
            String profileName, Map<String, Double> customWeights,
            String responseSchema, double threshold) {

        AgentTypeProfile profile;
        if (customWeights != null && !customWeights.isEmpty()) {
            profile = new AgentTypeProfile("CUSTOM", customWeights);
        } else if (profileName != null && !profileName.isBlank()) {
            profile = AgentTypeProfile.fromName(profileName);
        } else {
            profile = AgentTypeProfile.DEFAULT;
        }

        PromptUnderTest prompt = new PromptUnderTest(
                agentName, systemPrompt, userPrompt,
                declaredInputs, declaredOutputKey, profile, responseSchema);

        return analyzeAndReport(prompt, threshold);
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
