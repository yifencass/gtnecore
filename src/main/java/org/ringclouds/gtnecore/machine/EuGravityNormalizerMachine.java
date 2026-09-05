package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import earth.terrarium.adastra.api.systems.GravityApi;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * EU 重力正常化器（ad_astra:gravity_normalizer 的 EU 适配版）。
 *
 * EU 供电，密封区域内恢复正常重力（1.0）；EUt = 8 + 覆盖数/16（可调）。
 */
public class EuGravityNormalizerMachine extends EuAdAstraMachine {

    /** 正常重力值（原版 TargetGravity 默认） */
    public static final float NORMAL_GRAVITY = 1.0F;

    public EuGravityNormalizerMachine(IMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction) {
        super(holder, tier, tankScalingFunction);
    }

    @Override
    protected void applyEffect(ServerLevel level, Set<BlockPos> blocks) {
        GravityApi.API.setGravity(level, blocks, NORMAL_GRAVITY);
        // 剥夺失重/扭曲空间环境（轨道广播器绑定的）
        org.ringclouds.gtnecore.handler.OrbitEnvironmentHandler.stripWeightless(level, blocks);
    }

    @Override
    protected void removeEffect(ServerLevel level, Set<BlockPos> blocks) {
        GravityApi.API.removeGravity(level, blocks);
    }

    @Override
    protected long energyCost() {
        return 8 + distributedBlocksCount / 16;
    }
}
