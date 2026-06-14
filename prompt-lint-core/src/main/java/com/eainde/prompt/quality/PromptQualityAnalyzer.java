package com.eainde.prompt.quality;

import com.eainde.prompt.quality.analyzers.*;
import com.eainde.prompt.quality.api.PromptQualityResult;
import com.eainde.prompt.quality.config.Baseline;
import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.config.LexiconAware;
import com.eainde.prompt.quality.config.LintConfig;
import com.eainde.prompt.quality.fix.FixGenerator;
import com.eainde.prompt.quality.fix.PromptFix;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.model.Severity;
import com.eainde.prompt.quality.model.SeverityCalibrator;
import com.eainde.prompt.quality.report.PromptQualityReport;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final LintConfig config;

    PromptQualityAnalyzer(List<PromptDimensionAnalyzer> analyzers) {
        this(analyzers, LintConfig.none());
    }

    PromptQualityAnalyzer(List<PromptDimensionAnalyzer> analyzers, LintConfig config) {
        this.analyzers = List.copyOf(analyzers);
        this.config = config;
    }

    /**
     * Creates an analyzer with all 8 standard dimension analyzers.
     */
    public static PromptQualityAnalyzer create() {
        return builder().build();
    }

    /** Returns a new builder for configuring the analyzer. */
    public static Builder builder() {
        return new Builder();
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
        return new PromptQualityAnalyzer(allAnalyzers, this.config);
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

        // Filter suppressed/baselined issues; then calibrate (profile) and apply user severity overrides
        results = results.stream().map(r -> {
            List<QualityIssue> processed = r.issues().stream()
                    .filter(issue -> !config.isFiltered(prompt.agentName(), issue.ruleId()))
                    .map(issue -> {
                        Severity cal = config.severityOverrides().getOrDefault(
                                issue.ruleId(),
                                SeverityCalibrator.calibrate(issue, prompt.agentTypeProfile()));
                        if (cal != issue.severity()) {
                            return new QualityIssue(issue.dimension(), cal, issue.message(), issue.ruleId());
                        }
                        return issue;
                    })
                    .toList();
            return processed.equals(r.issues()) ? r
                    : new DimensionResult(r.dimension(), r.score(), r.maxScore(), processed, r.suggestions());
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

    /**
     * Registers plugin-declared categories (if absent) and hands the resolved
     * lexicon to LexiconAware plugins. Public so tests outside this package
     * can exercise the contract directly.
     */
    public static Lexicon prepareLexiconForPlugins(Lexicon lexicon,
                                                   List<PromptDimensionAnalyzer> plugins) {
        Lexicon resolved = lexicon;
        for (var plugin : plugins) {
            if (plugin instanceof LexiconAware aware) {
                for (var entry : aware.declaredCategories().entrySet()) {
                    if (!resolved.hasCategory(entry.getKey())) {
                        resolved = resolved.withCategory(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        for (var plugin : plugins) {
            if (plugin instanceof LexiconAware aware) {
                aware.setLexicon(resolved);
            }
        }
        return resolved;
    }

    /** Builder for configuring a {@code PromptQualityAnalyzer}. */
    public static final class Builder {
        private Lexicon lexicon = Lexicon.defaults();
        private final Map<String, Severity> severityOverrides = new HashMap<>();
        private final Set<String> suppressedRules = new HashSet<>();
        private Path baselinePath;

        private Builder() {}

        /** Keyword lists for all analyzers. Default: {@link Lexicon#defaults()}. */
        public Builder lexicon(Lexicon lexicon) {
            Objects.requireNonNull(lexicon, "lexicon must not be null — use Lexicon.defaults()");
            this.lexicon = lexicon;
            return this;
        }

        /** Remaps a rule's severity. Wins over profile calibration. */
        public Builder severityOverride(String ruleId, Severity severity) {
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(severity, "severity");
            severityOverrides.put(ruleId, severity);
            return this;
        }

        /** Hides the rule's issues from reports. Scores are NOT affected. */
        public Builder suppress(String ruleId) {
            Objects.requireNonNull(ruleId, "ruleId");
            suppressedRules.add(ruleId);
            return this;
        }

        /** Same as {@link #suppress(String)}; the reason is documentation only. */
        public Builder suppress(String ruleId, String reason) {
            return suppress(ruleId);
        }

        /**
         * Filters issues recorded in the baseline file ({@code agentName:ruleId}
         * fingerprints) so only new issues surface. Scores are NOT affected.
         * Missing file fails here, at build time.
         */
        public Builder baseline(Path baselineFile) {
            this.baselinePath = baselineFile;
            return this;
        }

        /**
         * Builds an immutable {@code PromptQualityAnalyzer}.
         *
         * <p>SPI plugins are discovered via {@link ServiceLoader} and appended after
         * the 8 built-in dimension analyzers. Plugins without a {@code @DimensionMeta}
         * annotation are skipped with a warning.</p>
         *
         * <p>If a baseline file was configured via {@link #baseline(Path)}, it is
         * loaded here at build time. A missing or unreadable file throws
         * {@link java.io.UncheckedIOException} immediately (fail-fast).</p>
         *
         * <p>The returned analyzer is immutable: its analyzer list and config are
         * defensively copied and cannot be mutated after this call.</p>
         */
        public PromptQualityAnalyzer build() {
            var plugins = new ArrayList<PromptDimensionAnalyzer>();
            ServiceLoader.load(PromptDimensionAnalyzer.class).forEach(plugin -> {
                var meta = plugin.getClass().getAnnotation(DimensionMeta.class);
                if (meta == null) {
                    System.err.println("[prompt-lint] WARNING: Skipping plugin "
                            + plugin.getClass().getName() + " — missing @DimensionMeta annotation");
                    return;
                }
                plugins.add(plugin);
            });

            Lexicon resolvedLexicon = prepareLexiconForPlugins(lexicon, plugins);

            var analyzers = new ArrayList<PromptDimensionAnalyzer>(List.of(
                    new ClarityAnalyzer(resolvedLexicon),
                    new SpecificityAnalyzer(resolvedLexicon),
                    new GroundednessAnalyzer(resolvedLexicon),
                    new OutputContractAnalyzer(),
                    new ConstraintCoverageAnalyzer(resolvedLexicon),
                    new ConsistencyAnalyzer(resolvedLexicon),
                    new TokenEfficiencyAnalyzer(resolvedLexicon),
                    new InjectionResistanceAnalyzer(resolvedLexicon)
            ));
            analyzers.addAll(plugins);

            Baseline baseline = baselinePath != null ? Baseline.load(baselinePath) : null;
            var config = new LintConfig(resolvedLexicon, Map.copyOf(severityOverrides),
                    Set.copyOf(suppressedRules), baseline);
            return new PromptQualityAnalyzer(analyzers, config);
        }
    }
}
