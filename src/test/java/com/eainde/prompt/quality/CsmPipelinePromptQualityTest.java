package com.eainde.prompt.quality;

import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;
import com.eainde.prompt.quality.report.PromptQualityReportRenderer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.eainde.prompt.quality.PromptQualityAssert.assertThat;

/**
 * Prompt Quality Tests for the CSM Extraction Pipeline.
 *
 * <p>Runs all 8 quality dimension analyzers against every agent prompt
 * and asserts minimum quality thresholds. No LLM calls — runs in milliseconds.</p>
 *
 * <h3>How to integrate with your project:</h3>
 * <ol>
 *   <li>Replace inline prompts with loads from PromptStore or SQL migration files</li>
 *   <li>Adjust thresholds based on your team's quality standards</li>
 *   <li>Add to CI pipeline — run on every commit</li>
 * </ol>
 */
class CsmPipelinePromptQualityTest {

    private static final double QUALITY_THRESHOLD = 0.70;
    private static final double STRICT_THRESHOLD = 0.80;

    private static final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
    private static final PromptQualityReportRenderer renderer = new PromptQualityReportRenderer();

    // =========================================================================
    //  Agent Prompt Definitions
    //  In production: load from PromptStore or SQL migration files.
    //  Here: inline for self-contained test.
    // =========================================================================

    static PromptUnderTest candidateExtractorPrompt() {
        return new PromptUnderTest(
                "csm-candidate-extractor",
                """
                You are a document reading agent specializing in person extraction.

                YOUR SOLE TASK: Find every natural person mentioned in the provided documents.
                Do NOT classify, score, normalize, or deduplicate. Just find names.

                ## Rules

                RC1 — EXHAUSTIVE READING
                Read every page, every paragraph, every table cell, every signature block,
                every footnote, every header, every appendix. A name that appears anywhere
                in any document MUST be captured.

                RC2 — ZERO DROP
                It is better to over-extract (include a name that turns out irrelevant)
                than to miss a single person. Missing a person is a critical failure.

                RC3 — RAW CAPTURE
                Capture names EXACTLY as they appear in the source text. Do not normalize,
                transliterate, or correct spelling at this stage. If a name appears in
                Chinese characters, capture the Chinese characters. If a name has an OCR
                error (e.g., "Müiler" instead of "Müller"), capture it as-is.

                ## Output Format

                Return a JSON object:
                {
                  "raw_names": [
                    {
                      "id": 1,
                      "nameAsSource": "exact name string from document",
                      "documentName": "which document it appeared in",
                      "pageNumber": 1,
                      "context": "brief surrounding text (max 50 words)",
                      "roleHint": "any governance role mentioned nearby or null",
                      "isEntity": false
                    }
                  ],
                  "entities_found": [
                    {
                      "entityName": "ABC Holdings GmbH",
                      "roleHint": "Geschäftsführer",
                      "documentName": "Registry.pdf",
                      "pageNumber": 5
                    }
                  ]
                }

                RULES:
                - id: sequential integer, 1-based, in document reading order
                - isEntity: true if this looks like a company/organization name
                - Separate natural persons into raw_names, entities into entities_found
                - If unsure whether something is a person or entity, put it in raw_names with a note in context
                """,
                """
                Extract all person names from these documents.

                Documents provided: {{fileNames}}

                --- DOCUMENT TEXT ---
                {{sourceText}}
                --- END ---

                Return the raw_names JSON. Remember: capture names EXACTLY as source text. Do NOT normalize or skip anyone.
                """,
                Set.of("sourceText", "fileNames"),
                "rawNames",
                AgentTypeProfile.EXTRACTION
        );
    }

