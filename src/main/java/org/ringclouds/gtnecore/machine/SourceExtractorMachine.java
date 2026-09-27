package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 魔源子素摄取仪（魔力再锻 · Ars 第一机）：
 *
 * - 无 EU、无配方：把 Ars 魔源浆果（ars_nouveau:sourceberry_bush，营养2/饱和0.1）按
 *   菌丝公式（11×营养+60×饱和=28/颗）经 16 并行通道（200t/颗）转化为魔源，
 *   存入内部 50,000 池；实现 ars ISourceTile 并注册 SourceManager —— 5~10 格内
 *   Ars 方块可直接抽池。
 * - 顶环（.js 小写 a/b = 真实 ars sourcestone/gem）：成型后经
 *   {@link StructureTransparencyHook} 把 16 个环位登记幽灵并替换为
 *   不可破坏/不可放置覆盖的隐形 StructureGhostBlock 占位——mixin 让成型重检按原方块
 *   判定（结构不解体），渲染层环隐形；glTF 魔力环(Y+4)与存量紫球由两个 DynamicRender
 *   客户端呈现。解体时占位自动替换回原方块（材料不丢）。
 *
 * 数值/几何常量集中在类顶（调参位）。
 */
public class SourceExtractorMachine extends WorkableMultiblockMachine implements ISourceTile,
        com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            SourceExtractorMachine.class, WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    // ============ 数值调参 ============
    /** 内部魔源池上限（5× Ars Source Jar 10000） */
    public static final int MAX_SOURCE = 50_000;
    /** 单颗浆果产源 = 菌丝公式 11×营养2 + 60×饱和0.1 = 28 */
    public static final int SOURCE_PER_BERRY = 28;
    /** 单通道转换时长（tick） */
    public static final int BERRY_DURATION = 200;
    /** 并行通道数 */
    public static final int LANES = 16;

    /** 顶环格位（相对控制器中心的 x/z 偏移，Y 偏移固定 +4；4 向旋转候选都试） */
    private static final int[][] RING_CELLS = {
            {-1, -3}, {0, -3}, {1, -3}, {-2, -2}, {2, -2}, {-3, -1}, {3, -1}, {-3, 0},
            {3, 0}, {-3, 1}, {3, 1}, {-2, 2}, {2, 2}, {-1, 3}, {0, 3}, {1, 3}};

    private static Item berryItemCache;

    private static Item berryItem() {
        if (berryItemCache == null) {
            berryItemCache = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ars_nouveau", "sourceberry_bush"));
        }
        return berryItemCache;
    }

    private static Block arsBlock(String id) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ars_nouveau", id));
    }

    // ============ 状态 ============
    /** 魔源池（服务端权威，DescSynced 给客户端驱动紫球半径） */
    @Persisted
    @DescSynced
    private int storedSource;


    /** 顶环隐藏持久化："x,y,z,kind;…"（kind=S sourcestone / G gem），重启后重建幽灵表 */
    @Persisted
    private String ghostData = "";

    /** 服务端幽灵表：隐藏位 → 原方块状态（StructureTransparencyHook 登记） */
    private final Map<BlockPos, BlockState> ghostMap = new HashMap<>();

    /** 已注册 Ars 供源接口（失效项由 SourceManager 60t 清理） */
    private ISpecialSourceProvider sourceProvider;

    /** 主线程队列：成型/解体回调可能来自异步线程，世界改动一律延迟到主 tick 执行 */
    private boolean hideQueued;
    private boolean restoreQueued;

    public SourceExtractorMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ============ 结构生命周期 ============

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.hideQueued = true;      // 延迟到主线程 tick（成型回调可能在异步线程）
        registerSourceProvider();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.restoreQueued = true;   // 同上
        this.sourceProvider = null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        var self = self();
        if (self.getLevel() != null && !self.getLevel().isClientSide) {
            // 重启后重建幽灵表：保存时若已清空（空气），成型重检能按原方块通过
            rebuildGhostFromData(self.getLevel());
            self.subscribeServerTick(this::conversionTick);
        }
    }

    // ============ 顶环透明化（可复用钩子，无新增方块） ============

    private int busyLanes() {
        var logic = this.getRecipeLogic();
        return logic != null && logic.getStatus() == com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.Status.WORKING ? 1 : 0;
    }


    /** 主线程执行：登记幽灵并替换 Y+4 环位（ars sourcestone/gem ×16）为幽灵占位。 */
    private void performHide(Level level) {
        if (level == null || level.isClientSide) return;
        Block sourcestone = arsBlock("sourcestone");
        Block gem = arsBlock("source_gem_block");
        if (sourcestone == null || gem == null) return;
        BlockPos layer = getPos().above(4);
        List<BlockPos> targets = new ArrayList<>();
        for (int[] cell : RING_CELLS) {
            int a = cell[0], b = cell[1];
            for (int rot = 0; rot < 4; rot++) {
                int x = rot == 0 ? a : rot == 1 ? b : rot == 2 ? -a : -b;
                int z = rot == 0 ? b : rot == 1 ? -a : rot == 2 ? -b : a;
                BlockPos pos = layer.offset(x, 0, z);
                BlockState state = level.getBlockState(pos);
                if ((state.is(sourcestone) || state.is(gem)) && !ghostMap.containsKey(pos)
                        && !StructureTransparencyHook.isGhostState(state)) {
                    ghostMap.put(pos, state);
                    targets.add(pos);
                }
            }
        }
        if (targets.isEmpty()) return;
        StructureTransparencyHook.addAll(level, ghostMap); // 先登记后替换：中间重检也按原方块判定
        Block ghost = StructureTransparencyHook.ghostBlock();
        if (ghost == null) return; // 幽灵块尚未注册（理论不会走到）
        BlockState ghostState = ghost.defaultBlockState();
        for (BlockPos pos : targets) {
            BlockState old = level.getBlockState(pos);
            level.setBlock(pos, ghostState,
                    net.minecraft.world.level.block.Block.UPDATE_NEIGHBORS | net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            level.sendBlockUpdated(pos, old, ghostState,
                    net.minecraft.world.level.block.Block.UPDATE_NEIGHBORS | net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        saveGhostData();
    }

    /** 主线程执行：解体还原。 */
    private void performRestore(Level level) {
        if (level == null || ghostMap.isEmpty()) return;
        StructureTransparencyHook.removeAll(level, ghostMap.keySet());
        if (!level.isClientSide) {
            for (var e : ghostMap.entrySet()) {
                BlockPos pos = e.getKey();
                BlockState current = level.getBlockState(pos);
                if (current.isAir() || StructureTransparencyHook.isGhostState(current)) {
                    level.setBlock(pos, e.getValue(),
                            net.minecraft.world.level.block.Block.UPDATE_NEIGHBORS | net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    level.sendBlockUpdated(pos, current, e.getValue(),
                            net.minecraft.world.level.block.Block.UPDATE_NEIGHBORS | net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
        ghostMap.clear();
        this.ghostData = "";
    }

    /** 从持久化数据重建幽灵表（服务端 onLoad）。 */
    private void rebuildGhostFromData(Level level) {
        if (ghostData == null || ghostData.isEmpty()) return;
        for (String entry : ghostData.split(";")) {
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(",");
            if (parts.length != 4) continue;
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                Block block = arsBlock(parts[3].equals("G") ? "source_gem_block" : "sourcestone");
                if (block == null) continue;
                BlockState state = level.getBlockState(pos);
                // 物理仍在则不动；若已是空气（上次保存前已隐藏）登记幽灵供成型重检
                ghostMap.put(pos, block.defaultBlockState());
                StructureTransparencyHook.add(level, pos, block.defaultBlockState());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void saveGhostData() {
        StringBuilder sb = new StringBuilder();
        for (var e : ghostMap.entrySet()) {
            BlockPos p = e.getKey();
            boolean gem = e.getValue().getBlock() == arsBlock("source_gem_block");
            sb.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ())
                    .append(',').append(gem ? 'G' : 'S').append(';');
        }
        this.ghostData = sb.toString();
    }

    // ============ 转换主循环（服务端每 tick） ============

    private long ghostCheckCounter;

    private void conversionTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        // 主线程队列执行（成型/解体回调可能来自 GTCEu 异步线程，不能直接改世界）
        if (hideQueued) {
            hideQueued = false;
            if (isFormed()) performHide(level);
        }
        if (restoreQueued) {
            restoreQueued = false;
            performRestore(level);
        }
        // 自愈保险：解体态但幽灵表仍有残留（错过后还原时机/异常路径）→ 每 100t 强制还原重试
        if (!isFormed() && !ghostMap.isEmpty() && ++ghostCheckCounter % 100 == 0) {
            LOGGER.warn("摄取仪[{}] 幽灵残留自愈: {} 格未还原", getPos(), ghostMap.size());
            performRestore(level);
        }
        // 供源自愈：SourceManager 每 60t 清理 isValid=false 的 provider（一旦被误清不再恢复）
        // → 成型态每 600t 幂等重注册（Set 去重），并诊断首次 isValid=false
        if (isFormed() && ++sourceRegCounter % 600 == 0) {
            if (!providerValidLogged && sourceProvider != null && !sourceProvider.isValid()) {
                providerValidLogged = true;
                LOGGER.warn("摄取仪[{}] 供源 provider isValid=false（isInValid={}）——检查持有状态",
                        getPos(), isInValid());
            }
            registerSourceProvider();
        }
    }

    private long sourceRegCounter;
    private boolean providerValidLogged;

    /** 配方完成钩子：读配方 data.source × 并行数入池（IWorkable.afterWorking 由 RecipeLogic 在完成时调用） */
    @Override
    public void afterWorking() {
        super.afterWorking();
        var logic = this.getRecipeLogic();
        var recipe = logic != null ? logic.getLastRecipe() : null;
        if (recipe == null || recipe.data == null) return;
        int per = recipe.data.getInt("source");
        if (per <= 0) return;
        int parallel = Math.max(1, recipe.parallels);
        storedSource = Math.min(MAX_SOURCE, storedSource + per * parallel);
    }

    // ============ Ars 供源注册 ============

    private void registerSourceProvider() {
        var level = getLevel();
        if (level == null || level.isClientSide) return;
        if (sourceProvider == null) {
        this.sourceProvider = new ISpecialSourceProvider() {
            @Override
            public ISourceTile getSource() {
                return SourceExtractorMachine.this;
            }

            @Override
            public boolean isValid() {
                return !isInValid() && isFormed();
            }

            @Override
            public BlockPos getCurrentPos() {
                return getPos();
            }
        };
        }
        // 幂等重入（Set 去重）：被 SourceManager.tick 误清后可恢复
        SourceManager.INSTANCE.addInterface(level, sourceProvider);
    }

    // ============ ISourceTile（机器 = 巨型魔源罐供 Ars 直接抽取） ============

    @Override
    public int getTransferRate() {
        return MAX_SOURCE;
    }

    @Override
    public boolean canAcceptSource() {
        return isFormed() && storedSource < MAX_SOURCE;
    }

    @Override
    public int getSource() {
        return storedSource;
    }

    @Override
    public int getMaxSource() {
        return MAX_SOURCE;
    }

    @Override
    public void setMaxSource(int maxSource) {
        // 固定容量，不接受外部修改
    }

    @Override
    public int setSource(int source) {
        int next = Math.max(0, Math.min(MAX_SOURCE, source));
        int delta = next - storedSource;
        storedSource = next;
        return delta;
    }

    @Override
    public int addSource(int amount) {
        if (amount <= 0 || !canAcceptSource()) return 0;
        int added = Math.min(amount, MAX_SOURCE - storedSource);
        storedSource += added;
        return added;
    }

    @Override
    public int removeSource(int amount) {
        if (amount <= 0) return 0;
        int removed = Math.min(amount, storedSource);
        storedSource -= removed;
        return removed;
    }

    // ============ UI（GTM 风格：ComponentPanel 面板 + 进度条 + 魔源总量） ============

    @Override
    public com.lowdragmc.lowdraglib.gui.widget.Widget createUIWidget() {
        var group = new com.lowdragmc.lowdraglib.gui.widget.WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup(4, 4, 182, 100)
                .setBackground(com.gregtechceu.gtceu.api.gui.GuiTextures.BACKGROUND)
                .addWidget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(4, 5,
                        self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget(4, 20, this::addDisplayText)
                        .setMaxWidthLimit(180)));
        group.setBackground(com.gregtechceu.gtceu.api.gui.GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    /** 控制器面板文本：魔源总量 + 运行状态 */
    protected void addDisplayText(java.util.List<net.minecraft.network.chat.Component> text) {
        var logic = this.getRecipeLogic();
        text.add(net.minecraft.network.chat.Component.translatable(
                "block.gtnecore.source_extractor.ui.source", storedSource, MAX_SOURCE));
        if (logic != null && logic.getStatus() == com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.Status.WORKING) {
            text.add(net.minecraft.network.chat.Component.translatable(
                    "block.gtnecore.source_extractor.ui.working",
                    logic.getProgress(), logic.getMaxProgress(),
                    String.format("%.0f%%", logic.getProgressPercent() * 100.0F)));
        } else {
            text.add(net.minecraft.network.chat.Component.translatable(
                    "block.gtnecore.source_extractor.ui.idle"));
        }
    }

    /** 客户端读取池量（紫球半径/显示用） */
    public int getStoredSource() {
        return storedSource;
    }
}
