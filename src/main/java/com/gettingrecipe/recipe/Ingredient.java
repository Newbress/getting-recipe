package com.gettingrecipe.recipe;

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
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ingredient")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = false, length = 200)
    private String name;

    /** 알 수 없으면 null */
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 50)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IngredientSource source;

    @Column(name = "is_estimated", nullable = false)
    private boolean isEstimated;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Ingredient() {
    }

    public Ingredient(String name, BigDecimal amount, String unit, IngredientSource source,
                      boolean isEstimated, int sortOrder) {
        this.name = name;
        this.amount = amount;
        this.unit = unit;
        this.source = source;
        this.isEstimated = isEstimated;
        this.sortOrder = sortOrder;
    }

    void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public Long getId() { return id; }
    public Recipe getRecipe() { return recipe; }
    public String getName() { return name; }
    public BigDecimal getAmount() { return amount; }
    public String getUnit() { return unit; }
    public IngredientSource getSource() { return source; }
    public boolean isEstimated() { return isEstimated; }
    public int getSortOrder() { return sortOrder; }
}
