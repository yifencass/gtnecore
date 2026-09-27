package org.ringclouds.gtnecore.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 完全夜视（佩戴 GT之环）配套：原版 LightTexture 只在玩家带夜视效果时才调用
 * getNightVisionScale（否则直接 f=0，亮度提升不生效）。这里把 hasEffect(NIGHT_VISION)
 * 检查重定向为"佩戴者恒 true"，配合 GameRendererNightVisionMixin（scale 恒 1.0）
 * 走完整夜视亮度提升路径。
 *
 * 注意：调用点的 receiver 是 LocalPlayer（this.minecraft.player 字段），
 * target 的 owner 必须写 LocalPlayer，写父类 LivingEntity 会导致注入检查失败。
 */
@Mixin(LightTexture.class)
public abstract class LightTextureNightVisionMixin {

    @Redirect(method = "updateLightTexture",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z"))
    private boolean gtne$fakeNightVisionEffect(LocalPlayer player, MobEffect effect) {
        if (effect == MobEffects.NIGHT_VISION && HaloAbilities.isWearingHalo(player)) {
            return true;
        }
        return player.hasEffect(effect);
    }
}
