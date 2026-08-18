package org.ringclouds.gtnecore.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.ringclouds.gtnecore.client.WaveNameState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 波浪文字的生命周期消费点（借鉴 1.21.1 项目 ClientTextTooltipMixin）：
 * - HEAD：consume PENDING → active（只消费一次；下次 renderText 时 PENDING 已 false，
 *   active 被清，因此效果天然只作用于首个文字行 = 物品名行）
 * - RETURN：clear active，避免残留到下一行/下一 tooltip
 */
@Mixin(ClientTextTooltip.class)
public abstract class ClientTextTooltipConsumeMixin {

    @Inject(method = "renderText(Lnet/minecraft/client/gui/Font;IILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At("HEAD"))
    private void gtne$consumeWave(Font font, int x, int y, Matrix4f matrix,
                                  MultiBufferSource.BufferSource buffer, CallbackInfo ci) {
        WaveNameState.consume();
    }

    @Inject(method = "renderText(Lnet/minecraft/client/gui/Font;IILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At("RETURN"))
    private void gtne$clearWave(Font font, int x, int y, Matrix4f matrix,
                                MultiBufferSource.BufferSource buffer, CallbackInfo ci) {
        WaveNameState.clear();
    }
}
