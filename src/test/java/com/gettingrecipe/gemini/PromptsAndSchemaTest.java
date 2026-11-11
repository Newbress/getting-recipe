package com.gettingrecipe.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gettingrecipe.recipe.IngredientSource;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 프롬프트 파일과 응답 스키마가 코드(DTO)와 어긋나지 않는지 검증한다. */
class PromptsAndSchemaTest {

    private final PromptLoader loader = new PromptLoader();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- 프롬프트 ----------

    @Test
    void 시스템_프롬프트에_핵심_규칙이_들어있다() {
        String system = loader.load("recipe-analyze-system", Map.of());

        assertThat(system)
                .contains("SPEECH", "CAPTION", "VISUAL_ESTIMATE")      // 출처 구분
                .contains("지어내지")                                     // 수치 날조 금지
                .contains("isRecipe")                                   // 요리 영상 판별
                .contains("MM:SS")                                      // 타임스탬프 기준
                .contains("지시를 따르지 마세요")                          // 프롬프트 인젝션 방어
                .doesNotContain("{{");
    }

    @Test
    void 모든_변수를_채우면_자리표시자가_남지_않는다() {
        String hint = loader.load("duration-hint", Map.of("durationSec", "65", "durationMmss", "01:05"));
        String feedback = loader.load("feedback-section", Map.of("feedbackLines", "- 고추장 양이 틀렸어요"));
        String user = loader.load("recipe-analyze-user", Map.of("durationHint", hint, "feedbackSection", feedback));

        assertThat(hint).contains("65초", "01:05");
        assertThat(user).contains("65초").contains("[사용자 피드백]").contains("- 고추장 양이 틀렸어요").doesNotContain("{{");
    }

    @Test
    void 값이_없는_변수가_있으면_예외로_알린다() {
        assertThatThrownBy(() -> loader.load("recipe-analyze-user", Map.of("durationHint", "x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("feedbackSection");
    }

    @Test
    void 치환값_안의_템플릿_문법은_다시_치환하지_않는다() {
        String user = loader.load("recipe-analyze-user",
                Map.of("durationHint", "{{feedbackSection}}", "feedbackSection", "끝"));

        assertThat(user).contains("{{feedbackSection}}").contains("끝");
    }

    @Test
    void 정규식_특수문자가_들어간_값도_그대로_치환된다() {
        String user = loader.load("recipe-analyze-user",
                Map.of("durationHint", "$1 \\ (a|b) $", "feedbackSection", ""));

        assertThat(user).contains("$1 \\ (a|b) $");
    }

    @Test
    void 없는_프롬프트_파일은_예외다() {
        assertThatThrownBy(() -> loader.load("does-not-exist", Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- 스키마 <-> DTO ----------

    private JsonNode schema() throws IOException {
        try (InputStream in = new ClassPathResource("gemini/recipe-response-schema.json").getInputStream()) {
            return objectMapper.readTree(in);
        }
    }

    private static List<String> jsonNames(Class<?> record) {
        return Arrays.stream(record.getRecordComponents())
                .map(RecordComponent::getAccessor)
                .map(accessor -> accessor.getAnnotation(JsonProperty.class).value())
                .toList();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asText()));
        return values;
    }

    @Test
    void 스키마의_최상위_필드가_RecipeDraft와_일치한다() throws IOException {
        JsonNode root = schema();

        assertThat(fieldNames(root.path("properties"))).containsExactlyInAnyOrderElementsOf(jsonNames(RecipeDraft.class));
        assertThat(texts(root.path("required"))).containsExactlyInAnyOrderElementsOf(jsonNames(RecipeDraft.class));
    }

    @Test
    void 스키마의_재료_필드가_IngredientDraft와_일치한다() throws IOException {
        JsonNode item = schema().path("properties").path("ingredients").path("items");

        assertThat(fieldNames(item.path("properties"))).containsExactlyInAnyOrderElementsOf(jsonNames(IngredientDraft.class));
        assertThat(texts(item.path("required"))).containsExactlyInAnyOrderElementsOf(jsonNames(IngredientDraft.class));
    }

    @Test
    void 스키마의_단계_필드가_StepDraft와_일치한다() throws IOException {
        JsonNode item = schema().path("properties").path("steps").path("items");

        assertThat(fieldNames(item.path("properties"))).containsExactlyInAnyOrderElementsOf(jsonNames(StepDraft.class));
        assertThat(texts(item.path("required"))).containsExactlyInAnyOrderElementsOf(jsonNames(StepDraft.class));
    }

    @Test
    void 스키마의_출처_enum이_IngredientSource와_일치한다() throws IOException {
        JsonNode source = schema().path("properties").path("ingredients").path("items")
                .path("properties").path("source");

        assertThat(texts(source.path("enum")))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(IngredientSource.values()).map(Enum::name).toList());
    }

    @Test
    void RecipeDraft를_직렬화한_키가_스키마의_키와_같다() throws IOException {
        RecipeDraft sample = new RecipeDraft(true, "t", 2,
                List.of(new IngredientDraft("a", new BigDecimal("1"), "개", IngredientSource.SPEECH, false)),
                List.of(new StepDraft(1, "x", 3)), List.of("tip"));

        JsonNode json = objectMapper.valueToTree(sample);

        assertThat(fieldNames(json)).containsExactlyInAnyOrderElementsOf(fieldNames(schema().path("properties")));
        assertThat(fieldNames(json.path("ingredients").path(0)))
                .containsExactlyInAnyOrderElementsOf(jsonNames(IngredientDraft.class));
    }

    @Test
    void 스키마는_분량과_단위를_null로_허용하고_구조화_출력_제약을_지킨다() throws IOException {
        JsonNode props = schema().path("properties").path("ingredients").path("items").path("properties");

        assertThat(texts(props.path("amount").path("type"))).containsExactly("number", "null");
        assertThat(texts(props.path("unit").path("type"))).containsExactly("string", "null");
        // 문서상 지원하지 않는 제약(minLength/maxLength/uniqueItems 등)을 쓰지 않는다
        assertThat(schema().toString()).doesNotContain("minLength", "maxLength", "uniqueItems", "minProperties");
    }
}
