package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 原始水泵结构重写（输出仓可放任何水泵盖板）：
 * 原版 pattern 只有一个固定输出仓位（aisle 2 的 'H'），这里重定向
 * GTMultiMachines.lambda$static$42 里的 FactoryBlockPattern.build()，
 * 重建结构：'X'（水泵盖板）位置同时接受输出仓（PUMP_FLUID_HATCH /
 * FLUID_EXPORT_HATCH），'H' 专属位取消——输出仓可放在任意盖板处。
 *
 * 结构（与原版一致，默认 start() 方向）：
 *   aisle1: "XXXX"  aisle2: "XXXX"  aisle3: "SXXX"
 *           "##F#"          "F##F"          "##F#"
 *           "##F#"          "FFFF"          "##F#"
 * S=控制器 X=泵盖板(或输出仓) F=防腐木框架 #=任意
 * 输出仓谓词 setMaxGlobalLimited(1)：结构里最多 1 个输出仓，多放不成型。
 *
 * remap=false：GTMultiMachines 是 GTCEu mod 类（lambda 方法无 SRG 映射）。
 */
@Mixin(GTMultiMachines.class)
public abstract class PrimitivePumpPatternMixin {

    @Redirect(method = "lambda$static$42(Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;)Lcom/gregtechceu/gtceu/api/pattern/BlockPattern;",
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/api/pattern/FactoryBlockPattern;build()Lcom/gregtechceu/gtceu/api/pattern/BlockPattern;"),
            remap = false)
    private static BlockPattern gtne$pumpPattern(FactoryBlockPattern ignored, MultiblockMachineDefinition definition) {
        return FactoryBlockPattern.start()
                .aisle("XXXX", "##F#", "##F#")
                .aisle("XXXX", "F##F", "FFFF")
                .aisle("SXXX", "##F#", "##F#")
                .where('S', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .where('X', Predicates.blocks(GTBlocks.CASING_PUMP_DECK.get())
                        .or(Predicates.abilities(PartAbility.PUMP_FLUID_HATCH)
                                .or(Predicates.blocks(GTMachines.FLUID_EXPORT_HATCH[0].get(),
                                        GTMachines.FLUID_EXPORT_HATCH[1].get()))
                                .setMaxGlobalLimited(1)))
                .where('F', Predicates.frames(GTMaterials.TreatedWood))
                .where('#', Predicates.any())
                .build();
    }
}
