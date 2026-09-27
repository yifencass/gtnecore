package org.ringclouds.gtnecore.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;
import org.ringclouds.gtnecore.client.WaveNameState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 文字渲染注入（完整移植 1.21.1 项目 FontStringRenderOutputMixin 设计思路）：
 * Font.drawInBatch 逐字符回调 Font$StringRenderOutput.accept(index, style, codePoint)。
 *
 * 1. 波浪（#gtnecore:wave_name）：accept HEAD 调整实例 y 坐标 —— 字符提交前
 *    vanilla 的 renderChar 直接用偏移后的坐标绘制，无需 cancel 重绘。
 *    y 是相对增量：上一个字符的偏移撤销、当前字符的偏移施加。
 * 2. 双层文字（#gtnecore:double_layer_text）：@Redirect renderChar —— 同一字符
 *    绘制两次：底层灰色（左下偏移，dim 0.4）+ 顶层主色（右上偏移）；
 *    返回值（advance）只取顶层，与 1.21.1 一致。
 *
 * 两种效果独立可叠加（波浪改 y、双层改绘制），仅作用于首个文字行（物品名行），
 * 生命周期由 GuiGraphicsWaveCaptureMixin / ClientTextTooltipConsumeMixin 管理。
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontStringRenderOutputMixin {

    @Shadow
    private float y;

    @Shadow
    private boolean dropShadow;

    /** 上一个字符施加的偏移（撤销用）。 */
    @Unique
    private float gtne$lastOffset;

    @Inject(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", at = @At("HEAD"))
    private void gtne$applyWave(int index, Style style, int codePoint,
                                CallbackInfoReturnable<Boolean> cir) {
        if (WaveNameState.isWave()) {
            float offset = WaveNameState.computeYOffset(index);
            this.y = this.y - this.gtne$lastOffset + offset;
            this.gtne$lastOffset = offset;
        } else if (this.gtne$lastOffset != 0.0F) {
            this.y -= this.gtne$lastOffset;
            this.gtne$lastOffset = 0.0F;
        }
    }

    @Redirect(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;renderChar(Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;ZZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V"))
    private void gtne$effectsRender(Font font, BakedGlyph glyph, boolean bold, boolean italic,
                                    float boldOffset, float x, float y, Matrix4f matrix,
                                    VertexConsumer consumer, float r, float g, float b,
                                    float a, int light) {
        if (WaveNameState.isDoubleLayer()) {
            float dim = this.dropShadow ? 0.25f : 1.0f;
            // 双层与波浪组合（刻意设计，无静态偏移）：灰层反向但幅度减半
            // （y - 1.5×offset = 反向 0.5×offset）——相对运动速度 1.5×，
            // 比全幅度反向（2×）平滑，同时保留反向错动感
            float bottomY = WaveNameState.isWave() ? y - 1.5f * this.gtne$lastOffset : y;
            // 底层 — 灰色阴影（反向、幅度 0.5×，同位置）
            ((FontInvoker) font).gtne$invokeRenderChar(glyph, bold, italic, boldOffset,
                    x, bottomY, matrix, consumer,
                    0.4f * dim, 0.4f * dim, 0.4f * dim, a, light);
            // 顶层 — 主色（正向波浪，同位置，遮挡灰层）
            ((FontInvoker) font).gtne$invokeRenderChar(glyph, bold, italic, boldOffset,
                    x, y, matrix, consumer, r, g, b, a, light);
        } else {
            ((FontInvoker) font).gtne$invokeRenderChar(glyph, bold, italic, boldOffset,
                    x, y, matrix, consumer, r, g, b, a, light);
        }
    }
}
