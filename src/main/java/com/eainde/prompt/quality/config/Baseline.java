package com.eainde.prompt.quality.config;

import com.eainde.prompt.quality.report.PromptQualityReport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Snapshot of known issues, keyed by {@code agentName:ruleId}. Issues present
 * in the baseline are filtered from later reports so only NEW issues surface.
 * Scores are NOT affected — only the issue lists.
 *
 * <p>Workflow: run once, {@code Baseline.fromReport(report).writeTo(path)},
 * commit the file, then configure {@code builder().baseline(path)}.</p>
 */
public final class Baseline {

    /** JSON shape: {"version":1,"entries":["agent:RULE", ...]} (sorted, git-diff stable). */
    record BaselineFile(int version, List<String> entries) {
        @JsonCreator
        BaselineFile(
                @JsonProperty("version") int version,
                @JsonProperty("entries") List<String> entries) {
            this.version = version;
            this.entries = entries;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Set<String> entries;

    private Baseline(Set<String> entries) {
        this.entries = Set.copyOf(entries);
    }

    public static Baseline fromReport(PromptQualityReport report) {
        var set = new TreeSet<String>();
        report.dimensionResults().forEach(r ->
                r.issues().forEach(i -> set.add(fingerprint(report.agentName(), i.ruleId()))));
        return new Baseline(set);
    }

    public static Baseline load(Path path) {
        try {
            BaselineFile file = MAPPER.readValue(path.toFile(), BaselineFile.class);
            return new Baseline(new TreeSet<>(file.entries()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load baseline file " + path
                    + " — run Baseline.fromReport(...).writeTo(path) to create it", e);
        }
    }

    public void writeTo(Path path) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),
                    new BaselineFile(1, new TreeSet<>(entries).stream().toList()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write baseline file " + path, e);
        }
    }

    public boolean contains(String agentName, String ruleId) {
        return entries.contains(fingerprint(agentName, ruleId));
    }

    private static String fingerprint(String agentName, String ruleId) {
        return agentName + ":" + ruleId;
    }
}
