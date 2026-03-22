package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.fix.*;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;

import java.util.*;

/**
 * Analyzes token efficiency — is the prompt concise without losing quality?
 */
public class TokenEfficiencyAnalyzer implements PromptDimensionAnalyzer, FixGenerator {

    /** Max acceptable system prompt size in estimated tokens — TOK-001 check. */
    private static final int MAX_SYSTEM_TOKENS = 2000;
    /** Min system prompt size — too short means insufficient instructions (TOK-002). */
    private static final int MIN_SYSTEM_TOKENS = 100;
    /** Max user template overhead (excluding {{variables}}) — TOK-003 check. */
    private static final int MAX_USER_TEMPLATE_TOKENS = 500;

    /** Filler phrases that waste tokens without adding info — TOK-004 check. */
    private static final List<String> FILLER_PHRASES = List.of(
            "please note that", "it is important to remember",
            "it should be noted that", "keep in mind that",
            "as mentioned earlier", "as stated above",
            "in other words", "that is to say",
            "needless to say", "it goes without saying"
    );

    @Override
    public String dimensionName() {
        return "TOKEN_EFFICIENCY";
    }

    @Override
    public DimensionResult analyze(PromptUnderTest prompt) {
        List<QualityIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double totalPoints = 0;
        double maxPoints = 4;

        int systemTokens = prompt.systemPrompt().length() / 4;
        String userTemplateOnly = prompt.userPrompt().replaceAll("\\{\\{\\w+\\}\\}", "");
        int userTemplateTokens = userTemplateOnly.length() / 4;

        // Check 1: System prompt within budget
        if (systemTokens <= MAX_SYSTEM_TOKENS && systemTokens >= MIN_SYSTEM_TOKENS) {
            totalPoints += 1;
        } else if (systemTokens > MAX_SYSTEM_TOKENS) {
            issues.add(QualityIssue.warning("TOKEN_EFFICIENCY",
                    "System prompt estimated at " + systemTokens + " tokens (max "
                            + MAX_SYSTEM_TOKENS + "). Consider condensing.", "TOK-001"));
            suggestions.add("Reduce system prompt by removing filler text and "
                    + "combining related rules.");
        } else {
            issues.add(QualityIssue.info("TOKEN_EFFICIENCY",
                    "System prompt is very short (" + systemTokens + " tokens). "
                            + "Ensure it contains sufficient instructions.", "TOK-002"));
        }

        // Check 2: User template overhead
        if (userTemplateTokens <= MAX_USER_TEMPLATE_TOKENS) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.info("TOKEN_EFFICIENCY",
                    "User prompt template overhead is " + userTemplateTokens
                            + " tokens. Keep template text minimal.", "TOK-003"));
        }

        // Check 3: No filler phrases
        String lower = prompt.combinedPrompt().toLowerCase();
        List<String> foundFiller = FILLER_PHRASES.stream()
                .filter(lower::contains)
                .toList();
        if (foundFiller.isEmpty()) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.info("TOKEN_EFFICIENCY",
                    "Filler phrases found: " + foundFiller + ". These waste tokens "
                            + "without adding information.", "TOK-004"));
            suggestions.add("Remove filler phrases: " + foundFiller);
        }

        // Check 4: No significant repetition (improved with similarity matching)
        String[] sentences = prompt.systemPrompt().split("[.!?]\\s+");
        List<String> normSentences = new ArrayList<>();
        for (String s : sentences) {
            String norm = s.trim().toLowerCase().replaceAll("\\s+", " ");
            if (norm.length() > 20) normSentences.add(norm);
        }
        boolean hasDuplicates = false;
        for (int i = 0; i < normSentences.size() && !hasDuplicates; i++) {
            for (int j = i + 1; j < normSentences.size(); j++) {
                if (similarity(normSentences.get(i), normSentences.get(j)) > 0.75) {
                    hasDuplicates = true;
                    break;
                }
            }
        }
        if (!hasDuplicates) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.info("TOKEN_EFFICIENCY",
                    "Near-duplicate sentences detected. Remove redundancy.",
                    "TOK-005"));
        }

        // Check 5: Redundant constraints (TOK-006)
        boolean hasRedundantConstraints = false;
        for (int i = 0; i < normSentences.size() && !hasRedundantConstraints; i++) {
            for (int j = i + 1; j < normSentences.size(); j++) {
                double sim = similarity(normSentences.get(i), normSentences.get(j));
                if (sim > 0.6 && sim <= 0.75) {
                    // Similar but not duplicate — likely redundant constraint
                    hasRedundantConstraints = true;
                    break;
                }
            }
        }
        if (hasRedundantConstraints) {
            issues.add(QualityIssue.info("TOKEN_EFFICIENCY",
                    "Potentially redundant constraints detected. Same rule may be "
                            + "expressed differently in multiple places.", "TOK-006"));
        }

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("TOKEN_EFFICIENCY", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }

    @Override
    public List<PromptFix> suggestFixes(PromptUnderTest prompt, DimensionResult result) {
        List<PromptFix> fixes = new ArrayList<>();
        for (QualityIssue issue : result.issues()) {
            if ("TOK-004".equals(issue.ruleId())) {
                String system = prompt.systemPrompt();
                for (String filler : FILLER_PHRASES) {
                    String lower = system.toLowerCase();
                    int idx = lower.indexOf(filler);
                    if (idx >= 0) {
                        String actual = system.substring(idx, idx + filler.length());
                        fixes.add(new PromptFix("TOK-004",
                                "Remove filler phrase: '" + filler + "'",
                                FixType.REPLACE, FixLocation.SYSTEM_PROMPT,
                                actual, "", FixConfidence.HIGH));
                    }
                }
            }
        }
        return fixes;
    }

    private double similarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshteinDistance(a, b) / maxLen;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                dp[i][j] = Math.min(dp[i - 1][j] + 1,
                        Math.min(dp[i][j - 1] + 1,
                                dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)));
        return dp[a.length()][b.length()];
    }
}
