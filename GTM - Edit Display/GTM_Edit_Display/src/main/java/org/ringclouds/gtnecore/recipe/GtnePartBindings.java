package org.ringclouds.gtnecore.recipe;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.data.recipe.CraftingComponent;
import com.gregtechceu.gtceu.data.recipe.GTCraftingComponents;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.ringclouds.gtnecore.voltages.GtneVoltages;
import org.slf4j.Logger;

import java.util.Locale;

/**
 * 部件组件（电机/泵/活塞/传送带/机械臂/发射器/传感器/场发生器）的档位重绑。
 *
 * GTCEu 的 GTCraftingComponents 按原版索引把 12..14 档绑定到 UXV/OpV/MAX 档物品，
 * 且 15..21 档无绑定（槽位 null，get(tier) 返回 fallback）。GTNEcore 重排档位后：
 * - 12=UMV / 13=SWV / 14=GCV 机器配方吃到原版 UXV/OpV/MAX 档部件（物品名错位）
 * - 15..21（UXV..MAX）机器配方没有部件输入
 * - GtnecoreItems 补的 7 档物品只进了 tag，没进机器配方
 *
 * 这里把 8 类部件组件的 12..21 档重绑到正确物品：
 * - 15/18 档（UXV/OpV）：gtceu 原版物品（electric_motor_uxv 等，注册名恰好是
 *   重排后的档位名，GtnecoreItems 注释确认）
 * - 其余档（含 21=MAX）：gtnecore 补的物品（umv_electric_motor、max_electric_motor 等，
 *   GtnecoreItems 注册）
 *
 * 绑定时机与 GtneCircuitTags 相同（AddPackFindersEvent SERVER_DATA，LOWEST）：
 * 此时 GTCEu 已完成 GTCraftingComponents.init()，且全部物品注册完毕。
 *
 * 机壳（CASING）同样重绑：GTCEu 的 CASING 组件按原版索引绑定 GTBlocks.MACHINE_CASING_UXV
 * 等写死字段（12..14 档错位、15..21 无绑定）。GTNEcore 已补注册 umv/swv/gcv/qgv/cyv/vev/sgv
 * 机壳（GtnecoreBlocks，gtceu 命名空间，贴图/lang 已就位），按新档名逐档重绑。
 * 外壳（HULL）组件绑定 GTMachines.HULL 数组（运行时按 patch 后的 22 档 ALL_TIERS 注册，
 * 注册名即新档名 machine_hull_umv 等，12..14 档自动正确），只需补绑 15..21 档。
 */
public final class GtnePartBindings {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 部件组件与其 gtceu 原版物品名后缀。 */
    private record Part(CraftingComponent component, String suffix) {
    }

    private static final Part[] PARTS = {
            new Part(GTCraftingComponents.MOTOR, "electric_motor"),
            new Part(GTCraftingComponents.PUMP, "electric_pump"),
            new Part(GTCraftingComponents.PISTON, "electric_piston"),
            new Part(GTCraftingComponents.CONVEYOR, "conveyor_module"),
            new Part(GTCraftingComponents.ROBOT_ARM, "robot_arm"),
            new Part(GTCraftingComponents.EMITTER, "emitter"),
            new Part(GTCraftingComponents.SENSOR, "sensor"),
            new Part(GTCraftingComponents.FIELD_GENERATOR, "field_generator"),
    };

    /** 已无 gtceu 原生部件物品：12..21 全部走 gtnecore 补注册物品（含 UXV/OpV）。 */
    private static final int[] GTCEU_TIERS = {};

    private GtnePartBindings() {
    }

