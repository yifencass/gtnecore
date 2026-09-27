package org.ringclouds.gtnecore.planner.gtceu;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.ringclouds.gtnecore.planner.api.PlannerRecipe;
import org.ringclouds.gtnecore.planner.api.PlannerStack;
import org.ringclouds.gtnecore.planner.api.RecipeCategory;
import org.ringclouds.gtnecore.planner.api.RecipeLookup;

import java.util.ArrayList;
import java.util.List;

/**
 * GTCEu 配方适配层——全包唯一 import com.gregtechceu.* 的地方。
 * 逻辑不要求只在客户端：优先客户端 RecipeManager（服务端同步的完整配方表），
 * 客户端不可用时回退服务端世界（服务端也能跑规划逻辑）。
 * 拆成独立 mod 时：换掉本类即可（简化模式用 EmptyRecipeLookup）。
 */
public class GtceRecipeLookup implements RecipeLookup {

    public static final GtceRecipeLookup INSTANCE = new GtceRecipeLookup();

    private static boolean categoriesInitialized;

    private GtceRecipeLookup() {
    }

    @Override
    public List<PlannerRecipe> find(RecipeCategory category, List<PlannerStack> wantedOutputs, List<PlannerStack> providedInputs) {
        ensureCategories();
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            // 服务端兜底（规划逻辑不锁死客户端）
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) level = server.overworld();
        }
        if (level == null) return List.of();
        GTRecipeType type = GTRegistries.RECIPE_TYPES.get(ResourceLocation.tryParse(category.id()));
        if (type == null) return List.of();
        List<PlannerRecipe> result = new ArrayList<>();
        for (GTRecipe r : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(type)) {
            // 某些配方类型（含 addon 自定义）的配方结构 RecipeHelper 无法适配
            // （如无输入/特殊能力配方会抛 Index 0）——跳过该配方而非崩溃
            final GtcePlannerRecipe p;
            try {
                p = new GtcePlannerRecipe(r, category.id());
            } catch (RuntimeException ex) {
                continue;
            }
            if (!wantedOutputs.isEmpty()) {
                if (anyMatch(p.outputs(), wantedOutputs)) result.add(p);
            } else if (!providedInputs.isEmpty()) {
                if (anyMatch(p.inputs(), providedInputs)) result.add(p);
            } else {
                result.add(p);
            }
        }
        return result;
    }

    /** 常见配方类型中文名（动态类型显示用；未收录的显示英文 path） */
    private static final java.util.Map<String, String> CAT_NAMES = new java.util.HashMap<>();

    static {
        CAT_NAMES.put("distillation_tower", "蒸馏塔");
        CAT_NAMES.put("distillery", "蒸馏机");
        CAT_NAMES.put("cracker", "裂解");
        CAT_NAMES.put("assembler", "组装机");
        CAT_NAMES.put("alloy_smelter", "合金炉");
        CAT_NAMES.put("electric_blast_furnace", "电弧高炉");
        CAT_NAMES.put("blast_furnace", "高炉");
        CAT_NAMES.put("macerator", "磨粉机");
        CAT_NAMES.put("chemical_reactor", "化学反应釜");
        CAT_NAMES.put("large_chemical_reactor", "大型化学反应釜");
        CAT_NAMES.put("centrifuge", "离心机");
        CAT_NAMES.put("electrolyzer", "电解机");
        CAT_NAMES.put("thermal_centrifuge", "热离机");
        CAT_NAMES.put("sifter", "筛矿机");
        CAT_NAMES.put("ore_washer", "洗矿机");
        CAT_NAMES.put("extractor", "提取机");
        CAT_NAMES.put("compressor", "压缩机");
        CAT_NAMES.put("extruder", "挤压机");
        CAT_NAMES.put("wiremill", "线材轧机");
        CAT_NAMES.put("bender", "卷板机");
        CAT_NAMES.put("cutter", "切割机");
        CAT_NAMES.put("lathe", "车床");
        CAT_NAMES.put("forming_press", "冲压机");
        CAT_NAMES.put("packer", "打包机");
        CAT_NAMES.put("unpacker", "拆包机");
        CAT_NAMES.put("mixer", "搅拌机");
        CAT_NAMES.put("autoclave", "高压釜");
        CAT_NAMES.put("microwave", "微波炉");
        CAT_NAMES.put("fermenter", "发酵机");
        CAT_NAMES.put("fluid_heater", "流体加热器");
        CAT_NAMES.put("fluid_solidifier", "流体固化机");
        CAT_NAMES.put("fluid_canner", "流体罐装机");
        CAT_NAMES.put("battery_charger", "充电器");
        CAT_NAMES.put("engraver", "激光刻印机");
        CAT_NAMES.put("circuit_assembler", "电路组装机");
        CAT_NAMES.put("scanner", "扫描仪");
        CAT_NAMES.put("laser_engraver", "激光刻蚀机");
        CAT_NAMES.put("brewery", "酿造机");
        CAT_NAMES.put("rock_crusher", "碎石机");
        CAT_NAMES.put("polarizer", "磁化机");
        CAT_NAMES.put("combustion_generator", "燃烧发电机");
        CAT_NAMES.put("gas_turbine", "燃气轮机");
        CAT_NAMES.put("steam_turbine", "蒸汽轮机");
        CAT_NAMES.put("plasma_generator", "等离子发电机");
        CAT_NAMES.put("fusion_reactor", "聚变反应堆");
        CAT_NAMES.put("coke_oven", "焦炉");
        CAT_NAMES.put("primitive_blast_furnace", "原始高炉");
        CAT_NAMES.put("assembly_line", "装配线");
        CAT_NAMES.put("research_scanning", "研究扫描");
        CAT_NAMES.put("research_station", "研究站");
        CAT_NAMES.put("chemical_bath", "化学浸洗");
        CAT_NAMES.put("electromagnetic_separator", "电磁分离机");
        CAT_NAMES.put("dryer", "烘干机");
        CAT_NAMES.put("slicer", "切片机");
        CAT_NAMES.put("milling", "铣床");
        CAT_NAMES.put("alloy_blast_furnace", "合金高炉");
        CAT_NAMES.put("vacuum_freezer", "真空冷冻机");
        CAT_NAMES.put("gas_collector", "气体收集器");
        CAT_NAMES.put("arc_furnace", "电弧炉");
        CAT_NAMES.put("extruder", "挤压机");
        CAT_NAMES.put("assembly_line", "装配线");
    }

    /**
     * 枚举 GT 全部配方类型（含其他 mod 的自定义类型）填充 RecipeCategory。
     * 只跑一次（保留内置默认，重复 of 返回原实例），懒加载（打开规划器时注册表已就绪）。
     */
    private static void ensureCategories() {
        if (categoriesInitialized) return;
        categoriesInitialized = true;
        for (GTRecipeType t : GTRegistries.RECIPE_TYPES.values()) {
            if (t.registryName == null) continue;
            String path = t.registryName.getPath();
            RecipeCategory.of(t.registryName.toString(), CAT_NAMES.getOrDefault(path, path));
        }
    }

    private static boolean anyMatch(List<PlannerStack> pool, List<PlannerStack> wanted) {
        for (PlannerStack w : wanted) {
            for (PlannerStack p : pool) {
                if (p.sameType(w)) return true;
            }
        }
        return false;
    }
}
