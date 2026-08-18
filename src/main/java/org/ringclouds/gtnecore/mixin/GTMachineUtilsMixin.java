package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import org.ringclouds.gtnecore.voltages.GtneVoltages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GTMachineUtils 在 &lt;clinit&gt; 时用 tiersBetween 缓存档位数组（ELECTRIC_TIERS 等，
 * 上限取决于 isHighTier 配置，最高 13/14 档）。GTCEu 原生机器/部件注册全部走这些数组，
 * 不扩它们则 15-21 档永远没有原生内容。
 *
 * 这里在 clinit 结束后覆写这 5 个 static final 数组，使原生注册延伸到 21 档
 * （UHV..MAX 顺延的新档位全部有机器/部件）。
 */
@Mixin(GTMachineUtils.class)
public abstract class GTMachineUtilsMixin {

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void gtne$extendTierArrays(CallbackInfo ci) {
        GtneVoltages.installMachineUtils();
    }
}
