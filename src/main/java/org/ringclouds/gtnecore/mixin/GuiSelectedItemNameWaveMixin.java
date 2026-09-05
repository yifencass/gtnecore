package org.ringclouds.gtnecore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.ringclouds.gtnecore.client.GtneModernTextRenderer;
import org.ringclouds.gtnecore.client.WaveNameState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 快捷栏切换物品时弹出的物品名（HUD 路径，不走 tooltip 管线）：
 * 注入 Gui.renderSelectedItemName 单参重载（该重载有 official↔srg 映射；双参重载
 * 在 Forge 1.20.1 映射表缺失，无法 remap，发布环境（srg）注入会失败）。
 *
 * - HEAD：根据 lastToolHighlight 是否带 #gtnecore:wave_name / #gtnecore:double_layer_text
 *   tag 激活特效状态（pending + consume）；特效激活时**自绘物品名**（复刻原版布局：
 *   居中、顶部、白字阴影；有 Modern UI 走 GtneModernTextRenderer 管线保留其字体，
 *   无 Modern UI 走原版 drawString——StringRenderOutput mixin 的波浪/双层照常生效），
 *   并 cancel 原版双参渲染（其 drawString 被 Modern UI @Overwrite 无法注入）；
 * - RETURN：清理状态。
 */
@Mixin(Gui.class)
public abstract class GuiSelectedItemNameWaveMixin {

    @Shadow
    protected ItemStack lastToolHighlight;

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true)
    private void gtne$captureHotbarName(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (this.lastToolHighlight != null && !this.lastToolHighlight.isEmpty()) {
            WaveNameState.setPending(this.lastToolHighlight.is(WaveNameState.WAVE_TAG));
            WaveNameState.setPendingDoubleLayer(this.lastToolHighlight.is(WaveNameState.DOUBLE_LAYER_TAG));
            WaveNameState.consume();
            if (WaveNameState.isWave() || WaveNameState.isDoubleLayer()) {
                renderHotbarNameSelf(guiGraphics);
                ci.cancel();
            }
        } else {
            WaveNameState.clear();
        }
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("RETURN"))
    private void gtne$clearHotbarName(GuiGraphics guiGraphics, CallbackInfo ci) {
        WaveNameState.clear();
    }

    /** 复刻原版物品名渲染（居中 + 顶部 + 白字阴影），特效走 Modern UI 管线或原版 drawString。 */
    private void renderHotbarNameSelf(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        MutableComponent name = Component.empty().append(this.lastToolHighlight.getHoverName())
                .withStyle(this.lastToolHighlight.getRarity().getStyleModifier());
        if (!this.lastToolHighlight.hasCustomHoverName()) {
            name.withStyle(ChatFormatting.ITALIC);
        }
        Component finalName = this.lastToolHighlight.getHighlightTip(name);
        Font font = mc.font;
        int textWidth = font.width(finalName);
        int x = Math.max(59, mc.getWindow().getGuiScaledWidth() / 2 - textWidth / 2);
        int y = 6;
        if (GtneModernTextRenderer.isModernUi(font)) {
            PoseStack pose = guiGraphics.pose();
            GtneModernTextRenderer.drawWaveLine(font, finalName.getVisualOrderText(),
                    (float) x, (float) y, 0xFFFFFF, true, pose.last().pose(), guiGraphics.bufferSource(),
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
        } else {
            guiGraphics.drawString(font, finalName, x, y, 0xFFFFFF);
        }
    }
}
