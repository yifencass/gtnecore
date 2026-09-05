package org.ringclouds.gtnecore.planner.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 背包界面新按钮：打开配方规划器（纯客户端）。
 * 位置：背包右上角空位（guiLeft+152, guiTop+4）。图标贴图 TODO：换成品按钮样式。
 */
public final class InventoryPlannerButton {

    private InventoryPlannerButton() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            int x = screen.getGuiLeft() + 152;
            int y = screen.getGuiTop() + 4;
            event.addListener(Button.builder(Component.literal("P"), btn -> PlannerEntry.openPlanner())
                    .bounds(x, y, 18, 18)
                    .tooltip(Tooltip.create(Component.translatable("gtnecore.planner.open")))
                    .build());
        }
    }
}
