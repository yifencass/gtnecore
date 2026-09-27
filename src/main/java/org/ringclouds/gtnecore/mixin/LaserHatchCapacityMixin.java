package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableLaserContainer;
import com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * GTNEcore 22 档电压（MAX=21，V[21] = 8×4²¹）下，激光靶仓/源仓的缓存
 * V[tier]×64×amperage 恰好 = 2⁶³（MAX 4096A）→ long 回绕成 Long.MIN_VALUE（-9.22e18）。
 * 构造时对 capacity 参数饱和：负值钳到 Long.MAX_VALUE。
 */
@Mixin(value = LaserHatchPartMachine.class, remap = false)
public class LaserHatchCapacityMixin {

    @ModifyArg(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/api/machine/trait/NotifiableLaserContainer;emitterContainer(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;JJJ)Lcom/gregtechceu/gtceu/api/machine/trait/NotifiableLaserContainer;"),
            index = 1)
    private long gtne$saturateEmitterCapacity(long capacity) {
        return capacity < 0 ? Long.MAX_VALUE : capacity;
    }

    @ModifyArg(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/api/machine/trait/NotifiableLaserContainer;receiverContainer(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;JJJ)Lcom/gregtechceu/gtceu/api/machine/trait/NotifiableLaserContainer;"),
            index = 1)
    private long gtne$saturateReceiverCapacity(long capacity) {
        return capacity < 0 ? Long.MAX_VALUE : capacity;
    }
}