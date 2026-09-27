package org.ringclouds.gtnecore.mixin.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * JEI/REI「+」快速填充的电路候选净化：
 * findBestIngredient 按 ME 网络库存优先挑候选——网络里的散装裸电路（无号）会压过
 * 配方语义的带号电路，导致填充进编码格的电路丢号。此处把电路槽候选过滤为仅带号项。
 */
@Mixin(value = EncodingHelper.class, remap = false)
public abstract class EncodingHelperMixin {

    @Inject(method = "findBestIngredient", at = @At("HEAD"))
    private static void gtnecore$keepConfiguredCircuit(Map<AEItemKey, Integer> priorities,
                                                       List<GenericStack> option,
                                                       CallbackInfoReturnable<GenericStack> cir) {
        boolean hasCircuit = false, hasConfigured = false;
        for (GenericStack stack : option) {
            if (stack == null || !(stack.what() instanceof AEItemKey key)) continue;
            if (!IntCircuitBehaviour.isIntegratedCircuit(key.getReadOnlyStack())) continue;
            hasCircuit = true;
            if (IntCircuitBehaviour.getCircuitConfiguration(key.getReadOnlyStack()) > 0) {
                hasConfigured = true;
                break;
            }
        }
        if (!hasCircuit || !hasConfigured) return;
        try {
            option.removeIf(stack -> {
                if (stack == null || !(stack.what() instanceof AEItemKey key)) return false;
                var item = key.getReadOnlyStack();
                return IntCircuitBehaviour.isIntegratedCircuit(item)
                        && IntCircuitBehaviour.getCircuitConfiguration(item) <= 0;
            });
        } catch (UnsupportedOperationException ignored) {
            // 不可变列表（理论不会出现）——保持原样，编码端提取逻辑仍会处理带号电路
        }
    }
}
