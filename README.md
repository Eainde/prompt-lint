# prompt-lint

A Java library that statically analyzes LLM prompt quality, no LLM calls required. Think of it as a linter for your AI agent prompts.

It scores prompts across 8 quality dimensions, flags issues by severity, and produces detailed reports. Runs in milliseconds, integrates into CI, and catches prompt regressions before they hit production.

## Why use it?

- **Prompts are code.** They silently degrade over time — someone adds vague language, removes the output schema, introduces contradictory rules. You don't notice until the LLM starts hallucinating.
- **No LLM calls.** All analysis is rule-based and deterministic. No API keys, no latency, no flaky tests.
- **CI-ready.** Set a quality threshold, run on every commit. Prompt regressions fail the build.
- **Weighted scoring by agent type.** An extraction agent weights groundedness higher; a formatter weights output contract higher. Built-in profiles: `EXTRACTION`, `CLASSIFICATION`, `FORMATTING`, `REVIEW`, `DEFAULT`.

## Quality Dimensions

| Dimension | What it checks |
|---|---|
| **CLARITY** | Role definition, task statement, imperative verbs, vague language, output format section, task-before-rules ordering |
| **SPECIFICITY** | Numbered rules, concrete examples, quantified thresholds, boundary conditions, enum constraints |
| **GROUNDEDNESS** | Source-grounding phrases, anti-hallucination guards, evidence requirements, citation instructions |
| **OUTPUT_CONTRACT** | JSON examples present and valid, field-level documentation, schema completeness |
| **CONSTRAINT_COVERAGE** | Edge case handling, null/empty handling, error handling, ordering rules, boundary conditions |
| **CONSISTENCY** | Template variable alignment with declared inputs, contradictory instructions, terminology consistency |
| **TOKEN_EFFICIENCY** | Prompt length vs complexity ratio, redundancy detection, filler removal |
| **INJECTION_RESISTANCE** | System/user boundary enforcement, input sanitization, refusal instructions, role-lock phrases |

## Getting Started

### Add dependency

```xml
<dependency>
    <groupId>com.eainde</groupId>
    <artifactId>prompt-lint</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

### Basic usage

```java
import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.model.AgentTypeProfile;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;
import com.eainde.prompt.quality.report.PromptQualityReportRenderer;

import java.util.Set;

// 1. Create the analyzer
var analyzer = PromptQualityAnalyzer.create();

// 2. Wrap your prompt
var prompt = new PromptUnderTest(
    "my-extraction-agent",
    systemPromptText,
    userPromptText,
    Set.of("sourceText", "fileNames"),   // declared input variables
    "rawNames",                           // declared output key
    AgentTypeProfile.EXTRACTION           // agent type profile
);

// 3. Analyze
PromptQualityReport report = analyzer.analyze(prompt);

// 4. Check results
System.out.println("Score: " + report.overallScore());
System.out.println("Critical issues: " + report.hasCriticalIssues());
```

### Use in JUnit tests

```java
import static com.eainde.prompt.quality.PromptQualityAssert.assertThat;

@Test
void promptMeetsQualityBar() {
    var analyzer = PromptQualityAnalyzer.create();
    var report = analyzer.analyze(myPrompt());

    assertThat(report)
        .passesThreshold(0.75)
        .hasNoCriticalIssues()
        .dimensionScoreAbove("GROUNDEDNESS", 0.80)
        .dimensionScoreAbove("OUTPUT_CONTRACT", 0.70)
        .hasFewerIssuesThan(10);
}
```

### Validate multiple agents at once

```java
@Test
void allAgentsPassQualityBar() {
    var analyzer = PromptQualityAnalyzer.create();
    var renderer = new PromptQualityReportRenderer();

    List<PromptUnderTest> prompts = List.of(
        extractorPrompt(),
        classifierPrompt(),
        formatterPrompt()
    );

    List<PromptQualityReport> reports = analyzer.analyzeAll(prompts);

    // Print CI summary
    System.out.println(renderer.renderSummary(reports, 0.75));

    // Assert all pass
    for (var report : reports) {
        assertThat(report).passesThreshold(0.75);
    }
}
```

### Regression testing

```java
@Test
void fixedIssueStaysFixed() {
    var report = analyzer.analyze(myPrompt());

    assertThat(report)
        .doesNotHaveIssue("CLR-005")   // output format section was added
        .doesNotHaveIssue("OUT-001");   // JSON example was added
}
```

## Agent Type Profiles

Each profile adjusts dimension weights to match what matters for that agent type:

| Profile | Top weighted dimensions |
|---|---|
| `EXTRACTION` | Groundedness (0.25), Specificity (0.15), Output Contract (0.15) |
| `CLASSIFICATION` | Specificity (0.20), Constraint Coverage (0.20), Groundedness (0.15) |
| `FORMATTING` | Output Contract (0.30), Consistency (0.15), Constraint Coverage (0.15) |
| `REVIEW` | Groundedness (0.20), Specificity (0.15), Output Contract (0.15) |
| `DEFAULT` | Equal weights (0.125 each) |

You can also create custom profiles:

```java
var customProfile = new AgentTypeProfile("MY_AGENT", Map.of(
    "CLARITY", 0.20,
    "SPECIFICITY", 0.20,
    "GROUNDEDNESS", 0.10,
    "OUTPUT_CONTRACT", 0.20,
    "CONSTRAINT_COVERAGE", 0.10,
    "CONSISTENCY", 0.10,
    "TOKEN_EFFICIENCY", 0.05,
    "INJECTION_RESISTANCE", 0.05
));
```

## Report Output

The renderer produces formatted console output:

```
═══════════════════════════════════════════════════════════════
  PROMPT QUALITY REPORT: my-extraction-agent
  Profile: EXTRACTION
═══════════════════════════════════════════════════════════════

  Overall Score: 0.82 / 1.00  PASS (threshold: 0.75)

  ┌────────────────────────┬───────┬────────┬─────────┐
  │ Dimension              │ Score │ Weight │ Contrib │
  ├────────────────────────┼───────┼────────┼─────────┤
  │   CLARITY              │  0.83 │  0.10  │  0.083  │
  │   SPECIFICITY          │  0.75 │  0.15  │  0.113  │
  │   GROUNDEDNESS         │  0.90 │  0.25  │  0.225  │
  │   OUTPUT_CONTRACT      │  0.80 │  0.15  │  0.120  │
  │   ...                  │       │        │         │
  └────────────────────────┴───────┴────────┴─────────┘

  Issues Found: 3 total (0 critical, 2 warning, 1 info)
    WARNING  [CLR-004] Vague language detected: [try to]
    WARNING  [SPC-002] No quantified thresholds found
    INFO     [CLR-007] Ambiguous quantifiers found: [some of]

  Suggestions:
    1. Replace vague phrases with imperative: 'try to extract' -> 'Extract'
    2. Add quantified thresholds where possible
═══════════════════════════════════════════════════════════════
```

## Issue Severity Levels

| Severity | Meaning |
|---|---|
| `CRITICAL` | Prompt is missing something fundamental (e.g., no output format) |
| `WARNING` | Quality problem that should be fixed (e.g., vague language) |
| `INFO` | Improvement opportunity (e.g., add more imperative verbs) |

## Requirements

- Java 17+
- No runtime dependencies beyond Jackson (for JSON validation in `OutputContractAnalyzer`)
