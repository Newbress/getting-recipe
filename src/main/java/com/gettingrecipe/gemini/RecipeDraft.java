package com.gettingrecipe.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Gemini 가 반환하는 레시피 JSON (응답 스키마와 1:1 대응). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipeDraft(
        @JsonProperty("isRecipe") boolean isRecipe,
        @JsonProperty("title") String title,
        @JsonProperty("servings") Integer servings,
        @JsonProperty("ingredients") List<IngredientDraft> ingredients,
        @JsonProperty("steps") List<StepDraft> steps,
        @JsonProperty("tips") List<String> tips) {
}
