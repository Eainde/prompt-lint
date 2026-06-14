# prompt-lint examples

Runnable, green examples of every prompt-lint feature, written as JUnit tests.
Run them all:

```bash
mvn -pl prompt-lint-examples -am test
```

Each test method is a self-contained example and prints its rendered report, so
reading the test output teaches the feature.

## Index

| Example test | Demonstrates |
|---|---|
| `GettingStartedExample` | Build an analyzer, analyze a prompt, read the score |
| `AgentTypeProfileExample` | Built-in profiles + custom weights change scoring |
| `EightDimensionsExample` | What each of the 8 dimensions catches (CLR/SPC/GRD/OUT/CON/CNS/TOK/INJ) |
| `FluentAssertionsExample` | `PromptQualityAssert` fluent gate for CI |
| `ApiResultExample` | `analyzeAndReport` → JSON-friendly `PromptQualityResult` |
| `OutputContractSchemaExample` | Response-schema / JSON output-contract checks |
| `LexiconCustomizationExample` | Extend keyword lists in code or load JSON/YAML/properties |
| `LintConfigExample` | Severity overrides, suppression, baseline filtering |
| `AutoFixExample` | Inspect and apply `PromptFix` suggestions |
| `ReportRenderingExample` | Console report + multi-prompt summary |
| `CustomDimensionPluginExample` | Add a custom dimension (SPI, `@DimensionMeta`, `LexiconAware`) |
| `SeedFileLoadingExample` | Load prompts from SQL/JSON/YAML/properties seed files |

## Linting seed files as a CI gate (Maven plugin)

`SeedFileLoadingExample` shows the loaders programmatically. To make it a build
gate, wire the plugin into the project that owns the seed files:

```xml
<plugin>
    <groupId>com.eainde</groupId>
    <artifactId>prompt-lint-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
    <configuration>
        <failOnScoreBelow>0.75</failOnScoreBelow>
        <failOnCritical>true</failOnCritical>
        <htmlReport>true</htmlReport>
        <sources>
            <source>
                <path>src/main/resources/db/prompts.sql</path>
                <mapping>
                    <table>prompts</table>
                    <nameField>agent_id</nameField>
                    <systemPrompt>system_text</systemPrompt>
                    <userPrompt>user_text</userPrompt>
                    <agentType>agent_type</agentType>
                </mapping>
            </source>
        </sources>
    </configuration>
</plugin>
```
