package org.ringclouds.gtnecore.planner.model;

import org.ringclouds.gtnecore.planner.api.PlannerStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 规划图：节点 + 连线 + 重定向计算 + 电量汇总。纯逻辑、零 GTCEu 依赖。
 */
public class PlannerGraph {

    private final List<PlannerNode> nodes = new ArrayList<>();
    private final List<PlannerEdge> edges = new ArrayList<>();
    private int nextId = 1;

    /** 新建节点（id 自动分配） */
    public PlannerNode addNode(int x, int y) {
        return addNode(nextId, x, y);
    }

    /** 以指定 id 建节点（加载存档用）；nextId 顺延避免冲突 */
    PlannerNode addNode(int id, int x, int y) {
        PlannerNode n = new PlannerNode(id);
        n.x = x;
        n.y = y;
        nodes.add(n);
        nextId = Math.max(nextId, id + 1);
        return n;
    }

    public PlannerNode node(int id) {
        for (PlannerNode n : nodes) {
            if (n.id == id) return n;
        }
        return null;
    }

    public List<PlannerNode> nodes() {
        return nodes;
    }

    public List<PlannerEdge> edges() {
        return edges;
    }

    /** 删除节点及其所有关联边 */
    public void removeNode(int id) {
        nodes.removeIf(n -> n.id == id);
        edges.removeIf(e -> e.fromNode() == id || e.toNode() == id);
    }

    public void removeEdge(PlannerEdge e) {
        edges.remove(e);
    }

    /**
     * 建边（重定向）。校验：两端都存在、不是同一节点、都有配方、端口下标合法。
     */
    public boolean addEdge(int fromNode, int fromOutput, int toNode, int toInput) {
        PlannerNode a = node(fromNode), b = node(toNode);
        if (a == null || b == null || a == b) return false;
        if (a.recipe == null || b.recipe == null) return false;
        if (fromOutput >= a.recipe.outputs().size() || toInput >= b.recipe.inputs().size()) return false;
        edges.add(new PlannerEdge(fromNode, fromOutput, toNode, toInput));
        return true;
    }

