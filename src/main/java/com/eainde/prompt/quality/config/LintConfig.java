package com.eainde.prompt.quality.config;

import com.eainde.prompt.quality.model.Severity;

import java.util.Map;
import java.util.Set;

/**
 * Resolved rule configuration applied by {@code PromptQualityAnalyzer}.
 *
 * <ul>
 *   <li>{@code severityOverrides} — remaps a rule's severity; the explicit value
 *       wins over profile calibration.</li>
 *   <li>{@code suppressedRules} — hides matching issues from reports entirely;
 *       scores are not affected.</li>
 *   <li>{@code baseline} — filters issues whose fingerprints ({@code agentName:ruleId})
 *       were already recorded; only new issues surface. Scores are not affected.</li>
 * </ul>
 */
public record LintConfig(Lexicon lexicon,
                         Map<String, Severity> severityOverrides,
                         Set<String> suppressedRules,
                         Baseline baseline) {

    public static LintConfig none() {
        return new LintConfig(Lexicon.defaults(), Map.of(), Set.of(), null);
    }

    public boolean isFiltered(String agentName, String ruleId) {
        return suppressedRules.contains(ruleId)
                || (baseline != null && baseline.contains(agentName, ruleId));
    }
}
