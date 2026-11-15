package org.ringclouds.gtnecore.material;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.common.data.GTElements;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.mojang.logging.LogUtils;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.voltages.GtneVoltages;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_PLATE;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_GEAR;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_SMALL_GEAR;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_RING;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_FOIL;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_ROUND;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.GEM_HORIZONTAL;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.METALLIC;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.SHINY;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.DULL;

/**
 * GTNEcore 自定义材料。
 *
 * 注册流程（GTCEu 7.5.3 实测，addon 材料不走 IGTAddon 回调——jar 内无人调用
 * registerMaterials()，必须走 mod 总线事件）：
 * 1. MaterialRegistryEvent（mod 总线）：GTCEuAPI.materialManager.createRegistry(modid)
 *    —— Material.Builder 按 id 命名空间取注册表（getRegistry(getModid())），
 *    不创建会 NPE
 * 2. MaterialEvent（mod 总线）：new Material.Builder(...).buildAndRegister()
 *    —— 触发时机在 GTMaterials.init() 之后、closeRegistries 之前（元素已就绪）
 * 3. PostMaterialEvent（mod 总线）：已有物品归类（ItemMaterialData，Supplier 惰性）
 *
 * 物品/流体/方块由 GTCEu 的 GTMaterialItems 流程自动生成（gtnecore 命名空间）。
 */
public final class GtneMaterials {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static Material TIMEIUM;

    public static Material MAGIC;
    public static Material SOUL;
    public static Material UNKNOWN;
    public static Material MANO;
    /** 弦（Strium）：纯弦构成的原子，超导材料（锭 + MAX 电压超导电缆）。 */
    public static Material STRUM;


    /** 族统合物质：下标 0..17 = 族 1..18，18 = 镧系，19 = 锕系。 */
    public static final Material[] UNIFIED_MATTER = new Material[20];

    /**
     * 归一物质：由**族统合物质**按族顺序组成，每族 4 份，共 20 × 4 = 80 份。
     *
     * <p>
     * 注意：**不是**把元素周期表 118 个元素各取 4 份。归一物质保留"族"这一层结构 ——
     * 它由 {@link #UNIFIED_MATTER} 的 20 种族统合物质构成，每种 4 个。
     * 想要元素级别时，先分解成族统合物质，再各自分解成元素（族统合物质本身可分解）。
     */
    public static Material NORMALIZED;

    /** 基本粒子粉（测试用）：夸克/玻色子/希格斯玻色子/电子/质子。 */
    public static Material QUARK;
    public static Material BOSON;
    public static Material HIGGS_BOSON;
    public static Material ELECTRON;
    public static Material PROTON;
    private GtneMaterials() {
    }

    /** MaterialRegistryEvent（mod 总线）：创建 gtnecore 材料注册表。 */
    public static void createRegistry(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(Gtnecore.MODID);
    }

