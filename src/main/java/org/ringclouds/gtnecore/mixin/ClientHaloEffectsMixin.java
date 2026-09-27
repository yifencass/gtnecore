package org.ringclouds.gtnecore.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.ringclouds.gtnecore.item.GtnecoreItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端免死动画：服务端发来的自定义实体事件（TOTEM_ANIM_EVENT_ID）→
 * 屏幕中央显示 GT之环（不死图腾式动画）。伽马覆写走 HaloClientEvents（事件，无需 mixin）。
 */
@Mixin(LivingEntity.class)
public abstract class ClientHaloEffectsMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void gtne$haloTotemAnimation(byte event, CallbackInfo ci) {
        if (event == HaloAbilities.TOTEM_ANIM_EVENT_ID) {
            Minecraft mc = Minecraft.getInstance();
            if ((Object) this == mc.player) {
                mc.gameRenderer.displayItemActivation(new ItemStack(GtnecoreItems.GT_HALO.get()));
                ci.cancel();
            }
        }
    }
}
