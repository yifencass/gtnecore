package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.voltages.GtneVoltages;

import java.util.Locale;


/**
 * GTM-style machines registered into GTCEu's registries.
 * Registered via GTCEuAPI.RegisterEvent (fired before GTCEu freezes its registries).
 */
public final class GtnecoreMachines {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private GtnecoreMachines() {
    }

    /** Energy Cubes for every tier LV..MAX(21) (indexed by tier, null where not registered). */
    public static MachineDefinition[] ENERGY_CUBES = new MachineDefinition[GTValues.TIER_COUNT];

    /** LV Energy Cube (first tiered example). */
    public static MachineDefinition LV_ENERGY_CUBE;

    /** 蒸汽熔炉（64 并行，蒸汽耗量 = steam_oven 两倍） */
    public static MultiblockMachineDefinition STEAM_OVEN_64;

    /** 火箭发射台（可回收小行星勘矿）：配方型采矿机器 */
    public static MultiblockMachineDefinition ROCKET_LAUNCH_PAD;

    /** 天文观测站：环境型多方块（捏他超净间——校准→低功耗，内部机器无线供电） */
    public static MultiblockMachineDefinition ASTRONOMICAL_OBSERVATORY;

    /** 便携天文环境维护仓：挂任意多方块（维护仓位）→ 宿主直接获得天文观测环境
     *  （超净维护仓同款 DummyCleanroom 机制，CleanroomType 换成 ASTRONOMICAL） */
    public static MachineDefinition ASTRONOMICAL_MAINTENANCE_HATCH;

    /** 全能维护仓：同时提供超净 + 天文观测双环境（自定义类，DummyCleanroom 多类型） */
    public static MachineDefinition OMNI_MAINTENANCE_HATCH;

    /** EU 氧气分配器（ad_astra:oxygen_distributor 适配版） */
    public static MachineDefinition EU_OXYGEN_DISTRIBUTOR;

    /** EU 重力正常化器（ad_astra:gravity_normalizer 适配版） */
    public static MachineDefinition EU_GRAVITY_NORMALIZER;

    /** 反曲率-重力引擎：LuV 无燃料发电机（时空弯曲） */
    public static MultiblockMachineDefinition ANTIGRAVITY_ENGINE;

    /** 线程熔炉（线程机制测试机，FURNACE_RECIPES） */
    public static MultiblockMachineDefinition THREAD_FURNACE;

    /** 线程仓：品位 × 档（LV..UHV）全部定义，索引 = grade.ordinal() * 9 + (tier - LV) */
    public static final com.gregtechceu.gtceu.api.machine.MachineDefinition[][] THREAD_HATCHES =
            new com.gregtechceu.gtceu.api.machine.MachineDefinition[3][9];

    /** 火箭发射台配方类型（火箭物品+星图不消耗 + 液体 → 星图对应矿物，消耗 EU） */
    public static com.gregtechceu.gtceu.api.recipe.GTRecipeType ROCKET_LAUNCH_RECIPES;

    /** 星象编程仪配方类型（在天文观测站环境内编程星图/天体数据） */
    public static com.gregtechceu.gtceu.api.recipe.GTRecipeType PLANISPHERE_RECIPES;

    /** 星象编程仪（LV..UHV 九档，GTM registerSimpleMachines 工厂注册） */
    public static com.gregtechceu.gtceu.api.machine.MachineDefinition[] PLANISPHERE_PROGRAMMERS;

