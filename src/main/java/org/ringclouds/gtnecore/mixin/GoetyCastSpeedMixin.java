package org.ringclouds.gtnecore.mixin;

import com.Polarice3.Goety.api.magic.ISpell;
import com.Polarice3.Goety.common.items.magic.DarkWand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Goety 施法加速（佩戴 GT之环）：DarkWand.setSpellConditions 把法术的
 * castDuration 写入 wand 的 Duration NBT（施法按住时长 + 法术持续时间），
 * 这里除以 256（最小 1）——施法整体压缩 256 倍。
 * Duration NBT 每次施法都重新推导，不会累积减半。
 *
 * remap=false：DarkWand / ISpell 都是 Goety mod 类（SEHelper 重定向同款先例）。
 */
@Mixin(DarkWand.class)
public abstract class GoetyCastSpeedMixin {

    @Redirect(method = "setSpellConditions", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lcom/Polarice3/Goety/api/magic/ISpell;castDuration(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)I"))
    private int gtne$speedUpCasting(ISpell spell, LivingEntity entity, ItemStack stack) {
        int duration = spell.castDuration(entity, stack);
        if (HaloAbilities.isWearingHalo(entity) && duration > 0) {
            return Math.max(1, duration / 256);
        }
        return duration;
    }
}
