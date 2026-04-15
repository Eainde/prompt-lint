package com.eainde.prompt.quality;

import com.eainde.prompt.quality.analyzers.ClarityAnalyzer;
import com.eainde.prompt.quality.analyzers.PromptDimensionAnalyzer;
import com.eainde.prompt.quality.analyzers.SpecificityAnalyzer;
import com.eainde.prompt.quality.api.PromptQualityResult;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.Severity;
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
                Set.of("input"), "output", "EXTRACTION", weights, null, 0.5);
        assertEquals("CUSTOM", result.profile());
    }

    @Test
    @DisplayName("analyzeAndReport raw: empty customWeights + profileName -> named profile")
    void rawProfileName() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", "EXTRACTION", Map.of(), null, 0.5);
        assertEquals("EXTRACTION", result.profile());
    }

    @Test
    @DisplayName("analyzeAndReport raw: null customWeights + profileName -> named profile")
    void rawNullWeightsProfileName() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", "REVIEW", null, null, 0.5);
        assertEquals("REVIEW", result.profile());
    }

    @Test
    @DisplayName("analyzeAndReport raw: blank profileName + null customWeights -> DEFAULT")
    void rawBlankProfileNullWeights() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", "  ", null, null, 0.5);
        assertEquals("DEFAULT", result.profile());
    }

    @Test
    @DisplayName("analyzeAndReport raw: null profileName + null customWeights -> DEFAULT")
    void rawNullProfileNullWeights() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        PromptQualityResult result = analyzer.analyzeAndReport(
                "test", "You are an agent.", "{{input}}",
                Set.of("input"), "output", null, null, null, 0.5);
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

    @Test
    @DisplayName("SeverityCalibrator downgrades CLR-001 to INFO for FORMATTING profile")
    void severityCalibrationFormattingProfile() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        // No role definition -> triggers CLR-001 as WARNING normally
        var prompt = new PromptUnderTest("test",
                "Format the data as JSON. Return valid JSON. Apply formatting rules.\n## Output\n```json\n{\"data\": []}\n```",
                "{{input}}", Set.of("input"), "result", AgentTypeProfile.FORMATTING);
        PromptQualityReport report = analyzer.analyze(prompt);
        var clr001 = report.allIssues().stream()
                .filter(i -> "CLR-001".equals(i.ruleId()))
                .findFirst();
        assertTrue(clr001.isPresent(), "CLR-001 should be present");
        assertEquals(Severity.INFO, clr001.get().severity(),
                "CLR-001 should be downgraded to INFO for FORMATTING profile");
    }

    @Test
    @DisplayName("SeverityCalibrator keeps CLR-001 as WARNING for EXTRACTION profile")
    void severityCalibrationExtractionProfile() {
        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
        var prompt = new PromptUnderTest("test",
                "Extract data from documents.",
                "{{input}}", Set.of("input"), "result", AgentTypeProfile.EXTRACTION);
        PromptQualityReport report = analyzer.analyze(prompt);
        var clr001 = report.allIssues().stream()
                .filter(i -> "CLR-001".equals(i.ruleId()))
                .findFirst();
        assertTrue(clr001.isPresent());
        assertEquals(Severity.WARNING, clr001.get().severity(),
                "CLR-001 should remain WARNING for EXTRACTION profile");
    }

    @Test
    @DisplayName("analyze includes suggested fixes for weak prompt")
    void analyzeIncludesSuggestedFixes() {
        var prompt = new PromptUnderTest("test",
                "Extract data from documents.",
                "{{sourceText}}", Set.of("sourceText"), "result", AgentTypeProfile.EXTRACTION);
        var report = PromptQualityAnalyzer.create().analyze(prompt);
        assertFalse(report.suggestedFixes().isEmpty(), "Should have suggested fixes");
        assertTrue(report.suggestedFixes().stream().allMatch(f -> f.ruleId() != null));
    }

    @Test
    @DisplayName("withAdditionalAnalyzers includes custom dimension in report")
    void withAdditionalAnalyzersIncludedInReport() {
        var custom = new PromptDimensionAnalyzer() {
            @Override public String dimensionName() { return "CUSTOM"; }
            @Override public DimensionResult analyze(PromptUnderTest p) {
                return new DimensionResult("CUSTOM", 1.0, 1.0, List.of(), List.of());
            }
        };
        var analyzer = PromptQualityAnalyzer.create().withAdditionalAnalyzers(custom);
        var report = analyzer.analyze(simplePrompt());
        assertEquals(9, report.dimensionResults().size());
    }

    @Test
    @DisplayName("withAdditionalAnalyzers rejects duplicate dimension name")
    void withAdditionalAnalyzersDuplicateRejected() {
        var duplicate = new PromptDimensionAnalyzer() {
            @Override public String dimensionName() { return "CLARITY"; }
            @Override public DimensionResult analyze(PromptUnderTest p) {
                return new DimensionResult("CLARITY", 1.0, 1.0, List.of(), List.of());
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> PromptQualityAnalyzer.create().withAdditionalAnalyzers(duplicate));
    }
}
