package org.ringclouds.gtnecore.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.ringclouds.gtnecore.client.GtneModernTextRenderer;
import org.ringclouds.gtnecore.client.WaveNameState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Modern UI 兼容：tooltip 文字行（ClientTextTooltip.renderText）内的
 * Font.drawInBatch 调用被 Modern UI @Overwrite —— 特效激活（首行物品名，
 * 由 ClientTextTooltipConsumeMixin 的 HEAD consume 激活状态）时改用 Modern UI
 * 管线逐字符渲染，否则原样调用。与 ConsumeMixin 的 HEAD/RETURN 生命周期配合：
 * 状态在 renderText 调用期间 active，@Redirect 恰好拦截其中的 drawInBatch。
 */
@Mixin(ClientTextTooltip.class)
public abstract class ClientTextTooltipRenderMixin {

    @Redirect(method = "renderText(Lnet/minecraft/client/gui/Font;IILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private int gtne$modernDrawTooltipLine(Font font, FormattedCharSequence text, float x, float y,
                                           int color, boolean shadow, Matrix4f pose, MultiBufferSource buffer,
                                           Font.DisplayMode mode, int bgColor, int light) {
        if ((WaveNameState.isWave() || WaveNameState.isDoubleLayer()) && GtneModernTextRenderer.isModernUi(font)) {
            return (int) GtneModernTextRenderer.drawWaveLine(
                    font, text, x, y, color, shadow, pose, buffer, mode, bgColor, light);
        }
        return font.drawInBatch(text, x, y, color, shadow, pose, buffer, mode, bgColor, light);
    }
}
