package org.ringclouds.gtnecore.planner.client;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.client.gui.GuiGraphics;
import org.ringclouds.gtnecore.planner.api.PlannerStack;
import org.ringclouds.gtnecore.planner.model.PlannerNode;

/**
 * Blender 风格 + MC 原生容器配色的配方节点卡片：
 * - 左侧边缘 = 输入端口（每个配方输入一个），右侧边缘 = 输出端口
 * - 端口色：物品=橙，流体=蓝；悬停端口显示对应物料名+数量（自绘 tooltip）
 * - 右上角 × 删除按钮；拖拽/点击判定由 PlannerCanvas 统一处理
 * - 绘制用 getPosition() 全局坐标（LDLib 绘制链不平移子控件）
 */
final class RecipeNodeWidget extends Widget {

    /** 宽度固定（防重叠排布整齐）；高度可垂直拉伸（node.height，默认 60） */
    static final int NODE_W = 84;
    static final int DEFAULT_H = 60;
    /** 底部拉伸手柄高度（命中后只改高度，宽度不变） */
    private static final int RESIZE_H = 6;
    /** × 删除按钮区域（节点局部坐标）：右上角 12×9 */
    private static final int DEL_W = 12, DEL_H = 9;
    private static final int PORT_SIZE = 8;
    /** 端口颜色：物品/流体 */
    private static final int COLOR_ITEM = 0xffe8a23c;
    private static final int COLOR_FLUID = 0xff4a8ff5;
    /** 端口纵向布局带（避开标题条与底部） */
    private static final int PORT_TOP = 12;
    // MC 原生容器配色
    private static final int C_PANEL = 0xFFC6C6C6;
    private static final int C_BORDER = 0xFF373737;
    private static final int C_HIGHLIGHT = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFF404040;
    private static final int C_TEXT_DIM = 0xFF555555;
    private static final int C_TEXT_BLUE = 0xFF1E4DA6;

    public final PlannerNode node;
    private boolean selected;

    RecipeNodeWidget(PlannerNode node) {
        super(node.x, node.y, NODE_W, node.height);
        this.node = node;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    /** 节点局部坐标是否落在 × 删除按钮上 */
    boolean isDeleteButton(int lx, int ly) {
        return lx >= NODE_W - DEL_W && ly < DEL_H;
    }

    /** 节点局部坐标是否落在底部拉伸手柄上（宽度固定，只拉高度） */
    boolean isResizeHandle(int lx, int ly) {
        return ly >= node.height - RESIZE_H && lx >= 0 && lx < NODE_W;
    }

    /** 更新卡片高度（宽度不变） */
    void setNodeHeight(int h) {
        node.height = h;
        setSize(NODE_W, h);
    }

    @Override
    public void drawInBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Position pos = getPosition();
        int x = pos.x, y = pos.y, h = node.height;
        // MC 容器面板：灰底 + 双层描边（选中时金色外框）
        DrawerHelper.drawSolidRect(guiGraphics, x, y, NODE_W, h, C_PANEL);
        DrawerHelper.drawBorder(guiGraphics, x, y, NODE_W, h, selected ? 0xFFE8B64A : C_BORDER, 2);
        DrawerHelper.drawBorder(guiGraphics, x + 2, y + 2, NODE_W - 4, h - 4, C_HIGHLIGHT, 1);
        // 标题条（有配方=绿，无=暗琥珀）
        DrawerHelper.drawSolidRect(guiGraphics, x, y, NODE_W, 8, node.hasRecipe() ? 0xff3d6b3d : 0xff6b5a2a);
        DrawerHelper.drawBorder(guiGraphics, x, y, NODE_W, 8, C_BORDER, 1);
        DrawerHelper.drawText(guiGraphics, "节点 " + node.id, x + 3, y + 0, 1f, 0xffffffff);
        // × 删除按钮
        DrawerHelper.drawSolidRect(guiGraphics, x + NODE_W - DEL_W, y, DEL_W, DEL_H, 0xff8a2a2a);
        DrawerHelper.drawText(guiGraphics, "×", x + NODE_W - DEL_W + 3, y + 0, 1f, 0xffffaaaa);
        // 底部拉伸手柄提示（暗条）
        DrawerHelper.drawSolidRect(guiGraphics, x + NODE_W / 2 - 6, y + h - RESIZE_H, 12, 3, 0xFF8A8A8A);

        if (node.hasRecipe()) {
            String name = node.recipe.displayName();
            if (name.length() > 10) name = name.substring(0, 10) + "…";
            DrawerHelper.drawText(guiGraphics, name, x + 3, y + 11, 0.8f, C_TEXT);
            DrawerHelper.drawText(guiGraphics, "EUt %d × %dt".formatted(node.recipe.eut(), node.recipe.duration()),
                    x + 3, y + 23, 0.8f, C_TEXT_BLUE);
            // 倍率（自动配平结果 / 手动设定）
            DrawerHelper.drawText(guiGraphics, "×" + node.effectiveMultiplier() + (node.multiplier > 0 ? "手" : "自"),
                    x + NODE_W - 26, y + 23, 0.7f, node.multiplier > 0 ? 0xFFB8860B : C_TEXT_DIM);
            // 端口（输入=左缘中心，输出=右缘中心，矩形方块；portYOffset 是节点局部坐标）
            int in = node.recipe.inputs().size(), out = node.recipe.outputs().size();
            for (int i = 0; i < in; i++) {
                drawPort(guiGraphics, x, y + portYOffset(in, i, node.height), node.recipe.inputs().get(i));
            }
            for (int i = 0; i < out; i++) {
                drawPort(guiGraphics, x + NODE_W, y + portYOffset(out, i, node.height), node.recipe.outputs().get(i));
            }
        } else {
            DrawerHelper.drawText(guiGraphics, "未选配方", x + 3, y + 20, 0.8f, C_TEXT);
            DrawerHelper.drawText(guiGraphics, "单击选择配方", x + 3, y + 34, 0.7f, C_TEXT_DIM);
        }
    }

