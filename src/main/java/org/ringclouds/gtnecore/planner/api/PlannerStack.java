package org.ringclouds.gtnecore.planner.api;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 规划器中的"物料流"：一个物品栈或流体栈（二选一）。
 * 仅依赖原版/Forge，零 GTCEu 依赖——独立拆包时本包可整体拷走。
 */
public record PlannerStack(ItemStack item, FluidStack fluid) {

    public static PlannerStack of(ItemStack item) {
        return new PlannerStack(item.copy(), FluidStack.EMPTY);
    }

    public static PlannerStack of(FluidStack fluid) {
        return new PlannerStack(ItemStack.EMPTY, fluid.copy());
    }

    /** 是否为流体 */
    public boolean isFluid() {
        return !fluid.isEmpty();
    }

    public boolean isEmpty() {
        return item.isEmpty() && fluid.isEmpty();
    }

    /** 物料身份是否相同（物品比 id、流体比类型），不管数量 */
    public boolean sameType(PlannerStack other) {
        if (other == null || isFluid() != other.isFluid()) return false;
        return isFluid()
                ? fluid.getFluid().isSame(other.fluid.getFluid())
                : ItemStack.isSameItem(item, other.item);
    }

    /** 数量：物品=个数，流体=mB */
    public long amount() {
        return isFluid() ? fluid.getAmount() : item.getCount();
    }

    /** 注册表 id 字符串（持久化/签名/显示用） */
    public String idString() {
        return isFluid()
                ? ForgeRegistries.FLUIDS.getKey(fluid.getFluid()).toString()
                : ForgeRegistries.ITEMS.getKey(item.getItem()).toString();
    }
}
