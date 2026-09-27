package org.ringclouds.gtnecore.research;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.Nullable;
import org.ringclouds.gtnecore.Gtnecore;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按**配方 ID** 把已加载的配方归类进研究条目，并挂上研究条件。
 *
 * <h2>这是两段式的第二段</h2>
 * 第一段是 {@link GtneResearch#register}（启动期注册研究条目 + 它管哪些配方 id）。
 * 本类在**服务端**扫配方管理器，对每条配方问一句"你归哪个研究"，
 * 命中的就补一个 {@link GtneResearchCondition}。
 *
 * <p><b>配方本体不需要任何改动</b>——这是相对旧方案（让配方自带 {@code addData} 键）
 * 的主要优势，也是用户点出的"接 KubeJS 方便"的落点。
 *
 * <h2>为什么必须在服务端</h2>
 * {@code recipe.id}（{@link ResourceLocation}）**只在服务端存在**——客户端收到的配方
 * 没有 id。所以归类只能在服务端做；挂上的条件会随配方一起同步到客户端，
 * JEI 因此也能显示"需要研究"（tooltip 由 {@code getTooltips()} 提供，
 * GTCEu 的配方页对所有 {@code RecipeCondition} 一视同仁地渲染）。
 *
 * <h2>生命周期（照 {@code VacuumRecipeInjector}）</h2>
 * <ul>
 * <li>{@code ServerAboutToStart} —— 服务器启动，配方已加载</li>
 * <li>{@code TagsUpdated(SERVER_DATA_LOAD)} —— {@code /reload} 之后重新归类</li>
 * <li>{@code AddReloadListenerEvent} —— 重载前清掉「已处理」标记</li>
 * </ul>
 *
 * <h2>⚠ 配方条件列表可能是不可变的</h2>
 * JSON 反序列化出来的配方，其 {@code conditions} 是 {@code List.copyOf} 的不可变列表，
 * 直接 {@code add} 会抛 {@code UnsupportedOperationException}。
 * 处理办法与 {@code VacuumRecipeInjector} 一致：原地写失败 → 反射换可变副本
 * （{@code GTRecipe.conditions} 是 public final 字段，Java 17 允许对其写入）。
 */
public final class ResearchRecipeInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger("GTNEcore[研究归类]");

    /**
     * 可选：配方自带的"我归哪个研究"数据键。
     *
     * <p>两段式之后**不再需要**它（按 id 归类就够了），但保留这个逃生口：
     * 万一某条配方 id 会变、或想在不改研究定义的情况下单独指定，写这个键即可，
     * 优先级高于 id 归类。
     */
    public static final String DATA_KEY = "gtnecore:research";

    private static MinecraftServer lastInjectedForServer = null;
    private static Field conditionsField = null;

    private ResearchRecipeInjector() {
    }

    // ==================== 驱动方式 ====================

    // ⚠ 本类**刻意不自己监听事件**。
    // 归类（GtneResearchRecipeAssignments）必须严格早于注入，而两个类各自监听
    // 同一个事件时执行顺序不确定 —— 一旦注入先跑，那一轮就锁不住任何配方。
    // 所以事件由 GtneResearchRecipeAssignments 统一接管，按 清标记 → 归类 → 注入 调用。

    /** 清掉「本服务器实例已处理过」标记，让下一次 {@link #inject()} 真的重新扫。 */
    public static void resetInjectedFlag() {
        lastInjectedForServer = null;
    }

    // ==================== 归类 + 注入 ====================

    /** 执行归类。任何一环为 0 都能从日志立刻看出来。 */
    public static void inject() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // 主菜单阶段的 TagsUpdated（客户端侧）会走到这里，属正常
            return;
        }
        if (lastInjectedForServer == server) {
            return;
        }
        lastInjectedForServer = server;

        int scanned = 0;
        int classified = 0;
        int patched = 0;
        int already = 0;
        int failed = 0;
        // 按研究分组记录命中的配方，日志一目了然，也便于核对清单
        Map<ResourceLocation, List<String>> byResearch = new LinkedHashMap<>();

        for (var recipeType : com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_TYPES) {
            for (GTRecipe recipe : server.getRecipeManager().getAllRecipesFor(recipeType)) {
                scanned++;
                GtneResearch.ResearchCategory research = researchOf(recipe);
                if (research == null) {
                    continue;
                }
                classified++;
                byResearch.computeIfAbsent(research.id(), k -> new ArrayList<>())
                        .add(recipe.id.toString());
                if (hasResearchCondition(recipe)) {
                    already++;
                    continue;
                }
                if (addResearchCondition(recipe, research)) {
                    patched++;
                } else {
                    failed++;
                }
            }
        }

        LOGGER.info("GTNEcore[研究归类]: 扫描 {} 条配方；归入研究 {} 条；"
                        + "本次注入条件 {} 条；已存在 {} 条；失败 {} 条",
                scanned, classified, patched, already, failed);
        byResearch.forEach((id, recipes) ->
                LOGGER.info("GTNEcore[研究归类]: {} <- {} 条配方 {}", id, recipes.size(), recipes));
    }

    // ==================== 判定某条配方归谁 ====================

    /**
     * 这条配方归哪个研究。
     *
     * <p>优先级：配方自带的数据键 > 按 id 归类 > 无。
     */
    public static @Nullable GtneResearch.ResearchCategory researchOf(GTRecipe recipe) {
        // 1) 逃生口：配方自己声明的（优先级最高）
        if (recipe.data != null && recipe.data.contains(DATA_KEY)) {
            ResourceLocation explicit = ResourceLocation.tryParse(recipe.data.getString(DATA_KEY));
            GtneResearch.ResearchCategory byData = GtneResearch.byId(explicit);
            if (byData != null) {
                return byData;
            }
            if (explicit != null) {
                LOGGER.warn("GTNEcore[研究归类]: 配方 {} 声明了未注册的研究 {} —— 忽略该声明",
                        recipe.id, explicit);
            }
        }
        // 2) 精确 / 批量登记命中（查缓存映射，O(1)）
        GtneResearch.ResearchCategory direct = GtneResearch.researchFor(recipe.id);
        if (direct != null) {
            return direct;
        }
        // 3) 通配规则回退：通配无法预先展开，只能扫一遍规则表
        for (GtneResearch.ResearchCategory category : GtneResearch.all()) {
            if (category.matchesPattern(recipe.id)) {
                return category;
            }
        }
        return null;
    }

    /** 本来就有研究条件吗（例如配方 JSON 里手写了条件）。 */
    private static boolean hasResearchCondition(GTRecipe recipe) {
        for (RecipeCondition<?> condition : recipe.conditions) {
            if (condition instanceof GtneResearchCondition) {
                return true;
            }
        }
        return false;
    }

    /** 加研究条件；列表不可变时按 GTCEu 约定换可变副本。 */
    private static boolean addResearchCondition(GTRecipe recipe, GtneResearch.ResearchCategory research) {
        GtneResearchCondition condition =
                new GtneResearchCondition(research.id(), research.displayName());
        try {
            recipe.conditions.add(condition); // 代码生成的配方：本来就是 ArrayList
            return true;
        } catch (UnsupportedOperationException immutable) {
            // JSON 反序列化的配方：List.copyOf 出来的不可变列表，换成可变副本
        }
        List<RecipeCondition<?>> target = mutableConditions(recipe);
        if (target == null) {
            return false;
        }
        target.add(condition);
        return true;
    }

    /**
     * 把配方的条件列表替换为**可写**副本（仅在原地写入失败后调用）。
     *
     * <p>{@code GTRecipe.conditions} 是 public final 字段，唯一可行的替换手段是反射
     * {@code Field.set}。反射失败返回 null（调用方计数并告警，不抛）。
     */
    @SuppressWarnings("unchecked")
    private static List<RecipeCondition<?>> mutableConditions(GTRecipe recipe) {
        List<RecipeCondition<?>> current = recipe.conditions;
        try {
            if (conditionsField == null) {
                conditionsField = GTRecipe.class.getField("conditions");
            }
            List<RecipeCondition<?>> copy = new ArrayList<>(current);
            conditionsField.set(recipe, copy);
            return copy;
        } catch (ReflectiveOperationException e) {
            LOGGER.error("GTNEcore[研究归类]: 无法替换配方 {} 的条件列表（反射失败）", recipe.id, e);
            return null;
        }
    }
}
