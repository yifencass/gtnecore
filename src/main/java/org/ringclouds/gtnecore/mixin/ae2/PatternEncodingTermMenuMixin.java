package org.ringclouds.gtnecore.mixin.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.ConfigInventory;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2 样板编码携带虚拟电路号（编码端）。
 *
 * 玩家把编程电路物品当普通输入放进处理样板的编码格——保存时：
 * - 电路从输入列表移除（不进样板输入，AE2 网络不会要求电路材料）
 * - 电路号写入样板 NBT 的自定义键 {@code gtnecore_circuit}
 * 推送端见 {@link PatternProviderLogicMixin}。
 */
@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public abstract class PatternEncodingTermMenuMixin {

    @Shadow
    private ConfigInventory encodedInputsInv;

    @Unique
    private int gtnecore$circuit = 0;

    /** 编码时被虚拟化移除的电路（槽位 → 原内容），编码完成后放回——编码格配置不丢失 */
    @Unique
    private final java.util.Map<Integer, GenericStack> gtnecore$removedCircuits = new java.util.HashMap<>();

    @Inject(method = "encodeProcessingPattern", at = @At("HEAD"))
    private void gtnecore$extractCircuit(CallbackInfoReturnable<ItemStack> cir) {
        gtnecore$circuit = 0;
        gtnecore$removedCircuits.clear();
        for (int i = 0; i < encodedInputsInv.size(); i++) {
            GenericStack stack = encodedInputsInv.getStack(i);
            if (!(stack != null && stack.what() instanceof AEItemKey key)) continue;
            ItemStack itemStack = key.getReadOnlyStack();
            if (!IntCircuitBehaviour.isIntegratedCircuit(itemStack)) continue;
            int config = IntCircuitBehaviour.getCircuitConfiguration(itemStack);
            if (config > 0) {
                gtnecore$circuit = config;
            }
            // 电路虚拟化：暂时移除（避免进样板输入列表），编码后放回
            gtnecore$removedCircuits.put(i, stack);
            encodedInputsInv.setStack(i, null);
        }
    }

    @Inject(method = "encodeProcessingPattern", at = @At("RETURN"))
    private void gtnecore$writeCircuitTagAndRestore(CallbackInfoReturnable<ItemStack> cir) {
        // 编码产物已定格（不含电路），把电路放回编码格
        for (var entry : gtnecore$removedCircuits.entrySet()) {
            encodedInputsInv.setStack(entry.getKey(), entry.getValue());
        }
        gtnecore$removedCircuits.clear();

        ItemStack pattern = cir.getReturnValue();
        if (pattern == null || pattern.isEmpty() || gtnecore$circuit <= 0) return;
        pattern.getOrCreateTag().putInt(org.ringclouds.gtnecore.util.Ae2CircuitSupport.CIRCUIT_KEY, gtnecore$circuit);
    }
}
