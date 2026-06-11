package com.eainde.prompt.quality.analyzers;

import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.fix.*;
import com.eainde.prompt.quality.model.DimensionResult;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.model.QualityIssue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Analyzes prompt clarity — does the prompt clearly communicate
 * what the agent should do?
 *
 * <h3>Checks:</h3>
 * <ul>
 *   <li>Has explicit role definition ("You are a...")</li>
 *   <li>Has clearly stated task (imperative task statement)</li>
 *   <li>Uses imperative verbs, not vague language</li>
 *   <li>Avoids ambiguous words ("some", "might", "could")</li>
 *   <li>Has defined output format section</li>
 *   <li>Task appears before rules (not buried)</li>
 * </ul>
 */
public class ClarityAnalyzer implements PromptDimensionAnalyzer, FixGenerator {

    private final Lexicon lexicon;

    public ClarityAnalyzer() {
        this(Lexicon.defaults());
    }

    public ClarityAnalyzer(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    @Override
    public String dimensionName() {
        return "CLARITY";
    }

    @Override
    public DimensionResult analyze(PromptUnderTest prompt) {
        List<QualityIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        double totalPoints = 0;
        double maxPoints = 6;

        String system = prompt.systemPrompt();
        String systemLower = system.toLowerCase();

        // ── Check 1: Role definition ────────────────────────────────────
        boolean hasRole = lexicon.keywords("clarity.role-starters").stream().anyMatch(systemLower::contains);
        if (hasRole) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("CLARITY",
                    "System prompt does not start with a clear role definition "
                            + "(e.g., 'You are a...')", "CLR-001"));
            suggestions.add("Start system prompt with: 'You are a [role] agent. "
                    + "Your task is to [specific task].'");
        }

