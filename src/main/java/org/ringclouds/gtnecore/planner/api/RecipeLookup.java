package org.ringclouds.gtnecore.planner.api;

import java.util.List;

/**
 * 配方查询端口（可分离层）。简化模式/独立拆包：换用 EmptyRecipeLookup，
 * 规划器降级为"无配方数据"，画布仍可打开。
 */
public interface RecipeLookup {

    /**
     * 找配方。优先级：wantedOutputs 非空→按产物反查；
     * 否则 providedInputs 非空→按输入找；否则返回该类全部配方。
     */
    List<PlannerRecipe> find(RecipeCategory category, List<PlannerStack> wantedOutputs, List<PlannerStack> providedInputs);

    /** 是否有真实配方数据源 */
    default boolean isAvailable() {
        return true;
    }
}