    /** 重绑 12..21 档部件组件 + 机壳/外壳组件（在 GtneCircuitTags.install() 之后调用）。 */
    public static void install() {
        // 8 类部件：15/18/21 档用 gtceu 原版物品（electric_motor_uxv 等），其余用 gtnecore 补的物品
        for (int tier = 12; tier <= 21; tier++) {
            String tierName = GtneVoltages.VN[tier].toLowerCase(Locale.ROOT);
            boolean gtceu = isGtceuTier(tier);
            String namespace = gtceu ? "gtceu" : "gtnecore";
            for (Part part : PARTS) {
                String itemId = tierName + "_" + part.suffix(); // gtnecore 物品统一"档名_部件"序
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, itemId));
                if (item == null) {
                    LOGGER.warn("GTNEcore: 部件重绑找不到物品 {}:{}，跳过", namespace, itemId);
                    continue;
                }
                part.component().add(tier, new ItemStack(item));
            }
        }
        LOGGER.info("GTNEcore: 部件组件 12..21 档已重绑（{} 类 × 10 档）", PARTS.length);
        // 机壳（CASING）：gtceu 命名空间。id 顺序是 <档>_machine_casing（如 umv_machine_casing），
        // 原 machine_casing_<档> 拼法全部查空 → air 被静默写入组件（CASING.get(15)=air 的根因）
        for (int tier = 12; tier <= 21; tier++) {
            String itemId = GtneVoltages.VN[tier].toLowerCase(Locale.ROOT) + "_machine_casing";
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtceu", itemId));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                LOGGER.warn("GTNEcore: 机壳重绑找不到物品 gtceu:{}，跳过", itemId);
                continue;
            }
            GTCraftingComponents.CASING.add(tier, new ItemStack(item));
        }
        LOGGER.info("GTNEcore: 机壳组件 12..21 档已重绑");
        // 外壳（HULL）：绑定 GTMachines.HULL 数组运行时元素（12..14 自动正确），补绑 15..21
        for (int tier = 15; tier <= 21; tier++) {
            GTCraftingComponents.HULL.add(tier, GTMachines.HULL[tier].asStack());
        }
        LOGGER.info("GTNEcore: 外壳组件 15..21 档已补绑");

        // ===== 配方组件 10..21 档（"神秘材料"修复）=====
        // 原版 GTCraftingComponents：PLATE/CABLE 只绑到 9（UHV：中子素板/铕电缆），
        // 10..21 全走 fallback（铁板/红石合金电缆——JEI 里神秘材料的来源）；
        // CASING 等带 isHighTier 的按原版索引 12..14 绑定，22 档重编号后错位到 UMV/SWV/GCV。
        // 映射（2026-08-27 用户声明 + 填充）：
        //   电缆/线缆系 10..16 = 铕（弦锭 CYV 前的最高导体）；17..21（CYV..MAX）= 弦
        //   （弦电缆即 MAX 超导体——用户声明）
        //   板 10..16 = 中子素；17..21 = 弦板（CYV 主材料弦锭——用户声明，18..21 延续弦系）
        //   内衬板（HULL_PLATE）10..21 = PBI（延续 9 档顶配聚合物）
        final int STRIUM_TIER = 17; // CYV（GtneVoltages.VN[17]，GTValues 无新档常量）
        com.gregtechceu.gtceu.api.data.chemical.material.Material strium =
                org.ringclouds.gtnecore.material.GtneMaterials.STRUM;
        for (int tier = GTValues.UEV; tier <= GTValues.MAX; tier++) {
            boolean striumEra = tier >= STRIUM_TIER; // CYV：弦系起点
            var wireMat = striumEra ? strium : com.gregtechceu.gtceu.common.data.GTMaterials.Europium;
            var plateMat = striumEra ? strium : com.gregtechceu.gtceu.common.data.GTMaterials.Neutronium;
            // 弦是超导体：GT 只生成裸线（wire）物品、无绝缘电缆（cable）——
            // 弦世代 CABLE 组件绑 wireGt* 前缀（超导裸线即电缆），铕世代用 cableGt*
            com.gregtechceu.gtceu.api.data.tag.TagPrefix single = striumEra
                    ? com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtSingle
                    : com.gregtechceu.gtceu.api.data.tag.TagPrefix.cableGtSingle;
            com.gregtechceu.gtceu.api.data.tag.TagPrefix dbl = striumEra
                    ? com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtDouble
                    : com.gregtechceu.gtceu.api.data.tag.TagPrefix.cableGtDouble;
            com.gregtechceu.gtceu.api.data.tag.TagPrefix quad = striumEra
                    ? com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtQuadruple
                    : com.gregtechceu.gtceu.api.data.tag.TagPrefix.cableGtQuadruple;
            com.gregtechceu.gtceu.api.data.tag.TagPrefix oct = striumEra
                    ? com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtOctal
                    : com.gregtechceu.gtceu.api.data.tag.TagPrefix.cableGtOctal;
            com.gregtechceu.gtceu.api.data.tag.TagPrefix hex = striumEra
                    ? com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtHex
                    : com.gregtechceu.gtceu.api.data.tag.TagPrefix.cableGtHex;
            GTCraftingComponents.CABLE.add(tier, single, wireMat);
            GTCraftingComponents.CABLE_DOUBLE.add(tier, dbl, wireMat);
            GTCraftingComponents.CABLE_QUAD.add(tier, quad, wireMat);
            GTCraftingComponents.CABLE_OCT.add(tier, oct, wireMat);
            GTCraftingComponents.CABLE_HEX.add(tier, hex, wireMat);
            GTCraftingComponents.WIRE_ELECTRIC.add(tier, com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtSingle, wireMat);
            GTCraftingComponents.WIRE_QUAD.add(tier, com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtQuadruple, wireMat);
            GTCraftingComponents.WIRE_OCT.add(tier, com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtOctal, wireMat);
            GTCraftingComponents.WIRE_HEX.add(tier, com.gregtechceu.gtceu.api.data.tag.TagPrefix.wireGtHex, wireMat);
            GTCraftingComponents.PLATE.add(tier, com.gregtechceu.gtceu.api.data.tag.TagPrefix.plate, plateMat);
            GTCraftingComponents.HULL_PLATE.add(tier, com.gregtechceu.gtceu.api.data.tag.TagPrefix.plate,
                    com.gregtechceu.gtceu.common.data.GTMaterials.Polybenzimidazole);
        }
        LOGGER.info("GTNEcore: 配方组件 10..21 档已绑（电缆/板：铕+中子素 → CYV 起弦系列）");

        // 验证：读回绑定值（若 GTCEu 的 init() 后于本方法执行，此处会打印旧值/空）
        LOGGER.info("GTNEcore: 部件绑定验证 HULL.get(15)={} PUMP.get(12)={} CASING.get(15)={} CABLE.get(17)={}",
                GTCraftingComponents.HULL.get(15), GTCraftingComponents.PUMP.get(12),
                GTCraftingComponents.CASING.get(15), GTCraftingComponents.CABLE.get(17));
    }

    private static boolean isGtceuTier(int tier) {
        for (int t : GTCEU_TIERS) {
            if (t == tier) {
                return true;
            }
        }
        return false;
    }
}
