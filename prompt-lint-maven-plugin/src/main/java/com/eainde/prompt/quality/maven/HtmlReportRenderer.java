package com.eainde.prompt.quality.maven;

import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.QualityIssue;
import com.eainde.prompt.quality.report.PromptQualityReport;

import java.util.List;

/** Renders a list of reports as a single self-contained HTML document. */
public class HtmlReportRenderer {

    public String render(List<PromptQualityReport> reports, double threshold) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">");
        sb.append("<title>Prompt Lint Report</title><style>");
        sb.append("body{font-family:system-ui,sans-serif;margin:2rem;color:#1a1a1a}");
        sb.append("table{border-collapse:collapse;margin:0.5rem 0;width:100%}");
        sb.append("th,td{border:1px solid #ddd;padding:4px 8px;text-align:left;font-size:14px}");
        sb.append(".pass{color:#137333;font-weight:600}.fail{color:#c5221f;font-weight:600}");
        sb.append(".crit{color:#c5221f}.warn{color:#b06000}.info{color:#5f6368}");
        sb.append(".agent{margin:1.5rem 0;padding:1rem;border:1px solid #eee;border-radius:8px}");
        sb.append("</style></head><body>");
        sb.append("<h1>Prompt Lint Report</h1>");
        sb.append("<p>Threshold: ").append(String.format("%.2f", threshold)).append("</p>");

        for (PromptQualityReport report : reports) {
            boolean passed = report.passes(threshold);
            sb.append("<div class=\"agent\">");
            sb.append("<h2>").append(escape(report.agentName())).append(" ");
            sb.append("<span class=\"").append(passed ? "pass" : "fail").append("\">")
                    .append(passed ? "PASS" : "FAIL").append("</span></h2>");
            sb.append("<p>Profile: ").append(escape(report.profile().agentType()))
                    .append(" &middot; Overall: ")
                    .append(String.format("%.2f", report.overallScore())).append("</p>");

            sb.append("<table><tr><th>Dimension</th><th>Score</th><th>Weight</th></tr>");
            for (DimensionResult dim : report.dimensionResults()) {
                sb.append("<tr><td>").append(escape(dim.dimension())).append("</td><td>")
                        .append(String.format("%.2f", dim.score())).append("</td><td>")
                        .append(String.format("%.3f", report.profile().weightFor(dim.dimension())))
                        .append("</td></tr>");
            }
            sb.append("</table>");

            List<QualityIssue> issues = report.allIssues();
            if (!issues.isEmpty()) {
                sb.append("<table><tr><th>Severity</th><th>Rule</th><th>Message</th></tr>");
                for (QualityIssue issue : issues) {
                    String cls = switch (issue.severity()) {
                        case CRITICAL -> "crit";
                        case WARNING -> "warn";
                        case INFO -> "info";
                    };
                    sb.append("<tr><td class=\"").append(cls).append("\">")
                            .append(issue.severity()).append("</td><td>")
                            .append(escape(issue.ruleId())).append("</td><td>")
                            .append(escape(issue.message())).append("</td></tr>");
                }
                sb.append("</table>");
            } else {
                sb.append("<p>No issues found.</p>");
            }
            sb.append("</div>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
