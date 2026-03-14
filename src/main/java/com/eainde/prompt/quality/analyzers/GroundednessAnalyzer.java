package com.eainde.prompt.quality.analyzers;

import com.db.clm.kyc.ai.testing.quality.model.*;
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
public class GroundednessAnalyzer implements PromptDimensionAnalyzer {

    private static final List<String> GROUNDING_INSTRUCTIONS = List.of(
            "only from the document", "only the information contained",
            "only from the provided", "only information from",
            "must appear in", "must be found in", "verbatim",
            "exactly as they appear", "exactly as written",
            "from the source text"
    );

    private static final List<String> EXTERNAL_KNOWLEDGE_PROHIBITIONS = List.of(
            "do not use prior knowledge", "do not use external",
            "do not infer", "do not assume", "do not guess",
            "no external knowledge", "no prior knowledge",
            "do not use your training", "do not use any knowledge"
    );

    private static final List<String> CITATION_REQUIREMENTS = List.of(
            "documentname", "document name", "pagename", "page number",
            "pagenumber", "source document", "cite", "citation",
            "reference the source"
    );

    private static final List<String> FABRICATION_PROHIBITIONS = List.of(
            "never fabricate", "do not fabricate", "never invent",
            "do not invent", "never hallucinate", "never make up",
            "do not make up", "never generate names"
    );

    private static final List<String> DOCUMENT_BOUNDARY_MARKERS = List.of(
            "document_start", "document_end", "document text",
            "--- document", "--- end", "<<<document", ">>>",
            "begin document", "end document"
    );

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
        boolean hasGrounding = GROUNDING_INSTRUCTIONS.stream()
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
        boolean prohibitsExternal = EXTERNAL_KNOWLEDGE_PROHIBITIONS.stream()
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
        long citationCount = CITATION_REQUIREMENTS.stream()
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
        boolean prohibitsFabrication = FABRICATION_PROHIBITIONS.stream()
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
        boolean hasMarkers = DOCUMENT_BOUNDARY_MARKERS.stream()
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

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("GROUNDEDNESS", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }
}
