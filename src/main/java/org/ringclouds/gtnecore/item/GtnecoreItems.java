package org.ringclouds.gtnecore.item;

import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.voltages.GtneVoltages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GTNEcore 补全的高电压档位内容：部件物品（电机/泵/机械臂等 9 类 × 7 档 = 63 个）。
 * GTCEu 原生只注册了 uxv/opv/max（15/18/21 档），重排后 12/13/14/16/17/19/20 档缺失。
 * 全部物品自动进入 GTNEcore 创造标签页。
 */
public final class GtnecoreItems {

    /** 需要补全的档位：12=UMV, 13=SWV, 14=GCV, 16=QGV, 17=CYV, 19=VEV, 20=SGV
     * （15/18/21 = UXV/OpV/MAX 由 GTCEu 原生注册，名字恰好是重排后的档位名）。 */
    private static final int[] EXTRA_TIERS = {12, 13, 14, 16, 17, 19, 20};

    /** 部件类型：注册名后缀 / 显示名 / GTCEu 部件 tag。 */
    private record PartType(String suffix, String display, TagKey<Item> tag) {
    }

    private static final PartType[] PART_TYPES = {
            new PartType("electric_motor", "Electric Motor", CustomTags.ELECTRIC_MOTORS),
            new PartType("electric_pump", "Electric Pump", CustomTags.ELECTRIC_PUMPS),
            new PartType("fluid_regulator", "Fluid Regulator", CustomTags.FLUID_REGULATORS),
            new PartType("conveyor_module", "Conveyor Module", CustomTags.CONVEYOR_MODULES),
            new PartType("electric_piston", "Electric Piston", CustomTags.ELECTRIC_PISTONS),
            new PartType("robot_arm", "Robot Arm", CustomTags.ROBOT_ARMS),
            new PartType("field_generator", "Field Generator", CustomTags.FIELD_GENERATORS),
            new PartType("emitter", "Emitter", CustomTags.EMITTERS),
            new PartType("sensor", "Sensor", CustomTags.SENSORS),
    };

    /** 注册的部件物品（供创造标签页枚举）。 */
    public static final List<ItemEntry<? extends Item>> PARTS = new ArrayList<>();

    private GtnecoreItems() {
    }

    /** 在 REGISTRATE.registerRegistrate() 之前调用。 */
    public static void register() {
        // 部件物品：7 档 × 9 类
        for (int tier : EXTRA_TIERS) {
            String name = GtneVoltages.VN[tier].toLowerCase(Locale.ROOT);
            for (PartType type : PART_TYPES) {
                PARTS.add(Gtnecore.REGISTRATE.item(name + "_" + type.suffix(), Item::new)
                        // 显示名走 lang 文件（键 item.gtnecore.<档名>_<类型>），本地化由语言文件提供
                        .tag(type.tag())
                        .register());
            }
        }
    }
}
