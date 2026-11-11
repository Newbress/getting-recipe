package com.gettingrecipe.gemini;

import com.gettingrecipe.recipe.IngredientSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gemini 응답을 서버 규칙에 맞게 검증·보정한다 (순수 함수).
 *
 * <ul>
 *   <li>startSec: null/음수 -> 보정, 영상 길이 초과 -> 길이 안으로 clamp, 순서 역전 -> 이전 단계 시점으로 올림</li>
 *   <li>order: 입력 order 순으로 정렬 후 1..n 재번호</li>
 *   <li>VISUAL_ESTIMATE 재료는 항상 isEstimated=true</li>
 *   <li>레시피인데 재료/단계가 하나도 없으면 사용 불가 -> {@link RecipeDraftInvalidException}</li>
 * </ul>
 */
public final class RecipeDraftSanitizer {

    static final int MAX_INGREDIENTS = 100;
    static final int MAX_STEPS = 100;
    static final int MAX_TIPS = 30;
    static final int MAX_SERVINGS = 100;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");

    private RecipeDraftSanitizer() {
    }

    public static RecipeDraft sanitize(RecipeDraft draft, Integer durationSec) {
        if (draft == null) {
            throw new RecipeDraftInvalidException("빈 응답");
        }
        String title = truncate(blankToNull(draft.title()), 500);
        if (!draft.isRecipe()) {
            return new RecipeDraft(false, title, null, List.of(), List.of(), List.of());
        }

        List<IngredientDraft> ingredients = sanitizeIngredients(draft.ingredients());
        List<StepDraft> steps = sanitizeSteps(draft.steps(), durationSec);
        if (ingredients.isEmpty()) {
            throw new RecipeDraftInvalidException("재료가 없는 레시피 응답");
        }
        if (steps.isEmpty()) {
            throw new RecipeDraftInvalidException("조리 단계가 없는 레시피 응답");
        }
        Integer servings = draft.servings() != null && draft.servings() > 0 && draft.servings() <= MAX_SERVINGS
                ? draft.servings() : null;
        return new RecipeDraft(true, title, servings, ingredients, steps, sanitizeTips(draft.tips()));
    }

    private static List<IngredientDraft> sanitizeIngredients(List<IngredientDraft> raw) {
        List<IngredientDraft> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (IngredientDraft in : raw) {
            if (in == null || blankToNull(in.name()) == null) {
                continue;
            }
            // 출처가 빠졌으면 가장 보수적인 값(화면 추정)으로 표시한다
            IngredientSource source = in.source() != null ? in.source() : IngredientSource.VISUAL_ESTIMATE;
            boolean estimated = in.isEstimated() || source == IngredientSource.VISUAL_ESTIMATE;
            result.add(new IngredientDraft(
                    truncate(in.name().trim(), 200),
                    sanitizeAmount(in.amount()),
                    truncate(blankToNull(in.unit()), 50),
                    source,
                    estimated));
            if (result.size() == MAX_INGREDIENTS) {
                break;
            }
        }
        return result;
    }

    private static BigDecimal sanitizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(MAX_AMOUNT) >= 0) {
            return null;
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static List<StepDraft> sanitizeSteps(List<StepDraft> raw, Integer durationSec) {
        if (raw == null) {
            return List.of();
        }
        List<StepDraft> ordered = raw.stream()
                .filter(s -> s != null && blankToNull(s.description()) != null)
                .sorted(Comparator.comparingInt(StepDraft::order))     // stable sort
                .limit(MAX_STEPS)
                .toList();

        int upperBound = durationSec == null ? Integer.MAX_VALUE : Math.max(durationSec - 1, 0);
        List<StepDraft> result = new ArrayList<>();
        int previous = 0;
        for (int i = 0; i < ordered.size(); i++) {
            StepDraft s = ordered.get(i);
            int sec = s.startSec() == null ? previous : s.startSec();
            sec = Math.max(sec, 0);
            sec = Math.min(sec, upperBound);
            sec = Math.max(sec, previous);                              // 순서 역전 방지
            result.add(new StepDraft(i + 1, truncate(s.description().trim(), 2000), sec));
            previous = sec;
        }
        return result;
    }

    private static List<String> sanitizeTips(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .map(RecipeDraftSanitizer::blankToNull)
                .filter(t -> t != null)
                .map(t -> truncate(t, 500))
                .limit(MAX_TIPS)
                .toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
