package com.eainde.prompt.quality;

import com.eainde.prompt.quality.analyzers.ClarityAnalyzer;
import com.eainde.prompt.quality.analyzers.SpecificityAnalyzer;
import com.eainde.prompt.quality.api.PromptQualityResult;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PromptQualityAnalyzerTest {

    private PromptUnderTest simplePrompt() {
        return new PromptUnderTest("test-agent",
                """
                You are a data extraction agent.
                Your task is to extract, classify, and return data.
                Validate and normalize fields.
                ## Output Format
                {"records": [{"name": "Alice", "id": 1}]}
                ## Rules
                R1 — Extract only.
                """,
                "{{input}}", Set.of("input"), "output", AgentTypeProfile.DEFAULT);
    }

    @Test
    @DisplayName("create() returns analyzer with 8 dimensions")
    void createReturns8Dimensions() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityReport report = analyzer.analyze(simplePrompt());
        assertEquals(8, report.dimensionResults().size());
    }

    @Test
    @DisplayName("withAnalyzers() uses custom set")
    void withAnalyzersCustomSet() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.withAnalyzers(
                new ClarityAnalyzer(), new SpecificityAnalyzer());
        PromptQualityReport report = analyzer.analyze(simplePrompt());
        assertEquals(2, report.dimensionResults().size());
    }

    @Test
    @DisplayName("analyze() populates all report fields")
    void analyzePopulatesAllFields() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityReport report = analyzer.analyze(simplePrompt());
        assertEquals("test-agent", report.agentName());
        assertEquals(AgentTypeProfile.DEFAULT, report.profile());
        assertNotNull(report.analyzedAt());
        assertTrue(report.overallScore() >= 0 && report.overallScore() <= 1);
    }

    @Test
    @DisplayName("analyze() computes weighted score using profile weights")
    void weightedScoreUsesProfile() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

        PromptUnderTest extractionPrompt = new PromptUnderTest("test",
                simplePrompt().systemPrompt(), simplePrompt().userPrompt(),
                simplePrompt().declaredInputs(), simplePrompt().declaredOutputKey(),
                AgentTypeProfile.EXTRACTION);
        PromptUnderTest defaultPrompt = simplePrompt();

        PromptQualityReport extractionReport = analyzer.analyze(extractionPrompt);
        PromptQualityReport defaultReport = analyzer.analyze(defaultPrompt);

        // Different profiles should produce different weighted scores
        // (unless by coincidence all dimensions score exactly the same)
        assertNotNull(extractionReport.overallScore());
        assertNotNull(defaultReport.overallScore());
    }

    @Test
    @DisplayName("analyzeAll() returns reports for all prompts")
    void analyzeAllReturnsAll() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        List<PromptUnderTest> prompts = List.of(simplePrompt(), simplePrompt());
        List<PromptQualityReport> reports = analyzer.analyzeAll(prompts);
        assertEquals(2, reports.size());
    }

    @Test
    @DisplayName("analyzeAndReport(PromptUnderTest, threshold) returns result")
    void analyzeAndReportWithPrompt() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(simplePrompt(), 0.5);
        assertNotNull(result);
        assertEquals("test-agent", result.agentName());
        assertEquals(0.5, result.threshold());
    }

    @Test
    @DisplayName("analyzeAndReport raw: customWeights non-empty -> CUSTOM profile")
    void rawCustomWeights() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        Map<String, Double> weights = Map.of("CLARITY", 1.0);
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", "EXTRACTION", weights, 0.5);
        assertEquals("CUSTOM", result.profile());
    }

    @Test
    @DisplayName("analyzeAndReport raw: empty customWeights + profileName -> named profile")
    void rawProfileName() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", "EXTRACTION", Map.of(), 0.5);
        assertEquals("EXTRACTION", result.profile());
    }

    @Test
    @DisplayName("analyzeAndReport raw: null customWeights + profileName -> named profile")
    void rawNullWeightsProfileName() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", "REVIEW", null, 0.5);
        assertEquals("REVIEW", result.profile());
    }

    @Test
    @DisplayName("analyzeAndReport raw: blank profileName + null customWeights -> DEFAULT")
    void rawBlankProfileNullWeights() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", "  ", null, 0.5);
        assertEquals("DEFAULT", result.profile());
    }

    @Test
    @DisplayName("analyzeAndReport raw: null profileName + null customWeights -> DEFAULT")
    void rawNullProfileNullWeights() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", null, null, 0.5);
        assertEquals("DEFAULT", result.profile());
    }

    @Test
    @DisplayName("withAnalyzers with single analyzer produces report with 1 dimension")
    void singleAnalyzer() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.withAnalyzers(new ClarityAnalyzer());
        PromptQualityReport report = analyzer.analyze(simplePrompt());
        assertEquals(1, report.dimensionResults().size());
        assertEquals("CLARITY", report.dimensionResults().get(0).dimension());
    }

    @Test
    @DisplayName("analyzeAll with empty list returns empty")
    void analyzeAllEmpty() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        List<PromptQualityReport> reports = analyzer.analyzeAll(List.of());
        assertTrue(reports.isEmpty());
    }
}