        // ── Check 2: Task statement ─────────────────────────────────────
        boolean hasTask = lexicon.keywords("clarity.task-markers").stream().anyMatch(systemLower::contains);
        if (hasTask) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("CLARITY",
                    "No explicit task statement found (e.g., 'YOUR SOLE TASK:', "
                            + "'Your goal is:')", "CLR-002"));
            suggestions.add("Add an explicit task statement near the top of the system prompt.");
        }

        // ── Check 3: Imperative verbs ───────────────────────────────────
        long imperativeCount = lexicon.keywords("clarity.imperative-verbs").stream()
                .filter(systemLower::contains)
                .count();
        if (imperativeCount >= 3) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.info("CLARITY",
                    "Only " + imperativeCount + " imperative verbs found. "
                            + "Use direct commands: Extract, Classify, Return, etc.",
                    "CLR-003"));
        }

        // ── Check 4: Vague language ─────────────────────────────────────
        List<String> foundVague = lexicon.keywords("clarity.vague-words").stream()
                .filter(systemLower::contains)
                .toList();
        if (foundVague.isEmpty()) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.warning("CLARITY",
                    "Vague language detected: " + foundVague
                            + ". Replace with direct instructions.", "CLR-004"));
            suggestions.add("Replace vague phrases with imperative: "
                    + "'try to extract' → 'Extract', 'if possible' → remove entirely.");
        }

        // ── Check 5: Output format section ──────────────────────────────
        boolean hasResponseSchema = prompt.responseSchema() != null
                && !prompt.responseSchema().isBlank();
        boolean hasOutputSection = hasResponseSchema
                || lexicon.keywords("clarity.output-section-markers").stream().anyMatch(systemLower::contains);
        if (hasOutputSection) {
            totalPoints += 1;
        } else {
            issues.add(QualityIssue.critical("CLARITY",
                    "No output format section found. The agent needs explicit output "
                            + "format instructions.", "CLR-005"));
            suggestions.add("Add '## Output Format' section with a JSON example.");
        }

        // ── Check 6: Task before rules ──────────────────────────────────
        int taskPosition = -1;
        for (String marker : lexicon.keywords("clarity.task-markers")) {
            int pos = systemLower.indexOf(marker);
            if (pos >= 0 && (taskPosition < 0 || pos < taskPosition)) {
                taskPosition = pos;
            }
        }

        // Find first rule marker
        Pattern rulePattern = Pattern.compile("(?i)(##\\s*rules|rule\\s+\\d|\\b[A-Z]{1,3}\\d+\\s*[—-])");
        var ruleMatcher = rulePattern.matcher(system);
        int rulePosition = ruleMatcher.find() ? ruleMatcher.start() : system.length();

        if (taskPosition >= 0 && taskPosition < rulePosition) {
            totalPoints += 1;
        } else if (taskPosition < 0) {
            // Already flagged in Check 2
        } else {
            issues.add(QualityIssue.info("CLARITY",
                    "Task statement appears AFTER the rules section. "
                            + "LLM pays more attention to text that appears first.",
                    "CLR-006"));
            suggestions.add("Move the task statement above the rules section.");
        }

        // ── Check ambiguous quantifiers (informational) ─────────────────
        List<String> foundAmbiguous = lexicon.keywords("clarity.ambiguous-quantifiers").stream()
                .filter(systemLower::contains)
                .toList();
        if (!foundAmbiguous.isEmpty()) {
            issues.add(QualityIssue.info("CLARITY",
                    "Ambiguous quantifiers found: " + foundAmbiguous
                            + ". Consider using specific numbers.", "CLR-007"));
        }

        // ── Check 7: Ambiguous pronoun references (CLR-008) ──────────────
        String combined = prompt.combinedPrompt().toLowerCase();
        String[] sentences = combined.split("[.!?]\\s+");
        int ambiguousCount = 0;
        for (int i = 1; i < sentences.length; i++) {
            String sentence = sentences[i];
            boolean hasAmbiguousPronoun = lexicon.keywords("clarity.ambiguous-pronouns").stream()
                    .anyMatch(p -> sentence.contains(p) || sentence.startsWith(p.trim()));
            if (hasAmbiguousPronoun) {
                String prev = sentences[i - 1].trim();
                if (prev.split("\\s+").length < 8) {
                    ambiguousCount++;
                }
            }
        }
        if (ambiguousCount >= 2) {
            issues.add(QualityIssue.info("CLARITY",
                    "Found " + ambiguousCount + " potentially ambiguous pronoun references "
                            + "(it/this/that). Consider using specific nouns.", "CLR-008"));
        }

        // ── Check 8: Instruction density (CLR-009) ───────────────────────
        String[] allSentences = system.split("[.!?]\\s+");
        if (allSentences.length > 4) {
            long instructionSentences = Arrays.stream(allSentences)
                    .filter(s -> {
                        String lower = s.toLowerCase().trim();
                        return lexicon.keywords("clarity.imperative-verbs").stream().anyMatch(lower::contains)
                                || lower.contains("must") || lower.contains("should")
                                || lower.contains("always") || lower.contains("never");
                    }).count();
            double density = (double) instructionSentences / allSentences.length;
            if (density < 0.4) {
                issues.add(QualityIssue.info("CLARITY",
                        String.format("Low instruction density (%.0f%% preamble). "
                                + "Over 60%% of sentences are context rather than instructions.",
                                (1 - density) * 100), "CLR-009"));
            }
        }

        double score = maxPoints > 0 ? totalPoints / maxPoints : 0;
        return new DimensionResult("CLARITY", Math.min(score, 1.0), 1.0,
                issues, suggestions);
    }

    @Override
    public List<PromptFix> suggestFixes(PromptUnderTest prompt, DimensionResult result) {
        List<PromptFix> fixes = new ArrayList<>();
        for (QualityIssue issue : result.issues()) {
            switch (issue.ruleId()) {
                case "CLR-001" -> fixes.add(new PromptFix("CLR-001", "Add role definition",
                        FixType.INSERT, FixLocation.SYSTEM_PROMPT, null,
                        "You are a " + prompt.agentTypeProfile().agentType().toLowerCase() + " specialist.\n",
                        FixConfidence.HIGH));
                case "CLR-005" -> fixes.add(new PromptFix("CLR-005", "Add output format section",
                        FixType.INSERT, FixLocation.SYSTEM_PROMPT, null,
                        "\n## Output Format\nRespond with a JSON object matching the expected schema.\n",
                        FixConfidence.HIGH));
            }
        }
        return fixes;
    }
}
