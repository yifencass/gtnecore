package org.ringclouds.gtnecore.mixin;

import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ForgeConfigSpec.ConfigValue.get() 防护：config 尚未加载（shimmer 等在 shader 重载
 * 早期读配置）时，返回默认值而不是抛 "Cannot get config value before config is loaded"
 * （该异常仅 dev 环境抛，production 不抛；此处统一降级为默认值，避免崩溃）。
 */
@Mixin(value = ForgeConfigSpec.ConfigValue.class, remap = false)
public class ForgeConfigSpecGetGuardMixin {

    @Shadow
    private ForgeConfigSpec spec;

    @Shadow
    public Object getDefault() {
        return null;
    }

    @Inject(method = "get()Ljava/lang/Object;", at = @At("HEAD"), cancellable = true)
    private void guardUnloaded(CallbackInfoReturnable<Object> cir) {
        if (spec == null || !spec.isLoaded()) {
            cir.setReturnValue(getDefault());
        }
    }
}