    /**
     * 重算全部节点的供需（重定向核心）：
     * 0) 自动配平：被供给的节点按"上游供给量/本输入需求量"向上取整设置 autoMultiplier
     *    （迭代直到稳定，支持链式 A→B→C 逐级放大），手动倍率（multiplier>0）不受影响；
     * 1) 按生效倍率重建端口剩余量；
     * 2) 对每条边，若上下游物料同型，则按 min(供给, 需求) 转移——
     *    下游输入口 remaining 减少（= 仍需外部供给的差额），上游输出口 remaining 减少（= 已分配产能）。
     * 调用时机：任何节点/边变更后。
     */
    public void recalc() {
        // 预重置（旧倍率），保证配平迭代时端口列表存在
        for (PlannerNode n : nodes) n.resetCalc();
        // 自动配平：迭代到稳定（链式供给逐级传导）
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 64) {
            changed = false;
            for (PlannerNode n : nodes) {
                long auto = 1;
                for (PlannerEdge e : edges) {
                    if (e.toNode() != n.id) continue;
                    PlannerNode a = node(e.fromNode());
                    if (a == null) continue;
                    if (e.fromOutput() >= a.outputPorts.size() || e.toInput() >= n.inputPorts.size()) continue;
                    PlannerStack supply = a.outputPorts.get(e.fromOutput()).stack;
                    PlannerStack demand = n.inputPorts.get(e.toInput()).stack;
                    if (!supply.sameType(demand)) continue;
                    long supplyAmt = supply.amount() * a.effectiveMultiplier();
                    long demandAmt = demand.amount();
                    long m = demandAmt <= 0 ? 1 : (supplyAmt + demandAmt - 1) / demandAmt;
                    if (m > auto) auto = m;
                }
                if (auto != n.autoMultiplier) {
                    n.autoMultiplier = (int) Math.min(auto, Integer.MAX_VALUE);
                    changed = true;
                }
            }
        }
        for (PlannerNode n : nodes) n.resetCalc();
        for (PlannerEdge e : edges) {
            PlannerNode a = node(e.fromNode()), b = node(e.toNode());
            if (a == null || b == null) continue;
            PlannerNode.PortState supply = a.outputPorts.get(e.fromOutput());
            PlannerNode.PortState demand = b.inputPorts.get(e.toInput());
            if (supply == null || demand == null || !supply.stack.sameType(demand.stack)) continue;
            long transfer = Math.min(supply.remaining, demand.remaining);
            supply.remaining -= transfer;
            demand.remaining -= transfer;
        }
    }

    // ---- 缺口统计 ----

    /** 该输入端口是否已连接供给但未满足（返回缺口量；未连接/已满足 = 0） */
    public long missingAt(PlannerNode n, int inputIdx) {
        if (n.recipe == null || inputIdx < 0 || inputIdx >= n.inputPorts.size()) return 0;
        boolean linked = false;
        for (PlannerEdge e : edges) {
            if (e.toNode() == n.id && e.toInput() == inputIdx) {
                linked = true;
                break;
            }
        }
        if (!linked) return 0;
        return Math.max(0, n.inputPorts.get(inputIdx).remaining);
    }

    /** 配方链缺少清单：所有"已连接但供给不足"的输入缺口，按物料合并（数量相加） */
    public List<PlannerStack> missingSummary() {
        List<PlannerStack> out = new ArrayList<>();
        for (PlannerNode n : nodes) {
            if (n.recipe == null) continue;
            for (int i = 0; i < n.inputPorts.size(); i++) {
                long miss = missingAt(n, i);
                if (miss <= 0) continue;
                PlannerStack s = n.inputPorts.get(i).stack;
                PlannerStack existing = null;
                for (PlannerStack o : out) {
                    if (o.sameType(s)) {
                        existing = o;
                        break;
                    }
                }
                if (existing == null) {
                    out.add(withAmount(s, miss));
                } else {
                    out.set(out.indexOf(existing), withAmount(existing, existing.amount() + miss));
                }
            }
        }
        return out;
    }

    private static PlannerStack withAmount(PlannerStack s, long amount) {
        if (s.isFluid()) {
            net.minecraftforge.fluids.FluidStack f = new net.minecraftforge.fluids.FluidStack(
                    s.fluid().getFluid(), (int) Math.min(amount, Integer.MAX_VALUE), s.fluid().getTag());
            return PlannerStack.of(f);
        }
        return PlannerStack.of(new net.minecraft.world.item.ItemStack(s.item().getItem(),
                (int) Math.min(amount, Integer.MAX_VALUE), s.item().getTag()));
    }

    // ---- 电量汇总（只算 EU/t，其他能量（CWU/蒸汽等）不参与） ----

    /** 全部节点总耗电（EU/t，恒正，按生效倍率；溢出饱和到 Long.MAX_VALUE 而非负数） */
    public long totalConsumedPerTick() {
        long s = 0;
        for (PlannerNode n : nodes) {
            if (n.recipe != null) s = saturatedAdd(s, safeMul(Math.max(0, n.recipe.eut()), n.effectiveMultiplier()));
        }
        return s;
    }

    /**
     * 全部节点总发电（EU/t，恒正，按生效倍率）。
     * outputEut() 与 -eut() 均可能含发电（eut = 输入EUt − 输出EUt），取较大者避免漏算/重复。
     * 溢出饱和到 Long.MAX_VALUE 而非负数。
     */
    public long totalGeneratedPerTick() {
        long s = 0;
        for (PlannerNode n : nodes) {
            if (n.recipe == null) continue;
            s = saturatedAdd(s, safeMul(Math.max(n.recipe.outputEut(), Math.max(0, -n.recipe.eut())), n.effectiveMultiplier()));
        }
        return s;
    }

    /** 饱和乘法：溢出时钳到 Long.MAX_VALUE（高阶电压 × 大配平倍率会超 long） */
    private static long safeMul(long a, long b) {
        if (a == 0 || b == 0) return 0;
        long r = a * b;
        if (a != r / b) return Long.MAX_VALUE;
        return r;
    }

    /** 饱和加法：溢出时钳到 Long.MAX_VALUE */
    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) return Long.MAX_VALUE; // 同号相加溢出
        return r;
    }

    /** 净电量（EU/t，正=有盈余，负=入不敷出） */
    public long netPowerPerTick() {
        return totalGeneratedPerTick() - totalConsumedPerTick();
    }

    // ---- 最终产物统计 ----

    /**
     * 最终产物：recalc 后未被下游连接消耗的输出（remaining > 0）。
     * 普适规则：连接到"纯发电/纯能量节点"（无物质输出）的输入端，其物料视为
     * 全部转化为能量（燃料烧尽），不计入最终产物——适用于所有发电机类配方，不针对具体物料。
     */
    public List<PlannerStack> finalOutputs() {
        List<PlannerStack> out = new ArrayList<>();
        for (PlannerNode n : nodes) {
            if (n.recipe == null) continue;
            for (int i = 0; i < n.outputPorts.size(); i++) {
                PlannerNode.PortState p = n.outputPorts.get(i);
                double rem = p.remaining;
                for (PlannerEdge e : edges) {
                    if (e.fromNode() != n.id || e.fromOutput() != i) continue;
                    PlannerNode b = node(e.toNode());
                    if (b != null && b.recipe != null && isPureEnergy(b)) {
                        rem = 0; // 燃料被发电机完全转化
                        break;
                    }
                }
                if (rem > 0) out.add(p.stack);
            }
        }
        return out;
    }

    /** 纯能量节点：无物质输出但有发电量的配方（燃气轮机/发电机等，输入全部转化） */
    private static boolean isPureEnergy(PlannerNode n) {
        return n.recipe.outputs().isEmpty() && n.recipe.outputEut() > 0;
    }
}
