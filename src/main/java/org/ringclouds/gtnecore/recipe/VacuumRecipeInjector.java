package org.ringclouds.gtnecore.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.recipe.condition.CleanroomCondition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.machine.GtneEnvironments;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 给电路装配机里的"真空管相关配方"注入真空环境条件。
 *
 * <p>
 * 背景（用户 2026-09-13 指定）：GT 的 ULV 电路就叫<b>真空管</b>（vacuum tube），
 * 所以凡是涉及它的电路装配机配方，全都要求真空环境——语义自洽，同时也是真空环境
 * 的第一个实际消费者。
 *
 * <p>
 * 匹配口径：{@code gtceu:circuit_assembler} 配方中**输入或输出**含 {@code gtceu:vacuum_tube}。
 *
 * <p>
 * <b>显示侧零成本</b>：JEI 配方页（{@code GTRecipeWidget}）对**所有** {@code RecipeCondition}
 * 一视同仁地渲染 {@code condition.getTooltips()} 作为一行字，而
 * {@code CleanroomCondition.getTooltips()} = {@code "gtceu.recipe.cleanroom"} +
 * {@code cleanroom.getTranslationKey()}（=「真空环境」）。所以只要条件挂上，
 * 配方页就会自动出现「需要真空环境」，与「需要超净间」同款。
 *
 * <h2>⚠️ 踩坑史（两次都栽在同一个地方）</h2>
 *
 * <b>坑 1（时机）</b>：最早挂在 {@link net.minecraftforge.event.AddReloadListenerEvent} 的
 * 监听器里用 {@code barrier.wait(...)} 之后注入。但 Forge 的
 * {@code ReloadableServerResources.m_247740_()} 把 listener 交给 {@code SimpleReloadInstance}，
 * 阶段是 <i>prepare → reload → apply</i>，而配方灌进 {@code RecipeManager} 在 <b>apply</b> 阶段
 * ——{@code barrier.wait()} 之后仍在 apply 之前，于是 {@code getAllRecipesFor(...)} 是<b>空表</b>，
 * 一条都匹配不到还静默无日志。
 *
 * <b>坑 2（可变性）</b>：改挂 {@code ServerAboutToStartEvent} 后确实读到配方了，但
 * {@code recipe.conditions.add(...)} 抛 {@code UnsupportedOperationException}——
 * {@code GTRecipe.conditions} 虽然是 {@code public final List}（引用 final），
 * 但从 {@code GTDynamicDataPack} 的 JSON 反序列化出来时，字段用
 * {@code RecipeCondition.CODEC.listOf()} 构造，内容是 {@code List.copyOf(...)} 出来的
 * <b>不可变列表</b>。GTCEu 自己的 {@code GTRecipe.copy()} 用 {@code new ArrayList<>(conditions)}
 * ——说明它的设计约定就是"配方构造后条件不可变，要改就换一个对象"。
 *
 * <h2>当前实现</h2>
 *
 * 注入点（两者都在配方已就绪之后）：
 * <ol>
 * <li>{@link ServerAboutToStartEvent} —— 进世界时（{@code MinecraftServer} 构造器内已完成
 * {@code ReloadableServerResources.loadResources()}，配方表已填充；早于 JEI 注册配方与玩家入服同步）。</li>
 * <li>{@link TagsUpdatedEvent}（{@code SERVER_DATA_LOAD}）—— 每次 {@code /reload} 之后，
 * 位于 Forge patch 的 {@code updateRegistryTags()} 尾部。</li>
 * </ol>
 *
 * 写入方式：条件列表可变就原地 {@code add}（代码生成的配方是 {@code new ArrayList<>()}）；
 * 不可变（JSON 反序列化）则按 GTCEu 的约定<b>换一个可变副本</b>再写入
 * ——{@code GTRecipe.conditions} 是 final 字段，唯一可行的替换手段是反射 {@code Field.set}，
 * 故见 {@link #mutableConditions}。该反射仅在 Java 未做模块封装的本 mod 类上使用，
 * 且每次重载只对命中的少数配方各执行一次。
 *
 * <p>
 * 安全性：本处理器运行在事件总线上，**抛异常会打断服务器启动**（2026-09-24 实际发生过），
 * 所以失败一律降级为错误日志，绝不外抛。
 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID)
public final class VacuumRecipeInjector {

    /** ULV 电路 = 真空管 */
    private static final ResourceLocation VACUUM_TUBE = new ResourceLocation("gtceu", "vacuum_tube");

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(VacuumRecipeInjector.class);

    /** {@code GTRecipe.conditions} 字段（用于把不可变列表换成可变副本）。 */
    private static Field conditionsField;

    /** 已注入过的服务器实例（身份比较）。重载时清空（配方对象会被重建）。 */
    private static MinecraftServer lastInjectedForServer = null;

    private VacuumRecipeInjector() {
    }

