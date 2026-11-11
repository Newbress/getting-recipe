package com.gettingrecipe.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.gettingrecipe.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** gemini.mock 설정에 따라 RecipeAnalyzer 구현체가 올바르게 선택되는지 확인한다. */
class RecipeAnalyzerWiringTest {

    @Nested
    @DisplayName("기본 설정")
    class DefaultConfig extends AbstractIntegrationTest {

        @Autowired
        RecipeAnalyzer analyzer;

        @Test
        void 실제_Gemini_분석기가_등록된다() {
            assertThat(analyzer).isInstanceOf(GeminiRecipeAnalyzer.class);
        }
    }

    @Nested
    @DisplayName("목 모드 (gemini.mock=true)")
    @TestPropertySource(properties = "gemini.mock=true")
    class MockMode extends AbstractIntegrationTest {

        @Autowired
        RecipeAnalyzer analyzer;

        @Test
        void 목_분석기가_등록되고_API_호출_없이_보정된_샘플을_반환한다() {
            assertThat(analyzer).isInstanceOf(MockRecipeAnalyzer.class);

            RecipeDraft draft = analyzer.analyze(AnalysisInput.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ", 20));

            assertThat(draft.isRecipe()).isTrue();
            assertThat(draft.steps()).extracting(StepDraft::startSec).allMatch(sec -> sec <= 19);
        }
    }
}
