package org.ringclouds.gtnecore.item;

import com.gregtechceu.gtceu.api.item.GTBucketItem;
import com.gregtechceu.gtceu.api.item.MaterialBlockItem;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.block.GtnecoreBlocks;
import org.ringclouds.gtnecore.machine.GtnecoreMachines;

import java.util.Set;

/**
 * GTNEcore 创造标签页（仿 GTCEu GTCreativeModeTabs 分页：物品/机器/材料）。
 *
 * GTCEu 的物品在注册时挂 tab（Registrate creativeModeTab）；GTNEcore 的部件/机器
 * 是自己的 REGISTRATE，而材料物品由 GTCEu GTMaterialItems 自动生成（硬编码挂 GTCEu
 * 的 Material Items tab，无法改），所以这里在 displayItems 里按物品类别运行时分类：
 * - 机器：instanceof MetaMachineItem（能量立方）
 * - 材料：GTBucketItem（流体桶）/ MaterialBlockItem（材料方块）/ 注册名以材料前缀开头
 *   （dust_timeium、ingot_strium、cable_strium 等，限定 gtnecore 命名空间）
 * - 其余：部件物品、GT之环、机壳 → Items tab
 *
 * 注意：gtnecore 材料物品会同时出现在 GTCEu 的材料 tab（GTCEu 生成时挂的）和这里。
 */
public final class GtneCreativeTabs {

    /** 材料物品注册名前缀（TagPrefix 名）：`<前缀>_<材料>`，如 dust_timeium / cable_strium。 */
    private static final Set<String> MATERIAL_PREFIXES = Set.of(
            "dust", "ingot", "nugget", "plate", "gem", "rod", "bolt", "screw", "gear",
            "ring", "foil", "frame", "spring", "cable", "wire", "cell");

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Gtnecore.MODID);

    /** 物品：部件、GT之环、机壳等非机器非材料内容。 */
    public static final RegistryObject<CreativeModeTab> ITEMS = TABS.register("gtne_items",
            () -> CreativeModeTab.builder()
                    .icon(() -> GtnecoreItems.GT_HALO.asStack())
                    .title(Component.translatable("itemGroup.gtnecore.items"))
                    .displayItems((parameters, output) -> {
                        forEachGtneItem(stack -> {
                            Item item = stack.getItem();
                            if (!isMachine(item) && !isMaterial(item)) {
                                output.accept(stack);
                            }
                        });
                        // 高电压档位机壳方块（注册在 gtceu 命名空间，不在 REGISTRATE 内）
                        GtnecoreBlocks.EXTRA_MACHINE_CASINGS.values().forEach(entry ->
                                output.accept(entry.asStack()));
                        // 天文观测站建材（同 gtceu 命名空间）
                        if (GtnecoreBlocks.ASTRONOMICAL_FINDERSCOPE != null) {
                            output.accept(GtnecoreBlocks.ASTRONOMICAL_FINDERSCOPE.asStack());
                        }
                    })
                    .build());

    /** 机器：能量立方（LV..MAX 22 档）。 */
    public static final RegistryObject<CreativeModeTab> MACHINES = TABS.register("gtne_machines",
            () -> CreativeModeTab.builder()
                    .icon(() -> GtnecoreMachines.LV_ENERGY_CUBE.asStack())
                    .title(Component.translatable("itemGroup.gtnecore.machines"))
                    .displayItems((parameters, output) -> forEachGtneItem(stack -> {
                        if (isMachine(stack.getItem()) && !isHiddenThreadContent(stack.getItem())) {
                            output.accept(stack);
                        }
                    }))
                    .build());

    /** 材料：gtnecore 材料生成的物品（粉/锭/板/电缆/流体桶等）。 */
    public static final RegistryObject<CreativeModeTab> MATERIALS = TABS.register("gtne_materials",
            () -> CreativeModeTab.builder()
                    .icon(() -> materialIcon())
                    .title(Component.translatable("itemGroup.gtnecore.materials"))
                    .displayItems((parameters, output) -> forEachGtneItem(stack -> {
                        if (isMaterial(stack.getItem())) {
                            output.accept(stack);
                        }
                    }))
                    .build());

    private GtneCreativeTabs() {
    }

    /** 遍历 GTNEcore REGISTRATE 注册的全部物品（gtnecore 命名空间）。 */
    private static void forEachGtneItem(java.util.function.Consumer<ItemStack> consumer) {
        Gtnecore.REGISTRATE.getAll(Registries.ITEM).forEach(entry ->
                consumer.accept(ItemEntry.cast(entry).asStack()));
    }

    private static boolean isMachine(Item item) {
        return item instanceof MetaMachineItem;
    }

    /** 线程机制内容（开发优先度最低）：从创造页隐藏，JEI 侧由 hidden_from_recipe_viewers tag 隐藏 */
    private static boolean isHiddenThreadContent(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !Gtnecore.MODID.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.endsWith("_thread_hatch") || path.equals("thread_furnace");
    }

    /** gtnecore 命名空间下、注册名以材料前缀开头的物品（材料方块/流体桶按类判）。 */
    private static boolean isMaterial(Item item) {
        if (item instanceof GTBucketItem || item instanceof MaterialBlockItem) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !Gtnecore.MODID.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        int idx = path.indexOf('_');
        return idx > 0 && MATERIAL_PREFIXES.contains(path.substring(0, idx));
    }

    /** 材料 tab 图标：弦锭（ingot_strium），未就绪时退回 GT之环。 */
    private static ItemStack materialIcon() {
        Item ingot = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Gtnecore.MODID, "ingot_strium"));
        return ingot != null ? new ItemStack(ingot) : GtnecoreItems.GT_HALO.asStack();
    }
}
