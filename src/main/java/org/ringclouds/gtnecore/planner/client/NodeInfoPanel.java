package org.ringclouds.gtnecore.planner.client;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.client.gui.GuiGraphics;
import org.ringclouds.gtnecore.planner.api.PlannerRecipe;
import org.ringclouds.gtnecore.planner.api.PlannerStack;
import org.ringclouds.gtnecore.planner.model.PlannerNode;

import java.util.List;

/**
 * 右侧信息面板（仿 LDLib UI 编辑器的配置面板位）：
 * 选中节点的 配方/输入端口/输出端口 清单（两列、滚轮翻页）+ 电量汇总 + 最终产物统计。
 * 每次渲染都从画布状态现读，无需手动刷新。
 */
final class NodeInfoPanel extends Widget {

    private static final int C_PANEL = 0xFFC6C6C6;
    private static final int C_BORDER = 0xFF373737;
    private static final int C_HIGHLIGHT = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFF404040;
    private static final int C_TEXT_DIM = 0xFF555555;
    private static final int C_TEXT_BLUE = 0xFF1E4DA6;
    private static final int ROW_H = 14, MAX_ROWS = 6, COL_W = 88;

    private final PlannerScreen.PlannerCanvas canvas;
    private int inputScroll, outputScroll, finalsScroll;
    private boolean multFocused; // 倍率输入框聚焦（键盘输入数字）
    private String multInput = ""; // 倍率输入缓冲

    NodeInfoPanel(PlannerScreen.PlannerCanvas canvas, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.canvas = canvas;
    }

