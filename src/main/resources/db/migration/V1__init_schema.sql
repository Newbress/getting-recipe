-- 초기 스키마 (인증/사용자 관련 테이블은 별도 마이그레이션에서 추가한다)

CREATE TABLE video (
    video_id     VARCHAR(20)  PRIMARY KEY,
    url          VARCHAR(500) NOT NULL,
    title        VARCHAR(500),
    duration_sec INTEGER,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE recipe (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    video_id          VARCHAR(20)  NOT NULL REFERENCES video (video_id),
    title             VARCHAR(500),
    servings          INTEGER,
    is_recipe         BOOLEAN      NOT NULL,
    -- 재생성할 때마다 증가. 변환본(parent_recipe_id 있음)은 1부터 시작
    version           INTEGER      NOT NULL DEFAULT 1,
    -- 변환본일 때 원본 레시피 id. 원본은 NULL
    parent_recipe_id  BIGINT       REFERENCES recipe (id),
    -- 변환 조건(조리도구/식단 등). 원본은 NULL
    adapt_conditions  TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 영상당 원본 레시피 버전은 하나씩만 존재
CREATE UNIQUE INDEX ux_recipe_original_version
    ON recipe (video_id, version) WHERE parent_recipe_id IS NULL;
CREATE INDEX ix_recipe_parent ON recipe (parent_recipe_id);

-- tips: 피드백 대상(targetId)으로 지정해야 하므로 jsonb 대신 별도 테이블로 둔다
CREATE TABLE recipe_tip (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipe_id  BIGINT       NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    content    TEXT         NOT NULL,
    sort_order INTEGER      NOT NULL
);
CREATE INDEX ix_recipe_tip_recipe ON recipe_tip (recipe_id);

CREATE TABLE ingredient (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipe_id    BIGINT        NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    name         VARCHAR(200)  NOT NULL,
    amount       NUMERIC(10,2),               -- 알 수 없으면 NULL
    unit         VARCHAR(50),
    source       VARCHAR(20)   NOT NULL,      -- SPEECH | CAPTION | VISUAL_ESTIMATE
    is_estimated BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order   INTEGER       NOT NULL,
    CONSTRAINT ck_ingredient_source CHECK (source IN ('SPEECH', 'CAPTION', 'VISUAL_ESTIMATE'))
);
CREATE INDEX ix_ingredient_recipe ON ingredient (recipe_id);

CREATE TABLE step (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipe_id   BIGINT   NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    step_order  INTEGER  NOT NULL,
    description TEXT     NOT NULL,
    start_sec   INTEGER,
    CONSTRAINT ck_step_start_sec CHECK (start_sec IS NULL OR start_sec >= 0)
);
CREATE INDEX ix_step_recipe ON step (recipe_id);

-- type: ANALYZE(최초 분석) | REGENERATE(피드백 반영 재생성) | ADAPT(조건 변환)
-- status: PENDING -> PROCESSING -> COMPLETED / FAILED
CREATE TABLE analysis_job (
    id               UUID         PRIMARY KEY,
    video_id         VARCHAR(20)  NOT NULL REFERENCES video (video_id),
    type             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    source_recipe_id BIGINT       REFERENCES recipe (id),   -- REGENERATE/ADAPT 대상
    request_payload  TEXT,                                  -- 변환 조건 등 요청 내용
    recipe_id        BIGINT       REFERENCES recipe (id),   -- 결과 레시피
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_job_type   CHECK (type IN ('ANALYZE', 'REGENERATE', 'ADAPT')),
    CONSTRAINT ck_job_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

-- 같은 영상에 대한 분석/재생성 작업은 동시에 하나만 진행(중복 Gemini 호출 방지)
CREATE UNIQUE INDEX ux_job_active_per_video
    ON analysis_job (video_id)
    WHERE type IN ('ANALYZE', 'REGENERATE') AND status IN ('PENDING', 'PROCESSING');

CREATE TABLE feedback (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipe_id   BIGINT       NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    target_type VARCHAR(20)  NOT NULL,   -- INGREDIENT | STEP | TIP | OVERALL
    target_id   BIGINT,                  -- OVERALL이면 NULL
    comment     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_feedback_target CHECK (target_type IN ('INGREDIENT', 'STEP', 'TIP', 'OVERALL'))
);
CREATE INDEX ix_feedback_recipe ON feedback (recipe_id);