    /** MaterialEvent（mod 总线）：注册材料。 */
    public static void register(MaterialEvent event) {
        TIMEIUM = new Material.Builder(Gtnecore.id("timeium"))
                .langValue("Timeium")
                .element(GTElements.get("Timeium"))
                .dust().ingot().fluid()
                .flags(GENERATE_PLATE)
                .color(0xFF9100)
                .secondaryColor(0xFFD500)
                .iconSet(METALLIC)
                .buildAndRegister();

        // Infinity / Crystal Matrix / Matrix 材料与 If / Mx 元素均不再由 GTNEcore 注册：
        // 无尽材料与矩阵材料改由整合包中的 avaritia_integration 等 mod 提供。

        // Magic：补上原版只有元素（GTElements.Ma=Magic）没有材料的空缺，宝石+粉+流体
        MAGIC = new Material.Builder(Gtnecore.id("magic"))
                .langValue("Magic")
                .element(GTElements.get("Magic"))
                .gem().dust().fluid()
                .color(0xA020F0)
                .iconSet(GEM_HORIZONTAL)
                .buildAndRegister();

        // Soul：灵魂材料（Soulium 元素），青绿呼应液态灵魂能量 #4FE3C1
        SOUL = new Material.Builder(Gtnecore.id("soul"))
                .langValue("Soul")
                .element(GTElements.get("Soulium"))
                .dust().fluid()
                .color(0x4FE3C1)
                .iconSet(SHINY)
                .buildAndRegister();

        // Unknown：问号材料（? 元素），无形态纯成分占位。
        // 无成分的元素材料 → GT 分解生成器不会为它产出任何东西（电解/离心天然不产出）
        UNKNOWN = new Material.Builder(Gtnecore.id("unknown"))
                .langValue("Unknown")
                .element(GTElements.get("Unknown"))
                .buildAndRegister();
        MANO = new Material.Builder(Gtnecore.id("mano"))
                .langValue("Mano")
                .element(GTElements.get("Manoium"))
                .buildAndRegister();

        // 弦（Strium）：纯弦构成的原子（元素 Str），锭/粉/流体 + 超导电缆。
        // cableProperties(V[MAX], 32A, 无损耗, 超导)：GTCEu 自动生成 wire_strium/cable_strium
        // （GTMaterialBlocks.generateCableBlocks 按 WireProperties 生成，电压上限 = V[21]；
        // 1x 导线电流 = amperage × Insulation 倍率(1x=1) = 32A）。
        // 贴图白色：超弦（纯白光）意象。
        //
        // 变体套（同氢材料的 EXT2_METAL 写法）：棒/长杆/螺栓/螺丝/齿轮/小齿轮/环/箔/圆盘。
        // STRUM 带 .ingot()，所以环/箔/圆盘（配方前置 = INGOT）也能完整生成。
        STRUM = new Material.Builder(Gtnecore.id("strium"))
                .langValue("String")
                .element(GTElements.get("Strium"))
                .dust().ingot().fluid()
                .appendFlags(GTMaterials.EXT2_METAL, GENERATE_RING, GENERATE_FOIL, GENERATE_GEAR,
                        GENERATE_SMALL_GEAR, GENERATE_ROUND)
                .color(0xFFFFFF)
                .secondaryColor(0xF0F0F0)
                .iconSet(SHINY)
                .cableProperties(GtneVoltages.V[GtneVoltages.MAX_TIER], 32, 0, true, 0)
                .buildAndRegister();

        registerUnifiedMatter();
        registerParticleDusts();
    }

    /**
     * 基本粒子粉（测试用，不考虑合理性）：夸克/玻色子/希格斯玻色子/电子/质子，
     * 各注册纯 dust 形态（无成分无配方，创造获取）。
     */
    private static void registerParticleDusts() {
        QUARK = new Material.Builder(Gtnecore.id("quark"))
                .langValue("Quark").dust().color(0x8B5CF6).iconSet(SHINY)
                .element(GTElements.get("Quark")).buildAndRegister();
        BOSON = new Material.Builder(Gtnecore.id("boson"))
                .langValue("Boson").dust().color(0x38BDF8).iconSet(SHINY)
                .element(GTElements.get("Boson")).buildAndRegister();
        HIGGS_BOSON = new Material.Builder(Gtnecore.id("higgsboson"))
                .langValue("Higgs Boson").dust().color(0xFB7185).iconSet(SHINY)
                .element(GTElements.get("HiggsBoson")).buildAndRegister();
        ELECTRON = new Material.Builder(Gtnecore.id("electron"))
                .langValue("Electron").dust().color(0x2DD4BF).iconSet(SHINY)
                .element(GTElements.get("Electron")).buildAndRegister();
        PROTON = new Material.Builder(Gtnecore.id("proton"))
                .langValue("Proton").dust().color(0xF87171).iconSet(SHINY)
                .element(GTElements.get("Proton")).buildAndRegister();
        LOGGER.info("GTNEcore: 注册 5 个基本粒子粉（测试）");
    }

    // ---------------- 族统合物质（元素周期表 18 族 + 镧系 + 锕系） ----------------

    /** 族 1..18 的统合物质注册名 / 显示名（英文）/ 颜色。 */
    private static final String[] GROUP_IDS = {
            "group1_matter", "group2_matter", "group3_matter", "group4_matter",
            "group5_matter", "group6_matter", "group7_matter", "group8_matter",
            "group9_matter", "group10_matter", "group11_matter", "group12_matter",
            "group13_matter", "group14_matter", "group15_matter", "group16_matter",
            "group17_matter", "group18_matter", "lanthanide_matter", "actinide_matter"
    };

