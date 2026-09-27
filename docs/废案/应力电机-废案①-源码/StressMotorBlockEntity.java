package org.ringclouds.gtnecore.kinetics;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 应力电机方块实体：消耗 GT 电网的 EU，对外提供 Create 转速 + 应力容量。
 *
 * <p>
 * 换算（用户设定）：<b>1 EU/t = 16 su</b>；转速由数值面板调节（1..256 RPM，与转速控制器同量程），
 * 输入电流 1..16 A 可调，实际输出 = {@code V[tier] × 安培 × 16} su。
 *
 * <p>
 * 容量为什么除以转速：Create 的实际容量 = {@code calculateAddedStressCapacity() × |getGeneratedSpeed()|}
 * （{@code KineticNetwork.getActualCapacityOf}），这里返回"基数"，让乘积恒等于目标 su
 * —— 即转速只改变扭矩/转速的配比，不改变总容量，符合 Create 的物理。
 *
 * <p>
 * 无电时的表现：转速仍由面板给定（刻意不随电量抖动——Create 的 flicker 计数在转速 0↔非0
 * 反复变化时会累计，超过 128 会直接把方块拆掉），但容量按实际可用电力比例衰减，
 * 电网断供时容量归零 → 网络过载 → Create 自行停转。
 */
public class StressMotorBlockEntity extends GeneratingKineticBlockEntity {

    public static final int MIN_RPM = 1;
    public static final int MAX_RPM = 256;
    public static final int DEFAULT_RPM = 32;

    public static final int MIN_AMP = 1;
    public static final int MAX_AMP = 16;
    public static final int DEFAULT_AMP = 1;

    /** 换算率：1 EU/t → 16 su（用户设定） */
    public static final long SU_PER_EU = 16;

    /** 内部缓冲可攒的目标功率 tick 数（吸收电网推电的 tick 顺序抖动） */
    private static final int BUFFER_TICKS = 20;

    public ScrollValueBehaviour speedBehaviour;
    public ScrollValueBehaviour amperageBehaviour;

    /** 内部储能（EU）——电网推来的电先落这里，机器每 tick 按目标功率扣 */
    private long energyBuffer;
    /** 上一 tick 实际扣到的 EU，决定容量输出比例 */
    private long consumedEUt;
    /** 上一次通知网络的容量基数，避免重复 updateCapacity */
    private float lastNotifiedCapacity = -1f;

    private final IEnergyContainer energyContainer = new MotorEnergyContainer();

    public StressMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ==================== 档位与设定值 ====================

    public int getTier() {
        return ((StressMotorBlock) getBlockState().getBlock()).getTier();
    }

    /** 额定输入电压（= 机器档位电压） */
    public long getNominalVoltage() {
        return GTValues.V[getTier()];
    }

    public int getTargetAmperage() {
        return amperageBehaviour == null ? DEFAULT_AMP : amperageBehaviour.getValue();
    }

    public int getTargetRpm() {
        return speedBehaviour == null ? DEFAULT_RPM : speedBehaviour.getValue();
    }

    /** 目标输入功率（EU/t）= 档位电压 × 安培 */
    public long getTargetEUt() {
        return getNominalVoltage() * getTargetAmperage();
    }

    /** 目标应力容量（su）= 1 EU/t × 16 */
    public long getTargetSu() {
        return getTargetEUt() * SU_PER_EU;
    }

    public long getEnergyBuffer() {
        return energyBuffer;
    }

    public long getConsumedEUt() {
        return consumedEUt;
    }

    // ==================== Create 侧 ====================

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        speedBehaviour = new KineticScrollValueBehaviour(
                Component.translatable("gtnecore.stress_motor.speed"), this, new MotorValueBox(true));
        speedBehaviour.between(MIN_RPM, MAX_RPM);
        speedBehaviour.value = DEFAULT_RPM;
        speedBehaviour.withCallback(ignored -> updateGeneratedRotation());
        behaviours.add(speedBehaviour);

