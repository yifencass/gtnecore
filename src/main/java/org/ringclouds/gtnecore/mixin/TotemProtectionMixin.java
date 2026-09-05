package org.ringclouds.gtnecore.mixin;

import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 免死（成就绑定，无需佩戴）：持有"获得 GT之环"成就的玩家死亡时——
 * 取消死亡 → 回满血 → 清除全部负面效果 → 播放不死图腾式动画（显示 GT之环，
 * 自定义实体事件 id）。
 *
 * 锚点：原版图腾的判定点 checkTotemDeathProtection（血量 ≤0 时调用）。
 */
@Mixin(LivingEntity.class)
public abstract class TotemProtectionMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void gtne$haloDeathProtection(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            return;
        }
        if (!HaloAbilities.hasHaloAbility(player)) {
            return;
        }
        // 免死：回满血 + 清除负面效果
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        // 不死图腾式动画（服务端发自定义实体事件，客户端显示 GT之环）
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(
                    new ClientboundEntityEventPacket(serverPlayer, HaloAbilities.TOTEM_ANIM_EVENT_ID));
        }
        cir.setReturnValue(true);
    }
}
