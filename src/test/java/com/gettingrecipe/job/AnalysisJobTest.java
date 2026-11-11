package com.gettingrecipe.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnalysisJobTest {

    @Test
    void 정상_흐름은_PENDING_PROCESSING_COMPLETED_순서다() {
        AnalysisJob job = AnalysisJob.analyze("dQw4w9WgXcQ");
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getType()).isEqualTo(JobType.ANALYZE);
        assertThat(job.getId()).isNotNull();

        job.markProcessing();
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);

        job.complete(7L);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getRecipeId()).isEqualTo(7L);
    }

    @Test
    void 진행중_작업은_실패_처리할_수_있다() {
        AnalysisJob job = AnalysisJob.analyze("dQw4w9WgXcQ");
        job.markProcessing();

        job.fail("응답 파싱 실패");

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("응답 파싱 실패");
    }

    @Test
    void 잘못된_상태_전이는_거절한다() {
        AnalysisJob job = AnalysisJob.analyze("dQw4w9WgXcQ");

        assertThatThrownBy(() -> job.complete(1L)).isInstanceOf(IllegalStateException.class);

        job.markProcessing();
        job.complete(1L);
        assertThatThrownBy(job::markProcessing).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> job.fail("x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 변환과_재생성_작업은_원본_레시피와_요청_내용을_보관한다() {
        AnalysisJob adapt = AnalysisJob.adapt("dQw4w9WgXcQ", 3L, "{\"conditions\":\"에어프라이어\"}");
        AnalysisJob regen = AnalysisJob.regenerate("dQw4w9WgXcQ", 3L, "[]");

        assertThat(adapt.getType()).isEqualTo(JobType.ADAPT);
        assertThat(adapt.getSourceRecipeId()).isEqualTo(3L);
        assertThat(adapt.getRequestPayload()).contains("에어프라이어");
        assertThat(regen.getType()).isEqualTo(JobType.REGENERATE);
    }
}
