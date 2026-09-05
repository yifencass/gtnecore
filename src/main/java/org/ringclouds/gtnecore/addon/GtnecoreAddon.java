package org.ringclouds.gtnecore.addon;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

/**
 * GTNEcore addon 入口（GTCEuAPI 发现）。
 */
@GTAddon
public class GtnecoreAddon implements IGTAddon {

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
        for (String[] chart : chartMaterials) {
            // 发射台任务档数 = 该元素矿石材料数（数据包配方 rocket_launch/*_c<N>.json 为准，
            // 与 cu/fe"一矿一电路"惯例一致）：sn/ni 三档、au 单档（金改 HV 512），
            // 其余 0..4；al 的 c5（raw_pyrope）由数据包配方独占，代码不生成。
            // 删档/缩档必须同步这里，否则孤儿 dust 配方会让已删档位复活。
            int[] circuits = switch (chart[0]) {
                case "sn", "ni" -> new int[] {0, 1, 2};
                case "au" -> new int[] {0};
                default -> new int[] {0, 1, 2, 3, 4};
            };
            var chartEntry = org.ringclouds.gtnecore.item.GtnecoreItems.ASTRAL_CHARTS.get(chart[0]);
            if (chartEntry == null) continue;
            var material = com.gregtechceu.gtceu.common.data.GTMaterials.get(chart[1]);
            if (material == null) continue;
            var dustEntry = com.gregtechceu.gtceu.common.data.GTMaterialItems.MATERIAL_ITEMS.get(
                    com.gregtechceu.gtceu.api.data.tag.TagPrefix.dust, material);
            if (dustEntry == null) continue;
            long eut = chart[0].equals("al")
                    ? com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.HV]
                    : com.gregtechceu.gtceu.api.GTValues.V[com.gregtechceu.gtceu.api.GTValues.MV];
            for (int i = 0; i < circuits.length; i++) {
                int mult = circuits[i] == 4 ? 4 : 1;
                rocketType.recipeBuilder("gtnecore_launch_" + chart[0] + "_c" + circuits[i])
                        .notConsumable(mkiRocket.asStack())
                        .notConsumable(chartEntry.asStack())
                        .notConsumable(com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient.of(circuits[i]))
                        .inputFluids(com.gregtechceu.gtceu.common.data.GTMaterials.Hydrogen.getFluid(32000))
                        .outputItems(dustEntry.asStack(mult))
                        .duration(durations[i]).EUt(eut)
                        .save(consumer);
            }
        }

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
        // Infinite（无尽）：质子 1729，中子 0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(1729, 0, -1, null, "Infinite", "If", false);
        // Matrix（矩阵）：质子=中子=0
        com.gregtechceu.gtceu.common.data.GTElements.createAndRegister(0, 0, -1, null, "Matrix", "Mx", false);
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