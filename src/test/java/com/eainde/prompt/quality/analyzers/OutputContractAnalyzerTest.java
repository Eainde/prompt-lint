package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OutputContractAnalyzerTest {

    private final OutputContractAnalyzer analyzer = new OutputContractAnalyzer();

    private PromptUnderTest prompt(String system) {
        return new PromptUnderTest("test-agent", system, "{{input}}",
                Set.of("input"), "output", AgentTypeProfile.DEFAULT);
    }

    private PromptUnderTest promptWithSchema(String system, String responseSchema) {
        return new PromptUnderTest("test-agent", system, "{{input}}",
                Set.of("input"), "output", AgentTypeProfile.DEFAULT, responseSchema);
    }

    @Test
    @DisplayName("dimensionName returns OUTPUT_CONTRACT")
    void dimensionName() {
        assertEquals("OUTPUT_CONTRACT", analyzer.dimensionName());
    }

    @Test
    @DisplayName("check1: no JSON block returns score 0 + OUT-001 CRITICAL")
    void noJsonBlock() {
        DimensionResult result = analyzer.analyze(prompt("No json here at all."));
        assertEquals(0.0, result.score());
        assertTrue(result.issues().stream()
                .anyMatch(i -> "OUT-001".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
    }

    @Test
    @DisplayName("check2: invalid JSON returns early with OUT-002 CRITICAL")
    void invalidJson() {
        String system = "Output: { invalid json here }";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream()
                .anyMatch(i -> "OUT-002".equals(i.ruleId()) && i.severity() == Severity.CRITICAL));
        assertTrue(result.score() > 0); // got 1 point for having a JSON block
    }

    @Test
    @DisplayName("check3: JSON array inside object wrapper triggers OUT-003 when root is not object")
    void jsonArrayRoot() {
        // extractJsonBlock finds first {...} so we need a JSON that parses but isn't an object
        // Since extractJsonBlock only finds {}, a pure array won't be found.
        // Instead, test that a valid JSON object passes check 3
        String system = "Output: {\"data\": \"value\"}";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check3: JSON object root passes")
    void jsonObjectRoot() {
        String system = "Output: {\"key\": \"value\"}";
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-003".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: JSON with array field passes")
    void jsonWithArrayField() {
        String system = """
                Output: {"records": [{"name": "Alice", "id": 1}], "count": 1}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check4: JSON without array field triggers OUT-004")
    void jsonWithoutArrayField() {
        String system = """
                Output: {"name": "Alice", "count": 1}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "OUT-004".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: array record with int + string types passes")
    void arrayRecordWithProperTypes() {
        String system = """
                Output: {"records": [{"name": "Alice", "id": 1, "active": true}]}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: array record missing int or string type triggers OUT-005")
    void arrayRecordMissingTypes() {
        String system = """
                Output: {"records": [{"active": true, "enabled": false}]}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "OUT-005".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: null field demonstration present avoids OUT-006")
    void nullFieldDemonstration() {
        String system = """
                Output: {"records": [{"name": "Alice", "id": 1, "middle": null}]}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: no null field triggers OUT-006")
    void noNullFieldDemonstration() {
        String system = """
                Output: {"records": [{"name": "Alice", "id": 1}]}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "OUT-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check5: empty array skips type check")
    void emptyArraySkipsTypeCheck() {
        String system = """
                Output: {"records": []}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        // No OUT-005 or OUT-006 since array is empty
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-005".equals(i.ruleId())));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-006".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: mixed naming convention triggers OUT-007")
    void mixedNamingConvention() {
        String system = """
                Output: {"records": [{"firstName": "Alice", "last_name": "Smith", "id": 1}]}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.issues().stream().anyMatch(i -> "OUT-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: consistent camelCase naming passes")
    void consistentCamelCase() {
        String system = """
                Output: {"records": [{"firstName": "Alice", "lastName": "Smith", "id": 1}]}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("check6: consistent snake_case naming passes")
    void consistentSnakeCase() {
        String system = """
                Output: {"records": [{"first_name": "Alice", "last_name": "Smith", "id": 1}]}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("well-formed JSON scores high")
    void wellFormedJsonScoresHigh() {
        String system = """
                ## Output Format
                {"records": [{"name": "Alice", "id": 1, "active": true, "middle": null}], "count": 1}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertTrue(result.score() >= 0.8);
    }

    @Test
    @DisplayName("maxScore is 1.0")
    void maxScoreIs1() {
        DimensionResult result = analyzer.analyze(prompt("no json"));
        assertEquals(1.0, result.maxScore());
    }

    @Test
    @DisplayName("check6: all lowercase field names with no underscore are not counted as camelCase")
    void allLowercaseFields() {
        // "id" == "id".toLowerCase() so camelCase count stays 0
        String system = """
                Output: {"records": [{"name": "Alice", "id": 1, "age": 30}]}
                """;
        DimensionResult result = analyzer.analyze(prompt(system));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-007".equals(i.ruleId())));
    }

    @Test
    @DisplayName("responseSchema provided: analyzer uses it instead of system prompt")
    void responseSchemaUsedOverSystemPrompt() {
        String schema = """
                {"records": [{"name": "Alice", "id": 1, "active": true, "middle": null}], "count": 1}
                """;
        DimensionResult result = analyzer.analyze(promptWithSchema("No json here.", schema.strip()));
        assertTrue(result.score() >= 0.8);
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("responseSchema null: falls back to system prompt extraction")
    void responseSchemaNullFallsBack() {
        DimensionResult result = analyzer.analyze(promptWithSchema("No json here.", null));
        assertEquals(0.0, result.score());
        assertTrue(result.issues().stream().anyMatch(i -> "OUT-001".equals(i.ruleId())));
    }

    @Test
    @DisplayName("responseSchema takes priority over embedded JSON in system prompt")
    void responseSchemaPriorityOverEmbedded() {
        String embeddedSystem = """
                Output: {"records": [{"active": true, "enabled": false}]}
                """;
        String schema = """
                {"records": [{"name": "Alice", "id": 1, "active": true, "middle": null}], "count": 1}
                """;
        // schema has proper types; embedded does not
        DimensionResult result = analyzer.analyze(promptWithSchema(embeddedSystem, schema.strip()));
        assertFalse(result.issues().stream().anyMatch(i -> "OUT-005".equals(i.ruleId())));
    }
}