    public static void onRecipeTypeRegister(
            com.gregtechceu.gtceu.api.GTCEuAPI.RegisterEvent<ResourceLocation, com.gregtechceu.gtceu.api.recipe.GTRecipeType> event) {
        ROCKET_LAUNCH_RECIPES = new com.gregtechceu.gtceu.api.recipe.GTRecipeType(Gtnecore.id("rocket_launch"), "multiblock")
                .setMaxIOSize(3, 3, 1, 0) // 输入物品 2（火箭+星图，机器槽）、输出物品 1、输入流体 1
                .setEUIO(com.gregtechceu.gtceu.api.capability.recipe.IO.IN)
                .setSound(com.gregtechceu.gtceu.common.data.GTSoundEntries.FURNACE);
        // 与 GTRecipeTypes.register 一致：RecipeType + RecipeSerializer + GTRegistry 三处注册
        com.gregtechceu.gtceu.api.registry.GTRegistries.register(
                net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE,
                ROCKET_LAUNCH_RECIPES.registryName, ROCKET_LAUNCH_RECIPES);
        com.gregtechceu.gtceu.api.registry.GTRegistries.register(
                net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER,
                ROCKET_LAUNCH_RECIPES.registryName, new com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer());
        com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_TYPES.register(Gtnecore.id("rocket_launch"), ROCKET_LAUNCH_RECIPES);

        // 星象编程仪配方类型：单方块简单机器（输入星图胚/材料 → 编程成品，消耗 EU）
        PLANISPHERE_RECIPES = new com.gregtechceu.gtceu.api.recipe.GTRecipeType(Gtnecore.id("planisphere"), "simple")
                .setMaxIOSize(4, 2, 2, 1)
                .setEUIO(com.gregtechceu.gtceu.api.capability.recipe.IO.IN)
                .setSound(com.gregtechceu.gtceu.common.data.GTSoundEntries.FURNACE);
        com.gregtechceu.gtceu.api.registry.GTRegistries.register(
                net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE,
                PLANISPHERE_RECIPES.registryName, PLANISPHERE_RECIPES);
        com.gregtechceu.gtceu.api.registry.GTRegistries.register(
                net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER,
                PLANISPHERE_RECIPES.registryName, new com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer());
        com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_TYPES.register(Gtnecore.id("planisphere"), PLANISPHERE_RECIPES);
    }

    /** 22-tier re-ladder: register LV(1) .. MAX(21); ULV(0) not registered. */
    private static int[] energyCubeTiers() {
        int[] tiers = new int[GtneVoltages.TIER_COUNT - GTValues.LV];
        for (int i = 0; i < tiers.length; i++) {
            tiers[i] = GTValues.LV + i;
        }
        return tiers;
    }

    public static void onMachineRegister(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        ENERGY_CUBES = new MachineDefinition[GTValues.TIER_COUNT];
        for (int tier : energyCubeTiers()) {
            // 注册名用 GTNEcore 自己的 22 档阶梯名（GTValues.VN 0-14 保持原版名，
            // 避免 GTCEu 原生 12-14 档机器被连带改名；显示名由 lang 键翻译嫁接）
            ENERGY_CUBES[tier] = Gtnecore.REGISTRATE
                    .machine(GtneVoltages.VN[tier].toLowerCase(Locale.ROOT) + "_energy_cube",
                            holder -> new EnergyCubeMachine(holder, tier))
                    .tier(tier)
                    .rotationState(RotationState.NONE)
                    .defaultModel()
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_DOWN, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_UP, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_NORTH, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_SOUTH, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_WEST, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_EAST, EnergyCubeMachine.EnergyIOMode.NONE)
                    .tooltips(Component.translatable("block.gtnecore.energy_cube.tooltip"))
                    .register();
        }
        LV_ENERGY_CUBE = ENERGY_CUBES[GTValues.LV];

