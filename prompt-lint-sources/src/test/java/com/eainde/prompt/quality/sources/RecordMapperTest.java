package com.eainde.prompt.quality.sources;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class RecordMapperTest {

    private static Function<String, String> from(Map<String, String> m) {
        return m::get;
    }

    @Test
    void mapsAllFields() {
        var record = Map.of(
                "ID", "extractor",
                "SYS", "You extract names.",
                "USR", "Text: {{text}}",
                "TYPE", "extraction",
                "IN", "text, fileNames",
                "OUT", "names");
        var mapping = new SourceMapping("ID", "SYS", "USR", "TYPE", "IN", "OUT", null, null);
        var warnings = new ArrayList<String>();

        NamedPrompt np = RecordMapper.toNamedPrompt(from(record), mapping, "fallback", warnings);

        assertThat(warnings).isEmpty();
        assertThat(np).isNotNull();
        assertThat(np.id()).isEqualTo("extractor");
        assertThat(np.prompt().systemPrompt()).isEqualTo("You extract names.");
        assertThat(np.prompt().userPrompt()).isEqualTo("Text: {{text}}");
        assertThat(np.prompt().agentTypeProfile()).isEqualTo(AgentTypeProfile.EXTRACTION);
        assertThat(np.prompt().declaredInputs()).containsExactlyInAnyOrder("text", "fileNames");
        assertThat(np.prompt().declaredOutputKey()).isEqualTo("names");
    }

    @Test
    void skipsRecordWithoutSystemPrompt() {
        var mapping = SourceMapping.systemOnly("SYS");
        var warnings = new ArrayList<String>();

        NamedPrompt np = RecordMapper.toNamedPrompt(from(Map.of("OTHER", "x")), mapping, "row-3", warnings);

        assertThat(np).isNull();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("row-3").contains("SYS");
    }

    @Test
    void defaultsUserToEmptyAndProfileToDefault() {
        var mapping = SourceMapping.systemOnly("SYS");
        var warnings = new ArrayList<String>();

        NamedPrompt np = RecordMapper.toNamedPrompt(from(Map.of("SYS", "hi")), mapping, "row-0", warnings);

        assertThat(np.id()).isEqualTo("row-0");
        assertThat(np.prompt().userPrompt()).isEmpty();
        assertThat(np.prompt().agentTypeProfile()).isEqualTo(AgentTypeProfile.DEFAULT);
        assertThat(np.prompt().declaredInputs()).isEmpty();
    }
}
