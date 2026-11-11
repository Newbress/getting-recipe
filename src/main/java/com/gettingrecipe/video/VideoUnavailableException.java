package com.gettingrecipe.video;

/** 존재하지 않거나 비공개/라이브 등 Gemini 로 분석할 수 없는 영상. 메시지는 사용자에게 노출 가능. */
public class VideoUnavailableException extends RuntimeException {

    public VideoUnavailableException(String message) {
        super(message);
    }
}
