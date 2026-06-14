package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.api.PromptQualityResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: analyzeAndReport() returns a flat, JSON-serializable PromptQualityResult
 * for REST endpoints / non-technical consumers. Two overloads shown.
 */
class ApiResultExample {

    private final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

    @Test
    void structuredResultFromAPromptObject() {
        PromptQualityResult result =
                analyzer.analyzeAndReport(Examples.wellFormedExtractionPrompt(), 0.75);

        assertThat(result.agentName()).isEqualTo("well-formed-extractor");
        assertThat(result.threshold()).isEqualTo(0.75);
        assertThat(result.dimensions()).hasSize(8);
        assertThat(result.issueSummary().total()).isEqualTo(result.issues().size());
        System.out.println("passed=" + result.passed() + " score=" + result.overallScore());
    }

    @Test
    void rawInputsOverloadResolvesProfileByName() {
        PromptQualityResult result = analyzer.analyzeAndReport(
                "rest-caller",
                "You classify the message sentiment as positive, negative, or neutral.",
                "Message: {{message}}",
                Set.of("message"),
                "label",
                "CLASSIFICATION",
                Map.of(),
                null,
                0.75);

        assertThat(result.profile()).isEqualTo("CLASSIFICATION");
        assertThat(result.overallScore()).isBetween(0.0, 1.0);
    }
}
