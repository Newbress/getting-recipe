package com.gettingrecipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywaySchemaTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 초기_스키마의_모든_테이블이_생성된다() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "video", "recipe", "recipe_tip", "ingredient", "step", "analysis_job", "feedback");
    }

    @Test
    void 같은_영상의_진행중_분석_작업은_하나만_허용한다() {
        jdbc.update("INSERT INTO video (video_id, url) VALUES ('abc123XYZ_0', 'https://youtu.be/abc123XYZ_0')");
        jdbc.update("INSERT INTO analysis_job (id, video_id, type, status) "
                + "VALUES (gen_random_uuid(), 'abc123XYZ_0', 'ANALYZE', 'PROCESSING')");

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO analysis_job (id, video_id, type, status) "
                        + "VALUES (gen_random_uuid(), 'abc123XYZ_0', 'ANALYZE', 'PENDING')"));
    }
}
