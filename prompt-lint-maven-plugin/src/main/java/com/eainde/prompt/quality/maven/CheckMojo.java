package com.eainde.prompt.quality.maven;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.report.PromptQualityReport;
import com.eainde.prompt.quality.report.PromptQualityReportRenderer;
import com.eainde.prompt.quality.sources.LoadResult;
import com.eainde.prompt.quality.sources.NamedPrompt;
import com.eainde.prompt.quality.sources.PromptSource;
import com.eainde.prompt.quality.sources.SourceLoaders;
import com.eainde.prompt.quality.sources.SourceMapping;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lints prompts extracted from seed files and fails the build on quality violations. */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, requiresProject = true,
        threadSafe = true)
public class CheckMojo extends AbstractMojo {

    @Parameter(required = true)
    private SourceConfig[] sources;

    @Parameter(defaultValue = "0.75")
    private double failOnScoreBelow;

    @Parameter(defaultValue = "true")
    private boolean failOnCritical;

    @Parameter(defaultValue = "true")
    private boolean htmlReport;

    @Parameter(defaultValue = "false")
    private boolean allowEmpty;

    @Parameter
    private File lexiconFile;

    @Parameter
    private File baselineFile;

    @Parameter(defaultValue = "${project.build.directory}/prompt-lint")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (sources == null || sources.length == 0) {
            throw new MojoExecutionException("No <sources> configured for prompt-lint:check");
        }

        PromptQualityAnalyzer analyzer = buildAnalyzer();
        PromptQualityReportRenderer renderer = new PromptQualityReportRenderer();

        List<NamedPrompt> all = loadAll();
        List<PromptQualityReport> reports = new ArrayList<>();
        for (NamedPrompt np : all) {
            PromptQualityReport report = analyzer.analyze(np.prompt());
            reports.add(report);
            getLog().info(renderer.render(report, failOnScoreBelow));
        }
        getLog().info(renderer.renderSummary(reports, failOnScoreBelow));

        if (htmlReport) {
            writeHtml(reports);
        }

        enforce(reports);
    }

    private List<NamedPrompt> loadAll() throws MojoExecutionException, MojoFailureException {
        List<NamedPrompt> all = new ArrayList<>();
        for (SourceConfig sc : sources) {
            if (sc.path == null) {
                throw new MojoExecutionException("A <source> is missing its <path>");
            }
            Path path = sc.path.toPath();
            if (!Files.exists(path)) {
                throw new MojoExecutionException("Source file not found: " + path);
            }
            String type = sc.type != null ? sc.type : SourceLoaders.inferType(path);
            PromptSource loader = SourceLoaders.forType(type);
            SourceMapping mapping = sc.mapping != null
                    ? sc.mapping.toMapping()
                    : SourceMapping.systemOnly("systemPrompt");

            LoadResult result = loader.load(path, mapping);
            result.warnings().forEach(w -> getLog().warn("[" + path.getFileName() + "] " + w));

            boolean allowEmptyHere = sc.allowEmpty != null ? sc.allowEmpty : allowEmpty;
            if (result.prompts().isEmpty() && !allowEmptyHere) {
                throw new MojoFailureException("No prompts extracted from " + path
                        + " — check the <mapping>, or set <allowEmpty>true</allowEmpty>");
            }
            all.addAll(result.prompts());
        }
        return all;
    }

    private PromptQualityAnalyzer buildAnalyzer() {
        PromptQualityAnalyzer.Builder builder = PromptQualityAnalyzer.builder();
        if (lexiconFile != null) {
            builder.lexicon(Lexicon.from(lexiconFile.toPath()));
        }
        if (baselineFile != null) {
            builder.baseline(baselineFile.toPath());
        }
        return builder.build();
    }

    private void writeHtml(List<PromptQualityReport> reports) throws MojoExecutionException {
        try {
            Files.createDirectories(outputDirectory.toPath());
            Path out = outputDirectory.toPath().resolve("report.html");
            String html = new HtmlReportRenderer().render(reports, failOnScoreBelow);
            Files.writeString(out, html);
            getLog().info("Prompt-lint HTML report: " + out);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write HTML report", e);
        }
    }

    private void enforce(List<PromptQualityReport> reports) throws MojoFailureException {
        List<PromptQualityReport> failures = reports.stream()
                .filter(r -> !r.passes(failOnScoreBelow)
                        || (failOnCritical && r.hasCriticalIssues()))
                .toList();
        if (failures.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("Prompt quality gate failed for "
                + failures.size() + " prompt(s):\n");
        for (PromptQualityReport r : failures) {
            sb.append(String.format("  - %s: score %.2f (threshold %.2f)%s%n",
                    r.agentName(), r.overallScore(), failOnScoreBelow,
                    r.hasCriticalIssues() ? " — has CRITICAL issues" : ""));
        }
        throw new MojoFailureException(sb.toString());
    }
}
