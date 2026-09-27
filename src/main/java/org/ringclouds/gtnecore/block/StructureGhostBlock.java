package org.ringclouds.gtnecore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 结构幽灵占位块（透明化钩子的占位用）：
 *
 * - 渲染：空模型（完全隐形，资产在 gtceu 命名空间——本类经 GTRegistration.REGISTRATE 注册）
 * - 不可破坏（destroyTime = -1，生存无法挖掘）
 * - 不可被替换放置（默认 replaceable=false → 其他方块无法盖上来）
 * - 碰撞/交互/遮挡 shape 全空：准星射线打不到 → **Jade/TOP 不会显示它**，也不产生阻挡
 *
 * 用途：结构透明化（如摄取仪顶环）把"原方块"隐藏时置为本块——挡住后续放置导致
 * 结构失效；解体时由机器把本块替换回原方块（材料不丢）。
 * 属于 {@code StructureTransparencyHook} 基础设施，未来结构透明化一律复用它。
 */
public class StructureGhostBlock extends Block {

    private static final VoxelShape EMPTY = Shapes.empty();

    public StructureGhostBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EMPTY;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EMPTY;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return EMPTY;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return EMPTY;
    }
}
