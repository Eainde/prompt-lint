package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpecificityAnalyzerTest {

    private final SpecificityAnalyzer analyzer = new SpecificityAnalyzer();

    private PromptUnderTest prompt(String system) {
        return new PromptUnderTest("test-agent", system, "{{input}}",
                Set.of("input"), "output", AgentTypeProfile.DEFAULT);
    }

    @Test
    @DisplayName("dimensionName returns SPECIFICITY")
    void dimensionName() {
        assertEquals("SPECIFICITY", analyzer.dimensionName());
    }

    @Test
    @DisplayName("check1: 5+ named rules gives full point")
    void fiveOrMoreRules() {
        String system = "R1 rule R2 rule R3 rule R4 rule R5 rule. Do not do X. Never do Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(
                i -> "SPC-001".equals(i.ruleId()) && i.message().contains("Very few")));
    }

    @Test
    @DisplayName("check1: 2-4 rules gives 0.5 points + info")
    void twoToFourRules() {
        String system = "R1 rule. R2 rule. Do not do X. Never do Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(
                i -> "SPC-001".equals(i.ruleId()) && i.message().contains("Only")));
    }

    @Test
    @DisplayName("check1: <2 rules gives warning")
    void fewerThanTwoRules() {
        String system = "Just some instructions. Do not do X. Never do Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(
                i -> "SPC-001".equals(i.ruleId()) && i.message().contains("Very few")));
    }

    @Test
    @DisplayName("check2: 3+ thresholds gives full point")
    void threeOrMoreThresholds() {
        String system = "R1 R2 R3 R4 R5 rule. max > 10 items, score >= 0.85, limit < 50. Do not X. Never Y. Example: valid output. e.g. correct.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "SPC-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: 1-2 thresholds gives 0.5 points")
    void oneToTwoThresholds() {
        String system = "R1 R2 R3 R4 R5. Score >= 0.85. Do not X. Never Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "SPC-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: 0 thresholds gives info")
    void zeroThresholds() {
        String system = "Just instructions with no numbers at all.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "SPC-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: 3+ conditionals gives full point")
    void threeOrMoreConditionals() {
        String system = "R1 R2 R3 R4 R5. If the document is empty then stop. When the user asks for help then respond. Unless the field is null then skip. Do not X. Never Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "SPC-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: 1-2 conditionals gives 0.5")
    void oneConditional() {
        String system = "R1 R2 R3 R4 R5. If the document is empty stop.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "SPC-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: 0 conditionals gives info")
    void zeroConditionals() {
        String system = "Just instructions.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "SPC-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: 2+ positive examples gives full point")
    void twoPositiveExamples() {
        String system = "R1 R2 R3 R4 R5. Example: valid output. e.g. correct data. Do not X. Never Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(
                i -> "SPC-004".equals(i.ruleId()) && i.message().contains("No positive")));
    }

    @Test
    @DisplayName("check4: 1 positive example gives 0.5 + info")
    void onePositiveExample() {
        String system = "R1 R2 R3 R4 R5. Example: output. Do not X. Never Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(
                i -> "SPC-004".equals(i.ruleId()) && i.message().contains("Only")));
    }

    @Test
    @DisplayName("check4: 0 positive examples gives warning")
    void zeroPositiveExamples() {
        String system = "R1 R2 R3 R4 R5. Do not X. Never Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(
                i -> "SPC-004".equals(i.ruleId()) && i.message().contains("No positive")));
    }

    @Test
    @DisplayName("check5: 2+ negative examples gives full point")
    void twoNegativeExamples() {
        String system = "Do not do X. Never do Y.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "SPC-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: 1 negative example gives 0.5")
    void oneNegativeExample() {
        String system = "R1 R2 R3 R4 R5. The output is invalid sometimes.";
        // "invalid" matches negative pattern
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "SPC-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: 0 negative examples gives warning")
    void zeroNegativeExamples() {
        String system = "Just simple instructions with no boundaries.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "SPC-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: open-ended phrases trigger SPC-006")
    void openEndedPhrases() {
        String system = "Be creative and use your judgment to do your best.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "SPC-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: no open-ended phrases gives full point")
    void noOpenEndedPhrases() {
        String system = "R1 R2 R3 R4 R5. Extract only verified names. Do not infer. Never guess.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "SPC-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("maxScore is 1.0")
    void maxScoreIs1() {
        DimensionResult result = analyzer.analyze(prompt("test"));
        assertEquals(1.0, result.maxScore());
    }

    @Test
    @DisplayName("score capped at 1.0")
    void scoreCappedAt1() {
        DimensionResult result = analyzer.analyze(prompt("R1 R2 R3 R4 R5 R6 R7. > 10 >= 0.85 < 50. If empty then stop. When null then skip. Unless absent then ignore. Example: valid. e.g. correct. Do not X. Never Y. Invalid Z."));
        assertTrue(result.score() <= 1.0);
    }
}
