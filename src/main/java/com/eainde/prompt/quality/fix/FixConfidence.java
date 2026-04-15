package com.eainde.prompt.quality.fix;

/**
 * Confidence level for auto-applying a fix.
 */
public enum FixConfidence {
    /** Safe to auto-apply — unlikely to break the prompt. */
    HIGH,
    /** Likely correct but should be reviewed before applying. */
    MEDIUM,
    /** Speculative — needs human review. */
    LOW
}
