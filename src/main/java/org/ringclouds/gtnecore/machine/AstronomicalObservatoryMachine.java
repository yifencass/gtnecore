package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CleanroomMachine;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.ringclouds.gtnecore.Gtnecore;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Set;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.*;

/**
 * 天文观测站（Astronomical Observatory）：捏他超净间的环境型多方块。
 *
 * - 固定 9×5×9 结构（KJS 示意转写，见 docs/超净间机制与天文研究室设计.md）：
 *   塑料混凝土墙体 + 超净玻璃（可通行仓/幻影门≤7）+ 天文辅镜幕墙 + 后墙中段控制器
 * - 控制器 RotationState.NONE，不可旋转不可翻转
 * - 校准机制 = 超净间清洁度：成型后耗电校准（每周期 VA[tier]，档位越高越快），
 *   校准度 ≥95 进入低功耗模式（3/16 V[tier]），期间向内部机器提供 ASTRONOMICAL 环境
 * - 无线供电：能源仓（1..8）的 EU 自动推给内部接收机器（每台每 tick 上限 2×VA[tier]）
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AstronomicalObservatoryMachine extends CleanroomMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AstronomicalObservatoryMachine.class, CleanroomMachine.MANAGED_FIELD_HOLDER);

    /** 天文观测环境类型：构造即自注册，配方用 .cleanroom(...) / "astronomical_observation" 引用 */
    public static final CleanroomType ASTRONOMICAL =
            new CleanroomType("astronomical_observation", "gtnecore.recipe.astronomical.display_name");

    /** 校准周期（tick/次）：每完成一周期校准度 +（2 + 3×(档位-LV+1)） */
    public static final int CALIBRATION_CYCLE = 200;

    /** 无线供电单机上限系数：每台内部机器每 tick 最多 2×VA[档] EU */
    public static final long WIRELESS_PER_MACHINE_AMPS = 2;

    public AstronomicalObservatoryMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new AstronomicalLogic(this);
    }

    @Override
    public AstronomicalLogic getRecipeLogic() {
        return (AstronomicalLogic) super.getRecipeLogic();
    }

    //////////////////////////////////////
    // ****** 环境（捏他超净间）******//
    //////////////////////////////////////

    /** 不走顶棚过滤器：固定提供 ASTRONOMICAL（isClean 由校准度驱动，见 CleanroomLogic） */
    @Override
    public Set<CleanroomType> getTypes() {
        return Set.of(ASTRONOMICAL);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 固定校准周期（超净间按体积缩放，我们固定）
        getRecipeLogic().setDuration(CALIBRATION_CYCLE);
    }

    //////////////////////////////////////
    // ****** 结构 ******//
    //////////////////////////////////////

    @Override
    public BlockPattern getPattern() {
        return createPattern(this.getDefinition().getBlock());
    }

    /** 固定 pattern（builder 的 .pattern() 也用一个工厂，JEI/自动搭建兜底）。
     *  轴语义（start 参数名 charDir/stringDir/aisleDir）：start(LEFT, UP, FRONT) =
     *  KJS 无参默认——字符串方向为上（每 aisle 5 行 = 5 格高），aisle 沿前后进给
     *  （9 个 = 9 格深），字符沿左右（9 宽）。整体 9×9×5：控制器在顶面中心、
     *  辅镜铺顶棚环带（控制器周围 3×3 为 plascrete）。 */
    public static BlockPattern createPattern(Block controller) {
        return FactoryBlockPattern.start(LEFT, UP, FRONT)
                // 切片 1（前）：地面能源仓位（B）+ 玻璃墙 + plascrete 顶
                .aisle("ABBBBBBBA", "ACCCCCCCA", "ACCCCCCCA", "ACCCCCCCA", "AAAAAAAAA")
                // 中间切片×7：plascrete 地/墙 + 玻璃墙 + 内部空腔 + 辅镜顶棚
                .aisle("BAAAAAAAB", "C#######C", "C#######C", "C#######C", "ADDDDDDDA")
                .aisle("BAAAAAAAB", "C#######C", "C#######C", "C#######C", "ADDDDDDDA")
                .aisle("BAAAAAAAB", "C#######C", "C#######C", "C#######C", "ADDAAADDA")
                // 中央切片：控制器（E）在顶面中心，四周 plascrete（3×3 顶面环）
                .aisle("BAAAAAAAB", "C#######C", "C#######C", "C#######C", "ADDAEADDA")
                .aisle("BAAAAAAAB", "C#######C", "C#######C", "C#######C", "ADDAAADDA")
                .aisle("BAAAAAAAB", "C#######C", "C#######C", "C#######C", "ADDDDDDDA")
                .aisle("BAAAAAAAB", "C#######C", "C#######C", "C#######C", "ADDDDDDDA")
                // 切片 9（后）：地面维护仓（F）+ 能源仓位 + 玻璃墙（两孔）+ plascrete 顶
                .aisle("AFBBBBBBA", "ACCC#CCCA", "ACCC#CCCA", "ACCCCCCCA", "AAAAAAAAA")
                .where('A', gtBlockPredicate("plascrete"))
                .where('B', Predicates.abilities(PartAbility.INPUT_ENERGY)
                        .setMinGlobalLimited(1).setMaxGlobalLimited(8)
                        .or(gtBlockPredicate("plascrete")))
                .where('C', gtBlockPredicate("cleanroom_glass")
                        .or(Predicates.abilities(PartAbility.PASSTHROUGH_HATCH))
                        .or(goetyApparitionDoor().setMaxGlobalLimited(7)))
                .where('D', gtBlockPredicate("astronomical_finderscope"))
                .where('E', Predicates.controller(Predicates.blocks(controller)))
                .where('F', Predicates.abilities(PartAbility.MAINTENANCE)
                        .setMinGlobalLimited(1)
                        .or(gtBlockPredicate("plascrete")))
                .where('#', innerReceiversPredicate())
                .build();
    }

    /** gtceu 方块运行时惰性解析（机器注册事件早于 GTBlocks 注册，同 GtnecoreMachines 惯例） */
    private static TraceabilityPredicate gtBlockPredicate(String id) {
        return Predicates.custom(state -> {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", id));
            return b != null && state.getBlockState().is(b);
        }, () -> {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", id));
            return b == null
                    ? new BlockInfo[]{}
                    : new BlockInfo[]{new BlockInfo(b.defaultBlockState())};
        });
    }

    /** 内部谓词（超净间 innerPredicate 的静态化转写）：收集接收机器，环境绑定用 */
    private static TraceabilityPredicate innerReceiversPredicate() {
        return new TraceabilityPredicate(blockWorldState -> {
            Set<ICleanroomReceiver> receivers = blockWorldState.getMatchContext().getOrCreate("cleanroomReceiver",
                    Sets::newHashSet);
            if (blockWorldState.getTileEntity() instanceof IMachineBlockEntity machineBlockEntity) {
                var machine = machineBlockEntity.getMetaMachine();
                if (machine instanceof ICleanroomProvider || isBannedMachine(machine)) {
                    return false;
                }
            }
            if (blockWorldState.getTileEntity() != null) {
                var receiver = GTCapabilityHelper.getCleanroomReceiver(blockWorldState.getWorld(),
                        blockWorldState.getPos(), null);
                if (receiver != null) {
                    receivers.add(receiver);
                }
            }
            return true;
        }, null) {

            @Override
            public boolean isAny() {
                return true;
            }

            @Override
            public boolean addCache() {
                return true;
            }
        };
    }

    /** 禁入内部机器（超净间 isMachineBanned 同款名单：发生器/采矿/原始机械/另一环境提供者） */
    private static boolean isBannedMachine(MetaMachine machine) {
        if (machine instanceof ICleanroomProvider) return true;
        if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.multiblock.IMufflerMachine) return true;
        if (machine instanceof com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine) return true;
        if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.generator.LargeCombustionEngineMachine)
            return true;
        if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.generator.LargeTurbineMachine)
            return true;
        if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.electric.LargeMinerMachine) return true;
        if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine) return true;
        if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.electric.BedrockOreMinerMachine)
            return true;
        if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.primitive.CokeOvenMachine) return true;
        if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveBlastFurnaceMachine)
            return true;
        return machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitivePumpMachine;
    }

    //////////////////////////////////////
    // ****** 界面 ******//
    //////////////////////////////////////

    @Override
    public void addDisplayText(List<Component> textList) {
        if (isFormed()) {
            var maxVoltage = getMaxVoltage();
            if (maxVoltage > 0) {
                String voltageName = GTValues.VNF[Mth.clamp(GTUtil.getFloorTierByVoltage(maxVoltage), 0,
                        GTValues.VNF.length - 1)];
                textList.add(Component.translatable("gtceu.multiblock.max_energy_per_tick", maxVoltage, voltageName));
            }
            textList.add(Component.translatable("gtnecore.machine.astronomical.type"));
            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtceu.multiblock.work_paused"));
            } else if (isActive()) {
                textList.add(Component.translatable("gtceu.multiblock.running"));
                int currentProgress = (int) (getRecipeLogic().getProgressPercent() * 100);
                double maxInSec = (float) getRecipeLogic().getDuration() / 20.0f;
                double currentInSec = (float) getRecipeLogic().getProgress() / 20.0f;
                textList.add(Component.translatable("gtceu.multiblock.progress",
                        String.format("%.2f", (float) currentInSec),
                        String.format("%.2f", (float) maxInSec), currentProgress));
            }
            if (getRecipeLogic().isWaiting()) {
                textList.add(Component.translatable("gtceu.multiblock.waiting")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            // 校准状态（超净间清洁度的捏他）：校准度 xx/95
            if (isClean()) {
                textList.add(Component.translatable("gtnecore.machine.astronomical.ready")
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            } else {
                textList.add(Component.translatable("gtnecore.machine.astronomical.calibrating.amount",
                        getCleanAmount()).withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            textList.add(Component.translatable("gtnecore.machine.astronomical.wireless"));
        } else {
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    //////////////////////////////////////
    // ****** 无线供电 ******//
    //////////////////////////////////////

    /**
     * 无线供电：把能源仓（inputEnergyContainers）的 EU 推给内部接收机器。
     * 推送式（push）实现——源侧主动查询接收者的能量容器并注入；
     * 通用无线供电体系的探索笔记见 docs/超净间机制与天文研究室设计.md。
     */
    public void wirelessPowerTick() {
        EnergyContainerList sources = getInputEnergyContainers();
        var receivers = getCleanroomReceivers();
        if (sources == null || receivers == null || sources.getEnergyStored() <= 0) return;

        long perMachine = WIRELESS_PER_MACHINE_AMPS
                * GTValues.VA[Mth.clamp(getTier(), GTValues.ULV, GTValues.V.length - 1)];
        for (ICleanroomReceiver receiver : receivers) {
            if (!(receiver instanceof MetaMachine machine) || machine.self().getLevel() == null) continue;
            IEnergyContainer target = GTCapabilityHelper.getEnergyContainer(
                    machine.self().getLevel(), machine.self().getPos(), null);
            if (target == null || !target.inputsEnergy(null)) continue;
            long space = target.getEnergyCapacity() - target.getEnergyStored();
            if (space <= 0) continue;
            long amount = Math.min(space, Math.min(perMachine, sources.getEnergyStored()));
            if (amount <= 0) return;
            long added = target.addEnergy(amount);
            if (added > 0) sources.removeEnergy(added);
        }
    }
}
