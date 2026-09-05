package org.ringclouds.gtnecore.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 无敌二、三层（佩戴 GT之环）：
 * L2 hurt @Inject HEAD 取消 —— 拦截伤害调用本身（void/kill/wither/爆炸等全走 hurt）
 * L3 actuallyHurt 伤害量归 0 —— 兜底"绕过 hurt 直接 setHealth"的路径；
 *    伤害 0 → 血量不变 → 原生受伤音效/屏幕震动不触发
 * （L1 isInvulnerableTo 见 EntityInvulnerableMixin —— 该方法声明在 Entity 上）
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void gtne$blockHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (HaloAbilities.isWearingHalo((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "actuallyHurt", ordinal = 0, argsOnly = true, at = @At("HEAD"))
    private float gtne$zeroDamage(float amount) {
        if (HaloAbilities.isWearingHalo((LivingEntity) (Object) this)) {
            return 0.0F;
        }
        return amount;
    }
}
