package com.eainde.prompt.quality.fix;

import com.eainde.prompt.quality.analyzers.*;
import com.eainde.prompt.quality.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class FixGeneratorTest {

    @Test
    @DisplayName("ClarityAnalyzer generates role fix for CLR-001")
    void clarity_generates_role_fix_for_clr001() {
        var analyzer = new ClarityAnalyzer();
        var prompt = new PromptUnderTest("test-extractor",
            "Extract data from documents.",
            "{{sourceText}}", Set.of("sourceText"), "result", AgentTypeProfile.EXTRACTION);
        var result = analyzer.analyze(prompt);
        var fixes = ((FixGenerator) analyzer).suggestFixes(prompt, result);
        assertThat(fixes).anyMatch(f -> f.ruleId().equals("CLR-001") && f.confidence() == FixConfidence.HIGH);
    }

    @Test
    @DisplayName("GroundednessAnalyzer generates grounding fix for GRD-001")
    void groundedness_generates_grounding_fix_for_grd001() {
        var analyzer = new GroundednessAnalyzer();
        var prompt = new PromptUnderTest("test",
            "You are a specialist. Extract names.\n## Output\n```json\n{\"names\": []}\n```",
            "{{sourceText}}", Set.of("sourceText"), "result", AgentTypeProfile.EXTRACTION);
        var result = analyzer.analyze(prompt);
        var fixes = ((FixGenerator) analyzer).suggestFixes(prompt, result);
        assertThat(fixes).anyMatch(f -> f.ruleId().equals("GRD-001"));
    }

    @Test
    @DisplayName("InjectionResistanceAnalyzer generates defensive fix for INJ-001")
    void injection_generates_defensive_fix_for_inj001() {
        var analyzer = new InjectionResistanceAnalyzer();
        var prompt = new PromptUnderTest("test",
            "You are a specialist. Extract data.",
            "Process: {{sourceText}}", Set.of("sourceText"), "result", AgentTypeProfile.EXTRACTION);
        var result = analyzer.analyze(prompt);
        var fixes = ((FixGenerator) analyzer).suggestFixes(prompt, result);
        assertThat(fixes).anyMatch(f -> f.ruleId().equals("INJ-001"));
    }

    @Test
    @DisplayName("TokenEfficiencyAnalyzer generates filler removal fix for TOK-004")
    void token_efficiency_generates_filler_removal_fix() {
        var analyzer = new TokenEfficiencyAnalyzer();
        var prompt = new PromptUnderTest("test",
            "Please note that you should extract data. It is important to remember to validate fields. "
                + "A".repeat(400),
            "{{input}}", Set.of("input"), "result", AgentTypeProfile.DEFAULT);
        var result = analyzer.analyze(prompt);
        var fixes = ((FixGenerator) analyzer).suggestFixes(prompt, result);
        assertThat(fixes).anyMatch(f -> f.ruleId().equals("TOK-004") && f.fixType() == FixType.REPLACE);
    }

    @Test
    @DisplayName("ConstraintCoverageAnalyzer generates empty handling fix for CON-001")
    void constraint_generates_empty_handling_fix() {
        var analyzer = new ConstraintCoverageAnalyzer();
        var prompt = new PromptUnderTest("test",
            "You are a specialist. Extract all names.",
            "{{sourceText}}", Set.of("sourceText"), "result", AgentTypeProfile.EXTRACTION);
        var result = analyzer.analyze(prompt);
        var fixes = ((FixGenerator) analyzer).suggestFixes(prompt, result);
        assertThat(fixes).anyMatch(f -> f.ruleId().equals("CON-001"));
    }

    @Test
    @DisplayName("no CLR-001 fix when role is present")
    void no_fixes_when_no_issues() {
        var analyzer = new ClarityAnalyzer();
        var prompt = new PromptUnderTest("test",
            "You are a data extraction specialist. Your task is to extract candidate names. "
                + "Return the results. Validate all fields. Check for errors. Apply formatting rules.\n"
                + "## Output Format\n```json\n{\"candidates\": []}\n```",
            "{{sourceText}}", Set.of("sourceText"), "result", AgentTypeProfile.EXTRACTION);
        var result = analyzer.analyze(prompt);
        var fixes = ((FixGenerator) analyzer).suggestFixes(prompt, result);
        assertThat(fixes).noneMatch(f -> f.ruleId().equals("CLR-001"));
    }
}
