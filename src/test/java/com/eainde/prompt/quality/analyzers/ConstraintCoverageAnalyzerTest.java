package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintCoverageAnalyzerTest {

    private final ConstraintCoverageAnalyzer analyzer = new ConstraintCoverageAnalyzer();

    private PromptUnderTest prompt(String system, String user) {
        return new PromptUnderTest("test-agent", system, user,
                Set.of("input"), "output", AgentTypeProfile.DEFAULT);
    }

    private PromptUnderTest prompt(String system) {
        return prompt(system, "{{input}}");
    }

    @Test
    @DisplayName("dimensionName returns CONSTRAINT_COVERAGE")
    void dimensionName() {
        assertEquals("CONSTRAINT_COVERAGE", analyzer.dimensionName());
    }

    @Test
    @DisplayName("fully covered prompt scores 1.0")
    void fullyCoveredPrompt() {
        String system = """
                If no data found, return empty. If unsure, skip.
                Do not include invalid entries. Never guess. Must not fabricate.
                Fields: null allowed, required name, optional middle.
                Valid values: "one of" active/inactive.
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertEquals(1.0, result.score(), 0.01);
    }

    @Test
    @DisplayName("check1: empty handling present passes")
    void emptyHandlingPresent() {
        String system = "If no data is found, return empty result.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check1: missing empty handling triggers CON-001")
    void missingEmptyHandling() {
        String system = "Extract all data.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "CON-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: uncertainty handling present passes")
    void uncertaintyHandlingPresent() {
        String system = "If unsure about a match, mark it as uncertain.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: missing uncertainty handling triggers CON-002")
    void missingUncertaintyHandling() {
        String system = "Extract all data.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "CON-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: 3+ negative instructions gives full point")
    void threeOrMoreNegativeInstructions() {
        String system = "Do not guess. Never fabricate. Must not infer. Avoid hallucination.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: 1-2 negative instructions gives 0.5")
    void oneToTwoNegativeInstructions() {
        String system = "Do not guess. Never fabricate.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: 0 negative instructions triggers CON-003")
    void zeroNegativeInstructions() {
        String system = "Extract all the data from the source.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "CON-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: 3+ field constraints gives full point")
    void threeOrMoreFieldConstraints() {
        String system = "Name is required. Middle is nullable. Status must be valid values.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: 1-2 field constraints gives 0.5")
    void oneToTwoFieldConstraints() {
        String system = "Name is required. Middle is optional.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: 0 field constraints triggers CON-004")
    void zeroFieldConstraints() {
        String system = "Extract all the data from the source text here.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "CON-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: 'one of' present passes")
    void oneOfPresent() {
        String system = "Status must be one of active, inactive, pending.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: 'valid values' present passes")
    void validValuesPresent() {
        String system = "Status valid values are: active, inactive.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: quoted enum pattern passes")
    void quotedEnumPattern() {
        String system = "The type can be \"report\" , \"article\" , \"email\" for each entry.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: no enum/valid values triggers CON-005")
    void noEnumValues() {
        String system = "Extract all the data.";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "CON-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("combined prompt used for checks 1-4")
    void combinedPromptUsed() {
        // empty handling in user prompt, not system
        String system = "Extract data.";
        String user = "If no results, return empty. {{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("maxScore is 1.0")
    void maxScoreIs1() {
        DimensionResult result = analyzer.analyze(prompt("test"));
        assertEquals(1.0, result.maxScore());
    }

    @Test
    @DisplayName("minimal prompt scores low")
    void minimalPromptScoresLow() {
        DimensionResult result = analyzer.analyze(prompt("Do stuff."));
        assertTrue(result.score() < 0.5);
    }

    @Test
    @DisplayName("CON-006: optional fields without defaults")
    void optionalFieldsWithoutDefaults() {
        DimensionResult result = analyzer.analyze(prompt(
                "Extract data. Some fields are optional and nullable."));
        assertTrue(result.issues().stream().anyMatch(i -> "CON-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("CON-006: no issue when defaults specified")
    void optionalFieldsWithDefaults() {
        DimensionResult result = analyzer.analyze(prompt(
                "Extract data. Some fields are optional. If not provided, defaults to empty string."));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("CON-007: no input size handling")
    void noInputSizeHandling() {
        DimensionResult result = analyzer.analyze(prompt(
                "Extract all names from the document."));
        assertTrue(result.issues().stream().anyMatch(i -> "CON-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("CON-007: no issue when truncation mentioned")
    void inputSizeHandlingPresent() {
        DimensionResult result = analyzer.analyze(prompt(
                "Extract names. If input exceeds 4000 tokens, truncate."));
        assertFalse(result.issues().stream().anyMatch(i -> "CON-007".equals(i.ruleId())));
    }
}
