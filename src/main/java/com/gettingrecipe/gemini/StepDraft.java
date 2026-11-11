package com.gettingrecipe.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Gemini 응답의 조리 단계. startSec 은 영상에서 이 단계가 시작되는 초. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StepDraft(
        @JsonProperty("order") int order,
        @JsonProperty("description") String description,
        @JsonProperty("startSec") Integer startSec) {
}
