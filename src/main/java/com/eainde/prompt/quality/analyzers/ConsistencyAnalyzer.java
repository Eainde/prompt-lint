package com.eainde.prompt.quality.analyzers;

import com.db.clm.kyc.ai.testing.quality.model.*;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes internal consistency — field names, rule numbering, variable alignment.
 */
public class ConsistencyAnalyzer implements PromptDimensionAnalyzer {

    private static final Pattern RULE_NUMBER_PATTERN =
            Pattern.compile("\\b([A-Z]{1,3})(\\d+)\\b");

    private static final Pattern TEMPLATE_VAR_PATTERN =
            Pattern.compile("\\{\\{(\\w+)\\}\\}");

    @Override
    public String dimensionName() {
        return "CONSISTENCY";
    }

    @Override
    public DimensionResult analyze(PromptUnderTest prompt) {
        List<QualityIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double totalPoints = 0;
        double maxPoints = 4;

        // Check 1: Template variables match declared inputs
        Set<String> templateVars = new HashSet<>();
        Matcher varMatcher = TEMPLATE_VAR_PATTERN.matcher(prompt.userPrompt());
        while (varMatcher.find()) {
            templateVars.add(varMatcher.group(1));
        }

        Set<String> declaredInputs = prompt.declaredInputs();

        Set<String> undeclared = new HashSet<>(templateVars);
        undeclared.removeAll(declaredInputs);
        if (!undeclared.isEmpty()) {
            issues.add(QualityIssue.critical("CONSISTENCY",
                    "Template variables not in AgentSpec inputs: " + undeclared,
                    "CNS-001"));
        }

        Set<String> unused = new HashSet<>(declaredInputs);
        unused.removeAll(templateVars);
        if (!unused.isEmpty()) {
            issues.add(QualityIssue.warning("CONSISTENCY",
                    "AgentSpec declares inputs not used in template: " + unused,
                    "CNS-002"));
        }

        if (undeclared.isEmpty() && unused.isEmpty()) {
            totalPoints += 1;
        } else if (undeclared.isEmpty()) {
            totalPoints += 0.5;
        }

        // Check 2: System prompt has no template variables
        Matcher sysVarMatcher = TEMPLATE_VAR_PATTERN.matcher(prompt.systemPrompt());
        if (sysVarMatcher.find()) {
            issues.add(QualityIssue.warning("CONSISTENCY",
                    "System prompt contains {{" + sysVarMatcher.group(1) + "}}. "
                            + "Template variables should only be in user prompt.",
                    "CNS-003"));
        } else {
            totalPoints += 1;
        }

        // Check 3: Rule numbering has no gaps
        Map<String, List<Integer>> ruleGroups = new HashMap<>();
        Matcher ruleMatcher = RULE_NUMBER_PATTERN.matcher(prompt.systemPrompt());
        while (ruleMatcher.find()) {
            String prefix = ruleMatcher.group(1);
            int number = Integer.parseInt(ruleMatcher.group(2));
            ruleGroups.computeIfAbsent(prefix, k -> new ArrayList<>()).add(number);
        }

        boolean hasGaps = false;
        for (Map.Entry<String, List<Integer>> entry : ruleGroups.entrySet()) {
            List<Integer> numbers = entry.getValue().stream().sorted().distinct().toList();
            if (numbers.size() >= 2) {
                for (int i = 1; i < numbers.size(); i++) {
                    if (numbers.get(i) - numbers.get(i - 1) > 1) {
                        hasGaps = true;
                        issues.add(QualityIssue.info("CONSISTENCY",
                                "Rule numbering gap in " + entry.getKey() + " series: "
                                        + numbers.get(i - 1) + " → " + numbers.get(i),
                                "CNS-004"));
                    }
                }
            }
        }
        totalPoints += hasGaps ? 0.5 : 1;

        // Check 4: No contradictory instructions
        String lower = prompt.systemPrompt().toLowerCase();
        boolean hasContradiction = false;

        if (lower.contains("over-extract") && lower.contains("only extract verified")) {
            hasContradiction = true;
            issues.add(QualityIssue.critical("CONSISTENCY",
                    "Contradictory: 'over-extract' vs 'only extract verified'.",
                    "CNS-005"));
        }
        if (lower.contains("do not normalize") && lower.contains("normalize each")) {
            hasContradiction = true;
            issues.add(QualityIssue.critical("CONSISTENCY",
                    "Contradictory: 'do not normalize' vs 'normalize each'.",
                    "CNS-006"));
        }

        totalPoints += hasContradiction ? 0 : 1;

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("CONSISTENCY", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }
}