    /** 端口：矩形小方块（以中心定位，cx,cy = 端口中心） */
    private static void drawPort(GuiGraphics guiGraphics, int cx, int cy, PlannerStack s) {
        int c = s.isFluid() ? COLOR_FLUID : COLOR_ITEM;
        DrawerHelper.drawSolidRect(guiGraphics, cx - PORT_SIZE / 2, cy - PORT_SIZE / 2, PORT_SIZE, PORT_SIZE, c);
        DrawerHelper.drawBorder(guiGraphics, cx - PORT_SIZE / 2, cy - PORT_SIZE / 2, PORT_SIZE, PORT_SIZE, C_BORDER, 1);
    }

    /** 端口全局中心（连线/悬停命中测试用；世界坐标） */
    int portX(boolean output) {
        return output ? node.x + NODE_W : node.x;
    }

    int portY(int count, int i) {
        return node.y + portYOffset(count, i, node.height);
    }

    /** 端口纵向中心（节点局部坐标）：固定间距（不随高度均布拉大，端口多时可区分） */
    static int portYOffset(int count, int i, int height) {
        return PORT_TOP + i * PORT_GAP;
    }

    /** 按端口数计算所需最小高度（选配方时自动调整卡片高度） */
    static int neededHeight(int in, int out) {
        return PORT_TOP + Math.max(in, out) * PORT_GAP + 10;
    }

    private static final int PORT_GAP = 14;

    /** 悬停端口 → 自绘 tooltip（LDLib hover 不分发给画布子控件，只能自己画） */
    @Override
    public void drawInForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!node.hasRecipe()) return;
        Position pos = getPosition();
        int x = pos.x, y = pos.y;
        PlannerStack hit = null;
        int in = node.recipe.inputs().size(), out = node.recipe.outputs().size();
        for (int i = 0; i < in; i++) {
            if (Math.abs(mouseX - x) <= 5
                    && Math.abs(mouseY - (y + portYOffset(in, i, node.height))) <= 5) {
                hit = node.recipe.inputs().get(i);
                break;
            }
        }
        if (hit == null) {
            for (int i = 0; i < out; i++) {
                if (Math.abs(mouseX - (x + NODE_W)) <= 5
                        && Math.abs(mouseY - (y + portYOffset(out, i, node.height))) <= 5) {
                    hit = node.recipe.outputs().get(i);
                    break;
                }
            }
        }
        if (hit == null) return;
        // MC 风格 tooltip：深底 + 描边 + 白字
        String text = shortName(hit) + (hit.isFluid() ? "  " + hit.amount() + " mB" : "  ×" + hit.amount());
        int tw = (int) (text.length() * 4.5f) + 6;
        int tx = Math.max(2, mouseX + 10);
        int ty = Math.max(2, mouseY - 12);
        DrawerHelper.drawSolidRect(guiGraphics, tx, ty, tw, 12, 0xF0100010);
        DrawerHelper.drawBorder(guiGraphics, tx, ty, tw, 12, 0xFFFFD83D, 1);
        DrawerHelper.drawText(guiGraphics, text, tx + 3, ty + 1, 0.7f, 0xFFFFFFFF);
    }

    private static String shortName(PlannerStack s) {
        if (s.isFluid()) {
            return s.fluid().getFluid().getFluidType().getDescription().getString();
        }
        return s.item().getHoverName().getString();
    }
}
