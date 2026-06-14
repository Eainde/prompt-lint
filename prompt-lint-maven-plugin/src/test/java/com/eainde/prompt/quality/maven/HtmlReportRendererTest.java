package com.eainde.prompt.quality.maven;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlReportRendererTest {

    @Test
    void rendersSelfContainedHtmlAndEscapes() {
        var dim = new DimensionResult("CLARITY", 0.4, 1.0,
                List.of(QualityIssue.warning("CLARITY", "Vague <term> & stuff", "CLR-005")),
                List.of());
        var report = new PromptQualityReport("agent-1", AgentTypeProfile.DEFAULT,
                List.of(dim), 0.4, LocalDateTime.of(2026, 6, 13, 10, 0));

        String html = new HtmlReportRenderer().render(List.of(report), 0.75);

        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("agent-1");
        assertThat(html).contains("CLR-005");
        assertThat(html).contains("Vague &lt;term&gt; &amp; stuff");
        assertThat(html).contains("FAIL");
    }
}
