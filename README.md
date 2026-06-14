# prompt-lint

A Java library that statically analyzes LLM prompt quality — no LLM calls required. Think of it as a linter for your AI agent prompts.

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
    <artifactId>prompt-lint-core</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

> The library was split into modules in 1.0-SNAPSHOT. The engine is now `prompt-lint-core` (API and packages unchanged). To lint prompt **seed files** in CI without writing Java, see [Lint seed files in CI](#lint-seed-files-in-ci).

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

## Rule Configuration

By default the analyzer uses built-in keyword lists. The builder lets you customize those lists, remap rule severities, suppress noisy issues, and filter known-good issues via a baseline snapshot — all without touching the analyzer code.

### Quick start

```java
import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.model.Severity;

import java.nio.file.Path;

var analyzer = PromptQualityAnalyzer.builder()
    .lexicon(Lexicon.defaults()
        .extend("clarity.vague-words", "leverage")
        .mergeFrom(Path.of("prompt-lint.yaml")))
    .severityOverride("OUT-001", Severity.WARNING)
    .suppress("CLR-007")
    .suppress("TOK-001", "legacy prompt, cleanup ticket PL-42")
    .baseline(Path.of("prompt-lint-baseline.json"))
    .build();
```

### Lexicon category reference

All 28 built-in keyword categories, the analyzer dimension each feeds, and the rule(s) they influence:

| Category | Analyzer dimension | What it detects |
|---|---|---|
| `clarity.role-starters` | CLARITY | role definition present (CLR-001) |
| `clarity.task-markers` | CLARITY | explicit task statement (CLR-002, CLR-006) |
| `clarity.imperative-verbs` | CLARITY | direct command verbs (CLR-003, CLR-009) |
| `clarity.vague-words` | CLARITY | vague/hedging language (CLR-004) |
| `clarity.ambiguous-pronouns` | CLARITY | ambiguous pronoun references (CLR-008) |
| `clarity.ambiguous-quantifiers` | CLARITY | imprecise quantifiers (CLR-007) |
| `clarity.output-section-markers` | CLARITY | output format section present (CLR-005) |
| `constraints.empty-handling` | CONSTRAINT_COVERAGE | empty/missing input instructions (CON-001) |
| `constraints.uncertainty-handling` | CONSTRAINT_COVERAGE | when-in-doubt guidance (CON-002) |
| `constraints.negative-instructions` | CONSTRAINT_COVERAGE | what-not-to-do instructions (CON-003) |
| `constraints.default-value-markers` | CONSTRAINT_COVERAGE | default values for optional fields (CON-006) |
| `constraints.input-size-handling` | CONSTRAINT_COVERAGE | truncation/pagination for large inputs (CON-007) |
| `constraints.field-constraints` | CONSTRAINT_COVERAGE | field nullability and valid values (CON-004) |
| `consistency.formal-markers` | CONSISTENCY | formal register words for tone-shift detection (CNS-007) |
| `consistency.informal-markers` | CONSISTENCY | informal register words for tone-shift detection (CNS-007) |
| `groundedness.grounding-instructions` | GROUNDEDNESS | source-only grounding phrases (GRD-001) |
| `groundedness.external-knowledge-prohibitions` | GROUNDEDNESS | bans on external knowledge (GRD-002) |
| `groundedness.citation-requirements` | GROUNDEDNESS | document/page citation phrases (GRD-003) |
| `groundedness.fabrication-prohibitions` | GROUNDEDNESS | never-fabricate/invent phrases (GRD-004) |
| `groundedness.conflicting-grounding-phrases` | GROUNDEDNESS | contradictory use-your-knowledge phrases (GRD-007) |
| `groundedness.document-boundary-markers` | GROUNDEDNESS | document delimiter markers in user prompt (GRD-005) |
| `injection.defensive-instructions` | INJECTION_RESISTANCE | treat-document-as-data phrases (INJ-001) |
| `injection.role-boundaries` | INJECTION_RESISTANCE | role-lock / sole-task phrases (INJ-002) |
| `injection.risky-echo-patterns` | INJECTION_RESISTANCE | repeat-back / echo-input patterns (INJ-005) |
| `injection.privilege-escalation-patterns` | INJECTION_RESISTANCE | user-claims-admin / grant-access patterns (INJ-006) |
| `specificity.vague-verbs` | SPECIFICITY | vague action verbs (SPC-008) |
| `specificity.open-ended-phrases` | SPECIFICITY | open-ended freedom phrases (SPC-006) |
| `token-efficiency.filler-phrases` | TOKEN_EFFICIENCY | verbose filler phrases (TOK-004) |

### Lexicon file formats

`mergeFrom` and `from` accept `.json`, `.properties`, `.yaml`, or `.yml` files.

**JSON** — array value = replace the full list; object value = named ops:

```json
{
  "clarity.vague-words": ["try to", "if possible", "leverage"],
  "groundedness.grounding-instructions": {
    "extend": ["as stated in the document"],
    "remove": ["verbatim"]
  },
  "token-efficiency.filler-phrases": {
    "replace": ["please note that", "keep in mind that"]
  }
}
```

**Properties** — key format `<category>.<op>=<comma-separated values>`:

```properties
clarity.vague-words.extend=leverage,synergize
groundedness.fabrication-prohibitions.remove=never hallucinate
token-efficiency.filler-phrases.replace=please note that,keep in mind that
```

> **Note:** properties format splits on commas after trimming — it cannot express keywords that contain commas or where leading/trailing spaces are significant (e.g. the default `" it "` pronoun entries). Use JSON or YAML for those.

**YAML** — same shape as JSON:

```yaml
clarity.vague-words:
  - try to
  - if possible
  - leverage
groundedness.grounding-instructions:
  extend:
    - as stated in the document
  remove:
    - verbatim
```

#### Loading modes

| Method | Semantics |
|---|---|
| `Lexicon.defaults()` | Starts from all 28 built-in keyword lists; no file required. |
| `Lexicon.defaults().mergeFrom(path)` | Applies file ops (extend/remove/replace) on top of the built-in lists; categories not in the file keep their defaults. |
| `Lexicon.from(path)` / `Lexicon.from(path, Strictness)` | External-only: starts from an empty lexicon (all 28 categories present, zero keywords); the file provides all keywords. Categories the file omits stay empty — their checks match nothing. Warns on stderr by default; pass `Strictness.SILENT` to suppress. |

> **Matching semantics:** keywords are matched as lowercase substring `contains`. Leading and trailing spaces are significant — the default `" it "` entry enforces a word boundary. Properties format trims each value after splitting on `,`, so space-significant keywords and comma-containing keywords must use JSON or YAML.

### Suppression and baseline

**Suppression** hides issues from reports and assertions. **Scores are never affected** — a suppressed issue still costs the same points. This is intentional: suppression is for noise management, not threshold gaming.

```java
// Suppress with optional reason (documentation only — not stored):
.suppress("CLR-007")
.suppress("TOK-001", "legacy prompt, cleanup ticket PL-42")
```

**Baseline** filters issues that were already present when the baseline was captured, so only *new* regressions surface. Scores are likewise unaffected.

Baseline workflow:

```java
// 1. Capture a snapshot after an initial analysis pass:
PromptQualityReport report = analyzer.analyze(myPrompt);
Baseline.fromReport(report).writeTo(Path.of("prompt-lint-baseline.json"));

// 2. Commit the file to version control.

// 3. Configure the analyzer to use it:
var analyzer = PromptQualityAnalyzer.builder()
    .baseline(Path.of("prompt-lint-baseline.json"))
    .build();
```

The baseline file is plain JSON with `agentName:ruleId` fingerprints, sorted for stable diffs:

```json
{
  "version": 1,
  "entries": [
    "my-extraction-agent:CLR-007",
    "my-extraction-agent:TOK-001"
  ]
}
```

A missing or unreadable baseline file throws at build time (fail-fast).

### YAML optional dependency

YAML lexicon files (`.yaml` / `.yml`) require an optional dependency not included by default:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
    <version>${jackson.version}</version>
</dependency>
```

If the dependency is absent and a `.yaml` file is loaded, the loader throws `IllegalStateException` with a clear message. JSON and `.properties` files have no additional dependencies.

### Plugin analyzers and custom categories

Implement `LexiconAware` to declare custom keyword categories in a plugin analyzer. The builder discovers plugins via `ServiceLoader` and registers their categories before the lexicon is resolved.

```java
@DimensionMeta(name = "MY_CHECK", defaultWeight = 0.10, description = "Custom safety check")
public class MyAnalyzer implements PromptDimensionAnalyzer, LexiconAware {

    @Override
    public Map<String, List<String>> declaredCategories() {
        return Map.of("my.safety-words", List.of("unsafe", "restricted"));
    }

    @Override
    public void setLexicon(Lexicon lexicon) {
        // called once at build time with the fully-resolved lexicon
    }
    // ...
}
```

To configure a plugin's categories from a lexicon file, call `withCategory` *before* `mergeFrom` so the category exists when the file ops are applied:

```java
Lexicon.defaults()
    .withCategory("my.safety-words", List.of("unsafe", "restricted"))
    .mergeFrom(Path.of("prompt-lint.yaml"));
```

## Lint seed files in CI

When prompts don't live in Java code — for example, they're loaded into an in-memory database from **seed files** checked into the repo — use the Maven plugin to lint those files directly. It extracts each prompt, runs the full analyzer, writes console + HTML reports, and fails the build below a threshold.

Supported seed formats: SQL `INSERT` scripts, JSON, YAML, and `.properties`.

```xml
<plugin>
    <groupId>com.eainde</groupId>
    <artifactId>prompt-lint-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>   <!-- bound to the verify phase -->
        </execution>
    </executions>
    <configuration>
        <failOnScoreBelow>0.75</failOnScoreBelow>
        <failOnCritical>true</failOnCritical>
        <htmlReport>true</htmlReport>           <!-- target/prompt-lint/report.html -->
        <sources>
            <source>
                <path>src/main/resources/db/data.sql</path>
                <!-- <type> is optional; inferred from the file extension -->
                <mapping>
                    <table>prompts</table>          <!-- SQL only: which table to read -->
                    <nameField>agent_id</nameField> <!-- prompt id; omit for a generated id -->
                    <systemPrompt>system_text</systemPrompt>
                    <userPrompt>user_text</userPrompt>
                    <agentType>agent_type</agentType>
                </mapping>
            </source>
        </sources>
    </configuration>
</plugin>
```

Run it directly with `mvn prompt-lint:check`, or let it run on `mvn verify`.

### Mapping by format

A `<mapping>` names the **source field** that supplies each prompt attribute. `systemPrompt` is required (records without it are skipped with a warning); everything else is optional (`userPrompt` → empty, `agentType` → `DEFAULT`, `inputs` → comma-separated list, etc.).

| Format | What a "field name" refers to | Record identity |
|---|---|---|
| **SQL** | a column name (case-insensitive); `<table>` filters which `INSERT`s are read | `<nameField>` column, else generated |
| **JSON** | an object key; dotted paths like `prompt.system` are supported | array index, or the object key when the file is an object-of-objects |
| **YAML** | same as JSON | the top-level key |
| **properties** | the `<field>` in `prompts.<id>.<field>` keys | `<id>` segment |

### Plugin parameters

| Parameter | Default | Purpose |
|---|---|---|
| `failOnScoreBelow` | `0.75` | Build fails if any prompt scores below this |
| `failOnCritical` | `true` | Build fails if any prompt has a CRITICAL issue |
| `htmlReport` | `true` | Write `target/prompt-lint/report.html` |
| `allowEmpty` | `false` | If false, a source yielding zero prompts fails the build (catches a broken `<mapping>`); also settable per `<source>` |
| `lexiconFile` | — | Optional lexicon file to customize keyword lists (JSON/properties/YAML) |
| `baselineFile` | — | Optional baseline file; only issues new since the baseline surface |

## Requirements

- Java 17+
- No runtime dependencies beyond Jackson (for JSON validation in `OutputContractAnalyzer`)
- `jackson-dataformat-yaml` optional for the core (YAML lexicon files only); required by `prompt-lint-sources` for `.yaml` seed files
