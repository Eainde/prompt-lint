package com.eainde.prompt.quality.fix;

import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import java.util.List;

/**
 * Interface for dimension analyzers that can suggest concrete fixes.
 *
 * <p>Analyzers implementing this (e.g., ClarityAnalyzer, GroundednessAnalyzer)
 * generate {@link PromptFix} objects that can be auto-applied via
 * {@link PromptFixApplicator}.</p>
 */
public interface FixGenerator {

    /**
     * Generates fix suggestions based on the analysis result.
     *
     * @param prompt the original prompt under test
     * @param result the analysis result for this analyzer's dimension
     * @return list of actionable fixes (may be empty)
     */
    List<PromptFix> suggestFixes(PromptUnderTest prompt, DimensionResult result);
}
