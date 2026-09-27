package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 维格纳-亨廷顿对顶砧（Wigner-Huntington Anvil）：
 * UEV 时代的超高压压机——把液态氢在钻石对顶砧之间压成金属氢（H 的锭）。
 *
 * - 电动多方块（{@link WorkableElectricMultiblockMachine}）：标准 RecipeLogic，无需自定义逻辑，
 *   GTM 电动多方块 UI 自带（只在 {@code addDisplayText} 追加两行专有信息）
 * - 结构：13×13 底座、26 高，逐字翻译自 {@code kjs版多方块结构/维格纳-亨廷顿对顶砧.js}
 *   （蛙明灯位 = 控制器）。砧体 = avaritia 钻石晶格块，外壳 = 聚变玻璃，
 *   中段环 = UHV 机壳，端盖 = UEV 机壳
 * - 能源仓位 **9** 个（结构 B 位）：GTCEu 同档 ≥2 仓解锁 tier+1 —— 9 个 UEV 仓可吃到 UIV 级超频
 * - 配方：144 mB 液态氢 → 1 氢锭（{@link #HYDROGEN_MB_PER_INGOT}），EUt = {@link #PRESS_EUT}
 *
 * 数值全部集中在下方"数值调参"常量（配方注册在 GtnecoreAddon，引用同一批常量）。
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WignerHuntingtonAnvilMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            WignerHuntingtonAnvilMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    // ============ 数值调参 ============
    /** 单锭耗液氢量（mB）：GT 流体单位 1 锭 = 144 mB，即液态氢与金属氢 1:1 */
    public static final int HYDROGEN_MB_PER_INGOT = 144;
    /** 单次压制时长（tick）：200t = 10 秒（用户 2026-09-12 定） */
    public static final int PRESS_DURATION_TICKS = 200;
    /** 配方电压：UEV（V[UEV] = 8,388,608 EU/t，用户 2026-09-12 定） */
    public static final long PRESS_EUT = GTValues.V[GTValues.UEV];
    /** 能源仓上限（= 结构内 B 位数，用户 2026-09-12 定：最多 9 个） */
    public static final int MAX_ENERGY_HATCHES = 9;
    /** 内置并行上限（用户 2026-09-12：自带 16 并行） */
    public static final int BUILTIN_PARALLELS = 16;

    /**
     * 内置 16 并行 modifier。
     *
     * 按**实际可容纳的输入/输出**取并行数（GT 并行语义：料不够就少跑几路，不空转），
     * 输入/输出 ×并行数，**duration 与 EUt 保持不变**——即"并行不放大功耗"
     * （用户 2026-09-12 明确：这个并行不会 16 倍功耗；GT 并行仓那种"并行越多越费电"不适用本机）。
     * 注册时 alwaysTryModify=true：并行数依赖当前库存，每次匹配都要重算。
     */
    public static final com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier BUILTIN_PARALLEL = (machine, recipe) -> {
        // 允许裁剪（用户 2026-09-12）：料够就跑满 16 路，不够就按实际能支撑的路数跑（最少 1 路）。
        // 主判定走 GT 自己的 ParallelLogic.getParallelAmount（输入可支撑数 ∩ 输出可容纳数）。
        // 兜底：它在**创造类输入仓**上会被读成"只够 1 路"（该类 handler 上报存量的形式特殊），
        // 此时退回"只按输出空间裁剪"，避免因为输入判定失灵而白瞎整个并行能力。
        int byInput = BUILTIN_PARALLELS;
        int parallel = BUILTIN_PARALLELS;
        if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine rlm) {
            byInput = com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic
                    .getParallelAmount(machine, recipe, BUILTIN_PARALLELS);
            parallel = byInput;
            if (parallel <= 1) {
                parallel = Math.max(1, com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic
                        .limitByOutputMerging(rlm, recipe, BUILTIN_PARALLELS,
                                cap -> true, java.util.Collections.emptyList()));
            }
        }
        logParallel(machine, byInput, parallel);
        if (parallel <= 1) {
            return com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction.IDENTITY;
        }
        return com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction.builder()
                .inputModifier(com.gregtechceu.gtceu.api.recipe.content.ContentModifier.multiplier(parallel))
                .outputModifier(com.gregtechceu.gtceu.api.recipe.content.ContentModifier.multiplier(parallel))
                .parallels(parallel)
                .build();
    };

    /** 并行诊断：只在数值变化时打一条，把"输入判定 / 最终采用"都打出来（值变了才打，不刷屏）。 */
    private static int lastLoggedParallel = -1;

    private static void logParallel(com.gregtechceu.gtceu.api.machine.MetaMachine machine,
                                    int byInput, int parallel) {
        if (parallel == lastLoggedParallel) return;
        lastLoggedParallel = parallel;
        LOGGER.info("GTNEcore: 对顶砧[{}] 内置并行 —— 输入判定能跑 {} 路，最终采用 {} 路（上限 {}）",
                machine.getPos(), byInput, parallel, BUILTIN_PARALLELS);
    }

    /**
     * 本机配方 modifier 链：**默认超频（OC_NON_PERFECT）+ 内置 16 并行**。
     *
     * ⚠ `MachineBuilder.recipeModifier(...)` 是**覆盖**默认列表的（构造器默认 `[OC_NON_PERFECT]`），
     * 只传并行会把超频顶掉——所以这里必须把 OC 一起写进链里（火箭发射台当年同理，
     * 它的 PAD_OC 自带了超频数学）。
     */
    public static final com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier MODIFIERS =
            new com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList(
                    com.gregtechceu.gtceu.common.data.GTRecipeModifiers.OC_NON_PERFECT, BUILTIN_PARALLEL);

    public WignerHuntingtonAnvilMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** 控制器面板：标准电动多方块信息（发电/耗电/档位）之上追加压制说明与进度 */
    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed()) return;
        textList.add(Component.translatable("gtnecore.machine.wigner_huntington_anvil.press",
                HYDROGEN_MB_PER_INGOT, GTValues.VNF[GTValues.UEV]));
        var logic = getRecipeLogic();
        if (logic != null && logic.isWorking()) {
            textList.add(Component.translatable("gtnecore.machine.wigner_huntington_anvil.working",
                    logic.getProgress(), logic.getMaxProgress(),
                    String.format("%.0f%%", logic.getProgressPercent() * 100.0F)));
        } else {
            textList.add(Component.translatable("gtnecore.machine.wigner_huntington_anvil.idle"));
        }
    }

    // ==================================================================================
    // 结构幽灵：成型后把钻石晶格（D）替换为 StructureGhostBlock，交由客户端 glTF 动态渲染
    // 呈现两个对撞砧（用户 2026-09-12 二阶段）。机制与摄取仪顶环同款：
    // StructureTransparencyHook 登记"隐藏位 → 原方块"，成型重检按原方块判定（结构不解体），
    // 解体时还原。位置由 PATTERN 数据算出，不落 NBT（重载后按方块类型自愈）。
    // ==================================================================================

    /** 已隐藏的朝向档（0..3，见 {@link #rotateOffset}）；-1 = 未隐藏。@DescSynced 供客户端渲染摆放。 */
    @Persisted
    @DescSynced
    private int ghostRotation = -1;

    private boolean ghostHideQueued;
    private boolean ghostRestoreQueued;
    private int ghostCheckTimer;

    /** 隐藏位朝向档（0..3）；-1 = 未隐藏。客户端动态渲染据此把模型摆到正确的一侧。 */
    public int getGhostRotation() {
        return ghostRotation;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.ghostHideQueued = true;      // 延迟到主线程 tick（成型回调可能不在主线程）
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.ghostRestoreQueued = true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            self().subscribeServerTick(this::ghostTick);
        }
    }

    /**
     * 控制器被砸掉时立刻还原幽灵位：此时机器实体被移除、tick 不会再跑，
     * 若不在这里处理就会留下**无法破坏也无法清除**的幽灵占位（用户实测踩到）。
     * 区分两种 onUnload：区块卸载（方块还在自己）→ 不动，留给重载自愈；方块已不是自己 → 还原。
     * 世界修改延到下一 server tick，避开方块移除过程中的嵌套 setBlock。
     */
    @Override
    public void onUnload() {
        Level level = getLevel();
        if (level != null && !level.isClientSide && ghostRotation >= 0
                && level.getBlockState(getPos()).getBlock() != getDefinition().getBlock()) {
            Block diamond = diamondBlock();
            var server = level.getServer();
            if (diamond != null && server != null) {
                List<BlockPos> targets = ghostRestoreTargets(level, diamond, StructureTransparencyHook.ghostBlock());
                ghostRotation = -1;
                if (!targets.isEmpty()) {
                    server.execute(() -> applyRestore(level, diamond, targets));
                }
            }
        }
        super.onUnload();
    }

    private void ghostTick() {
        if (ghostHideQueued) {
            ghostHideQueued = false;
            performGhostHide();
        }
        if (ghostRestoreQueued) {
            ghostRestoreQueued = false;
            performGhostRestore();
        }
        // 100t 兜底（照抄摄取仪的自愈保险）：
        //   成型期 → 复查隐藏（世界重载 / 占位被外力清掉自愈）
        //   解体期 → 重试还原（还原时机被错过、异常路径漏掉）
        if (++ghostCheckTimer >= 100) {
            ghostCheckTimer = 0;
            if (isFormed()) {
                performGhostHide();
            } else {
                performGhostRestore();
            }
        }
    }

    /** 图案系 (dx, dz) 按朝向档旋转（与摄取仪 RING_CELLS 同款 90° 递增）。 */
    public static int[] rotateOffset(int dx, int dz, int rot) {
        return switch (rot & 3) {
            case 1 -> new int[] { dz, -dx };
            case 2 -> new int[] { -dx, -dz };
            case 3 -> new int[] { -dz, dx };
            default -> new int[] { dx, dz };
        };
    }

    /** 取某个图案系偏移在给定朝向档下的世界坐标。 */
    private BlockPos worldPos(int[] offset, int rot) {
        int[] r = rotateOffset(offset[0], offset[2], rot);
        return getPos().offset(r[0], offset[1], r[1]);
    }

    /**
     * 识别本结构实际使用的朝向档：四种水平旋转里按"命中晶格块或幽灵块"的数量打分取最高。
     * 结构在水平面不对称 → 正确朝向接近全中、其它朝向接近 0。
     *
     * @return 0..3；命中数不足 1/8（缺 avaritia / 还没搭 / 已全部还原）时返回 -1
     */
    private int detectRotation(Level level, Block diamond, Block ghost) {
        int best = -1, bestScore = 0;
        for (int rot = 0; rot < 4; rot++) {
            int score = 0;
            for (int[] off : DIAMOND_OFFSETS) {
                BlockState state = level.getBlockState(worldPos(off, rot));
                if (state.is(diamond) || (ghost != null && state.is(ghost))) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = rot;
            }
        }
        return bestScore * 8 >= DIAMOND_OFFSETS.length ? best : -1;
    }

    /** 主线程：登记幽灵表 + 把仍是晶格的隐藏位替换成幽灵占位。 */
    private void performGhostHide() {
        Level level = getLevel();
        if (level == null || level.isClientSide || !isFormed()) return;
        Block diamond = diamondBlock();
        Block ghost = StructureTransparencyHook.ghostBlock();
        if (diamond == null || ghost == null || DIAMOND_OFFSETS.length == 0) return;
        int rot = detectRotation(level, diamond, ghost);
        if (rot < 0) {
            ghostRotation = -1;
            return;
        }
        ghostRotation = rot;
        BlockState diamondState = diamond.defaultBlockState();
        BlockState ghostState = ghost.defaultBlockState();
        Map<BlockPos, BlockState> register = new LinkedHashMap<>();
        List<BlockPos> toHide = new ArrayList<>();
        for (int[] off : DIAMOND_OFFSETS) {
            BlockPos pos = worldPos(off, rot);
            BlockState state = level.getBlockState(pos);
            if (state.is(diamond)) {
                register.put(pos, diamondState);
                toHide.add(pos);
            } else if (StructureTransparencyHook.isGhostState(state)) {
                register.put(pos, diamondState);   // 重载后自愈：已是幽灵块也要登记（成型重检用）
            }
        }
        if (!register.isEmpty()) StructureTransparencyHook.addAll(level, register);   // 先登记后替换
        for (BlockPos pos : toHide) {
            level.setBlock(pos, ghostState, Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
            level.sendBlockUpdated(pos, diamondState, ghostState,
                    Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        }
    }

    /** 主线程：解体还原——把仍是幽灵占位的隐藏位换回钻石晶格。 */
    private void performGhostRestore() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        Block diamond = diamondBlock();
        Block ghost = StructureTransparencyHook.ghostBlock();
        if (diamond == null || ghost == null) {
            ghostRotation = -1;
            return;
        }
        List<BlockPos> targets = ghostRestoreTargets(level, diamond, ghost);
        ghostRotation = -1;
        applyRestore(level, diamond, targets);
    }

    /** 收集当前仍是幽灵占位的隐藏位（朝向档优先用已记录的，缺失时按幽灵块识别）。 */
    private List<BlockPos> ghostRestoreTargets(Level level, Block diamond, Block ghost) {
        int rot = ghostRotation >= 0 ? ghostRotation : detectRotation(level, diamond, ghost);
        if (rot < 0) return List.of();
        List<BlockPos> targets = new ArrayList<>();
        for (int[] off : DIAMOND_OFFSETS) {
            BlockPos pos = worldPos(off, rot);
            if (StructureTransparencyHook.isGhostState(level.getBlockState(pos))) targets.add(pos);
        }
        return targets;
    }

    /** 解除幽灵登记并把占位还原成钻石晶格。 */
    private static void applyRestore(Level level, Block diamond, List<BlockPos> targets) {
        if (targets.isEmpty()) return;
        StructureTransparencyHook.removeAll(level, targets);
        BlockState diamondState = diamond.defaultBlockState();
        for (BlockPos pos : targets) {
            BlockState current = level.getBlockState(pos);
            level.setBlock(pos, diamondState, Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
            level.sendBlockUpdated(pos, current, diamondState,
                    Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        }
    }

    /**
     * 幽灵占位也算合法砧体块（用户 2026-09-12）：占位残留且无法清除时，塔仍能重新成型，
     * 成型后机器的隐藏/还原逻辑会把它们规范化回钻石晶格。结构谓词用（惰性取幽灵块，
     * 注册期不可 get）。
     */
    private static com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate ghostOrDiamond() {
        return Predicates.custom(
                state -> {
                    Block ghost = StructureTransparencyHook.ghostBlock();
                    return ghost != null && state.getBlockState().is(ghost);
                },
                () -> {
                    Block ghost = StructureTransparencyHook.ghostBlock();
                    return ghost == null
                            ? new com.lowdragmc.lowdraglib.utils.BlockInfo[] {}
                            : new com.lowdragmc.lowdraglib.utils.BlockInfo[] {
                                    new com.lowdragmc.lowdraglib.utils.BlockInfo(ghost.defaultBlockState()) };
                });
    }

    /** avaritia 钻石晶格块（懒解析缓存；缺 avaritia 时 null → 幽灵机制整体跳过）。 */
    private static Block DIAMOND_BLOCK;
    private static boolean diamondResolved;

    private static Block diamondBlock() {
        if (!diamondResolved) {
            DIAMOND_BLOCK = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                    new net.minecraft.resources.ResourceLocation("avaritia", "diamond_lattice_block"));
            diamondResolved = true;
        }
        return DIAMOND_BLOCK;
    }

    /**
     * 结构数据：13 层（Z）× 26 行（Y，下→上）× 13 字符（X）。
     * 逐字来自 {@code kjs版多方块结构/维格纳-亨廷顿对顶砧.js}——
     * {@link #createPattern} 建模式与 {@link #DIAMOND_OFFSETS}（幽灵隐藏位）读同一份数据，改结构只改这里。
     */
    public static final String[][] PATTERN = {
            { "####AAAAA####", "####AAAAA####", "####ABBBA####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####AAAAA####", "####AAAAA####", "####AAAAA####" },
            { "##AAAAAAAAA##", "##AAAAAAAAA##", "##AAAAAAAAA##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##AAAAAAAAA##", "##AAAAAAAAA##", "##AAAAAAAAA##" },
            { "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#C##DDDDD##C#", "#C##DDDDD##C#", "#C##DDDDD##C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C##DDDDD##C#", "#C##DDDDD##C#", "#C##DDDDD##C#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#" },
            { "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#C#DDDDDDD#C#", "#C#DDDDDDD#C#", "#C#DDDDDDD#C#", "#C##DDDDD##C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C##DDDDD##C#", "#C#DDDDDDD#C#", "#C#DDDDDDD#C#", "#C#DDDDDDD#C#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#" },
            { "AAAAAAAAAAAAA", "AAAAAAAAAAAAA", "AAAAEAAAEAAAA", "C#DDEDDDEDD#C", "C#DDEDDDEDD#C", "C#DDEDDDEDD#C", "C##DEDDDED##C", "C###EDDDE###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###EDDDE###C", "C##DEDDDED##C", "C#DDEDDDEDD#C", "C#DDEDDDEDD#C", "C#DDEDDDEDD#C", "AAAAEAAAEAAAA", "AAAAAAAAAAAAA", "AAAAAAAAAAAAA" },
            { "AAAAAAAAAAAAA", "AAAAAAAAAAAAA", "BAAAAAAAAAAAB", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C##DDDDDDD##C", "C###DDDDD###C", "C####DDD####C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C####DDD####C", "C###DDDDD###C", "C##DDDDDDD##C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "AAAAAAAAAAAAA", "AAAAAAAAAAAAA", "AAAAAAAAAAAAA" },
            { "AAAAAAAAAAAAA", "AAAAAAAAAAAAA", "BAAAAAAAAAAAB", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C##DDDDDDD##C", "C###DDDDD###C", "C####DDD####C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C####DDD####C", "C###DDDDD###C", "C##DDDDDDD##C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "AAAAAAAAAAAAA", "AAAAAAAAAAAAA", "AAAAAAAAAAAAA" },
            { "AAAAAAAAAAAAA", "AAAAAAAAAAAAA", "BAAAAAAAAAAAB", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C##DDDDDDD##C", "C###DDDDD###C", "C####DDD####C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C###########C", "C####DDD####C", "C###DDDDD###C", "C##DDDDDDD##C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "C#DDDDDDDDD#C", "AAAAAAAAAAAAA", "AAAAAAAAAAAAA", "AAAAAAAAAAAAA" },
            { "AAAAAAAAAAAAA", "AAAAAAAAAAAAA", "AAAAEAAAEAAAA", "C#DDEDDDEDD#C", "C#DDEDDDEDD#C", "C#DDEDDDEDD#C", "C##DEDDDED##C", "C###EDDDE###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###E###E###C", "C###EDDDE###C", "C##DEDDDED##C", "C#DDEDDDEDD#C", "C#DDEDDDEDD#C", "C#DDEDDDEDD#C", "AAAAEAAAEAAAA", "AAAAAAAAAAAAA", "AAAAAAAAAAAAA" },
            { "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#C#DDDDDDD#C#", "#C#DDDDDDD#C#", "#C#DDDDDDD#C#", "#C##DDDDD##C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C##DDDDD##C#", "#C#DDDDDDD#C#", "#C#DDDDDDD#C#", "#C#DDDDDDD#C#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#" },
            { "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#C##DDDDD##C#", "#C##DDDDD##C#", "#C##DDDDD##C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C#########C#", "#C##DDDDD##C#", "#C##DDDDD##C#", "#C##DDDDD##C#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#", "#AAAAAAAAAAA#" },
            { "##AAAAAAAAA##", "##AAAAAAAAA##", "##AAAAAAAAA##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##CC#####CC##", "##AAAAAAAAA##", "##AAAAAAAAA##", "##AAAAAAAAA##" },
            { "####AAAAA####", "####AAAAA####", "####AFGIA####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####AAHAA####", "####AAAAA####", "####AAAAA####" },
    };

    /** 控制器（G）在结构数据中的坐标（由 {@link #computeDiamondOffsets} 校验唯一性）。 */
    private static final int[] CONTROLLER_AT = findController();

    /** 钻石晶格（D）相对控制器的偏移 {dx, dy, dz}（图案坐标系；成型时按朝向旋到世界）。 */
    private static final int[][] DIAMOND_OFFSETS = computeDiamondOffsets();

    /** 在 {@link #PATTERN} 中定位控制器（G）；缺失/重复直接抛（静态数据写错要立刻炸出来）。 */
    private static int[] findController() {
        int[] found = null;
        for (int z = 0; z < PATTERN.length; z++) {
            for (int y = 0; y < PATTERN[z].length; y++) {
                String row = PATTERN[z][y];
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) != 'G') continue;
                    if (found != null) {
                        throw new IllegalStateException("WignerHuntingtonAnvil: PATTERN 里出现多个控制器 G");
                    }
                    found = new int[] { x, y, z };
                }
            }
        }
        if (found == null) {
            throw new IllegalStateException("WignerHuntingtonAnvil: PATTERN 里没有控制器 G");
        }
        return found;
    }

    /** 收集钻石晶格（D）相对控制器的偏移 {dx, dy, dz}（图案坐标系）。 */
    private static int[][] computeDiamondOffsets() {
        List<int[]> list = new ArrayList<>();
        for (int z = 0; z < PATTERN.length; z++) {
            for (int y = 0; y < PATTERN[z].length; y++) {
                String row = PATTERN[z][y];
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) == 'D') {
                        list.add(new int[] { x - CONTROLLER_AT[0], y - CONTROLLER_AT[1], z - CONTROLLER_AT[2] });
                    }
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    /** 砧体（钻石晶格）在图案坐标系里的包围盒 {minX,maxX,minY,maxY,minZ,maxZ}（客户端摆放模型用）。 */
    public static int[] diamondBounds() {
        return DIAMOND_BOUNDS;
    }

    private static final int[] DIAMOND_BOUNDS = computeDiamondBounds();

    private static int[] computeDiamondBounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] off : DIAMOND_OFFSETS) {
            minX = Math.min(minX, off[0]); maxX = Math.max(maxX, off[0]);
            minY = Math.min(minY, off[1]); maxY = Math.max(maxY, off[1]);
            minZ = Math.min(minZ, off[2]); maxZ = Math.max(maxZ, off[2]);
        }
        return new int[] { minX, maxX, minY, maxY, minZ, maxZ };
    }

    /**
     * 结构模式：13(X) × 26(Y) × 13(Z)，数据来自 {@link #PATTERN}（逐字来自 .js 导出）。
     * 轴向：每个字符串 = 一层 Y（下→上），每次 {@code .aisle()} = 沿 Z 推进，字符 = X。
     *
     * 字母表（与 .js 同名同义；.js 里的"蛙明灯"是控制器占位，Java 侧换成控制器本体）：
     *  A = UEV 机壳（上下端盖）· B = 能源仓（9 位：z=0 端面 ×3 + x=0/x=12 侧 ×6，1..9 个）
     *  C = 聚变玻璃（外壳）· D = avaritia 钻石晶格块（砧体）· E = UHV 机壳（中段环）
     *  G = 控制器（z=12 端面正中，朝向轴两端留空 → 正面 overlay 朝外可见）
     *  F / I = **物品输出总线 / 流体输入仓（两位可对调）** · H = 维护仓（高位）· # = 任意
     */
    public static BlockPattern createPattern(Block controller) {
        var builder = FactoryBlockPattern.start();
        for (String[] aisle : PATTERN) {
            builder.aisle(aisle);
        }
        return builder
                .where('G', Predicates.controller(Predicates.blocks(controller)))
                .where('D', AntigravityEngineMachine.blockPredicate("avaritia", "diamond_lattice_block")
                        .or(ghostOrDiamond()))   // 幽灵占位也算合法砧体块（自愈/清理）
                .where('A', Predicates.blocks(GTBlocks.MACHINE_CASING_UEV.get()))
                .where('E', Predicates.blocks(GTBlocks.MACHINE_CASING_UHV.get()))
                .where('C', Predicates.blocks(GTBlocks.FUSION_GLASS.get()))
                .where('H', Predicates.abilities(PartAbility.MAINTENANCE))
                // B 位：能源仓 1..9 个（最多 9，用户 2026-09-12 定）；空位可用 UEV 机壳顶替
                // （本 mod 惯例：观察站/发射台的能源仓位都带 .or(机壳) 备选）
                .where('B', Predicates.abilities(PartAbility.INPUT_ENERGY)
                        .setMinGlobalLimited(1).setMaxGlobalLimited(MAX_ENERGY_HATCHES)
                        .or(Predicates.blocks(GTBlocks.MACHINE_CASING_UEV.get())))
                // F / I 位（端面控制器两侧）：物品输出总线与流体输入仓**可对调**（用户 2026-09-12）——
                // 两个位置都接受 EXPORT_ITEMS 或 IMPORT_FLUIDS，随便怎么摆
                .where('F', Predicates.abilities(PartAbility.EXPORT_ITEMS, PartAbility.IMPORT_FLUIDS))
                .where('I', Predicates.abilities(PartAbility.EXPORT_ITEMS, PartAbility.IMPORT_FLUIDS))
                .where('#', Predicates.any())
                .build();
    }
}
