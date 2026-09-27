package net.fabricmc.fabric.api.rendering.data.v1;

import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.minecraft.core.BlockPos;

/**
 * Fabric API 渲染数据接口桩（配合 FabricBlockView 桩，供 embeddium WorldSlice 类加载）。
 * 方法用 default（WorldSlice 未实现 fabric 分支——Forge 环境不调用，返回 null 安全）。
 */
public interface RenderAttachedBlockView extends FabricBlockView {

    default Object getBlockEntityRenderData(BlockPos pos) {
        return null;
    }
}
