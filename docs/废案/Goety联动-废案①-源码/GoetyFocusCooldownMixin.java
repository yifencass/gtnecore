package org.ringclouds.gtnecore.mixin;

import com.Polarice3.Goety.common.capabilities.soulenergy.FocusCooldown;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Goety 聚晶冷却清除：聚晶不走原版 ItemCooldowns，用自研 FocusCooldown
 * capability（cooldowns / cooldownsSpecific 两张表，tick(Player, Level) 递减）。
 * 佩戴 GT之环时直接清空两张表并跳过递减。
 *
 * remap=false：FocusCooldown 是 Goety mod 类（无 SRG 映射，Goety 注入先例）。
 */
@Mixin(FocusCooldown.class)
public abstract class GoetyFocusCooldownMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtne$clearFocusCooldowns(Player player, Level level, CallbackInfo ci) {
        if (HaloAbilities.isWearingHalo(player)) {
            FocusCooldown self = (FocusCooldown) (Object) this;
            self.cooldowns.clear();
            self.cooldownsSpecific.clear();
            ci.cancel();
        }
    }
}
