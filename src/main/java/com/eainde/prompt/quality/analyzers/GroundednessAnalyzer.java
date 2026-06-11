package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.fix.*;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes prompt groundedness — does the prompt enforce grounding
 * in source data and prevent hallucination?
 *
 * <p>This is the most critical dimension for extraction agents.</p>
 */
public class GroundednessAnalyzer implements PromptDimensionAnalyzer, FixGenerator {

    private final Lexicon lexicon;

    public GroundednessAnalyzer() {
        this(Lexicon.defaults());
    }

    public GroundednessAnalyzer(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    @Override
    public String dimensionName() {
        return "GROUNDEDNESS";
    }

    @Override
    public DimensionResult analyze(PromptUnderTest prompt) {
        List<QualityIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double totalPoints = 0;
        double maxPoints = 6;

        String systemLower = prompt.systemPrompt().toLowerCase();
        String userLower = prompt.userPrompt().toLowerCase();
        String combinedLower = (prompt.systemPrompt() + "\n" + prompt.userPrompt()).toLowerCase();

        // ── Check 1: Grounding instruction ──────────────────────────────
        boolean hasGrounding = lexicon.keywords("groundedness.grounding-instructions").stream()
                .anyMatch(combinedLower::contains);
        if (hasGrounding) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.critical("GROUNDEDNESS",
                    "No grounding instruction found. The agent is not told to use "
                            + "ONLY information from the provided documents.", "GRD-001"));
            suggestions.add("Add: 'Use ONLY information from the provided documents. "
                    + "Do not use any external knowledge.'");
        }

        // ── Check 2: External knowledge prohibition ─────────────────────
        boolean prohibitsExternal = lexicon.keywords("groundedness.external-knowledge-prohibitions").stream()
                .anyMatch(combinedLower::contains);
        if (prohibitsExternal) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.critical("GROUNDEDNESS",
                    "No explicit prohibition of external knowledge. The LLM may "
                            + "use its training data to fill in gaps.", "GRD-002"));
            suggestions.add("Add: 'Do NOT use any of your prior knowledge or information "
                    + "from outside the given context.'");
        }

        // ── Check 3: Citation requirements ──────────────────────────────
        long citationCount = lexicon.keywords("groundedness.citation-requirements").stream()
                .filter(combinedLower::contains)
                .count();
        if (citationCount >= 2) {
            totalPoints += 1;
        } else if (citationCount >= 1) {
            totalPoints += 0.5;
            issues.add(QualityIssue.info("GROUNDEDNESS",
                    "Partial citation requirements. Ensure both documentName and "
                            + "pageNumber are required.", "GRD-003"));
        } else {
            issues.add(QualityIssue.warning("GROUNDEDNESS",
                    "No citation requirements found. Agents should be required to cite "
                            + "the source document and page number.", "GRD-003"));
            suggestions.add("Require documentName and pageNumber for every extracted record.");
        }

        // ── Check 4: Fabrication prohibition ────────────────────────────
        boolean prohibitsFabrication = lexicon.keywords("groundedness.fabrication-prohibitions").stream()
                .anyMatch(combinedLower::contains);
        if (prohibitsFabrication) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("GROUNDEDNESS",
                    "No explicit fabrication prohibition. Add 'NEVER fabricate' or "
                            + "'Do NOT invent' instruction.", "GRD-004"));
            suggestions.add("Add: 'NEVER fabricate or invent information not present "
                    + "in the source documents.'");
        }

        // ── Check 5: Document boundary markers (in user prompt) ─────────
        boolean hasMarkers = lexicon.keywords("groundedness.document-boundary-markers").stream()
                .anyMatch(userLower::contains);
        if (hasMarkers) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("GROUNDEDNESS",
                    "No document boundary markers in user prompt. Use markers like "
                            + "'--- DOCUMENT TEXT ---' to clearly delimit source content.",
                    "GRD-005"));
            suggestions.add("Wrap document content in clear markers: "
                    + "'--- DOCUMENT TEXT ---' ... '--- END ---'");
        }

        // ── Check 6: Source text variable present ───────────────────────
        boolean hasSourceTextVar = prompt.userPrompt().contains("{{sourceText}}")
                || prompt.userPrompt().contains("{{documentText}}")
                || prompt.userPrompt().contains("{{document}}");
        boolean readsSourceText = prompt.declaredInputs().contains("sourceText")
                || prompt.declaredInputs().contains("documentText");

        if (hasSourceTextVar && readsSourceText) {
            totalPoints += 1;
        } else if (readsSourceText && !hasSourceTextVar) {
            issues.add(QualityIssue.critical("GROUNDEDNESS",
                    "AgentSpec declares sourceText as input but no {{sourceText}} "
                            + "variable found in user prompt.", "GRD-006"));
        } else if (!readsSourceText) {
            // Agent doesn't read source text — groundedness is less relevant
            // (e.g., scoring engine only reads classifiedCandidates)
            totalPoints += 1; // Give full points — not applicable
        }

        // ── Check 7: Conflicting grounding scope (GRD-007) ───────────────
        if (hasGrounding) {
            boolean hasConflict = lexicon.keywords("groundedness.conflicting-grounding-phrases").stream()
                    .anyMatch(combinedLower::contains);
            if (hasConflict) {
                issues.add(QualityIssue.critical("GROUNDEDNESS",
                        "Conflicting grounding scope: prompt says to use only provided "
                                + "documents but also encourages using external knowledge.",
                        "GRD-007"));
            }
        }

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("GROUNDEDNESS", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }

    @Override
    public List<PromptFix> suggestFixes(PromptUnderTest prompt, DimensionResult result) {
        List<PromptFix> fixes = new ArrayList<>();
        for (QualityIssue issue : result.issues()) {
            switch (issue.ruleId()) {
                case "GRD-001" -> fixes.add(new PromptFix("GRD-001",
                        "Add grounding instruction",
                        FixType.INSERT, FixLocation.SYSTEM_PROMPT, null,
                        "Use ONLY information from the provided documents.\n",
                        FixConfidence.HIGH));
                case "GRD-002" -> fixes.add(new PromptFix("GRD-002",
                        "Add external knowledge prohibition",
                        FixType.INSERT, FixLocation.SYSTEM_PROMPT, null,
                        "Do NOT use any external knowledge or prior training data.\n",
                        FixConfidence.HIGH));
                case "GRD-004" -> fixes.add(new PromptFix("GRD-004",
                        "Add fabrication prohibition",
                        FixType.INSERT, FixLocation.SYSTEM_PROMPT, null,
                        "NEVER fabricate or invent information not present in the source.\n",
                        FixConfidence.MEDIUM));
            }
        }
        return fixes;
    }
}
