package com.gettingrecipe.feedback;

import com.gettingrecipe.recipe.Recipe;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private FeedbackTargetType targetType;

    /** OVERALL이면 null */
    @Column(name = "target_id")
    private Long targetId;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Feedback() {
    }

    public Feedback(Recipe recipe, FeedbackTargetType targetType, Long targetId, String comment) {
        this.recipe = recipe;
        this.targetType = targetType;
        this.targetId = targetId;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public FeedbackTargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
