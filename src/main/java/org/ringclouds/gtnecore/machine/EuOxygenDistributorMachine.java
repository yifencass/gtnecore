package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import earth.terrarium.adastra.api.systems.OxygenApi;
import earth.terrarium.adastra.api.systems.TemperatureApi;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * EU 氧气分配器（ad_astra:oxygen_distributor 的 EU 适配版）。
 *
 * 任意 "oxygen" 命名流体（gtceu:oxygen——GT 材料流体，格雷化）管道导入 + EU 供电，
 * 密封区域内标记含氧。EUt = 8 + 覆盖数/16；氧气 = max(1, 覆盖数/1500) mB/t（对齐原版）。
 */
public class EuOxygenDistributorMachine extends EuAdAstraMachine {

    public EuOxygenDistributorMachine(IMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction) {
        super(holder, tier, tankScalingFunction);
    }

    /**
     * 显式建 1 个输入流体槽：DUMMY_RECIPES 的 getMaxInputs(FluidRecipeCapability)=0，
     * 默认 createImportFluidHandler 会建出 0 槽 handler → 流体槽不存在、管道接不上。
     * （只给氧气机建槽——重力正常化器不需要流体）
     */
    @Override
    protected NotifiableFluidTank createImportFluidHandler(Object... args) {
        return new NotifiableFluidTank(this, 1, tankScalingFunction.applyAsInt(getTier()), IO.IN);
    }

    /** GUI 左下角补一个可见流体槽（DUMMY_RECIPES 的可编辑 UI 模板不生成流体槽） */
    @Override
    public Widget createMainPage(FancyMachineUIWidget parent) {
        var page = super.createMainPage(parent);
        if (page instanceof WidgetGroup group) {
            group.addWidget(new TankWidget(importFluids, 8, group.getSize().height - 26, false, true)
                    .setBackground(GuiTextures.FLUID_SLOT));
        }
        return page;
    }

    @Override
    protected void applyEffect(ServerLevel level, Set<BlockPos> blocks) {
        OxygenApi.API.setOxygen(level, blocks, true);
        // 原版氧分配器同时把区域温度设为 22°C（tickOxygen 里 setOxygen 后紧跟
        // setTemperature 22）——只供氧不恒温的话，极端温度星球上玩家照样冻/烫伤，
        // 表现为"房间无氧"的错觉。重力正常化器只标重力、不标温（原版如此）。
        TemperatureApi.API.setTemperature(level, blocks, (short) 22);
    }

    @Override
    protected void removeEffect(ServerLevel level, Set<BlockPos> blocks) {
        OxygenApi.API.removeOxygen(level, blocks);
        TemperatureApi.API.removeTemperature(level, blocks);
    }

    @Override
    protected long energyCost() {
        return 8 + distributedBlocksCount / 16;
    }

    /** 任意命名空间的 "oxygen" 流体都算氧气（gtceu:oxygen / ad_astra:oxygen 等，防流体不统一） */
    private static boolean isOxygenFluid(Fluid fluid) {
        var key = fluid != null ? ForgeRegistries.FLUIDS.getKey(fluid) : null;
        return key != null && key.getPath().equals("oxygen");
    }

    /** 检查流体槽有氧气可用（至少 1mB），有则消耗。
     *  消耗速率对齐 Ad Astra 原版：max(1, 覆盖数/1500) mB/t——
     *  之前误用 /2（750 倍超标），储罐几十秒抽干 → 效果被撤销 → 机器一直"失效"。 */
    @Override
    protected boolean canApplyEffect() {
        if (distributedBlocksCount <= 0) return true;
        int mb = (int) Math.max(1, distributedBlocksCount / 1500);
        // 直接用 importFluids 字段——getTraits() 里没有它（WorkableTieredMachine
        // 构造器只 putfield 不 attachTraits，traits 列表不含这些槽）
        var tank = importFluids;
        if (tank == null) return false;
        for (int i = 0; i < tank.getTanks(); i++) {
            var stack = tank.getFluidInTank(i);
            if (stack.isEmpty()) continue;
            if (isOxygenFluid(stack.getFluid())) {
                if (stack.getAmount() >= mb) {
                    tank.drain(mb, IFluidHandler.FluidAction.EXECUTE);
                    return true;
                }
                return false;
            }
        }
        if (self().getOffsetTimer() % 100 == 0) {
            LOGGER.debug("GTNEcore EU Oxygen: no oxygen fluid in tanks (timer={})", self().getOffsetTimer());
        }
        return false;
    }
}
