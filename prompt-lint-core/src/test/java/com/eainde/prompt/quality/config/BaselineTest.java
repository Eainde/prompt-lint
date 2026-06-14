package com.eainde.prompt.quality.config;

import com.eainde.prompt.quality.model.*;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaselineTest {

    @TempDir
    Path dir;

    private PromptQualityReport reportWithIssues() {
        var issues = List.of(
                QualityIssue.critical("CLARITY", "no output section", "CLR-005"),
                QualityIssue.warning("CLARITY", "vague", "CLR-004"));
        var result = new DimensionResult("CLARITY", 0.5, 1.0, issues, List.of());
        return new PromptQualityReport("agent-x", AgentTypeProfile.DEFAULT,
                List.of(result), 0.5, LocalDateTime.now(), List.of());
    }

    @Test
    void fromReport_collects_agent_and_ruleId_fingerprints() {
        var baseline = Baseline.fromReport(reportWithIssues());
        assertThat(baseline.contains("agent-x", "CLR-005")).isTrue();
        assertThat(baseline.contains("agent-x", "CLR-004")).isTrue();
        assertThat(baseline.contains("agent-x", "GRD-001")).isFalse();
        assertThat(baseline.contains("other-agent", "CLR-005")).isFalse();
    }

    @Test
    void writeTo_then_load_roundtrips_sorted_stable_json() throws Exception {
        Path f = dir.resolve("baseline.json");
        Baseline.fromReport(reportWithIssues()).writeTo(f);
        String json = Files.readString(f);
        assertThat(json).contains("\"version\"");
        // sorted entries: CLR-004 before CLR-005
        assertThat(json.indexOf("CLR-004")).isLessThan(json.indexOf("CLR-005"));
        var loaded = Baseline.load(f);
        assertThat(loaded.contains("agent-x", "CLR-005")).isTrue();
    }

    @Test
    void load_missing_file_throws() {
        assertThatThrownBy(() -> Baseline.load(dir.resolve("nope.json")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("nope.json");
    }
}
