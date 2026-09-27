package org.ringclouds.gtnecore.util;

import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 通用"多个流体罐合并成一份对外视图"的组合器——{@link ItemHandlerComposite} 的流体对应物。
 *
 * <p>
 * 用途见 {@link ItemHandlerComposite} 的类注释（焦炉仓共享）。罐位沿用同样的**统一编号**：
 * 第 i 份 handler 的第 t 罐 = 组合视图的第 {@code offset(i)+t} 罐。
 *
 * <h2>合并语义（流体与物品的差异）</h2>
 * 流体栈不像物品栈那样能简单地"按槽位一一对应"，因为同一台机器的一罐里只能装一种流体。
 * 因此：
 * <ul>
 * <li>{@link #getFluidInTank}：返回该罐位在各委托里**第一个非空**的流体（同种流体由 fill 保证入同一罐）；</li>
 * <li>{@link #getTankCapacity}：该罐位在各委托里的**容量之和**（对外表现为一个更大的罐）；</li>
 * <li>{@link #fill} / {@link #drain}：依次在各委托间接力，返回实际总量。</li>
 * </ul>
 * 这套语义对"焦炉导出焦油/杂酚油"这种**单一产出流体**的场景完全够用，且不会出现
 * "同种流体散落在多个罐里"的歧义。
 */
public class FluidTankComposite implements IFluidHandlerModifiable {

    /**
     * 实时提供当前参与合并的流体 handler 列表。
     *
     * <p>
     * 嵌套通配符同 {@link ItemHandlerComposite}：{@code Supplier}/{@code List} 都是不变的，
     * 这样才能直接传 {@code Supplier<List<NotifiableFluidTank>>}。
     */
    private final Supplier<? extends List<? extends IFluidHandlerModifiable>> delegates;

    public FluidTankComposite(Supplier<? extends List<? extends IFluidHandlerModifiable>> delegates) {
        this.delegates = delegates;
    }

    private List<? extends IFluidHandlerModifiable> current() {
        List<? extends IFluidHandlerModifiable> list = delegates.get();
        if (list == null) return List.of();
        return list.stream().filter(Objects::nonNull).toList();
    }

    //////////////////////////////////////
    // *****  罐位映射  ***** //
    //////////////////////////////////////

    @Override
    public int getTanks() {
        int n = 0;
        for (IFluidHandlerModifiable h : current()) n += h.getTanks();
        return n;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        int offset = 0;
        for (IFluidHandlerModifiable h : current()) {
            int size = h.getTanks();
            if (tank < offset + size) {
                FluidStack stack = h.getFluidInTank(tank - offset);
                // 该罐位在别的委托里有内容时也一并呈现（同种流体，取第一个非空即可）
                return stack == null ? FluidStack.EMPTY : stack;
            }
            offset += size;
        }
        return FluidStack.EMPTY;
    }

    /**
     * 容量口径：**取各委托中最大的单罐容量，而不是求和**。
     *
     * <p>
     * 为什么不能求和（2026-09-24 实测数据）：n 台焦炉共享一个仓时，**每台炉**都会把这个仓
     * 当作自己的部件纳入自己的能力列表；若仓上报 {@code n × 32B}，机器层累加后就是
     * {@code n² × 32B} —— 实测 1/2/4 台分别显示 32B/128B/512B，正是 n² 曲线。
     *
     * <p>
     * 改为"取最大单罐"后：每台炉看到的仓都是 32B，n 台共 {@code n × 32B}，
     * 恰好等于真实总量（n 个 32B 罐），Jade 显示也回到正确值。
     *
     * <p>
     * 注意：这只是**上报口径**。实际存取仍由 {@link #fill}/{@link #drain} 在各委托间接力完成，
     * 仓依旧能同时为 n 台炉服务。
     */
    @Override
    public int getTankCapacity(int tank) {
        int offset = 0;
        for (IFluidHandlerModifiable h : current()) {
            int size = h.getTanks();
            if (tank < offset + size) {
                int local = tank - offset;
                int max = 0;
                for (IFluidHandlerModifiable other : current()) {
                    if (local < other.getTanks()) max = Math.max(max, other.getTankCapacity(local));
                }
                return max;
            }
            offset += size;
        }
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        int offset = 0;
        for (IFluidHandlerModifiable h : current()) {
            int size = h.getTanks();
            if (tank < offset + size) return h.isFluidValid(tank - offset, stack);
            offset += size;
        }
        return false;
    }

    @Override
    public void setFluidInTank(int tank, FluidStack stack) {
        // 先清空同一下标的所有委托罐，再写入本罐位对应的那个（保持"一份流体"的语义）
        int offset = 0;
        int target = -1;
        for (IFluidHandlerModifiable h : current()) {
            int size = h.getTanks();
            if (tank < offset + size) {
                target = tank - offset;
                break;
            }
            offset += size;
        }
        if (target < 0) return;
        for (IFluidHandlerModifiable h : current()) {
            if (target < h.getTanks()) h.setFluidInTank(target, FluidStack.EMPTY);
        }
        int off2 = 0;
        for (IFluidHandlerModifiable h : current()) {
            int size = h.getTanks();
            if (tank < off2 + size) {
                h.setFluidInTank(tank - off2, stack);
                return;
            }
            off2 += size;
        }
    }

    //////////////////////////////////////
    // *****  读写  ***** //
    //////////////////////////////////////

    /** 依次向各委托填充，返回实际填充量。 */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return 0;
        int filled = 0;
        FluidStack remaining = resource.copy();
        for (IFluidHandlerModifiable h : current()) {
            if (remaining.isEmpty()) break;
            int done = h.fill(remaining, action);
            if (done > 0) {
                filled += done;
                remaining.shrink(done);
            }
        }
        return filled;
    }

    /** 依次从各委托抽取，返回实际抽出的流体。 */
    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource == null || resource.isEmpty()) return FluidStack.EMPTY;
        FluidStack out = FluidStack.EMPTY;
        int want = resource.getAmount();
        for (IFluidHandlerModifiable h : current()) {
            if (want <= 0) break;
            FluidStack got = h.drain(new FluidStack(resource, want), action);
            if (got.isEmpty()) continue;
            want -= got.getAmount();
            if (out.isEmpty()) {
                out = got;
            } else {
                out.grow(got.getAmount());
            }
        }
        return out;
    }

    /** 依次从各委托抽取指定量（不限流体种类），返回实际抽出的流体。 */
    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        FluidStack out = FluidStack.EMPTY;
        int want = maxDrain;
        for (IFluidHandlerModifiable h : current()) {
            if (want <= 0) break;
            FluidStack got = h.drain(want, action);
            if (got.isEmpty()) continue;
            want -= got.getAmount();
            if (out.isEmpty()) {
                out = got;
            } else if (out.isFluidEqual(got)) {
                out.grow(got.getAmount());
            }
        }
        return out;
    }

    /** 便于排查：当前合并了几份流体 handler。 */
    public int delegateCount() {
        return current().size();
    }
}
