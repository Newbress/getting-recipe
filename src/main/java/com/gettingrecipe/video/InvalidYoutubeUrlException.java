package com.gettingrecipe.video;

/** YouTube 영상 URL로 해석할 수 없는 입력. API 계층에서 400으로 매핑한다. */
public class InvalidYoutubeUrlException extends RuntimeException {

    public InvalidYoutubeUrlException(String message) {
        super(message);
    }
}