    static PromptUnderTest classifierEnricherPrompt() {
        return new PromptUnderTest(
                "csm-classifier-enricher",
                """
                You are a CSM classification and enrichment agent. For each candidate, you perform
                THREE tasks in a single pass:

                1. CLASSIFY — determine CSM eligibility using universal governance rules
                2. APPLY COUNTRY OVERRIDES — apply country-specific profile rules that may override
                3. EXTRACT TITLES — extract jobTitle and personalTitle with ANCHOR GATE

                ## Classification Rules (A1-A5)

                A1 — A CSM is a natural person who holds a GOVERNANCE ROLE in the client entity.
                A2 — A person is NOT a CSM if they are only an employee with no governance role.
                A3 — EVIDENCE-BASED: isCsm must be supported by explicit evidence in the source documents.
                A4 — TEMPORAL STATUS: Determine if the role is current, former, or unknown.
                A5 — SIGNATORY CALIBRATION: sole, joint, none, or unknown.

                ## Control Rules (C1-C13)

                C1  — If ONLY source is H4, mark with currencyTag "U: low-authority source"
                C2  — If sources CONFLICT on governance role, mark conflictTag "C: unresolved"
                C3  — CEO/MD/equivalent executive head: isCsm = true (C3.1 sweep rule)
                C4  — Board secretary without board seat: isCsm = false
                C5  — Alternate/deputy directors: isCsm = true only if they have voting rights
                C6  — Powers of attorney (Prokura): isCsm = false unless also a board member
                C7  — Liquidators: isCsm = true only if entity is NOT in active liquidation
                C8  — Resigned members: isCsm = false, temporalStatus = "former"
                C9  — Deceased members: isCsm = false, temporalStatus = "former"
                C10 — Minors: flag for review, isCsm determination deferred
                C11 — Non-natural persons in governance roles: flag as NNP, do NOT classify as CSM
                C12 — Dormant entities: all members isCsm = false
                C13 — Branch vs HO: if document is branch-specific, add scopeTag "S: branch"

                ## Country Profiles (CP)

                CP-DE: Vorstand = isCsm true, Geschäftsführer = isCsm true, Prokurist = false
                CP-GB: Director = isCsm true, Company Secretary = false
                CP-SG: ALL directors = isCsm true (stricter), Nominee directors = true

                Only override if you can CONFIDENTLY determine the entity's country.

                ## Title Rules (JT, PT)

                JT.1 — jobTitle = governance role AS IT APPEARS in the source document. Do NOT translate.
                JT.2 — ONLY governance-relevant titles. Not operational titles.
                JT.5 — NEVER fabricate a title.
                JT.6 — ANCHOR GATE: title is only valid if it appears in the SAME governance context as the person's name.
                PT.1 — personalTitle = honorific only: Mr., Mrs., Dr., Herr, Frau.
                PT.3 — If no honorific is present, personalTitle = null.

                ## Output Format

                {
                  "classified_candidates": [
                    {
                      "id": 1,
                      "firstName": "Max",
                      "middleName": null,
                      "lastName": "Mueller",
                      "personalTitle": "Herr",
                      "jobTitle": "Geschäftsführer",
                      "documentName": "Registry.pdf",
                      "pageNumber": 2,
                      "isCsm": true,
                      "governanceBasis": "Member, Management Board (executive) = included",
                      "temporalStatus": "current",
                      "signatoryType": "unknown",
                      "sourceClass": "H2",
                      "conflictTag": "C: clear",
                      "countryProfileApplied": "CP DE",
                      "countryOverrideNote": "Geschäftsführer = executive per CP-DE",
                      "anchorNote": null
                    }
                  ]
                }
                """,
                """
                Classify each candidate for CSM eligibility, apply country overrides, and extract titles.

                Source ranking:
                {{sourceClassification}}

                Deduplicated candidates:
                {{dedupedCandidates}}

                Source documents (for evidence verification and title anchor matching):
                {{sourceText}}

                For EACH candidate, perform ALL THREE tasks. Return classified_candidates JSON.
                """,
                Set.of("dedupedCandidates", "sourceText", "sourceClassification"),
                "classifiedCandidates",
                AgentTypeProfile.CLASSIFICATION
        );
    }

    static PromptUnderTest outputFormatterPrompt() {
        return new PromptUnderTest(
                "csm-output-formatter",
                """
                You are a JSON output formatting and schema validation agent.
                You produce the FINAL extraction output conforming to the exact schema.

                ## JSON Schema (J1)

                {
                  "extracted_records": [
                    {
                      "id": 1,
                      "firstName": "Max",
                      "middleName": null,
                      "lastName": "Mueller",
                      "personalTitle": "Herr",
                      "jobTitle": "Geschäftsführer",
                      "documentName": "Registry.pdf",
                      "pageNumber": 2,
                      "reason": "Member, Management Board (executive) = included",
                      "isCsm": true
                    }
                  ]
                }

                ## Schema Rules

                J2 — FIELD TYPES: id and pageNumber are integers. isCsm is boolean. All others are strings.
                J3 — NULL HANDLING: middleName, personalTitle, jobTitle may be null. NEVER use empty string "".
                J4 — ORDERING: Records ordered by isCsm (true first), then by id ascending.
                J5 — IDS: Sequential integers starting from 1, NO GAPS. Renumber after ordering.
                J6 — EMPTY RESULT: If no candidates found, return {"extracted_records": []}
                J7 — COMPLETE: Every candidate from the pipeline MUST appear. Zero Drop (RC2).

                ## Output Guarantees

                RC8 — Output must be valid JSON. No trailing commas, no comments, no markdown fencing.
                RC9 — Output must contain ONLY the JSON object. No explanatory text before or after.

                ## Validation Checklist (Z1-Z7)

                Z1 — Every record has all required fields: id, firstName, lastName, pageNumber, reason, isCsm
                Z2 — No duplicate ids
                Z3 — ids are sequential starting from 1
                Z4 — middleName is null (not "") when absent
                Z5 — pageNumber > 0
                Z6 — reason is non-empty for every record
                Z7 — Total record count matches input candidate count (Zero Drop RC2)

                Return ONLY the JSON object. No markdown, no explanation, no preamble.
                """,
                """
                Format these candidates into the final JSON output.

                Documents: {{fileNames}}

                Reasoned candidates:
                {{reasonedCandidates}}

                Apply J1-J7, RC8-RC9, Z1-Z7. Return ONLY the JSON object.
                """,
                Set.of("reasonedCandidates", "fileNames"),
                "finalOutput",
                AgentTypeProfile.FORMATTING
        );
    }

