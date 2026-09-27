package org.ringclouds.gtnecore.mixin.ae2;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 编码终端输入格访问器（JEI + 键补电路用） */
@Mixin(value = PatternEncodingTermMenu.class, remap = false)
public interface PatternEncodingTermMenuAccessor {

    @Accessor("encodedInputsInv")
    ConfigInventory gtnecore$getEncodedInputs();
}
