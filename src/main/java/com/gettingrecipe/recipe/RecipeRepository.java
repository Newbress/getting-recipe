package com.gettingrecipe.recipe;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /** 영상의 최신 원본 레시피(캐시 조회용). 변환본은 제외한다. */
    Optional<Recipe> findFirstByVideoVideoIdAndParentRecipeIdIsNullOrderByVersionDesc(String videoId);
}
