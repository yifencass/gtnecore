package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 【临时诊断】无尽锭/粒 tooltip 公式排查：首次 hover 无尽锭/粒时打印
 * GT 材料条目解析结果（entry 是否为空、材料、化学式）。定位后删除。
 */
@Mixin(TooltipsHandler.class)
public abstract class InfinityTooltipDiagMixin {

    private static boolean gtne$logged = false;

    @Inject(method = "appendTooltips", at = @At("HEAD"), remap = false)
    private static void gtne$diag(ItemStack stack, TooltipFlag flag, List<Component> tooltips, CallbackInfo ci) {
        if (gtne$logged) {
            return;
        }
        Item item = stack.getItem();
        Item ingot = ForgeRegistries.ITEMS.getValue(new ResourceLocation("avaritia", "infinity_ingot"));
        Item nugget = ForgeRegistries.ITEMS.getValue(new ResourceLocation("avaritia", "infinity_nugget"));
        if (item == ingot || item == nugget) {
            gtne$logged = true;
            var entry = ChemicalHelper.getMaterialEntry(item);
            LogUtils.getLogger().info(
                    "GTNEcore: infinity tooltip diag — item={}, entryEmpty={}, entryMaterial={}, formula='{}'",
                    item, entry.isEmpty(), entry.material(), entry.material().getChemicalFormula());
        }
    }
}