        // 蒸汽熔炉：并行 = 16×蒸汽仓数（最多 4 仓 = 64）；蒸汽耗量为 steam_oven 的两倍；
        // D/C 槽位可互相放置（输出总线/输入流体仓），各至少 1 个；
        // 墙壁 = gtceu:steam_machine_casing；方块全部用运行时惰性解析（custom 谓词），
        // 不依赖 GTCEu 方块在机器注册事件的注册时序
        STEAM_OVEN_64 = Gtnecore.REGISTRATE
                .multiblock("steam_oven_64", GtneSteamOvenMachine::new)
                .rotationState(RotationState.ALL)
                .appearanceBlock(() -> gtBlock("steam_machine_casing"))
                .defaultModel()
                .recipeType(GTRecipeTypes.FURNACE_RECIPES)
                .recipeModifier(SteamParallelMultiblockMachine::recipeModifier, true)
                .addOutputLimit(ItemRecipeCapability.CAP, 1)
                .tooltips(
                        Component.translatable("block.gtnecore.steam_oven_64.tooltip.parallel"),
                        Component.translatable("block.gtnecore.steam_oven_64.tooltip.steam"))
                .pattern(def -> FactoryBlockPattern.start()
                        .aisle("#ABA#", "#CCC#", "#DDD#", "#E#E#", "#####", "#####", "#####")
                        .aisle("AAAAA", "DFGFC", "DFGFC", "EFEFE", "#EEE#", "##E##", "##E##")
                        .aisle("BAAAB", "DGGGC", "DGGGC", "#EGE#", "#EFE#", "#E#E#", "#E#E#")
                        .aisle("AAAAA", "DFGFC", "DFGFC", "EFEFE", "#EEE#", "##E##", "##E##")
                        .aisle("#ABA#", "#EHE#", "#EEE#", "#E#E#", "#####", "#####", "#####")
                        .where('F', gtBlockPredicate("bronze_gearbox"))
                        .where('E', gtBlockPredicate("steam_machine_casing"))
                        .where('A', gtBlockPredicate("bronze_firebox_casing"))
                        .where('H', Predicates.controller(Predicates.blocks(def.getBlock())))
                        .where('G', gtBlockPredicate("bronze_pipe_casing"))
                        .where('D', Predicates.abilities(PartAbility.EXPORT_ITEMS, PartAbility.IMPORT_ITEMS)
                                .setMinGlobalLimited(1).setMaxGlobalLimited(3)
                                .or(gtBlockPredicate("steam_machine_casing")))
                        .where('C', Predicates.abilities(PartAbility.IMPORT_ITEMS, PartAbility.EXPORT_ITEMS)
                                .setMinGlobalLimited(1).setMaxGlobalLimited(5)
                                .or(gtBlockPredicate("steam_machine_casing")))
                        .where('B', Predicates.abilities(PartAbility.STEAM)
                                .setMinGlobalLimited(1).setMaxGlobalLimited(4)
                                .or(gtBlockPredicate("bronze_firebox_casing")))
                        .where('#', Predicates.any())
                        .build())
                .register();

