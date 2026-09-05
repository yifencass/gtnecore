package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTCovers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 覆盖板注册档位扩展到全 22 档：原版 GTCovers.ALL_TIERS 只到 OpV（isHighTier ? 13 : 8），
 * 新档位（>13）没有任何覆盖板定义。重定向 <clinit> 里两次 tiersBetween：
 * ALL_TIERS = 1..V.length-1，ALL_TIERS_WITH_ULV = 0..V.length-1。
 *
 * remap=false：GTCovers/GTValues 是 GTCEu mod 类（无 SRG 映射）。
 */
@Mixin(GTCovers.class)
public abstract class GTCoversTiersMixin {

    @Redirect(method = "<clinit>", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/api/GTValues;tiersBetween(II)[I",
                    ordinal = 0))
    private static int[] gtne$allTiers(int from, int to) {
        return GTValues.tiersBetween(1, GTValues.V.length - 1);
    }

    @Redirect(method = "<clinit>", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/api/GTValues;tiersBetween(II)[I",
                    ordinal = 1))
    private static int[] gtne$allTiersWithUlv(int from, int to) {
        return GTValues.tiersBetween(0, GTValues.V.length - 1);
    }
}
