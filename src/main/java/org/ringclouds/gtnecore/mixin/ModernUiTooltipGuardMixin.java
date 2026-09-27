package org.ringclouds.gtnecore.mixin;

import net.minecraftforge.client.event.RenderTooltipEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁用 Modern UI 的 tooltip 接管（tooltip 背景透明问题的根因）。
 *
 * UIManagerForge 监听 RenderTooltipEvent$Pre：
 * - HIGH（onRenderTooltipH）：sTooltip 开启时用 Modern UI 的 TooltipRenderer.drawExtTooltip
 *   渲染 tooltip（其背景样式透明/主题化）；
 * - LOW（onRenderTooltipL）：sTooltip 开启时 setCanceled(true) —— 原版
 *   renderTooltipInternal 整体取消，fillGradient 背景一次都不会执行（表现为
 *   "tooltip 透明、仅剩字符和外框"）。
 *
 * 两个监听全部 cancel：Modern UI 完全不碰 tooltip，原版背景/文字/边框恢复。
 * 保留 Modern UI 的字体/UI 等其他功能。@Pseudo：modern-ui 是运行时 mod，
 * 目标类缺失时静默跳过（未装 Modern UI 的环境不受影响）。
 */
@Pseudo
@Mixin(targets = "icyllis.modernui.mc.forge.UIManagerForge")
public abstract class ModernUiTooltipGuardMixin {

    @Inject(method = "onRenderTooltipH(Lnet/minecraftforge/client/event/RenderTooltipEvent$Pre;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gtne$blockMuiTooltipDraw(RenderTooltipEvent.Pre event, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onRenderTooltipL(Lnet/minecraftforge/client/event/RenderTooltipEvent$Pre;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void gtne$blockMuiTooltipCancel(RenderTooltipEvent.Pre event, CallbackInfo ci) {
        ci.cancel();
    }
}
