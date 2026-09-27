package org.ringclouds.gtnecore.mixin.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jei.transfer.EncodePatternTransferHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.ConfigInventory;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI/REI「+」键填充：GT 配方带虚拟电路时，AE2 的槽位视图链路不保证电路进编码格
 * （findBestIngredient 还会被网络里的裸电路污染）。此处在填充完成后，直接从 GT 配方
 * 语义读出电路号，主动补一个带号编程电路进编码输入格（替换既有裸电路或占空槽）。
 */
@Mixin(value = EncodePatternTransferHandler.class, remap = false)
public abstract class EncodePatternTransferHandlerMixin {

    @Inject(method = "transferRecipe", at = @At("RETURN"))
    private void gtnecore$injectCircuit(PatternEncodingTermMenu menu, Object recipe,
                                        mezz.jei.api.gui.ingredient.IRecipeSlotsView slotsView,
                                        Player player, boolean maxTransfer, boolean doTransfer,
                                        CallbackInfoReturnable<mezz.jei.api.recipe.transfer.IRecipeTransferError> cir) {
        if (!doTransfer) return;
        // GT 的 JEI category 直接以 GTRecipe 为 recipe 对象；Wrapper 是另一条链路的形态
        GTRecipe gtRecipe;
        if (recipe instanceof GTRecipe direct) {
            gtRecipe = direct;
        } else if (recipe instanceof GTRecipeWrapper wrapper) {
            gtRecipe = wrapper.recipe;
        } else {
            return;
        }
        // 电路以 notConsumable(IntCircuitIngredient) 输入存在（builder.circuitMeta）；
        // data.circuit 作兜底（KJS 数据配方可能用）
        int circuit = gtRecipe.data.getInt("circuit");
        int materialCount = 0;
        for (var content : gtRecipe.getInputContents(ItemRecipeCapability.CAP)) {
            if (!(content.getContent() instanceof Ingredient ingredient)) continue;
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) continue;
            if (IntCircuitBehaviour.isIntegratedCircuit(items[0])) {
                if (circuit <= 0) {
                    circuit = IntCircuitBehaviour.getCircuitConfiguration(items[0]);
                }
            } else {
                materialCount++; // 非电路物品输入数 = AE2 填充将占用的槽位数
            }
        }
        if (circuit <= 0) return;

        // 写槽必须走 AE2 的正规同步包（SET_FILTER）——直接改 ConfigInventory 只动客户端，
        // 服务端编码时读不到（"出现了但无效"的根因）
        ConfigInventory inputs = ((PatternEncodingTermMenuAccessor) menu).gtnecore$getEncodedInputs();
        appeng.menu.slot.FakeSlot[] slots = menu.getProcessingInputSlots();
        GenericStack circuitStack = new GenericStack(AEItemKey.of(IntCircuitBehaviour.stack(circuit)), 1);
        // 目标槽选择（两大约束）：
        // 1) AE2 编码格按行递进启用——未启用槽 getItem 返回 EMPTY（服务端数据在但
        //    不显示、点击却生效，"隐藏电路"事故根源）——必须选 isSlotEnabled 的槽
        // 2) 注入时材料的 SET_FILTER 包还在路上（客户端 inv 是填充前状态，不能数空位），
        //    AE2 填充按配方材料数 n 依序占 slot[0..n-1]——电路从 n 起找第一个启用槽
        int target = -1;
        for (int i = 0; i < inputs.size() && i < slots.length; i++) {
            GenericStack existing = inputs.getStack(i);
            if (existing != null && slots[i].isSlotEnabled()
                    && existing.what() instanceof AEItemKey key
                    && IntCircuitBehaviour.isIntegratedCircuit(key.getReadOnlyStack())) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            for (int i = materialCount; i < slots.length; i++) {
                if (slots[i].isSlotEnabled()) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) return;
        if (target >= 0) {
            // 客户端本地立即回显（AE2 的 menu 同步对注入包不回推 FakeSlot 显示）
            inputs.setStack(target, circuitStack);
            appeng.core.sync.network.NetworkHandler.instance().sendToServer(
                    new appeng.core.sync.packets.InventoryActionPacket(
                            appeng.helpers.InventoryAction.SET_FILTER,
                            slots[target].index,
                            GenericStack.wrapInItemStack(circuitStack)));
        }
    }
}
