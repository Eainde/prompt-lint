package com.eainde.prompt.quality.maven;

import com.eainde.prompt.quality.sources.SourceMapping;
import org.apache.maven.plugins.annotations.Parameter;

/** Maven config object for a {@code <mapping>} block. Public fields are injected by Maven. */
public class MappingConfig {

    @Parameter public String nameField;
    @Parameter public String systemPrompt;
    @Parameter public String userPrompt;
    @Parameter public String agentType;
    @Parameter public String inputs;
    @Parameter public String outputs;
    @Parameter public String responseSchema;
    @Parameter public String table;

    public SourceMapping toMapping() {
        return new SourceMapping(nameField, systemPrompt, userPrompt, agentType,
                inputs, outputs, responseSchema, table);
    }
}
