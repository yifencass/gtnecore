package org.ringclouds.gtnecore.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.ringclouds.gtnecore.client.WaveNameState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 快捷栏切换物品时弹出的物品名（HUD 路径，不走 tooltip 管线）：
 * 注入 Gui.renderSelectedItemName —— HEAD 时根据 lastToolHighlight（当前高亮物品）
 * 是否带 #gtnecore:wave_name tag 激活 wave（pending + consume），RETURN 清理。
 * 文字本身由 GuiGraphics.drawString → Font.drawInBatch → StringRenderOutput.accept
 * 渲染，复用 FontStringRenderOutputMixin 的逐字符波形注入。
 *
 * 注意：renderSelectedItemName 在 toolHighlightTimer <= 0 时直接 return（不渲染），
 * 此时 HEAD 激活的 wave 在 RETURN 被 clear，无副作用。
 */
@Mixin(Gui.class)
public abstract class GuiSelectedItemNameWaveMixin {

    @Shadow
    protected ItemStack lastToolHighlight;

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", remap = false,
            at = @At("HEAD"))
    private void gtne$captureHotbarName(GuiGraphics guiGraphics, int width, CallbackInfo ci) {
        if (this.lastToolHighlight != null && !this.lastToolHighlight.isEmpty()) {
            WaveNameState.setPending(this.lastToolHighlight.is(WaveNameState.WAVE_TAG));
            WaveNameState.setPendingDoubleLayer(this.lastToolHighlight.is(WaveNameState.DOUBLE_LAYER_TAG));
            WaveNameState.consume();
        } else {
            WaveNameState.clear();
        }
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", remap = false,
            at = @At("RETURN"))
    private void gtne$clearHotbarName(GuiGraphics guiGraphics, int width, CallbackInfo ci) {
        WaveNameState.clear();
    }
}
