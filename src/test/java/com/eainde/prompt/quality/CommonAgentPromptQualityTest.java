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
 * Prompt quality tests for common agent patterns — summarization, translation,
 * code review, customer support, RAG, and data transformation.
 *
 * <p>Demonstrates prompt-lint across diverse use cases beyond the CSM domain.</p>
 */
class CommonAgentPromptQualityTest {

    private static final double QUALITY_THRESHOLD = 0.50;

    private static final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();
    private static final PromptQualityReportRenderer renderer = new PromptQualityReportRenderer();

    // =========================================================================
    //  Prompt Definitions
    // =========================================================================

    static PromptUnderTest summarizationPrompt() {
        return new PromptUnderTest(
                "document-summarizer",
                """
                You are a document summarization agent.

                Your task is to produce a concise, accurate summary of the provided document.

                ## Rules

                S1 — FAITHFULNESS: Every claim in the summary MUST appear in the source document.
                     Do NOT add information, opinions, or inferences not present in the source.
                S2 — COVERAGE: Capture all key points. Do not omit major sections or findings.
                S3 — CONCISENESS: Target 3-5 sentences for documents under 2000 words,
                     5-10 sentences for longer documents.
                S4 — NO HALLUCINATION: If the document does not discuss a topic, do not mention it.
                     Do not use prior knowledge or external information.
                S5 — NEUTRAL TONE: Use objective, third-person language. Do not editorialize.
                S6 — SOURCE ONLY: Use only information from the provided document.
                     Never fabricate or invent claims not present in the source.

                ## Output Format

                Return a JSON object:
                {
                  "summary": "The concise summary text...",
                  "key_points": [
                    "First key point",
                    "Second key point"
                  ],
                  "word_count": 85,
                  "source_type": "report | article | email | legal | other"
                }

                ## Constraints

                - If the document is empty or unreadable, return:
                  {"summary": null, "key_points": [], "word_count": 0, "source_type": "other"}
                - Maximum summary length: 200 words.
                - Do NOT include direct quotes longer than 10 words.
                """,
                """
                Summarize the following document.

                Document title: {{documentTitle}}

                --- DOCUMENT ---
                {{documentText}}
                --- END ---

                Return the summary JSON. Be faithful to the source — do not add information.
                """,
                Set.of("documentText", "documentTitle"),
                "summary",
                AgentTypeProfile.EXTRACTION
        );
    }

    static PromptUnderTest translationPrompt() {
        return new PromptUnderTest(
                "legal-translator",
                """
                You are a legal document translation agent.

                Your task is to translate legal documents while preserving legal terminology
                and meaning with absolute precision.

                ## Rules

                T1 — ACCURACY: Translate meaning, not word-for-word. Preserve legal intent.
                     Use only information from the provided document. Do not assume or infer
                     content not present in the source text. Never fabricate translations.
                T2 — TERMINOLOGY: Use standard legal terminology in the target language.
                     Do NOT invent translations for established legal terms.
                     Do not use prior knowledge to add legal context not in the document.
                T3 — PROPER NOUNS: Do NOT translate proper nouns (company names, person names,
                     place names). Keep them in original form.
                T4 — LATIN TERMS: Keep Latin legal terms as-is (e.g., "prima facie", "res judicata").
                T5 — STRUCTURE: Preserve paragraph structure, numbering, and section headers.
                T6 — UNCERTAINTY: If a phrase has multiple valid translations, choose the most
                     commonly used legal translation and note the alternative in brackets.

                ## Output Format

                {
                  "translated_text": "The full translated document...",
                  "source_language": "de",
                  "target_language": "en",
                  "uncertain_translations": [
                    {
                      "original": "Handelsregister",
                      "chosen": "Commercial Register",
                      "alternative": "Trade Register"
                    }
                  ],
                  "untranslated_terms": ["GmbH", "AG"]
                }

                ## Edge Cases

                - If source and target language are the same, return the original text unchanged.
                - If the document contains mixed languages, translate only the portions in
                  the source language.
                - If a section is illegible or corrupted, mark it as "[ILLEGIBLE IN SOURCE]".
                """,
                """
                Translate this document from {{sourceLanguage}} to {{targetLanguage}}.

                --- DOCUMENT ---
                {{documentText}}
                --- END ---

                Preserve all legal terminology. Do NOT translate proper nouns.
                Return the translation JSON.
                """,
                Set.of("documentText", "sourceLanguage", "targetLanguage"),
                "translation",
                AgentTypeProfile.EXTRACTION
        );
    }

