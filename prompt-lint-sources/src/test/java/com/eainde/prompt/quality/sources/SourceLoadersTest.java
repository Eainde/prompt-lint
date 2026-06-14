package com.eainde.prompt.quality.sources;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceLoadersTest {

    @Test
    void resolvesLoaderByType() {
        assertThat(SourceLoaders.forType("sql")).isInstanceOf(SqlSeedSource.class);
        assertThat(SourceLoaders.forType("JSON")).isInstanceOf(JsonSource.class);
        assertThat(SourceLoaders.forType("yaml")).isInstanceOf(YamlPropertiesSource.class);
        assertThat(SourceLoaders.forType("properties")).isInstanceOf(YamlPropertiesSource.class);
    }

    @Test
    void infersTypeFromExtension() {
        assertThat(SourceLoaders.inferType(Path.of("a/b/data.sql"))).isEqualTo("sql");
        assertThat(SourceLoaders.inferType(Path.of("p.JSON"))).isEqualTo("json");
        assertThat(SourceLoaders.inferType(Path.of("p.yml"))).isEqualTo("yaml");
        assertThat(SourceLoaders.inferType(Path.of("p.properties"))).isEqualTo("properties");
    }

    @Test
    void rejectsUnknownTypeAndExtension() {
        assertThatThrownBy(() -> SourceLoaders.forType("xml"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceLoaders.inferType(Path.of("p.xml")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
