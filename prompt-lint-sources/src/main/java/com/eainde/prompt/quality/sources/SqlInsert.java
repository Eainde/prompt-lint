package com.eainde.prompt.quality.sources;

import java.util.List;

/** One parsed INSERT statement. A value of {@code null} in a row means SQL NULL. */
record SqlInsert(String table, List<String> columns, List<List<String>> rows) {}