        // 可回收小行星勘矿火箭发射台：19×19×19 圆柱发射塔（KJS 结构翻译）
        // J=控制器（蛙明灯位）、B=能源仓(≤4)/流体输入/物品输出/solid_machine_casing、G=any、I=空气
        ROCKET_LAUNCH_PAD = Gtnecore.REGISTRATE
                .multiblock("rocket_launch_pad", RocketLaunchPadMachine::new)
                .rotationState(RotationState.NONE)   // 无朝向（超净间同款）：成型检测水平 4 向，结构自然堆叠
                .appearanceBlock(() -> gtBlock("solid_machine_casing"))   // 成型后部件显示 solid_machine_casing
                .recipeType(ROCKET_LAUNCH_RECIPES)
                // 超频 + 失重加成：单参 recipeModifier(...) 会整个替换 MachineBuilder 默认链
                // （默认 = OC_NON_PERFECT，被换掉就是"配方不吃超频"）——必须用 recipeModifiers
                // 组链：OC_NON_PERFECT 在补链末尾（能量仓电压 > 配方 EUt 即按档减时），
                // SPACE_BONUS 置 alwaysTry 以便每 tick 检测失重环境（时间 1/4、产出 ×2）
                .recipeModifiers(true,
                        com.gregtechceu.gtceu.common.data.GTRecipeModifiers.OC_NON_PERFECT,
                        RocketLaunchPadMachine.SPACE_BONUS_MODIFIER)
                .simpleModel(Gtnecore.id("block/machine/rocket_launch_pad"))
                .tooltips(Component.translatable("block.gtnecore.rocket_launch_pad.tooltip"))
                .pattern(def -> FactoryBlockPattern.start()
                        .aisle("####ABBBBBBBBBA####", "####AAAAAAAAAAA####", "########CDC########", "########CDC########", "########CDC########", "########CDC########", "########CDC########", "####EEEEEFEEEEE####", "####EEEEFFFEEEE####", "####EEEEEFEEEEE####", "########C#C########", "########C#C########", "########C#C########", "########F#F########", "###################", "###################", "###################", "###################", "###################")
                        .aisle("###AAHHHHHHHHHAA###", "###AAHHHHHHHHHAA###", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "###EEIIIIIIIIIEE###", "###EEIIIIIIIIIEE###", "###EEIIIIIIIIIEE###", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "####EEEEEFEEEEE####", "#########D#########", "#########D#########", "#########D#########", "#########D#########", "#########F#########")
                        .aisle("##AAHHHHHHHHHHHAA##", "##AAHHHHHHHHHHHAA##", "##E#IIIIIIIIIII#E##", "##E#IIIIIIIIIII#E##", "##E#IIIIIIIIIII#E##", "##E#IIIIIIIIIII#E##", "##E#IIIIIIIIIII#E##", "##EEIIIIIIIIIIIEE##", "##EEIIIIIIIIIIIEE##", "##EEIIIIIIIIIIIEE##", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EEIIIIIIIIIEE###", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "####EEEEEEEEEEE####")
                        .aisle("#AAHHHHHHHHHHHHHAA#", "#AAHHHHHHHHHHHHHAA#", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "#EEIIIIIIIIIIIIIEE#", "#EEIIIIIIIIIIIIIEE#", "#EEIIIIIIIIIIIIIEE#", "##EIIIIIIIIIIIIIE##", "##EIIIIIIIIIIIIIE##", "##EIIIIIIIIIIIIIE##", "##EEIIIIIIIIIIIEE##", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EEIIIIIIIIIEE###")
                        .aisle("AAHHHHHHHHHHHHHHHAA", "AAHHHHHHHHHHHHHHHAA", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "EEIIIIIIIIIIIIIIIEE", "EEIIIIIIIIIIIIIIIEE", "EEIIIIIIIIIIIIIIIEE", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "#EEIIIIIIIIIIIIIEE#", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "##EEIIIIIIIIIIIEE##")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHHHHHHHHHHA", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#EIIIIIIIIIIIIIIIE#", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##EIIIIIIIIIIIIIE##")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHHHHHHHHHHA", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#EIIIIIIIIIIIIIIIE#", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##EIIIIIIIIIIIIIE##")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHHHHHHHHHHA", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#EIIIIIIIIIIIIIIIE#", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##EIIIIIIIIIIIIIE##")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHHHHHHHHHHA", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "EIIIIIIIIIIIIIIIIIE", "FIIIIIIIIIIIIIIIIIF", "EIIIIIIIIIIIIIIIIIE", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "FEIIIIIIIIIIIIIIIEF", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##EIIIIIIIIIIIIIE##")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHJHHHHHHHHA", "DIIIIIIIIIIIIIIIIID", "DIIIIIIIIIIIIIIIIID", "DIIIIIIIIIIIIIIIIID", "DIIIIIIIIIIIIIIIIID", "DIIIIIIIIIIIIIIIIID", "FIIIIIIIIIIIIIIIIIF", "FIIIIIIIIIIIIIIIIIF", "FIIIIIIIIIIIIIIIIIF", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#FIIIIIIIIIIIIIIIF#", "#DIIIIIIIIIIIIIIID#", "#DIIIIIIIIIIIIIIID#", "#DIIIIIIIIIIIIIIID#", "#DIIIIIIIIIIIIIIID#", "#FEIIIIIIIIIIIIIEF#")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHHHHHHHHHHA", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "EIIIIIIIIIIIIIIIIIE", "FIIIIIIIIIIIIIIIIIF", "EIIIIIIIIIIIIIIIIIE", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "CIIIIIIIIIIIIIIIIIC", "FEIIIIIIIIIIIIIIIEF", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##EIIIIIIIIIIIIIE##")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHHHHHHHHHHA", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#EIIIIIIIIIIIIIIIE#", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##EIIIIIIIIIIIIIE##")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHHHHHHHHHHA", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#EIIIIIIIIIIIIIIIE#", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##EIIIIIIIIIIIIIE##")
                        .aisle("BHHHHHHHHHHHHHHHHHB", "AHHHHHHHHHHHHHHHHHA", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "EIIIIIIIIIIIIIIIIIE", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#IIIIIIIIIIIIIIIII#", "#EIIIIIIIIIIIIIIIE#", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##EIIIIIIIIIIIIIE##")
                        .aisle("AAHHHHHHHHHHHHHHHAA", "AAHHHHHHHHHHHHHHHAA", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "EEIIIIIIIIIIIIIIIEE", "EEIIIIIIIIIIIIIIIEE", "EEIIIIIIIIIIIIIIIEE", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "##IIIIIIIIIIIIIII##", "#EEIIIIIIIIIIIIIEE#", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "##EEIIIIIIIIIIIEE##")
                        .aisle("#AAHHHHHHHHHHHHHAA#", "#AAHHHHHHHHHHHHHAA#", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "###IIIIIIIIIIIII###", "#EEIIIIIIIIIIIIIEE#", "#EEIIIIIIIIIIIIIEE#", "#EEIIIIIIIIIIIIIEE#", "##EIIIIIIIIIIIIIE##", "##EIIIIIIIIIIIIIE##", "##EIIIIIIIIIIIIIE##", "##EEIIIIIIIIIIIEE##", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EEIIIIIIIIIEE###")
                        .aisle("##AAHHHHHHHHHHHAA##", "##AAHHHHHHHHHHHAA##", "##E#IIIIIIIIIII#E##", "##E#IIIIIIIIIII#E##", "##E#IIIIIIIIIII#E##", "##E#IIIIIIIIIII#E##", "##E#IIIIIIIIIII#E##", "##EEIIIIIIIIIIIEE##", "##EEIIIIIIIIIIIEE##", "##EEIIIIIIIIIIIEE##", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EIIIIIIIIIIIE###", "###EEIIIIIIIIIEE###", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "####EEEEEEEEEEE####")
                        .aisle("###AAHHHHHHHHHAA###", "###AAHHHHHHHHHAA###", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "###EEIIIIIIIIIEE###", "###EEIIIIIIIIIEE###", "###EEIIIIIIIIIEE###", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "#####IIIIIIIII#####", "####EEEEEFEEEEE####", "#########D#########", "#########D#########", "#########D#########", "#########D#########", "#########F#########")
                        .aisle("####ABBBBBBBBBA####", "####AAAAAAAAAA#####", "########CDC########", "########CDC########", "########CDC########", "########CDC########", "########CDC########", "####EEEEEFEEEEE####", "####EEEEFFFEEEE####", "####EEEEEFEEEEE####", "########C#C########", "########C#C########", "########C#C########", "########F#F########", "###################", "###################", "###################", "###################", "###################")
                        .where('A', gtBlockPredicate("solid_machine_casing"))
                        .where('H', Predicates.blocks(net.minecraft.world.level.block.Blocks.SMOOTH_STONE))
                        .where('C', gtBlockPredicate("steel_gearbox"))
                        .where('E', gtBlockPredicate("steel_frame"))
                        .where('F', gtBlockPredicate("mv_machine_casing"))
                        .where('D', gtBlockPredicate("lv_hermetic_casing"))
                        .where('I', Predicates.air())
                        .where('J', Predicates.controller(Predicates.blocks(def.getBlock())))
                        .where('B', Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(4)
                                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_ITEMS))
                        .or(gtBlockPredicate("solid_machine_casing")))
                .where('#', Predicates.any())
                .build())
                .register();

        // 天文观测站：NONE 不可旋转（超净间同款）；结构见 AstronomicalObservatoryMachine.createPattern
        // （固定 9×5×9：plascrete 墙 + 玻璃（通行仓/幻影门≤7）+ 辅镜幕墙 + 后墙中段控制器）
        // workableCasingModel（超净间同款）会附带声明 RECIPE_LOGIC_STATUS 等渲染属性，
        // 模型变体键需要它们；simpleModel 不声明 → "Unknown machine model state property"
        ASTRONOMICAL_OBSERVATORY = Gtnecore.REGISTRATE
                .multiblock("astronomical_observatory", AstronomicalObservatoryMachine::new)
                .rotationState(RotationState.NONE)
                .allowExtendedFacing(false)
                .allowFlip(false)
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .appearanceBlock(() -> gtBlock("plascrete"))
                .workableCasingModel(com.gregtechceu.gtceu.GTCEu.id("block/casings/cleanroom/plascrete"),
                        com.gregtechceu.gtceu.GTCEu.id("block/multiblock/cleanroom"))
                .tooltips(Component.translatable("block.gtnecore.astronomical_observatory.tooltip"))
                .pattern(def -> AstronomicalObservatoryMachine.createPattern(def.getBlock()))
                .register();

        // 星象编程仪 LV..UHV（9 档）：GTM 工厂同款——SimpleTieredMachine + NON_Y_AXIS +
        // workableTieredHullModel（声明渲染属性）+ OC 超频 + 标准 tooltip/UI。
        // overlay 贴图：assets/gtceu/textures/block/machines/planisphere_programmer/
        // overlay_front(_active).png（active 为 8 帧动画，带 mcmeta）
        PLANISPHERE_PROGRAMMERS = com.gregtechceu.gtceu.common.data.machines.GTMachineUtils
                .registerSimpleMachines(Gtnecore.REGISTRATE, "planisphere_programmer", PLANISPHERE_RECIPES,
                        com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.defaultTankSizeFunction, false,
                        GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV,
                        GTValues.LuV, GTValues.ZPM, GTValues.UV, GTValues.UHV);

        // 便携天文环境维护仓：CleaningMaintenanceHatchPartMachine 类型参数化——
        // 同款机制（DummyCleanroom.createForTypes + addedToController 绑宿主），换 ASTRONOMICAL
        ASTRONOMICAL_MAINTENANCE_HATCH = Gtnecore.REGISTRATE
                .machine("astronomical_maintenance_hatch",
                        holder -> new com.gregtechceu.gtceu.common.machine.multiblock.part.CleaningMaintenanceHatchPartMachine(
                                holder, AstronomicalObservatoryMachine.ASTRONOMICAL))
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.MAINTENANCE)
                .modelProperty(com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_FORMED, false)
                .tooltips(Component.translatable("gtceu.part_sharing.disabled"),
                        Component.translatable("gtnecore.machine.astronomical_maintenance.tooltip.0"),
                        Component.translatable("gtnecore.machine.astronomical_maintenance.tooltip.1"))
                .tooltipBuilder((stack, tooltips) -> tooltips.add(Component.literal("  ").append(
                        Component.translatable("gtnecore.recipe.astronomical.display_name")
                                .withStyle(net.minecraft.ChatFormatting.GREEN))))
                .overlayTieredHullModel(com.gregtechceu.gtceu.GTCEu.id("block/machine/part/cleaning_maintenance_hatch"))
                .tier(GTValues.HV)
                .register();

        // 全能维护仓：超净 + 天文观测双环境（EV 档，比单能高一档）
        OMNI_MAINTENANCE_HATCH = Gtnecore.REGISTRATE
                .machine("omni_maintenance_hatch", OmniMaintenanceHatchPartMachine::new)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.MAINTENANCE)
                .modelProperty(com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_FORMED, false)
                .tooltips(Component.translatable("gtceu.part_sharing.disabled"),
                        Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"),
                        Component.translatable("gtnecore.machine.omni_maintenance.tooltip.0"))
                .tooltipBuilder((stack, tooltips) -> {
                    tooltips.add(Component.literal("  ").append(Component
                            .translatable(com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType.CLEANROOM
                                    .getTranslationKey())
                            .withStyle(net.minecraft.ChatFormatting.GREEN)));
                    tooltips.add(Component.literal("  ").append(Component
                            .translatable("gtnecore.recipe.astronomical.display_name")
                            .withStyle(net.minecraft.ChatFormatting.GREEN)));
                })
                .overlayTieredHullModel(com.gregtechceu.gtceu.GTCEu.id("block/machine/part/cleaning_maintenance_hatch"))
                .tier(GTValues.EV)
                .register();

        // Ad Astra EU 适配机器：HV 档单方块（EU 电缆直供；氧气版另需 ad_astra:oxygen 流体导入）。
        // 效果复刻原版（FloodFill3D 密封扫描 + OxygenApi/GravityApi 区域标记），区域上限与
        // 重扫周期沿用 Ad Astra MachineConfig；EUt = 8 + 覆盖数/16
        EU_OXYGEN_DISTRIBUTOR = Gtnecore.REGISTRATE
                .machine("eu_oxygen_distributor",
                        holder -> new EuOxygenDistributorMachine(holder, GTValues.HV,
                                com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.defaultTankSizeFunction))
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .workableTieredHullModel(Gtnecore.id("block/machines/eu_oxygen_distributor"))
                .tier(GTValues.HV)
                .tooltips(Component.translatable("block.gtnecore.eu_oxygen_distributor.tooltip"))
                .register();

        EU_GRAVITY_NORMALIZER = Gtnecore.REGISTRATE
                .machine("eu_gravity_normalizer",
                        holder -> new EuGravityNormalizerMachine(holder, GTValues.HV,
                                com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.defaultTankSizeFunction))
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .workableTieredHullModel(Gtnecore.id("block/machines/eu_gravity_normalizer"))
                .tier(GTValues.HV)
                .tooltips(Component.translatable("block.gtnecore.eu_gravity_normalizer.tooltip"))
                .register();

        // 反曲率-重力引擎：LuV 无燃料发电机（利用时空弯曲）
        // 失重=LuV 96A / 强扭曲空间=UHV 96A / 无环境=不发电
        ANTIGRAVITY_ENGINE = Gtnecore.REGISTRATE
                .multiblock("antigravity_engine", AntigravityEngineMachine::new)
                .rotationState(RotationState.NONE)
                .allowExtendedFacing(false)
                .allowFlip(false)
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .appearanceBlock(() -> gtBlock("luv_machine_casing"))
                .workableCasingModel(
                        com.gregtechceu.gtceu.GTCEu.id("block/casings/solid/machine_casing_solid_titanium"),
                        Gtnecore.id("block/machine/antigravity_engine"))
                .tooltips(Component.translatable("block.gtnecore.antigravity_engine.tooltip"))
                .pattern(def -> AntigravityEngineMachine.createPattern(def.getBlock()))
                .renderMultiblockXEIPreview(true)   // 在 JEI 多方块信息分类中显示结构预览
                .register();

        // ===== 线程机制（阶段 1：测试机 + 三品位线程仓，LV..UHV 九档）=====
        // 线程仓：普通（与并行相斥）/ 均分（P/T）/ 倍频（T×P）；线程数 = 2^(tier-LV)（LV=1）
        {
            int[] tiers = new int[]{GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV,
                    GTValues.IV, GTValues.LuV, GTValues.ZPM, GTValues.UV, GTValues.UHV};
            for (int i = 0; i < tiers.length; i++) {
                int tier = tiers[i];
                for (var grade : ThreadGrade.values()) {
                    String id = GtneVoltages.VN[tier].toLowerCase(Locale.ROOT) + "_" + grade.idSuffix();
                    var def = Gtnecore.REGISTRATE
                            .machine(id, holder -> new ThreadHatchPartMachine(holder, tier, grade))
                            .langValue(com.gregtechceu.gtceu.api.GTValues.VN[tier]
                                    + " " + (grade == ThreadGrade.NORMAL ? "" : "Split ") + "Thread Hatch")
                            .rotationState(RotationState.ALL)
                            .abilities(IThreadHatch.THREAD_ABILITY)
                            .modelProperty(com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_FORMED, false)
                            // overlayTieredHullModel：按机器档位自动拼 tiered 机壳 + 品位 overlay 模型
                            .overlayTieredHullModel(Gtnecore.id("block/machine/part/" + grade.idSuffix()))
                            .tooltips(Component.translatable("gtnecore.machine.thread_hatch.tooltip.0"),
                                    Component.translatable("gtnecore.thread_grade." + grade.getSerializedName()))
                            .tier(tier)
                            .register();
                    THREAD_HATCHES[grade.ordinal()][i] = def;
                }
            }
        }

        // 线程熔炉（测试机）：FURNACE_RECIPES，HV 档
        THREAD_FURNACE = Gtnecore.REGISTRATE
                .multiblock("thread_furnace", ThreadFurnaceMachine::new)
                .langValue("Thread Furnace")
                .rotationState(RotationState.NONE)
                .allowExtendedFacing(false)
                .allowFlip(false)
                .recipeType(GTRecipeTypes.FURNACE_RECIPES)
                .recipeModifier(ThreadFurnaceMachine.THREAD_PARALLEL, true)
                .appearanceBlock(() -> gtBlock("solid_machine_casing"))
                .workableCasingModel(
                        com.gregtechceu.gtceu.GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        Gtnecore.id("block/machine/thread_furnace"))
                .tooltips(Component.translatable("block.gtnecore.thread_furnace.tooltip"))
                .pattern(def -> ThreadFurnaceMachine.createPattern(def.getBlock(), def.getRecipeTypes()))
                // 隐藏：不参与 JEI 多方块信息预览（线程机制优先度最低）
                .register();

        // GTNEcore: 官方部件补 IS_FORMED 渲染属性必须在模型解析（read）之前执行。
        // 原在 FMLCommonSetup 里调用太晚——机器模型在客户端资源加载时用当时的
        // stateDefinition 烘焙（无 is_formed），而机器运行时渲染状态是补属性后的
        // （有 is_formed）→ IdentityHashMap 永远 miss → 机器/部件透明。
        // RegisterEvent（本方法）在资源加载之前，此处补属性后烘焙与运行一致。
        registerPartFormedProperty();
    }

