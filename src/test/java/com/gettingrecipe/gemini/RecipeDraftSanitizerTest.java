package com.gettingrecipe.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gettingrecipe.recipe.IngredientSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecipeDraftSanitizerTest {

    private static IngredientDraft ing(String name) {
        return new IngredientDraft(name, new BigDecimal("1"), "개", IngredientSource.SPEECH, false);
    }

    private static RecipeDraft recipe(List<IngredientDraft> ingredients, List<StepDraft> steps) {
        return new RecipeDraft(true, "김치볶음밥", 2, ingredients, steps, List.of("팁"));
    }

    private static RecipeDraft withSteps(StepDraft... steps) {
        return recipe(List.of(ing("김치")), List.of(steps));
    }

    private static List<Integer> startSecs(RecipeDraft d) {
        return d.steps().stream().map(StepDraft::startSec).toList();
    }

    // ---------- startSec 보정 ----------

    @Test
    void 정상_값은_그대로_유지한다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                withSteps(new StepDraft(1, "a", 3), new StepDraft(2, "b", 20), new StepDraft(3, "c", 45)), 60);

        assertThat(startSecs(result)).containsExactly(3, 20, 45);
    }

    @Test
    void 음수_startSec은_0으로_보정한다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                withSteps(new StepDraft(1, "a", -5), new StepDraft(2, "b", 10)), 60);

        assertThat(startSecs(result)).containsExactly(0, 10);
    }

    @Test
    void 영상_길이를_넘는_startSec은_마지막_초로_보정한다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                withSteps(new StepDraft(1, "a", 10), new StepDraft(2, "b", 500)), 65);

        assertThat(startSecs(result)).containsExactly(10, 64);
    }

    @Test
    void 영상_길이를_모르면_상한_보정은_하지_않는다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                withSteps(new StepDraft(1, "a", 10), new StepDraft(2, "b", 5000)), null);

        assertThat(startSecs(result)).containsExactly(10, 5000);
    }

    @Test
    void 순서가_역전된_startSec은_이전_단계_시점으로_올린다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                withSteps(new StepDraft(1, "a", 30), new StepDraft(2, "b", 10), new StepDraft(3, "c", 40)), 60);

        assertThat(startSecs(result)).containsExactly(30, 30, 40);
    }

    @Test
    void startSec이_없으면_이전_단계_시점을_사용한다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                withSteps(new StepDraft(1, "a", null), new StepDraft(2, "b", 12), new StepDraft(3, "c", null)), 60);

        assertThat(startSecs(result)).containsExactly(0, 12, 12);
    }

    @Test
    void 단계_번호는_order_순으로_정렬한_뒤_1부터_다시_매긴다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                withSteps(new StepDraft(7, "둘째", 20), new StepDraft(3, "첫째", 5), new StepDraft(9, "셋째", 30)), 60);

        assertThat(result.steps()).extracting(StepDraft::order).containsExactly(1, 2, 3);
        assertThat(result.steps()).extracting(StepDraft::description).containsExactly("첫째", "둘째", "셋째");
        assertThat(startSecs(result)).containsExactly(5, 20, 30);
    }

    @Test
    void 영상_길이가_1초_이하여도_범위를_벗어나지_않는다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(withSteps(new StepDraft(1, "a", 30)), 1);

        assertThat(startSecs(result)).containsExactly(0);
    }

    @Test
    void 내용이_빈_단계는_제거한다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                withSteps(new StepDraft(1, "  ", 3), new StepDraft(2, "볶는다", 10)), 60);

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).order()).isEqualTo(1);
    }

    // ---------- 재료 ----------

    @Test
    void 화면_추정_재료는_항상_추정으로_표시한다() {
        IngredientDraft visual = new IngredientDraft("소금", new BigDecimal("1"), "작은술",
                IngredientSource.VISUAL_ESTIMATE, false);

        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                recipe(List.of(visual), List.of(new StepDraft(1, "a", 1))), 60);

        assertThat(result.ingredients().get(0).isEstimated()).isTrue();
    }

    @Test
    void 출처가_없는_재료는_화면_추정으로_간주한다() {
        IngredientDraft noSource = new IngredientDraft("소금", new BigDecimal("1"), "작은술", null, false);

        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                recipe(List.of(noSource), List.of(new StepDraft(1, "a", 1))), 60);

        assertThat(result.ingredients().get(0).source()).isEqualTo(IngredientSource.VISUAL_ESTIMATE);
        assertThat(result.ingredients().get(0).isEstimated()).isTrue();
    }

    @Test
    void 음성이나_자막_출처의_추정_표시는_유지한다() {
        IngredientDraft vague = new IngredientDraft("간장", new BigDecimal("2"), "큰술", IngredientSource.SPEECH, true);

        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                recipe(List.of(vague), List.of(new StepDraft(1, "a", 1))), 60);

        assertThat(result.ingredients().get(0).source()).isEqualTo(IngredientSource.SPEECH);
        assertThat(result.ingredients().get(0).isEstimated()).isTrue();
    }

    @Test
    void 알_수_없는_분량_null은_그대로_둔다() {
        IngredientDraft unknown = new IngredientDraft("참기름", null, null, IngredientSource.SPEECH, false);

        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                recipe(List.of(unknown), List.of(new StepDraft(1, "a", 1))), 60);

        assertThat(result.ingredients().get(0).amount()).isNull();
        assertThat(result.ingredients().get(0).unit()).isNull();
    }

    @Test
    void 비정상_분량은_null로_바꾸고_소수는_둘째_자리로_맞춘다() {
        List<IngredientDraft> ingredients = List.of(
                new IngredientDraft("음수", new BigDecimal("-3"), "g", IngredientSource.SPEECH, false),
                new IngredientDraft("거대", new BigDecimal("999999999"), "g", IngredientSource.SPEECH, false),
                new IngredientDraft("소수", new BigDecimal("1.5555"), "컵", IngredientSource.SPEECH, false));

        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                recipe(ingredients, List.of(new StepDraft(1, "a", 1))), 60);

        assertThat(result.ingredients().get(0).amount()).isNull();
        assertThat(result.ingredients().get(1).amount()).isNull();
        assertThat(result.ingredients().get(2).amount()).isEqualByComparingTo("1.56");
    }

    @Test
    void 이름이_없는_재료는_제거하고_이름과_단위는_다듬는다() {
        List<IngredientDraft> ingredients = List.of(
                new IngredientDraft("  ", new BigDecimal("1"), "개", IngredientSource.SPEECH, false),
                new IngredientDraft("  양파 ", new BigDecimal("1"), "  ", IngredientSource.SPEECH, false));

        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                recipe(ingredients, List.of(new StepDraft(1, "a", 1))), 60);

        assertThat(result.ingredients()).hasSize(1);
        assertThat(result.ingredients().get(0).name()).isEqualTo("양파");
        assertThat(result.ingredients().get(0).unit()).isNull();
    }

    // ---------- 레시피 단위 검증 ----------

    @Test
    void 요리_영상이_아니면_내용을_비우고_isRecipe_false를_유지한다() {
        RecipeDraft notRecipe = new RecipeDraft(false, "고양이 영상", 3,
                List.of(ing("x")), List.of(new StepDraft(1, "x", 1)), List.of("x"));

        RecipeDraft result = RecipeDraftSanitizer.sanitize(notRecipe, 60);

        assertThat(result.isRecipe()).isFalse();
        assertThat(result.ingredients()).isEmpty();
        assertThat(result.steps()).isEmpty();
        assertThat(result.tips()).isEmpty();
        assertThat(result.servings()).isNull();
    }

    @Test
    void 요리_영상이_아니고_필드가_null이어도_통과한다() {
        RecipeDraft result = RecipeDraftSanitizer.sanitize(new RecipeDraft(false, null, null, null, null, null), 60);

        assertThat(result.isRecipe()).isFalse();
    }

    @Test
    void 레시피인데_재료가_없으면_사용할_수_없는_응답이다() {
        assertThatThrownBy(() -> RecipeDraftSanitizer.sanitize(
                recipe(List.of(), List.of(new StepDraft(1, "a", 1))), 60))
                .isInstanceOf(RecipeDraftInvalidException.class);
    }

    @Test
    void 레시피인데_단계가_없으면_사용할_수_없는_응답이다() {
        assertThatThrownBy(() -> RecipeDraftSanitizer.sanitize(recipe(List.of(ing("김치")), null), 60))
                .isInstanceOf(RecipeDraftInvalidException.class);
    }

    @Test
    void null_응답은_사용할_수_없다() {
        assertThatThrownBy(() -> RecipeDraftSanitizer.sanitize(null, 60))
                .isInstanceOf(RecipeDraftInvalidException.class);
    }

    @Test
    void 인분_수가_비정상이면_null이다() {
        RecipeDraft zero = new RecipeDraft(true, "t", 0, List.of(ing("a")), List.of(new StepDraft(1, "a", 1)), null);
        RecipeDraft huge = new RecipeDraft(true, "t", 5000, List.of(ing("a")), List.of(new StepDraft(1, "a", 1)), null);

        assertThat(RecipeDraftSanitizer.sanitize(zero, 60).servings()).isNull();
        assertThat(RecipeDraftSanitizer.sanitize(huge, 60).servings()).isNull();
    }

    @Test
    void 개수와_길이_상한을_적용한다() {
        List<IngredientDraft> manyIngredients = new ArrayList<>();
        List<StepDraft> manySteps = new ArrayList<>();
        List<String> manyTips = new ArrayList<>();
        IntStream.range(0, 150).forEach(i -> {
            manyIngredients.add(ing("재료" + i));
            manySteps.add(new StepDraft(i, "단계" + i, i));
            manyTips.add("팁" + i);
        });
        manyTips.add("  ");
        String longTitle = "가".repeat(900);

        RecipeDraft result = RecipeDraftSanitizer.sanitize(
                new RecipeDraft(true, longTitle, 2, manyIngredients, manySteps, manyTips), null);

        assertThat(result.ingredients()).hasSize(RecipeDraftSanitizer.MAX_INGREDIENTS);
        assertThat(result.steps()).hasSize(RecipeDraftSanitizer.MAX_STEPS);
        assertThat(result.tips()).hasSize(RecipeDraftSanitizer.MAX_TIPS);
        assertThat(result.title()).hasSize(500);
    }

    @Test
    void 빈_팁은_제거하고_null_팁_목록은_빈_목록이_된다() {
        RecipeDraft withTips = new RecipeDraft(true, "t", 2, List.of(ing("a")),
                List.of(new StepDraft(1, "a", 1)), List.of(" 센 불 ", "", " "));
        RecipeDraft noTips = new RecipeDraft(true, "t", 2, List.of(ing("a")),
                List.of(new StepDraft(1, "a", 1)), null);

        assertThat(RecipeDraftSanitizer.sanitize(withTips, 60).tips()).containsExactly("센 불");
        assertThat(RecipeDraftSanitizer.sanitize(noTips, 60).tips()).isEmpty();
    }
}
