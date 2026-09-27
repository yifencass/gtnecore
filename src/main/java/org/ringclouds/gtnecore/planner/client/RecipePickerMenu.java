package org.ringclouds.gtnecore.planner.client;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.client.gui.GuiGraphics;
import org.ringclouds.gtnecore.planner.api.PlannerRecipe;
import org.ringclouds.gtnecore.planner.api.PlannerStack;
import org.ringclouds.gtnecore.planner.api.RecipeCategory;
import org.ringclouds.gtnecore.planner.api.RecipeLookup;
import org.ringclouds.gtnecore.planner.gtceu.PlannerJeiPlugin;
import org.ringclouds.gtnecore.planner.gtceu.RecipeProjection;
import org.ringclouds.gtnecore.planner.model.PlannerNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点配方选择面板（MC 原生容器风格，覆盖在画布上）：
 * - 顶部：分类下拉按钮 + 搜索框（按输入/输出物料名过滤）
 * - 左侧：该分类全部配方滚动列表（每条目两行：主输入+次输入 / 主输出+次输出）
 * - 单击配方 = 预览（桥接 JEI 显示完整详情）；再次点击同一配方 = 确认应用
 * - 应用后由画布在右侧自动生成每个产物的子节点（连锁规划流程）
 */
final class RecipePickerMenu extends Widget {

    // MC 原生容器配色
    private static final int C_PANEL = 0xFFC6C6C6;
    private static final int C_BORDER = 0xFF373737;
    private static final int C_HIGHLIGHT = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFF404040;
    private static final int C_TEXT_DIM = 0xFF555555;
    private static final int C_TEXT_BLUE = 0xFF1E4DA6;

    private static final int P_W = 300, P_H = 178;
    private static final int CAT_X = 4, CAT_Y = 4, CAT_W = 140, CAT_H = 18;
    private static final int SEARCH_X = 148, SEARCH_Y = 4, SEARCH_W = P_W - SEARCH_X - 4, SEARCH_H = 16;
    private static final int LIST_X = 4, LIST_W = 110, LIST_Y = 26, LIST_H = 146;
    private static final int INFO_X = 118, INFO_W = P_W - INFO_X - 4;
    private static final int ENTRY_H = 26;
    private static final int VISIBLE = LIST_H / ENTRY_H;

    private final PlannerScreen.PlannerCanvas canvas;
    private final com.lowdragmc.lowdraglib.gui.widget.WidgetGroup root;
    private final PlannerNode node;
    private final RecipeLookup lookup;

    private RecipeCategory category;
    private List<PlannerRecipe> allRecipes = List.of();
    private String searchText = "";
    private boolean searchFocused; // 点击搜索框后聚焦，直接打字过滤
    private PlannerRecipe preview; // 单击预览的配方（再点一次确认）
    private RecipeProjection projection; // 预览配方的 JEI 显示投影
    private int scroll;
    private CategoryMenu categoryMenu;

    RecipePickerMenu(PlannerScreen.PlannerCanvas canvas, PlannerNode node, RecipeLookup lookup, int x, int y) {
        super(x, y, P_W, P_H);
        this.canvas = canvas;
        this.root = canvas.root;
        this.node = node;
        this.lookup = lookup;
        this.category = node.category;
        rebuild();
    }

    /** 面板整体关闭（连同分类下拉） */
    void close() {
        closeCategoryMenu();
        root.removeWidget(this);
    }

    private void setSearchText(String s) {
        searchText = s == null ? "" : s.trim();
        rebuild();
    }

    private void rebuild() {
        allRecipes = new ArrayList<>(lookup.find(category, List.of(), List.of()));
        if (!searchText.isBlank()) {
            String q = searchText.toLowerCase(java.util.Locale.ROOT);
            allRecipes.removeIf(r -> !matchesSearch(r, q));
        }
        scroll = 0;
        preview = null;
        projection = null;
    }

    /** 输入或输出任一物料名/描述含关键词即命中 */
    private static boolean matchesSearch(PlannerRecipe r, String q) {
        for (PlannerStack s : r.inputs()) if (stackName(s).toLowerCase(java.util.Locale.ROOT).contains(q)) return true;
        for (PlannerStack s : r.outputs()) if (stackName(s).toLowerCase(java.util.Locale.ROOT).contains(q)) return true;
        return false;
    }

