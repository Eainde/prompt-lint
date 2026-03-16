package com.eainde.prompt.quality.api;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PromptQualityResultTest {

    private static final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

    private static PromptUnderTest goodPrompt() {
        return new PromptUnderTest(
                "test-extractor",
                """
                You are an extraction agent.

                ## Rules
                R1 — Extract names from the source text only. Do not fabricate names.
                R2 — Do not use prior knowledge. Only use provided input.
                R3 — If no names found, return empty list.

                ## Output Format
                {
                  "names": ["Alice", "Bob"],
                  "count": 2
                }

                ## Constraints
                - Max 100 names per request.
                - Ignore names shorter than 2 characters.
                """,
                """
                Extract names from:
                {{sourceText}}

                File: {{fileName}}
                Return JSON.
                """,
                Set.of("sourceText", "fileName"),
                "names",
                AgentTypeProfile.EXTRACTION
        );
    }

    private static PromptUnderTest weakPrompt() {
        return new PromptUnderTest(
                "weak-agent",
                "You are a helpful assistant. Try to help.",
                "{{userInput}}",
                Set.of("userInput"),
                "response",
                AgentTypeProfile.DEFAULT
        );
    }

    @Test
    @DisplayName("analyzeAndReport returns result with all fields populated")
    void allFieldsPopulated() {
        PromptQualityResult result = analyzer.analyzeAndReport(goodPrompt(), 0.50);

        assertNotNull(result.agentName());
        assertNotNull(result.profile());
        assertNotNull(result.analyzedAt());
        assertNotNull(result.dimensions());
        assertNotNull(result.issueSummary());
        assertNotNull(result.issues());
        assertNotNull(result.suggestions());
        assertNotNull(result.weakestDimension());
        assertTrue(result.overallScore() > 0);
        assertEquals(0.50, result.threshold());
    }

    @Test
    @DisplayName("passed is correct relative to threshold")
    void passedRelativeToThreshold() {
        PromptQualityResult high = analyzer.analyzeAndReport(goodPrompt(), 0.01);
        assertTrue(high.passed());

        PromptQualityResult low = analyzer.analyzeAndReport(goodPrompt(), 0.99);
        assertFalse(low.passed());
    }

    @Test
    @DisplayName("dimensions list has 8 entries")
    void dimensionsHas8Entries() {
        PromptQualityResult result = analyzer.analyzeAndReport(goodPrompt(), 0.50);
        assertEquals(8, result.dimensions().size());
    }

    @Test
    @DisplayName("issueSummary counts match issues list size")
    void issueSummaryMatchesIssuesList() {
        PromptQualityResult result = analyzer.analyzeAndReport(goodPrompt(), 0.50);

        int countFromList = result.issues().size();
        assertEquals(countFromList, result.issueSummary().total());

        long critical = result.issues().stream().filter(i -> "CRITICAL".equals(i.severity())).count();
        long warning = result.issues().stream().filter(i -> "WARNING".equals(i.severity())).count();
        long info = result.issues().stream().filter(i -> "INFO".equals(i.severity())).count();

        assertEquals((int) critical, result.issueSummary().critical());
        assertEquals((int) warning, result.issueSummary().warning());
        assertEquals((int) info, result.issueSummary().info());
    }

    @Test
    @DisplayName("customWeights takes precedence over profileName")
    void customWeightsPrecedence() {
        Map<String, Double> custom = Map.of(
                "CLARITY", 0.50, "SPECIFICITY", 0.50,
                "GROUNDEDNESS", 0.0, "OUTPUT_CONTRACT", 0.0,
                "CONSTRAINT_COVERAGE", 0.0, "CONSISTENCY", 0.0,
                "TOKEN_EFFICIENCY", 0.0, "INJECTION_RESISTANCE", 0.0
        );

        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are a test agent.", "{{input}}",
                Set.of("input"), "output",
                "EXTRACTION", custom, 0.50);

        assertEquals("CUSTOM", result.profile());
    }

    @Test
    @DisplayName("null profileName + null customWeights -> DEFAULT profile")
    void defaultProfileResolution() {
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are a test agent.", "{{input}}",
                Set.of("input"), "output",
                null, null, 0.50);

        assertEquals("DEFAULT", result.profile());
    }

    @Test
    @DisplayName("weak prompt returns passed=false and hasCriticalIssues=true")
    void weakPromptFailsAndHasCritical() {
        PromptQualityResult result = analyzer.analyzeAndReport(weakPrompt(), 0.50);

        assertFalse(result.passed());
        assertTrue(result.hasCriticalIssues());
    }
}
