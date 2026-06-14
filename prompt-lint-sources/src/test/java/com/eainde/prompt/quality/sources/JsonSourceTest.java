package com.eainde.prompt.quality.sources;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSourceTest {

    private static Path resource(String name) {
        return Path.of("src/test/resources", name);
    }

    @Test
    void loadsArrayFormAndSkipsBlankSystem() {
        var mapping = new SourceMapping("name", "system", "user", "type", null, null, null, null);

        LoadResult result = new JsonSource().load(resource("prompts-array.json"), mapping);

        assertThat(result.prompts()).hasSize(1);
        NamedPrompt np = result.prompts().get(0);
        assertThat(np.id()).isEqualTo("extractor");
        assertThat(np.prompt().agentTypeProfile()).isEqualTo(AgentTypeProfile.EXTRACTION);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("blank-system"));
    }

    @Test
    void loadsObjectFormWithDottedPathAndKeyAsId() {
        var mapping = new SourceMapping(null, "prompt.system", null, "type", null, null, null, null);

        LoadResult result = new JsonSource().load(resource("prompts-object.json"), mapping);

        assertThat(result.prompts()).hasSize(1);
        NamedPrompt np = result.prompts().get(0);
        assertThat(np.id()).isEqualTo("classifier");
        assertThat(np.prompt().systemPrompt()).isEqualTo("Classify the sentiment.");
        assertThat(np.prompt().agentTypeProfile()).isEqualTo(AgentTypeProfile.CLASSIFICATION);
    }
}
