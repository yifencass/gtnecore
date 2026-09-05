package org.ringclouds.gtnecore.planner.api;

import java.util.List;

/**
 * 一份可供规划的配方。接口层零 GTCEu 依赖；
 * GTCEu 的 GTRecipe 由适配层（planner.gtceu 包）实现本接口。
 * 简化模式/独立拆包：可换其他实现或空实现。
 */
public interface PlannerRecipe {

    List<PlannerStack> inputs();

    List<PlannerStack> outputs();

    /** 每秒（tick）耗电，EU/t；正=耗电，负=发电 */
    long eut();

    /** 每秒（tick）发电量，EU/t（恒正；发电机/能量配方专用，与 eut 独立） */
    default long outputEut() {
        return 0;
    }

    /** 时长（tick） */
    int duration();

    /** 所属配方类型 id（对应 RecipeCategory.id） */
    String recipeTypeId();

    /** 展示名（如首个产物名） */
    String displayName();

    /** 单个周期总能量（EUt×时长），正=耗电；溢出饱和到 Long.MAX_VALUE 而非负数 */
    default long totalEU() {
        long a = eut(), b = duration();
        if (a == 0 || b == 0) return 0;
        long r = a * b;
        if (a != r / b) return Long.MAX_VALUE; // 溢出饱和
        return r;
    }

    /** 单个周期总耗电（恒正） */
    default long consumedEU() {
        return Math.max(0, totalEU());
    }

    /** 单个周期总发电（恒正） */
    default long generatedEU() {
        return Math.max(0, -totalEU());
    }
}
