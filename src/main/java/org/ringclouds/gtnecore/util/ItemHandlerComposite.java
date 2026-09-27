package org.ringclouds.gtnecore.util;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

/**
 * 通用"多份库存合并成一份对外视图"的物品组合器：把 N 个 {@link IItemHandlerModifiable}
 * 拼成一个**逻辑上连续的槽位网格**。
 *
 * <h2>为什么需要它</h2>
 * GTCEu 原版禁止<b>焦炉仓</b>被多台焦炉共享（{@code CokeOvenHatch.canShared() == false}）。
 * 本项目放开该限制（见 {@code src/patch/.../CokeOvenHatch.java}），而焦炉仓对外暴露的是
 * {@code ItemHandlerProxyTrait}，其 {@code proxy} 字段是<b>单值</b>——若沿用"指向某一台焦炉库存"
 * 的老写法，后成型的焦炉会覆盖前一台，共享等于半残。故改为指向本组合器：
 * **共享该仓的所有焦炉，库存合成一份对外视图**。
 *
 * <h2>设计要点</h2>
 * <ul>
 * <li><b>槽位统一编号</b>：第 i 份库存的第 s 槽 = 组合视图的第 {@code offset(i)+s} 槽，
 * 因此所有单槽操作（读/写/限值/校验）都是确定性的，不需要"合并同类堆"那种有损语义。</li>
 * <li><b>实时查询</b>：委托列表由 {@link Supplier} 提供、每次调用现算，
 * 因此**共享关系变化（成型/解体/增减共享者）无需重新绑定**——这正是选它而非
 * "每次 addedToController 重绑"的原因。</li>
 * <li><b>越界安全</b>：列表为空或槽位越界时返回空值/0，不抛异常。</li>
 * </ul>
 *
 * <p>流体侧的对应实现见 {@link FluidTankComposite}。
 */
public class ItemHandlerComposite implements IItemHandlerModifiable {

    /**
     * 实时提供当前参与合并的库存列表（每次调用都会重新求值）。
     *
     * <p>
     * 签名用嵌套通配符是为了让调用方可以直接传 {@code Supplier<List<XxxHandler>>}
     * （{@code Supplier} 与 {@code List} 都是不变的，写成
     * {@code Supplier<List<? extends IItemHandlerModifiable>>} 会拒绝
     * {@code Supplier<List<NotifiableItemStackHandler>>}）。
     */
    private final Supplier<? extends List<? extends IItemHandlerModifiable>> delegates;

    public ItemHandlerComposite(Supplier<? extends List<? extends IItemHandlerModifiable>> delegates) {
        this.delegates = delegates;
    }

    /** 当前实际的委托列表（已过滤 null）。 */
    private List<? extends IItemHandlerModifiable> current() {
        List<? extends IItemHandlerModifiable> list = delegates.get();
        if (list == null) return List.of();
        return list.stream().filter(java.util.Objects::nonNull).toList();
    }

    //////////////////////////////////////
    // *****  槽位映射  ***** //
    //////////////////////////////////////

    /**
     * 槽位口径：**只暴露单份库存的槽位数，而不是各委托之和**。
     *
     * <p>
     * 原因与 {@link FluidTankComposite#getTankCapacity} 同源（2026-09-24 实测）：n 台焦炉共享一个仓时，
     * <b>每台炉</b>都会把仓纳入自己的能力列表；若仓上报 {@code n × slots}，机器层累加即为
     * {@code n² × slots}（流体侧实测 1/2/4 台显示 32B/128B/512B，正是 n² 曲线）。
     * 故改为"取单份库存的槽位数"（各焦炉自身槽数相同）。
     *
     * <p>
     * 槽位映射相应改为**按槽号对 n 取模**：第 s 号对外槽位 = 第 {@code (s % n)} 台炉的同名槽。
     * 对任意 n 都稳定，不会因 n 变化出现越界或空洞。
     */
    @Override
    public int getSlots() {
        List<? extends IItemHandlerModifiable> list = current();
        return list.isEmpty() ? 0 : list.get(0).getSlots();
    }

    /** 对外槽位 → (第几份库存, 该库存内的槽号)；无法定位时返回 null。 */
    private int[] locate(int slot) {
        List<? extends IItemHandlerModifiable> list = current();
        if (list.isEmpty()) return null;
        int per = list.get(0).getSlots();
        if (per <= 0 || slot < 0) return null;
        return new int[] { slot % list.size(), (slot / list.size()) % per };
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        int[] at = locate(slot);
        if (at == null) return ItemStack.EMPTY;
        return current().get(at[0]).getStackInSlot(at[1]);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        int[] at = locate(slot);
        if (at == null) return;
        current().get(at[0]).setStackInSlot(at[1], stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        int[] at = locate(slot);
        if (at == null) return 64;
        return current().get(at[0]).getSlotLimit(at[1]);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        int[] at = locate(slot);
        if (at == null) return false;
        return current().get(at[0]).isItemValid(at[1], stack);
    }

    //////////////////////////////////////
    // *****  读写（跨库存依次接力）  ***** //
    //////////////////////////////////////

    /**
     * 从槽号定位到的那份库存起，依次向后续库存接力插入，直到装完或全部试完。
     * 语义与单份 handler 一致：返回装不下的余量。
     */
    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int[] at = locate(slot);
        if (at == null) return stack;
        List<? extends IItemHandlerModifiable> list = current();
        ItemStack rest = stack;
        for (int j = at[0]; j < list.size() && !rest.isEmpty(); j++) {
            IItemHandlerModifiable cur = list.get(j);
            // 只有起始那份库存从"目标槽号"开始，后续从 0 开始
            int from = (j == at[0]) ? at[1] : 0;
            for (int s = from; s < cur.getSlots() && !rest.isEmpty(); s++) {
                rest = cur.insertItem(s, rest, simulate);
            }
        }
        return rest;
    }

    /** 从槽号定位到的那份库存起，依次抽取，累计到需求量为止。 */
    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        int[] at = locate(slot);
        if (at == null) return ItemStack.EMPTY;
        List<? extends IItemHandlerModifiable> list = current();
        ItemStack out = ItemStack.EMPTY;
        for (int j = at[0]; j < list.size() && out.getCount() < amount; j++) {
            IItemHandlerModifiable cur = list.get(j);
            int from = (j == at[0]) ? at[1] : 0;
            for (int s = from; s < cur.getSlots() && out.getCount() < amount; s++) {
                ItemStack got = cur.extractItem(s, amount - out.getCount(), simulate);
                if (got.isEmpty()) continue;
                if (out.isEmpty()) {
                    out = got;
                } else if (ItemStack.isSameItemSameTags(out, got)) {
                    out.grow(got.getCount());
                }
            }
        }
        return out;
    }

    /** 便于排查：当前合并了几份库存。 */
    public int delegateCount() {
        return current().size();
    }
}
