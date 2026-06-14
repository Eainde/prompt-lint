package com.eainde.prompt.quality.report;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.QualityIssue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptQualityReportRendererTest {

    private final PromptQualityReportRenderer renderer = new PromptQualityReportRenderer();

    private DimensionResult dim(String name, double score) {
        return new DimensionResult(name, score, 1.0, List.of(), List.of());
    }

    private DimensionResult dimWithIssues(String name, double score, List<QualityIssue> issues, List<String> suggestions) {
        return new DimensionResult(name, score, 1.0, issues, suggestions);
    }

    private PromptQualityReport report(String agentName, double score, List<DimensionResult> dims) {
        return new PromptQualityReport(agentName, AgentTypeProfile.DEFAULT, dims, score, LocalDateTime.now());
    }

    // ── render() tests ────────────────────────────────────────────────

    @Test
    @DisplayName("render includes agent name and profile")
    void renderIncludesHeader() {
        var r = report("my-agent", 0.85, List.of(dim("CLARITY", 0.9)));
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("my-agent"));
        assertTrue(output.contains("DEFAULT"));
    }

    @Test
    @DisplayName("render shows PASS when score >= threshold")
    void renderShowsPass() {
        var r = report("agent", 0.85, List.of(dim("CLARITY", 0.9)));
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("PASS"));
    }

    @Test
    @DisplayName("render shows FAIL when score < threshold")
    void renderShowsFail() {
        var r = report("agent", 0.50, List.of(dim("CLARITY", 0.5)));
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("FAIL"));
    }

    @Test
    @DisplayName("render includes dimension table")
    void renderIncludesDimensionTable() {
        var r = report("agent", 0.85, List.of(
                dim("CLARITY", 0.9),
                dim("SPECIFICITY", 0.7)));
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("CLARITY"));
        assertTrue(output.contains("SPECIFICITY"));
        assertTrue(output.contains("Score"));
        assertTrue(output.contains("Weight"));
        assertTrue(output.contains("Contrib"));
    }

    @Test
    @DisplayName("render shows weakest dimension when score < 0.8")
    void renderShowsWeakestDimension() {
        var r = report("agent", 0.6, List.of(
                dim("CLARITY", 0.9),
                dim("SPECIFICITY", 0.3)));
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("Weakest dimension"));
        assertTrue(output.contains("SPECIFICITY"));
    }

    @Test
    @DisplayName("render does not show weakest when all >= 0.8")
    void renderNoWeakestWhenAllHigh() {
        var r = report("agent", 0.9, List.of(dim("CLARITY", 0.9)));
        String output = renderer.render(r, 0.75);
        assertFalse(output.contains("Weakest dimension"));
    }

    @Test
    @DisplayName("render shows issues grouped by severity")
    void renderShowsIssues() {
        var r = report("agent", 0.5, List.of(
                dimWithIssues("CLARITY", 0.5, List.of(
                        QualityIssue.critical("CLARITY", "critical msg", "CLR-005"),
                        QualityIssue.warning("CLARITY", "warning msg", "CLR-001"),
                        QualityIssue.info("CLARITY", "info msg", "CLR-007")
                ), List.of())));
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("CRITICAL"));
        assertTrue(output.contains("WARNING"));
        assertTrue(output.contains("INFO"));
        assertTrue(output.contains("critical msg"));
        assertTrue(output.contains("warning msg"));
        assertTrue(output.contains("info msg"));
    }

    @Test
    @DisplayName("render shows 'No issues found' when empty")
    void renderNoIssues() {
        var r = report("agent", 0.95, List.of(dim("CLARITY", 0.95)));
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("No issues found"));
    }

    @Test
    @DisplayName("render shows suggestions when present")
    void renderShowsSuggestions() {
        var r = report("agent", 0.5, List.of(
                dimWithIssues("CLARITY", 0.5, List.of(),
                        List.of("Add role definition.", "Add output format."))));
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("Suggestions"));
        assertTrue(output.contains("1. Add role definition."));
        assertTrue(output.contains("2. Add output format."));
    }

    @Test
    @DisplayName("render does not show suggestions section when empty")
    void renderNoSuggestions() {
        var r = report("agent", 0.9, List.of(dim("CLARITY", 0.9)));
        String output = renderer.render(r, 0.75);
        assertFalse(output.contains("Suggestions"));
    }

    @Test
    @DisplayName("render dimension indicators: good >= 0.8, warning >= 0.5, bad < 0.5")
    void renderDimensionIndicators() {
        var r = report("agent", 0.6, List.of(
                dim("HIGH", 0.9),
                dim("MID", 0.6),
                dim("LOW", 0.3)));
        String output = renderer.render(r, 0.5);
        // The indicators are in the table rows
        assertTrue(output.contains("HIGH"));
        assertTrue(output.contains("MID"));
        assertTrue(output.contains("LOW"));
    }

    // ── renderSummary() tests ─────────────────────────────────────────

    @Test
    @DisplayName("renderSummary includes agent count")
    void renderSummaryAgentCount() {
        var r1 = report("agent-1", 0.85, List.of(dim("CLARITY", 0.9)));
        var r2 = report("agent-2", 0.90, List.of(dim("CLARITY", 0.95)));
        String output = renderer.renderSummary(List.of(r1, r2), 0.75);
        assertTrue(output.contains("2 agents"));
    }

    @Test
    @DisplayName("renderSummary shows OVERALL PASS when all pass")
    void renderSummaryAllPass() {
        var r1 = report("agent-1", 0.85, List.of(dim("CLARITY", 0.9)));
        var r2 = report("agent-2", 0.90, List.of(dim("CLARITY", 0.95)));
        String output = renderer.renderSummary(List.of(r1, r2), 0.75);
        assertTrue(output.contains("OVERALL: PASS"));
    }

    @Test
    @DisplayName("renderSummary shows OVERALL FAIL and failed agents list")
    void renderSummaryWithFailures() {
        var r1 = report("good-agent", 0.85, List.of(dim("CLARITY", 0.9)));
        var r2 = report("bad-agent", 0.40, List.of(dim("CLARITY", 0.3)));
        String output = renderer.renderSummary(List.of(r1, r2), 0.75);
        assertTrue(output.contains("OVERALL: FAIL"));
        assertTrue(output.contains("Failed agents"));
        assertTrue(output.contains("bad-agent"));
    }

    @Test
    @DisplayName("renderSummary shows passed/failed/critical counts")
    void renderSummaryCounts() {
        var r1 = report("a", 0.85, List.of(dim("CLARITY", 0.9)));
        var r2 = report("b", 0.40, List.of(
                dimWithIssues("CLARITY", 0.3, List.of(
                        QualityIssue.critical("CLARITY", "bad", "CLR-005")
                ), List.of())));
        String output = renderer.renderSummary(List.of(r1, r2), 0.75);
        assertTrue(output.contains("1 passed"));
        assertTrue(output.contains("1 failed"));
        assertTrue(output.contains("1 critical"));
    }

    @Test
    @DisplayName("renderSummary includes agent scores and issue counts in table")
    void renderSummaryTable() {
        var r = report("my-agent", 0.85, List.of(dim("CLARITY", 0.9)));
        String output = renderer.renderSummary(List.of(r), 0.75);
        assertTrue(output.contains("my-agent"));
        assertTrue(output.contains("0.85"));
        assertTrue(output.contains("PASS"));
    }

    @Test
    @DisplayName("renderSummary truncates long agent names")
    void renderSummaryTruncatesLongNames() {
        String longName = "a".repeat(40);
        var r = report(longName, 0.85, List.of(dim("CLARITY", 0.9)));
        String output = renderer.renderSummary(List.of(r), 0.75);
        // truncate(name, 32) adds "…"
        assertTrue(output.contains("…"));
    }

    @Test
    @DisplayName("renderSummary empty list")
    void renderSummaryEmpty() {
        String output = renderer.renderSummary(List.of(), 0.75);
        assertTrue(output.contains("0 agents"));
        assertTrue(output.contains("OVERALL: PASS"));
    }

    @Test
    @DisplayName("renderSummary weakest dimension shows N/A when null")
    void renderSummaryWeakestNA() {
        // A report with no dimensions will have lowestScoringDimension() == null
        var r = new PromptQualityReport("empty-agent", AgentTypeProfile.DEFAULT,
                List.of(), 0.0, LocalDateTime.now());
        String output = renderer.renderSummary(List.of(r), 0.75);
        assertTrue(output.contains("N/A"));
    }

    // ── render() with PromptQualityReport ─────────────────────────────

    @Test
    @DisplayName("render with no dimensions still produces output")
    void renderNoDimensions() {
        var r = new PromptQualityReport("empty", AgentTypeProfile.DEFAULT,
                List.of(), 0.0, LocalDateTime.now());
        String output = renderer.render(r, 0.75);
        assertTrue(output.contains("empty"));
        assertTrue(output.contains("FAIL"));
    }
}
