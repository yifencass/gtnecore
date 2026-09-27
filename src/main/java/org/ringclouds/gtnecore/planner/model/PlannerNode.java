package org.ringclouds.gtnecore.planner.model;

import org.ringclouds.gtnecore.planner.api.PlannerRecipe;
import org.ringclouds.gtnecore.planner.api.PlannerStack;
import org.ringclouds.gtnecore.planner.api.RecipeCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方单位（画布上的一个节点）。流程：右键创建空节点 → 设定目标产物
 * （desiredOutputs）→ 查配方 → 选定 recipe → 左右出现输入/输出口（recalc 填充）。
 * 一个节点的输出口可连到另一个节点的输入口（见 PlannerGraph.recalc 的重定向逻辑）。
 */
public class PlannerNode {

    public final int id;
    /** 画布坐标（DraggableScrollableWidgetGroup 内部坐标） */
    public int x, y;
    /** 卡片高度（画布上可垂直拉伸，宽度固定 84，可持久化） */
    public int height = 60;
    public RecipeCategory category = RecipeCategory.DISTILLATION;
    /** 空节点时用户想要的目标产物（物品/流体），用于查配方 */
    public List<PlannerStack> desiredOutputs = new ArrayList<>();
    /** 选定的配方；null = 尚未选定 */
    public PlannerRecipe recipe;
    /** 手动配方倍率（输入输出全翻倍）；0 = 自动配平（recalc 按上游供给量算） */
    public int multiplier = 0;
    /** 自动配平结果（recalc 计算）：被供给的输入按 供给量/需求量 向上取整 */
    public int autoMultiplier = 1;

    /** 计算态：配方输入/输出口（recalc 时从 recipe 重建，不持久化） */
    public final List<PortState> inputPorts = new ArrayList<>();
    public final List<PortState> outputPorts = new ArrayList<>();

    public PlannerNode(int id) {
        this.id = id;
    }

    public boolean hasRecipe() {
        return recipe != null;
    }

    /** 生效倍率：手动 > 自动 */
    public int effectiveMultiplier() {
        return multiplier > 0 ? multiplier : Math.max(1, autoMultiplier);
    }

    /** 从配方重建端口（剩余量=全量×倍率，等待边转移） */
    public void resetCalc() {
        inputPorts.clear();
        outputPorts.clear();
        if (recipe != null) {
            long m = effectiveMultiplier();
            for (PlannerStack s : recipe.inputs()) inputPorts.add(new PortState(s, s.amount() * m));
            for (PlannerStack s : recipe.outputs()) outputPorts.add(new PortState(s, s.amount() * m));
        }
    }

    /**
     * 一个端口的状态：原型栈 + 剩余量。
     * 输入口：remaining = 仍需外部供给的数量（被上游连接后减少）；
     * 输出口：remaining = 尚未被下游消耗的数量。
     */
    public static final class PortState {
        public final PlannerStack stack;
        public long remaining;

        public PortState(PlannerStack stack, long remaining) {
            this.stack = stack;
            this.remaining = remaining;
        }
    }
}
