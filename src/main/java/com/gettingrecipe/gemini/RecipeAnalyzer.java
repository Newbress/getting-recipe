package com.gettingrecipe.gemini;

/** 영상 -> 레시피 분석기. 테스트에서는 Mock 구현으로 교체한다. */
public interface RecipeAnalyzer {

    /**
     * @return 검증·보정이 끝난 레시피
     * @throws RecipeAnalysisException 분석에 실패한 경우(메시지는 사용자에게 보여줄 수 있는 문구)
     */
    RecipeDraft analyze(AnalysisInput input);
}
