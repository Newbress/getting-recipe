package com.gettingrecipe.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class YoutubeUrlParserTest {

    private static final String ID = "dQw4w9WgXcQ";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://youtube.com/shorts/dQw4w9WgXcQ?feature=share",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&list=PL123",
            "https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?si=abcdef",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=dQw4w9WgXcQ",
            "  https://youtu.be/dQw4w9WgXcQ  "
    })
    void 지원하는_모든_형식에서_videoId를_추출한다(String url) {
        ParsedYoutubeUrl parsed = YoutubeUrlParser.parse(url);

        assertThat(parsed.videoId()).isEqualTo(ID);
        assertThat(parsed.canonicalUrl()).isEqualTo("https://www.youtube.com/watch?v=" + ID);
    }

    @Test
    void 하이픈과_언더스코어가_포함된_ID도_허용한다() {
        assertThat(YoutubeUrlParser.parse("https://youtu.be/a-B_c1D2e3F").videoId()).isEqualTo("a-B_c1D2e3F");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://vimeo.com/123456789",
            "https://www.google.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://evil.com/youtube.com/watch?v=dQw4w9WgXcQ",
            "https://notyoutu.be/dQw4w9WgXcQ",
            "https://youtube.com@evil.com/watch?v=dQw4w9WgXcQ"
    })
    void YouTube가_아닌_호스트는_거절한다(String url) {
        assertThatThrownBy(() -> YoutubeUrlParser.parse(url))
                .isInstanceOf(InvalidYoutubeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?v=",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQtoolong",
            "https://www.youtube.com/watch?v=dQw4w9WgXc!",
            "https://www.youtube.com/shorts/",
            "https://www.youtube.com/channel/UC123",
            "https://www.youtube.com/playlist?list=PL123",
            "https://youtu.be/",
            "https://www.youtube.com/watch?x=dQw4w9WgXcQ"
    })
    void 영상_ID가_없거나_형식이_틀리면_거절한다(String url) {
        assertThatThrownBy(() -> YoutubeUrlParser.parse(url))
                .isInstanceOf(InvalidYoutubeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "ftp://youtube.com/watch?v=dQw4w9WgXcQ",
            "file:///etc/passwd",
            "not a url",
            "youtube.com/watch?v=dQw4w9WgXcQ"
    })
    void http가_아니거나_URL이_아니면_거절한다(String url) {
        assertThatThrownBy(() -> YoutubeUrlParser.parse(url))
                .isInstanceOf(InvalidYoutubeUrlException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 비어있는_입력은_거절한다(String url) {
        assertThatThrownBy(() -> YoutubeUrlParser.parse(url))
                .isInstanceOf(InvalidYoutubeUrlException.class);
    }

    @Test
    void 너무_긴_URL은_거절한다() {
        String url = "https://www.youtube.com/watch?v=" + ID + "&x=" + "a".repeat(3000);

        assertThatThrownBy(() -> YoutubeUrlParser.parse(url))
                .isInstanceOf(InvalidYoutubeUrlException.class);
    }
}
