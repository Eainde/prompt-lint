package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.report.PromptQualityReport;
import com.eainde.prompt.quality.report.PromptQualityReportRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: PromptQualityReportRenderer turns a report into formatted text for
 * console / CI logs — a single detailed report, or a summary table over many.
 */
class ReportRenderingExample {

    private final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
    private final PromptQualityReportRenderer renderer = new PromptQualityReportRenderer();

    @Test
    void renderASingleReport() {
        PromptQualityReport report = analyzer.analyze(Examples.wellFormedExtractionPrompt());

        String text = renderer.render(report, Examples.THRESHOLD);
        System.out.println(text);

        assertThat(text).contains("PROMPT QUALITY REPORT").contains("well-formed-extractor");
    }

    @Test
    void renderASummaryOverManyPrompts() {
        List<PromptQualityReport> reports = analyzer.analyzeAll(List.of(
                Examples.wellFormedExtractionPrompt(), Examples.flawedDraft()));

        String summary = renderer.renderSummary(reports, Examples.THRESHOLD);
        System.out.println(summary);

        assertThat(summary).contains("PROMPT QUALITY SUMMARY")
                .contains("well-formed-extractor").contains("flawed-draft");
    }
}
