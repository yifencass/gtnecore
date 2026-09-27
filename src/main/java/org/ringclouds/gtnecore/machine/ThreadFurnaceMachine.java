package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;
import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.*;

/**
 * 线程熔炉（Thread Furnace）—— 线程机制测试机（阶段 1）。
 *
 * 结构：3×3×3 全机壳立方，控制器在前壁中央。任意机壳位可替换为：
 * 物品输入/输出仓、能源输入仓、维护仓、并行仓（autoAbilities），或线程仓（THREAD_ABILITY）。
 *
 * 机制：ThreadRecipeLogic 让机器同时运行 T 种配方（T = Σ线程仓线程数）。
 * 线程三品位决定每线程并行（配方放大倍数 q，经 THREAD_PARALLEL 修饰器施加于全部槽）：
 * - 线程仓：与并行仓互斥（有并行仓时 P 作废），q=1 → 总吞吐 T
 * - 均分线程仓：q = max(1, P/T) → 总吞吐 ≈ P
 * - 倍频线程仓：q = P → 总吞吐 T×P
 */
public class ThreadFurnaceMachine extends WorkableElectricMultiblockMachine {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** 线程修饰器：把当前每线程并行 q 放大到配方上（施加给主槽 + 全部附加槽） */
    public static final RecipeModifier THREAD_PARALLEL = ThreadFurnaceMachine::threadParallel;

    private static ModifierFunction threadParallel(MetaMachine machine, GTRecipe recipe) {
        if (machine instanceof ThreadFurnaceMachine tm && tm.isFormed()) {
            int q = tm.computeThreadParallel();
            if (q <= 1) return ModifierFunction.IDENTITY;
            return ModifierFunction.builder()
                    .modifyAllContents(ContentModifier.multiplier(q))
                    .eutMultiplier(q)
                    .parallels(q)
                    .build();
        }
        return ModifierFunction.IDENTITY;
    }

    public ThreadFurnaceMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    /** 已装线程仓（多仓叠加） */
    public List<IThreadHatch> getThreadHatches() {
        List<IThreadHatch> list = new ArrayList<>();
        for (var part : getParts()) {
            if (part instanceof IThreadHatch hatch) {
                list.add(hatch);
            }
        }
        return list;
    }

    /** 线程总数 T（Σ各仓当前线程数；无线程仓 = 0） */
    public int getThreadCount() {
        int t = 0;
        for (var hatch : getThreadHatches()) {
            t += hatch.getThreads();
        }
        return t;
    }

    /**
     * 品位判定与每线程并行 q：
     * 品位取机器内最高级（MULT > SPLIT > NORMAL）；
     * 有 NORMAL 线程仓时并行仓作废（互斥语义）；无并行仓时 SPLIT/MULT 退化为 q=1。
     */
    public int computeThreadParallel() {
        int t = getThreadCount();
        if (t <= 0) return 1;
        boolean hasNormal = false;
        boolean hasSplit = false;
        boolean hasMult = false;
        for (var hatch : getThreadHatches()) {
            switch (hatch.getThreadGrade()) {
                case NORMAL -> hasNormal = true;
                case SPLIT -> hasSplit = true;
                case MULT -> hasMult = true;
            }
        }
        int p = getParallelHatch().map(IParallelHatch::getCurrentParallel).orElse(0);
        if (hasNormal) p = 0; // 线程仓与并行仓相斥
        ThreadGrade grade = hasMult ? ThreadGrade.MULT : hasSplit ? ThreadGrade.SPLIT : ThreadGrade.NORMAL;
        return switch (grade) {
            case NORMAL -> 1;
            case SPLIT -> p <= 0 ? 1 : Math.max(1, p / t);
            case MULT -> p <= 0 ? 1 : p;
        };
    }

