package org.ringclouds.gtnecore.addon;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

/**
 * GTNEcore addon 入口（GTCEuAPI 发现）。
 */
@GTAddon
public class GtnecoreAddon implements IGTAddon {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GtnecoreAddon.class);

    @Override
    public GTRegistrate getRegistrate() {
        return org.ringclouds.gtnecore.Gtnecore.REGISTRATE;
    }

    @Override
    public String addonModId() {
        return org.ringclouds.gtnecore.Gtnecore.MODID;
    }

    @Override
    public void initializeAddon() {
    }

    @Override
    public void registerCovers() {
        // 真空泵覆盖板定义：**唯一**注册点（GTCEu 在 GTCovers.init() 里 unfreeze 之后、
        // freeze 之前调用本 hook）。主类构造器里**不能**调——那时 gtceu:cover 已冻结，
        // 会抛 IllegalStateException 导致启动崩溃（2026-09-24 事故）。
        org.ringclouds.gtnecore.cover.GtnecoreCovers.register();
    }

    @Override
    public void addRecipes(java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe> consumer) {
        // 星际采矿任务：6 种元素星图 × 电路 0-4（时间 4/8/16/32/64min，4 号电路矿产 ×4）
        // 输入：MKI 火箭（不消耗）+ 对应星图（不消耗）+ 编程电路（不消耗，Configuration 匹配）+ 液态氢 32000mB（消耗）
        // 电压：Al 星图 = HV，其余 = MV
        var rocketType = org.ringclouds.gtnecore.machine.GtnecoreMachines.ROCKET_LAUNCH_RECIPES;
        if (rocketType == null) return;
        var mkiRocket = org.ringclouds.gtnecore.item.GtnecoreItems.ROCKETS.get("i");
        if (mkiRocket == null) return;
        String[][] chartMaterials = {
                {"al", "Aluminium"}, {"au", "Gold"}, {"cu", "Copper"},
                {"fe", "Iron"}, {"ni", "Nickel"}, {"sn", "Tin"}
        };
        int[] durations = {2400, 4800, 9600, 19200, 38400}; // 4/8/16/32/64 min
        // 发射台配方（2026-09-12 定案）：纯数据包 JSON 单源（data/gtnecore/recipes/rocket_launch/，
        // 24 个 9 槽概率版）——历史代码版从未实际注册（GTDynamicDataPack 无 gtnecore_launch 条目，
        // JEI 一直显示 JSON 版），已删除避免双源混乱。
        // 星图编写（gtnecore:planisphere）：天体星图（消耗）+ 编程电路（不消耗，Configuration 匹配）
        // → 对应元素小行星星图。电路 1..6 ↔ Al/Au/Cu/Fe/Ni/Sn；Al 需 HV、其余 MV；
        // 基础时长 2 小时；有损超频由机器的 OC_NON_PERFECT 提供（registerSimpleMachines 自带）；
        // 需要天文观测环境（JEI 配方页经 GTRecipeWidget 条件标签自动显示）。
        // 电路用 IntCircuitIngredient（GTM 正统）：JEI 显示编号徽标——NBTPredicateIngredient
        // 拿不到编号会全显示成 0 号电路
        var planisphereType = org.ringclouds.gtnecore.machine.GtnecoreMachines.PLANISPHERE_RECIPES;
        if (planisphereType == null) return;
        var blankChart = org.ringclouds.gtnecore.item.GtnecoreItems.ASTRAL_CHARTS.get("none");
        if (blankChart == null) return;
        for (int i = 0; i < chartMaterials.length; i++) {
            var targetChart = org.ringclouds.gtnecore.item.GtnecoreItems.ASTRAL_CHARTS.get(chartMaterials[i][0]);
            if (targetChart == null) continue;
            int configuration = i + 1; // 电路 1..6
            long eut = chartMaterials[i][0].equals("al")
                    ? com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.HV]
                    : com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.MV];
            planisphereType.recipeBuilder("star_chart_" + chartMaterials[i][0])
                    .inputItems(blankChart.asStack())
                    .notConsumable(com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient.of(configuration))
                    .outputItems(targetChart.asStack())
                    .cleanroom(org.ringclouds.gtnecore.machine.AstronomicalObservatoryMachine.ASTRONOMICAL)
                    .duration((int) (2 * com.gregtechceu.gtceu.api.GTValues.HOURS)) // 2 小时
                    .EUt(eut)
                    .save(consumer);
        }

        // 魔源萃取：源浆果丛 ×16 → 448 魔源（28/颗）200t，无 EU（16 通道一轮的配方化表达）
        var berryItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("ars_nouveau", "sourceberry_bush"));
        if (berryItem != null && berryItem != net.minecraft.world.item.Items.AIR) {
            org.ringclouds.gtnecore.machine.GtnecoreMachines.SOURCE_EXTRACTION_RECIPES
                    .recipeBuilder("sourceberry_to_source")
                    .inputItems(berryItem, 16)
                    .addData("source", 16 * 28)
                    .duration(200)
                    .EUt(0)
                    .save(consumer);
        }

        // 对顶砧压制：液态氢 144 mB → 金属氢（H 的锭）×1，UEV 电压、200t——金属氢专属产线
        // 数值真源 = WignerHuntingtonAnvilMachine 的调参常量（改数值只改那一处）
        var hydrogenIngot = com.gregtechceu.gtceu.common.data.GTMaterialItems.MATERIAL_ITEMS.get(
                com.gregtechceu.gtceu.api.data.tag.TagPrefix.ingot,
                com.gregtechceu.gtceu.common.data.GTMaterials.Hydrogen);
        if (hydrogenIngot != null) {
            org.ringclouds.gtnecore.machine.GtnecoreMachines.WIGNER_HUNTINGTON_PRESS_RECIPES
                    .recipeBuilder("liquid_hydrogen_to_metal_hydrogen")
                    .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Hydrogen.getFluid(
                            com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys.LIQUID,
                            org.ringclouds.gtnecore.machine.WignerHuntingtonAnvilMachine.HYDROGEN_MB_PER_INGOT))
                    .outputItems(hydrogenIngot.asStack(1))
                    .duration(org.ringclouds.gtnecore.machine.WignerHuntingtonAnvilMachine.PRESS_DURATION_TICKS)
                    .EUt(org.ringclouds.gtnecore.machine.WignerHuntingtonAnvilMachine.PRESS_EUT)
                    // 用户 2026-09-12：金属氢必须在超净环境下压制（JEI 条件标签自动显示）
                    // 塔高 26 格、塞不进超净间 → 实战走 H 维护仓位装「超净维护仓 / 全能维护仓」
                    .cleanroom(com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType.CLEANROOM)
                    .save(consumer);
        }

        // 空白天体星图（用户 2026-08-29 指定）：
        // 编程电路(不消耗) + 4×MV电路 + 4×铝板 + 2×RAM + 32mB焊锡，MV 电路组装机
        var blankChartEntry = org.ringclouds.gtnecore.item.GtnecoreItems.ASTRAL_CHARTS.get("none");
        if (blankChartEntry != null) {
            var ram = com.gregtechceu.gtceu.common.data.GTItems.RANDOM_ACCESS_MEMORY;
            var aluPlate = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation("gtceu", "aluminium_plate"));
            com.gregtechceu.gtceu.common.data.GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES
                    .recipeBuilder("astral_chart_blank")
                    .notConsumable(com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient.of(0))
                    .inputItems(com.gregtechceu.gtceu.data.recipe.CustomTags.MV_CIRCUITS, 4)
                    .inputItems(new net.minecraft.world.item.ItemStack(aluPlate, 4))
                    .inputItems(ram.asStack(2))
                    .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.SolderingAlloy.getFluid(32))
                    .outputItems(blankChartEntry.asStack())
                    .duration(200).EUt(com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.MV])
                    .save(consumer);
        }

        // 能量立方配方（用户指定 XLX/EEE/XVX 图案，2026-08-29）：
        // L=次一级变压器  V=当前级变压器  E=当前级能源仓  X=当前级机器外壳
        var energyCubes = org.ringclouds.gtnecore.machine.GtnecoreMachines.ENERGY_CUBES;
        var transformer = com.gregtechceu.gtceu.common.data.GTMachines.TRANSFORMER;
        var energyHatch = com.gregtechceu.gtceu.common.data.GTMachines.ENERGY_INPUT_HATCH;
        var hulls = com.gregtechceu.gtceu.common.data.GTMachines.HULL;
        for (int tier = com.gregtechceu.gtceu.api.GTValues.LV;
             tier <= Math.min(com.gregtechceu.gtceu.api.GTValues.IV, energyCubes.length - 1); tier++) {
            if (energyCubes[tier] == null || transformer[tier] == null || transformer[tier - 1] == null
                    || energyHatch[tier] == null || hulls[tier] == null) continue;
            com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES
                    .recipeBuilder(energyCubes[tier].getId().getPath())
                    .inputItems(transformer[tier - 1].asStack())
                    .inputItems(energyHatch[tier].asStack(), 3)
                    .inputItems(transformer[tier].asStack())
                    .inputItems(hulls[tier].asStack(), 2)
                    .outputItems(energyCubes[tier].asStack())
                    .duration(200).EUt(com.gregtechceu.gtceu.api.GTValues.V[tier])
                    .save(consumer);
        }

        // MKI 火箭组装机配方（动力合成 13×13 的低成本替代，用户 2026-08-28 指定）：
        // 仅三种耗材（数量取自动力合成图案：双板 23+2=25 / 流体单元 5 / 致密钢板 12）
        // + 焊锡 1961mB；最低 HV（EUt=512，HV 以下机器跑不动）
        var gtItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("gtceu", "double_aluminium_plate"));
        var fluidCell = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("gtceu", "aluminium_fluid_cell"));
        var densePlate = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("gtceu", "dense_steel_plate"));
        if (gtItem == null || fluidCell == null || densePlate == null) return;
        com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES
                .recipeBuilder("mk1_rocket")
                .inputItems(new net.minecraft.world.item.ItemStack(gtItem, 25))
                .inputItems(new net.minecraft.world.item.ItemStack(fluidCell, 5))
                .inputItems(new net.minecraft.world.item.ItemStack(densePlate, 12))
                .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.SolderingAlloy.getFluid(1961))
                .outputItems(mkiRocket.asStack())
                .duration(1200) // 60s 基准（可调）
                .EUt(com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.HV])
                .save(consumer);

        // ============ 0→HV 机器获取配方（gtnecore 自给；GTCEu 不给 addon 机器自动生成配方） ============
        // 审计结论（docs/0到HV测试版覆盖审计-2026-09-05.md）：此前全部机器本体无配方只能 /give。
        // 策略：蒸汽期/纯建材走合成台，电力机器走组装机，档位贴合机器用途节奏
        // （编程仪 LV/MV/HV 全给——LV 档留作起步；观测站 MV；发射台/氧分配器/重力正常化器 HV；
        //  天文维护仓 HV、全能维护仓 EV）。
        var gtItem2 = new java.util.function.Function<String, net.minecraft.world.item.Item>() {
            @Override
            public net.minecraft.world.item.Item apply(String id) {
                return net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new net.minecraft.resources.ResourceLocation("gtceu", id));
            }
        };
        java.util.function.Function<Integer, net.minecraft.world.item.ItemStack> hull = tier -> {
            var def = com.gregtechceu.gtceu.common.data.GTMachines.HULL[tier];
            return def == null ? net.minecraft.world.item.ItemStack.EMPTY : def.asStack();
        };

        // ---- 蒸汽熔炉 64（合成台；蒸汽期无 EU，先于组装机）----
        var steamCasing = gtItem2.apply("steam_machine_casing");
        var firebox = gtItem2.apply("bronze_firebox_casing");
        var ovenDef = org.ringclouds.gtnecore.machine.GtnecoreMachines.STEAM_OVEN_64;
        if (steamCasing != null && firebox != null && ovenDef != null) {
            try {
                com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper.addShapedRecipe(consumer,
                        "steam_oven_64_controller", ovenDef.asStack(),
                        "CCC", "CFC", "CCC",
                        'C', steamCasing, 'F', firebox);
            } catch (Throwable e) {
                LOGGER.warn("蒸汽熔炉 64 合成配方注册失败: {}", e.toString());
            }
        }
        // ---- 天文辅镜（合成台建材；观测站结构必需块）----
        var finder = org.ringclouds.gtnecore.block.GtnecoreBlocks.ASTRONOMICAL_FINDERSCOPE;
        if (finder != null) {
            try {
                com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper.addShapedRecipe(consumer,
                        "astronomical_finderscope", finder.asStack(),
                        " I ", "IGI", " I ",
                        'I', net.minecraft.world.item.Items.IRON_INGOT, 'G', net.minecraft.world.item.Items.GLASS);
            } catch (Throwable e) {
                LOGGER.warn("天文辅镜合成配方注册失败: {}", e.toString());
            }
        }

        // ---- 电力机器组装机配方（材料 = 对应档外壳 + 部件；EMPTY 输入自动跳过）----
        var ram2 = com.gregtechceu.gtceu.common.data.GTItems.RANDOM_ACCESS_MEMORY == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : com.gregtechceu.gtceu.common.data.GTItems.RANDOM_ACCESS_MEMORY.asStack(2);
        // 星象编程仪 LV/MV/HV
        var planisphere = org.ringclouds.gtnecore.machine.GtnecoreMachines.PLANISPHERE_PROGRAMMERS;
        if (planisphere != null) {
            int[] tiers = {com.gregtechceu.gtceu.api.GTValues.LV,
                    com.gregtechceu.gtceu.api.GTValues.MV, com.gregtechceu.gtceu.api.GTValues.HV};
            String[] tNames = {"lv", "mv", "hv"};
            for (int i = 0; i < tiers.length; i++) {
                if (planisphere[tiers[i]] == null) continue;
                addMachineRecipe(consumer, "planisphere_programmer_" + tNames[i],
                        planisphere[tiers[i]].asStack(),
                        com.gregtechceu.gtceu.api.GTValues.V[tiers[i]], 300,
                        hull.apply(tiers[i]),
                        new net.minecraft.world.item.ItemStack(gtItem2.apply(tNames[i] + "_electric_motor"), 1),
                        ram2);
            }
        }
        // 热力反应釜（LV~UHV 九档，组装机；与化学反应的机器获取节奏一致）
        var thermalReactors = org.ringclouds.gtnecore.machine.GtnecoreMachines.THERMAL_REACTORS;
        if (thermalReactors != null) {
            int[] thermalTiers = {com.gregtechceu.gtceu.api.GTValues.ULV,
                    com.gregtechceu.gtceu.api.GTValues.LV, com.gregtechceu.gtceu.api.GTValues.MV,
                    com.gregtechceu.gtceu.api.GTValues.HV, com.gregtechceu.gtceu.api.GTValues.EV,
                    com.gregtechceu.gtceu.api.GTValues.IV, com.gregtechceu.gtceu.api.GTValues.LuV,
                    com.gregtechceu.gtceu.api.GTValues.ZPM, com.gregtechceu.gtceu.api.GTValues.UV,
                    com.gregtechceu.gtceu.api.GTValues.UHV};
            String[] thermalNames = {"ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv"};
            for (int i = 0; i < thermalTiers.length; i++) {
                int tier = thermalTiers[i];
                if (tier >= thermalReactors.length || thermalReactors[tier] == null) continue;
                addMachineRecipe(consumer, "thermal_reactor_" + thermalNames[i],
                        thermalReactors[tier].asStack(),
                        com.gregtechceu.gtceu.api.GTValues.V[tier], 400,
                        hull.apply(tier),
                        new net.minecraft.world.item.ItemStack(
                                gtItem2.apply(thermalNames[i] + "_electric_pump"), 2),
                        new net.minecraft.world.item.ItemStack(
                                gtItem2.apply(thermalNames[i] + "_electric_motor"), 1),
                        ram2);
            }
        }

        // 天文观测站（MV 组装机）
        addMachineRecipe(consumer, "astronomical_observatory_controller",
                org.ringclouds.gtnecore.machine.GtnecoreMachines.ASTRONOMICAL_OBSERVATORY.asStack(),
                com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.MV], 600,
                hull.apply(com.gregtechceu.gtceu.api.GTValues.MV),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("mv_electric_motor"), 1),
                finder == null ? net.minecraft.world.item.ItemStack.EMPTY : finder.asStack(4),
                ram2);
        // 火箭发射台（HV 组装机）
        addMachineRecipe(consumer, "rocket_launch_pad_controller",
                org.ringclouds.gtnecore.machine.GtnecoreMachines.ROCKET_LAUNCH_PAD.asStack(),
                com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.HV], 600,
                hull.apply(com.gregtechceu.gtceu.api.GTValues.HV),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_electric_motor"), 1),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_electric_pump"), 1),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_sensor"), 1));
        // EU 氧分配器 / 重力正常化器（HV 组装机）
        addMachineRecipe(consumer, "eu_oxygen_distributor",
                org.ringclouds.gtnecore.machine.GtnecoreMachines.EU_OXYGEN_DISTRIBUTOR.asStack(),
                com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.HV], 400,
                hull.apply(com.gregtechceu.gtceu.api.GTValues.HV),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_electric_motor"), 1),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_electric_pump"), 1));
        addMachineRecipe(consumer, "eu_gravity_normalizer",
                org.ringclouds.gtnecore.machine.GtnecoreMachines.EU_GRAVITY_NORMALIZER.asStack(),
                com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.HV], 400,
                hull.apply(com.gregtechceu.gtceu.api.GTValues.HV),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_electric_motor"), 1),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_sensor"), 1));
        // 天文维护仓（HV）/ 全能维护仓（EV）
        addMachineRecipe(consumer, "astronomical_maintenance_hatch",
                org.ringclouds.gtnecore.machine.GtnecoreMachines.ASTRONOMICAL_MAINTENANCE_HATCH.asStack(),
                com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.HV], 400,
                hull.apply(com.gregtechceu.gtceu.api.GTValues.HV),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_sensor"), 1));
        addMachineRecipe(consumer, "omni_maintenance_hatch",
                org.ringclouds.gtnecore.machine.GtnecoreMachines.OMNI_MAINTENANCE_HATCH.asStack(),
                com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.EV], 400,
                hull.apply(com.gregtechceu.gtceu.api.GTValues.EV),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("ev_sensor"), 1),
                new net.minecraft.world.item.ItemStack(gtItem2.apply("ev_electric_pump"), 1));

        // ---- 真空泵覆盖板（HV，用户指定：无运行消耗，门槛全在材料）----
        // 语义呼应：ULV 电路就叫"真空管"，所以造真空管要真空环境（见 VacuumRecipeInjector）
        var stainlessPlate = gtItem2.apply("stainless_steel_plate");
        if (stainlessPlate != null && org.ringclouds.gtnecore.item.GtnecoreItems.VACUUM_PUMP_COVER != null) {
            com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES
                    .recipeBuilder("vacuum_pump_cover")
                    .inputItems(new net.minecraft.world.item.ItemStack(stainlessPlate, 4))
                    .inputItems(new net.minecraft.world.item.ItemStack(gtItem2.apply("hv_electric_pump"), 1))
                    .inputItems(com.gregtechceu.gtceu.data.recipe.CustomTags.HV_CIRCUITS, 2)
                    .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.SolderingAlloy.getFluid(144))
                    .outputItems(org.ringclouds.gtnecore.item.GtnecoreItems.VACUUM_PUMP_COVER.asStack())
                    .duration(400)
                    .EUt(com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.HV])
                    .save(consumer);
        }
    }

    /**
     * 组装机机器配方（0→HV 获取链）：输出为空/全输入为空则跳过；EUt 给档位电压。
     * GTCEu 不会为 addon 机器自动生成组装配方，本方法统一补链。
     */
    private static void addMachineRecipe(
            java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe> consumer,
            String name, net.minecraft.world.item.ItemStack output,
            long euPerTick, int duration, net.minecraft.world.item.ItemStack... inputs) {
        if (output.isEmpty()) return;
        var builder = com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES.recipeBuilder(name);
        boolean any = false;
        for (var in : inputs) {
            if (in == null || in.isEmpty()) continue;
            builder.inputItems(in);
            any = true;
        }
        if (!any) return; // 部件缺失（mod 环境变化）→ 整条跳过，避免无意义配方
        builder.outputItems(output).duration(duration).EUt(euPerTick).save(consumer);
    }

    /**
     * 自定义元素（虚构元素）：
     * 注 key = 元素 name，GTElements.get(name) 可查询；元素多余字段（气体/产物）用于 tooltip。
     * 注：GTCEu 原版已有 Sp=Space（无产物/物品）"Spacium"，不重复注册。
     * 元素名称字段（第 5 参）用 null，显示名用第 6 参（避免英文名覆盖）。
     */
    @Override
    public void registerElements() {
        // Godium（神性物质）：质子=中子=666666，质量 -1
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(666666, 666666, -1, null, "Godium", "God", false);
        // Strium（弦）：质子=中子=0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(0, 0, -1, null, "Strium", "Str", false);
        // Timeium（时间元素）：质子=中子=0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(0, 0, -1, null, "Timeium", "Time", false);
        // Soulium（灵魂）：质子 0，中子 21
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(0, 21, -1, null, "Soulium", "Sl", false);
        // Unknown（未知）：质子=中子=0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(0, 0, -1, null, "Unknown", "?", false);
        // Electron（电子）：质子=中子=0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(0, 0, -1, null, "Electron", "e", false);
        // Proton（质子）：质子 1，中子 0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(1, 0, -1, null, "Proton", "p", false);
        // Quark（夸克）：质子=中子=0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(0, 0, -1, null, "Quark", "q", false);
        // Boson（玻色子）：质子=中子=0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(0, 0, -1, null, "Boson", "Bo", false);
        // HiggsBoson（希格斯玻色子）：质子 134，中子 0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(134, 0, -1, null, "HiggsBoson", "h", false);
    }
}