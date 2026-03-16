package com.eainde.prompt.quality.report;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptQualityReportTest {

    private DimensionResult dim(String name, double score) {
        return new DimensionResult(name, score, 1.0, List.of(), List.of());
    }

    private DimensionResult dimWithIssues(String name, double score, List<QualityIssue> issues, List<String> suggestions) {
        return new DimensionResult(name, score, 1.0, issues, suggestions);
    }

    private PromptQualityReport report(double score, List<DimensionResult> dims) {
        return new PromptQualityReport("test", AgentTypeProfile.DEFAULT, dims, score, LocalDateTime.now());
    }

    @Test
    @DisplayName("passes returns true when score >= threshold")
    void passesTrue() {
        assertTrue(report(0.8, List.of()).passes(0.75));
    }

    @Test
    @DisplayName("passes returns false when score < threshold")
    void passesFalse() {
        assertFalse(report(0.5, List.of()).passes(0.75));
    }

    @Test
    @DisplayName("passes returns true when score == threshold")
    void passesEqual() {
        assertTrue(report(0.75, List.of()).passes(0.75));
    }

    @Test
    @DisplayName("hasCriticalIssues true when CRITICAL present")
    void hasCriticalTrue() {
        var r = report(0.5, List.of(dimWithIssues("C", 0.5,
                List.of(QualityIssue.critical("C", "bad", "X")), List.of())));
        assertTrue(r.hasCriticalIssues());
    }

    @Test
    @DisplayName("hasCriticalIssues false when no CRITICAL")
    void hasCriticalFalse() {
        var r = report(0.8, List.of(dimWithIssues("C", 0.8,
                List.of(QualityIssue.warning("C", "warn", "X")), List.of())));
        assertFalse(r.hasCriticalIssues());
    }

    @Test
    @DisplayName("hasCriticalIssues false when no issues")
    void hasCriticalFalseEmpty() {
        assertFalse(report(0.9, List.of(dim("C", 0.9))).hasCriticalIssues());
    }

    @Test
    @DisplayName("allIssues returns sorted by severity")
    void allIssuesSorted() {
        var r = report(0.5, List.of(
                dimWithIssues("A", 0.5, List.of(
                        QualityIssue.info("A", "info", "I"),
                        QualityIssue.critical("A", "crit", "C")), List.of()),
                dimWithIssues("B", 0.5, List.of(
                        QualityIssue.warning("B", "warn", "W")), List.of())));
        var issues = r.allIssues();
        assertEquals(3, issues.size());
        // Severity enum order: CRITICAL, WARNING, INFO
        assertEquals(Severity.CRITICAL, issues.get(0).severity());
        assertEquals(Severity.WARNING, issues.get(1).severity());
        assertEquals(Severity.INFO, issues.get(2).severity());
    }

    @Test
    @DisplayName("allIssues empty when no issues")
    void allIssuesEmpty() {
        assertTrue(report(0.9, List.of(dim("C", 0.9))).allIssues().isEmpty());
    }

    @Test
    @DisplayName("issuesBySeverity filters correctly")
    void issuesBySeverity() {
        var r = report(0.5, List.of(
                dimWithIssues("A", 0.5, List.of(
                        QualityIssue.critical("A", "c", "C"),
                        QualityIssue.warning("A", "w", "W"),
                        QualityIssue.info("A", "i", "I")), List.of())));
        assertEquals(1, r.issuesBySeverity(Severity.CRITICAL).size());
        assertEquals(1, r.issuesBySeverity(Severity.WARNING).size());
        assertEquals(1, r.issuesBySeverity(Severity.INFO).size());
    }

    @Test
    @DisplayName("allSuggestions flattens from all dimensions")
    void allSuggestions() {
        var r = report(0.5, List.of(
                dimWithIssues("A", 0.5, List.of(), List.of("sug1", "sug2")),
                dimWithIssues("B", 0.5, List.of(), List.of("sug3"))));
        assertEquals(3, r.allSuggestions().size());
    }

    @Test
    @DisplayName("allSuggestions empty when no suggestions")
    void allSuggestionsEmpty() {
        assertTrue(report(0.9, List.of(dim("C", 0.9))).allSuggestions().isEmpty());
    }

    @Test
    @DisplayName("totalIssueCount returns correct count")
    void totalIssueCount() {
        var r = report(0.5, List.of(
                dimWithIssues("A", 0.5, List.of(
                        QualityIssue.info("A", "a", "1"),
                        QualityIssue.info("A", "b", "2")), List.of())));
        assertEquals(2, r.totalIssueCount());
    }

    @Test
    @DisplayName("lowestScoringDimension returns min")
    void lowestScoringDimension() {
        var r = report(0.6, List.of(
                dim("A", 0.9),
                dim("B", 0.3),
                dim("C", 0.7)));
        assertEquals("B", r.lowestScoringDimension().dimension());
        assertEquals(0.3, r.lowestScoringDimension().score());
    }

    @Test
    @DisplayName("lowestScoringDimension returns null when empty")
    void lowestScoringDimensionEmpty() {
        assertNull(report(0.0, List.of()).lowestScoringDimension());
    }

    @Test
    @DisplayName("resultFor returns matching dimension")
    void resultForMatch() {
        var r = report(0.8, List.of(dim("A", 0.9), dim("B", 0.7)));
        assertEquals("B", r.resultFor("B").dimension());
    }

    @Test
    @DisplayName("resultFor returns null when not found")
    void resultForNull() {
        assertNull(report(0.8, List.of(dim("A", 0.9))).resultFor("X"));
    }
}
