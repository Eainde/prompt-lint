package com.eainde.prompt.quality.sources;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written scanner extracting {@code INSERT INTO ... VALUES ...} statements.
 * Handles single-quoted strings with {@code ''} escapes (including newlines),
 * line ({@code --}) and block comments, quoted/bracketed
 * identifiers, schema-qualified table names, multi-row VALUES, NULL and numeric
 * literals. Non-INSERT statements and unparseable INSERTs are skipped (the latter
 * with a warning). INSERTs without a column list cannot be name-mapped and are
 * dropped with a warning.
 */
final class SqlInsertParser {

    private static final class ParseException extends RuntimeException {
        ParseException(String message) { super(message); }
    }

    private final String src;
    private final List<String> warnings;
    private int pos;

    SqlInsertParser(String src, List<String> warnings) {
        this.src = src;
        this.warnings = warnings;
    }

    List<SqlInsert> parseAll() {
        List<SqlInsert> result = new ArrayList<>();
        while (true) {
            skipTrivia();
            if (pos >= src.length()) {
                break;
            }
            int stmtStart = pos;
            if (lookingAtKeyword("INSERT")) {
                try {
                    SqlInsert ins = parseInsert();
                    if (ins != null) {
                        result.add(ins);
                    }
                } catch (ParseException e) {
                    warnings.add("Skipped unparseable INSERT near offset " + stmtStart
                            + ": " + e.getMessage());
                    skipToStatementEnd();
                }
            } else {
                skipToStatementEnd();
            }
        }
        return result;
    }

    private SqlInsert parseInsert() {
        expectKeyword("INSERT");
        expectKeyword("INTO");
        String table = parseIdentifier();
        skipTrivia();

        List<String> columns = new ArrayList<>();
        if (peek() == '(') {
            columns = parseIdentifierList();
            skipTrivia();
        }

        expectKeyword("VALUES");

        List<List<String>> rows = new ArrayList<>();
        rows.add(parseTuple());
        skipTrivia();
        while (peek() == ',') {
            pos++;
            rows.add(parseTuple());
            skipTrivia();
        }
        if (peek() == ';') {
            pos++;
        }

        if (columns.isEmpty()) {
            warnings.add("INSERT INTO " + table + " has no column list — cannot map by name; skipped");
            return null;
        }
        return new SqlInsert(table, columns, rows);
    }

    private List<String> parseIdentifierList() {
        expect('(');
        List<String> ids = new ArrayList<>();
        ids.add(parseIdentifier());
        skipTrivia();
        while (peek() == ',') {
            pos++;
            ids.add(parseIdentifier());
            skipTrivia();
        }
        expect(')');
        return ids;
    }

    private List<String> parseTuple() {
        skipTrivia();
        expect('(');
        List<String> values = new ArrayList<>();
        values.add(parseValue());
        skipTrivia();
        while (peek() == ',') {
            pos++;
            values.add(parseValue());
            skipTrivia();
        }
        expect(')');
        return values;
    }

    private String parseValue() {
        skipTrivia();
        if (peek() == '\'') {
            return parseStringLiteral();
        }
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ',' || c == ')') {
                break;
            }
            // Stop at a comment start; the trailing comment is consumed by the
            // skipTrivia() that parseTuple runs before the next delimiter.
            if (c == '-' && pos + 1 < src.length() && src.charAt(pos + 1) == '-') {
                break;
            }
            if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
                break;
            }
            pos++;
        }
        String raw = src.substring(start, pos).trim();
        if (raw.equalsIgnoreCase("NULL")) {
            return null;
        }
        return raw;
    }

    private String parseStringLiteral() {
        expect('\'');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '\'') {
                if (pos < src.length() && src.charAt(pos) == '\'') {
                    sb.append('\'');
                    pos++;
                } else {
                    return sb.toString();
                }
            } else {
                sb.append(c);
            }
        }
        throw new ParseException("unterminated string literal");
    }

    private String parseIdentifier() {
        skipTrivia();
        char c = peek();
        if (c == '"') {
            return parseDelimitedIdentifier('"', '"');
        }
        if (c == '`') {
            return parseDelimitedIdentifier('`', '`');
        }
        if (c == '[') {
            return parseDelimitedIdentifier('[', ']');
        }
        int start = pos;
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '$') {
                pos++;
            } else {
                break;
            }
        }
        if (pos == start) {
            throw new ParseException("expected identifier at offset " + pos);
        }
        String id = src.substring(start, pos);
        int dot = id.lastIndexOf('.');
        return dot >= 0 ? id.substring(dot + 1) : id;
    }

    private String parseDelimitedIdentifier(char open, char close) {
        expect(open);
        int start = pos;
        while (pos < src.length() && src.charAt(pos) != close) {
            pos++;
        }
        if (pos >= src.length()) {
            throw new ParseException("unterminated quoted identifier");
        }
        String id = src.substring(start, pos);
        pos++;
        return id;
    }

    // ---- low-level helpers ----

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private void expect(char c) {
        skipTrivia();
        if (peek() != c) {
            throw new ParseException("expected '" + c + "' at offset " + pos);
        }
        pos++;
    }

    private boolean lookingAtKeyword(String kw) {
        skipTrivia();
        if (pos + kw.length() > src.length()) {
            return false;
        }
        if (!src.regionMatches(true, pos, kw, 0, kw.length())) {
            return false;
        }
        int after = pos + kw.length();
        if (after < src.length()) {
            char c = src.charAt(after);
            return !(Character.isLetterOrDigit(c) || c == '_');
        }
        return true;
    }

    private void expectKeyword(String kw) {
        if (!lookingAtKeyword(kw)) {
            throw new ParseException("expected keyword '" + kw + "' at offset " + pos);
        }
        pos += kw.length();
    }

    private void skipToStatementEnd() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\'') {
                parseStringLiteral();
                continue;
            }
            if (c == '-' && pos + 1 < src.length() && src.charAt(pos + 1) == '-') {
                skipLineComment();
                continue;
            }
            if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
                skipBlockComment();
                continue;
            }
            pos++;
            if (c == ';') {
                return;
            }
        }
    }

    private void skipTrivia() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (c == '-' && pos + 1 < src.length() && src.charAt(pos + 1) == '-') {
                skipLineComment();
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
                skipBlockComment();
            } else {
                break;
            }
        }
    }

    private void skipLineComment() {
        while (pos < src.length() && src.charAt(pos) != '\n') {
            pos++;
        }
    }

    private void skipBlockComment() {
        pos += 2;
        while (pos + 1 < src.length() && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
            pos++;
        }
        pos = Math.min(pos + 2, src.length());
    }
}
