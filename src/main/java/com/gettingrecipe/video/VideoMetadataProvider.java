package com.gettingrecipe.video;

import java.util.Optional;

/** 영상 제목/길이 조회. 외부 API 장애 시에는 빈 값을 돌려주고, 분석 불가 영상이면 예외를 던진다. */
public interface VideoMetadataProvider {

    /**
     * @return 메타데이터. API 키가 없거나 일시 장애면 empty (길이 검증은 건너뛴다)
     * @throws VideoUnavailableException 없는 영상, 비공개/일부 공개, 라이브 등 분석할 수 없는 영상
     */
    Optional<VideoMetadata> fetch(String videoId);
}
