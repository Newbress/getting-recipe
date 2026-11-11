package com.gettingrecipe.gemini;

/** 분석 실패. 메시지는 Job.errorMessage 로 사용자에게 노출될 수 있으므로 내부 정보를 담지 않는다. */
public class RecipeAnalysisException extends RuntimeException {

    public RecipeAnalysisException(String message) {
        super(message);
    }

    public RecipeAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
