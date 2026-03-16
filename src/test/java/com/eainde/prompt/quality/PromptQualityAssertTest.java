package com.eainde.prompt.quality;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PromptQualityAssertTest {

    private PromptQualityReport reportWithScore(double score, List<DimensionResult> dims) {
        return new PromptQualityReport("test-agent", AgentTypeProfile.DEFAULT, dims, score, LocalDateTime.now());
    }

    private DimensionResult dim(String name, double score) {
        return new DimensionResult(name, score, 1.0, List.of(), List.of());
    }

    private DimensionResult dimWithIssues(String name, double score, List<QualityIssue> issues) {
        return new DimensionResult(name, score, 1.0, issues, List.of());
    }

    @Test
    @DisplayName("passesThreshold succeeds when score >= threshold")
    void passesThresholdSuccess() {
        var report = reportWithScore(0.8, List.of(dim("CLARITY", 0.8)));
        assertDoesNotThrow(() -> PromptQualityAssert.assertThat(report).passesThreshold(0.75));
    }

    @Test
    @DisplayName("passesThreshold fails when score < threshold")
    void passesThresholdFailure() {
        var report = reportWithScore(0.5, List.of(dim("CLARITY", 0.5)));
        assertThrows(AssertionError.class,
                () -> PromptQualityAssert.assertThat(report).passesThreshold(0.75));
    }

    @Test
    @DisplayName("hasNoCriticalIssues succeeds when no criticals")
    void hasNoCriticalIssuesSuccess() {
        var report = reportWithScore(0.8, List.of(
                dimWithIssues("CLARITY", 0.8, List.of(
                        QualityIssue.warning("CLARITY", "minor", "CLR-001")))));
        assertDoesNotThrow(() -> PromptQualityAssert.assertThat(report).hasNoCriticalIssues());
    }

    @Test
    @DisplayName("hasNoCriticalIssues fails when criticals present")
    void hasNoCriticalIssuesFailure() {
        var report = reportWithScore(0.3, List.of(
                dimWithIssues("CLARITY", 0.3, List.of(
                        QualityIssue.critical("CLARITY", "bad", "CLR-005")))));
        assertThrows(AssertionError.class,
                () -> PromptQualityAssert.assertThat(report).hasNoCriticalIssues());
    }

    @Test
    @DisplayName("hasNoWarnings succeeds when no warnings or criticals")
    void hasNoWarningsSuccess() {
        var report = reportWithScore(0.9, List.of(
                dimWithIssues("CLARITY", 0.9, List.of(
                        QualityIssue.info("CLARITY", "fyi", "CLR-007")))));
        assertDoesNotThrow(() -> PromptQualityAssert.assertThat(report).hasNoWarnings());
    }

    @Test
    @DisplayName("hasNoWarnings fails when warnings present")
    void hasNoWarningsFailure() {
        var report = reportWithScore(0.7, List.of(
                dimWithIssues("CLARITY", 0.7, List.of(
                        QualityIssue.warning("CLARITY", "warn", "CLR-001")))));
        assertThrows(AssertionError.class,
                () -> PromptQualityAssert.assertThat(report).hasNoWarnings());
    }

    @Test
    @DisplayName("hasNoWarnings fails when criticals present (calls hasNoCriticalIssues)")
    void hasNoWarningsFailsOnCriticals() {
        var report = reportWithScore(0.3, List.of(
                dimWithIssues("CLARITY", 0.3, List.of(
                        QualityIssue.critical("CLARITY", "crit", "CLR-005")))));
        assertThrows(AssertionError.class,
                () -> PromptQualityAssert.assertThat(report).hasNoWarnings());
    }

    @Test
    @DisplayName("dimensionScoreAbove succeeds when score >= minimum")
    void dimensionScoreAboveSuccess() {
        var report = reportWithScore(0.8, List.of(dim("CLARITY", 0.85)));
        assertDoesNotThrow(() ->
                PromptQualityAssert.assertThat(report).dimensionScoreAbove("CLARITY", 0.8));
    }

    @Test
    @DisplayName("dimensionScoreAbove fails when score < minimum")
    void dimensionScoreAboveFailure() {
        var report = reportWithScore(0.8, List.of(dim("CLARITY", 0.5)));
        assertThrows(AssertionError.class,
                () -> PromptQualityAssert.assertThat(report).dimensionScoreAbove("CLARITY", 0.8));
    }

    @Test
    @DisplayName("dimensionScoreAbove fails when dimension not found")
    void dimensionScoreAboveNotFound() {
        var report = reportWithScore(0.8, List.of(dim("CLARITY", 0.9)));
        assertThrows(AssertionError.class,
                () -> PromptQualityAssert.assertThat(report).dimensionScoreAbove("GROUNDEDNESS", 0.5));
    }

    @Test
    @DisplayName("hasFewerIssuesThan succeeds when under limit")
    void hasFewerIssuesThanSuccess() {
        var report = reportWithScore(0.8, List.of(
                dimWithIssues("CLARITY", 0.8, List.of(
                        QualityIssue.info("CLARITY", "fyi", "CLR-007")))));
        assertDoesNotThrow(() -> PromptQualityAssert.assertThat(report).hasFewerIssuesThan(5));
    }

    @Test
    @DisplayName("hasFewerIssuesThan fails when at or above limit")
    void hasFewerIssuesThanFailure() {
        var report = reportWithScore(0.5, List.of(
                dimWithIssues("CLARITY", 0.5, List.of(
                        QualityIssue.info("C", "a", "A"),
                        QualityIssue.info("C", "b", "B")))));
        assertThrows(AssertionError.class,
                () -> PromptQualityAssert.assertThat(report).hasFewerIssuesThan(2));
    }

    @Test
    @DisplayName("doesNotHaveIssue succeeds when ruleId absent")
    void doesNotHaveIssueSuccess() {
        var report = reportWithScore(0.8, List.of(
                dimWithIssues("CLARITY", 0.8, List.of(
                        QualityIssue.info("CLARITY", "fyi", "CLR-007")))));
        assertDoesNotThrow(() ->
                PromptQualityAssert.assertThat(report).doesNotHaveIssue("CLR-001"));
    }

    @Test
    @DisplayName("doesNotHaveIssue fails when ruleId present")
    void doesNotHaveIssueFailure() {
        var report = reportWithScore(0.8, List.of(
                dimWithIssues("CLARITY", 0.8, List.of(
                        QualityIssue.info("CLARITY", "fyi", "CLR-007")))));
        assertThrows(AssertionError.class,
                () -> PromptQualityAssert.assertThat(report).doesNotHaveIssue("CLR-007"));
    }

    @Test
    @DisplayName("printReport does not throw and returns self")
    void printReportDoesNotThrow() {
        var report = reportWithScore(0.8, List.of(dim("CLARITY", 0.8)));
        assertDoesNotThrow(() ->
                PromptQualityAssert.assertThat(report).printReport(0.75));
    }

    @Test
    @DisplayName("fluent chain works")
    void fluentChain() {
        var report = reportWithScore(0.9, List.of(
                dimWithIssues("CLARITY", 0.9, List.of(
                        QualityIssue.info("CLARITY", "fyi", "CLR-007")))));
        assertDoesNotThrow(() ->
                PromptQualityAssert.assertThat(report)
                        .passesThreshold(0.75)
                        .hasNoCriticalIssues()
                        .dimensionScoreAbove("CLARITY", 0.8)
                        .hasFewerIssuesThan(10)
                        .doesNotHaveIssue("CLR-005"));
    }
}
