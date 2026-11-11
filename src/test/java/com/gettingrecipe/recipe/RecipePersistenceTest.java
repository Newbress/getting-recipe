package com.gettingrecipe.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.gettingrecipe.AbstractIntegrationTest;
import com.gettingrecipe.feedback.Feedback;
import com.gettingrecipe.feedback.FeedbackRepository;
import com.gettingrecipe.feedback.FeedbackTargetType;
import com.gettingrecipe.job.AnalysisJob;
import com.gettingrecipe.job.AnalysisJobRepository;
import com.gettingrecipe.job.JobStatus;
import com.gettingrecipe.video.Video;
import com.gettingrecipe.video.VideoRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class RecipePersistenceTest extends AbstractIntegrationTest {

    @Autowired VideoRepository videos;
    @Autowired RecipeRepository recipes;
    @Autowired FeedbackRepository feedbacks;
    @Autowired AnalysisJobRepository jobs;
    @Autowired TransactionTemplate tx;

    private Video video(String id) {
        return videos.save(new Video(id, "https://www.youtube.com/watch?v=" + id, "김치볶음밥", 95));
    }

    private Recipe sampleRecipe(Video video, int version) {
        Recipe r = Recipe.original(video, "김치볶음밥", 2, true, version);
        r.addIngredient(new Ingredient("김치", new BigDecimal("200"), "g", IngredientSource.SPEECH, false, 1));
        r.addIngredient(new Ingredient("고추장", new BigDecimal("1.5"), "큰술", IngredientSource.VISUAL_ESTIMATE, true, 0));
        r.addIngredient(new Ingredient("참기름", null, null, IngredientSource.CAPTION, false, 2));
        r.addStep(new Step(2, "밥을 넣고 볶는다", 40));
        r.addStep(new Step(1, "김치를 볶는다", 3));
        r.addTip(new RecipeTip("센 불에서 빠르게", 0));
        return r;
    }

    @Test
    void 레시피_집합을_저장하고_정렬된_상태로_조회한다() {
        Video v = video("aaaaaaaaaa1");
        Long id = recipes.save(sampleRecipe(v, 1)).getId();

        tx.executeWithoutResult(s -> {
            Recipe found = recipes.findById(id).orElseThrow();
            assertThat(found.getIngredients()).extracting(Ingredient::getName)
                    .containsExactly("고추장", "김치", "참기름");          // sortOrder 순
            assertThat(found.getSteps()).extracting(Step::getOrder)
                    .containsExactly(1, 2);                               // stepOrder 순
            assertThat(found.getIngredients().get(0).isEstimated()).isTrue();
            assertThat(found.getIngredients().get(0).getAmount()).isEqualByComparingTo("1.5");
            assertThat(found.getIngredients().get(2).getAmount()).isNull();   // amount 는 null 허용
            assertThat(found.getTips()).extracting(RecipeTip::getContent).containsExactly("센 불에서 빠르게");
            assertThat(found.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void 최신_원본_레시피만_캐시_조회_대상이다() {
        Video v = video("aaaaaaaaaa2");
        Recipe v1 = recipes.save(sampleRecipe(v, 1));
        Recipe v2 = recipes.save(sampleRecipe(v, 2));
        recipes.save(Recipe.adaptedFrom(v2, "김치볶음밥(에어프라이어)", 2, "에어프라이어만 사용"));

        Recipe latest = recipes
                .findFirstByVideoVideoIdAndParentRecipeIdIsNullOrderByVersionDesc("aaaaaaaaaa2").orElseThrow();

        assertThat(latest.getId()).isEqualTo(v2.getId());
        assertThat(latest.getId()).isNotEqualTo(v1.getId());
        assertThat(latest.isAdapted()).isFalse();
    }

    @Test
    void 변환본은_원본과_별도로_저장되고_원본을_바꾸지_않는다() {
        Video v = video("aaaaaaaaaa3");
        Recipe original = recipes.save(sampleRecipe(v, 1));

        Recipe adapted = Recipe.adaptedFrom(original, "비건 김치볶음밥", 2, "비건");
        adapted.addIngredient(new Ingredient("두부", new BigDecimal("100"), "g", IngredientSource.SPEECH, false, 0));
        Recipe savedAdapted = recipes.save(adapted);

        assertThat(savedAdapted.getParentRecipeId()).isEqualTo(original.getId());
        tx.executeWithoutResult(s -> {
            Recipe reloaded = recipes.findById(original.getId()).orElseThrow();
            assertThat(reloaded.getIngredients()).hasSize(3);
            assertThat(reloaded.getTitle()).isEqualTo("김치볶음밥");
            assertThat(reloaded.isAdapted()).isFalse();
        });
    }

    @Test
    void 피드백은_레시피에_연결되어_저장된다() {
        Recipe r = recipes.save(sampleRecipe(video("aaaaaaaaaa4"), 1));
        Long ingredientId = tx.execute(s ->
                recipes.findById(r.getId()).orElseThrow().getIngredients().get(0).getId());

        feedbacks.save(new Feedback(r, FeedbackTargetType.INGREDIENT, ingredientId, "고추장 양이 틀렸어요"));
        feedbacks.save(new Feedback(r, FeedbackTargetType.OVERALL, null, "전체적으로 다름"));

        assertThat(feedbacks.findByRecipeIdOrderByCreatedAtAsc(r.getId()))
                .extracting(Feedback::getTargetType)
                .containsExactly(FeedbackTargetType.INGREDIENT, FeedbackTargetType.OVERALL);
    }

    @Test
    void 작업_상태_변경이_DB에_반영된다() {
        video("aaaaaaaaaa5");
        AnalysisJob job = jobs.save(AnalysisJob.analyze("aaaaaaaaaa5"));
        assertThat(jobs.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.PENDING);

        job.markProcessing();
        jobs.save(job);

        AnalysisJob reloaded = jobs.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(reloaded.getCreatedAt());
    }
}
