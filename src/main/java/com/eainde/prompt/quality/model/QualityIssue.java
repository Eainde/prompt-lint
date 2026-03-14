package com.eainde.prompt.quality.model;

/**
 * A quality issue found during prompt analysis.
 *
 * @param dimension  which dimension flagged this
 * @param severity   CRITICAL, WARNING, or INFO
 * @param message    human-readable description
 * @param ruleId     which analysis rule triggered this (for suppression)
 */
public record QualityIssue(
        String dimension,
        Severity severity,
        String message,
        String ruleId) {

    public static QualityIssue critical(String dimension, String message, String ruleId) {
        return new QualityIssue(dimension, Severity.CRITICAL, message, ruleId);
    }

    public static QualityIssue warning(String dimension, String message, String ruleId) {
        return new QualityIssue(dimension, Severity.WARNING, message, ruleId);
    }

    public static QualityIssue info(String dimension, String message, String ruleId) {
        return new QualityIssue(dimension, Severity.INFO, message, ruleId);
    }
}
