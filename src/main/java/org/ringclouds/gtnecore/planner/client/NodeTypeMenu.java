package org.ringclouds.gtnecore.planner.client;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * 右键弹出的"选择节点类型"列表。作为 mainGroup 的直接子控件添加
 * （mainGroup 的点击会正常分发给子控件，画布 DSWidgetGroup 不会），
 * 位置用全局坐标（LDLib 自绘/点击均按全局坐标约定）。
 */
final class NodeTypeMenu extends Widget {

    private static final int MENU_W = 76;
    private static final int TITLE_H = 10;
    private static final int ENTRY_H = 16;
    private static final List<PlannerNodeType> TYPES = List.of(PlannerNodeType.values());

    private final PlannerScreen.PlannerCanvas canvas;
    private final int anchorX, anchorY; // 创建节点的画布局部坐标（右键位置）

    NodeTypeMenu(PlannerScreen.PlannerCanvas canvas, int x, int y, int anchorX, int anchorY) {
        super(x, y, MENU_W, TITLE_H + ENTRY_H * TYPES.size());
        this.canvas = canvas;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    @Override
    public void drawInBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Position p = getPosition();
        int x = p.x, y = p.y;
        DrawerHelper.drawSolidRect(guiGraphics, x, y, MENU_W, TITLE_H + ENTRY_H * TYPES.size(), 0xee333333);
        DrawerHelper.drawSolidRect(guiGraphics, x, y, MENU_W, TITLE_H, 0xee555555);
        DrawerHelper.drawText(guiGraphics, "选择节点类型", x + 3, y, 1f, 0xffffffff);
        int lmx = (int) mouseX - x, lmy = (int) mouseY - y;
        for (int i = 0; i < TYPES.size(); i++) {
            int ey = y + TITLE_H + i * ENTRY_H;
            if (lmx >= 2 && lmx < MENU_W - 2 && lmy >= TITLE_H + i * ENTRY_H && lmy < TITLE_H + (i + 1) * ENTRY_H) {
                DrawerHelper.drawSolidRect(guiGraphics, x + 2, ey, MENU_W - 4, ENTRY_H - 2, 0x664477cc);
            }
            DrawerHelper.drawText(guiGraphics, TYPES.get(i).displayName, x + 5, ey + 2, 1f, 0xffdddddd);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Position p = getPosition();
            int lx = (int) mouseX - p.x, ly = (int) mouseY - p.y;
            int idx = (ly - TITLE_H) / ENTRY_H;
            if (idx >= 0 && idx < TYPES.size() && lx >= 2 && lx < MENU_W - 2) {
                canvas.pickNodeType(TYPES.get(idx), anchorX, anchorY);
                return true;
            }
        }
        return false; // 非选项区域：交给画布（关闭菜单/继续其他交互）
    }
}
