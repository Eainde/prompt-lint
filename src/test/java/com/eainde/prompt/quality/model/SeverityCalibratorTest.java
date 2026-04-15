package com.eainde.prompt.quality.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SeverityCalibratorTest {

    @Test
    void grd001_critical_for_extraction() {
        var issue = QualityIssue.critical("GROUNDEDNESS", "No grounding instruction", "GRD-001");
        var result = SeverityCalibrator.calibrate(issue, AgentTypeProfile.EXTRACTION);
        assertThat(result).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void grd001_info_for_formatting() {
        var issue = QualityIssue.critical("GROUNDEDNESS", "No grounding instruction", "GRD-001");
        var result = SeverityCalibrator.calibrate(issue, AgentTypeProfile.FORMATTING);
        assertThat(result).isEqualTo(Severity.INFO);
    }

    @Test
    void grd002_info_for_formatting() {
        var issue = QualityIssue.critical("GROUNDEDNESS", "No external knowledge prohibition", "GRD-002");
        var result = SeverityCalibrator.calibrate(issue, AgentTypeProfile.FORMATTING);
        assertThat(result).isEqualTo(Severity.INFO);
    }

    @Test
    void clr001_warning_for_extraction_but_info_for_formatting() {
        var issue = QualityIssue.warning("CLARITY", "No role definition", "CLR-001");
        var resultExtraction = SeverityCalibrator.calibrate(issue, AgentTypeProfile.EXTRACTION);
        var resultFormatting = SeverityCalibrator.calibrate(issue, AgentTypeProfile.FORMATTING);
        assertThat(resultExtraction).isEqualTo(Severity.WARNING);
        assertThat(resultFormatting).isEqualTo(Severity.INFO);
    }

    @Test
    void unknown_rule_returns_original_severity() {
        var issue = QualityIssue.warning("CUSTOM", "Custom issue", "CUSTOM-001");
        var result = SeverityCalibrator.calibrate(issue, AgentTypeProfile.EXTRACTION);
        assertThat(result).isEqualTo(Severity.WARNING);
    }

    @Test
    void default_profile_returns_original_severity() {
        var issue = QualityIssue.critical("GROUNDEDNESS", "No grounding", "GRD-001");
        var result = SeverityCalibrator.calibrate(issue, AgentTypeProfile.DEFAULT);
        assertThat(result).isEqualTo(Severity.CRITICAL);
    }
}