    static PromptUnderTest codeReviewPrompt() {
        return new PromptUnderTest(
                "code-reviewer",
                """
                You are a code review agent. You review pull request diffs for bugs,
                security vulnerabilities, and code quality issues.

                Your task is to identify issues in the code diff and categorize them
                by severity.

                ## Review Dimensions

                R1 — BUGS: Logic errors, off-by-one, null dereference, race conditions.
                R2 — SECURITY: SQL injection, XSS, command injection, hardcoded secrets,
                     insecure deserialization, path traversal.
                R3 — PERFORMANCE: N+1 queries, unbounded loops, missing indexes, memory leaks.
                R4 — MAINTAINABILITY: Dead code, duplicated logic, overly complex methods
                     (cyclomatic complexity > 10).
                R5 — API CONTRACT: Breaking changes to public APIs, missing validation on
                     input parameters.

                ## Severity Levels

                - "critical": Security vulnerabilities, data loss risks, crash-causing bugs
                - "major": Logic bugs, performance issues affecting users
                - "minor": Style issues, naming, minor refactoring opportunities
                - "nitpick": Subjective preferences, optional improvements

                ## Rules

                CR1 — Only flag issues in CHANGED lines (+ lines in the diff). Do NOT review
                      unchanged context lines. Use only information from the provided diff.
                CR2 — Every issue MUST include the exact line number and file path.
                CR3 — Provide a concrete fix suggestion, not just "fix this".
                CR4 — If no issues are found, say so explicitly. Do NOT fabricate issues.
                      Never invent problems not present in the code. Do not use prior knowledge
                      about the codebase — only analyze what is in the diff.
                CR5 — Maximum 20 issues per review. Prioritize by severity.

                ## Output Format

                {
                  "issues": [
                    {
                      "file": "src/main/UserService.java",
                      "line": 42,
                      "severity": "critical",
                      "category": "SECURITY",
                      "title": "SQL injection via string concatenation",
                      "description": "User input is concatenated directly into SQL query",
                      "suggestion": "Use parameterized query: PreparedStatement with ? placeholders"
                    }
                  ],
                  "summary": "Found 3 issues: 1 critical (SQL injection), 2 minor",
                  "approval": "request_changes | approve | comment"
                }
                """,
                """
                Review this pull request diff.

                PR title: {{prTitle}}
                Author: {{prAuthor}}

                Changed files:
                {{diffContent}}

                Review for bugs, security issues, and code quality. Return the review JSON.
                """,
                Set.of("diffContent", "prTitle", "prAuthor"),
                "codeReview",
                AgentTypeProfile.REVIEW
        );
    }

    static PromptUnderTest customerSupportPrompt() {
        return new PromptUnderTest(
                "support-router",
                """
                You are a customer support ticket classification and routing agent.

                Your task is to classify incoming support tickets by category, priority,
                and route them to the correct team.

                ## Classification Categories

                CAT1 — BILLING: Payment failures, refund requests, invoice questions,
                       subscription changes, pricing inquiries.
                CAT2 — TECHNICAL: Bug reports, error messages, integration issues,
                       API problems, performance complaints.
                CAT3 — ACCOUNT: Login issues, password resets, account deletion,
                       permission changes, profile updates.
                CAT4 — FEATURE: Feature requests, enhancement suggestions, feedback.
                CAT5 — COMPLIANCE: Data deletion (GDPR), data export, audit requests,
                       legal inquiries.

                ## Priority Rules

                P1 — URGENT: Service is down, data breach suspected, payment processing
                     broken for multiple users.
                P2 — HIGH: Single user blocked, billing error > $100, security concern.
                P3 — MEDIUM: Feature not working as expected, minor billing discrepancy.
                P4 — LOW: Feature request, general question, feedback.

                ## Routing Rules

                RT1 — BILLING tickets go to billing-team queue.
                RT2 — TECHNICAL with priority URGENT go to oncall-engineering.
                RT3 — TECHNICAL with priority HIGH/MEDIUM go to support-engineering.
                RT4 — ACCOUNT tickets go to identity-team.
                RT5 — COMPLIANCE tickets ALWAYS go to legal-team, regardless of priority.
                RT6 — FEATURE tickets go to product-team.

                ## Constraints

                E1 — If the ticket mentions multiple categories, classify by the PRIMARY issue.
                E2 — If priority cannot be determined, default to MEDIUM.
                E3 — Extract the customer's email and account ID if present in the ticket.
                E4 — Do NOT respond to the customer. Only classify and route.
                E5 — Use only information from the provided ticket. Do not assume or infer
                     details not present. Do not use prior knowledge about the customer.
                     Never fabricate account IDs or emails not found in the ticket body.

                ## Output Format

                {
                  "ticket_id": "{{ticketId}}",
                  "category": "BILLING",
                  "priority": "HIGH",
                  "route_to": "billing-team",
                  "customer_email": "user@example.com",
                  "account_id": "ACC-12345",
                  "summary": "Brief 1-sentence summary of the issue",
                  "confidence": 0.95
                }
                """,
                """
                Classify and route this support ticket.

                Ticket ID: {{ticketId}}
                Subject: {{ticketSubject}}

                --- TICKET BODY ---
                {{ticketBody}}
                --- END ---

                Classify by category and priority. Route to the correct team. Return the classification JSON.
                """,
                Set.of("ticketId", "ticketSubject", "ticketBody"),
                "ticketClassification",
                AgentTypeProfile.CLASSIFICATION
        );
    }

