package com.eainde.prompt.quality.sources;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SqlSeedSourceTest {

    @Test
    void loadsOnlyConfiguredTableAndMapsColumns() {
        var mapping = new SourceMapping(
                "agent_id", "system_text", "user_text", "agent_type",
                null, null, null, "prompts");

        LoadResult result = new SqlSeedSource()
                .load(Path.of("src/test/resources/seed.sql"), mapping);

        assertThat(result.prompts()).hasSize(2);
        assertThat(result.prompts())
                .anySatisfy(np -> {
                    assertThat(np.id()).isEqualTo("extractor");
                    assertThat(np.prompt().agentTypeProfile()).isEqualTo(AgentTypeProfile.EXTRACTION);
                    assertThat(np.prompt().userPrompt()).isEqualTo("Source: {{text}}");
                })
                .anySatisfy(np -> assertThat(np.id()).isEqualTo("greeter"));
    }

    @Test
    void columnNameMatchingIsCaseInsensitive() {
        var mapping = new SourceMapping(
                "AGENT_ID", "SYSTEM_TEXT", null, null, null, null, null, "prompts");

        LoadResult result = new SqlSeedSource()
                .load(Path.of("src/test/resources/seed.sql"), mapping);

        assertThat(result.prompts()).hasSize(2);
    }
}