    @Override
    public void drawInBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Position p = getPosition();
        int x = p.x, y = p.y;
        DrawerHelper.drawSolidRect(guiGraphics, x, y, getSize().width, getSize().height, C_PANEL);
        DrawerHelper.drawBorder(guiGraphics, x, y, getSize().width, getSize().height, C_BORDER, 1);
        DrawerHelper.drawBorder(guiGraphics, x + 1, y + 1, getSize().width - 2, getSize().height - 2, C_HIGHLIGHT, 1);
        DrawerHelper.drawText(guiGraphics, "节点信息", x + 4, y + 3, 0.8f, C_TEXT);
        int dy = y + 15;
        // 配方链缺少汇总（整条链的输入缺口，红字）
        List<PlannerStack> missing = canvas.graph().missingSummary();
        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("缺少: ");
            int shown = 0;
            for (PlannerStack s : missing) {
                if (shown >= 2) {
                    sb.append("…共").append(missing.size()).append("项");
                    break;
                }
                sb.append(shortName(s)).append("×").append(s.amount()).append(" ");
                shown++;
            }
            DrawerHelper.drawText(guiGraphics, truncate(sb.toString().trim(), 22), x + 4, dy, 0.7f, 0xFFB00000);
            dy += 12;
        }

        PlannerNode n = canvas.selectedNode;
        if (n == null) {
            DrawerHelper.drawText(guiGraphics, "未选中节点", x + 4, dy, 0.8f, C_TEXT_DIM);
            DrawerHelper.drawText(guiGraphics, "单击画布节点查看", x + 4, dy + 14, 0.7f, C_TEXT_DIM);
            dy += 40;
        } else {
            DrawerHelper.drawText(guiGraphics, "节点 " + n.id, x + 4, dy, 0.8f, C_TEXT);
            dy += 12;
            if (n.hasRecipe()) {
                PlannerRecipe r = n.recipe;
                DrawerHelper.drawText(guiGraphics, truncate(r.displayName(), 16), x + 4, dy, 0.7f, C_TEXT_BLUE);
                dy += 11;
                DrawerHelper.drawText(guiGraphics, "EUt %d × %dt".formatted(r.eut(), r.duration()), x + 4, dy, 0.7f, C_TEXT_DIM);
                dy += 12;
                // 倍率行：输入框（数字直输，回车确认）+ [-] [+] 按钮
                int mx = x + 4, my = dy;
                DrawerHelper.drawText(guiGraphics, "倍率", mx, my, 0.7f, C_TEXT);
                int ix = mx + 30, iw = 32;
                DrawerHelper.drawSolidRect(guiGraphics, ix, my, iw, 10, 0xFFF0F0F0);
                DrawerHelper.drawBorder(guiGraphics, ix, my, iw, 10, multFocused ? 0xFF2D6BFF : C_BORDER, 1);
                String shown = multFocused ? multInput + "_" : String.valueOf(n.effectiveMultiplier());
                DrawerHelper.drawText(guiGraphics, truncate(shown, 4), ix + 2, my + 1, 0.6f,
                        multFocused ? C_TEXT : (n.multiplier > 0 ? C_TEXT_BLUE : C_TEXT_DIM));
                DrawerHelper.drawSolidRect(guiGraphics, mx + 66, my, 14, 10, C_PANEL);
                DrawerHelper.drawBorder(guiGraphics, mx + 66, my, 14, 10, C_BORDER, 1);
                DrawerHelper.drawText(guiGraphics, "-", mx + 70, my + 1, 0.7f, C_TEXT);
                DrawerHelper.drawSolidRect(guiGraphics, mx + 82, my, 14, 10, C_PANEL);
                DrawerHelper.drawBorder(guiGraphics, mx + 82, my, 14, 10, C_BORDER, 1);
                DrawerHelper.drawText(guiGraphics, "+", mx + 86, my + 1, 0.7f, C_TEXT);
                DrawerHelper.drawText(guiGraphics, n.multiplier > 0 ? "手动" : "自动", mx + 100, my, 0.6f, C_TEXT_DIM);
                dy += 13;
                dy = drawStackList2(guiGraphics, x, dy, "输入（×" + n.effectiveMultiplier() + "）", n.recipe.inputs(), inputScroll, n, true);
                dy = drawStackList2(guiGraphics, x, dy, "输出（×" + n.effectiveMultiplier() + "）", n.recipe.outputs(), outputScroll, n, false);
            } else {
                DrawerHelper.drawText(guiGraphics, "未选配方", x + 4, dy, 0.7f, C_TEXT_DIM);
                dy += 22;
            }
        }

        // 最终产物统计（电量汇总上方）：未被下游消耗的输出，两列可滚动
        List<PlannerStack> finals = canvas.graph().finalOutputs();
        int by = y + getSize().height - 42;
        int fy = by - 8 - Math.min(MAX_ROWS, (finals.size() + 1) / 2) * ROW_H;
        if (fy > y + 40) {
            DrawerHelper.drawText(guiGraphics, "最终产物（" + finals.size() + "，滚轮翻页）", x + 4, fy, 0.7f, C_TEXT_DIM);
            fy += 10;
            int rows = (finals.size() + 1) / 2;
            for (int r = 0; r < Math.min(MAX_ROWS, rows); r++) {
                int idx0 = (finalsScroll + r) * 2;
                for (int col = 0; col < 2; col++) {
                    int idx = idx0 + col;
                    if (idx >= finals.size()) break;
                    PlannerStack s = finals.get(idx);
                    int cx = x + 4 + col * COL_W;
                    int ry = fy + r * ROW_H;
                    drawIcon(guiGraphics, s, cx, ry);
                    DrawerHelper.drawText(guiGraphics, truncate(shortName(s), 8), cx + 18, ry + 1, 0.6f, C_TEXT);
                    String amt = s.isFluid() ? s.amount() + "mB" : "×" + s.amount();
                    DrawerHelper.drawText(guiGraphics, amt, cx + COL_W - 4, ry + 1, 0.6f, C_TEXT_DIM);
                }
            }
        }

        // 电量汇总（底部固定，只算 EU/t）
        DrawerHelper.drawBorder(guiGraphics, x + 2, by, getSize().width - 4, 1, C_BORDER, 1);
        DrawerHelper.drawText(guiGraphics, "总耗电 %d EU/t".formatted(canvas.graph().totalConsumedPerTick()), x + 4, by + 4, 0.7f, C_TEXT);
        DrawerHelper.drawText(guiGraphics, "总发电 %d EU/t".formatted(canvas.graph().totalGeneratedPerTick()), x + 4, by + 15, 0.7f, C_TEXT);
        DrawerHelper.drawText(guiGraphics, "净电量 %d EU/t".formatted(canvas.graph().netPowerPerTick()) + "（正=盈余）", x + 4, by + 26, 0.7f, C_TEXT_BLUE);
    }

    /** 两列物料列表（滚轮翻页），数量按节点生效倍率显示，输入项缺口显示红色"缺×N"，返回下一段 y */
    private int drawStackList2(GuiGraphics g, int x, int dy, String label, List<PlannerStack> stacks, int scroll, PlannerNode n, boolean isInput) {
        DrawerHelper.drawText(g, label + "（两列，滚轮翻页）", x + 4, dy, 0.7f, C_TEXT_DIM);
        dy += 11;
        int rows = (stacks.size() + 1) / 2;
        long m = n.effectiveMultiplier();
        for (int r = 0; r < MAX_ROWS; r++) {
            int idx0 = (scroll + r) * 2;
            if (idx0 >= stacks.size()) break;
            for (int col = 0; col < 2; col++) {
                int idx = idx0 + col;
                if (idx >= stacks.size()) break;
                PlannerStack s = stacks.get(idx);
                int cx = x + 4 + col * COL_W;
                int ry = dy + r * ROW_H;
                drawIcon(g, s, cx, ry);
                DrawerHelper.drawText(g, truncate(shortName(s), 8), cx + 18, ry + 1, 0.6f, C_TEXT);
                long miss = isInput ? canvas.graph().missingAt(n, idx) : 0;
                String amt;
                int amtColor = C_TEXT_DIM;
                if (miss > 0) {
                    amt = "缺×" + miss;
                    amtColor = 0xFFB00000;
                } else {
                    amt = s.isFluid() ? s.amount() * m + "mB" : "×" + s.amount() * m;
                }
                DrawerHelper.drawText(g, amt, cx + COL_W - 4, ry + 1, 0.6f, amtColor);
            }
        }
        if (rows > MAX_ROWS) {
            DrawerHelper.drawText(g, "… 共 " + stacks.size() + " 项", x + 4, dy + MAX_ROWS * ROW_H + 1, 0.6f, C_TEXT_DIM);
            dy += MAX_ROWS * ROW_H + 12;
        } else {
            dy += rows * ROW_H + 6;
        }
        return dy;
    }

    private void drawIcon(GuiGraphics g, PlannerStack s, int ix, int iy) {
        if (s.isFluid()) {
            com.lowdragmc.lowdraglib.side.fluid.FluidStack ld = com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(s.fluid().getFluid(), s.fluid().getAmount(), s.fluid().getTag());
            DrawerHelper.drawFluidForGui(g, ld, 1000, ix, iy, 14, 14);
        } else {
            g.renderItem(s.item(), ix, iy);
        }
    }

    private static String shortName(PlannerStack s) {
        if (s.isFluid()) {
            return s.fluid().getFluid().getFluidType().getDescription().getString();
        }
        return s.item().getHoverName().getString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        Position p = getPosition();
        int lx = (int) mouseX - p.x, ly = (int) mouseY - p.y;
        if (lx >= 0 && lx < getSize().width) {
            int delta = (int) (wheelDelta * 4);
            PlannerNode n = canvas.selectedNode;
            // 下半区（最终产物区）滚最终产物
            int by = p.y + getSize().height - 42;
            List<PlannerStack> finals = canvas.graph().finalOutputs();
            int finalsRows = (finals.size() + 1) / 2;
            if (ly >= by - 8 - Math.min(MAX_ROWS, finalsRows) * ROW_H && ly < by) {
                finalsScroll = clamp(finalsScroll - delta, finalsRows - MAX_ROWS);
                return true;
            }
            if (n != null && n.hasRecipe()) {
                if (ly < getSize().height / 2) {
                    inputScroll = clamp(inputScroll - delta, (n.recipe.inputs().size() + 1) / 2 - MAX_ROWS);
                } else if (ly < by) {
                    outputScroll = clamp(outputScroll - delta, (n.recipe.outputs().size() + 1) / 2 - MAX_ROWS);
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        Position p = getPosition();
        int lx = (int) mouseX - p.x, ly = (int) mouseY - p.y;
        PlannerNode n = canvas.selectedNode;
        if (n == null || !n.hasRecipe()) return false;
        // 倍率行位置 = 流式布局复刻（与绘制一致）：标题下 + 缺少行(可选) + 节点行 + 配方名 + EUt 行
        int dy = 15;
        if (!canvas.graph().missingSummary().isEmpty()) dy += 12;
        dy += 12 + 11 + 12;
        if (ly >= dy && ly < dy + 10) {
            if (lx >= 30 && lx < 62) {
                // 输入框：聚焦，缓冲填入当前手动值（0=自动则留空）
                multFocused = true;
                multInput = n.multiplier > 0 ? String.valueOf(n.multiplier) : "";
                return true;
            }
            if (lx >= 66 && lx < 80) {
                commitMultiplier();
                n.multiplier = Math.max(0, n.multiplier - 1);
                canvas.graph().recalc();
                return true;
            }
            if (lx >= 82 && lx < 96) {
                commitMultiplier();
                n.multiplier = n.multiplier == 0 ? 2 : n.multiplier + 1;
                canvas.graph().recalc();
                return true;
            }
        }
        // 其他区域点击：倍率输入框失焦并提交
        if (multFocused) commitMultiplier();
        return false;
    }

    /** 提交倍率输入（回车/失焦）：整数取整、负数转回 1、空=自动；立即重算 */
    private void commitMultiplier() {
        if (!multFocused) return;
        multFocused = false;
        PlannerNode n = canvas.selectedNode;
        String s = multInput.trim();
        multInput = "";
        if (n == null) return;
        if (s.isEmpty()) {
            n.multiplier = 0; // 空 = 自动配平
        } else {
            try {
                int v = Integer.parseInt(s);
                if (v < 0) v = 1; // 负数自动转回 1
                n.multiplier = v;
            } catch (NumberFormatException ex) {
                n.multiplier = 0;
            }
        }
        canvas.graph().recalc();
    }

    // ---- 倍率输入框键盘（聚焦时只收数字） ----

    @Override
    public boolean charTyped(char ch, int mods) {
        if (!multFocused) return false;
        if (ch >= '0' && ch <= '9' && multInput.length() < 9) multInput += ch;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (!multFocused) return false;
        if (keyCode == 259) { // Backspace
            if (!multInput.isEmpty()) multInput = multInput.substring(0, multInput.length() - 1);
        } else if (keyCode == 257 || keyCode == 335 || keyCode == 256) { // 回车 / 数字键盘回车 / Esc：提交
            commitMultiplier();
        } else {
            return false;
        }
        return true;
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(v, Math.max(0, max)));
    }
}