    /** 时机 ①：服务器启动（配方已加载，玩家尚未入服） */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        LOGGER.info("GTNEcore[真空注入]: ServerAboutToStart —— 开始注入");
        inject();
    }

    /** 时机 ②：每次重载的注册表标签更新完成 */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // 只认服务端数据加载那次；客户端收到的标签包不重复注入
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        LOGGER.info("GTNEcore[真空注入]: TagsUpdated（重载后）—— 开始注入");
        inject();
    }

    /** 重载即将开始：配方会被重建，清掉"已注入"标记 */
    @SubscribeEvent
    public static void onReloadStart(net.minecraftforge.event.AddReloadListenerEvent event) {
        lastInjectedForServer = null;
    }

    /** 执行注入：全链路带日志，任何一环为 0 都能立刻看出来。 */
    public static void inject() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // 主菜单阶段的 TagsUpdated（客户端侧）会走到这里，属正常
            LOGGER.debug("GTNEcore[真空注入]: 跳过——getCurrentServer() == null");
            return;
        }
        if (lastInjectedForServer == server) {
            LOGGER.debug("GTNEcore[真空注入]: 本服务器实例已注入过，跳过重复扫描");
            return;
        }
        lastInjectedForServer = server;

        var all = server.getRecipeManager().getAllRecipesFor(GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES);
        int total = all.size();
        int matched = 0;
        int already = 0;
        int patched = 0;
        int failed = 0;
        List<String> matchedIds = new ArrayList<>();

        for (GTRecipe recipe : all) {
            try {
                if (!involvesVacuumTube(recipe)) continue;
                matched++;
                matchedIds.add(recipe.id + "[" + describeConditions(recipe) + "]");
                if (alreadyVacuum(recipe)) {
                    already++;
                    continue;
                }
                if (addVacuumCondition(recipe)) patched++;
                else failed++;
            } catch (Throwable t) {
                // 绝不外抛：本方法跑在事件总线上，抛出去会打断服务器启动
                failed++;
                LOGGER.error("GTNEcore[真空注入]: 配方 {} 注入失败", recipe.id, t);
            }
        }

        LOGGER.info("GTNEcore[真空注入]: 电路装配机配方 {} 条 → 含真空管 {} 条 → 已带真空条件 {} 条"
                + " → 本次注入 {} 条" + (failed > 0 ? " → 失败 " + failed + " 条" : ""),
                total, matched, already, patched);
        // 明细：命中哪几条、各带什么条件（排查"生成期条件到底进没进 JSON"的关键证据）
        LOGGER.info("GTNEcore[真空注入]: 命中明细 = {}", matchedIds);
    }

    /** 把配方的条件列成短文本，供日志判断（空 = 该配方不带任何条件）。 */
    private static String describeConditions(GTRecipe recipe) {
        if (recipe.conditions.isEmpty()) return "无条件";
        StringBuilder sb = new StringBuilder();
        for (var c : recipe.conditions) {
            if (sb.length() > 0) sb.append(',');
            if (c instanceof CleanroomCondition cr) {
                var type = cr.getCleanroom();
                sb.append("cleanroom=")
                        .append(type == null ? "null"
                                : type.getName() + (type == GtneEnvironments.VACUUM ? "(真空✓)" : ""));
            } else {
                sb.append(c.getClass().getSimpleName());
            }
        }
        return sb.toString();
    }

    /** 把真空条件加进配方；列表不可变时按 GTCEu 约定换成可变副本。 */
    private static boolean addVacuumCondition(GTRecipe recipe) {
        CleanroomCondition condition = new CleanroomCondition(false, GtneEnvironments.VACUUM);
        try {
            recipe.conditions.add(condition); // 代码生成的配方：本来就是 ArrayList
            return true;
        } catch (UnsupportedOperationException immutable) {
            // JSON 反序列化的配方：List.copyOf 出来的不可变列表，换成可变副本
        }
        List<RecipeCondition<?>> target = mutableConditions(recipe);
        if (target == null) return false;
        target.add(condition);
        return true;
    }

    /**
     * 把配方的条件列表替换为**可写**副本（仅在原地写入失败后调用）。
     *
     * <p>
     * {@code GTRecipe.conditions} 是 {@code public final} 字段，唯一可行的替换手段是反射
     * {@code Field.set}——该字段在实际运行的 jar 里确认为 public（javap 核实），
     * 且 {@code GTRecipe} 非 record/非 hidden 类，Java 17 允许对 final 实例字段写入。
     *
     * @return 可写列表；反射失败时返回 null（调用方计数并告警，不抛）
     */
    private static List<RecipeCondition<?>> mutableConditions(GTRecipe recipe) {
        List<RecipeCondition<?>> current = recipe.conditions;
        try {
            if (conditionsField == null) {
                Field f = GTRecipe.class.getField("conditions");
                f.setAccessible(true);
                conditionsField = f;
            }
            List<RecipeCondition<?>> copy = new ArrayList<>(current);
            conditionsField.set(recipe, copy);
            return copy;
        } catch (Throwable t) {
            LOGGER.error("GTNEcore[真空注入]: 无法替换配方 {} 的条件列表（final 字段反射失败）", recipe.id, t);
            return null;
        }
    }

    private static boolean alreadyVacuum(GTRecipe recipe) {
        for (var condition : recipe.conditions) {
            // 身份比较：GtneEnvironments.VACUUM 是单例，重载后重新注入到新配方对象上
            if (condition instanceof CleanroomCondition cleanroom
                    && cleanroom.getCleanroom() == GtneEnvironments.VACUUM) {
                return true;
            }
        }
        return false;
    }

    private static boolean involvesVacuumTube(GTRecipe recipe) {
        return contentsContainVacuumTube(recipe, IO.IN) || contentsContainVacuumTube(recipe, IO.OUT);
    }

    private static boolean contentsContainVacuumTube(GTRecipe recipe, IO io) {
        var contents = io == IO.IN
                ? recipe.getInputContents(ItemRecipeCapability.CAP)
                : recipe.getOutputContents(ItemRecipeCapability.CAP);
        for (var content : contents) {
            if (!(content.getContent() instanceof Ingredient ingredient)) continue;
            for (ItemStack stack : ingredient.getItems()) {
                if (VACUUM_TUBE.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))) {
                    return true;
                }
            }
        }
        return false;
    }
}
