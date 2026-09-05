package org.ringclouds.gtnecore.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 无敌第一层：所有伤害类型不可选中。
 *
 * isInvulnerableTo 声明在 Entity 上（LivingEntity 继承），因此注入目标必须是
 * Entity（描述符带 owner 时 mixin 只在该类里查找，锚 LivingEntity 会找不到）。
 * 只对佩戴 GT之环的活体生效，其余实体不受影响。
 */
@Mixin(Entity.class)
public abstract class EntityInvulnerableMixin {

    @Inject(method = "isInvulnerableTo(Lnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"), cancellable = true)
    private void gtne$allDamageImmune(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LivingEntity living && HaloAbilities.isWearingHalo(living)) {
            cir.setReturnValue(true);
        }
    }
}
