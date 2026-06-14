package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: the 30-second introduction.
 * Build an analyzer, wrap a prompt, analyze it, read the score and issues.
 */
class GettingStartedExample {

    @Test
    void analyzeAPromptInThreeLines() {
        // 1. Create an analyzer with all 8 built-in dimensions.
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

        // 2. Wrap the prompt under test. The profile tunes per-dimension weights.
        PromptUnderTest prompt = new PromptUnderTest(
                "greeting-agent",
                "You are a greeter. Greet the user by name, warmly and concisely.",
                "User name: {{name}}",
                Set.of("name"),
                "greeting",
                AgentTypeProfile.DEFAULT);

        // 3. Analyze. The report carries the weighted score plus every issue found.
        PromptQualityReport report = analyzer.analyze(prompt);
        Examples.print(report);

        assertThat(report.overallScore()).isBetween(0.0, 1.0);
        assertThat(report.dimensionResults()).hasSize(8);
        assertThat(report.agentName()).isEqualTo("greeting-agent");
    }
}
