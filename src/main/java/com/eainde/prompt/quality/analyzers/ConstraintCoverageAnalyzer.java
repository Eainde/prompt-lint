package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Analyzes constraint coverage — does the prompt cover edge cases and failure modes?
 */
public class ConstraintCoverageAnalyzer implements PromptDimensionAnalyzer {

    private static final List<String> EMPTY_HANDLING = List.of(
            "if no", "if none", "if empty", "if zero", "when no",
            "empty array", "empty result", "no candidates", "no persons"
    );

    private static final List<String> UNCERTAINTY_HANDLING = List.of(
            "if unsure", "when unsure", "when in doubt", "if uncertain",
            "if unclear", "if ambiguous", "cannot determine", "unknown"
    );

    private static final List<String> NEGATIVE_INSTRUCTIONS = List.of(
            "do not", "never", "must not", "should not",
            "avoid", "exclude", "skip", "ignore"
    );

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

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("CONSTRAINT_COVERAGE", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }
}
