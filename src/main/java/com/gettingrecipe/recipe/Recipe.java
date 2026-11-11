package com.gettingrecipe.recipe;

import com.gettingrecipe.video.Video;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 레시피 집합 루트. 원본(parentRecipeId == null)과 변환본(parentRecipeId != null)을
 * 같은 테이블에 별도 행으로 저장하며, 변환 시 원본을 덮어쓰지 않는다.
 */
@Entity
@Table(name = "recipe")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(length = 500)
    private String title;

    private Integer servings;

    @Column(name = "is_recipe", nullable = false)
    private boolean isRecipe;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "parent_recipe_id")
    private Long parentRecipeId;

    @Column(name = "adapt_conditions", columnDefinition = "text")
    private String adaptConditions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<Ingredient> ingredients = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<Step> steps = new ArrayList<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RecipeTip> tips = new ArrayList<>();

    protected Recipe() {
    }

    private Recipe(Video video, String title, Integer servings, boolean isRecipe,
                   int version, Long parentRecipeId, String adaptConditions) {
        this.video = video;
        this.title = title;
        this.servings = servings;
        this.isRecipe = isRecipe;
        this.version = version;
        this.parentRecipeId = parentRecipeId;
        this.adaptConditions = adaptConditions;
    }

    /** 영상에서 추출한 원본 레시피 */
    public static Recipe original(Video video, String title, Integer servings, boolean isRecipe, int version) {
        return new Recipe(video, title, servings, isRecipe, version, null, null);
    }

    /** 원본을 기반으로 한 변환본(재료 대체/조건 변환). 원본은 변경하지 않는다. */
    public static Recipe adaptedFrom(Recipe parent, String title, Integer servings, String adaptConditions) {
        return new Recipe(parent.video, title, servings, true, 1, parent.id, adaptConditions);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void addIngredient(Ingredient ingredient) {
        ingredient.setRecipe(this);
        ingredients.add(ingredient);
    }

    public void addStep(Step step) {
        step.setRecipe(this);
        steps.add(step);
    }

    public void addTip(RecipeTip tip) {
        tip.setRecipe(this);
        tips.add(tip);
    }

    public boolean isAdapted() { return parentRecipeId != null; }

    public Long getId() { return id; }
    public Video getVideo() { return video; }
    public String getTitle() { return title; }
    public Integer getServings() { return servings; }
    public boolean isRecipe() { return isRecipe; }
    public int getVersion() { return version; }
    public Long getParentRecipeId() { return parentRecipeId; }
    public String getAdaptConditions() { return adaptConditions; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Ingredient> getIngredients() { return ingredients; }
    public List<Step> getSteps() { return steps; }
    public List<RecipeTip> getTips() { return tips; }
}
