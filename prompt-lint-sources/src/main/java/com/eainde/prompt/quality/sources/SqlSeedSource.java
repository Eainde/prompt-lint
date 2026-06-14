package com.eainde.prompt.quality.sources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Loads prompts from SQL INSERT seed scripts. */
public class SqlSeedSource implements PromptSource {

    @Override
    public LoadResult load(Path path, SourceMapping mapping) {
        String sql;
        try {
            sql = Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read SQL source " + path, e);
        }

        List<String> warnings = new ArrayList<>();
        List<SqlInsert> inserts = new SqlInsertParser(sql, warnings).parseAll();
        List<NamedPrompt> prompts = new ArrayList<>();

        int rowIndex = 0;
        for (SqlInsert insert : inserts) {
            if (mapping.table() != null && !mapping.table().equalsIgnoreCase(insert.table())) {
                continue;
            }
            for (List<String> row : insert.rows()) {
                Map<String, String> record = toRecord(insert.columns(), row, path, warnings);
                if (record == null) {
                    continue;
                }
                NamedPrompt np = RecordMapper.toNamedPrompt(
                        record::get, mapping, "row-" + rowIndex, warnings);
                if (np != null) {
                    prompts.add(np);
                }
                rowIndex++;
            }
        }
        return new LoadResult(prompts, warnings);
    }

    private static Map<String, String> toRecord(List<String> columns, List<String> row,
                                                Path path, List<String> warnings) {
        if (row.size() != columns.size()) {
            warnings.add("Skipped row in " + path + ": " + row.size()
                    + " values for " + columns.size() + " columns");
            return null;
        }
        Map<String, String> record = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < columns.size(); i++) {
            record.put(columns.get(i), row.get(i));
        }
        return record;
    }
}
