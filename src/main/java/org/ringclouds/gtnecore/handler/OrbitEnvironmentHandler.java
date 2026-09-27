package org.ringclouds.gtnecore.handler;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.machine.GtneEnvironments;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维度级环境广播器：模仿超净间的"直接引用绑定"，但方向相反——
 * 不是机器绑定到房间，而是维度级环境自动附加到维度内的所有机器。
 *
 * 每 100 tick 扫描玩家附近的已加载方块实体，把维度环境绑定到
 * ICleanroomReceiver（与超净间同一管线——配方条件 .cleanroom() 直接可用）。
 *
 * - 非黑洞轨道 → WEIGHTLESS（失重）
 * - 黑洞轨道 → SPATIAL_DISTORTION（强扭曲空间）
 * - 轨道与月球 → 额外叠加 VACUUM（真空；同一个 provider 里合并多种类型）
 * - 重力正常化器（EuGravityNormalizerMachine）覆盖范围 → 剥夺失重（保留真空等其余类型）
 *
 * <p>
 * 注：氧气供给器走 Ad Astra 的 per-block 系统（OxygenApi/TemperatureApi），与本管线互不相干，
 * 所以供氧**不会**剥夺真空——这是"真空不被氧气供给器剥夺"需求的天然保证。
 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID)
public final class OrbitEnvironmentHandler {

    /** 每多少 tick 扫描一次（性能：轨道维度可能很大） */
    private static final int SCAN_INTERVAL = 100;

    /** 已绑定的接收者缓存（避免重复扫描）——Key: 方块位置 hash */
    private static final Map<Long, ICleanroomReceiver> boundReceivers = new ConcurrentHashMap<>();

    /**
     * 复合环境提供者：接收者上的 provider 字段是**单值**，而一个维度可能同时提供
     * 多种环境（轨道 = 失重/扭曲 + 真空），所以这里合并成一个 types 集合。
     * 恒生效、无需供电——维度属性。
     */
    private record CompositeProvider(Set<CleanroomType> types) implements ICleanroomProvider {

        @Override
        public Set<CleanroomType> getTypes() {
            return types;
        }

        @Override
        public boolean isClean() {
            return true;
        }
    }

    private OrbitEnvironmentHandler() {
    }

    /** 该维度的天然环境 → provider；该维度无天然环境时返回 null。 */
    @Nullable
    private static ICleanroomProvider providerFor(ResourceKey<Level> dim) {
        Set<CleanroomType> types = new HashSet<>();
        if (GtneEnvironments.isOrbitDimension(dim)) {
            types.add(GtneEnvironments.orbitEnvironment(dim));
        }
        if (GtneEnvironments.isVacuumDimension(dim)) {
            types.add(GtneEnvironments.VACUUM);
        }
        return types.isEmpty() ? null : new CompositeProvider(Set.copyOf(types));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % SCAN_INTERVAL != 0) return;

        var provider = providerFor(serverLevel.dimension());
        if (provider == null) return; // 该维度没有天然环境（主世界等）

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

    /** 绑定环境到接收者（如果该位置的机器是 ICleanroomReceiver 且未被其他提供者占用） */
    private static void bindReceiver(ServerLevel level, BlockPos pos, ICleanroomProvider provider) {
        var receiver = GTCapabilityHelper.getCleanroomReceiver(level, pos, null);
        if (receiver == null) return;

        // 如果已有绑定（超净间/观测站/真空泵覆盖板/正常化器），不覆盖
        if (receiver.getCleanroom() != null) return;

        receiver.setCleanroom(provider);
        boundReceivers.put(pos.asLong(), receiver);
    }

    /**
     * 重力正常化器调用：剥夺失重/强扭曲。
     *
     * <p>
     * 只从 provider 的 types 里**减去**这两种类型，保留其余（例如真空）——否则正常化器一开启，
     * 会把同一 provider 里的真空一起清掉。
     */
    public static void stripWeightless(ServerLevel level, Iterable<BlockPos> blocks) {
        for (var pos : blocks) {
            var receiver = GTCapabilityHelper.getCleanroomReceiver(level, pos, null);
            if (receiver == null) continue;
            var current = receiver.getCleanroom();
            if (current == null) continue;

            Set<CleanroomType> types = current.getTypes();
            boolean hasWeightless = types.contains(GtneEnvironments.WEIGHTLESS);
            boolean hasDistortion = types.contains(GtneEnvironments.SPATIAL_DISTORTION);
            // 不是维度环境绑定（超净间/观测站/真空泵覆盖板），不动
            if (!hasWeightless && !hasDistortion) continue;

            Set<CleanroomType> remaining = new HashSet<>(types);
            remaining.remove(GtneEnvironments.WEIGHTLESS);
            remaining.remove(GtneEnvironments.SPATIAL_DISTORTION);
            receiver.setCleanroom(remaining.isEmpty() ? null : new CompositeProvider(Set.copyOf(remaining)));
        }
    }
}
