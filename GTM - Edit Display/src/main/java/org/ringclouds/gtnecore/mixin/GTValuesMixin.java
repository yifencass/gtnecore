package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.api.GTValues;
import org.ringclouds.gtnecore.voltages.GtneVoltages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@link GTValues} 静态初始化完成后立即安装 GTNEcore 的 22 档电压表。
 *
 * 原理：任何读取 GTValues.V/VN/VNF/VC/VCM/TIER_COUNT 的类都必然先触发
 * GTValues 的类初始化，因此在本注入点（clinit RETURN）覆写字段后，
 * 所有读取方 getstatic 拿到的都是新表，无需逐类重定向。
 */
@Mixin(GTValues.class)
public abstract class GTValuesMixin {

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void gtne$installVoltageTables(CallbackInfo ci) {
        GtneVoltages.install();
    }
}