    /** 当前活动槽数（主槽 + 附加槽中正在跑的）—— 文本 UI 用 */
    public int getActiveSlotCount() {
        if (!isFormed()) return 0;
        var logic = getRecipeLogic();
        int active = logic.isWorking() && logic.getLastRecipe() != null ? 1 : 0;
        if (logic instanceof ThreadRecipeLogic threadLogic) {
            active += threadLogic.getActiveExtraSlots();
        }
        return active;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new ThreadRecipeLogic(this);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed()) return;
        int t = getThreadCount();
        int q = computeThreadParallel();
        if (t > 0) {
            textList.add(Component.translatable("gtnecore.machine.thread_furnace.threads",
                    t, q).withStyle(ChatFormatting.AQUA));
            textList.add(Component.translatable("gtnecore.machine.thread_furnace.active",
                    getActiveSlotCount(), t).withStyle(ChatFormatting.GREEN));
        }
    }

    //////////////////////////////////////
    // ****** 结构 ******//
    //////////////////////////////////////

    @Override
    public BlockPattern getPattern() {
        return createPattern(this.getDefinition().getBlock(), this.getDefinition().getRecipeTypes());
    }

    /** 3×3×3 立方：前壁中央控制器，其余 26 位机壳（可换部件/线程仓/并行仓/IO/维护） */
    public static BlockPattern createPattern(net.minecraft.world.level.block.Block controllerBlock,
                                             com.gregtechceu.gtceu.api.recipe.GTRecipeType[] recipeTypes) {
        return FactoryBlockPattern.start(LEFT, UP, FRONT)
                .aisle("CCC", "CCC", "CCC")
                .aisle("CCC", "CCC", "CCC")
                .aisle("CSC", "CCC", "CCC")
                .where('S', controller(blocks(controllerBlock)))
                .where('C', blocks(com.gregtechceu.gtceu.common.data.GTBlocks.CASING_STEEL_SOLID.get())
                        .or(Predicates.autoAbilities(recipeTypes))
                        .or(abilities(IThreadHatch.THREAD_ABILITY))
                        // 维护/并行可选（不强制）：不用 autoAbilities(true,false,true)——
                        // 其维护分支 min = ConfigHolder.machines.enableMaintenance（默认 true），
                        // 没放维护仓会整机永不成立
                        .or(abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.MAINTENANCE)
                                .setMaxGlobalLimited(1))
                        .or(abilities(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.PARALLEL_HATCH)
                                .setMaxGlobalLimited(1)))
                .build();
    }

    //////////////////////////////////////
    // ****** ThreadRecipeLogic ******//
    //////////////////////////////////////

    /**
     * 多线程配方逻辑：槽 0 = 父类原状态（status/progress/duration/lastRecipe 全 @Persisted，
     * UI/JEI/存档完全兼容）；附加槽（1..T-1）为内存态，各自独立 搜索→消耗→推进→产出。
     *
     * 附加槽进度不持久化（v1 接受：重启丢附加槽进度）；配方放大经机器 THREAD_PARALLEL
     * 修饰器自动施加（主槽与附加槽同一 fullModifyRecipe 路径，不会双倍放大）。
     */
    public static class ThreadRecipeLogic extends RecipeLogic {

        private static final class Slot {
            @Nullable GTRecipe recipe; // 放大后的配方（已消耗输入）
            int progress;
            int duration;
        }

        private final List<Slot> slots = new ArrayList<>();

        public ThreadRecipeLogic(ThreadFurnaceMachine machine) {
            super(machine);
        }

        /** 附加槽中正在推进的个数（文本 UI） */
        public int getActiveExtraSlots() {
            int n = 0;
            for (var s : slots) {
                if (s.recipe != null && s.progress < s.duration) n++;
            }
            return n;
        }

        /** 按当前线程数调整附加槽数量（成型/拆仓/调线程数后变化） */
        private void syncSlots() {
            ThreadFurnaceMachine machine = (ThreadFurnaceMachine) this.machine;
            int target = Math.max(0, machine.getThreadCount() - 1);
            while (slots.size() < target) {
                slots.add(new Slot());
            }
            while (slots.size() > target) {
                slots.remove(slots.size() - 1); // 仓被拆：直接丢弃该槽（进度不保留）
            }
        }

        @Override
        public void serverTick() {
            // 槽 0（主槽）：父类全权（含订阅/退订/状态清理/断电 SUSPEND）
            super.serverTick();
            if (isSuspend()) return; // 整机断电：附加槽同停
            syncSlots();
            if (slots.isEmpty()) return;

            long off = getMachine().getOffsetTimer();
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot.recipe == null) {
                    // 错开搜索相位，避免所有槽同一 tick 抢输入
                    if (off % 5 == (i + 1) % 5) {
                        tryFindRecipe(slot);
                    }
                    continue;
                }
                if (slot.progress < slot.duration) {
                    var condition = com.gregtechceu.gtceu.api.recipe.RecipeHelper
                            .checkConditions(slot.recipe, this);
                    if (condition.isSuccess()) {
                        var handle = handleTickRecipe(slot.recipe); // 每 tick EU + tick IO
                        if (handle.isSuccess()) {
                            slot.progress++;
                        }
                        // 失败（EU 不足等）：保持进度等电；机器 5 连败会由主槽整机 SUSPEND
                    } else {
                        // 条件失败（如环境丢失）：仿父类 regressRecipe 语义
                        slot.progress = Math.min(slot.progress, 1);
                    }
                } else {
                    // 完成：产出 → 清槽（下一 tick 重新搜索）
                    machine.afterWorking();
                    handleRecipeIO(slot.recipe, IO.OUT);
                    slot.recipe = null;
                    slot.progress = 0;
                    slot.duration = 0;
                }
            }
        }

        /** 附加槽找配方：搜索 → 机器修饰（含线程放大）→ 校验+消耗输入 → 上槽 */
        private void tryFindRecipe(Slot slot) {
            var it = searchRecipe();
            while (it.hasNext()) {
                var match = it.next();
                var modified = machine.fullModifyRecipe(match); // THREAD_PARALLEL 在此放大
                if (modified == null) continue;
                if (!checkRecipe(modified).isSuccess()) continue; // 输入/条件不足
                var consumed = handleRecipeIO(modified, IO.IN);
                if (!consumed.isSuccess()) continue; // 输入被其它槽/主槽抢走
                slot.recipe = modified;
                slot.progress = 0;
                slot.duration = modified.duration;
                return;
            }
        }
    }
}
