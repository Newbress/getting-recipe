package com.gettingrecipe.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gettingrecipe.recipe.IngredientSource;
import java.math.BigDecimal;

/** Gemini 응답의 재료 한 줄. amount/unit 은 영상에서 알 수 없으면 null. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IngredientDraft(
        @JsonProperty("name") String name,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("unit") String unit,
        @JsonProperty("source") IngredientSource source,
        @JsonProperty("isEstimated") boolean isEstimated) {
}
