package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomLogic;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CleanroomMachine;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import earth.terrarium.adastra.api.systems.OxygenApi;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.*;

/**
 * 反曲率-重力引擎（Antigravity-Gravity Engine）：
 * LuV 级无燃料发电机，利用时空弯曲发电。
 *
 * - 失重环境（WEIGHTLESS）：LuV 电压 96A 稳定输出
 * - 强扭曲空间（SPATIAL_DISTORTION）：UHV 电压 96A 输出
 * - 无环境 → 不发电
 * - 动力仓全满（输出无法满足）时停止工作
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AntigravityEngineMachine extends CleanroomMachine {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AntigravityEngineMachine.class, CleanroomMachine.MANAGED_FIELD_HOLDER);

    /** LuV 96A = 8192 EU/t */
    public static final long BASE_OUTPUT = GTValues.V[GTValues.LuV] * 96;
    /** UHV 96A = 2097152 EU/t（强扭曲空间加成） */
    public static final long DISTORTION_OUTPUT = GTValues.V[GTValues.UHV] * 96;

    @Persisted
    private long currentOutput;

    /** 当前输出 EU/t（@Persisted 客户端同步，供动态渲染器判定运行态） */
    public long getCurrentOutput() {
        return currentOutput;
    }

    public AntigravityEngineMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new AntigravityLogic(this);
    }

    public AntigravityLogic getEngineLogic() {
        return (AntigravityLogic) recipeLogic;
    }

    // 不走超净间环境提供——本机是发电机不是环境提供者
    @Override
    public java.util.Set<CleanroomType> getTypes() {
        return java.util.Set.of();
    }

    /**
     * 覆盖超净间的"内部机器不算部件"过滤：反曲率引擎不是环境提供者，
     * 结构内全包围的部件（环内侧能源输出仓/维护仓/消声仓）必须加入控制器——
     * 否则不 addedToController → 部件 isFormed=false → 成型后机壳伪装失效
     * （火箭发射台仓在结构外层一侧临空所以过滤通过、伪装正常，反曲率引擎内侧仓全包围被过滤）。
     */
    @Override
    public boolean shouldAddPartToController(com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart part) {
        return true;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        initializeAbilities();
        // CleanroomLogic.serverTick 有 duration>0 门槛——必须设置周期，否则发电逻辑不跑
        getRecipeLogic().setDuration(100);
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        if (isFormed()) {
            textList.add(Component.translatable("gtnecore.machine.antigravity_engine.title"));
            var env = getCurrentEnvironment();
            if (env != null) {
                textList.add(Component.translatable("gtnecore.machine.antigravity_engine.environment",
                        Component.translatable(env.getTranslationKey())).withStyle(ChatFormatting.AQUA));
                textList.add(Component.translatable("gtceu.multiblock.max_energy_per_tick",
                        currentOutput, GTValues.VNF[GTValues.UHV]).withStyle(ChatFormatting.GREEN));
            } else {
                textList.add(Component.translatable("gtnecore.machine.antigravity_engine.no_env")
                        .withStyle(ChatFormatting.RED));
            }
            if (getRecipeLogic().isActive()) {
                textList.add(Component.translatable("gtceu.multiblock.running")
                        .withStyle(ChatFormatting.GREEN));
            }
        } else {
            textList.add(Component.translatable("gtceu.multiblock.invalid_structure")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /**
     * 当前所处环境：读 cleanroom 接收者绑定（与火箭发射台同一管线）。
     * 轨道维度由 OrbitEnvironmentHandler 每 100t 自动绑定 WEIGHTLESS / SPATIAL_DISTORTION；
     * 重力正常化器覆盖范围会剥夺绑定（stripWeightless）。无绑定（地面/被剥夺）= 无环境。
     */
    public CleanroomType getCurrentEnvironment() {
        var cleanroom = getCleanroom();
        if (cleanroom == null) return null;
        var types = cleanroom.getTypes();
        if (types.contains(GtneEnvironments.SPATIAL_DISTORTION)) return GtneEnvironments.SPATIAL_DISTORTION;
        if (types.contains(GtneEnvironments.WEIGHTLESS)) return GtneEnvironments.WEIGHTLESS;
        return null;
    }

    /** 根据环境 + 输出仓档位输出 EU：
     *  - 失重：LuV 96A（装 LuV 仓）/ UHV 仓也只出 LuV
     *  - 强扭曲空间：UHV 仓 = UHV 96A；LuV 仓 = LuV 96A（仓跟不上 UHV 就降档）
     *  - 无环境：0 */
    public long getOutputEUt() {
        var env = getCurrentEnvironment();
        if (env == null) return 0;
        int hatchTier = getMaxOutputHatchTier();
        if (env == GtneEnvironments.SPATIAL_DISTORTION && hatchTier >= GTValues.UHV) {
            return DISTORTION_OUTPUT;
        }
        return BASE_OUTPUT;
    }

    /** 已装能源输出仓的最高档位（LuV=6 / UHV=9；其他档仓视为 LuV）。
     *  只统计有能源输出能力的部件——维护仓等其它带档部件不算（否则误解锁 UHV）。 */
    private int getMaxOutputHatchTier() {
        int max = GTValues.LuV;
        for (var part : getParts()) {
            if (!(part instanceof com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine tiered)
                    || tiered.getTier() <= max) continue;
            boolean hasOut = false;
            for (var handler : part.getRecipeHandlers()) {
                if (handler.isValid(IO.OUT)
                        && !handler.getCapability(EURecipeCapability.CAP).isEmpty()) {
                    hasOut = true;
                    break;
                }
            }
            if (hasOut) max = Math.min(tiered.getTier(), GTValues.UHV);
        }
        return max;
    }

    //////////////////////////////////////
    // ****** 结构 ******//
    //////////////////////////////////////

    @Override
    public BlockPattern getPattern() {
        return createPattern(this.getDefinition().getBlock());
    }

    /** 反曲率引擎结构（用户 KJS 示意转写，# → 空格） */
    public static BlockPattern createPattern(Block controller) {
        return FactoryBlockPattern.start(LEFT, UP, FRONT)
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "            AABBABBAA            ", "            CBBDDDBBC            ", "            AABBABBAA            ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "            E       E            ", "            E   E   E            ", "          AAEAAAEAAAEAA          ", "          CCEAAAEAAAECC          ", "          AAEAAAEAAAEAA          ", "            E   E   E            ", "            E       E            ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "        AAAAAAAAAAAAAAAAA        ", "        CCAAAAAAAAAAAAACC        ", "        AAAAAAAAAAAAAAAAA        ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "      AAAAAAA       AAAAAAA      ", "      CCAAAAA       AAAAACC      ", "      AAAAAAA       AAAAAAA      ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "     AAAAA             AAAAA     ", "     CAAAA             AAAAC     ", "     AAAAA             AAAAA     ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "     E                     E     ", "     E                     E     ", "     E                     E     ", "    AEEAA               AAEEA    ", "    CEAAA               AAAEC    ", "    AEEAA               AAEEA    ", "     E                     E     ", "     E                     E     ", "     E                     E     ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "   AAEA                   AEAA   ", "   CAAA                   AAAC   ", "   AAEA                   AEAA   ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "   AAA                     AAA   ", "   CAA                     AAC   ", "   AAA                     AAA   ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "  AAAA                     AAAA  ", "  CAAA                     AAAC  ", "  AAAA                     AAAA  ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "  AAA                       AAA  ", "  CAA                       AAC  ", "  AAA                       AAA  ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", " AAA                         AAA ", " CAA                         AAC ", " AAA                         AAA ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", " AAA                         AAA ", " CAA                         AAC ", " AAA                         AAA ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", " E                             E ", " E                             E ", "AEAA                         AAEA", "CEAA                         AAEC", "AEAA                         AAEA", " E              A              E ", " E            AA AA            E ", "                                 ", "                                 ", "                                 ", "              AA AA              ", "                A                ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "AAA                           AAA", "BAA                           AAB", "AAA                           AAA", "              AAEAA              ", "             A     A             ", "                                 ", "                                 ", "                                 ", "             A     A             ", "              AAEAA              ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                C                ", "                C                ", "                C                ", "BAA            CEC            AAB", "BAA           CEEEC           AAB", "BAA            CEC            AAB", "             AAEEEAA             ", "            A B   B A            ", "              BB BB              ", "                                 ", "              BB BB              ", "            A B   B A            ", "             AAEEEAA             ")
                .aisle("                                 ", "                                 ", "                                 ", "                C                ", "                C                ", "                C                ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "BAA           CCCCC           AAB", "DAA           ECCCE           AAD", "BAA           CCCCC           AAB", "             AEAEAEA             ", "            A       A            ", "              B   B              ", "                                 ", "              B   B              ", "            A       A            ", "             AEAEAEA             ")
                .aisle("                C                ", "                C                ", "                C                ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "              CCCCC              ", "              CCCCC              ", " E            CCCCC            E ", "AEA           ECCCE           AEA", "DEA           ECCCE           AED", "AEA           ECCCE           AEA", " E          AEEEGEEEA          E ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "            AEEEHEEEA            ")
                .aisle("                                 ", "                                 ", "                                 ", "                C                ", "                C                ", "                C                ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "               CCC               ", "BAA           CCCCC           AAB", "DAA           ECCCE           AAD", "BAA           CCCCC           AAB", "             AEAEAEA             ", "            A       A            ", "              B   B              ", "                                 ", "              B   B              ", "            A       A            ", "             AEAEAEA             ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                C                ", "                C                ", "                C                ", "BAA            CEC            AAB", "BAA           CEEEC           AAB", "BAA            CEC            AAB", "             AAEEEAA             ", "            A B   B A            ", "              BB BB              ", "                                 ", "              BB BB              ", "            A B   B A            ", "             AAEEEAA             ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "AAA                           AAA", "BAA                           AAB", "AAA                           AAA", "              AAEAA              ", "             A     A             ", "                                 ", "                                 ", "                                 ", "             A     A             ", "              AAEAA              ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", " E                             E ", " E                             E ", "AEAA                         AAEA", "CEAA                         AAEC", "AEAA                         AAEA", " E              A              E ", " E            AA AA            E ", "                                 ", "                                 ", "                                 ", "              AA AA              ", "                A                ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", " AAA                         AAA ", " CAA                         AAC ", " AAA                         AAA ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", " AAA                         AAA ", " CAA                         AAC ", " AAA                         AAA ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "  AAA                       AAA  ", "  CAA                       AAC  ", "  AAA                       AAA  ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "  AAAA                     AAAA  ", "  CAAA                     AAAC  ", "  AAAA                     AAAA  ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "   AAA                     AAA   ", "   CAA                     AAC   ", "   AAA                     AAA   ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "   AAEA                   AEAA   ", "   CAAA                   AAAC   ", "   AAEA                   AEAA   ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "     E                     E     ", "     E                     E     ", "     E                     E     ", "    AEEAA               AAEEA    ", "    CEAAA               AAAEC    ", "    AEEAA               AAEEA    ", "     E                     E     ", "     E                     E     ", "     E                     E     ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "     AAAAA             AAAAA     ", "     CAAAA             AAAAC     ", "     AAAAA             AAAAA     ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "      AAAAAAA       AAAAAAA      ", "      CCAAAAA       AAAAACC      ", "      AAAAAAA       AAAAAAA      ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "        AAAAAAAAAAAAAAAAA        ", "        CCAAAAAAAAAAAAACC        ", "        AAAAAAAAAAAAAAAAA        ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "            E       E            ", "            E   E   E            ", "          AAEAAAEAAAEAA          ", "          CCEAAAEAAA CC          ", "          AAEAAAEAAAEAA          ", "            E   E   E            ", "            E       E            ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .aisle("                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "            AABBABBAA            ", "            CBBDDDBBC            ", "            AABBABBAA            ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ", "                                 ")
                .where('G', Predicates.controller(Predicates.blocks(controller)))
                // 钻石晶格是 Avaritia（无尽贪婪）的方块——gtceu 从未注册过
                // diamond_lattice_block，查 gtceu id 恒 null → 匹配失败且预览显示为空气
                .where('C', blockPredicate("avaritia", "diamond_lattice_block"))
                .where('A', gtBlockPredicate("luv_machine_casing"))
                .where('F', Predicates.any())
                .where('E', gtBlockPredicate("computer_casing"))
                .where('B', gtBlockPredicate("advanced_computer_casing"))
                .where('H', Predicates.abilities(PartAbility.MAINTENANCE))
                .where('D', Predicates.abilities(PartAbility.OUTPUT_ENERGY)
                        .or(gtBlockPredicate("luv_machine_casing")))
                .where('#', Predicates.any())
                .build();
    }

    private static TraceabilityPredicate gtBlockPredicate(String id) {
        return blockPredicate("gtceu", id);
    }

    /** 跨命名空间方块谓词（gtceu / avaritia / minecraft 均可用） */
    static TraceabilityPredicate blockPredicate(String ns, String id) {
        return Predicates.custom(state -> {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ns, id));
            return b != null && b != net.minecraft.world.level.block.Blocks.AIR
                    && state.getBlockState().is(b);
        }, () -> {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ns, id));
            return (b == null || b == net.minecraft.world.level.block.Blocks.AIR)
                    ? new com.lowdragmc.lowdraglib.utils.BlockInfo[]{}
                    : new com.lowdragmc.lowdraglib.utils.BlockInfo[]{new com.lowdragmc.lowdraglib.utils.BlockInfo(b.defaultBlockState())};
        });
    }

    //////////////////////////////////////
    // ****** 发电逻辑 ******//
    //////////////////////////////////////

    public static class AntigravityLogic extends CleanroomLogic {
        protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
                AntigravityLogic.class, CleanroomLogic.MANAGED_FIELD_HOLDER);

        public AntigravityLogic(AntigravityEngineMachine machine) {
            super(machine);
        }

        @Override
        public AntigravityEngineMachine getMachine() {
            return (AntigravityEngineMachine) super.getMachine();
        }

        @Override
        public ManagedFieldHolder getFieldHolder() {
            return MANAGED_FIELD_HOLDER;
        }

        @Override
        public void serverTick() {
            super.serverTick();
            var machine = getMachine();
            if (!machine.isFormed()) return;

            var output = machine.getOutputEUt();
            machine.currentOutput = output;

            if (output > 0) {
                // 按仓档限速推送；added==0 = 全部输出仓吃不下（满/档位过低）→ 停止
                long added = pushEnergyOutput(machine, output);
                setStatus(added > 0 ? Status.WORKING : Status.IDLE);
                // 调试日志（DEBUG 级）：每 100 tick 打印输出/实际入仓/各输出容器存储
                if (machine.self().getOffsetTimer() % 100 == 0) {
                    StringBuilder sb = new StringBuilder();
                    for (var part : machine.getParts()) {
                        for (var handler : part.getRecipeHandlers()) {
                            if (!handler.isValid(IO.OUT)) continue;
                            for (var c : handler.getCapability(EURecipeCapability.CAP)) {
                                if (c instanceof com.gregtechceu.gtceu.api.capability.IEnergyContainer ec) {
                                    sb.append(" [").append(ec.getEnergyStored()).append('/')
                                            .append(ec.getEnergyCapacity()).append(']');
                                }
                            }
                        }
                    }
                    LOGGER.debug("AntigravityEngine tick={} output={} added={} status={} containers:{}",
                            machine.self().getOffsetTimer(), output, added, getStatus(), sb);
                }
            } else {
                setStatus(Status.IDLE);
            }
        }

        /**
         * 向能源输出仓推送 EU（电压等级语义）：
         * 每 tick 每个仓最多收 min(剩余容量, V[仓档]×A[仓电流])，总配额 = 环境输出。
         * 即引擎的 96A 需要输出仓群电流合计 ≥96A 才吃满；低档仓（ULV/LV…）受电速率
         * 受自身电压限制，不会被"无视档位灌爆"。全满且无处可灌 → 返回 0（置 IDLE）。
         * 注：GTCEu 的 addEnergy 是纯电池模型（无电压门限），必须自己限速。
         */
        private long pushEnergyOutput(AntigravityEngineMachine machine, long eu) {
            long remaining = eu;
            long added = 0;
            for (var part : machine.getParts()) {
                if (remaining <= 0) break;
                for (var handler : part.getRecipeHandlers()) {
                    if (remaining <= 0) break;
                    if (!handler.isValid(IO.OUT)) continue;
                    for (var c : handler.getCapability(EURecipeCapability.CAP)) {
                        if (remaining <= 0) break;
                        if (!(c instanceof com.gregtechceu.gtceu.api.capability.IEnergyContainer ec)) continue;
                        long room = ec.getEnergyCanBeInserted(); // 剩余容量
                        if (room <= 0) continue;
                        // 仓档功率（输出电压×电流）= 该仓每 tick 受电上限
                        long rate = Math.max(1, ec.getOutputVoltage() * ec.getOutputAmperage());
                        long push = Math.min(remaining, Math.min(room, rate));
                        if (push <= 0) continue;
                        added += ec.addEnergy(push);
                        remaining -= push;
                    }
                }
            }
            return added;
        }
    }

}
