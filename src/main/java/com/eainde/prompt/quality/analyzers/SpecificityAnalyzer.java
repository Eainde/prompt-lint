package com.eainde.prompt.quality.analyzers;

import com.db.clm.kyc.ai.testing.quality.model.*;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes prompt specificity — does the prompt give concrete rules
 * instead of vague instructions?
 */
public class SpecificityAnalyzer implements PromptDimensionAnalyzer {

    /** Matches rule identifiers like RC1, A6.1, C3.1, JT.6, QG10, D0-D6, L1-L9 */
    private static final Pattern RULE_ID_PATTERN =
            Pattern.compile("\\b[A-Z]{1,3}\\d+(?:\\.\\d+)?\\b");

    /** Matches specific numeric thresholds: >= 0.85, > 0, < 50, = true */
    private static final Pattern THRESHOLD_PATTERN =
            Pattern.compile("[><=]=?\\s*\\d+\\.?\\d*");

    /** Matches conditional logic: "If X", "When X", "Unless X" */
    private static final Pattern CONDITIONAL_PATTERN =
            Pattern.compile("(?i)\\b(if\\s+[^,.]{5,}|when\\s+[^,.]{5,}|unless\\s+[^,.]{5,})");

    /** Matches positive examples: ✓, "valid", "correct", "Example:" */
    private static final Pattern POSITIVE_EXAMPLE_PATTERN =
            Pattern.compile("(?i)(✓|✔|\\bvalid\\b|\\bcorrect\\b|\\bexample:|e\\.g\\.)");

    /** Matches negative examples: ✗, ✘, "invalid", "wrong", "do NOT" */
    private static final Pattern NEGATIVE_EXAMPLE_PATTERN =
            Pattern.compile("(?i)(✗|✘|✕|\\binvalid\\b|\\bwrong\\b|\\bdo\\s+not\\b|\\bnever\\b)");

    private static final List<String> OPEN_ENDED_PHRASES = List.of(
            "be creative", "use your judgment", "do your best",
            "use common sense", "figure out", "as you see fit",
            "whatever you think", "at your discretion"
    );

    @Override
    public String dimensionName() {
        return "SPECIFICITY";
    }

    @Override
    public DimensionResult analyze(PromptUnderTest prompt) {
        List<QualityIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double totalPoints = 0;
        double maxPoints = 6;

        String system = prompt.systemPrompt();
        String systemLower = system.toLowerCase();

        // ── Check 1: Named rule identifiers ─────────────────────────────
        Matcher ruleIdMatcher = RULE_ID_PATTERN.matcher(system);
        int ruleCount = 0;
        while (ruleIdMatcher.find()) ruleCount++;

        if (ruleCount >= 5) {
            totalPoints += 1;
        } else if (ruleCount >= 2) {
            totalPoints += 0.5;
            issues.add(QualityIssue.info("SPECIFICITY",
                    "Only " + ruleCount + " named rules found. More specific rules "
                            + "improve LLM compliance.", "SPC-001"));
        } else {
            issues.add(QualityIssue.warning("SPECIFICITY",
                    "Very few named rules (" + ruleCount + "). Use identifiers like "
                            + "RC1, A1, C3 so rules can be referenced.", "SPC-001"));
            suggestions.add("Give each rule a unique identifier (e.g., RC1, A1, JT.1) "
                    + "for traceability.");
        }

        // ── Check 2: Specific thresholds/values ─────────────────────────
        Matcher thresholdMatcher = THRESHOLD_PATTERN.matcher(system);
        int thresholdCount = 0;
        while (thresholdMatcher.find()) thresholdCount++;

        if (thresholdCount >= 3) {
            totalPoints += 1;
        } else if (thresholdCount >= 1) {
            totalPoints += 0.5;
        } else {
            issues.add(QualityIssue.info("SPECIFICITY",
                    "No specific thresholds or values found. Consider adding concrete "
                            + "numbers (e.g., 'max 50 words', 'score >= 0.85').", "SPC-002"));
        }

        // ── Check 3: Conditional logic ──────────────────────────────────
        Matcher conditionalMatcher = CONDITIONAL_PATTERN.matcher(system);
        int conditionalCount = 0;
        while (conditionalMatcher.find()) conditionalCount++;

        if (conditionalCount >= 3) {
            totalPoints += 1;
        } else if (conditionalCount >= 1) {
            totalPoints += 0.5;
        } else {
            issues.add(QualityIssue.info("SPECIFICITY",
                    "No conditional logic found. Adding 'If X, then Y' statements "
                            + "helps the LLM handle edge cases.", "SPC-003"));
        }

        // ── Check 4: Positive examples ──────────────────────────────────
        Matcher posExMatcher = POSITIVE_EXAMPLE_PATTERN.matcher(system);
        int positiveExampleCount = 0;
        while (posExMatcher.find()) positiveExampleCount++;

        if (positiveExampleCount >= 2) {
            totalPoints += 1;
        } else if (positiveExampleCount >= 1) {
            totalPoints += 0.5;
            issues.add(QualityIssue.info("SPECIFICITY",
                    "Only " + positiveExampleCount + " positive example(s). "
                            + "More examples improve output quality.", "SPC-004"));
        } else {
            issues.add(QualityIssue.warning("SPECIFICITY",
                    "No positive examples found. Show the LLM what CORRECT output "
                            + "looks like.", "SPC-004"));
            suggestions.add("Add at least 2 positive examples showing expected behavior.");
        }

        // ── Check 5: Negative examples ──────────────────────────────────
        Matcher negExMatcher = NEGATIVE_EXAMPLE_PATTERN.matcher(system);
        int negativeExampleCount = 0;
        while (negExMatcher.find()) negativeExampleCount++;

        if (negativeExampleCount >= 2) {
            totalPoints += 1;
        } else if (negativeExampleCount >= 1) {
            totalPoints += 0.5;
        } else {
            issues.add(QualityIssue.warning("SPECIFICITY",
                    "No negative examples found. Show the LLM what NOT to do.",
                    "SPC-005"));
            suggestions.add("Add negative examples: 'Do NOT...', 'NEVER...', "
                    + "'✗ Invalid: ...'");
        }

        // ── Check 6: No open-ended instructions ─────────────────────────
        List<String> foundOpenEnded = OPEN_ENDED_PHRASES.stream()
                .filter(systemLower::contains)
                .toList();
        if (foundOpenEnded.isEmpty()) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("SPECIFICITY",
                    "Open-ended phrases found: " + foundOpenEnded
                            + ". These give the LLM too much freedom.", "SPC-006"));
            suggestions.add("Replace open-ended phrases with specific instructions.");
        }

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("SPECIFICITY", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }
}
