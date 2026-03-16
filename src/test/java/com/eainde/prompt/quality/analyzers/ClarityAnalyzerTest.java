package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClarityAnalyzerTest {

    private final ClarityAnalyzer analyzer = new ClarityAnalyzer();

    private PromptUnderTest prompt(String system, String user) {
        return new PromptUnderTest("test-agent", system, user,
                Set.of("input"), "output", AgentTypeProfile.DEFAULT);
    }

    @Test
    @DisplayName("dimensionName returns CLARITY")
    void dimensionName() {
        assertEquals("CLARITY", analyzer.dimensionName());
    }

    @Test
    @DisplayName("perfect prompt scores 1.0 with no issues")
    void perfectPrompt() {
        String system = """
                You are a data extraction agent.
                Your task is to extract, classify, and return structured data.
                Validate and normalize each field.

                ## Output Format
                { "result": [] }

                ## Rules
                R1 — Extract names only.
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertEquals(1.0, result.score(), 0.01);
        assertTrue(result.issues().isEmpty());
    }

    @Test
    @DisplayName("check1: missing role definition triggers CLR-001")
    void missingRoleDefinition() {
        String system = """
                Your task is to extract, classify, and return data.
                Validate and normalize fields.
                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertTrue(result.issues().stream().anyMatch(i -> "CLR-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check1: 'act as a' is recognized as role starter")
    void actAsRoleStarter() {
        String system = """
                Act as a data extraction agent.
                Your task is to extract, classify, and return data.
                Validate and normalize fields.
                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertFalse(result.issues().stream().anyMatch(i -> "CLR-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: missing task statement triggers CLR-002")
    void missingTaskStatement() {
        String system = """
                You are a helper agent.
                Extract and classify data.
                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertTrue(result.issues().stream().anyMatch(i -> "CLR-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: fewer than 3 imperative verbs triggers CLR-003")
    void fewImperativeVerbs() {
        String system = """
                You are a helper agent.
                Your task is to do stuff.
                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertTrue(result.issues().stream().anyMatch(i -> "CLR-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: 3+ imperative verbs pass")
    void enoughImperativeVerbs() {
        String system = """
                You are an agent.
                Your task is to extract names, classify them, and return results.
                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertFalse(result.issues().stream().anyMatch(i -> "CLR-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: vague language triggers CLR-004")
    void vagueLanguage() {
        String system = """
                You are an agent.
                Your task is to try to extract data if possible.
                Maybe include some details. Feel free to add more.
                Extract, classify, and return results.
                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertTrue(result.issues().stream().anyMatch(i -> "CLR-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: missing output section triggers CLR-005 as CRITICAL")
    void missingOutputSection() {
        String system = """
                You are an agent.
                Your task is to extract, classify, and return data.
                Validate and normalize fields.
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "CLR-005".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
    }

    @Test
    @DisplayName("check6: task after rules triggers CLR-006")
    void taskAfterRules() {
        String system = """
                You are an extraction agent.

                ## Rules
                R1 — Extract names only.
                R2 — Return JSON.

                Your task is to extract, classify, and return data.
                Validate and normalize fields.

                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertTrue(result.issues().stream().anyMatch(i -> "CLR-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: task before rules gives full point")
    void taskBeforeRules() {
        String system = """
                You are an extraction agent.
                Your task is to extract, classify, and return data.
                Validate and normalize fields.

                ## Rules
                R1 — Extract names only.

                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertFalse(result.issues().stream().anyMatch(i -> "CLR-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("ambiguous quantifiers trigger CLR-007")
    void ambiguousQuantifiers() {
        String system = """
                You are an agent.
                Your task is to extract some of the various names.
                Extract, classify, and return data.
                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertTrue(result.issues().stream().anyMatch(i -> "CLR-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("minimal prompt scores low")
    void minimalPromptScoresLow() {
        DimensionResult result = analyzer.analyze(prompt("Do something.", "{{input}}"));
        assertTrue(result.score() < 0.5);
    }

    @Test
    @DisplayName("check6: no task found + no rules = no CLR-006 (covered by CLR-002)")
    void noTaskNoRulesNoCLR006() {
        String system = """
                You are an agent.
                ## Output Format
                { "result": [] }
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertFalse(result.issues().stream().anyMatch(i -> "CLR-006".equals(i.ruleId())));
        assertTrue(result.issues().stream().anyMatch(i -> "CLR-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("score capped at 1.0")
    void scoreCappedAt1() {
        String system = """
                You are a data extraction agent.
                Your task is to extract, classify, and return structured data.
                Validate, normalize, and verify each field.
                ## Output Format
                { "names": [] }
                ## Rules
                R1 — Extract only.
                """;
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}"));
        assertTrue(result.score() <= 1.0);
    }

    @Test
    @DisplayName("maxScore is always 1.0")
    void maxScoreIs1() {
        DimensionResult result = analyzer.analyze(prompt("test", "{{input}}"));
        assertEquals(1.0, result.maxScore());
    }
}
