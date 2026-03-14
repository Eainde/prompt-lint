package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Analyzes the output contract — is the expected output format unambiguous?
 */
public class OutputContractAnalyzer implements PromptDimensionAnalyzer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String dimensionName() {
        return "OUTPUT_CONTRACT";
    }

    @Override
    public DimensionResult analyze(PromptUnderTest prompt) {
        List<QualityIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double totalPoints = 0;
        double maxPoints = 6;

        String system = prompt.systemPrompt();

        // ── Check 1: Has JSON example ───────────────────────────────────
        String jsonBlock = extractJsonBlock(system);
        if (jsonBlock == null) {
            issues.add(QualityIssue.critical("OUTPUT_CONTRACT",
                    "No JSON example found in system prompt. The agent needs a "
                            + "concrete output example.", "OUT-001"));
            suggestions.add("Add a complete JSON example in the '## Output Format' section.");
            return new DimensionResult("OUTPUT_CONTRACT", 0.0, 1.0, issues, suggestions);
        }
        totalPoints += 1;

        // ── Check 2: JSON is valid ──────────────────────────────────────
        JsonNode root;
        try {
            root = objectMapper.readTree(jsonBlock);
            totalPoints += 1;
        } catch (Exception e) {
            issues.add(QualityIssue.critical("OUTPUT_CONTRACT",
                    "JSON example is not valid JSON: " + e.getMessage(), "OUT-002"));
            suggestions.add("Fix the JSON example — ensure no trailing commas, "
                    + "unclosed braces, or comments.");
            return new DimensionResult("OUTPUT_CONTRACT", totalPoints / maxPoints, 1.0,
                    issues, suggestions);
        }

        // ── Check 3: Is a JSON object (not array or primitive) ──────────
        if (root.isObject()) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("OUTPUT_CONTRACT",
                    "JSON example is a " + root.getNodeType() + ", not an object. "
                            + "Output should be a JSON object for consistency.", "OUT-003"));
        }

        // ── Check 4: Has an array field (records) ───────────────────────
        boolean hasArray = false;
        String arrayFieldName = null;
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isArray()) {
                hasArray = true;
                arrayFieldName = field.getKey();
                break;
            }
        }

        if (hasArray) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.info("OUTPUT_CONTRACT",
                    "JSON example has no array field. Most agents return an array "
                            + "of records.", "OUT-004"));
        }

        // ── Check 5: Array records have proper field types ──────────────
        if (hasArray && root.get(arrayFieldName).size() > 0) {
            JsonNode firstRecord = root.get(arrayFieldName).get(0);
            boolean hasIntField = false;
            boolean hasBoolField = false;
            boolean hasNullField = false;
            boolean hasStringField = false;

            Iterator<Map.Entry<String, JsonNode>> recordFields = firstRecord.fields();
            while (recordFields.hasNext()) {
                Map.Entry<String, JsonNode> rf = recordFields.next();
                JsonNode value = rf.getValue();
                if (value.isInt()) hasIntField = true;
                if (value.isBoolean()) hasBoolField = true;
                if (value.isNull()) hasNullField = true;
                if (value.isTextual()) hasStringField = true;
            }

            if (hasIntField && hasStringField) {
                totalPoints += 1;
            } else {
                issues.add(QualityIssue.info("OUTPUT_CONTRACT",
                        "JSON example record may have incorrect field types. "
                                + "Ensure id is integer (not string), isCsm is boolean "
                                + "(not string).", "OUT-005"));
                suggestions.add("Use proper JSON types: \"id\": 1 (not \"1\"), "
                        + "\"isCsm\": true (not \"true\").");
            }

            // Check for null demonstration
            if (!hasNullField) {
                issues.add(QualityIssue.info("OUTPUT_CONTRACT",
                        "JSON example doesn't demonstrate null values. Show nullable "
                                + "fields as null in the example.", "OUT-006"));
                suggestions.add("Show nullable fields as null in the example: "
                        + "\"middleName\": null");
            }
        }

        // ── Check 6: Naming convention consistency ──────────────────────
        if (hasArray && root.get(arrayFieldName).size() > 0) {
            JsonNode firstRecord = root.get(arrayFieldName).get(0);
            int camelCase = 0;
            int snakeCase = 0;

            Iterator<String> fieldNames = firstRecord.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                if (name.contains("_")) snakeCase++;
                else if (!name.equals(name.toLowerCase())) camelCase++;
            }

            if (camelCase > 0 && snakeCase > 0) {
                issues.add(QualityIssue.warning("OUTPUT_CONTRACT",
                        "Mixed naming conventions in JSON: " + camelCase + " camelCase "
                                + "and " + snakeCase + " snake_case fields. Pick one.",
                        "OUT-007"));
                suggestions.add("Use consistent naming: either all camelCase "
                        + "or all snake_case.");
            }
            totalPoints += (camelCase > 0 && snakeCase > 0) ? 0.5 : 1.0;
        }

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("OUTPUT_CONTRACT", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }

    private String extractJsonBlock(String text) {
        int braceDepth = 0;
        int startIdx = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (braceDepth == 0) startIdx = i;
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0 && startIdx >= 0) {
                    return text.substring(startIdx, i + 1);
                }
            }
        }
        return null;
    }
}
