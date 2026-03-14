package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;

import java.util.*;

/**
 * Analyzes token efficiency — is the prompt concise without losing quality?
 */
public class TokenEfficiencyAnalyzer implements PromptDimensionAnalyzer {

    private static final int MAX_SYSTEM_TOKENS = 2000;
    private static final int MIN_SYSTEM_TOKENS = 100;
    private static final int MAX_USER_TEMPLATE_TOKENS = 500;

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

        // Check 4: No significant repetition
        String[] sentences = prompt.systemPrompt().split("[.!?]\\s+");
        Set<String> normalized = new HashSet<>();
        int duplicates = 0;
        for (String sentence : sentences) {
            String norm = sentence.trim().toLowerCase().replaceAll("\\s+", " ");
            if (norm.length() > 20 && !normalized.add(norm)) {
                duplicates++;
            }
        }
        if (duplicates == 0) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.info("TOKEN_EFFICIENCY",
                    duplicates + " repeated sentence(s) detected. Remove redundancy.",
                    "TOK-005"));
        }

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("TOKEN_EFFICIENCY", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }
}
