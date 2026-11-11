package com.gettingrecipe.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini generateContent REST API 로 YouTube 영상을 직접 분석한다.
 *
 * <ul>
 *   <li>영상은 {@code file_data.file_uri} 에 YouTube URL 을 넣어 전달 (공개 영상만 가능)</li>
 *   <li>출력은 {@code generationConfig.responseFormat} 의 JSON 스키마로 강제</li>
 *   <li>응답 파싱/검증 실패 시 1회 재시도 후 {@link RecipeAnalysisException}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "gemini", name = "mock", havingValue = "false", matchIfMissing = true)
public class GeminiRecipeAnalyzer implements RecipeAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GeminiRecipeAnalyzer.class);

    static final int MAX_ATTEMPTS = 2;                 // 최초 1회 + 재시도 1회
    private static final int MAX_FEEDBACK_NOTES = 20;
    private static final int MAX_FEEDBACK_LENGTH = 500;

    private final RestClient restClient;
    private final GeminiProperties props;
    private final PromptLoader prompts;
    private final ObjectMapper objectMapper;
    private final JsonNode responseSchema;

    public GeminiRecipeAnalyzer(@Qualifier("geminiRestClient") RestClient restClient,
                                GeminiProperties props,
                                PromptLoader prompts,
                                ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.props = props;
        this.prompts = prompts;
        this.objectMapper = objectMapper;
        this.responseSchema = loadSchema();
    }

    @Override
    public RecipeDraft analyze(AnalysisInput input) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new RecipeAnalysisException("서버에 Gemini API 키가 설정되지 않았습니다.");
        }
        ObjectNode request = buildRequest(input);

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String text = extractText(callGemini(request));
            try {
                RecipeDraft raw = objectMapper.readValue(text, RecipeDraft.class);
                return RecipeDraftSanitizer.sanitize(raw, input.durationSec());
            } catch (JsonProcessingException | RecipeDraftInvalidException e) {
                last = e;
                log.warn("Gemini 응답을 사용할 수 없습니다 (시도 {}/{}): {}", attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
        throw new RecipeAnalysisException("레시피를 만들지 못했습니다. 잠시 후 다시 시도해주세요.", last);
    }

    // ---------- 요청 ----------

    ObjectNode buildRequest(AnalysisInput input) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("system_instruction").putArray("parts").addObject()
                .put("text", prompts.load("recipe-analyze-system", Map.of()));

        ArrayNode parts = body.putArray("contents").addObject().putArray("parts");
        parts.addObject().putObject("file_data").put("file_uri", input.videoUrl());   // 영상을 먼저, 텍스트를 뒤에
        parts.addObject().put("text", buildUserPrompt(input));

        ObjectNode text = body.putObject("generationConfig").putObject("responseFormat").putObject("text");
        text.put("mimeType", "APPLICATION_JSON");
        text.set("schema", responseSchema);
        return body;
    }

    private String buildUserPrompt(AnalysisInput input) {
        String durationHint = "";
        if (input.durationSec() != null) {
            int sec = input.durationSec();
            durationHint = prompts.load("duration-hint", Map.of(
                    "durationSec", String.valueOf(sec),
                    "durationMmss", String.format("%02d:%02d", sec / 60, sec % 60)));
        }
        String feedbackSection = "";
        if (!input.feedbackNotes().isEmpty()) {
            String lines = input.feedbackNotes().stream()
                    .limit(MAX_FEEDBACK_NOTES)
                    .map(GeminiRecipeAnalyzer::formatFeedbackLine)
                    .collect(Collectors.joining("\n"));
            feedbackSection = prompts.load("feedback-section", Map.of("feedbackLines", lines));
        }
        String prompt = prompts.load("recipe-analyze-user",
                Map.of("durationHint", durationHint, "feedbackSection", feedbackSection));
        return prompt.replaceAll("\n{3,}", "\n\n");
    }

    private static String formatFeedbackLine(String note) {
        String oneLine = note.replaceAll("\\s+", " ").strip();
        if (oneLine.length() > MAX_FEEDBACK_LENGTH) {
            oneLine = oneLine.substring(0, MAX_FEEDBACK_LENGTH);
        }
        return "- " + oneLine;
    }

    // ---------- 호출/응답 ----------

    private JsonNode callGemini(ObjectNode request) {
        try {
            return restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", props.model())
                    .header("x-goog-api-key", props.apiKey())          // URL 이 아닌 헤더로 전달 (로그 노출 방지)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Gemini API 오류 status={} body={}", status, abbreviate(e.getResponseBodyAsString()));
            throw new RecipeAnalysisException(userMessageFor(status), e);
        } catch (RestClientException e) {
            log.warn("Gemini API 호출 실패: {}", e.getMessage());
            throw new RecipeAnalysisException("Gemini 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.", e);
        }
    }

    private static String userMessageFor(int status) {
        if (status == 429) {
            return "요청이 많아 분석할 수 없습니다. 잠시 후 다시 시도해주세요.";
        }
        if (status >= 500) {
            return "Gemini 서버에 일시적인 문제가 있습니다. 잠시 후 다시 시도해주세요.";
        }
        return "이 영상을 분석하지 못했습니다. 공개 영상인지 확인한 뒤 다시 시도해주세요.";
    }

    /** 응답에서 모델이 생성한 JSON 텍스트를 꺼낸다. 없으면 빈 문자열(-> 파싱 실패로 재시도). */
    private String extractText(JsonNode response) {
        if (response == null) {
            return "";
        }
        String blockReason = response.path("promptFeedback").path("blockReason").asText("");
        if (!blockReason.isBlank()) {
            log.warn("Gemini 요청이 차단되었습니다: {}", blockReason);
            throw new RecipeAnalysisException("이 영상은 분석 요청이 차단되었습니다. 다른 영상으로 시도해주세요.");
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) {
                continue;                                              // 사고 요약 파트는 제외
            }
            if (part.hasNonNull("text")) {
                sb.append(part.get("text").asText());
            }
        }
        return sb.toString();
    }

    private JsonNode loadSchema() {
        try (InputStream in = new ClassPathResource("gemini/recipe-response-schema.json").getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("응답 스키마를 읽을 수 없습니다", e);
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
