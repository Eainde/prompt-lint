package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.fix.PromptFix;
import com.eainde.prompt.quality.fix.PromptFixApplicator;
import com.eainde.prompt.quality.model.PromptUnderTest;
import com.eainde.prompt.quality.report.PromptQualityReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: failed checks can emit actionable PromptFix suggestions. You can inspect
 * them (each carries a confidence) and apply them with PromptFixApplicator to get a
 * rewritten prompt that scores at least as well.
 */
class AutoFixExample {

    private final PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.create();

    @Test
    void inspectAndApplySuggestedFixes() {
        PromptUnderTest draft = Examples.flawedDraft();
        PromptQualityReport before = analyzer.analyze(draft);
        Examples.print(before);

        List<PromptFix> fixes = before.suggestedFixes();
        assertThat(fixes).isNotEmpty();
        fixes.forEach(f -> System.out.printf("  [%s] %s -> %s%n",
                f.confidence(), f.ruleId(), f.description()));

        PromptUnderTest fixed = new PromptFixApplicator().apply(draft, fixes);
        PromptQualityReport after = analyzer.analyze(fixed);
        Examples.print(after);

        assertThat(after.overallScore()).isGreaterThanOrEqualTo(before.overallScore());
    }
}
