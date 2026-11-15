package org.ringclouds.gtnecore.material;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTElements;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.ringclouds.gtnecore.Gtnecore;
import org.slf4j.Logger;

import java.util.List;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_PLATE;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.GEM_HORIZONTAL;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.METALLIC;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.SHINY;

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
    public static Material INFINITY;
    public static Material MAGIC;
    public static Material SOUL;
    public static Material UNKNOWN;
    public static Material SOUL_UNKNOWN;

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

        // Infinity：锭/粒不生成（用 Re-Avaritia 的无尽锭/无尽粒，PostMaterialEvent 里归类绑定）
        INFINITY = new Material.Builder(Gtnecore.id("infinity"))
                .langValue("Infinity")
                .element(GTElements.get("Infinite"))
                .dust().fluid()
                .flags(GENERATE_PLATE)
                .color(0xFFFFFF)
                .iconSet(SHINY)
                .buildAndRegister();

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

        // 幽匿化合物（未知化二灵魂）：2 灵魂 + 1 未知，锭形态（Sl₂? 锭）
        // DISABLE_DECOMPOSITION：有成分会自动带 DECOMPOSITION flag，
        // 需禁用避免分解生成器处理；setFormula 手动写份数化学式
        // （GT 自动生成用 MaterialStack.toString 会把原始 mB 当下标：Sl₂₈₈?₁₄₄）
        SOUL_UNKNOWN = new Material.Builder(Gtnecore.id("soul_unknown"))
                .langValue("Soul-Unknown")
                .ingot()
                .componentStacks(new MaterialStack(SOUL, 288), new MaterialStack(UNKNOWN, 144))
                .flags(MaterialFlags.DISABLE_DECOMPOSITION)
                .color(0x4FE3C1)
                .iconSet(SHINY)
                .buildAndRegister();
        SOUL_UNKNOWN.setFormula("Sl" + FormattingUtil.toSmallDownNumbers("2") + "?");

        // Sculk（幽匿）材料 = 幽匿化合物：2 灵魂 + 1 未知 → 化学式 Sl₂?
        // DISABLE_DECOMPOSITION：不生成分解配方（无法电解/离心）
        GTMaterials.Sculk.setComponents(
                new MaterialStack(SOUL, 288),
                new MaterialStack(UNKNOWN, 144));
        GTMaterials.Sculk.setFormula(autoFormula(GTMaterials.Sculk.getMaterialComponents()));
        GTMaterials.Sculk.addFlags(MaterialFlags.DISABLE_DECOMPOSITION);
        LOGGER.info("GTNEcore: Sculk components after rewrite = {}, formula = {}",
                GTMaterials.Sculk.getMaterialComponents(), GTMaterials.Sculk.getChemicalFormula());
    }

    /**
     * 从成分生成化学式（对齐 GT 的 MaterialStack.toString 风格）：
     * - 复合材料（成分数 > 1）化学式用括号包裹，如 (SiO₂)
     * - 份数 > 1 用下标数字，如 Sl₂
     * 例：(Sl₂?)(SiO₂)
     */
    public static String autoFormula(List<MaterialStack> components) {
        StringBuilder sb = new StringBuilder();
        for (MaterialStack component : components) {
            Material material = component.material();
            String formula = material.getChemicalFormula();
            if (material.getMaterialComponents().size() > 1) {
                sb.append('(').append(formula).append(')');
            } else {
                sb.append(formula);
            }
            long parts = component.amount() / 144;
            if (parts > 1) {
                sb.append(FormattingUtil.toSmallDownNumbers(Long.toString(parts)));
            }
        }
        return sb.toString();
    }

    /** PostMaterialEvent（mod 总线）：Re-Avaritia 无尽锭/无尽粒归类为 Infinity 材料形态（映射，非替换）。 */
    public static void bindAvaritia(PostMaterialEvent event) {
        ItemMaterialData.registerMaterialEntry(
                () -> avaritiaItem("infinity_ingot"),
                TagPrefix.ingot, INFINITY);
        ItemMaterialData.registerMaterialEntry(
                () -> avaritiaItem("infinity_nugget"),
                TagPrefix.nugget, INFINITY);
    }

    private static Item avaritiaItem(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("avaritia", path));
        return item != null ? item : net.minecraft.world.item.Items.AIR;
    }
}
