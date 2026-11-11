package com.gettingrecipe.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YoutubeDataApiClientTest {

    private static final String BASE = "https://youtube.test";
    private static final String VIDEO_ID = "dQw4w9WgXcQ";

    private MockRestServiceServer server;
    private YoutubeDataApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YoutubeDataApiClient(builder.build(), new YoutubeProperties("yt-key", BASE));
    }

    private static String item(String title, String duration, String privacy, String live) {
        return """
                {"items":[{
                  "snippet":{"title":"%s","liveBroadcastContent":"%s"},
                  "contentDetails":{"duration":"%s"},
                  "status":{"privacyStatus":"%s"}}]}
                """.formatted(title, live, duration, privacy);
    }

    private void respond(String body) {
        server.expect(requestTo(startsWith(BASE + "/youtube/v3/videos")))
                .andExpect(queryParam("id", VIDEO_ID))
                .andExpect(header("x-goog-api-key", "yt-key"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void 제목과_길이를_초_단위로_반환한다() {
        respond(item("김치볶음밥 만들기", "PT1M5S", "public", "none"));

        Optional<VideoMetadata> result = client.fetch(VIDEO_ID);

        assertThat(result).contains(new VideoMetadata("김치볶음밥 만들기", 65));
        server.verify();
    }

    @Test
    void 시간_단위가_있는_길이도_해석한다() {
        respond(item("긴 영상", "PT1H2M3S", "public", "none"));

        assertThat(client.fetch(VIDEO_ID).orElseThrow().durationSec()).isEqualTo(3723);
    }

    @Test
    void 쇼츠처럼_초만_있는_길이도_해석한다() {
        respond(item("쇼츠", "PT45S", "public", "none"));

        assertThat(client.fetch(VIDEO_ID).orElseThrow().durationSec()).isEqualTo(45);
    }

    @Test
    void 없는_영상이면_분석_불가로_처리한다() {
        respond("{\"items\":[]}");

        assertThatThrownBy(() -> client.fetch(VIDEO_ID))
                .isInstanceOf(VideoUnavailableException.class)
                .hasMessageContaining("존재하지 않거나 비공개");
    }

    @Test
    void 공개가_아닌_영상은_분석_불가로_처리한다() {
        respond(item("일부 공개", "PT1M", "unlisted", "none"));

        assertThatThrownBy(() -> client.fetch(VIDEO_ID))
                .isInstanceOf(VideoUnavailableException.class)
                .hasMessageContaining("공개 영상만");
    }

    @Test
    void 라이브_방송은_분석_불가로_처리한다() {
        respond(item("라이브", "PT0S", "public", "live"));

        assertThatThrownBy(() -> client.fetch(VIDEO_ID))
                .isInstanceOf(VideoUnavailableException.class)
                .hasMessageContaining("라이브");
    }

    @Test
    void API_키가_없으면_호출하지_않고_빈_값을_반환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer noCalls = MockRestServiceServer.bindTo(builder).build();
        YoutubeDataApiClient noKey = new YoutubeDataApiClient(builder.build(), new YoutubeProperties("", BASE));

        assertThat(noKey.fetch(VIDEO_ID)).isEmpty();
        noCalls.verify();
    }

    @Test
    void API_장애는_분석을_막지_않고_빈_값을_반환한다() {
        server.expect(requestTo(startsWith(BASE + "/youtube/v3/videos"))).andRespond(withServerError());

        assertThat(client.fetch(VIDEO_ID)).isEmpty();
    }

    @Test
    void 길이_표기가_0초이거나_이상하면_null이다() {
        assertThat(YoutubeDataApiClient.parseDuration("P0D")).isNull();
        assertThat(YoutubeDataApiClient.parseDuration("PT0S")).isNull();
        assertThat(YoutubeDataApiClient.parseDuration("이상한값")).isNull();
        assertThat(YoutubeDataApiClient.parseDuration("")).isNull();
        assertThat(YoutubeDataApiClient.parseDuration(null)).isNull();
        assertThat(YoutubeDataApiClient.parseDuration("PT10M")).isEqualTo(600);
    }
}
