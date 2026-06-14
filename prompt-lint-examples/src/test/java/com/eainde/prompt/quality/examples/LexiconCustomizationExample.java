package com.eainde.prompt.quality.examples;

import com.eainde.prompt.quality.PromptQualityAnalyzer;
import com.eainde.prompt.quality.config.Lexicon;
import com.eainde.prompt.quality.config.Strictness;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAMPLE: the keyword lists driving the analyzers are externalized as a Lexicon.
 * You can extend categories in code, or load overrides from JSON / properties / YAML
 * files, then hand the customised lexicon to the analyzer via builder().lexicon(...).
 */
class LexiconCustomizationExample {

    private static final String FILLER = "token-efficiency.filler-phrases";

    @Test
    void extendACategoryInCode() {
        Lexicon lexicon = Lexicon.defaults().extend(FILLER, "circle back", "low-hanging fruit");

        assertThat(lexicon.keywords(FILLER)).contains("circle back", "low-hanging fruit");

        PromptQualityAnalyzer analyzer = PromptQualityAnalyzer.builder().lexicon(lexicon).build();
        assertThat(analyzer).isNotNull();
    }

    @Test
    void loadOverridesFromEachFileFormat() {
        for (String file : new String[] {
                "custom-lexicon.json", "custom-lexicon.properties", "custom-lexicon.yaml"}) {

            Lexicon lexicon = Lexicon.defaults().mergeFrom(Path.of("src/test/resources/lexicons", file));

            assertThat(lexicon.keywords(FILLER))
                    .as("loaded from %s", file)
                    .contains("as per our discussion", "at this point in time");
        }
    }

    @Test
    void buildAStandaloneLexiconFromAFileWithStrictness() {
        Lexicon lexicon = Lexicon.from(
                Path.of("src/test/resources/lexicons/custom-lexicon.json"), Strictness.SILENT);

        assertThat(lexicon.keywords(FILLER)).contains("as per our discussion");
    }
}