    private static String stackName(PlannerStack s) {
        return s.isFluid()
                ? s.fluid().getFluid().getFluidType().getDescription().getString()
                : s.item().getHoverName().getString();
    }

    // ---- 分类下拉 ----

    private void toggleCategoryMenu() {
        if (categoryMenu != null) {
            closeCategoryMenu();
            return;
        }
        Position p = getPosition();
        Position rp = root.getPosition();
        categoryMenu = new CategoryMenu(this, p.x + CAT_X - rp.x, p.y + CAT_Y + CAT_H - rp.y);
        root.addWidget(categoryMenu);
    }

    private void closeCategoryMenu() {
        if (categoryMenu != null) {
            root.removeWidget(categoryMenu);
            categoryMenu = null;
        }
    }

    void setCategory(RecipeCategory c) {
        closeCategoryMenu();
        if (c != category) {
            category = c;
            rebuild();
        }
    }

    /** 确认应用配方 */
    private void applyRecipe(PlannerRecipe r) {
        node.category = category;
        node.recipe = r;
        node.desiredOutputs = List.copyOf(r.outputs());
        // 按端口数自动调整卡片高度（端口固定间距，够高才能区分）
        int need = RecipeNodeWidget.neededHeight(r.inputs().size(), r.outputs().size());
        if (node.height < need) {
            node.height = need;
            Widget w = canvas.nodeWidget(node);
            if (w != null) ((RecipeNodeWidget) w).setNodeHeight(need);
        }
        canvas.onRecipePicked(node);
    }

