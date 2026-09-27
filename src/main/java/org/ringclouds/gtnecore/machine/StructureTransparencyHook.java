package org.ringclouds.gtnecore.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 结构透明化钩子（可复用）：让多方块结构在成型后把某些结构方块"隐形"，但成型校验
 * 把隐藏位按幽灵（隐藏前的原方块）判定——结构不散。隐藏位替换为
 * {@link org.ringclouds.gtnecore.block.StructureGhostBlock}（不可破坏、不可被放置覆盖、
 * 空模型隐形）而非空气，防止玩家搭方块顶掉结构位导致解体。
 *
 * 原理：结构校验走 GTCEu MultiblockState.getBlockState()（更新缓存），由
 * {@code StructureTransparencyMixin} 在取方块状态处重定向：若该位置处于幽灵表且实际
 * 为空气或幽灵占位 → 返回原方块状态。
 *
 * 用法（未来机器复用）：hide(level, pos, original) → setBlock(幽灵占位)；解体时
 * removeAll + 把幽灵/空气位还原原方块。注册表按 Level 隔离。
 */
public final class StructureTransparencyHook {

    /** 幽灵占位方块（懒解析：注册完成后首次查询才取，构造期不可 get()） */
    private static volatile Block ghostBlock;

    /** 幽灵占位方块本体：按需查注册表（gtceu 命名空间，见 GtnecoreBlocks） */
    public static Block ghostBlock() {
        Block b = ghostBlock;
        if (b == null) {
            b = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                    new net.minecraft.resources.ResourceLocation("gtceu", "structure_ghost"));
            ghostBlock = b; // 可能为 null（未加载完成），下次再试
        }
        return b;
    }

    public static boolean isGhostState(BlockState state) {
        Block b = ghostBlock();
        return b != null && state.is(b);
    }

    private StructureTransparencyHook() {
    }

    /** level → (隐藏位 → 原方块状态)；Level 用弱引用防泄漏 */
    private static final Map<Level, Map<BlockPos, BlockState>> REGISTRY = new WeakHashMap<>();

    /** 隐藏一个位置：登记幽灵（原状态）。调用方随后把世界方块置为空气。 */
    public static synchronized void add(Level level, BlockPos pos, BlockState original) {
        REGISTRY.computeIfAbsent(level, k -> new HashMap<>()).put(pos.immutable(), original);
    }

    /** 批量登记 */
    public static synchronized void addAll(Level level, Map<BlockPos, BlockState> entries) {
        if (entries == null || entries.isEmpty()) return;
        REGISTRY.computeIfAbsent(level, k -> new HashMap<>()).putAll(entries);
    }

    /** 移除若干隐藏位（解体恢复时调用） */
    public static synchronized void removeAll(Level level, Collection<BlockPos> positions) {
        Map<BlockPos, BlockState> map = REGISTRY.get(level);
        if (map == null) return;
        for (BlockPos pos : positions) {
            map.remove(pos);
        }
        if (map.isEmpty()) {
            REGISTRY.remove(level);
        }
    }

    /** 查询幽灵状态（mixin 用）：实际空气且有登记 → 原方块状态；否则 null */
    public static synchronized BlockState ghostAt(Level level, BlockPos pos) {
        Map<BlockPos, BlockState> map = REGISTRY.get(level);
        return map == null ? null : map.get(pos);
    }

    /** 当前某位置的登记状态（机器恢复/清理用） */
    public static synchronized BlockState currentGhost(Level level, BlockPos pos) {
        Map<BlockPos, BlockState> map = REGISTRY.get(level);
        return map == null ? null : map.get(pos);
    }
}
