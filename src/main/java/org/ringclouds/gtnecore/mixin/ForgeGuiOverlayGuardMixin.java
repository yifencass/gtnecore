package org.ringclouds.gtnecore.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * overlay 渲染防御：Goety 等第三方 overlay 在玩家加入世界瞬间渲染会 NPE，
 * Forge 只 log 错误不终止——但每帧刷屏（数百 MB 日志）把渲染线程拖死，
 * 表现为"卡在加入世界"。覆盖 overlay 渲染的三条路径：
 * 1. pre() —— fire RenderGuiOverlayEvent$Pre（Goety ClientEvents 监听器 NPE 的主要路径）
 * 2. IGuiOverlay.render() —— 实际渲染
 * 3. post() —— fire 后续事件
 * 对 Goety 命名空间的 overlay 全部 try-catch 吞异常（其余 overlay 原样，
 * 避免掩盖其他 mod 的问题）。
 *
 * 挂点：ForgeGui.render 的 lambda（overlay 渲染循环）。
 * remap=false：ForgeGui/IGuiOverlay 是 Forge 类（无 SRG 映射）。
 */
@Mixin(ForgeGui.class)
public abstract class ForgeGuiOverlayGuardMixin {

    /** ForgeGui.pre/post 是 private（事件分发），@Shadow 访问以便 try-catch 包装。 */
    @Shadow
    private boolean pre(NamedGuiOverlay named, GuiGraphics graphics) {
        throw new AssertionError();
    }

    @Shadow
    private void post(NamedGuiOverlay named, GuiGraphics graphics) {
        throw new AssertionError();
    }

    private static boolean isGoety(NamedGuiOverlay named) {
        return named != null && named.id() != null && "goety".equals(named.id().getNamespace());
    }

    @Redirect(method = "lambda$render$0(Lnet/minecraft/client/gui/GuiGraphics;FLnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;)V",
            remap = false, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/client/gui/overlay/ForgeGui;pre(Lnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;Lnet/minecraft/client/gui/GuiGraphics;)Z"))
    private boolean gtne$guardPre(ForgeGui gui, NamedGuiOverlay named, GuiGraphics graphics) {
        if (isGoety(named)) {
            try {
                return this.pre(named, graphics);
            } catch (Exception ignored) {
                return false; // Pre 事件异常：跳过该 overlay 渲染
            }
        }
        return this.pre(named, graphics);
    }

    @Redirect(method = "lambda$render$0(Lnet/minecraft/client/gui/GuiGraphics;FLnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;)V",
            remap = false, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/client/gui/overlay/IGuiOverlay;render(Lnet/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
    private void gtne$guardOverlay(IGuiOverlay overlay, ForgeGui gui, GuiGraphics graphics,
                                   float partialTick, int width, int height) {
        if (overlay.getClass().getName().startsWith("com.Polarice3.Goety")) {
            try {
                overlay.render(gui, graphics, partialTick, width, height);
            } catch (Exception ignored) {
                // Goety overlay 渲染异常：吞掉，避免每帧异常风暴
            }
        } else {
            overlay.render(gui, graphics, partialTick, width, height);
        }
    }

    @Redirect(method = "lambda$render$0(Lnet/minecraft/client/gui/GuiGraphics;FLnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;)V",
            remap = false, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/client/gui/overlay/ForgeGui;post(Lnet/minecraftforge/client/gui/overlay/NamedGuiOverlay;Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void gtne$guardPost(ForgeGui gui, NamedGuiOverlay named, GuiGraphics graphics) {
        if (isGoety(named)) {
            try {
                this.post(named, graphics);
            } catch (Exception ignored) {
                // Goety Post 事件异常：吞掉
            }
        } else {
            this.post(named, graphics);
        }
    }
}
