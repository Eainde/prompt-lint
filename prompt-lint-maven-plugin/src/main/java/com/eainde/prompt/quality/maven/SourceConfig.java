package com.eainde.prompt.quality.maven;

import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

/** Maven config object for one {@code <source>} block. */
public class SourceConfig {

    @Parameter(required = true) public File path;
    @Parameter public String type;          // optional; inferred from extension if absent
    @Parameter public MappingConfig mapping;
    @Parameter public Boolean allowEmpty;   // optional per-source override
}