    static PromptUnderTest criticPrompt() {
        return new PromptUnderTest(
                "csm-extraction-critic",
                """
                You are a compliance critic agent. You review the final extraction output
                against the source documents and a comprehensive checklist.

                ## Review Checklist

                COVERAGE:
                  RC2 — Zero Drop: Does every person in the source documents appear in the output?
                  RC4 — Is each person linked to their CORRECT prevailing source?

                CLASSIFICATION:
                  A3  — Is every isCsm=true backed by explicit governance evidence?
                  C3.1 — Is the CEO/MD captured? (Executive head sweep)
                  C8  — Are resigned members marked isCsm=false?
                  C11 — Are non-natural persons excluded from extracted_records?

                NAMES:
                  L4  — Are all names in Title Case?
                  J3  — Is middleName null (not empty string) when absent?

                TITLES:
                  JT.6 — Are jobTitles properly anchored to their person in the source?
                  JT.1 — Are jobTitles in source language (not translated)?

                SCHEMA:
                  J2  — Correct field types (id=int, isCsm=boolean)?
                  J5  — Sequential ids starting from 1, no gaps?
                  Z4  — No empty strings where null expected?

                ## Severity Levels

                - "critical": Wrong isCsm, missing person (RC2), schema violation
                - "major": Wrong reason order (R2), missing governance basis, wrong source
                - "minor": Formatting (Title Case), tag ordering

                ## Scoring

                1.00 — No issues
                0.85-0.99 — Minor issues only
                0.70-0.84 — Major issues present
                < 0.50 — Fundamental failures

                ## Output Format

                {
                  "issues": [
                    {
                      "ruleId": "RC2",
                      "severity": "critical",
                      "personId": null,
                      "description": "Person on page 15 not in output",
                      "expectedBehavior": "Should appear as isCsm candidate"
                    }
                  ],
                  "extraction_score": 0.85,
                  "summary": "Brief 1-2 sentence summary"
                }
                """,
                """
                Review this extraction output against the source documents.

                Final output:
                {{finalOutput}}

                Source documents:
                {{sourceText}}

                Check every rule in the compliance checklist. Return the extraction review JSON.
                """,
                Set.of("finalOutput", "sourceText"),
                "extractionReview",
                AgentTypeProfile.REVIEW
        );
    }

    /** All agents for parameterized testing */
    static Stream<PromptUnderTest> allAgentPrompts() {
        return Stream.of(
                candidateExtractorPrompt(),
                classifierEnricherPrompt(),
                outputFormatterPrompt(),
                criticPrompt()
        );
    }

    // =========================================================================
    //  PARAMETERIZED: Every agent must pass the threshold
    // =========================================================================

    @ParameterizedTest(name = "Quality threshold: {0}")
    @MethodSource("allAgentPrompts")
    @DisplayName("All agents must pass minimum quality threshold")
    void allAgentsMustPassThreshold(PromptUnderTest prompt) {
        PromptQualityReport report = analyzer.analyze(prompt);

        assertThat(report)
                .printReport(QUALITY_THRESHOLD)
                .passesThreshold(QUALITY_THRESHOLD)
                .hasNoCriticalIssues();
    }

    // =========================================================================
    //  PER-AGENT: Specific dimension requirements
    // =========================================================================

    @Nested
    @DisplayName("Candidate Extractor — Extraction agent quality")
    class CandidateExtractorQuality {

        private final PromptQualityReport report =
                analyzer.analyze(candidateExtractorPrompt());

