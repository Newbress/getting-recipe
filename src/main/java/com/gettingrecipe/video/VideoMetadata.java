package com.gettingrecipe.video;

/**
 * @param title       영상 제목
 * @param durationSec 영상 길이(초). 알 수 없으면 null
 */
public record VideoMetadata(String title, Integer durationSec) {
}
