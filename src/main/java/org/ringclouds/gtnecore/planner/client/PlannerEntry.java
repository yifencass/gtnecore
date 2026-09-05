package org.ringclouds.gtnecore.planner.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import org.ringclouds.gtnecore.planner.api.EmptyRecipeLookup;
import org.ringclouds.gtnecore.planner.api.RecipeLookup;
import org.ringclouds.gtnecore.planner.gtceu.GtceRecipeLookup;
import org.ringclouds.gtnecore.planner.model.PlannerGraph;
import org.ringclouds.gtnecore.planner.model.PlannerPersistence;

/**
 * 规划器对外单一入口（分离友好）：
 * - 独立 mod 化：整体复制 planner 包，换掉本类的 lookup() 即可；
 * - 简化模式：lookup() 换 EmptyRecipeLookup.INSTANCE，画布仍可开（无配方数据）。
 * Gtnecore 只在这里挂一个 initClient() 调用，不直接碰 planner 内部。
 */
public final class PlannerEntry {

    private static PlannerGraph GRAPH;

    private PlannerEntry() {
    }

    /** 客户端初始化：背包按钮事件（Gtnecore 构造器里 Dist.CLIENT 时调用） */
    public static void initClient() {
        MinecraftForge.EVENT_BUS.register(InventoryPlannerButton.class);
    }

    /** 配方数据源（GTCEu 适配；简化模式换 EmptyRecipeLookup） */
    public static RecipeLookup lookup() {
        return GtceRecipeLookup.INSTANCE;
    }

    /** 当前规划图（懒加载：读 config/gtnecore/recipe_planner.json） */
    public static PlannerGraph graph() {
        if (GRAPH == null) GRAPH = PlannerPersistence.load(lookup());
        return GRAPH;
    }

    /** 打开规划器界面（纯客户端） */
    public static void openPlanner() {
        PlannerScreen.open(graph(), lookup());
    }

    /** 关闭界面时调用，落盘规划 */
    public static void save() {
        if (GRAPH != null) PlannerPersistence.save(GRAPH);
    }

    /** 简化模式用（留给独立拆包） */
    @SuppressWarnings("unused")
    private static RecipeLookup emptyLookup() {
        return EmptyRecipeLookup.INSTANCE;
    }
}
