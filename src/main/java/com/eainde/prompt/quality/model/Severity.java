package com.eainde.prompt.quality.model;

/**
 * Issue severity levels, ordered from most to least severe.
 *
 * <ul>
 *   <li>{@link #CRITICAL} — prompt is likely broken or will produce wrong output</li>
 *   <li>{@link #WARNING} — prompt has a gap that may cause issues in some cases</li>
 *   <li>{@link #INFO} — improvement opportunity, not a defect</li>
 * </ul>
 *
 * <p>Severity can be recalibrated per agent profile via {@link SeverityCalibrator}.
 * E.g., a grounding issue (GRD-001) is CRITICAL for extraction agents but
 * only INFO for formatting agents.</p>
 */
public enum Severity {
    /** Prompt is likely broken — will produce wrong or dangerous output. */
    CRITICAL,

    /** Gap that may cause issues in edge cases or specific inputs. */
    WARNING,

    /** Improvement opportunity — prompt works but could be better. */
    INFO
}