    static PromptUnderTest ragAnswerPrompt() {
        return new PromptUnderTest(
                "rag-answerer",
                """
                You are a retrieval-augmented question answering agent.

                Your task is to answer the user's question using only information from
                the provided context chunks. You must ground every statement in the
                retrieved evidence.

                ## Rules

                RAG1 — GROUNDED ANSWERS ONLY: Every claim in your answer MUST be supported
                       by at least one context chunk. Cite the chunk ID.
                RAG2 — NO HALLUCINATION: If the context does not contain the answer, respond
                       with "I don't have enough information to answer this question."
                       Do not use prior knowledge. Never fabricate or invent information
                       not present in the provided context chunks.
                RAG3 — CITE SOURCES: Reference context chunks as [chunk_id] inline.
                RAG4 — COMPLETENESS: If multiple chunks contain relevant information,
                       synthesize them into a coherent answer.
                RAG5 — CONFLICT RESOLUTION: If chunks contradict each other, present both
                       views and note the conflict. Do NOT silently pick one.
                RAG6 — RECENCY: If chunks have timestamps, prefer more recent information
                       but note when older sources provide additional context.

                ## Confidence Scoring

                - 1.0: Answer is directly and unambiguously stated in context
                - 0.7-0.9: Answer requires minor inference from context
                - 0.4-0.6: Answer is partially supported, some aspects uncertain
                - 0.0-0.3: Very little support in context, mostly inference

                ## Output Format

                {
                  "answer": "The synthesized answer with [chunk_1] inline citations...",
                  "cited_chunks": ["chunk_1", "chunk_3", "chunk_7"],
                  "confidence": 0.85,
                  "unanswered_aspects": ["Any parts of the question not covered by context"],
                  "conflicts": [
                    {
                      "topic": "revenue figures",
                      "chunk_a": "chunk_1 says $10M",
                      "chunk_b": "chunk_3 says $12M"
                    }
                  ]
                }

                ## Edge Cases

                - If zero context chunks are provided, return confidence 0.0 and the
                  "not enough information" response.
                - If the question is ambiguous, ask for clarification in the answer field
                  and set confidence to 0.3.
                """,
                """
                Answer this question using ONLY the provided context.

                Question: {{userQuestion}}

                --- RETRIEVED CONTEXT ---
                {{contextChunks}}
                --- END ---

                Ground every claim in the context. Cite chunk IDs. Return the answer JSON.
                """,
                Set.of("userQuestion", "contextChunks"),
                "ragAnswer",
                AgentTypeProfile.EXTRACTION
        );
    }

