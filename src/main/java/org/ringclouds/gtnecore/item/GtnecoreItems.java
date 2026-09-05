package org.ringclouds.gtnecore.item;

import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.item.CoverPlaceBehavior;
import com.gregtechceu.gtceu.common.item.TooltipBehavior;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.cover.CoverRateScaling;
import org.ringclouds.gtnecore.voltages.GtneVoltages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GTNEcore 补全的高电压档位内容：部件物品（电机/泵/机械臂等 9 类 × 8 档 = 72 个）。
 * GTCEu 原生只注册了 uxv/opv（15/18 档），重排后 12/13/14/16/17/19/20/21 档缺失
 * （21=MAX 连 uxv/opv 也没有，一并补全）。全部物品自动进入 GTNEcore 创造标签页。
 *
 * 覆盖板：GTCEu 的部件物品能当覆盖板，靠注册时挂的 CoverPlaceBehavior 组件
 * （持有对应档位的 CoverDefinition，右键机器面时放置）。这里给 4 类可覆盖部件
 * （泵/传送带/机械臂/流体调节器）同样挂上——定义数组 GTCovers.PUMPS 等按
 * ALL_TIERS(=1..21) 排列，档位 t 的定义在下标 t-1；GTCovers 类在 onRegister
 * 时才访问（此时 GTCovers 必然已初始化，GTCoversTiersMixin 已生效）。
 */
public final class GtnecoreItems {

    /** 需要补全的档位：12..21 全档（GTCEu 只原生注册机器，部件物品一律没有——
     * 15/18=UXV/OpV 也无 gtceu 部件物品，曾误以为有导致组件绑到 air）。 */
    private static final int[] EXTRA_TIERS = {12, 13, 14, 15, 16, 17, 18, 19, 20, 21};

    /** 部件类型：注册名后缀 / 显示名 / GTCEu 部件 tag / 是否可作覆盖板（有档位覆盖板定义）。 */
    private record PartType(String suffix, String display, TagKey<Item> tag, boolean cover) {
    }

    private static final PartType[] PART_TYPES = {
            new PartType("electric_motor", "Electric Motor", CustomTags.ELECTRIC_MOTORS, false),
            new PartType("electric_pump", "Electric Pump", CustomTags.ELECTRIC_PUMPS, true),
            new PartType("fluid_regulator", "Fluid Regulator", CustomTags.FLUID_REGULATORS, true),
            new PartType("conveyor_module", "Conveyor Module", CustomTags.CONVEYOR_MODULES, true),
            new PartType("electric_piston", "Electric Piston", CustomTags.ELECTRIC_PISTONS, false),
            new PartType("robot_arm", "Robot Arm", CustomTags.ROBOT_ARMS, true),
            new PartType("field_generator", "Field Generator", CustomTags.FIELD_GENERATORS, false),
            new PartType("emitter", "Emitter", CustomTags.EMITTERS, false),
            new PartType("sensor", "Sensor", CustomTags.SENSORS, false),
    };

    /** 注册的部件物品（供创造标签页枚举）。 */
    public static final List<ItemEntry<? extends Item>> PARTS = new ArrayList<>();

    /** GT之环：Curios 饰品，头后渲染 GTCEu 青色 G 图标光环。 */
    public static final ItemEntry<GtneHaloItem> GT_HALO = Gtnecore.REGISTRATE
            .item("gt_halo", GtneHaloItem::new)
            .properties(p -> p.stacksTo(1))
            .register();

    /** 天体星图系列：按元素各自注册物品（-none 空白，-al/-au/-cu/-fe/-ni/-sn 对应元素小行星） */
    public static final java.util.Map<String, ItemEntry<ComponentItem>> ASTRAL_CHARTS = new java.util.LinkedHashMap<>();

    /** 已画贴图的星图元素（none = 空） */
    private static final String[] CHART_ELEMENTS = {"none", "al", "au", "cu", "fe", "ni", "sn"};

    /** 火箭系列（MKI~MKV，罗马数字）：1-5 级火箭，星际采矿任务消耗品（配方不消耗） */
    public static final java.util.Map<String, ItemEntry<ComponentItem>> ROCKETS = new java.util.LinkedHashMap<>();

