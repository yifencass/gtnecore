package net.fabricmc.fabric.api.blockview.v2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.Biome;

/**
 * Fabric API BlockView 桩接口（仅方法签名兼容 embeddium WorldSlice 的接口实现）。
 *
 * embeddium 0.3.31 的 WorldSlice 实现了 fabric 的 FabricBlockView/RenderAttachedBlockView，
 * Forge 环境没有 fabric-api，类加载时（TransformerClassWriter 计算继承层级）直接
 * RuntimeException "Cannot find class FabricBlockView"，打断客户端玩家加入任务 → 卡在加入世界。
 * 提供同名接口让类加载通过；fabric 专属方法（getBiomeFabric）签名与 WorldSlice 实现一致。
 */
public interface FabricBlockView extends BlockGetter {

    Holder<Biome> getBiomeFabric(BlockPos pos);
}
