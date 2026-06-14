package com.eainde.prompt.quality.analyzers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Required annotation for plugin dimension analyzers loaded via {@link java.util.ServiceLoader}.
 *
 * <p>Plugins missing this annotation are skipped with a warning at startup.
 * The {@link #defaultWeight()} is used when the agent profile has no explicit weight for this dimension.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DimensionMeta {
    /** Unique dimension name (e.g., "DOMAIN_COMPLIANCE"). */
    String name();
    /** Default weight for scoring when no profile override exists (0.0–1.0). */
    double defaultWeight();
    /** Short description shown in reports and validation warnings. */
    String description();
}
