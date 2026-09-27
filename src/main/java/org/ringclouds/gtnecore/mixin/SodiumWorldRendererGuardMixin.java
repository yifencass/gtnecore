package org.ringclouds.gtnecore.mixin;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium 进世界时序防御：方块更新包在 renderSectionManager 初始化前到达时，
 * SodiumWorldRenderer.scheduleRebuildForChunk 直接解引用 null 的
 * renderSectionManager 崩溃（NPE 刷屏卡死"加入世界"）。
 * 渲染管理器就绪前的重建请求直接丢弃——区块数据到达后会重新安排重建，安全。
 *
 * @Pseudo：embeddium 是运行时 mod（发布环境整合包提供），目标类缺失时静默跳过。
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer")
public abstract class SodiumWorldRendererGuardMixin {

    @Shadow
    private RenderSectionManager renderSectionManager;

    @Inject(method = "scheduleRebuildForChunk(IIIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gtne$guardScheduleRebuild(int x, int y, int z, boolean important, CallbackInfo ci) {
        if (this.renderSectionManager == null) {
            ci.cancel();
        }
    }
}
