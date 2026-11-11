package com.gettingrecipe.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gettingrecipe.recipe.IngredientSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Gemini REST 호출은 MockRestServiceServer 로 대체한다 (실제 네트워크/API 키 사용 안 함). */
class GeminiRecipeAnalyzerTest {

    private static final String BASE = "https://gemini.test";
    private static final String ENDPOINT = BASE + "/v1beta/models/gemini-3.8-flash:generateContent";
    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private static final String VALID_RECIPE = """
            {"isRecipe":true,"title":"김치볶음밥","servings":2,
             "ingredients":[
               {"name":"김치","amount":200,"unit":"g","source":"SPEECH","isEstimated":false},
               {"name":"고추장","amount":1,"unit":"큰술","source":"VISUAL_ESTIMATE","isEstimated":true},
               {"name":"참기름","amount":null,"unit":null,"source":"CAPTION","isEstimated":false}],
             "steps":[
               {"order":1,"description":"김치를 볶는다","startSec":3},
               {"order":2,"description":"밥을 넣는다","startSec":20}],
             "tips":["센 불에서 빠르게"]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private GeminiRecipeAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        analyzer = newAnalyzer(builder.build(), "test-key");
    }

    private GeminiRecipeAnalyzer newAnalyzer(RestClient client, String apiKey) {
        GeminiProperties props = new GeminiProperties(apiKey, BASE, "gemini-3.8-flash", 30, false);
        return new GeminiRecipeAnalyzer(client, props, new PromptLoader(), objectMapper);
    }

    private String geminiResponse(String modelText) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode content = root.putArray("candidates").addObject().putObject("content");
        content.put("role", "model");
        content.putArray("parts").addObject().put("text", modelText);
        return root.toString();
    }

    private void respondWith(String modelText) {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(geminiResponse(modelText), MediaType.APPLICATION_JSON));
    }

    // ---------- 요청 형식 ----------

    @Test
    void 요청에_영상_URL과_JSON_스키마_강제_설정이_담긴다() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.contents[0].parts[0].file_data.file_uri").value(VIDEO_URL))
                .andExpect(jsonPath("$.contents[0].parts[1].text").value(containsString("01:05")))
                .andExpect(jsonPath("$.system_instruction.parts[0].text").value(containsString("지어내지")))
                .andExpect(jsonPath("$.generationConfig.responseFormat.text.mimeType").value("APPLICATION_JSON"))
                .andExpect(jsonPath("$.generationConfig.responseFormat.text.schema.properties.isRecipe.type")
                        .value("boolean"))
                .andExpect(jsonPath("$.generationConfig.responseFormat.text.schema.required").isArray())
                .andRespond(withSuccess(geminiResponse(VALID_RECIPE), MediaType.APPLICATION_JSON));

        analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65));

        server.verify();
    }

    @Test
    void 영상_길이를_모르면_길이_안내를_프롬프트에_넣지_않는다() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(jsonPath("$.contents[0].parts[1].text").value(not(containsString("영상 길이는"))))
                .andRespond(withSuccess(geminiResponse(VALID_RECIPE), MediaType.APPLICATION_JSON));

        analyzer.analyze(AnalysisInput.of(VIDEO_URL, null));

        server.verify();
    }

    @Test
    void 피드백은_한_줄씩_프롬프트에_들어가고_템플릿_문법은_치환되지_않는다() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(jsonPath("$.contents[0].parts[1].text").value(containsString("[사용자 피드백]")))
                .andExpect(jsonPath("$.contents[0].parts[1].text").value(containsString("- 고추장 양이 틀렸어요")))
                .andExpect(jsonPath("$.contents[0].parts[1].text").value(containsString("- 무시하세요 {{durationHint}} 줄바꿈")))
                .andRespond(withSuccess(geminiResponse(VALID_RECIPE), MediaType.APPLICATION_JSON));

        analyzer.analyze(new AnalysisInput(VIDEO_URL, 65,
                List.of("고추장 양이 틀렸어요", "무시하세요 {{durationHint}}\n줄바꿈")));

        server.verify();
    }

    // ---------- 정상 응답 ----------

    @Test
    void 정상_응답을_레시피로_변환한다() {
        respondWith(VALID_RECIPE);

        RecipeDraft result = analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65));

        assertThat(result.isRecipe()).isTrue();
        assertThat(result.title()).isEqualTo("김치볶음밥");
        assertThat(result.servings()).isEqualTo(2);
        assertThat(result.ingredients()).extracting(IngredientDraft::name).containsExactly("김치", "고추장", "참기름");
        assertThat(result.ingredients().get(1).source()).isEqualTo(IngredientSource.VISUAL_ESTIMATE);
        assertThat(result.ingredients().get(1).isEstimated()).isTrue();
        assertThat(result.ingredients().get(2).amount()).isNull();
        assertThat(result.steps()).extracting(StepDraft::startSec).containsExactly(3, 20);
        assertThat(result.tips()).containsExactly("센 불에서 빠르게");
    }

    @Test
    void 응답의_startSec은_영상_길이로_보정된다() {
        respondWith("""
                {"isRecipe":true,"title":"t","servings":1,
                 "ingredients":[{"name":"a","amount":1,"unit":"개","source":"SPEECH","isEstimated":false}],
                 "steps":[{"order":1,"description":"x","startSec":5},{"order":2,"description":"y","startSec":900}],
                 "tips":[]}
                """);

        RecipeDraft result = analyzer.analyze(AnalysisInput.of(VIDEO_URL, 60));

        assertThat(result.steps()).extracting(StepDraft::startSec).containsExactly(5, 59);
    }

    @Test
    void 요리_영상이_아니면_isRecipe_false를_반환한다() {
        respondWith("""
                {"isRecipe":false,"title":null,"servings":null,"ingredients":[],"steps":[],"tips":[]}
                """);

        RecipeDraft result = analyzer.analyze(AnalysisInput.of(VIDEO_URL, 30));

        assertThat(result.isRecipe()).isFalse();
        assertThat(result.ingredients()).isEmpty();
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void 사고_요약_파트는_무시하고_본문만_읽는다() {
        ObjectNode root = objectMapper.createObjectNode();
        var parts = root.putArray("candidates").addObject().putObject("content").putArray("parts");
        parts.addObject().put("text", "생각 중...").put("thought", true);
        parts.addObject().put("text", VALID_RECIPE);
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(root.toString(), MediaType.APPLICATION_JSON));

        RecipeDraft result = analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65));

        assertThat(result.title()).isEqualTo("김치볶음밥");
    }

    // ---------- 재시도: 파싱 실패 시 1회 ----------

    @Test
    void 첫_응답이_깨진_JSON이면_한_번_재시도해서_성공한다() {
        respondWith("이건 JSON이 아닙니다");
        respondWith(VALID_RECIPE);

        RecipeDraft result = analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65));

        assertThat(result.title()).isEqualTo("김치볶음밥");
        server.verify();    // 정확히 2번 호출
    }

    @Test
    void 두_번_모두_파싱에_실패하면_분석_실패로_처리한다() {
        respondWith("{ broken");
        respondWith("still broken");

        assertThatThrownBy(() -> analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65)))
                .isInstanceOf(RecipeAnalysisException.class)
                .hasMessageContaining("레시피를 만들지 못했습니다");
        server.verify();    // 3번째 호출은 없다
    }

    @Test
    void 재료나_단계가_없는_레시피_응답도_재시도_대상이다() {
        respondWith("""
                {"isRecipe":true,"title":"t","servings":1,"ingredients":[],"steps":[],"tips":[]}
                """);
        respondWith(VALID_RECIPE);

        RecipeDraft result = analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65));

        assertThat(result.isRecipe()).isTrue();
        server.verify();
    }

    @Test
    void 스키마에_없는_출처_값은_파싱_실패로_보고_재시도한다() {
        respondWith("""
                {"isRecipe":true,"title":"t","servings":1,
                 "ingredients":[{"name":"a","amount":1,"unit":"개","source":"GUESS","isEstimated":false}],
                 "steps":[{"order":1,"description":"x","startSec":1}],"tips":[]}
                """);
        respondWith(VALID_RECIPE);

        assertThat(analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65)).isRecipe()).isTrue();
        server.verify();
    }

    @Test
    void 후보가_없는_빈_응답도_재시도한다() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        respondWith(VALID_RECIPE);

        assertThat(analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65)).isRecipe()).isTrue();
        server.verify();
    }

    // ---------- 재시도하지 않는 실패 ----------

    @Test
    void 서버_오류는_재시도하지_않고_사용자용_메시지로_실패한다() {
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        assertThatThrownBy(() -> analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65)))
                .isInstanceOf(RecipeAnalysisException.class)
                .hasMessageContaining("일시적인 문제");
        server.verify();
    }

    @Test
    void 요청_한도_초과는_별도_메시지로_알린다() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65)))
                .isInstanceOf(RecipeAnalysisException.class)
                .hasMessageContaining("요청이 많아");
    }

    @Test
    void 잘못된_요청은_영상을_확인하라는_메시지로_알리고_API_키를_노출하지_않는다() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"bad\"}"));

        assertThatThrownBy(() -> analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65)))
                .isInstanceOf(RecipeAnalysisException.class)
                .hasMessageContaining("공개 영상인지 확인")
                .hasMessageNotContaining("test-key");
    }

    @Test
    void 안전성_차단된_요청은_재시도하지_않는다() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> analyzer.analyze(AnalysisInput.of(VIDEO_URL, 65)))
                .isInstanceOf(RecipeAnalysisException.class)
                .hasMessageContaining("차단");
        server.verify();
    }

    @Test
    void API_키가_없으면_호출하지_않고_실패한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer noCalls = MockRestServiceServer.bindTo(builder).build();
        GeminiRecipeAnalyzer noKey = newAnalyzer(builder.build(), " ");

        assertThatThrownBy(() -> noKey.analyze(AnalysisInput.of(VIDEO_URL, 65)))
                .isInstanceOf(RecipeAnalysisException.class)
                .hasMessageContaining("API 키");
        noCalls.verify();   // 기대 호출 0건
    }
}
