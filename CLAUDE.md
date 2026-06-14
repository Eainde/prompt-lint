# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test

Multi-module Maven build. The root `pom.xml` is a `pom`-packaging aggregator over four modules: `prompt-lint-core` (the engine), `prompt-lint-sources` (seed-file loaders), `prompt-lint-maven-plugin` (the `check` goal), and `prompt-lint-examples` (JUnit tests demonstrating every feature — the place to look for runnable usage examples; see `prompt-lint-examples/README.md`).

```bash
# Build everything
mvn compile

# Run all tests (all modules)
mvn test

# Test a single module
mvn -pl prompt-lint-core -am test

# Run a single test class / method (add -Dsurefire.failIfNoSpecifiedTests=false
# when the reactor pulls in modules that lack the named test)
mvn -pl prompt-lint-core -am test -Dtest=ClarityAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl prompt-lint-core -am test -Dtest=ClarityAnalyzerTest#testMethodName -Dsurefire.failIfNoSpecifiedTests=false

# Install the plugin to the local repo so other projects can use prompt-lint:check
mvn clean install
```

Java 17+ required. Maven wrapper not included — use system `mvn`. Engine code/packages are unchanged by the split; consumers of the library now depend on artifactId `prompt-lint-core` (not `prompt-lint`).

## Architecture

Static (no-LLM) prompt quality analyzer. Scores prompts across 8 dimensions using rule-based heuristics.

**Core flow:** `PromptQualityAnalyzer.analyze(PromptUnderTest)` → runs each `PromptDimensionAnalyzer` → collects `DimensionResult`s → computes weighted score via `AgentTypeProfile` → returns `PromptQualityReport`.

**Key types:**
- `PromptUnderTest` (record) — wraps system+user prompt text, declared inputs/outputs, agent profile, optional JSON response schema
- `PromptDimensionAnalyzer` (interface) — each dimension implements `analyze()` returning `DimensionResult` with score + `QualityIssue` list
- `AgentTypeProfile` — dimension weight presets (`EXTRACTION`, `CLASSIFICATION`, `FORMATTING`, `REVIEW`, `DEFAULT`) + custom weights
- `PromptQualityAssert` — fluent test assertions (`passesThreshold`, `hasNoCriticalIssues`, `dimensionScoreAbove`, `doesNotHaveIssue`)
- `PromptQualityResult` — API-friendly result wrapper used by `analyzeAndReport()`
- `Lexicon` / `LintConfig` / `Baseline` (in `config/`) — externalized keyword lists (28 categories, JSON/properties/YAML loadable), per-rule severity overrides, suppressions, baseline filtering. `PromptQualityAnalyzer.builder()` is the entry point; suppression/baseline never affect scores.

**8 dimension analyzers** in `analyzers/`: Clarity, Specificity, Groundedness, OutputContract, ConstraintCoverage, Consistency, TokenEfficiency, InjectionResistance. Each returns issues with codes like `CLR-005`, `OUT-001` and severity levels (CRITICAL/WARNING/INFO).

**Suggestions semantics:** improvement suggestions and `PromptFix`es are emitted only by FAILED checks (and not by every failed check — some INFO branches emit none). A clean prompt yields an empty suggestions list and the renderer omits the section entirely — this is by design, not a bug. `AllDimensionsPromptQualityTest` demonstrates both sides (well-formed prompt vs flawed draft with suggestions + fixes).

**Package layout (`prompt-lint-core`):**
- `com.eainde.prompt.quality` — entry points (`PromptQualityAnalyzer`, `PromptQualityAssert`)
- `com.eainde.prompt.quality.analyzers` — dimension analyzer implementations
- `com.eainde.prompt.quality.model` — data records (`PromptUnderTest`, `DimensionResult`, `QualityIssue`, `Severity`, `AgentTypeProfile`)
- `com.eainde.prompt.quality.report` — report generation and rendering
- `com.eainde.prompt.quality.api` — `PromptQualityResult` for API/REST use

**Seed-file loading (`prompt-lint-sources`, package `com.eainde.prompt.quality.sources`):** Turns repo seed files into `PromptUnderTest` objects so prompts stored outside code (e.g. an in-memory DB seeded from files) can be linted. `PromptSource` is the SPI; `SqlSeedSource` / `JsonSource` / `YamlPropertiesSource` implement it; `SourceLoaders` dispatches by type/extension. A `SourceMapping` declares which source field (SQL column / JSON key / YAML key / `prompts.<id>.<field>` property) supplies each prompt attribute. `RecordMapper` does the shared record→prompt conversion; `load()` returns a `LoadResult` (mapped prompts + warnings) — loaders never log. `SqlInsertParser` is a hand-written scanner for `INSERT` statements (no SQL dependency).

**Maven plugin (`prompt-lint-maven-plugin`, package `com.eainde.prompt.quality.maven`):** `CheckMojo` exposes the `prompt-lint:check` goal (bound to `verify`). It loads prompts via `prompt-lint-sources`, analyzes them, renders console + HTML reports (`HtmlReportRenderer`), and fails the build on threshold/critical violations. `SourceConfig`/`MappingConfig` are the `<source>`/`<mapping>` POM config objects.

## Dependencies

`prompt-lint-core`: only Jackson (JSON parsing for `OutputContractAnalyzer`) + JUnit 5 + AssertJ for tests. `jackson-dataformat-yaml` is an optional dependency (needed only for `.yaml`/`.yml` lexicon files). `prompt-lint-sources` additionally depends on `jackson-dataformat-yaml` (non-optional, for `.yaml` seed files). `prompt-lint-maven-plugin` depends on both modules plus the Maven Plugin API (`provided` scope).
