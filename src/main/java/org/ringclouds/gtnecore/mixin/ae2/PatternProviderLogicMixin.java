package org.ringclouds.gtnecore.mixin.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 样板推送端：样板带 {@code gtnecore_circuit} 时，推送前把供应器目标面的
 * GT 机器（IHasCircuitSlot）幽灵电路槽设置为该号——一张样板 = 一条产线切换指令，
 * 不再需要每个电路号克隆一台机器 + 专属供应器。
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicMixin {

    @Shadow
    private PatternProviderLogicHost host;

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void gtnecore$applyCircuit(IPatternDetails patternDetails,
                                       appeng.api.stacks.KeyCounter[] inputHolder,
                                       CallbackInfoReturnable<Boolean> cir) {
        AEItemKey key = patternDetails.getDefinition();
        if (key == null) return;
        CompoundTag tag = key.getTag();
        if (tag == null) return;
        int circuit = tag.getInt(org.ringclouds.gtnecore.util.Ae2CircuitSupport.CIRCUIT_KEY);
        if (circuit <= 0) return;

        BlockEntity be = host.getBlockEntity();
        if (be == null || be.getLevel() == null) return;
        BlockPos base = be.getBlockPos();
        // 推送目标面上的全部电路槽机器统一切号（材料进任何一台都跑样板对应产线）
        for (Direction side : host.getTargets()) {
            var machine = MetaMachine.getMachine(be.getLevel(), base.relative(side));
            if (machine instanceof IHasCircuitSlot hasCircuit && hasCircuit.isCircuitSlotEnabled()) {
                var handler = hasCircuit.getCircuitInventory();
                var current = handler.getStackInSlot(0);
                if (IntCircuitBehaviour.getCircuitConfiguration(current) != circuit) {
                    handler.setStackInSlot(0, IntCircuitBehaviour.stack(circuit));
                }
            }
        }
    }
}
