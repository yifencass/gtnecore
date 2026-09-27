package org.ringclouds.gtnecore.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 饥饿冻结（佩戴 GT之环）：跳过 FoodData.tick（不消耗饥饿/饱和度），
 * 并把饥饿值钳到 20、饱和度钳到 5（永远满）。
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    @Shadow
    private int foodLevel;

    @Shadow
    private float saturationLevel;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void gtne$freezeHunger(Player player, CallbackInfo ci) {
        if (HaloAbilities.isWearingHalo(player)) {
            this.foodLevel = 20;
            this.saturationLevel = 5.0F;
            ci.cancel();
        }
    }
}
