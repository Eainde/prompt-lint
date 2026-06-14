package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: the OUTPUT_CONTRACT dimension inspects the declared response schema and
 * any JSON example embedded in the prompt. A malformed JSON example is flagged
 * with an OUT- issue; a valid schema + example scores reasonably.
 */
class OutputContractSchemaExample {

    private final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

    @Test
    void malformedJsonExampleIsFlagged() {
        String system = """
                Extract the person's name and age.
                Output format: return JSON like {"name": "Jane", "age": 30
                """;
        PromptUnderTest prompt = new PromptUnderTest(
                "broken-contract", system, "{{text}}",
                Set.of("text"), "person", AgentTypeProfile.FORMATTING);

        PromptQualityReport report = analyzer.analyze(prompt);
        Examples.print(report);

        assertThat(report.allIssues())
                .anySatisfy(i -> assertThat(i.ruleId()).startsWith("OUT-"));
    }

    @Test
    void declaredResponseSchemaIsUsed() {
        String schema = """
                {"type":"object","properties":{"name":{"type":"string"},
                 "age":{"type":"integer"}},"required":["name","age"]}
                """;
        String system = """
                Extract the person's name and age from the text.
                Return a JSON object, for example: {"name": "Jane Doe", "age": 30}.
                If a field is missing, use null.
                """;
        PromptUnderTest prompt = new PromptUnderTest(
                "schema-aware", system, "Text: {{text}}",
                Set.of("text"), "person", AgentTypeProfile.FORMATTING, schema);

        PromptQualityReport report = analyzer.analyze(prompt);
        Examples.print(report);

        assertThat(report.resultFor("OUTPUT_CONTRACT")).isNotNull();
        assertThat(report.resultFor("OUTPUT_CONTRACT").score()).isBetween(0.0, 1.0);
    }
}
