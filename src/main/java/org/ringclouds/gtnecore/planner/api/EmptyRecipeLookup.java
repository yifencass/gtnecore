package org.ringclouds.gtnecore.planner.api;

import java.util.List;

/**
 * 简化模式配方源：找不到任何配方（isAvailable=false）。
 * 独立拆包且不依赖 GTCEu 时，PlannerEntry.lookup() 换回本实现即可。
 */
public final class EmptyRecipeLookup implements RecipeLookup {

    public static final EmptyRecipeLookup INSTANCE = new EmptyRecipeLookup();

    private EmptyRecipeLookup() {
    }

    @Override
    public List<PlannerRecipe> find(RecipeCategory category, List<PlannerStack> wantedOutputs, List<PlannerStack> providedInputs) {
        return List.of();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
