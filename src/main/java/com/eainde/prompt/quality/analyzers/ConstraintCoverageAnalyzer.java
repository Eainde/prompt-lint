package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.fix.*;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Analyzes constraint coverage — does the prompt cover edge cases and failure modes?
 */
public class ConstraintCoverageAnalyzer implements PromptDimensionAnalyzer, FixGenerator {

    private final Lexicon lexicon;

    public ConstraintCoverageAnalyzer() {
        this(Lexicon.defaults());
    }

    public ConstraintCoverageAnalyzer(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    @Override
    public String dimensionName() {
        return "CONSTRAINT_COVERAGE";
    }

    @Override
    public DimensionResult analyze(PromptUnderTest prompt) {
        List<QualityIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double totalPoints = 0;
        double maxPoints = 5;

        String lower = prompt.combinedPrompt().toLowerCase();

        // Check 1: Empty/missing input handling
        boolean hasEmptyHandling = lexicon.keywords("constraints.empty-handling").stream().anyMatch(lower::contains);
        if (hasEmptyHandling) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("CONSTRAINT_COVERAGE",
                    "No instructions for empty/missing input. What should the agent "
                            + "do if the document has no persons?", "CON-001"));
            suggestions.add("Add: 'If no persons are found, return {\"candidates\": []}'");
        }

        // Check 2: Uncertainty handling
        boolean hasUncertainty = lexicon.keywords("constraints.uncertainty-handling").stream().anyMatch(lower::contains);
        if (hasUncertainty) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.info("CONSTRAINT_COVERAGE",
                    "No 'when in doubt' instructions. Consider adding guidance "
                            + "for ambiguous cases.", "CON-002"));
            suggestions.add("Add: 'If unsure whether something is a person or entity, "
                    + "include it with a note.'");
        }

        // Check 3: Negative instructions (what NOT to do)
        long negativeCount = lexicon.keywords("constraints.negative-instructions").stream()
                .filter(lower::contains)
                .count();
        if (negativeCount >= 3) {
            totalPoints += 1;
        } else if (negativeCount >= 1) {
            totalPoints += 0.5;
        } else {
            issues.add(QualityIssue.warning("CONSTRAINT_COVERAGE",
                    "No negative instructions found. Tell the agent what NOT to do.",
                    "CON-003"));
        }

        // Check 4: Field-level constraints
        long fieldConstraintCount = lexicon.keywords("constraints.field-constraints").stream()
                .filter(lower::contains)
                .count();
        if (fieldConstraintCount >= 3) {
            totalPoints += 1;
        } else if (fieldConstraintCount >= 1) {
            totalPoints += 0.5;
        } else {
            issues.add(QualityIssue.info("CONSTRAINT_COVERAGE",
                    "Few field-level constraints. Specify which fields are nullable, "
                            + "required, or have valid value sets.", "CON-004"));
        }

        // Check 5: Enum/valid values specified
        boolean hasEnums = lower.contains("one of") || lower.contains("valid values")
                || lower.contains("must be one of")
                || Pattern.compile("\"\\w+\"\\s*,\\s*\"\\w+\"\\s*,\\s*\"\\w+\"")
                .matcher(prompt.systemPrompt()).find();
        if (hasEnums) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.info("CONSTRAINT_COVERAGE",
                    "No enum/valid value sets found. If fields have restricted values "
                            + "(e.g., temporalStatus: current/former/unknown), list them.",
                    "CON-005"));
        }

        // Check 6: Default value gaps (CON-006)
        boolean mentionsOptional = lower.contains("optional") || lower.contains("nullable")
                || lower.contains("null");
        if (mentionsOptional) {
            boolean hasDefaults = lexicon.keywords("constraints.default-value-markers").stream().anyMatch(lower::contains);
            if (!hasDefaults) {
                issues.add(QualityIssue.info("CONSTRAINT_COVERAGE",
                        "Prompt mentions optional/nullable fields but does not specify "
                                + "default values.", "CON-006"));
            }
        }

        // Check 7: Input size boundary handling (CON-007)
        boolean hasInputSizeHandling = lexicon.keywords("constraints.input-size-handling").stream().anyMatch(lower::contains);
        if (!hasInputSizeHandling) {
            issues.add(QualityIssue.info("CONSTRAINT_COVERAGE",
                    "No input size boundary handling. Consider adding instructions for "
                            + "truncation or pagination of large inputs.", "CON-007"));
        }

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("CONSTRAINT_COVERAGE", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }

    @Override
    public List<PromptFix> suggestFixes(PromptUnderTest prompt, DimensionResult result) {
        List<PromptFix> fixes = new ArrayList<>();
        for (QualityIssue issue : result.issues()) {
            switch (issue.ruleId()) {
                case "CON-001" -> fixes.add(new PromptFix("CON-001",
                        "Add empty input handling instruction",
                        FixType.INSERT, FixLocation.SYSTEM_PROMPT, null,
                        "If no data is found, return an empty result.\n",
                        FixConfidence.HIGH));
                case "CON-002" -> fixes.add(new PromptFix("CON-002",
                        "Add uncertainty handling instruction",
                        FixType.INSERT, FixLocation.SYSTEM_PROMPT, null,
                        "If unsure about a match, mark it as uncertain.\n",
                        FixConfidence.MEDIUM));
            }
        }
        return fixes;
    }
}
