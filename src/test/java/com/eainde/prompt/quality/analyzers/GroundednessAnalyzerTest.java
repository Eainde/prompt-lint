package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroundednessAnalyzerTest {

    private final GroundednessAnalyzer analyzer = new GroundednessAnalyzer();

    private PromptUnderTest prompt(String system, String user, Set<String> inputs) {
        return new PromptUnderTest("test-agent", system, user,
                inputs, "output", AgentTypeProfile.DEFAULT);
    }

    @Test
    @DisplayName("dimensionName returns GROUNDEDNESS")
    void dimensionName() {
        assertEquals("GROUNDEDNESS", analyzer.dimensionName());
    }

    @Test
    @DisplayName("fully grounded prompt scores high")
    void fullyGroundedPrompt() {
        String system = """
                Use only information from the provided document.
                Do not use prior knowledge. Never fabricate data.
                Cite the source document and page number for each claim.
                """;
        String user = """
                --- DOCUMENT ---
                {{documentText}}
                --- END ---
                Return the results.
                """;
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("documentText")));
        assertTrue(result.score() >= 0.8);
    }

    @Test
    @DisplayName("check1: no grounding instruction triggers GRD-001 CRITICAL")
    void missingGroundingInstruction() {
        String system = "Just process the data.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "GRD-001".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
    }

    @Test
    @DisplayName("check1: grounding in user prompt also counts")
    void groundingInUserPrompt() {
        String system = "Process data.";
        String user = "Use only from the provided context. {{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check2: no external knowledge prohibition triggers GRD-002 CRITICAL")
    void missingExternalKnowledgeProhibition() {
        String system = "Use only information from the provided documents.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "GRD-002".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
    }

    @Test
    @DisplayName("check2: external prohibition present passes")
    void externalProhibitionPresent() {
        String system = "Do not use prior knowledge. Use only from the provided data.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-002".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: 2+ citation requirements gives full point")
    void twoCitationRequirements() {
        String system = "Include document name and page number for each citation.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(
                i -> "GRD-003".equals(i.ruleId()) && i.message().contains("Partial")));
    }

    @Test
    @DisplayName("check3: 1 citation requirement gives 0.5 + info")
    void oneCitationRequirement() {
        // "cite" matches but only 1 keyword from the list
        String system = "You must cite each claim properly.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(
                i -> "GRD-003".equals(i.ruleId()) && i.message().contains("Partial")));
    }

    @Test
    @DisplayName("check3: 0 citation requirements gives warning")
    void zeroCitationRequirements() {
        String system = "Just extract data.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(
                i -> "GRD-003".equals(i.ruleId()) && i.message().contains("No citation")));
    }

    @Test
    @DisplayName("check4: fabrication prohibition present passes")
    void fabricationProhibitionPresent() {
        String system = "Never fabricate data. Use only from the provided documents.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: no fabrication prohibition triggers GRD-004")
    void missingFabricationProhibition() {
        String system = "Extract data from documents.";
        DimensionResult result = analyzer.analyze(prompt(system, "{{input}}", Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(i -> "GRD-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: document boundary markers in user prompt pass")
    void documentBoundaryMarkers() {
        String system = "Extract data.";
        String user = "--- DOCUMENT ---\n{{input}}\n--- END ---";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: no boundary markers triggers GRD-005")
    void missingBoundaryMarkers() {
        String system = "Extract data.";
        String user = "Here is the text: {{input}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("input")));
        assertTrue(result.issues().stream().anyMatch(i -> "GRD-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: sourceText declared and in template gives full point")
    void sourceTextDeclaredAndUsed() {
        String system = "Extract data.";
        String user = "{{sourceText}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("sourceText")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: documentText declared and in template gives full point")
    void documentTextDeclaredAndUsed() {
        String system = "Extract data.";
        String user = "--- DOCUMENT ---\n{{documentText}}\n--- END ---";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("documentText")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: sourceText declared but not in template triggers GRD-006 CRITICAL")
    void sourceTextDeclaredButNotInTemplate() {
        String system = "Extract data.";
        String user = "Just process it. {{other}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("sourceText", "other")));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "GRD-006".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
    }

    @Test
    @DisplayName("check6: no source text input gives full point (not applicable)")
    void noSourceTextInput() {
        String system = "Score the candidates.";
        String user = "{{classifiedCandidates}}";
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("classifiedCandidates")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: {{document}} variable also recognized")
    void documentVariableRecognized() {
        String system = "Extract data.";
        String user = "{{document}}";
        // "document" is not "sourceText" or "documentText" in declared inputs
        // so readsSourceText=false → full point
        DimensionResult result = analyzer.analyze(prompt(system, user, Set.of("document")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("maxScore is 1.0")
    void maxScoreIs1() {
        DimensionResult result = analyzer.analyze(prompt("test", "{{input}}", Set.of("input")));
        assertEquals(1.0, result.maxScore());
    }

    @Test
    @DisplayName("GRD-007: conflicting grounding scope detected")
    void conflictingGroundingScope() {
        String system = "You are a specialist. Extract information only from the provided documents. "
                + "Use your knowledge to fill in any gaps. Do not fabricate data.\n"
                + "## Output\n```json\n{\"data\": []}\n```";
        DimensionResult result = analyzer.analyze(prompt(system,
                "--- document ---\n{{sourceText}}\n--- end ---", Set.of("sourceText")));
        assertTrue(result.issues().stream().anyMatch(i -> "GRD-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("GRD-007: no conflict when only grounding present")
    void noConflictWithOnlyGrounding() {
        String system = "You are a specialist. Extract information only from the provided documents. "
                + "Do not use external knowledge. Never fabricate data.\n"
                + "## Output\n```json\n{\"data\": []}\n```";
        DimensionResult result = analyzer.analyze(prompt(system,
                "--- document ---\n{{sourceText}}\n--- end ---", Set.of("sourceText")));
        assertFalse(result.issues().stream().anyMatch(i -> "GRD-007".equals(i.ruleId())));
    }
}
