package com.gettingrecipe.video;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 다양한 형식의 YouTube URL을 videoId로 정규화한다.
 *
 * <p>지원: {@code /shorts/ID}, {@code /watch?v=ID}, {@code youtu.be/ID}, {@code /embed/ID}, {@code /live/ID}.
 * 호스트는 허용 목록과 정확히 일치해야 한다(youtube.com.evil.com 같은 우회 차단).
 */
public final class YoutubeUrlParser {

    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Set<String> YOUTUBE_HOSTS =
            Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com");
    private static final Set<String> PATH_PREFIXES = Set.of("shorts", "embed", "live");
    private static final int MAX_URL_LENGTH = 2048;

    private YoutubeUrlParser() {
    }

    public static ParsedYoutubeUrl parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidYoutubeUrlException("URL을 입력해주세요.");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new InvalidYoutubeUrlException("URL이 너무 깁니다.");
        }

        URI uri = toUri(trimmed);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new InvalidYoutubeUrlException("http(s) URL만 지원합니다.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

        String candidate;
        if (host.equals("youtu.be")) {
            candidate = firstPathSegment(uri.getPath());
        } else if (YOUTUBE_HOSTS.contains(host)) {
            candidate = fromYoutubeDotCom(uri);
        } else {
            throw new InvalidYoutubeUrlException("YouTube URL만 지원합니다.");
        }

        if (candidate == null || !VIDEO_ID.matcher(candidate).matches()) {
            throw new InvalidYoutubeUrlException("YouTube 영상 ID를 찾을 수 없습니다.");
        }
        return new ParsedYoutubeUrl(candidate, "https://www.youtube.com/watch?v=" + candidate);
    }

    private static URI toUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidYoutubeUrlException("올바른 URL 형식이 아닙니다.");
        }
    }

    private static String fromYoutubeDotCom(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.equals("/watch")) {
            return queryParam(uri.getRawQuery(), "v");
        }
        String[] segments = path.split("/");           // "", "shorts", "ID"
        if (segments.length >= 3 && PATH_PREFIXES.contains(segments[1])) {
            return segments[2];
        }
        return null;
    }

    private static String firstPathSegment(String path) {
        if (path == null) {
            return null;
        }
        String[] segments = path.split("/");           // "", "ID"
        return segments.length >= 2 ? segments[1] : null;
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}
