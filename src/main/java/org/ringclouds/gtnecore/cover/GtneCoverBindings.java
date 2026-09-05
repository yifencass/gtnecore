package org.ringclouds.gtnecore.cover;

import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.item.CoverPlaceBehavior;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

/**
 * GTCEu 原生物品的覆盖板档位换绑。
 *
 * GTItems 硬编码了 13 个部件物品（lv..opv），注册时按 GTCovers.PUMPS[0..12] 下标取
 * 定义。GTCoversTiersMixin 把 ALL_TIERS 扩到 1..21 后，PUMPS/CONVEYORS/ROBOT_ARMS/
 * FLUID_REGULATORS 变成 21 项新档序，GTCEu 的 uxv/opv 物品因此错绑到 12/13 档
 * （UMV/SWV）定义——放置后是错档的覆盖板（速率按 UMV/SWV 算）。LV..UIV 的 11 档
 * 绑定恰好正确，不动。
 *
 * 这里在物品全部注册完成后，反射替换 8 个错绑物品（uxv/opv × 泵/传送带/机械臂/
 * 流体调节器）的 CoverPlaceBehavior 组件，换到正确的 15/18 档定义。
 *
 * 绑定时机：与 GtnePartBindings 相同（AddPackFindersEvent SERVER_DATA，LOWEST），
 * 此时全部物品已注册完毕。
 */
public final class GtneCoverBindings {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 错绑的 GTCEu 物品与其正确档位：uxv=15 档、opv=18 档（对应 ALL_TIERS 下标 14/17）。 */
    private record Rebind(String itemId, int tier) {
    }

    private static final Rebind[] REBINDS = {
            new Rebind("uxv_electric_pump", 15), new Rebind("opv_electric_pump", 18),
            new Rebind("uxv_conveyor_module", 15), new Rebind("opv_conveyor_module", 18),
            new Rebind("uxv_robot_arm", 15), new Rebind("opv_robot_arm", 18),
            new Rebind("uxv_fluid_regulator", 15), new Rebind("opv_fluid_regulator", 18),
    };

    private static Field componentsField;

    private GtneCoverBindings() {
    }

    public static void install() {
        for (Rebind rebind : REBINDS) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtceu", rebind.itemId()));
            if (!(item instanceof IComponentItem componentItem)) {
                LOGGER.warn("GTNEcore: 覆盖板换绑找不到/不是组件物品 gtceu:{}，跳过", rebind.itemId());
                continue;
            }
            CoverDefinition[] defs = coverDefs(rebind.itemId());
            if (swap(componentItem, defs[rebind.tier() - 1])) {
                LOGGER.info("GTNEcore: 覆盖板换绑 gtceu:{} → 档位 {} 定义", rebind.itemId(), rebind.tier());
            } else {
                LOGGER.warn("GTNEcore: 覆盖板换绑未找到 CoverPlaceBehavior 组件 gtceu:{}", rebind.itemId());
            }
        }
    }

    /** 按物品名取对应档位定义数组（与 GtnecoreItems.coverDefs 相同的 4 类覆盖部件）。 */
    private static CoverDefinition[] coverDefs(String itemId) {
        if (itemId.contains("electric_pump")) {
            return GTCovers.PUMPS;
        }
        if (itemId.contains("conveyor_module")) {
            return GTCovers.CONVEYORS;
        }
        if (itemId.contains("robot_arm")) {
            return GTCovers.ROBOT_ARMS;
        }
        return GTCovers.FLUID_REGULATORS;
    }

    /** 替换组件列表里的 CoverPlaceBehavior 实例，返回是否找到并替换。 */
    @SuppressWarnings("unchecked")
    private static boolean swap(IComponentItem item, CoverDefinition correct) {
        try {
            List<IItemComponent> list = (List<IItemComponent>) componentsField().get(item);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof CoverPlaceBehavior) {
                    list.set(i, new CoverPlaceBehavior(correct));
                    return true;
                }
            }
        } catch (IllegalAccessException e) {
            LOGGER.error("GTNEcore: 覆盖板换绑反射失败", e);
        }
        return false;
    }

    private static Field componentsField() {
        if (componentsField == null) {
            try {
                componentsField = ComponentItem.class.getDeclaredField("components");
                componentsField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("GTNEcore: 找不到 ComponentItem.components 字段", e);
            }
        }
        return componentsField;
    }
}
