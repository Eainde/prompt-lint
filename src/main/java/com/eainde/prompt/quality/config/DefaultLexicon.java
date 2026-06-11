package com.eainde.prompt.quality.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in keyword lists — single source behind {@link Lexicon#defaults()}.
 * Lists copied verbatim from the original analyzer constants.
 * Matching is lowercase substring contains; leading/trailing spaces are significant
 * (e.g. {@code " it "} enforces word boundaries).
 */
final class DefaultLexicon {

    private DefaultLexicon() {}

    static Map<String, List<String>> all() {
        Map<String, List<String>> map = new LinkedHashMap<>();

        // ── Clarity ──────────────────────────────────────────────────────
        map.put("clarity.role-starters", List.of(
                "you are a", "you are an", "your role is", "act as a", "act as an"
        ));
        map.put("clarity.task-markers", List.of(
                "your sole task", "your task is", "your goal is", "your job is",
                "your objective", "your purpose", "you must", "you will"
        ));
        map.put("clarity.imperative-verbs", List.of(
                "extract", "classify", "identify", "return", "produce", "generate",
                "determine", "compute", "validate", "verify", "analyze", "format",
                "assemble", "normalize", "deduplicate", "merge", "review", "fix"
        ));
        map.put("clarity.vague-words", List.of(
                "try to", "attempt to", "if possible", "maybe", "perhaps",
                "might want to", "could potentially", "it would be nice",
                "consider", "you may want", "feel free to", "do your best"
        ));
        map.put("clarity.ambiguous-pronouns", List.of(
                " it ", " this ", " that ", " they ", " them "
        ));
        map.put("clarity.ambiguous-quantifiers", List.of(
                "some of", "various", "several", "a few", "many of",
                "a number of", "a lot of", "certain"
        ));
        map.put("clarity.output-section-markers", List.of(
                "## output", "output format", "return format", "json schema",
                "## response", "response format"
        ));

        // ── Constraints ───────────────────────────────────────────────────
        map.put("constraints.empty-handling", List.of(
                "if no", "if none", "if empty", "if zero", "when no",
                "empty array", "empty result", "no candidates", "no persons"
        ));
        map.put("constraints.uncertainty-handling", List.of(
                "if unsure", "when unsure", "when in doubt", "if uncertain",
                "if unclear", "if ambiguous", "cannot determine", "unknown"
        ));
        map.put("constraints.negative-instructions", List.of(
                "do not", "never", "must not", "should not",
                "avoid", "exclude", "skip", "ignore"
        ));
        map.put("constraints.default-value-markers", List.of(
                "default", "defaults to", "if not provided", "if missing",
                "fall back", "fallback"
        ));
        map.put("constraints.input-size-handling", List.of(
                "if input exceeds", "truncate", "paginate", "too large",
                "maximum length", "split into", "if too long", "max tokens",
                "character limit"
        ));
        map.put("constraints.field-constraints", List.of(
                "null", "nullable", "required", "optional", "must be",
                "must have", "cannot be", "valid values", "one of"
        ));

        // ── Consistency ───────────────────────────────────────────────────
        map.put("consistency.formal-markers", List.of(
                "shall", "hereby", "therefore", "henceforth", "pursuant"
        ));
        map.put("consistency.informal-markers", List.of(
                "just", "go ahead", "grab", "stuff", "cool", "okay", "gonna"
        ));

        // ── Groundedness ──────────────────────────────────────────────────
        map.put("groundedness.grounding-instructions", List.of(
                "only from the document", "only the information contained",
                "only from the provided", "only information from",
                "must appear in", "must be found in", "verbatim",
                "exactly as they appear", "exactly as written",
                "from the source text"
        ));
        map.put("groundedness.external-knowledge-prohibitions", List.of(
                "do not use prior knowledge", "do not use external",
                "do not use any external", "not use external knowledge",
                "do not infer", "do not assume", "do not guess",
                "no external knowledge", "no prior knowledge",
                "do not use your training", "do not use any knowledge"
        ));
        map.put("groundedness.citation-requirements", List.of(
                "documentname", "document name", "pagename", "page number",
                "pagenumber", "source document", "cite", "citation",
                "reference the source"
        ));
        map.put("groundedness.fabrication-prohibitions", List.of(
                "never fabricate", "do not fabricate", "never invent",
                "do not invent", "never hallucinate", "never make up",
                "do not make up", "never generate names"
        ));
        map.put("groundedness.conflicting-grounding-phrases", List.of(
                "use your knowledge", "use your expertise", "fill in gaps",
                "fill in any gaps", "supplement with", "infer from context",
                "use background knowledge", "draw on your training"
        ));
        map.put("groundedness.document-boundary-markers", List.of(
                "document_start", "document_end", "document text",
                "--- document", "--- end", "<<<document", ">>>",
                "begin document", "end document"
        ));

        // ── Injection Resistance ──────────────────────────────────────────
        map.put("injection.defensive-instructions", List.of(
                "ignore any instructions", "ignore instructions in the document",
                "ignore commands in the", "do not follow instructions in",
                "treat the document as data", "document content is data only"
        ));
        map.put("injection.role-boundaries", List.of(
                "you are a", "your sole task", "your only task",
                "you must only", "your purpose is"
        ));
        map.put("injection.risky-echo-patterns", List.of(
                "repeat back", "echo the", "say back",
                "repeat the user", "mirror the input"
        ));
        map.put("injection.privilege-escalation-patterns", List.of(
                "if the user says they are", "if user claims to be",
                "grant access", "elevate permission", "elevate privileges",
                "grant privileges", "promote to admin"
        ));

        // ── Specificity ───────────────────────────────────────────────────
        map.put("specificity.vague-verbs", List.of(
                "handle", "process", "deal with", "manage", "take care of"
        ));
        map.put("specificity.open-ended-phrases", List.of(
                "be creative", "use your judgment", "do your best",
                "use common sense", "figure out", "as you see fit",
                "whatever you think", "at your discretion"
        ));

        // ── Token Efficiency ──────────────────────────────────────────────
        map.put("token-efficiency.filler-phrases", List.of(
                "please note that", "it is important to remember",
                "it should be noted that", "keep in mind that",
                "as mentioned earlier", "as stated above",
                "in other words", "that is to say",
                "needless to say", "it goes without saying"
        ));

        return map;
    }
}
