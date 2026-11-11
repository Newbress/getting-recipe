package com.gettingrecipe.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey  환경변수 YOUTUBE_API_KEY. 비어 있으면 길이 검증 없이 동작한다
 * @param baseUrl YouTube Data API 서버 주소
 */
@ConfigurationProperties(prefix = "youtube")
public record YoutubeProperties(String apiKey, String baseUrl) {

    public YoutubeProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://www.googleapis.com";
        }
    }
}
