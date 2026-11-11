package com.gettingrecipe.gemini;

import java.util.List;

/**
 * @param videoUrl      정규화된 YouTube 시청 URL
 * @param durationSec   영상 길이(초). 모르면 null
 * @param feedbackNotes 재생성 시 프롬프트에 포함할 사용자 피드백. 최초 분석이면 빈 리스트
 */
public record AnalysisInput(String videoUrl, Integer durationSec, List<String> feedbackNotes) {

    public AnalysisInput {
        feedbackNotes = feedbackNotes == null ? List.of() : List.copyOf(feedbackNotes);
    }

    public static AnalysisInput of(String videoUrl, Integer durationSec) {
        return new AnalysisInput(videoUrl, durationSec, List.of());
    }
}
