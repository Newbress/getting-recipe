package com.gettingrecipe.gemini;

/** 파싱은 됐지만 레시피로 쓸 수 없는 응답(재시도 대상). */
public class RecipeDraftInvalidException extends RuntimeException {

    public RecipeDraftInvalidException(String message) {
        super(message);
    }
}
