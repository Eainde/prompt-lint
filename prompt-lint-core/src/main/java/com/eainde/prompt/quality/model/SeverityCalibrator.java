package com.eainde.prompt.quality.model;

import java.util.Map;

/**
 * Adjusts issue severity based on agent profile.
 * E.g., GRD-001 is CRITICAL for EXTRACTION but INFO for FORMATTING.
 */
public final class SeverityCalibrator {

    private SeverityCalibrator() {}

    /**
     * Map of ruleId → (agentType → overriddenSeverity).
     * E.g., "GRD-001" for "FORMATTING" agents is downgraded from CRITICAL to INFO
     * because formatting agents don't read source documents.
     */
    private static final Map<String, Map<String, Severity>> OVERRIDES = Map.of(
            "GRD-001", Map.of("FORMATTING", Severity.INFO, "CLASSIFICATION", Severity.INFO),
            "GRD-002", Map.of("FORMATTING", Severity.INFO, "CLASSIFICATION", Severity.INFO),
            "GRD-003", Map.of("FORMATTING", Severity.INFO),
            "CLR-001", Map.of("FORMATTING", Severity.INFO),
            "INJ-001", Map.of("FORMATTING", Severity.INFO),
            "INJ-002", Map.of("FORMATTING", Severity.INFO)
    );

    /**
     * Returns the calibrated severity for a given issue and agent profile.
     * If no override exists, the original severity is returned unchanged.
     *
     * @param issue   the quality issue to calibrate
     * @param profile the agent's type profile (determines which overrides apply)
     * @return calibrated severity (may differ from {@code issue.severity()})
     */
    public static Severity calibrate(QualityIssue issue, AgentTypeProfile profile) {
        var profileOverrides = OVERRIDES.get(issue.ruleId());
        if (profileOverrides == null) {
            return issue.severity();
        }
        return profileOverrides.getOrDefault(profile.agentType(), issue.severity());
    }
}
