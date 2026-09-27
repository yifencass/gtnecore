package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import earth.terrarium.adastra.common.config.MachineConfig;
import earth.terrarium.adastra.common.utils.floodfill.FloodFill3D;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

/**
 * Ad Astra 机器 EU 适配基类：吃 EU（GTM 电缆直供）复刻区域效果。
 *
 * 原版走 Botarium FE（WrappedBlockEnergyContainer）+ 各自的 BE tick；
 * 适配版为 GT 单方块机器（SimpleTieredMachine，HV 档），能量经自身 EU 容器，
 * 每 {@link MachineConfig#distributionRefreshRate} tick 做一次
 * {@link FloodFill3D} 密封扫描并应用效果（氧气/重力，子类实现），
 * EU 成本随覆盖方块数缩放。区域上限沿用原版 {@link MachineConfig#maxDistributionBlocks}。
 *
 * 子类可通过 {@link #canApplyEffect()} 追加资源检查（如氧气版的流体检查）——
 * 返回 false 时效果不应用且旧覆盖被撤销。
 */
public abstract class EuAdAstraMachine extends SimpleTieredMachine {

    protected static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    /** 上次覆盖的方块（断电/形状变化时移除效果用） */
    protected final Set<BlockPos> lastDistributedBlocks = new HashSet<>();
    /** 当前覆盖方块数（成本与显示用） */
    protected int distributedBlocksCount;
    /** 效果当前是否生效（驱动 recipe_logic_status 工作显示：动画 overlay + UI 状态） */
    protected boolean effectActive;

    /** 效果是否正在生效（供 RecipeLogic 驱动工作状态显示） */
    public boolean isEffectActive() {
        return effectActive;
    }

    protected EuAdAstraMachine(IMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction) {
        super(holder, tier, tankScalingFunction);
    }

    /** 效果应用：把覆盖集合标记为生效（氧气 setOxygen / 重力 setGravity） */
    protected abstract void applyEffect(ServerLevel level, Set<BlockPos> blocks);

    /** 移除旧覆盖的效果（断电/重扫时） */
    protected abstract void removeEffect(ServerLevel level, Set<BlockPos> blocks);

    /** 每 tick EU 成本（子类按覆盖方块数给公式） */
    protected abstract long energyCost();

    /** 追加资源检查（如氧气版的流体可用性）；默认 true（仅 EU 门槛）。
     *  返回 false 时效果不应用且旧覆盖被撤销。 */
    protected boolean canApplyEffect() {
        return true;
    }

    /**
     * 洪泛起点：机器朝向的前一格（对齐 Ad Astra：墙装/落地都朝房间内扫描）。
     * 之前从机器自身位置开始——机器贴在密封房间外墙上时洪泛的是室外，房间永远不被标记 → "无效"。
     */
    protected BlockPos floodOrigin() {
        return self().getPos().relative(self().getFrontFacing());
    }

    /** 自身 EU 容器（GTM 电缆直供）。
     *  直接用 energyContainer 字段——getTraits() 里没有它（这些槽是构造器
     *  putfield 的普通字段，不进 traits 列表，遍历 getTraits 永远找不到）。 */
    protected IEnergyContainer energy() {
        return energyContainer;
    }

    /** 每 tick 效果主循环（由 onLoad 的机器级订阅驱动） */
    public void effectTick() {
        Level level = self().getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        IEnergyContainer energy = energy();
        long off = self().getOffsetTimer();
        long cost = energyCost();

        if (energy == null || energy.getEnergyStored() < cost) {
            // 断电：移除效果并衰减
            effectActive = false;
            if (!lastDistributedBlocks.isEmpty() && off % 20 == 0) {
                removeEffect(serverLevel, lastDistributedBlocks);
                lastDistributedBlocks.clear();
                distributedBlocksCount = 0;
            }
            return;
        }

        // 子类资源检查（如氧气版检查流体）——不通过则撤销效果
        if (!canApplyEffect()) {
            effectActive = false;
            if (!lastDistributedBlocks.isEmpty() && self().getOffsetTimer() % 20 == 0) {
                removeEffect(serverLevel, lastDistributedBlocks);
                lastDistributedBlocks.clear();
                distributedBlocksCount = 0;
            }
            return;
        }

        energy.removeEnergy(cost);

        // 每 distributionRefreshRate tick 重扫一次密封区域
        if (self().getOffsetTimer() % Math.max(1, MachineConfig.distributionRefreshRate) != 0) return;
        Set<BlockPos> blocks = FloodFill3D.run(serverLevel, floodOrigin(),
                MachineConfig.maxDistributionBlocks, FloodFill3D.TEST_FULL_SEAL, true);
        distributedBlocksCount = blocks.size();

        // 旧集合中不再存在的位置移除效果
        var stale = new HashSet<>(lastDistributedBlocks);
        stale.removeAll(blocks);
        if (!stale.isEmpty()) removeEffect(serverLevel, stale);

        // 有密封空间才视为生效（空集 = 没封好，不算工作）
        effectActive = !blocks.isEmpty();
        applyEffect(serverLevel, blocks);
        lastDistributedBlocks.clear();
        lastDistributedBlocks.addAll(blocks);
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new EuAdAstraLogic(this);
    }

    /**
     * 效果 tick 订阅：不能用 RecipeLogic 的 tick 驱动——IDLE 时 findAndHandleRecipe
     * 找不到配方（DUMMY_RECIPES 永远没有）会退订 subscription，逻辑就此死掉。
     * 这里用机器自身的 subscribeServerTick（永不退订）驱动效果主循环。
     */
    @Override
    public void onLoad() {
        super.onLoad();
        var self = self();
        if (self.getLevel() != null && !self.getLevel().isClientSide) {
            self.subscribeServerTick(this::gtnecoreEffectTick);
        }
    }

    private void gtnecoreEffectTick() {
        effectTick();
        // 同步工作状态到 RecipeLogic（驱动动画 overlay + UI）
        var logic = (EuAdAstraLogic) recipeLogic;
        var target = effectActive ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE;
        if (logic.getStatus() != target) {
            logic.setStatus(target);
        }
    }

    /** 空转配方逻辑：不跑配方（效果由 onAttach 的独立订阅驱动） */
    public static class EuAdAstraLogic extends RecipeLogic {
        public EuAdAstraLogic(EuAdAstraMachine machine) {
            super(machine);
        }
    }
}