    private static final String[] GROUP_NAMES = {
            "Group 1 Unified Matter", "Group 2 Unified Matter", "Group 3 Unified Matter",
            "Group 4 Unified Matter", "Group 5 Unified Matter", "Group 6 Unified Matter",
            "Group 7 Unified Matter", "Group 8 Unified Matter", "Group 9 Unified Matter",
            "Group 10 Unified Matter", "Group 11 Unified Matter", "Group 12 Unified Matter",
            "Group 13 Unified Matter", "Group 14 Unified Matter", "Group 15 Unified Matter",
            "Group 16 Unified Matter", "Group 17 Unified Matter", "Group 18 Unified Matter",
            "Lanthanide Unified Matter", "Actinide Unified Matter"
    };

    /** 20 种统合物质的配色（色相渐变；族 1/2/17/18 用俗名色系）。 */
    private static final int[] GROUP_COLORS = {
            0xC0C0C0, 0xCCCC99, 0x99CCCC, 0x6699CC, 0x6699FF, 0x6666FF,
            0x9966CC, 0xCC66CC, 0xCC6699, 0xCC9966, 0xCCCC66, 0x99CC66,
            0x66CC66, 0x66CC99, 0x66CCCC, 0x66BBCC, 0x99CC33, 0xCCFF66,
            0xBB77DD, 0xDD7777
    };