    /**
     * 给官方部件机器（有 abilities 的非多方块）补注册 IS_FORMED 渲染属性（FMLCommonSetup 调用）：
     * GTCEu 1.20.1 的部件模型 JSON 已带 is_formed 变体（gtceu:machine loader），但部分官方部件
     * （能源输出仓等）注册时未声明 IS_FORMED 属性——变体键匹配不到任何渲染状态，成型模型切换
     * 不生效。补上属性后：addedToController 自动置 true → 渲染状态 is_formed=true →
     * 模型变体切换（资源覆盖版 is_formed=true = 纯机壳，实现"成型后部件机壳同步控制器"）。
     * 已注册属性的部件跳过（不重复）。
     */
    public static void registerPartFormedProperty() {
        var isFormed = com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_FORMED;
        int count = 0;
        for (var def : com.gregtechceu.gtceu.api.registry.GTRegistries.MACHINES.values()) {
            if (def instanceof MultiblockMachineDefinition) continue; // 控制器自带 is_formed 变体
            var old = def.getStateDefinition();
            if (old.getProperties().contains(isFormed)) continue;
            var builder = new net.minecraft.world.level.block.state.StateDefinition.Builder<
                    com.gregtechceu.gtceu.api.machine.MachineDefinition,
                    com.gregtechceu.gtceu.client.model.machine.MachineRenderState>(def);
            old.getProperties().forEach(p -> builder.add(p));
            builder.add(isFormed);
            def.setStateDefinition(builder.create(
                    com.gregtechceu.gtceu.api.machine.MachineDefinition::defaultRenderState,
                    com.gregtechceu.gtceu.client.model.machine.MachineRenderState::new));
            var defaultState = def.getStateDefinition().any();
            def.registerDefaultState(defaultState.setValue(isFormed, false));
            if (def.getId().getPath().contains("luv_energy_output")) {
                LOGGER.info("GTNEcore: 补注册示例 {} 属性集={}",
                        def.getId(), def.getStateDefinition().getProperties());
            }
            count++;
        }
        LOGGER.info("GTNEcore: 已为 {} 个官方部件机器补注册 IS_FORMED 渲染属性", count);
    }

    // ---- GTCEu 方块运行时惰性解析（规避注册时序：机器注册事件早于 GTBlocks 注册） ----

    /** 按注册名取 gtceu 方块（惰性缓存，运行时首次调用时才解析） */
    private static Block gtBlock(String id) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", id));
        return b;
    }

    /**
     * 匹配 gtceu 方块的 custom 谓词：test 在成型检查时执行（方块已注册）；
     * BlockInfo（预览）Supplier 同样惰性。不参与 limited 计数（作为 or 的填空项）。
     */
    private static com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate gtBlockPredicate(String id) {
        return Predicates.custom(state -> {
            Block b = gtBlock(id);
            return b != null && state.getBlockState().is(b);
        }, () -> {
            Block b = gtBlock(id);
            return b == null
                    ? new com.lowdragmc.lowdraglib.utils.BlockInfo[]{}
                    : new com.lowdragmc.lowdraglib.utils.BlockInfo[]{new com.lowdragmc.lowdraglib.utils.BlockInfo(b.defaultBlockState())};
        });
    }
}
