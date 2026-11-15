package org.ringclouds.gtnecore.recipe;

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
 * - 15/18/21 档（UXV/OpV/MAX）：gtceu 原版物品（electric_motor_uxv 等，注册名恰好是
 *   重排后的档位名，GtnecoreItems 注释确认）
 * - 其余档：gtnecore 补的物品（umv_electric_motor 等，GtnecoreItems 注册）
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

    /** GTCEu 原版注册了这些档位名的部件物品（UXV/OpV/MAX 恰为重排后的 15/18/21 档）。 */
    private static final int[] GTCEU_TIERS = {15, 18, 21};

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
                String itemId = gtceu ? part.suffix() + "_" + tierName : tierName + "_" + part.suffix();
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, itemId));
                if (item == null) {
                    LOGGER.warn("GTNEcore: 部件重绑找不到物品 {}:{}，跳过", namespace, itemId);
                    continue;
                }
                part.component().add(tier, new ItemStack(item));
            }
        }
        LOGGER.info("GTNEcore: 部件组件 12..21 档已重绑（{} 类 × 10 档）", PARTS.length);
        // 机壳（CASING）：gtceu 命名空间，15/18/21 档为原版 machine_casing_uxv/opv/max，其余为补注册方块
        for (int tier = 12; tier <= 21; tier++) {
            String itemId = "machine_casing_" + GtneVoltages.VN[tier].toLowerCase(Locale.ROOT);
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtceu", itemId));
            if (item == null) {
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
        // 验证：读回绑定值（若 GTCEu 的 init() 后于本方法执行，此处会打印旧值/空）
        LOGGER.info("GTNEcore: 部件绑定验证 HULL.get(15)={} PUMP.get(12)={} CASING.get(15)={}",
                GTCraftingComponents.HULL.get(15), GTCraftingComponents.PUMP.get(12),
                GTCraftingComponents.CASING.get(15));
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