        amperageBehaviour = new KineticScrollValueBehaviour(
                Component.translatable("gtnecore.stress_motor.amperage"), this, new MotorValueBox(false));
        amperageBehaviour.between(MIN_AMP, MAX_AMP);
        amperageBehaviour.value = DEFAULT_AMP;
        amperageBehaviour.withCallback(ignored -> updateGeneratedRotation());
        behaviours.add(amperageBehaviour);
    }

    @Override
    public float getGeneratedSpeed() {
        return convertToDirection(getTargetRpm(), getBlockState().getValue(StressMotorBlock.FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        float speed = Math.abs(getGeneratedSpeed());
        if (speed <= 0) return 0;
        long need = getTargetEUt();
        if (need <= 0) return 0;
        // 按本 tick 实际供上的电力比例输出；Create 拿到基数后会再乘 |speed|
        float ratio = Math.min(1f, (float) consumedEUt / (float) need);
        return (getTargetSu() * ratio) / speed;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        // 按目标功率扣电（扣不满则按现有量，容量随之衰减）
        long need = getTargetEUt();
        if (energyBuffer >= need) {
            energyBuffer -= need;
            consumedEUt = need;
        } else {
            consumedEUt = energyBuffer;
            energyBuffer = 0;
        }

        // 容量变化才通知网络（updateCapacityFor 会重算并 sync 所有成员）
        float capacity = calculateAddedStressCapacity();
        if (hasNetwork() && Math.abs(capacity - lastNotifiedCapacity) > 1e-4f) {
            lastNotifiedCapacity = capacity;
            notifyStressCapacityChange(capacity);
        }
    }

    // ==================== GT 电网侧 ====================

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.CAPABILITY_ENERGY_CONTAINER) {
            return GTCapability.CAPABILITY_ENERGY_CONTAINER.orEmpty(cap, LazyOptional.of(() -> energyContainer));
        }
        return super.getCapability(cap, side);
    }

    /** 电网终点容器：源机器/电池会把电"推"到这里（GT 电网是 push 模型） */
    protected class MotorEnergyContainer implements IEnergyContainer {

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            if (amperage <= 0 || voltage <= 0) return 0;
            if (voltage > getNominalVoltage()) return 0; // 超压：本机不接受，也不炸

            long canAccept = getEnergyCanBeInserted();
            if (canAccept <= 0) return 0;

            long ampsAccepted = Math.min(amperage, canAccept / voltage);
            if (ampsAccepted <= 0) return 0;

            energyBuffer += ampsAccepted * voltage;
            setChanged();
            return ampsAccepted;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return true;
        }

        @Override
        public boolean outputsEnergy(Direction side) {
            return false;
        }

        @Override
        public long getEnergyStored() {
            return energyBuffer;
        }

        @Override
        public long getEnergyCapacity() {
            return getTargetEUt() * BUFFER_TICKS;
        }

        @Override
        public long getEnergyCanBeInserted() {
            return Math.max(0, getEnergyCapacity() - energyBuffer);
        }

        @Override
        public long getInputAmperage() {
            return getTargetAmperage();
        }

        @Override
        public long getInputVoltage() {
            return getNominalVoltage();
        }

        @Override
        public long changeEnergy(long energyToAdd) {
            long old = energyBuffer;
            energyBuffer = Math.max(0, Math.min(getEnergyCapacity(), energyBuffer + energyToAdd));
            return energyBuffer - old;
        }
    }

    // ==================== 数值面板定位 ====================

    /**
     * 数值框位置：转速在正面偏上一格、电流偏下一格——同一方块挂两个行为时靠位置区分。
     */
    protected class MotorValueBox extends ValueBoxTransform.Sided {

        private final boolean upper;

        protected MotorValueBox(boolean upper) {
            this.upper = upper;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, upper ? 11 : 5, 12.5);
        }

        @Override
        public Vec3 getLocalOffset(net.minecraft.world.level.LevelAccessor level, BlockPos pos,
                                   BlockState state) {
            Direction facing = state.getValue(StressMotorBlock.FACING);
            return super.getLocalOffset(level, pos, state)
                    .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-0.0625));
        }

        /** 只贴在出轴那一面显示 */
        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
            return side == state.getValue(StressMotorBlock.FACING);
        }
    }
}
