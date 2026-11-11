package com.gettingrecipe.gemini;

import com.gettingrecipe.recipe.IngredientSource;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 로컬 개발용: Gemini 를 호출하지 않고 고정된 샘플 레시피를 반환한다 (GEMINI_MOCK=true). */
@Component
@ConditionalOnProperty(prefix = "gemini", name = "mock", havingValue = "true")
public class MockRecipeAnalyzer implements RecipeAnalyzer {

    @Override
    public RecipeDraft analyze(AnalysisInput input) {
        RecipeDraft sample = new RecipeDraft(
                true,
                "[Mock] 김치볶음밥",
                2,
                List.of(
                        new IngredientDraft("김치", new BigDecimal("200"), "g", IngredientSource.SPEECH, false),
                        new IngredientDraft("밥", new BigDecimal("2"), "공기", IngredientSource.CAPTION, false),
                        new IngredientDraft("고추장", new BigDecimal("1"), "큰술", IngredientSource.VISUAL_ESTIMATE, true),
                        new IngredientDraft("참기름", null, null, IngredientSource.SPEECH, false)),
                List.of(
                        new StepDraft(1, "김치를 잘게 썰어 기름에 볶는다", 3),
                        new StepDraft(2, "밥과 고추장을 넣고 센 불에서 볶는다", 15),
                        new StepDraft(3, "참기름을 두르고 마무리한다", 28)),
                List.of("김치는 충분히 볶아야 감칠맛이 난다"));
        return RecipeDraftSanitizer.sanitize(sample, input.durationSec());
    }
}
