package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;
import com.gregtechceu.gtceu.config.ConfigHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

/**
 * GTNEcore patch 版：官方 getPartAppearance 只读 definition.getPartAppearance()（官方机器
 * 从不设置 → 恒 null → 部件成型后不伪装）。patch：fallback 到 appearanceBlock——
 * 注册了 appearanceBlock 的控制器（反曲率引擎 = luv_machine_casing）成型后部件机壳
 * 同步控制器机壳贴图；appearance 等于控制器自身方块（官方机器默认）时不伪装，保持官方行为。
 */
public interface IMultiController extends IMachineFeature, IInteractedMachine {
    BooleanProperty IS_FORMED_PROPERTY = GTMachineModelProperties.IS_FORMED;

    default MultiblockControllerMachine self() {
        return (MultiblockControllerMachine) this;
    }

    default boolean checkPattern() {
        BlockPattern pattern = this.getPattern();
        return pattern != null && pattern.checkPatternAt(this.getMultiblockState(), false);
    }

    default boolean checkPatternWithLock() {
        Lock lock = this.getPatternLock();
        lock.lock();
        try {
            return this.checkPattern();
        } finally {
            lock.unlock();
        }
    }

    default boolean checkPatternWithTryLock() {
        Lock lock = this.getPatternLock();
        if (lock.tryLock()) {
            try {
                return this.checkPattern();
            } finally {
                lock.unlock();
            }
        } else {
            return false;
        }
    }

    default BlockPattern getPattern() {
        return this.self().getDefinition().getPatternFactory().get();
    }

    boolean isFormed();

    @NotNull
    MultiblockState getMultiblockState();

    void asyncCheckPattern(long var1);

    void onStructureFormed();

    void onStructureInvalid();

    boolean hasFrontFacing();

    List<IMultiPart> getParts();

    Optional<IParallelHatch> getParallelHatch();

    default boolean isBatchEnabled() {
        return false;
    }

    default void setBatchEnabled(boolean batch) {
    }

    void onPartUnload();

    Lock getPatternLock();

    default boolean shouldAddPartToController(IMultiPart part) {
        return true;
    }

    @Nullable
    default BlockState getPartAppearance(IMultiPart part, Direction side, BlockState sourceState, BlockPos sourcePos) {
        if (!this.isFormed()) return null;
        var definition = this.self().getDefinition();
        var partAppearanceFn = definition.getPartAppearance();
        if (partAppearanceFn != null) {
            BlockState partState = partAppearanceFn.apply(this, part, side);
            if (partState != null) return partState;
        }
        // GTNEcore patch：无 partAppearance 时 fallback 到 appearanceBlock（成型后部件伪装成
        // 控制器机壳贴图）。appearance 等于控制器自身方块（官方机器默认）时不伪装。
        if (definition.getAppearance() == null) return null;
        BlockState appearance = definition.getAppearance().get();
        return appearance.getBlock() != this.self().getBlockState().getBlock() ? appearance : null;
    }

    default Comparator<IMultiPart> getPartSorter() {
        return this.self().getDefinition().getPartSorter().apply(this.self());
    }

    default InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                    BlockHitResult hit) {
        if (!this.self().isFormed() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (world.isClientSide()) {
                MultiblockInWorldPreviewRenderer.showPreview(pos, this.self(),
                        ConfigHolder.INSTANCE.client.inWorldPreviewDuration * 20);
            }
            return InteractionResult.SUCCESS;
        } else {
            return IInteractedMachine.super.onUse(state, world, pos, player, hand, hit);
        }
    }

    default boolean allowCircuitSlots() {
        return true;
    }
}
