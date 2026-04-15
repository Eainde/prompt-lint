package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TokenEfficiencyAnalyzerTest {

    private final TokenEfficiencyAnalyzer analyzer = new TokenEfficiencyAnalyzer();

    private PromptUnderTest prompt(String system, String user) {
        return new PromptUnderTest("test-agent", system, user,
                Set.of("input"), "output", AgentTypeProfile.DEFAULT);
    }

    private PromptUnderTest prompt(String system) {
        return prompt(system, "{{input}}");
    }

    @Test
    @DisplayName("dimensionName returns TOKEN_EFFICIENCY")
    void dimensionName() {
        assertEquals("TOKEN_EFFICIENCY", analyzer.dimensionName());
    }

    @Test
    @DisplayName("efficient prompt scores 1.0")
    void efficientPrompt() {
        // Need 400-8000 chars (100-2000 tokens), no filler, no duplication
        StringBuilder sb = new StringBuilder();
        sb.append("You are a data extraction agent. Extract names from the provided document. ");
        sb.append("Return structured JSON. Follow all rules precisely. ");
        sb.append("R1 - Extract only verified names from the source text provided. ");
        sb.append("R2 - Return a JSON array of records with proper types. ");
        sb.append("R3 - Include confidence scores for each extraction. ");
        sb.append("R4 - Handle edge cases like empty documents gracefully. ");
        sb.append("R5 - Validate all fields before returning results. ");
        sb.append("R6 - Check for duplicates and remove them from output. ");
        // Pad to > 400 chars
        while (sb.length() < 420) sb.append("Additional extraction context is provided here. ");
        DimensionResult result = analyzer.analyze(prompt(sb.toString()));
        assertEquals(1.0, result.score(), 0.01);
    }

    @Test
    @DisplayName("check1: system prompt over 2000 tokens triggers TOK-001")
    void systemPromptOverBudget() {
        // 2000 tokens * 4 chars = 8000+ chars needed
        String system = "X".repeat(8100);
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "TOK-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check1: system prompt under 100 tokens triggers TOK-002")
    void systemPromptTooShort() {
        // < 100 tokens = < 400 chars
        String system = "Do stuff.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "TOK-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check1: system prompt within budget passes")
    void systemPromptWithinBudget() {
        String system = "A".repeat(500); // 125 tokens
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-001".equals(i.ruleId())));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: user template overhead over 500 tokens triggers TOK-003")
    void userTemplateOverhead() {
        String user = "Process this data: " + "X".repeat(2100) + " {{input}}";
        DimensionResult result = analyzer.analyze(prompt("A".repeat(500), user));
        assertTrue(result.issues().stream().anyMatch(i -> "TOK-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: user template within budget passes")
    void userTemplateWithinBudget() {
        String user = "Process {{input}} now.";
        DimensionResult result = analyzer.analyze(prompt("A".repeat(500), user));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: template variables stripped before counting")
    void templateVarsStripped() {
        // Long variable name doesn't count toward overhead
        String user = "{{veryLongVariableNameThatShouldBeStripped}}";
        DimensionResult result = analyzer.analyze(prompt("A".repeat(500), user));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: filler phrases trigger TOK-004")
    void fillerPhrases() {
        String system = "A".repeat(500) + " Please note that this is important. It should be noted that we must proceed.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "TOK-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: no filler phrases passes")
    void noFillerPhrases() {
        String system = "A".repeat(500);
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: filler in user prompt also detected")
    void fillerInUserPrompt() {
        String system = "A".repeat(500);
        String user = "Please note that you should process {{input}} carefully.";
        DimensionResult result = analyzer.analyze(prompt(system, user));
        assertTrue(result.issues().stream().anyMatch(i -> "TOK-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: duplicate sentences trigger TOK-005")
    void duplicateSentences() {
        String system = "A".repeat(500) + ". Extract all names from text. Do some other work. Extract all names from text. End.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "TOK-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: no duplicate sentences passes")
    void noDuplicateSentences() {
        String system = "A".repeat(500) + ". First unique sentence here. Second unique sentence here.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: short sentences (<= 20 chars) ignored for duplication")
    void shortSentencesIgnored() {
        String system = "A".repeat(500) + ". Do it. Do it. Do it.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("maxScore is 1.0")
    void maxScoreIs1() {
        DimensionResult result = analyzer.analyze(prompt("test"));
        assertEquals(1.0, result.maxScore());
    }

    @Test
    @DisplayName("check1: exactly at boundary (100 tokens = 400 chars) passes")
    void exactlyAtMinBoundary() {
        String system = "A".repeat(400); // exactly 100 tokens
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-001".equals(i.ruleId())));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check1: exactly at max boundary (2000 tokens = 8000 chars) passes")
    void exactlyAtMaxBoundary() {
        String system = "A".repeat(8000); // exactly 2000 tokens
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("TOK-005: near-duplicate sentences detected via similarity")
    void nearDuplicateSentences() {
        String system = "A".repeat(500)
                + ". Extract all verified names from the source document provided. "
                + "Do some other unrelated work here. "
                + "Extract all verified names from the source documents provided. End.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "TOK-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("TOK-005: completely different sentences pass")
    void differentSentencesPass() {
        String system = "A".repeat(500)
                + ". Extract all names from the document. Return the results as JSON format.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("TOK-006: redundant constraints detected (similarity 0.6-0.75)")
    void redundantConstraintsDetected() {
        // Sentences with similarity ~0.61 (between 0.6 and 0.75)
        String system = "A".repeat(500)
                + ". Always validate each input field before processing begins. "
                + "Do something completely unrelated in between here. "
                + "You must validate all input fields before any processing.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "TOK-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("TOK-006: no redundant constraints when sentences are very different")
    void noRedundantConstraints() {
        String system = "A".repeat(500)
                + ". Extract names from the document text. Return results in JSON array format.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "TOK-006".equals(i.ruleId())));
    }
}