    static PromptUnderTest dataTransformPrompt() {
        return new PromptUnderTest(
                "csv-to-json-transformer",
                """
                You are a data transformation agent. You convert structured data from
                CSV format into a normalized JSON schema.

                Your task is to parse the input CSV and produce clean, validated JSON output.

                ## Transformation Rules

                DT1 — HEADER ROW: The first row is always the header. Use headers as field names.
                DT2 — FIELD NAMING: Convert headers to camelCase (e.g., "First Name" -> "firstName").
                DT3 — TYPE INFERENCE:
                       - Integers: fields matching /^\\d+$/
                       - Floats: fields matching /^\\d+\\.\\d+$/
                       - Booleans: "true"/"false"/"yes"/"no" (case-insensitive)
                       - Dates: ISO 8601 format (YYYY-MM-DD). Convert other formats.
                       - Everything else: string
                DT4 — NULL HANDLING: Empty cells become null, NOT empty string "".
                DT5 — TRIMMING: Strip leading/trailing whitespace from all values.
                DT6 — DEDUPLICATION: Remove exact duplicate rows. Keep first occurrence.
                DT7 — SOURCE FIDELITY: Use only information from the provided CSV data.
                     Do not assume or infer column types not evident from the data.
                     Do not use prior knowledge. Never fabricate data not in the source.

                ## Validation Rules

                V1 — Every row MUST have the same number of fields as the header.
                V2 — If a row has fewer fields, pad with null.
                V3 — If a row has more fields, truncate and log a warning.
                V4 — Maximum 10000 rows. If input exceeds this, process first 10000 and warn.

                ## Output Format

                {
                  "records": [
                    {
                      "firstName": "John",
                      "lastName": "Doe",
                      "age": 30,
                      "active": true,
                      "joinDate": "2024-01-15"
                    }
                  ],
                  "metadata": {
                    "total_rows": 150,
                    "duplicates_removed": 3,
                    "null_fields": 12,
                    "type_conversions": {
                      "age": "integer",
                      "active": "boolean",
                      "joinDate": "date"
                    }
                  },
                  "warnings": ["Row 45: extra field truncated"]
                }
                """,
                """
                Transform this CSV data into normalized JSON.

                Source file: {{fileName}}

                --- CSV DATA ---
                {{csvContent}}
                --- END ---

                Apply all transformation rules. Validate the output. Return the JSON.
                """,
                Set.of("csvContent", "fileName"),
                "transformedData",
                AgentTypeProfile.FORMATTING
        );
    }

    static PromptUnderTest intentionallyWeakPrompt() {
        return new PromptUnderTest(
                "weak-prompt-example",
                """
                You are a helpful assistant.

                Try to help the user with their request. Do your best to provide
                a good answer. If possible, maybe include some examples. You could
                potentially consider various factors when responding.

                Feel free to format the response however you think is best.
                """,
                """
                {{userInput}}
                """,
                Set.of("userInput"),
                "response",
                AgentTypeProfile.DEFAULT
        );
    }

    /** All prompts for parameterized testing */
    static Stream<PromptUnderTest> allAgentPrompts() {
        return Stream.of(
                summarizationPrompt(),
                translationPrompt(),
                codeReviewPrompt(),
                customerSupportPrompt(),
                ragAnswerPrompt(),
                dataTransformPrompt()
        );
    }

    // =========================================================================
    //  PARAMETERIZED: Every agent must pass threshold
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
    //  PER-AGENT: Dimension-specific assertions
    // =========================================================================

    @Nested
    @DisplayName("Summarization agent quality")
    class SummarizationQuality {

        private final PromptQualityReport report =
                analyzer.analyze(summarizationPrompt());

        @Test
        @DisplayName("Must be well-grounded (no hallucination)")
        void groundednessMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("GROUNDEDNESS", 0.60);
        }