    // ---- 渲染 ----

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    @Override
    public void drawInBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Position p = getPosition();
        int x = p.x, y = p.y;
        // 面板
        DrawerHelper.drawSolidRect(guiGraphics, x, y, P_W, P_H, C_PANEL);
        DrawerHelper.drawBorder(guiGraphics, x, y, P_W, P_H, C_BORDER, 1);
        DrawerHelper.drawBorder(guiGraphics, x + 1, y + 1, P_W - 2, P_H - 2, C_HIGHLIGHT, 1);
        // 分类下拉按钮（MC 槽位风格）
        DrawerHelper.drawSolidRect(guiGraphics, x + CAT_X, y + CAT_Y, CAT_W, CAT_H, C_PANEL);
        DrawerHelper.drawBorder(guiGraphics, x + CAT_X, y + CAT_Y, CAT_W, CAT_H, C_HIGHLIGHT, 1);
        DrawerHelper.drawBorder(guiGraphics, x + CAT_X + 1, y + CAT_Y + 1, CAT_W - 2, CAT_H - 2, C_BORDER, 1);
        DrawerHelper.drawText(guiGraphics, truncate(category.displayName(), 13) + " ▼", x + CAT_X + 3, y + CAT_Y + 3, 0.8f, C_TEXT);
        // 搜索框（白底可见，点击聚焦后直接打字过滤）
        int sx = x + SEARCH_X, sy = y + SEARCH_Y;
        DrawerHelper.drawSolidRect(guiGraphics, sx, sy, SEARCH_W, SEARCH_H, 0xFFF0F0F0);
        DrawerHelper.drawBorder(guiGraphics, sx, sy, SEARCH_W, SEARCH_H, searchFocused ? 0xFF2D6BFF : C_BORDER, 1);
        if (searchFocused || !searchText.isEmpty()) {
            DrawerHelper.drawText(guiGraphics, truncate("搜索: " + searchText, 14) + (searchFocused ? "_" : ""),
                    sx + 3, sy + 2, 0.7f, C_TEXT);
        } else {
            DrawerHelper.drawText(guiGraphics, "搜索: 输入物料名过滤…", sx + 3, sy + 2, 0.7f, 0xFF9A9A9A);
        }
        // 左侧：全部配方列表（两行：主输入+次输入 / 主输出+次输出）
        int lmx = (int) mouseX - x, lmy = (int) mouseY - y;
        DrawerHelper.drawText(guiGraphics, "配方（" + allRecipes.size() + "）", x + LIST_X, y + LIST_Y - 9, 0.7f, C_TEXT_DIM);
        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= allRecipes.size()) break;
            PlannerRecipe r = allRecipes.get(idx);
            int ey = y + LIST_Y + i * ENTRY_H;
            boolean hover = lmx >= LIST_X && lmx < LIST_X + LIST_W && lmy >= LIST_Y + i * ENTRY_H && lmy < LIST_Y + (i + 1) * ENTRY_H;
            boolean sel = r == preview;
            DrawerHelper.drawSolidRect(guiGraphics, x + LIST_X, ey, LIST_W, ENTRY_H - 1,
                    sel ? 0x4D2D6BFF : hover ? 0x2EFFFFFF : 0x00000000);
            if (sel) DrawerHelper.drawBorder(guiGraphics, x + LIST_X, ey, LIST_W, ENTRY_H - 1, 0xFF2D6BFF, 1);
            drawIngredientLine(guiGraphics, r.inputs(), x + LIST_X + 2, ey + 1, true);
            drawIngredientLine(guiGraphics, r.outputs(), x + LIST_X + 2, ey + 12, false);
        }
        // 右侧：JEI 配方显示投影
        if (preview == null) {
            DrawerHelper.drawText(guiGraphics, "单击配方预览详情", x + INFO_X + 4, y + LIST_Y + 8, 0.8f, C_TEXT_DIM);
            DrawerHelper.drawText(guiGraphics, "再次点击同一配方确认", x + INFO_X + 4, y + LIST_Y + 22, 0.8f, C_TEXT_DIM);
        } else if (projection != null) {
            // JEI 投影画在右侧区域（屏幕全局坐标）
            int pw = projection.width(), ph = projection.height();
            int px = x + INFO_X + Math.max(2, (INFO_W - pw) / 2);
            int py = y + LIST_Y + 4;
            projection.draw(guiGraphics, mouseX, mouseY, px, py);
            DrawerHelper.drawText(guiGraphics, "再次点击本配方确认选择", x + INFO_X + 4, y + P_H - 12, 0.7f, C_TEXT_DIM);
        } else {
            DrawerHelper.drawText(guiGraphics, truncate(preview.displayName(), 18), x + INFO_X + 4, y + LIST_Y + 8, 0.8f, C_TEXT);
            DrawerHelper.drawText(guiGraphics, "JEI 不可用，无法预览", x + INFO_X + 4, y + LIST_Y + 24, 0.8f, C_TEXT_BLUE);
            DrawerHelper.drawText(guiGraphics, "再次点击本配方确认选择", x + INFO_X + 4, y + LIST_Y + 40, 0.7f, C_TEXT_DIM);
        }
    }

    /**
     * 物料行：主项（第一位，大字号）+ 次项（第二位，小一号）+ （多于两个时 "…"）。
     * 输入行主项最大；输出行整体比输入行小一号。
     */
    private static void drawIngredientLine(GuiGraphics g, List<PlannerStack> stacks, int x, int y, boolean input) {
        if (stacks.isEmpty()) return;
        int color = input ? C_TEXT : C_TEXT_BLUE;
        float big = input ? 0.75f : 0.6f;   // 输入主项 0.75 / 输出主项 0.6
        float small = input ? 0.6f : 0.5f;  // 输入次项 0.6 / 输出次项 0.5
        DrawerHelper.drawText(g, truncate(stackName(stacks.get(0)), 15), x, y, big, color);
        if (stacks.size() >= 2) {
            DrawerHelper.drawText(g, truncate(stackName(stacks.get(1)), 9), x + 72, y, small, C_TEXT_DIM);
        }
        if (stacks.size() > 2) {
            DrawerHelper.drawText(g, "…", x + 96, y, small, C_TEXT_DIM);
        }
    }

    // ---- 交互 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        Position p = getPosition();
        int lx = (int) mouseX - p.x, ly = (int) mouseY - p.y;
        // 搜索框：点击聚焦（打字过滤）；点击其他区域取消聚焦
        if (lx >= SEARCH_X && lx < SEARCH_X + SEARCH_W && ly >= SEARCH_Y && ly < SEARCH_Y + SEARCH_H) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        // 分类下拉按钮
        if (lx >= CAT_X && lx < CAT_X + CAT_W && ly >= CAT_Y && ly < CAT_Y + CAT_H) {
            toggleCategoryMenu();
            return true;
        }
        closeCategoryMenu();
        // 左侧配方列表：单击预览（桥接 JEI），再次单击同一配方确认
        if (lx >= LIST_X && lx < LIST_X + LIST_W && ly >= LIST_Y && ly < LIST_Y + LIST_H) {
            int idx = scroll + (ly - LIST_Y) / ENTRY_H;
            if (idx < allRecipes.size()) {
                PlannerRecipe r = allRecipes.get(idx);
                if (r == preview) {
                    applyRecipe(r);
                } else {
                    preview = r;
                    projection = PlannerJeiPlugin.createProjection(r); // JEI 投影（null=降级）
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        scroll = Math.max(0, Math.min(scroll - (int) (wheelDelta * 4), Math.max(0, allRecipes.size() - VISIBLE)));
        return true;
    }

    // ---- 搜索框键盘输入（聚焦时直接打字过滤） ----

    @Override
    public boolean charTyped(char ch, int mods) {
        if (!searchFocused) return false;
        if (ch >= 32) {
            setSearchText(searchText + ch);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (!searchFocused) return false;
        if (keyCode == 259) { // Backspace
            if (!searchText.isEmpty()) setSearchText(searchText.substring(0, searchText.length() - 1));
        } else if (keyCode == 256) { // Esc：取消聚焦，不关闭界面
            searchFocused = false;
        } else {
            return false;
        }
        return true;
    }

    // ---- 分类下拉弹层 ----

    private static final class CategoryMenu extends Widget {
        private static final int W = CAT_W, ENTRY_H = 16, MAX_VISIBLE = 12;
        private final RecipePickerMenu owner;
        private int scroll;

        CategoryMenu(RecipePickerMenu owner, int x, int y) {
            super(x, y, W, ENTRY_H * Math.min(RecipeCategory.all().size(), MAX_VISIBLE));
            this.owner = owner;
        }

        @Override
        public void drawInBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            Position p = getPosition();
            int lmx = (int) mouseX - p.x, lmy = (int) mouseY - p.y;
            List<RecipeCategory> cats = RecipeCategory.all();
            int visible = Math.min(cats.size(), MAX_VISIBLE);
            DrawerHelper.drawSolidRect(guiGraphics, p.x, p.y, W, ENTRY_H * visible, C_PANEL);
            DrawerHelper.drawBorder(guiGraphics, p.x, p.y, W, ENTRY_H * visible, C_BORDER, 1);
            for (int i = 0; i < visible; i++) {
                int idx = scroll + i;
                if (idx >= cats.size()) break;
                RecipeCategory c = cats.get(idx);
                int ey = p.y + i * ENTRY_H;
                boolean hover = lmx >= 0 && lmx < W && lmy >= i * ENTRY_H && lmy < (i + 1) * ENTRY_H;
                if (hover) DrawerHelper.drawSolidRect(guiGraphics, p.x, ey, W, ENTRY_H, 0x2EFFFFFF);
                DrawerHelper.drawText(guiGraphics, truncate(c.displayName(), 16) + (c == owner.category ? " ✓" : ""),
                        p.x + 4, ey + 2, 0.8f, C_TEXT);
            }
            if (cats.size() > visible) {
                DrawerHelper.drawText(guiGraphics, "… 共 " + cats.size() + " 类（滚轮翻页）", p.x + 4, p.y + ENTRY_H * visible - 3, 0.6f, C_TEXT_DIM);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                Position p = getPosition();
                int idx = scroll + ((int) mouseY - p.y) / ENTRY_H;
                if (idx >= 0 && idx < RecipeCategory.all().size()) {
                    owner.setCategory(RecipeCategory.all().get(idx));
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
            scroll = Math.max(0, Math.min(scroll - (int) (wheelDelta * 4),
                    Math.max(0, RecipeCategory.all().size() - MAX_VISIBLE)));
            return true;
        }
    }
}