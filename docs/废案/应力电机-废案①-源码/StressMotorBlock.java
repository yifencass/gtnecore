package org.ringclouds.gtnecore.kinetics;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 应力电机（Stress Motor）：GT 电缆 EU → Create 应力。
 *
 * <p>
 * 形态说明（为什么不是 GTM 机器）：Create 的应力网络只认 {@code KineticBlockEntity}
 * ——{@code KineticBlockEntity.setSource(BlockPos)} 里有 {@code instanceof KineticBlockEntity}
 * 硬检查，且每 60 tick 的 {@code validateKinetics()} 会把"无源却有转速"的方块强制归零。
 * 所以驱动端必须是 Create 方块的方块实体（Java 单继承下 KBE 与 MetaMachineBlockEntity 不可兼得）。
 * 本方块继承 Create 的 {@link DirectionalKineticBlock}，方块实体则同时暴露 GT 的
 * {@code CAPABILITY_ENERGY_CONTAINER}——电网（EnergyNetWalker 只认"邻居 BE 暴露该能力"）
 * 因此会像对待机器一样主动把电推给它，无需改动 GTCEu 任何代码。
 */
public class StressMotorBlock extends DirectionalKineticBlock implements IBE<StressMotorBlockEntity> {

    /** GT 电压档（GTValues.LV..UHV），决定输入电压与 SU 输出基数 */
    private final int tier;

    public StressMotorBlock(BlockBehaviour.Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    /** 只有正面（FACING）出轴，其余面是机壳 */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        return side == state.getValue(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public Class<StressMotorBlockEntity> getBlockEntityClass() {
        return StressMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StressMotorBlockEntity> getBlockEntityType() {
        return StressMotorRegistry.STRESS_MOTOR_BE.get();
    }
}
