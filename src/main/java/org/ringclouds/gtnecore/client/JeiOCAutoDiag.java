package org.ringclouds.gtnecore.client;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.ringclouds.gtnecore.Gtnecore;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 升压失效自动诊断：进世界 100t 后，从配方管理器取官方配方（聚变）与发射台配方，
 * 程序化构造 GTRecipeWidget 并调用 setRecipeOC——捕获异常/检查状态。
 * 结果打 [GTNE-OCDIAG]。诊断完成后自毁（不再执行）。
 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID, value = Dist.CLIENT)
public final class JeiOCAutoDiag {

    private static int tick;
    private static boolean done;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
                var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;
        if (++tick < 100) return;
        done = true;

        List<String> lines = new ArrayList<>();
        try {
            var rm = player.level().getRecipeManager();
            var recipes = rm.getAllRecipesFor(
                    com.gregtechceu.gtceu.common.data.GTRecipeTypes.FUSION_RECIPES);
            lines.add("聚变配方数=" + recipes.size());
            if (!recipes.isEmpty()) {
                GTRecipe recipe = (GTRecipe) recipes.get(0);
                var eut = recipe.getInputEUt();
                lines.add("聚变#1 eutEmpty=" + eut.isEmpty()
                        + (eut.isEmpty() ? "" : " eut=" + eut.getTotalEU()));
                try {
                    var widget = new GTRecipeWidget(recipe);
                    // 反射取 tier/minTier 前后状态
                    var f1 = GTRecipeWidget.class.getDeclaredField("minTier");
                    f1.setAccessible(true);
                    var f2 = GTRecipeWidget.class.getDeclaredField("tier");
                    f2.setAccessible(true);
                    lines.add("widget minTier=" + f1.get(widget) + " tier=" + f2.get(widget));
                    widget.setRecipeOC(0, false);
                    lines.add("setRecipeOC(0) 后 tier=" + f2.get(widget));
                    var vt = GTRecipeWidget.class.getDeclaredField("voltageTextWidget");
                    vt.setAccessible(true);
                    var label = (com.lowdragmc.lowdraglib.gui.widget.LabelWidget) vt.get(widget);
                    lines.add("voltageText=" + label);
                    lines.add("★ 聚变 widget 升压调用成功——显示层或事件层问题");
                } catch (Throwable t) {
                    lines.add("★ 聚变 widget 异常: " + t);
                    for (var s : t.getStackTrace()) {
                        lines.add("    at " + s);
                        if (lines.size() > 25) break;
                    }
                }
            }
        } catch (Throwable t) {
            lines.add("诊断外层异常: " + t);
        }
        for (var l : lines) System.out.println("[GTNE-OCDIAG] " + l);
    }

    private JeiOCAutoDiag() {
    }
}
