package com.eainde.prompt.quality.fix;

import com.eainde.prompt.quality.model.PromptUnderTest;
import java.util.List;

/**
 * Applies {@link PromptFix} suggestions to a prompt, producing a modified copy.
 *
 * <p>Supports INSERT (prepend or at anchor), REPLACE, and DELETE operations
 * on both system and user prompts. Idempotent — skips inserts if text already present.</p>
 */
public class PromptFixApplicator {

    /**
     * Applies all fixes to the prompt and returns a new modified {@link PromptUnderTest}.
     * The original prompt is not mutated.
     */
    public PromptUnderTest apply(PromptUnderTest original, List<PromptFix> fixes) {
        String system = original.systemPrompt();
        String user = original.userPrompt();

        for (PromptFix fix : fixes) {
            if (fix.location() == FixLocation.SYSTEM_PROMPT) {
                system = applyFix(system, fix);
            } else {
                user = applyFix(user, fix);
            }
        }

        return new PromptUnderTest(
                original.agentName(), system, user,
                original.declaredInputs(), original.declaredOutputKey(),
                original.agentTypeProfile(), original.responseSchema()
        );
    }

    private String applyFix(String text, PromptFix fix) {
        return switch (fix.fixType()) {
            case INSERT -> applyInsert(text, fix);
            case REPLACE -> applyReplace(text, fix);
            case DELETE -> applyDelete(text, fix);
        };
    }

    private String applyInsert(String text, PromptFix fix) {
        if (text.contains(fix.replacement().trim())) {
            return text;
        }
        if (fix.anchor() == null) {
            return fix.replacement() + text;
        }
        int idx = text.indexOf(fix.anchor());
        if (idx < 0) return text;
        int insertAt = idx + fix.anchor().length();
        return text.substring(0, insertAt) + fix.replacement() + text.substring(insertAt);
    }

    private String applyReplace(String text, PromptFix fix) {
        if (fix.anchor() == null || !text.contains(fix.anchor())) return text;
        return text.replace(fix.anchor(), fix.replacement());
    }

    private String applyDelete(String text, PromptFix fix) {
        if (fix.anchor() == null || !text.contains(fix.anchor())) return text;
        return text.replace(fix.anchor(), "");
    }
}