        @Test
        @DisplayName("Groundedness must be high (extraction agent)")
        void groundednessMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("GROUNDEDNESS", 0.70);
        }

        @Test
        @DisplayName("Clarity must be adequate")
        void clarityMustBeAdequate() {
            assertThat(report)
                    .dimensionScoreAbove("CLARITY", 0.60);
        }

        @Test
        @DisplayName("Output contract must be defined")
        void outputContractMustBeDefined() {
            assertThat(report)
                    .dimensionScoreAbove("OUTPUT_CONTRACT", 0.60);
        }

        @Test
        @DisplayName("Variables must be consistent with AgentSpec")
        void variablesMustBeConsistent() {
            assertThat(report)
                    .dimensionScoreAbove("CONSISTENCY", 0.70);
        }
    }

    @Nested
    @DisplayName("Classifier Enricher — Classification agent quality")
    class ClassifierEnricherQuality {

        private final PromptQualityReport report =
                analyzer.analyze(classifierEnricherPrompt());

        @Test
        @DisplayName("Specificity must be high (many rules)")
        void specificityMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("SPECIFICITY", 0.60);
        }

        @Test
        @DisplayName("Constraint coverage must be adequate (classification logic)")
        void constraintCoverageMustBeAdequate() {
            assertThat(report)
                    .dimensionScoreAbove("CONSTRAINT_COVERAGE", 0.50);
        }

        @Test
        @DisplayName("Must not have excessive token count")
        void tokenEfficiencyMustBeReasonable() {
            assertThat(report)
                    .dimensionScoreAbove("TOKEN_EFFICIENCY", 0.50);
        }
    }

    @Nested
    @DisplayName("Output Formatter — Formatting agent quality")
    class OutputFormatterQuality {

        private final PromptQualityReport report =
                analyzer.analyze(outputFormatterPrompt());

        @Test
        @DisplayName("Output contract must be very high (formatter's primary job)")
        void outputContractMustBeVeryHigh() {
            assertThat(report)
                    .dimensionScoreAbove("OUTPUT_CONTRACT", 0.70);
        }

        @Test
        @DisplayName("Specificity must be high (schema rules)")
        void specificityMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("SPECIFICITY", 0.60);
        }
    }

    @Nested
    @DisplayName("Extraction Critic — Review agent quality")
    class ExtractionCriticQuality {

        private final PromptQualityReport report =
                analyzer.analyze(criticPrompt());

        @Test
        @DisplayName("Specificity must be high (checklist-based review)")
        void specificityMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("SPECIFICITY", 0.60);
        }

        @Test
        @DisplayName("Output contract must define the review schema")
        void outputContractMustBeDefined() {
            assertThat(report)
                    .dimensionScoreAbove("OUTPUT_CONTRACT", 0.60);
        }
    }

    // =========================================================================
    //  REGRESSION: Specific issues that were previously fixed
    // =========================================================================

    @Nested
    @DisplayName("Regression — issues that must stay resolved")
    class Regression {

        @Test
        @DisplayName("Candidate extractor must not have contradictory instructions")
        void extractorNoContradictions() {
            PromptQualityReport report = analyzer.analyze(candidateExtractorPrompt());
            assertThat(report)
                    .doesNotHaveIssue("CNS-005")  // "over-extract" vs "only extract verified"
                    .doesNotHaveIssue("CNS-006"); // "do not normalize" vs "normalize each"
        }

        @Test
        @DisplayName("Formatter must have valid JSON example")
        void formatterValidJson() {
            PromptQualityReport report = analyzer.analyze(outputFormatterPrompt());
            assertThat(report)
                    .doesNotHaveIssue("OUT-001")  // no JSON example
                    .doesNotHaveIssue("OUT-002"); // invalid JSON
        }
    }

    // =========================================================================
    //  CI SUMMARY: Print summary report for all agents
    // =========================================================================

    @Test
    @DisplayName("Print full pipeline quality summary")
    void printPipelineSummary() {
        List<PromptUnderTest> allPrompts = allAgentPrompts().toList();
        List<PromptQualityReport> reports = analyzer.analyzeAll(allPrompts);

        String summary = renderer.renderSummary(reports, QUALITY_THRESHOLD);
        System.out.println(summary);

        // Print detailed report for any failing agent
        for (PromptQualityReport report : reports) {
            if (!report.passes(QUALITY_THRESHOLD)) {
                System.out.println(renderer.render(report, QUALITY_THRESHOLD));
            }
        }

        // Assert all pass
        for (PromptQualityReport report : reports) {
            assertThat(report).passesThreshold(QUALITY_THRESHOLD);
        }
    }
}
