package com.gettingrecipe.video;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "video")
public class Video {

    @Id
    @Column(name = "video_id", length = 20)
    private String videoId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 500)
    private String title;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Video() {
    }

    public Video(String videoId, String url, String title, Integer durationSec) {
        this.videoId = videoId;
        this.url = url;
        this.title = title;
        this.durationSec = durationSec;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public String getVideoId() { return videoId; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public Integer getDurationSec() { return durationSec; }
    public Instant getCreatedAt() { return createdAt; }
}
