package org.ringclouds.gtnecore.planner.client;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.ringclouds.gtnecore.planner.api.RecipeLookup;
import org.ringclouds.gtnecore.planner.model.PlannerEdge;
import org.ringclouds.gtnecore.planner.model.PlannerGraph;
import org.ringclouds.gtnecore.planner.model.PlannerNode;

/**
 * 配方规划器主界面（仿 LDLib UI 编辑器三段式布局）：
 * 左 = 节点类型面板（调色板）｜ 中 = 节点画布 ｜ 右 = 选中节点信息 + 电量汇总。
 * 交互：左键拖动节点、单击节点=选中并打开配方选择、右键画布=弹节点类型菜单、×删除。
 */
public final class PlannerScreen {

    private static final int W = 620, H = 300;
    // MC 原生容器配色
    private static final int C_PANEL = 0xFFC6C6C6;
    private static final int C_BORDER = 0xFF373737;
    private static final int C_HIGHLIGHT = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFF404040;
    private static final int C_TEXT_DIM = 0xFF555555;

    private PlannerScreen() {
    }

    public static void open(PlannerGraph graph, RecipeLookup lookup) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        Minecraft.getInstance().setScreen(new ModularUIGuiContainer(buildUI(graph, lookup, player), 0));
    }

    private static ModularUI buildUI(PlannerGraph graph, RecipeLookup lookup, Player player) {
        ModularUI ui = new ModularUI(W, H, IUIHolder.EMPTY, player);
        // 画布需要 root（mainGroup）挂弹层（只有 mainGroup 会把点击分发给弹层）
        PlannerCanvas canvas = new PlannerCanvas(graph, lookup, 100, 4, 324, 292, ui.mainGroup);
        NodeInfoPanel info = new NodeInfoPanel(canvas, 428, 4, 188, 292);
        canvas.infoPanel = info;
        PalettePanel palette = new PalettePanel(canvas, 4, 4, 92, 292);
        ui.widget(palette)
                .widget(canvas)
                .widget(info);
        // 纯客户端直开 UI 时，服务端开 UI 的绑定流程不会跑（ModularUIGuiContainer
        // 不调 initWidgets），必须手动绑定 widget 树，否则拖拽时 getGui() 为 null 崩溃
        ui.initWidgets();
        // 关闭界面时落盘（ESC / 切界面都会走 close 回调）
        ui.registerCloseListener(PlannerEntry::save);
        return ui;
    }

    // ---- 左侧：节点类型面板（调色板） ----

    private static final class PalettePanel extends Widget {

        private final PlannerCanvas canvas;
        private final int pw, ph;

        PalettePanel(PlannerCanvas canvas, int x, int y, int w, int h) {
            super(x, y, w, h);
            this.canvas = canvas;
            this.pw = w;
            this.ph = h;
        }

        @Override
        public void drawInBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            Position p = getPosition();
            int x = p.x, y = p.y;
            DrawerHelper.drawSolidRect(guiGraphics, x, y, pw, ph, C_PANEL);
            DrawerHelper.drawBorder(guiGraphics, x, y, pw, ph, C_BORDER, 1);
            DrawerHelper.drawBorder(guiGraphics, x + 1, y + 1, pw - 2, ph - 2, C_HIGHLIGHT, 1);
            DrawerHelper.drawText(guiGraphics, "节点类型", x + 4, y + 3, 0.8f, C_TEXT);
            // 配方节点按钮（MC 槽位风格）
            int bx = x + 6, by = y + 18, bw = pw - 12, bh = 20;
            boolean hover = (int) mouseX >= bx && (int) mouseX < bx + bw && (int) mouseY >= by && (int) mouseY < by + bh;
            DrawerHelper.drawSolidRect(guiGraphics, bx, by, bw, bh, hover ? 0xFFD6D6D6 : C_PANEL);
            DrawerHelper.drawBorder(guiGraphics, bx, by, bw, bh, C_HIGHLIGHT, 1);
            DrawerHelper.drawBorder(guiGraphics, bx + 1, by + 1, bw - 2, bh - 2, C_BORDER, 1);
            DrawerHelper.drawText(guiGraphics, "配方节点", bx + 4, by + 4, 0.8f, C_TEXT);
            // 操作提示
            DrawerHelper.drawText(guiGraphics, "操作:", x + 4, y + 52, 0.7f, C_TEXT_DIM);
            DrawerHelper.drawText(guiGraphics, "左键拖动移动", x + 4, y + 66, 0.7f, C_TEXT_DIM);
            DrawerHelper.drawText(guiGraphics, "单击=选配方", x + 4, y + 80, 0.7f, C_TEXT_DIM);
            DrawerHelper.drawText(guiGraphics, "右键画布=新建", x + 4, y + 94, 0.7f, C_TEXT_DIM);
            DrawerHelper.drawText(guiGraphics, "×=删除节点", x + 4, y + 108, 0.7f, C_TEXT_DIM);
            DrawerHelper.drawText(guiGraphics, "滚轮翻页", x + 4, y + 126, 0.7f, C_TEXT_DIM);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                Position p = getPosition();
                int bx = p.x + 6, by = p.y + 18, bw = pw - 12, bh = 20;
                if (mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh) {
                    canvas.addNodeAtCenter();
                    return true;
                }
            }
            return false;
        }
    }

    // ---- 中间：节点画布 ----

    /** 节点画布：右键弹"选择节点类型"菜单；单击=选中+配方面板；左键×删除/拖动；
     * 中键=端口连线（Blender 式，未连到目标端口则不生成）；空白左键拖动=平移视野。
     * 用普通 WidgetGroup（不用 DraggableScrollableWidgetGroup——其内置滚动会与
     * 自研视野平移/子控件坐标冲突，导致节点 Y 偏移、悬停失效）。 */
    static final class PlannerCanvas extends WidgetGroup {

        private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

        private final PlannerGraph graph;
        private final RecipeLookup lookup;
        private final java.util.Map<PlannerNode, RecipeNodeWidget> nodeWidgets = new java.util.HashMap<>();
        NodeInfoPanel infoPanel; // 右侧信息面板（buildUI 注入）
        WidgetGroup root; // mainGroup：挂弹层（只有 mainGroup 会把点击分发给弹层）
        PlannerNode selectedNode;
        private NodeTypeMenu openMenu;
        private RecipePickerMenu openPicker;
        private int tick;
        // 自研拖拽（不用 LDLib IDraggable：其 delta 整数截断+位置钳制会抖动）
        private PlannerNode draggingNode;
        private double grabDX, grabDY; // 按下时 鼠标全局坐标 − 节点原点全局坐标
        private double pressX, pressY; // 按下位置（判断点击 vs 拖拽）
        // 高度拉伸：底部手柄命中后只改高度（宽度固定），避免节点重叠
        private PlannerNode resizingNode;
        // 连线（Blender/UE 蓝图式）：端口拖拽 → 贝塞尔预览线 → 松手建边
        private boolean linking;
        private PlannerNode linkNode;
        private int linkPort;
        private boolean linkIsOutput;
        private int linkMouseX, linkMouseY;
        // 画布平移：节点位置是"世界坐标"，渲染/命中换算 视野偏移(viewX,viewY)
        private int viewX, viewY;
        private boolean panning; // 空白区左键拖动 = 平移视野
        private int pressViewX, pressViewY;

        PlannerCanvas(PlannerGraph graph, RecipeLookup lookup, int x, int y, int w, int h, WidgetGroup root) {
            super(x, y, w, h);
            this.graph = graph;
            this.lookup = lookup;
            this.root = root;
            setBackground(new ColorRectTexture(0x33000000));
            graph.recalc();
            for (PlannerNode n : graph.nodes()) {
                addNodeWidget(n);
            }
        }

        /** Blender/UE 蓝图式连线：先画边 + 拖拽预览线，再画节点卡片（连线垫底）。
         * 整个画布内容裁剪到画布矩形内（超出部分不渲染）。 */
        @Override
        public void drawInBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            Position pos = getPosition();
            int ox = pos.x - viewX, oy = pos.y - viewY; // 世界 → 屏幕
            guiGraphics.enableScissor(pos.x, pos.y, pos.x + getSize().width, pos.y + getSize().height);
            // 已建立的边
            for (PlannerEdge e : graph.edges()) {
                PlannerNode a = graph.node(e.fromNode()), b = graph.node(e.toNode());
                if (a == null || b == null || !a.hasRecipe() || !b.hasRecipe()) continue;
                int ax = a.x + RecipeNodeWidget.NODE_W + ox;
                int ay = a.y + RecipeNodeWidget.portYOffset(a.recipe.outputs().size(), e.fromOutput(), a.height) + oy;
                int bx = b.x + ox;
                int by = b.y + RecipeNodeWidget.portYOffset(b.recipe.inputs().size(), e.toInput(), b.height) + oy;
                drawBezier(guiGraphics, ax, ay, bx, by, 0xFF8A8A8A);
            }
            // 拖拽中的预览线（端口 → 鼠标）
            if (linking && linkNode != null) {
                int sx, sy;
                if (linkIsOutput) {
                    sx = linkNode.x + RecipeNodeWidget.NODE_W + ox;
                } else {
                    sx = linkNode.x + ox;
                }
                sy = linkNode.y + RecipeNodeWidget.portYOffset(
                        linkIsOutput ? linkNode.recipe.outputs().size() : linkNode.recipe.inputs().size(),
                        linkPort, linkNode.height) + oy;
                drawBezier(guiGraphics, sx, sy, linkMouseX, linkMouseY, 0xFF2D6BFF);
            }
            // 输入端口缺口标记（红字）：已连接但供给不足
            for (PlannerNode n : graph.nodes()) {
                if (!n.hasRecipe()) continue;
                int in = n.recipe.inputs().size();
                for (int i = 0; i < in; i++) {
                    long miss = graph.missingAt(n, i);
                    if (miss <= 0) continue;
                    int px = n.x + 2 + ox;
                    int py = n.y + RecipeNodeWidget.portYOffset(in, i, n.height) - 4 + oy;
                    DrawerHelper.drawText(guiGraphics, "缺×" + miss, px, py, 0.5f, 0xFFB00000);
                }
            }
            super.drawInBackground(guiGraphics, mouseX, mouseY, partialTicks);
            guiGraphics.disableScissor();
        }

        /** 三次贝塞尔曲线（水平控制点，Blender/UE 蓝图风格），逐像素采样画线 */
        private static void drawBezier(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
            int dx = Math.max(20, Math.min(80, Math.abs(x2 - x1) / 2));
            int cx1 = x1 + (x2 >= x1 ? dx : -dx);
            int cx2 = x2 + (x2 >= x1 ? -dx : dx);
            for (int i = 0; i <= 32; i++) {
                double t = i / 32.0;
                double mt = 1 - t;
                int x = (int) Math.round(mt * mt * mt * x1 + 3 * mt * mt * t * cx1 + 3 * mt * t * t * cx2 + t * t * t * x2);
                int y = (int) Math.round(mt * mt * mt * y1 + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t * t * t * y2);
                g.fill(x, y, x + 1, y + 1, color);
            }
        }

        PlannerGraph graph() {
            return graph;
        }

        /** 节点对应的卡片控件（供外部调整尺寸） */
        RecipeNodeWidget nodeWidget(PlannerNode n) {
            return nodeWidgets.get(n);
        }

        private void addNodeWidget(PlannerNode n) {
            RecipeNodeWidget w = new RecipeNodeWidget(n);
            w.setSelfPosition(n.x - viewX, n.y - viewY);
            nodeWidgets.put(n, w);
            addWidget(w);
        }

        /** 平移后刷新所有节点控件位置 */
        private void applyView() {
            for (RecipeNodeWidget w : nodeWidgets.values()) {
                w.setSelfPosition(w.node.x - viewX, w.node.y - viewY);
            }
        }

        /** 左面板"配方节点"按钮：视野中心新建 */
        void addNodeAtCenter() {
            PlannerNode n = graph.addNode(viewX + Math.max(0, getSize().width / 2 - RecipeNodeWidget.NODE_W / 2),
                    viewY + Math.max(0, getSize().height / 2 - RecipeNodeWidget.DEFAULT_H / 2));
            addNodeWidget(n);
            selectNode(n);
        }

        /** 更新选中状态（金色边框 + 右侧信息面板） */
        private void selectNode(PlannerNode n) {
            selectedNode = n;
            for (RecipeNodeWidget w : nodeWidgets.values()) {
                w.setSelected(w.node == n);
            }
        }

        // ---- 节点类型菜单 ----

        private void closeMenu() {
            if (openMenu != null) {
                root.removeWidget(openMenu);
                openMenu = null;
            }
        }

        /** 右键位置弹出类型菜单（挂 root 下；selfPosition = 全局坐标 − root 全局位置） */
        private void openTypeMenu(int globalX, int globalY) {
            closeMenu();
            Position rootPos = root.getPosition();
            Position pos = getPosition();
            int lx = globalX - pos.x + viewX, ly = globalY - pos.y + viewY; // 世界坐标锚点
            openMenu = new NodeTypeMenu(this, globalX - rootPos.x, globalY - rootPos.y, lx, ly);
            root.addWidget(openMenu);
        }

        /** 菜单选中类型：在世界坐标锚点创建对应节点 */
        void pickNodeType(PlannerNodeType type, int anchorX, int anchorY) {
            closeMenu();
            if (type == PlannerNodeType.RECIPE) {
                PlannerNode n = graph.addNode(anchorX - 42, anchorY - 30);
                LOGGER.info("[planner] 新建配方节点 id={} at=({}, {})", n.id, n.x, n.y);
                addNodeWidget(n);
                selectNode(n);
            }
        }

        // ---- 配方选择面板 ----

        private void closePicker() {
            if (openPicker != null) {
                openPicker.close(); // 内部会连分类下拉一起关
                openPicker = null;
            }
        }

        private void openPicker(PlannerNode n) {
            closeMenu();
            closePicker();
            Position rootPos = root.getPosition();
            int px = getPosition().x + 10 - rootPos.x;
            int py = getPosition().y + 8 - rootPos.y;
            openPicker = new RecipePickerMenu(this, n, lookup, px, py);
            root.addWidget(openPicker);
        }

        /** 配方已应用到节点：关面板 + 重算供需（子节点由玩家手动右键创建） */
        void onRecipePicked(PlannerNode parent) {
            closePicker();
            graph.recalc();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 任何点击先关旧菜单/旧面板（自身处理掉的点击不会走到这里）
            if (openMenu != null) closeMenu();
            if (openPicker != null) closePicker();
            if (button == 1) {
                // 右键：点在卡片上 = 切换配方（打开配方选择面板）；空白 = 选择节点类型菜单
                Position pos = getPosition();
                int lx = (int) mouseX - pos.x, ly = (int) mouseY - pos.y;
                int wx = lx + viewX, wy = ly + viewY;
                for (int i = graph.nodes().size() - 1; i >= 0; i--) {
                    PlannerNode n = graph.nodes().get(i);
                    if (wx < n.x || wx >= n.x + RecipeNodeWidget.NODE_W
                            || wy < n.y || wy >= n.y + n.height) continue;
                    RecipeNodeWidget w = nodeWidgets.get(n);
                    if (w == null) continue;
                    if (w.isDeleteButton(wx - n.x, wy - n.y)) return true; // ×删除区：不切配方
                    selectNode(n);
                    openPicker(n);
                    return true;
                }
                openTypeMenu((int) mouseX, (int) mouseY);
                return true;
            }
            Position pos = getPosition();
            int lx = (int) mouseX - pos.x, ly = (int) mouseY - pos.y;
            int wx = lx + viewX, wy = ly + viewY; // 世界坐标
            if (button == 2) {
                // 中键：从端口拖出连线（Blender 式）；起点必须是端口，否则忽略
                PortHit port = hitPort(wx, wy);
                if (port != null) {
                    linking = true;
                    linkNode = port.node;
                    linkPort = port.port;
                    linkIsOutput = port.isOutput;
                    linkMouseX = (int) mouseX;
                    linkMouseY = (int) mouseY;
                    return true;
                }
                return false;
            }
            if (button == 0) {
                // 左键：端口连线 → ×删除 → 底部拉伸 → 卡片拖动；空白区 = 平移视野
                // 端口优先（Blender 式连线起点/终点）
                PortHit port = hitPort(wx, wy);
                if (port != null) {
                    linking = true;
                    linkNode = port.node;
                    linkPort = port.port;
                    linkIsOutput = port.isOutput;
                    linkMouseX = (int) mouseX;
                    linkMouseY = (int) mouseY;
                    return true;
                }
                for (int i = graph.nodes().size() - 1; i >= 0; i--) {
                    PlannerNode n = graph.nodes().get(i);
                    if (wx < n.x || wx >= n.x + RecipeNodeWidget.NODE_W
                            || wy < n.y || wy >= n.y + n.height) continue;
                    RecipeNodeWidget w = nodeWidgets.get(n);
                    if (w == null) continue;
                    if (w.isDeleteButton(wx - n.x, wy - n.y)) {
                        LOGGER.info("[planner] 点击×删除节点 {}", n.id);
                        graph.removeNode(n.id);
                        nodeWidgets.remove(n);
                        removeWidget(w);
                        if (selectedNode == n) selectNode(null);
                        return true;
                    }
                    if (w.isResizeHandle(wx - n.x, wy - n.y)) {
                        // 底部手柄：只拉伸高度（宽度固定），避免节点重叠
                        resizingNode = n;
                        return true;
                    }
                    // 卡片非按钮区：准备拖拽（记录按下位置 + 抓取偏移；
                    // 若松手时位移很小则视为"单击"→选中并打开配方选择面板）
                    draggingNode = n;
                    grabDX = mouseX - (pos.x + n.x - viewX);
                    grabDY = mouseY - (pos.y + n.y - viewY);
                    pressX = mouseX;
                    pressY = mouseY;
                    return true;
                }
                // 空白区：开始平移
                panning = true;
                pressViewX = viewX;
                pressViewY = viewY;
                pressX = mouseX;
                pressY = mouseY;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        /**
         * 自研拖拽：节点跟随鼠标（世界坐标 = 全局 − 抓取偏移 − 画布原点 + 视野偏移），
         * 或空白区拖动平移视野。不依赖 LDLib 的 delta（整数截断）机制，移动平滑。
         */
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            // 位移超过阈值才算拖拽（区分单击）
            boolean moved = Math.abs(mouseX - pressX) > 3 || Math.abs(mouseY - pressY) > 3;
            if (button == 2) {
                // 中键连线拖拽：只更新预览线终点
                if (linking) {
                    linkMouseX = (int) mouseX;
                    linkMouseY = (int) mouseY;
                    return true;
                }
                return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            }
            if (button == 0) {
                if (linking) {
                    // 左键连线拖拽：只更新预览线终点
                    linkMouseX = (int) mouseX;
                    linkMouseY = (int) mouseY;
                    return true;
                }
                if (!moved) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
                if (resizingNode != null) {
                    // 只拉伸高度：世界 y 从节点顶算起，钳制在 [60, 200]；宽度固定不变
                    Position pos = getPosition();
                    int wy = (int) Math.round(mouseY - pos.y + viewY);
                    int h = Math.max(RecipeNodeWidget.DEFAULT_H, Math.min(200, wy - resizingNode.y));
                    resizingNode.height = h;
                    RecipeNodeWidget w = nodeWidgets.get(resizingNode);
                    if (w != null) w.setNodeHeight(h);
                    return true;
                }
                if (draggingNode != null) {
                    Position pos = getPosition();
                    int nx = (int) Math.round(mouseX - grabDX - pos.x + viewX);
                    int ny = (int) Math.round(mouseY - grabDY - pos.y + viewY);
                    // 防重叠：新位置与其他节点矩形相交则拒绝移动（保持原位）
                    if (!overlapsAny(draggingNode, nx, ny)) {
                        draggingNode.x = nx;
                        draggingNode.y = ny;
                        RecipeNodeWidget w = nodeWidgets.get(draggingNode);
                        if (w != null) w.setSelfPosition(nx - viewX, ny - viewY);
                    }
                    return true;
                }
                if (panning) {
                    viewX = pressViewX - (int) Math.round(mouseX - pressX);
                    viewY = pressViewY - (int) Math.round(mouseY - pressY);
                    applyView();
                    return true;
                }
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        /** 端口命中（世界坐标）：返回节点 + 端口索引 + 是否输出口 */
        private PortHit hitPort(int wx, int wy) {
            for (PlannerNode n : graph.nodes()) {
                if (!n.hasRecipe()) continue;
                int in = n.recipe.inputs().size(), out = n.recipe.outputs().size();
                for (int i = 0; i < out; i++) {
                    int px = n.x + RecipeNodeWidget.NODE_W, py = n.y + RecipeNodeWidget.portYOffset(out, i, n.height);
                    if (Math.abs(wx - px) <= 5 && Math.abs(wy - py) <= 5) return new PortHit(n, i, true);
                }
                for (int i = 0; i < in; i++) {
                    int px = n.x, py = n.y + RecipeNodeWidget.portYOffset(in, i, n.height);
                    if (Math.abs(wx - px) <= 5 && Math.abs(wy - py) <= 5) return new PortHit(n, i, false);
                }
            }
            return null;
        }

        private record PortHit(PlannerNode node, int port, boolean isOutput) {
        }

        /** 节点矩形（NODE_W × height）是否与其他节点相交 */
        private boolean overlapsAny(PlannerNode self, int nx, int ny) {
            for (PlannerNode o : graph.nodes()) {
                if (o == self) continue;
                if (nx < o.x + RecipeNodeWidget.NODE_W && nx + RecipeNodeWidget.NODE_W > o.x
                        && ny < o.y + o.height && ny + self.height > o.y) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 2 && linking) {
                // 中键松手：命中另一节点端口则建边（输出→输入），否则不生成连线
                Position pos = getPosition();
                int wx = (int) mouseX - pos.x + viewX, wy = (int) mouseY - pos.y + viewY;
                PortHit target = hitPort(wx, wy);
                if (target != null && target.node != linkNode) {
                    if (linkIsOutput && !target.isOutput) {
                        graph.addEdge(linkNode.id, linkPort, target.node.id, target.port);
                    } else if (!linkIsOutput && target.isOutput) {
                        graph.addEdge(target.node.id, target.port, linkNode.id, linkPort);
                    }
                    graph.recalc();
                }
                linking = false;
                linkNode = null;
                return true;
            }
            if (button == 0) {
                if (linking) {
                    // 左键松手：命中另一节点端口则建边（输出→输入），否则取消连线
                    Position pos = getPosition();
                    int wx = (int) mouseX - pos.x + viewX, wy = (int) mouseY - pos.y + viewY;
                    PortHit target = hitPort(wx, wy);
                    if (target != null && target.node != linkNode) {
                        if (linkIsOutput && !target.isOutput) {
                            graph.addEdge(linkNode.id, linkPort, target.node.id, target.port);
                        } else if (!linkIsOutput && target.isOutput) {
                            graph.addEdge(target.node.id, target.port, linkNode.id, linkPort);
                        }
                        graph.recalc();
                    }
                    linking = false;
                    linkNode = null;
                    return true;
                }
                if (draggingNode != null || panning || resizingNode != null) {
                    boolean isClick = Math.abs(mouseX - pressX) <= 3 && Math.abs(mouseY - pressY) <= 3;
                    if (resizingNode != null) {
                        resizingNode = null; // 拉伸结束（不算点击，不开配方面板）
                    }
                    if (draggingNode != null) {
                        PlannerNode n = draggingNode;
                        draggingNode = null;
                        if (isClick) {
                            selectNode(n);      // 左键单击卡片 = 只选中（右键才切换配方）
                        }
                    }
                    if (panning) {
                        panning = false;
                        if (isClick) selectNode(null); // 空白单击 = 取消选中
                    }
                    return true;
                }
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void updateScreen() {
            super.updateScreen();
            if (++tick % 20 == 0) {
                graph.recalc();
            }
        }
    }
}
