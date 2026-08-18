package org.ringclouds.gtnecore.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.ringclouds.gtnecore.client.WaveNameState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 波浪文字的捕获点（完整移植 1.21.1 项目 GuiGraphicsTooltipMixin）：
 * - HEAD：读 GuiGraphics.tooltipStack（renderTooltip(ItemStack) 等重载进入时设置）
 *   判断物品是否带 #gtnecore:wave_name tag，设 PENDING（set-or-clear）；
 * - TAIL：清 PENDING + tooltipStack = EMPTY（对齐 1.21.1，防止残留到下一 tooltip）。
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsWaveCaptureMixin {

    @Shadow
    private ItemStack tooltipStack;

    @Inject(method = "renderTooltipInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("HEAD"))
    private void gtne$captureWave(Font font, List<ClientTooltipComponent> components, int mouseX,
                                  int mouseY, ClientTooltipPositioner positioner, CallbackInfo ci) {
        if (this.tooltipStack != null && !this.tooltipStack.isEmpty()) {
            WaveNameState.setPending(this.tooltipStack.is(WaveNameState.WAVE_TAG));
            WaveNameState.setPendingDoubleLayer(this.tooltipStack.is(WaveNameState.DOUBLE_LAYER_TAG));
        } else {
            WaveNameState.setPending(false);
            WaveNameState.setPendingDoubleLayer(false);
        }
    }

    @Inject(method = "renderTooltipInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V",
            at = @At("TAIL"))
    private void gtne$clearWave(Font font, List<ClientTooltipComponent> components, int mouseX,
                                int mouseY, ClientTooltipPositioner positioner, CallbackInfo ci) {
        WaveNameState.clearPending();
        this.tooltipStack = ItemStack.EMPTY;
    }
}
