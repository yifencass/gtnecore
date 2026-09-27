package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.common.cover.ConveyorCover;
import com.gregtechceu.gtceu.common.cover.FluidRegulatorCover;
import com.gregtechceu.gtceu.common.cover.PumpCover;
import com.gregtechceu.gtceu.common.cover.RobotArmCover;
import net.minecraft.core.Direction;
import org.ringclouds.gtnecore.cover.CoverRateScaling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 覆盖板速率重写：4 参构造（GTCovers 按档创建用）最终走 5 参构造
 * (def, coverable, side, tier, rate)——在 rate 参数处按扩展公式重算：
 * 泵系（泵/流体调节器）64×4^(tier-1)、传送带系（传送带/机械臂）2×4^tier，
 * 原版 tier 6 后封顶，现在按 ×4/档延续到新档位（见 CoverRateScaling）。
 * 公式对 tier ≤ 6 与原版一致，仅扩展高位。
 *
 * remap=false：GTCEu mod 类。
 */
public abstract class CoverRateMixins {

    // 构造器 HEAD 注入在 super() 之前，this 不可用 → handler 必须是 static
    @Mixin(PumpCover.class)
    public abstract static class PumpCoverRateMixin {
        @ModifyVariable(method = "<init>(Lcom/gregtechceu/gtceu/api/cover/CoverDefinition;Lcom/gregtechceu/gtceu/api/capability/ICoverable;Lnet/minecraft/core/Direction;II)V",
                ordinal = 1, argsOnly = true, at = @At("HEAD"), remap = false)
        private static int gtne$pumpRate(int rate, CoverDefinition def, ICoverable coverable, Direction side, int tier) {
            return CoverRateScaling.pumpRate(tier);
        }
    }

    @Mixin(ConveyorCover.class)
    public abstract static class ConveyorCoverRateMixin {
        @ModifyVariable(method = "<init>(Lcom/gregtechceu/gtceu/api/cover/CoverDefinition;Lcom/gregtechceu/gtceu/api/capability/ICoverable;Lnet/minecraft/core/Direction;II)V",
                ordinal = 1, argsOnly = true, at = @At("HEAD"), remap = false)
        private static int gtne$conveyorRate(int rate, CoverDefinition def, ICoverable coverable, Direction side, int tier) {
            return CoverRateScaling.conveyorRate(tier);
        }
    }

    @Mixin(RobotArmCover.class)
    public abstract static class RobotArmCoverRateMixin {
        @ModifyVariable(method = "<init>(Lcom/gregtechceu/gtceu/api/cover/CoverDefinition;Lcom/gregtechceu/gtceu/api/capability/ICoverable;Lnet/minecraft/core/Direction;II)V",
                ordinal = 1, argsOnly = true, at = @At("HEAD"), remap = false)
        private static int gtne$robotArmRate(int rate, CoverDefinition def, ICoverable coverable, Direction side, int tier) {
            return CoverRateScaling.conveyorRate(tier);
        }
    }

    @Mixin(FluidRegulatorCover.class)
    public abstract static class FluidRegulatorCoverRateMixin {
        @ModifyVariable(method = "<init>(Lcom/gregtechceu/gtceu/api/cover/CoverDefinition;Lcom/gregtechceu/gtceu/api/capability/ICoverable;Lnet/minecraft/core/Direction;II)V",
                ordinal = 1, argsOnly = true, at = @At("HEAD"), remap = false)
        private static int gtne$fluidRegulatorRate(int rate, CoverDefinition def, ICoverable coverable, Direction side, int tier) {
            return CoverRateScaling.pumpRate(tier);
        }
    }
}
