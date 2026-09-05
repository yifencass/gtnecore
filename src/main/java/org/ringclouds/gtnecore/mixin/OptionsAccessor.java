package org.ringclouds.gtnecore.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 Options 的 private gamma（亮度）选项，供伽马覆写使用。 */
@Mixin(Options.class)
public interface OptionsAccessor {

    @Accessor("gamma")
    OptionInstance<Double> gtne$getGamma();
}