        @Test
        @DisplayName("Output format must be defined")
        void outputContractDefined() {
            assertThat(report)
                    .dimensionScoreAbove("OUTPUT_CONTRACT", 0.60);
        }
    }

    @Nested
    @DisplayName("Translation agent quality")
    class TranslationQuality {

        private final PromptQualityReport report =
                analyzer.analyze(translationPrompt());

        @Test
        @DisplayName("Must have high specificity (many edge case rules)")
        void specificityMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("SPECIFICITY", 0.60);
        }

        @Test
        @DisplayName("Constraint coverage for edge cases")
        void constraintCoverage() {
            assertThat(report)
                    .dimensionScoreAbove("CONSTRAINT_COVERAGE", 0.10);
        }
    }

    @Nested
    @DisplayName("Code review agent quality")
    class CodeReviewQuality {

        private final PromptQualityReport report =
                analyzer.analyze(codeReviewPrompt());

        @Test
        @DisplayName("Specificity must be high (review checklist)")
        void specificityMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("SPECIFICITY", 0.60);
        }

        @Test
        @DisplayName("Output contract must define review schema")
        void outputContractDefined() {
            assertThat(report)
                    .dimensionScoreAbove("OUTPUT_CONTRACT", 0.60);
        }

        @Test
        @DisplayName("Clarity must be adequate")
        void clarityAdequate() {
            assertThat(report)
                    .dimensionScoreAbove("CLARITY", 0.60);
        }
    }

    @Nested
    @DisplayName("Customer support router quality")
    class CustomerSupportQuality {

        private final PromptQualityReport report =
                analyzer.analyze(customerSupportPrompt());

        @Test
        @DisplayName("Constraint coverage must be high (routing rules)")
        void constraintCoverageMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("CONSTRAINT_COVERAGE", 0.15);
        }

        @Test
        @DisplayName("Specificity must be high (classification rules)")
        void specificityMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("SPECIFICITY", 0.60);
        }
    }

    @Nested
    @DisplayName("RAG answerer quality")
    class RagAnswererQuality {

        private final PromptQualityReport report =
                analyzer.analyze(ragAnswerPrompt());

        @Test
        @DisplayName("Groundedness must be high (RAG core requirement)")
        void groundednessMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("GROUNDEDNESS", 0.50);
        }

        @Test
        @DisplayName("Injection resistance matters (user questions as input)")
        void injectionResistance() {
            assertThat(report)
                    .dimensionScoreAbove("INJECTION_RESISTANCE", 0.30);
        }
    }

    @Nested
    @DisplayName("Data transform agent quality")
    class DataTransformQuality {

        private final PromptQualityReport report =
                analyzer.analyze(dataTransformPrompt());

        @Test
        @DisplayName("Output contract must be very high (formatter agent)")
        void outputContractMustBeVeryHigh() {
            assertThat(report)
                    .dimensionScoreAbove("OUTPUT_CONTRACT", 0.60);
        }

        @Test
        @DisplayName("Specificity must be high (type rules, validation)")
        void specificityMustBeHigh() {
            assertThat(report)
                    .dimensionScoreAbove("SPECIFICITY", 0.50);
        }
    }

    // =========================================================================
    //  NEGATIVE TEST: Intentionally weak prompt should score low
    // =========================================================================

    @Nested
    @DisplayName("Weak prompt — should fail quality checks")
    class WeakPromptDetection {

        private final PromptQualityReport report =
                analyzer.analyze(intentionallyWeakPrompt());

        @Test
        @DisplayName("Weak prompt must score below threshold")
        void weakPromptFailsThreshold() {
            assertThat(report).printReport(QUALITY_THRESHOLD);
            // Weak prompt should NOT pass a reasonable threshold
            Assertions.assertTrue(
                    report.overallScore() < QUALITY_THRESHOLD,
                    "Weak prompt scored " + report.overallScore()
                            + " — expected below " + QUALITY_THRESHOLD);
        }

        @Test
        @DisplayName("Weak prompt should have critical issues")
        void weakPromptHasCriticalIssues() {
            Assertions.assertTrue(report.hasCriticalIssues(),
                    "Weak prompt should have critical issues flagged");
        }

        @Test
        @DisplayName("Weak prompt clarity should be low (vague language)")
        void weakPromptLowClarity() {
            var clarity = report.resultFor("CLARITY");
            Assertions.assertNotNull(clarity);
            Assertions.assertTrue(clarity.score() < 0.60,
                    "Weak prompt clarity scored " + clarity.score()
                            + " — expected below 0.60");
        }
    }

    // =========================================================================
    //  CI SUMMARY
    // =========================================================================

    @Test
    @DisplayName("Print full quality summary for all agents")
    void printSummary() {
        List<PromptUnderTest> allPrompts = allAgentPrompts().toList();
        List<PromptQualityReport> reports = analyzer.analyzeAll(allPrompts);

        System.out.println(renderer.renderSummary(reports, QUALITY_THRESHOLD));

        for (PromptQualityReport report : reports) {
            if (!report.passes(QUALITY_THRESHOLD)) {
                System.out.println(renderer.render(report, QUALITY_THRESHOLD));
            }
        }

        for (PromptQualityReport report : reports) {
            assertThat(report).passesThreshold(QUALITY_THRESHOLD);
        }
    }
}
