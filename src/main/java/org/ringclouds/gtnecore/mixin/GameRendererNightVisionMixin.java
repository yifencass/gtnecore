package org.ringclouds.gtnecore.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GameRenderer 上的 GTNEcore 防护与效果：
 * 1. 完全夜视（佩戴 GT之环）：getNightVisionScale 恒返回 1.0。
 * 2. 世界渲染根上防御：原版 GameRenderer.render 只检查 level != null 不检查
 *    player（时序假设 level 创建时 player 已存在），渲染管线 mod（oculus/
 *    shimmer 等）打破时序后，player null 时 renderLevel 照常执行 → 内部
 *    解引用 null player 崩溃（oSpinningEffectIntensity / Camera.setup 等
 *    逐个爆）。根上修：@Redirect render 里 getfield Minecraft.level——
 *    player null 时伪装成 level null（触发原版 ifnull 跳过世界渲染），
 *    renderLevel 根本不会被调用，无需逐个打地鼠。
 * 3. renderLevel 内部字段防御（纵深）：即便有其他路径进入 renderLevel，
 *    两个睡眠旋转强度字段读取在 player null 时返回 0。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererNightVisionMixin {

    // 1.20.1 的 getNightVisionScale 是 static 方法 → handler 必须是 static
    @Inject(method = "getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F",
            at = @At("HEAD"), cancellable = true)
    private static void gtne$fullNightVision(LivingEntity entity, float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (HaloAbilities.isWearingHalo(entity)) {
            cir.setReturnValue(1.0F);
        }
    }

    /** 根上防御：player null 时伪装 level null → 原版跳过世界渲染。 */
    @Redirect(method = "render(FJZ)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;"))
    private ClientLevel gtne$guardRenderEntry(Minecraft mc) {
        return mc.player != null ? mc.level : null;
    }

    /** 睡眠旋转强度（旧值）：player null 时返回 0，避免 NPE。 */
    @Redirect(method = "renderLevel",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/LocalPlayer;oSpinningEffectIntensity:F"))
    private float gtne$guardOldSpinIntensity(net.minecraft.client.player.LocalPlayer player) {
        return player != null ? player.oSpinningEffectIntensity : 0.0F;
    }

    /** 睡眠旋转强度（当前值）：同上。 */
    @Redirect(method = "renderLevel",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/LocalPlayer;spinningEffectIntensity:F"))
    private float gtne$guardSpinIntensity(net.minecraft.client.player.LocalPlayer player) {
        return player != null ? player.spinningEffectIntensity : 0.0F;
    }

    /**
     * GUI 渲染 GL 状态恢复（shimmer 兼容）：shimmer 的渲染管线在 renderLevel 内
     * 切换 GL 状态（MRT glDrawBuffers/混合/深度等），残留状态会让后续 GUI 的
     * RenderType.gui 批次（tooltip 背景 fillGradient）渲染失效（背景透明）。
     * renderLevel RETURN（在 shimmer 的全部注入之后）把状态恢复为原版 GUI
     * 渲染依赖的值，并与 RenderSystem 缓存对齐（RenderType 应用状态时不再重复设置）。
     */
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void gtne$restoreGuiGlState(CallbackInfo ci) {
        GL20.glDrawBuffers(1);                     // 恢复单渲染目标（shimmer 的 MRT 残留）
        RenderSystem.defaultBlendFunc();           // 默认混合（GUI 半透明用）
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();           // GUI 无深度
        RenderSystem.depthMask(false);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(0);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
