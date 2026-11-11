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
@Table(name = "recipe_tip")
public class RecipeTip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected RecipeTip() {
    }

    public RecipeTip(String content, int sortOrder) {
        this.content = content;
        this.sortOrder = sortOrder;
    }

    void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public Long getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public String getContent() { return content; }
    public int getSortOrder() { return sortOrder; }
}
