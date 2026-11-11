package com.gettingrecipe.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gemini 설정 (application.yml 의 {@code gemini.*}).
 *
 * @param apiKey         환경변수 GEMINI_API_KEY 로 주입. 코드/설정 파일에 값을 두지 않는다
 * @param baseUrl        API 서버 주소
 * @param model          모델 ID (예: gemini-3.8-flash)
 * @param timeoutSeconds 응답 대기 시간. 영상 분석은 오래 걸릴 수 있어 넉넉히 둔다
 * @param mock           true 면 실제 호출 없이 {@link MockRecipeAnalyzer} 를 사용 (로컬 개발용)
 */
@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String baseUrl, String model, Integer timeoutSeconds, boolean mock) {

    public GeminiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://generativelanguage.googleapis.com";
        }
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            timeoutSeconds = 120;
        }
    }
}
