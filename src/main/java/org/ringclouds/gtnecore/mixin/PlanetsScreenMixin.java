package org.ringclouds.gtnecore.mixin;

import earth.terrarium.adastra.api.planets.Planet;
import earth.terrarium.adastra.client.screens.PlanetsScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑洞行星着陆按钮移除：选中黑洞时隐藏 LAND 按钮（保留空间站按钮）。
 *
 * createSelectedPlanetButtons() 只调 1 次 addRenderableWidget 创建 LAND 按钮
 * （空间站按钮走 addSpaceStationButtons 单独管理），所以直接对最后一个
 * renderable 中的 Button 设 visible=false 即可。
 */
@Mixin(value = PlanetsScreen.class, remap = false)
public abstract class PlanetsScreenMixin {

    @Shadow
    private Planet selectedPlanet;

    /** 黑洞维度 id（选中此行星时移除着陆按钮） */
    private static final ResourceKey<Level> BLACKHOLE = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            new net.minecraft.resources.ResourceLocation("gtnecore", "blackhole"));

    @Inject(method = "createSelectedPlanetButtons", at = @At("TAIL"))
    private void gtnecore$hideBlackHoleLandButton(CallbackInfo ci) {
        if (selectedPlanet == null || !selectedPlanet.dimension().equals(BLACKHOLE)) return;

        PlanetsScreen self = (PlanetsScreen) (Object) this;
        // 从 renderables 尾部找 LAND 按钮（createSelectedPlanetButtons 最后加的就是它）
        List<AbstractWidget> widgets = new ArrayList<>();
        for (var renderable : self.renderables) {
            if (renderable instanceof AbstractWidget widget) {
                widgets.add(widget);
            }
        }
        for (int i = widgets.size() - 1; i >= 0; i--) {
            AbstractWidget w = widgets.get(i);
            if (w instanceof Button btn && w.visible) {
                w.visible = false;
                w.active = false;
                return;
            }
        }
    }
}
