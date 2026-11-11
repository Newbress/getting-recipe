package com.gettingrecipe.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "step")
public class Step {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    /** 영상에서 이 단계가 시작되는 초. 알 수 없으면 null */
    @Column(name = "start_sec")
    private Integer startSec;

    protected Step() {
    }

    public Step(int order, String description, Integer startSec) {
        this.stepOrder = order;
        this.description = description;
        this.startSec = startSec;
    }

    void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public Long getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public int getOrder() { return stepOrder; }
    public String getDescription() { return description; }
    public Integer getStartSec() { return startSec; }
}
