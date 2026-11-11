package com.gettingrecipe.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 비동기 분석 작업. 상태: PENDING -> PROCESSING -> COMPLETED / FAILED */
@Entity
@Table(name = "analysis_job")
public class AnalysisJob {

    @Id
    private UUID id;

    @Column(name = "video_id", nullable = false, length = 20)
    private String videoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "source_recipe_id")
    private Long sourceRecipeId;

    @Column(name = "request_payload", columnDefinition = "text")
    private String requestPayload;

    @Column(name = "recipe_id")
    private Long recipeId;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnalysisJob() {
    }

    private AnalysisJob(String videoId, JobType type, Long sourceRecipeId, String requestPayload) {
        this.id = UUID.randomUUID();
        this.videoId = videoId;
        this.type = type;
        this.status = JobStatus.PENDING;
        this.sourceRecipeId = sourceRecipeId;
        this.requestPayload = requestPayload;
    }

    public static AnalysisJob analyze(String videoId) {
        return new AnalysisJob(videoId, JobType.ANALYZE, null, null);
    }

    public static AnalysisJob regenerate(String videoId, Long sourceRecipeId, String feedbackPayload) {
        return new AnalysisJob(videoId, JobType.REGENERATE, sourceRecipeId, feedbackPayload);
    }

    public static AnalysisJob adapt(String videoId, Long sourceRecipeId, String conditionsPayload) {
        return new AnalysisJob(videoId, JobType.ADAPT, sourceRecipeId, conditionsPayload);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        requireStatus(JobStatus.PENDING);
        this.status = JobStatus.PROCESSING;
    }

    public void complete(Long recipeId) {
        requireStatus(JobStatus.PROCESSING);
        this.status = JobStatus.COMPLETED;
        this.recipeId = recipeId;
    }

    public void fail(String errorMessage) {
        if (!status.isActive()) {
            throw new IllegalStateException("이미 종료된 작업입니다: " + status);
        }
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    private void requireStatus(JobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("잘못된 상태 전이: " + status + " (필요: " + expected + ")");
        }
    }

    public UUID getId() { return id; }
    public String getVideoId() { return videoId; }
    public JobType getType() { return type; }
    public JobStatus getStatus() { return status; }
    public Long getSourceRecipeId() { return sourceRecipeId; }
    public String getRequestPayload() { return requestPayload; }
    public Long getRecipeId() { return recipeId; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
