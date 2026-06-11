package com.eainde.prompt.quality.model;

import java.util.Map;

/**
 * Weight profile for a specific agent type.
 * Different agent types prioritize different quality dimensions.
 */
public record AgentTypeProfile(
        String agentType,
        Map<String, Double> weights) {

    /** Extraction agents: groundedness is critical, output contract matters. */
    public static final AgentTypeProfile EXTRACTION = new AgentTypeProfile(
            "EXTRACTION",
            Map.of(
                    "CLARITY", 0.10,
                    "SPECIFICITY", 0.15,
                    "GROUNDEDNESS", 0.25,
                    "OUTPUT_CONTRACT", 0.15,
                    "CONSTRAINT_COVERAGE", 0.15,
                    "CONSISTENCY", 0.10,
                    "TOKEN_EFFICIENCY", 0.05,
                    "INJECTION_RESISTANCE", 0.05
            ));

    /** Classification agents: specificity and constraint coverage are key. */
    public static final AgentTypeProfile CLASSIFICATION = new AgentTypeProfile(
            "CLASSIFICATION",
            Map.of(
                    "CLARITY", 0.10,
                    "SPECIFICITY", 0.20,
                    "GROUNDEDNESS", 0.15,
                    "OUTPUT_CONTRACT", 0.15,
                    "CONSTRAINT_COVERAGE", 0.20,
                    "CONSISTENCY", 0.10,
                    "TOKEN_EFFICIENCY", 0.05,
                    "INJECTION_RESISTANCE", 0.05
            ));

    /** Formatting agents: output contract is the primary concern. */
    public static final AgentTypeProfile FORMATTING = new AgentTypeProfile(
            "FORMATTING",
            Map.of(
                    "CLARITY", 0.10,
                    "SPECIFICITY", 0.10,
                    "GROUNDEDNESS", 0.05,
                    "OUTPUT_CONTRACT", 0.30,
                    "CONSTRAINT_COVERAGE", 0.15,
                    "CONSISTENCY", 0.15,
                    "TOKEN_EFFICIENCY", 0.10,
                    "INJECTION_RESISTANCE", 0.05
            ));

    /** Review/Critic agents: specificity and groundedness matter equally. */
    public static final AgentTypeProfile REVIEW = new AgentTypeProfile(
            "REVIEW",
            Map.of(
                    "CLARITY", 0.10,
                    "SPECIFICITY", 0.15,
                    "GROUNDEDNESS", 0.20,
                    "OUTPUT_CONTRACT", 0.15,
                    "CONSTRAINT_COVERAGE", 0.15,
                    "CONSISTENCY", 0.10,
                    "TOKEN_EFFICIENCY", 0.05,
                    "INJECTION_RESISTANCE", 0.10
            ));

    /** Default: equal weights across all dimensions. */
    public static final AgentTypeProfile DEFAULT = new AgentTypeProfile(
            "DEFAULT",
            Map.of(
                    "CLARITY", 0.125,
                    "SPECIFICITY", 0.125,
                    "GROUNDEDNESS", 0.125,
                    "OUTPUT_CONTRACT", 0.125,
                    "CONSTRAINT_COVERAGE", 0.125,
                    "CONSISTENCY", 0.125,
                    "TOKEN_EFFICIENCY", 0.125,
                    "INJECTION_RESISTANCE", 0.125
            ));

    /**
     * Resolves a profile by name (case-insensitive). Returns {@link #DEFAULT} for unknown names.
     */
    public static AgentTypeProfile fromName(String name) {
        return switch (name.toUpperCase()) {
            case "EXTRACTION" -> EXTRACTION;
            case "CLASSIFICATION" -> CLASSIFICATION;
            case "FORMATTING" -> FORMATTING;
            case "REVIEW" -> REVIEW;
            default -> DEFAULT;
        };
    }

    /**
     * Returns the weight for a dimension. Defaults to 0.125 (equal weight) if not configured.
     */
    public double weightFor(String dimension) {
        return weights.getOrDefault(dimension, 0.125);
    }

    /**
     * Returns a new profile with one dimension weight overridden. Useful for fine-tuning.
     */
    public AgentTypeProfile withCustomWeight(String dimension, double weight) {
        var newWeights = new java.util.HashMap<>(this.weights);
        newWeights.put(dimension, weight);
        return new AgentTypeProfile(this.agentType, Map.copyOf(newWeights));
    }
}
