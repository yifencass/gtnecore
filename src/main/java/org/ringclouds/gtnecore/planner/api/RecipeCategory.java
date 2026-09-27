package org.ringclouds.gtnecore.planner.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方分类（规划器视角）：动态注册表，id = GTRecipeType 的完整注册名（namespace:path）。
 * 默认内置蒸馏塔/蒸馏机/裂解；适配层（GtceRecipeLookup.ensureCategories）枚举
 * GTRegistries.RECIPE_TYPES 全部类型（含其他 mod 的自定义类型）后自动补齐。
 */
public final class RecipeCategory {

    private static final List<RecipeCategory> ALL = new ArrayList<>();
    private static final Map<String, RecipeCategory> BY_ID = new HashMap<>();

    public static final RecipeCategory DISTILLATION = of("gtceu:distillation_tower", "蒸馏塔");
    public static final RecipeCategory DISTILLERY = of("gtceu:distillery", "蒸馏机");
    public static final RecipeCategory CRACKING = of("gtceu:cracker", "裂解");

    private final String id;
    private final String displayName;

    private RecipeCategory(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** 注册（已存在则返回原实例） */
    public static RecipeCategory of(String id, String displayName) {
        RecipeCategory c = BY_ID.get(id);
        if (c == null) {
            c = new RecipeCategory(id, displayName);
            ALL.add(c);
            BY_ID.put(id, c);
        }
        return c;
    }

    /** 全部已注册分类（含适配层补充的） */
    public static List<RecipeCategory> all() {
        return ALL;
    }

    /** 按 id 查（精确 → path 后缀兼容旧存档），找不到返回第一个 */
    public static RecipeCategory byId(String id) {
        RecipeCategory c = BY_ID.get(id);
        if (c != null) return c;
        for (RecipeCategory x : ALL) {
            if (x.id.endsWith(":" + id)) return x;
        }
        return ALL.isEmpty() ? null : ALL.get(0);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}