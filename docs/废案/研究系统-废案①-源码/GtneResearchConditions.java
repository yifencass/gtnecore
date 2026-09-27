package org.ringclouds.gtnecore.research;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.resources.ResourceLocation;

import org.ringclouds.gtnecore.Gtnecore;

/**
 * 自定义配方条件类型的注册。
 *
 * <h2>注册时机（已用字节码核实 ✅）</h2>
 * {@code GTRecipeConditions} 的 {@code <clinit>} 流程：
 * <pre>
 * RECIPE_CONDITIONS.unfreeze()
 *   → 注册官方条件（biome / dimension / cleanroom / 官方 research / ...）
 *   → ModLoader.postEvent(GTCEuAPI.RegisterEvent&lt;RecipeConditionType&gt;)   ← 我们在这一步注册
 *   → RECIPE_CONDITIONS.freeze()
 * </pre>
 *
 * 与 {@code gtceu:recipe_capability} 完全同构（见「人造恒星-废案一号」§二·坑 4）。
 * 所以走 Forge 的 generic listener，与 {@code Gtnecore.java} 里现有的
 * {@code addGenericListener(GTRecipeType.class, ...)} 并列即可。
 *
 * <p>⚠ {@code IGTAddon} 里**没有** {@code registerRecipeConditions()} 钩子
 * （已核实：只有 registerCovers / registerRecipeCapabilities / registerElements / ...），
 * 所以不能走 addon hook。
 */
public final class GtneResearchConditions {

    /** {@code gtnecore:research} 条件类型。 */
    public static RecipeConditionType<GtneResearchCondition> RESEARCH;

    private GtneResearchConditions() {
    }

    /** 注册入口（由 {@code Gtnecore} 的 generic listener 调用）。 */
    public static void onRegister(GTCEuAPI.RegisterEvent<ResourceLocation, RecipeConditionType<?>> event) {
        RESEARCH = register("research", GtneResearchCondition::new, GtneResearchCondition.CODEC);
    }

    private static <T extends com.gregtechceu.gtceu.api.recipe.RecipeCondition<T>>
    RecipeConditionType<T> register(String name,
                                    RecipeConditionType.ConditionFactory<T> factory,
                                    com.mojang.serialization.Codec<T> codec) {
        RecipeConditionType<T> type = new RecipeConditionType<>(factory, codec);
        GTRegistries.RECIPE_CONDITIONS.register(name, type);
        return type;
    }
}