    /**
     * 注册 20 种族统合物质：族内全部现实元素按 1:1 混合的粉体材料。
     * 成分 = 元素单质材料（MaterialStack 1:1），GTCEu 自动生成化学式与电解分解配方。
     * 形态只做粉（用户确认）；获取配方后续再定。
     *
     * 注意：族内元素数组必须在方法内构建，不能做静态字段——GtneMaterials.<clinit>
     * 在 MaterialRegistryEvent 阶段就触发（createRegistry 监听），此时 GTMaterials
     * 的字段尚未赋值（赋值发生在 GTMaterials.init() 里），静态初始化会把 null 固化，
     * 导致 buildAndRegister 对成分做 HAZARD 检查时 NPE（Material.java:1850）。
     */
    private static void registerUnifiedMatter() {
        // 族内元素（GTCEu 单质材料）：下标 0..17 = 族 1..18，18 = 镧系，19 = 锕系。
        // 同位素归母元素；U/Pu 用最稳定的 U238/Pu239（GTCEu 无纯 U/Pu 单质材料）。
        Material[][] groupElements = {
                {GTMaterials.Hydrogen, GTMaterials.Lithium, GTMaterials.Sodium, GTMaterials.Potassium,
                        GTMaterials.Rubidium, GTMaterials.Caesium, GTMaterials.Francium},
                {GTMaterials.Beryllium, GTMaterials.Magnesium, GTMaterials.Calcium, GTMaterials.Strontium,
                        GTMaterials.Barium, GTMaterials.Radium},
                {GTMaterials.Scandium, GTMaterials.Yttrium},
                {GTMaterials.Titanium, GTMaterials.Zirconium, GTMaterials.Hafnium, GTMaterials.Rutherfordium},
                {GTMaterials.Vanadium, GTMaterials.Niobium, GTMaterials.Tantalum, GTMaterials.Dubnium},
                {GTMaterials.Chromium, GTMaterials.Molybdenum, GTMaterials.Tungsten, GTMaterials.Seaborgium},
                {GTMaterials.Manganese, GTMaterials.Technetium, GTMaterials.Rhenium, GTMaterials.Bohrium},
                {GTMaterials.Iron, GTMaterials.Ruthenium, GTMaterials.Osmium, GTMaterials.Hassium},
                {GTMaterials.Cobalt, GTMaterials.Rhodium, GTMaterials.Iridium, GTMaterials.Meitnerium},
                {GTMaterials.Nickel, GTMaterials.Palladium, GTMaterials.Platinum, GTMaterials.Darmstadtium},
                {GTMaterials.Copper, GTMaterials.Silver, GTMaterials.Gold, GTMaterials.Roentgenium},
                {GTMaterials.Zinc, GTMaterials.Cadmium, GTMaterials.Mercury, GTMaterials.Copernicium},
                {GTMaterials.Boron, GTMaterials.Aluminium, GTMaterials.Gallium, GTMaterials.Indium,
                        GTMaterials.Thallium, GTMaterials.Nihonium},
                {GTMaterials.Carbon, GTMaterials.Silicon, GTMaterials.Germanium, GTMaterials.Tin,
                        GTMaterials.Lead, GTMaterials.Flerovium},
                {GTMaterials.Nitrogen, GTMaterials.Phosphorus, GTMaterials.Arsenic, GTMaterials.Antimony,
                        GTMaterials.Bismuth, GTMaterials.Moscovium},
                {GTMaterials.Oxygen, GTMaterials.Sulfur, GTMaterials.Selenium, GTMaterials.Tellurium,
                        GTMaterials.Polonium, GTMaterials.Livermorium},
                {GTMaterials.Fluorine, GTMaterials.Chlorine, GTMaterials.Bromine, GTMaterials.Iodine,
                        GTMaterials.Astatine, GTMaterials.Tennessine},
                {GTMaterials.Helium, GTMaterials.Neon, GTMaterials.Argon, GTMaterials.Krypton,
                        GTMaterials.Xenon, GTMaterials.Radon, GTMaterials.Oganesson},
                {GTMaterials.Lanthanum, GTMaterials.Cerium, GTMaterials.Praseodymium, GTMaterials.Neodymium,
                        GTMaterials.Promethium, GTMaterials.Samarium, GTMaterials.Europium, GTMaterials.Gadolinium,
                        GTMaterials.Terbium, GTMaterials.Dysprosium, GTMaterials.Holmium, GTMaterials.Erbium,
                        GTMaterials.Thulium, GTMaterials.Ytterbium, GTMaterials.Lutetium},
                {GTMaterials.Actinium, GTMaterials.Thorium, GTMaterials.Protactinium, GTMaterials.Uranium238,
                        GTMaterials.Neptunium, GTMaterials.Plutonium239, GTMaterials.Americium, GTMaterials.Curium,
                        GTMaterials.Berkelium, GTMaterials.Californium, GTMaterials.Einsteinium, GTMaterials.Fermium,
                        GTMaterials.Mendelevium, GTMaterials.Nobelium, GTMaterials.Lawrencium}
        };
        for (int i = 0; i < groupElements.length; i++) {
            Material[] elements = groupElements[i];
            MaterialStack[] stacks = new MaterialStack[elements.length];
            for (int j = 0; j < elements.length; j++) {
                stacks[j] = new MaterialStack(elements[j], 1);
            }
            UNIFIED_MATTER[i] = new Material.Builder(Gtnecore.id(GROUP_IDS[i]))
                    .langValue(GROUP_NAMES[i])
                    .dust()
                    .color(GROUP_COLORS[i])
                    .iconSet(METALLIC)
                    .componentStacks(stacks)
                    .buildAndRegister();
        }
        // 归一物质：由 20 种族统合物质组成，**每族 4 份**（按族顺序，下标 0..19）。
        //
        // 早先的实现是把 groupElements 摊平成"118 个元素 × 4"，那不是预期：
        // 归一物质应当保留"族"这一层结构 —— 组成它的单位是族统合物质，不是单个元素。
        // 要拿到元素级别，路径是：归一物质 → 族统合物质（×4）→ 各族的元素。
        //
        // 不生成分解配方（DISABLE_DECOMPOSITION，用户确认）—— 20 个输出仍不适合电解器槽位；
        // 化学式由 GTCEu 自动生成（超长拼接，仅显示用）。
        List<MaterialStack> all = new ArrayList<>();
        for (Material unified : UNIFIED_MATTER) {
            all.add(new MaterialStack(unified, 4));
        }
        NORMALIZED = new Material.Builder(Gtnecore.id("normalized_matter"))
                .langValue("Normalized Matter")
                .dust()
                .color(0xFFD700)
                .secondaryColor(0xFFF8DC)
                .iconSet(SHINY)
                .componentStacks(all.toArray(new MaterialStack[0]))
                .flags(MaterialFlags.DISABLE_DECOMPOSITION)
                .buildAndRegister();
        LOGGER.info("GTNEcore: 注册 {} 种族统合物质 + 归一物质（{} 族 × 4 = {} 份）",
                UNIFIED_MATTER.length, all.size(), all.size() * 4);
    }
}
