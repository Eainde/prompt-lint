package com.eainde.prompt.quality.sources;

import com.eainde.prompt.quality.model.PromptUnderTest;

/** A prompt extracted from a seed file, tagged with the id it was found under. */
public record NamedPrompt(String id, PromptUnderTest prompt) {}
