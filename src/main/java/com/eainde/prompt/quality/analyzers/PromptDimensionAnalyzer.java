package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;

/**
 * Common interface for all prompt quality dimension analyzers.
 * Each implementation analyzes one quality dimension of a prompt.
 */
public interface PromptDimensionAnalyzer {

    /** The dimension name (e.g., "CLARITY", "GROUNDEDNESS"). */
    String dimensionName();

    /** Analyze the prompt and return a scored result with issues. */
    DimensionResult analyze(PromptUnderTest prompt);
}
