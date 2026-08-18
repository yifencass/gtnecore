package org.ringclouds.gtnecore;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CombinedDirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.mojang.logging.LogUtils;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.ringclouds.gtnecore.block.GtnecoreBlocks;
import org.ringclouds.gtnecore.config.GTNEcoreConfig;
import org.ringclouds.gtnecore.item.GtnecoreItems;
import org.ringclouds.gtnecore.machine.EnergyCubeMachine;
import org.ringclouds.gtnecore.machine.EnergyCubeSideConfigHandler;
import org.ringclouds.gtnecore.machine.GtnecoreMachines;
import org.ringclouds.gtnecore.recipe.GtneCircuitTags;
import org.ringclouds.gtnecore.recipe.GtnePartBindings;
import org.ringclouds.gtnecore.soulenergy.SoulEnergyFluids;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Gtnecore.MODID)
public class Gtnecore {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "gtnecore";
    // Human-readable name, used for creative tab titles (GTM style: "<NAME> <Category>")
    public static final String NAME = "GTNEcore";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // GTCEu Registrate instance: registers GT machines (hv_machine_block) and their blocks/items
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MODID);

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    // Creative tab for GTNEcore items (naming style follows GTM: "<NAME> <Category>")
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> GTNECORE_TAB = CREATIVE_MODE_TABS.register("gtne_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> GtnecoreMachines.LV_ENERGY_CUBE.asStack())
                    .title(Component.translatable("itemGroup.gtnecore"))
                    .displayItems((parameters, output) -> {
                        // 自动包含全部 GTNEcore 注册物品：能量立方、部件、机械外壳等
                        REGISTRATE.getAll(Registries.ITEM).forEach(entry ->
                                output.accept(ItemEntry.cast(entry).asStack()));
                        // 高电压档位机壳方块（注册在 gtceu 命名空间，不在 REGISTRATE 内）
                        GtnecoreBlocks.EXTRA_MACHINE_CASINGS.values().forEach(entry ->
                                output.accept(entry.asStack()));
                        // 液态灵魂能量桶（DeferredRegister 注册，不在 REGISTRATE 内）
                        if (org.ringclouds.gtnecore.soulenergy.SoulEnergyFluids.LIQUID_SOUL_ENERGY_BUCKET.get() != null) {
                            output.accept(org.ringclouds.gtnecore.soulenergy.SoulEnergyFluids.LIQUID_SOUL_ENERGY_BUCKET.get());
                        }
                    })
                    .build());

    public Gtnecore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Server config (FE <-> EU ratio etc.)
        GTNEcoreConfig.register();

        // 波浪文字客户端配置（振幅/速度/波长，游戏内可改）
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                org.ringclouds.gtnecore.client.WaveNameState.CLIENT_SPEC);

        // 能量立方：在 GTM "方向设置"页里注册面模式处理器（可视化点击方块面改模式）
        CombinedDirectionalFancyConfigurator.registerConfigHandler(machine ->
                machine instanceof EnergyCubeMachine e ? () -> new EnergyCubeSideConfigHandler(e) : null);

        // 补全的高电压档位内容（部件物品、机械外壳）
        GtnecoreItems.register();
        // CYV(17) 机壳方块（gtceu 命名空间）
        GtnecoreBlocks.register();

        REGISTRATE.registerRegistrate();

        // Register our GT machines before GTCEu freezes its registries
        modEventBus.addGenericListener(MachineDefinition.class, GtnecoreMachines::onMachineRegister);

        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        // 电路/部件组件档位重绑（AddPackFindersEvent 是 IModBusEvent，只在 mod 总线分发；
        // 主绑定走 GTCommonProxyMixin（GTCEu 方法内部、配方生成之前），此处仅作兜底）
        modEventBus.addListener(EventPriority.LOWEST, (AddPackFindersEvent event) -> {
            if (event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA) {
                GtneCircuitTags.install();
                GtnePartBindings.install();
            }
        });
        // 液态灵魂能量（Goety 兼容：灵魂能量流体化，科技 mod 通用载体）
        SoulEnergyFluids.register(modEventBus);

        // 自定义材料（Timeium 等）：MaterialRegistryEvent 建注册表 + MaterialEvent 注册 + PostMaterialEvent 物品归类
        modEventBus.addListener(org.ringclouds.gtnecore.material.GtneMaterials::createRegistry);
        modEventBus.addListener(org.ringclouds.gtnecore.material.GtneMaterials::register);
        modEventBus.addListener(org.ringclouds.gtnecore.material.GtneMaterials::bindAvaritia);

        LOGGER.info("GTNEcore loading");
    }
}
