package com.eainde.prompt.quality.sources;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class YamlPropertiesSourceTest {

    private static Path resource(String name) {
        return Path.of("src/test/resources", name);
    }

    @Test
    void loadsYamlObjectKeyedById() {
        var mapping = new SourceMapping(null, "system", null, "type", null, null, null, null);

        LoadResult result = new YamlPropertiesSource().load(resource("prompts.yaml"), mapping);

        assertThat(result.prompts()).hasSize(2);
        assertThat(result.prompts())
                .anySatisfy(np -> {
                    assertThat(np.id()).isEqualTo("greeter");
                    assertThat(np.prompt().agentTypeProfile()).isEqualTo(AgentTypeProfile.FORMATTING);
                });
    }

    @Test
    void loadsPropertiesWithPrefixConvention() {
        var mapping = new SourceMapping(null, "system", null, "type", null, null, null, null);

        LoadResult result = new YamlPropertiesSource().load(resource("prompts.properties"), mapping);

        assertThat(result.prompts()).hasSize(2);
        assertThat(result.prompts())
                .anySatisfy(np -> assertThat(np.id()).isEqualTo("greeter"))
                .anySatisfy(np -> assertThat(np.id()).isEqualTo("auditor"));
    }
}
