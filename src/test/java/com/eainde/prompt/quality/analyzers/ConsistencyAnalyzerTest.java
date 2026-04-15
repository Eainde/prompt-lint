package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConsistencyAnalyzerTest {

    private final ConsistencyAnalyzer analyzer = new ConsistencyAnalyzer();

    private PromptUnderTest prompt(String system, String user, Set<String> inputs) {
        return new PromptUnderTest("test-agent", system, user,
                inputs, "output", AgentTypeProfile.DEFAULT);
    }

    @Test
    @DisplayName("dimensionName returns CONSISTENCY")
    void dimensionName() {
        assertEquals("CONSISTENCY", analyzer.dimensionName());
    }

    @Test
    @DisplayName("perfectly consistent prompt scores 1.0")
    void perfectlyConsistent() {
        String system = "R1 extract. R2 classify. R3 return.";
        String user = "Process {{input}} now.";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertEquals(1.0, result.score(), 0.01);
    }

    @Test
    @DisplayName("check1: undeclared template vars trigger CNS-001 CRITICAL")
    void undeclaredTemplateVars() {
        String user = "{{input}} {{extra}}";
        DimensionResult result = analyzer.analyze(prompt("Rules.", user, Set.of("input")));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "CNS-001".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
    }

    @Test
    @DisplayName("check1: unused declared inputs trigger CNS-002 WARNING")
    void unusedDeclaredInputs() {
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt("Rules.", user, Set.of("input", "extra")));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "CNS-002".equals(i.ruleId()) && i.severity() == Severity.WARNING));
    }

    @Test
    @DisplayName("check1: both undeclared and unused gives 0 points")
    void bothUndeclaredAndUnused() {
        String user = "{{a}} {{b}}";
        DimensionResult result = analyzer.analyze(prompt("Rules.", user, Set.of("c", "d")));
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-001".equals(i.ruleId())));
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check1: only unused (no undeclared) gives 0.5 points")
    void onlyUnusedGivesHalfPoint() {
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt("Rules.", user, Set.of("input", "unused")));
        // undeclared empty, unused not empty -> 0.5 points
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-002".equals(i.ruleId())));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check1: all vars match gives full point")
    void allVarsMatch() {
        String user = "{{a}} {{b}}";
        DimensionResult result = analyzer.analyze(prompt("Rules.", user, Set.of("a", "b")));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-001".equals(i.ruleId())));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: system prompt with template vars triggers CNS-003")
    void systemPromptWithTemplateVars() {
        String system = "Process {{ticketId}} data.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: system prompt without template vars passes")
    void systemPromptWithoutTemplateVars() {
        String system = "Process data.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: sequential rule numbers give full point")
    void sequentialRuleNumbers() {
        String system = "R1 extract. R2 classify. R3 return.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: gap in rule numbers triggers CNS-004")
    void gapInRuleNumbers() {
        String system = "R1 extract. R3 classify. R5 return.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: single rule in group - no gap check")
    void singleRuleInGroup() {
        String system = "R1 extract. A1 classify.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: multiple groups checked independently")
    void multipleRuleGroups() {
        String system = "R1 extract. R2 classify. A1 first. A3 third.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        // A group has gap (1->3), R group is fine
        assertTrue(result.issues().stream().anyMatch(
                i -> "CNS-004".equals(i.ruleId()) && i.message().contains("A")));
    }

    @Test
    @DisplayName("check4: no contradictions gives full point")
    void noContradictions() {
        String system = "Extract only verified names.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-005".equals(i.ruleId())));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: over-extract + only extract verified triggers CNS-005")
    void overExtractContradiction() {
        String system = "Prefer to over-extract rather than miss. But only extract verified data.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "CNS-005".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
    }

    @Test
    @DisplayName("check4: do not normalize + normalize each triggers CNS-006")
    void normalizeContradiction() {
        String system = "Do not normalize the data. But normalize each field.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "CNS-006".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
    }

    @Test
    @DisplayName("check4: both contradictions present")
    void bothContradictions() {
        String system = "Over-extract data. Only extract verified. Do not normalize entries. Normalize each field.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-005".equals(i.ruleId())));
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("maxScore is 1.0")
    void maxScoreIs1() {
        DimensionResult result = analyzer.analyze(prompt("test", "{{i}}", Set.of("i")));
        assertEquals(1.0, result.maxScore());
    }

    @Test
    @DisplayName("check3: duplicate rule numbers are deduplicated")
    void duplicateRuleNumbers() {
        String system = "R1 extract. R1 also extract. R2 classify.";
        String user = "{{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        // R1 appears twice but distinct gives [1,2], no gap
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("CNS-007: tone shift detected")
    void toneShiftDetected() {
        String system = "You shall extract all entities. Hereby validate each. "
                + "Just go ahead and grab whatever looks right. Okay cool.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("CNS-007: no tone shift with consistent voice")
    void noToneShift() {
        String system = "You must extract all entities. You must validate each field.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("CNS-008: forward reference in steps")
    void forwardReferenceInSteps() {
        String system = "Step 1: Apply the format from step 3.\nStep 2: Validate.\nStep 3: Format as JSON.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(i -> "CNS-008".equals(i.ruleId())));
    }

    @Test
    @DisplayName("CNS-008: no issue with backward reference")
    void backwardReferenceOk() {
        String system = "Step 1: Extract data.\nStep 2: Validate step 1 output.\nStep 3: Format.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "CNS-008".equals(i.ruleId())));
    }
}
