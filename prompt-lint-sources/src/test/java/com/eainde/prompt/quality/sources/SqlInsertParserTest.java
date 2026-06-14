package com.eainde.prompt.quality.sources;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlInsertParserTest {

    private List<SqlInsert> parse(String sql, List<String> warnings) {
        return new SqlInsertParser(sql, warnings).parseAll();
    }

    @Test
    void parsesSingleRowWithColumns() {
        var warnings = new ArrayList<String>();
        List<SqlInsert> inserts = parse(
                "INSERT INTO prompts (id, system_text) VALUES ('a', 'Hello world');", warnings);

        assertThat(inserts).hasSize(1);
        SqlInsert ins = inserts.get(0);
        assertThat(ins.table()).isEqualTo("prompts");
        assertThat(ins.columns()).containsExactly("id", "system_text");
        assertThat(ins.rows()).hasSize(1);
        assertThat(ins.rows().get(0)).containsExactly("a", "Hello world");
        assertThat(warnings).isEmpty();
    }

    @Test
    void handlesEscapedQuotesMultiRowAndComments() {
        var warnings = new ArrayList<String>();
        String sql = """
                -- seed prompts
                INSERT INTO prompts (id, txt) VALUES
                  ('a', 'It''s fine'),
                  ('b', 'Line1
                Line2');
                """;
        List<SqlInsert> inserts = parse(sql, warnings);

        assertThat(inserts).hasSize(1);
        assertThat(inserts.get(0).rows()).hasSize(2);
        assertThat(inserts.get(0).rows().get(0)).containsExactly("a", "It's fine");
        assertThat(inserts.get(0).rows().get(1)).containsExactly("b", "Line1\nLine2");
    }

    @Test
    void stripsSchemaQualifierAndParsesNullAndNumbers() {
        var warnings = new ArrayList<String>();
        List<SqlInsert> inserts = parse(
                "INSERT INTO public.prompts (id, n, opt) VALUES ('a', 42, NULL);", warnings);

        assertThat(inserts.get(0).table()).isEqualTo("prompts");
        assertThat(inserts.get(0).rows().get(0).get(1)).isEqualTo("42");
        assertThat(inserts.get(0).rows().get(0).get(2)).isNull();
    }

    @Test
    void skipsNonInsertStatementsAndColumnlessInserts() {
        var warnings = new ArrayList<String>();
        String sql = "CREATE TABLE x (id INT); "
                + "INSERT INTO x VALUES ('no', 'cols'); "
                + "INSERT INTO prompts (id, s) VALUES ('a', 'ok');";
        List<SqlInsert> inserts = parse(sql, warnings);

        assertThat(inserts).hasSize(1);
        assertThat(inserts.get(0).table()).isEqualTo("prompts");
        assertThat(warnings).anySatisfy(w -> assertThat(w).contains("no column list"));
    }

    @Test
    void stripsCommentsAdjacentToUnquotedValues() {
        var warnings = new ArrayList<String>();
        List<SqlInsert> inserts = parse(
                "INSERT INTO prompts (id, n, m) VALUES "
                        + "('a', 42 /* inline */, 7 -- trailing\n);", warnings);

        assertThat(inserts).hasSize(1);
        assertThat(inserts.get(0).rows().get(0).get(1)).isEqualTo("42");
        assertThat(inserts.get(0).rows().get(0).get(2)).isEqualTo("7");
        assertThat(warnings).isEmpty();
    }
}
