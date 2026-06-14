package com.eainde.prompt.quality.fix;

/**
 * Type of fix operation to apply to a prompt.
 */
public enum FixType {
    /** Insert new text at the anchor point (or prepend if anchor is null). */
    INSERT,
    /** Replace the anchor text with the replacement text. */
    REPLACE,
    /** Delete the anchor text from the prompt. */
    DELETE
}
