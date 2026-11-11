package com.gettingrecipe.video;

/** @param videoId 11자리 영상 ID, @param canonicalUrl 정규화된 시청 URL */
public record ParsedYoutubeUrl(String videoId, String canonicalUrl) {
}
