package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: agent-type profiles change how dimensions are weighted, so the SAME
 * prompt earns different overall scores depending on the agent's job. Also shows
 * building a custom-weighted profile.
 */
class AgentTypeProfileExample {

    private final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

    @Test
    void builtInProfilesWeightDimensionsDifferently() {
        PromptUnderTest base = Examples.wellFormedExtractionPrompt();

        // Score the SAME prompt under every preset and collect the overall scores.
        Set<Double> distinctScores = new HashSet<>();
        for (AgentTypeProfile profile : List.of(
                AgentTypeProfile.EXTRACTION, AgentTypeProfile.CLASSIFICATION,
                AgentTypeProfile.FORMATTING, AgentTypeProfile.REVIEW, AgentTypeProfile.DEFAULT)) {

            PromptUnderTest p = new PromptUnderTest(
                    base.agentName(), base.systemPrompt(), base.userPrompt(),
                    base.declaredInputs(), base.declaredOutputKey(), profile);
            PromptQualityReport report = analyzer.analyze(p);

            assertThat(report.profile().agentType()).isEqualTo(profile.agentType());
            distinctScores.add(report.overallScore());
        }

        // The whole point: different weightings yield different overall scores.
        assertThat(distinctScores)
                .as("the same prompt should score differently across profiles")
                .hasSizeGreaterThan(1);
    }

    @Test
    void customWeightsEmphasiseAChosenDimension() {
        AgentTypeProfile custom = AgentTypeProfile.DEFAULT.withCustomWeight("GROUNDEDNESS", 0.5);

        assertThat(custom.weightFor("GROUNDEDNESS")).isEqualTo(0.5);

        PromptUnderTest p = new PromptUnderTest(
                "custom-weighted", Examples.wellFormedExtractionPrompt().systemPrompt(),
                "Source: {{sourceText}}", Set.of("sourceText"), "names", custom);
        PromptQualityReport report = analyzer.analyze(p);
        Examples.print(report);

        assertThat(report.overallScore()).isBetween(0.0, 1.0);
    }
}
