package org.ringclouds.gtnecore.handler;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.machine.GtneEnvironments;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轨道维度环境广播器：模仿超净间的"直接引用绑定"，但方向相反——
 * 不是机器绑定到房间，而是维度级环境自动附加到轨道内的所有机器。
 *
 * 每 100 tick 扫描轨道维度的已加载方块实体，把维度环境绑定到
 * ICleanroomReceiver（与超净间同一管线——配方条件 .cleanroom() 直接可用）。
 *
 * - 非黑洞轨道 → WEIGHTLESS（失重）
 * - 黑洞轨道 → SPATIAL_DISTORTION（强扭曲空间）
 * - 重力正常化器（EuGravityNormalizerMachine）覆盖范围 → 剥夺失重（绑定回 null）
 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID)
public final class OrbitEnvironmentHandler {

    /** 每多少 tick 扫描一次（性能：轨道维度可能很大） */
    private static final int SCAN_INTERVAL = 100;

    /** 已绑定的接收者缓存（避免重复扫描）——Key: 方块位置 hash */
    private static final Map<Long, ICleanroomReceiver> boundReceivers = new ConcurrentHashMap<>();

    /** 失重环境提供者（恒生效，无需供电——维度属性） */
    private static final ICleanroomProvider WEIGHTLESS_PROVIDER = new ICleanroomProvider() {
        @Override
        public java.util.Set<com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType> getTypes() {
            return java.util.Set.of(GtneEnvironments.WEIGHTLESS);
        }
        @Override
        public boolean isClean() {
            return true; // 恒生效
        }
    };

    /** 强扭曲空间提供者（恒生效） */
    private static final ICleanroomProvider DISTORTION_PROVIDER = new ICleanroomProvider() {
        @Override
        public java.util.Set<com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType> getTypes() {
            return java.util.Set.of(GtneEnvironments.SPATIAL_DISTORTION);
        }
        @Override
        public boolean isClean() {
            return true;
        }
    };

    private OrbitEnvironmentHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % SCAN_INTERVAL != 0) return;

        var dim = serverLevel.dimension();
        if (!GtneEnvironments.isOrbitDimension(dim)) return;

        var provider = GtneEnvironments.isBlackHoleOrbit(dim)
                ? DISTORTION_PROVIDER : WEIGHTLESS_PROVIDER;

        // 扫描玩家附近的区块（8 格步进、32 格半径——覆盖轨道空间站常见范围）
        for (var player : serverLevel.players()) {
            var center = player.blockPosition();
            int r = 32;
            for (int x = -r; x <= r; x += 8) {
                for (int z = -r; z <= r; z += 8) {
                    var chunk = serverLevel.getChunkAt(center.offset(x, 0, z));
                    for (var beEntry : chunk.getBlockEntities().entrySet()) {
                        bindReceiver(serverLevel, beEntry.getKey(), provider);
                    }
                }
            }
        }
    }

    /** 绑定环境到接收者（如果该位置的机器是 ICleanroomReceiver 且未被正常化器覆盖） */
    private static void bindReceiver(ServerLevel level, BlockPos pos, ICleanroomProvider provider) {
        var receiver = GTCapabilityHelper.getCleanroomReceiver(level, pos, null);
        if (receiver == null) return;

        // 如果已有绑定（超净间/观测站/正常化器），不覆盖
        if (receiver.getCleanroom() != null) return;

        receiver.setCleanroom(provider);
        boundReceivers.put(pos.asLong(), receiver);
    }

    /** 重力正常化器调用：剥夺失重（把绑定清空） */
    public static void stripWeightless(ServerLevel level, Iterable<BlockPos> blocks) {
        for (var pos : blocks) {
            var receiver = GTCapabilityHelper.getCleanroomReceiver(level, pos, null);
            if (receiver == null) continue;
            var current = receiver.getCleanroom();
            // 只剥夺失重/扭曲（不动超净间/天文观测的绑定）
            if (current != null
                    && (current.getTypes().contains(GtneEnvironments.WEIGHTLESS)
                     || current.getTypes().contains(GtneEnvironments.SPATIAL_DISTORTION))) {
                receiver.setCleanroom(null);
            }
        }
    }
}