    /** 火箭等级（罗马数字小写） */
    public static final String[] ROCKET_LEVELS = {"i", "ii", "iii", "iv", "v"};

    /** 注册天体星图系列（在 GtnecoreItems.register() 中调用） */
    public static void registerAstralCharts() {
        for (String e : CHART_ELEMENTS) {
            ItemEntry<ComponentItem> entry = Gtnecore.REGISTRATE
                    .item("astral_chart_" + e, ComponentItem::create)
                    .lang("Astral Chart" + (e.equals("none") ? "" : " (" + e.toUpperCase(Locale.ROOT) + ")"))
                    .defaultModel()
                    .register();
            ASTRAL_CHARTS.put(e, entry);
        }
    }

    /** 注册 MKI~MKV 火箭系列（在 GtnecoreItems.register() 中调用） */
    public static void registerRockets() {
        for (String r : ROCKET_LEVELS) {
            ItemEntry<ComponentItem> entry = Gtnecore.REGISTRATE
                    .item("mk" + (java.util.Arrays.asList(ROCKET_LEVELS).indexOf(r) + 1) + "_rocket", ComponentItem::create)
                    .lang("MK" + r.toUpperCase(Locale.ROOT) + " Rocket")
                    .defaultModel()
                    .register();
            ROCKETS.put(r, entry);
        }
    }

    private GtnecoreItems() {
    }

    /** 覆盖板定义数组：按部件后缀取 GTCovers 档位定义（在 onRegister 惰性调用）。 */
    private static CoverDefinition[] coverDefs(PartType type) {
        return switch (type.suffix()) {
            case "electric_pump" -> GTCovers.PUMPS;
            case "fluid_regulator" -> GTCovers.FLUID_REGULATORS;
            case "conveyor_module" -> GTCovers.CONVEYORS;
            case "robot_arm" -> GTCovers.ROBOT_ARMS;
            default -> throw new IllegalArgumentException("not a cover part: " + type.suffix());
        };
    }

    /** 覆盖板速率 tooltip：泵系显示 mB/t、传送带系显示 items/t（按 CoverRateScaling 扩展公式）。 */
    private static Component rateTooltip(PartType type, int tier) {
        return switch (type.suffix()) {
            case "electric_pump", "fluid_regulator" -> Component.translatable(
                    "item.gtnecore.cover.tooltip.rate_fluid", CoverRateScaling.pumpRate(tier));
            case "conveyor_module", "robot_arm" -> Component.translatable(
                    "item.gtnecore.cover.tooltip.rate_item", CoverRateScaling.conveyorRate(tier));
            default -> Component.empty();
        };
    }

    /** 在 REGISTRATE.registerRegistrate() 之前调用。 */
    public static void register() {
        // 部件物品：8 档 × 9 类
        for (int tier : EXTRA_TIERS) {
            String name = GtneVoltages.VN[tier].toLowerCase(Locale.ROOT);
            for (PartType type : PART_TYPES) {
                if (type.cover()) {
                    // 可覆盖部件：ComponentItem + CoverPlaceBehavior + 速率 tooltip。
                    // 定义解析放 onRegister（物品真正注册时 GTCovers 已初始化，此时访问不触发类加载顺序问题）
                    final int index = tier - 1;
                    PARTS.add(Gtnecore.REGISTRATE.item(name + "_" + type.suffix(), ComponentItem::create)
                            .onRegister(item -> item.attachComponents(
                                    new CoverPlaceBehavior(coverDefs(type)[index]),
                                    new TooltipBehavior(list -> list.add(rateTooltip(type, tier)))))
                            .tag(type.tag())
                            .register());
                } else {
                    PARTS.add(Gtnecore.REGISTRATE.item(name + "_" + type.suffix(), Item::new)
                            .tag(type.tag())
                            .register());
                }
            }
        }
        // 天体星图系列（none + 元素）
        registerAstralCharts();
        // MKI~MKV 火箭系列
        registerRockets();
    }
}
