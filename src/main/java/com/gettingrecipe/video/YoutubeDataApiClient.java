package com.gettingrecipe.video;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * YouTube Data API v3 {@code videos.list} 로 제목/길이/공개 여부를 조회한다 (할당량 1 unit).
 * 길이는 단계별 startSec 보정에 쓰이고, 비공개/라이브 영상은 Gemini 호출 전에 걸러낸다.
 */
@Component
public class YoutubeDataApiClient implements VideoMetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(YoutubeDataApiClient.class);

    private final RestClient restClient;
    private final YoutubeProperties props;

    public YoutubeDataApiClient(@Qualifier("youtubeRestClient") RestClient restClient, YoutubeProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    @Override
    public Optional<VideoMetadata> fetch(String videoId) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            log.debug("YOUTUBE_API_KEY 가 없어 영상 메타데이터 조회를 건너뜁니다");
            return Optional.empty();
        }

        JsonNode response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path("/youtube/v3/videos")
                            .queryParam("part", "snippet,contentDetails,status")
                            .queryParam("id", videoId)
                            .build())
                    .header("x-goog-api-key", props.apiKey())          // 키는 헤더로 전달 (URL/로그 노출 방지)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("YouTube Data API 호출 실패 - 메타데이터 없이 진행합니다: {}", e.getMessage());
            return Optional.empty();
        }

        JsonNode item = response == null ? null : response.path("items").path(0);
        if (item == null || item.isMissingNode() || item.isNull()) {
            throw new VideoUnavailableException("존재하지 않거나 비공개인 영상입니다.");
        }
        if (!"public".equals(item.path("status").path("privacyStatus").asText("public"))) {
            throw new VideoUnavailableException("공개 영상만 분석할 수 있습니다.");
        }
        String live = item.path("snippet").path("liveBroadcastContent").asText("none");
        if (live.equals("live") || live.equals("upcoming")) {
            throw new VideoUnavailableException("라이브 방송은 분석할 수 없습니다.");
        }

        String title = item.path("snippet").path("title").asText(null);
        return Optional.of(new VideoMetadata(title, parseDuration(item.path("contentDetails").path("duration").asText(null))));
    }

    /** ISO 8601 (예: PT1M5S) -> 초. 0초이거나 해석할 수 없으면 null. */
    static Integer parseDuration(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            long seconds = Duration.parse(iso).getSeconds();
            return seconds > 0 && seconds <= Integer.MAX_VALUE ? (int) seconds : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
