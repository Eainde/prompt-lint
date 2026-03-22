package com.eainde.prompt.quality.analyzers;

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

    /** Phrases for handling empty/missing input — CON-001 check. */
    private static final List<String> EMPTY_HANDLING = List.of(
            "if no", "if none", "if empty", "if zero", "when no",
            "empty array", "empty result", "no candidates", "no persons"
    );

    /** Guidance for ambiguous cases — CON-002 check. */
    private static final List<String> UNCERTAINTY_HANDLING = List.of(
            "if unsure", "when unsure", "when in doubt", "if uncertain",
            "if unclear", "if ambiguous", "cannot determine", "unknown"
    );

    /** Prohibition keywords — 3+ expected for good coverage (CON-003). */
    private static final List<String> NEGATIVE_INSTRUCTIONS = List.of(
            "do not", "never", "must not", "should not",
            "avoid", "exclude", "skip", "ignore"
    );

    /** Default/fallback value indicators — CON-006 check. */
    private static final List<String> DEFAULT_VALUE_MARKERS = List.of(
            "default", "defaults to", "if not provided", "if missing",
            "fall back", "fallback"
    );

    /** Large input handling phrases — CON-007 check. */
    private static final List<String> INPUT_SIZE_HANDLING = List.of(
            "if input exceeds", "truncate", "paginate", "too large",
            "maximum length", "split into", "if too long", "max tokens",
            "character limit"
    );

    /** Field-level constraint keywords — CON-004 check. */
    private static final List<String> FIELD_CONSTRAINTS = List.of(
            "null", "nullable", "required", "optional", "must be",
            "must have", "cannot be", "valid values", "one of"
    );

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
        boolean hasEmptyHandling = EMPTY_HANDLING.stream().anyMatch(lower::contains);
        if (hasEmptyHandling) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("CONSTRAINT_COVERAGE",
                    "No instructions for empty/missing input. What should the agent "
                            + "do if the document has no persons?", "CON-001"));
            suggestions.add("Add: 'If no persons are found, return {\"candidates\": []}'");
        }

        // Check 2: Uncertainty handling
        boolean hasUncertainty = UNCERTAINTY_HANDLING.stream().anyMatch(lower::contains);
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
        long negativeCount = NEGATIVE_INSTRUCTIONS.stream()
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
        long fieldConstraintCount = FIELD_CONSTRAINTS.stream()
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
            boolean hasDefaults = DEFAULT_VALUE_MARKERS.stream().anyMatch(lower::contains);
            if (!hasDefaults) {
                issues.add(QualityIssue.info("CONSTRAINT_COVERAGE",
                        "Prompt mentions optional/nullable fields but does not specify "
                                + "default values.", "CON-006"));
            }
        }

        // Check 7: Input size boundary handling (CON-007)
        boolean hasInputSizeHandling = INPUT_SIZE_HANDLING.stream().anyMatch(lower::contains);
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
