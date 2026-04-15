package com.eainde.prompt.quality.fix;

/**
 * Which part of the prompt a fix targets.
 */
public enum FixLocation {
    /** Fix applies to the system instruction text. */
    SYSTEM_PROMPT,
    /** Fix applies to the user message template. */
    USER_PROMPT
}
