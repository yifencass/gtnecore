package org.ringclouds.gtnecore;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CombinedDirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.ringclouds.gtnecore.block.GtnecoreBlocks;
import org.ringclouds.gtnecore.config.GTNEcoreConfig;
import org.ringclouds.gtnecore.cover.GtneCoverBindings;
import org.ringclouds.gtnecore.item.GtneCreativeTabs;
import org.ringclouds.gtnecore.item.GtnecoreItems;
import org.ringclouds.gtnecore.machine.EnergyCubeMachine;
import org.ringclouds.gtnecore.machine.EnergyCubeSideConfigHandler;
import org.ringclouds.gtnecore.machine.GtnecoreMachines;
import org.ringclouds.gtnecore.recipe.GtneCircuitTags;
import org.ringclouds.gtnecore.recipe.GtnePartBindings;
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

    public Gtnecore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 动力合成尺寸扩容：Create 在 AllRecipeTypes.register() 里调 setCraftingSize(9, 9)，
        // 该方法只增不减（取最大）——这里提到 16×16，支持更大的机械合成配方。
        // 必须在配方 JSON 加载前调用（mod 构造期远早于 datapack 加载，安全）。
        // 运行时无上限硬编码（RecipeGridHandler/MechanicalCraftingInventory 已核），JEI 分类按配方缩放。
        net.minecraft.world.item.crafting.ShapedRecipe.setCraftingSize(16, 16);

        // Server config (FE <-> EU ratio etc.)
        GTNEcoreConfig.register();

        // 波浪文字客户端配置（振幅/速度/波长 + 供氧区域开关，游戏内可改）
        // 注意：一个 mod 只能注册一个 ModConfig.Type.CLIENT 配置
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

        // GT之环（Curios 饰品）：必须在物品真正注册后（FMLCommonSetup 阶段，Forge
        // RegisterEvent 已 fire）注册饰品能力，可佩戴到 HEAD 槽位（data/curios/tags/items/head.json）
        modEventBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(() ->
                        top.theillusivec4.curios.api.CuriosApi.registerCurio(
                                GtnecoreItems.GT_HALO.get(), GtnecoreItems.GT_HALO.get())));

        // Register our GT machines before GTCEu freezes its registries
        modEventBus.addGenericListener(MachineDefinition.class, GtnecoreMachines::onMachineRegister);

        // 自定义配方类型（火箭发射台等）：GTCEu 冻结前注册
        modEventBus.addGenericListener(com.gregtechceu.gtceu.api.recipe.GTRecipeType.class,
                org.ringclouds.gtnecore.machine.GtnecoreMachines::onRecipeTypeRegister);

        // 创造标签页（物品/机器/材料 3 分页，仿 GTCEu GTCreativeModeTabs）
        GtneCreativeTabs.TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        // 电路/部件组件档位重绑（AddPackFindersEvent 是 IModBusEvent，只在 mod 总线分发；
        // 主绑定走 GTCommonProxyMixin（GTCEu 方法内部、配方生成之前），此处仅作兜底）
        modEventBus.addListener(EventPriority.LOWEST, (AddPackFindersEvent event) -> {
            if (event.getPackType() == net.minecraft.server.packs.PackType.SERVER_DATA) {
                GtneCircuitTags.install();
                GtnePartBindings.install();
                // GTCEu uxv/opv 部件物品的覆盖板定义换绑（物品全部注册完毕后）
                GtneCoverBindings.install();
            }
        });
        // 自定义材料（Timeium 等）：MaterialRegistryEvent 建注册表 + MaterialEvent 注册 + PostMaterialEvent 物品归类
        modEventBus.addListener(org.ringclouds.gtnecore.material.GtneMaterials::createRegistry);
        modEventBus.addListener(org.ringclouds.gtnecore.material.GtneMaterials::register);
        // 无尽锭/粒归类必须在物品注册后（FMLCommonSetup）执行，否则注册条目解析到 AIR、
        // ChemicalHelper.getMaterialEntry 永远查不到（PostMaterialEvent 时 avaritia 物品尚未注册）
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            // 官方部件补 IS_FORMED 渲染属性已提前到机器注册事件（GtnecoreMachines.onMachineRegister
            // 尾部）执行——FMLCommonSetup 晚于模型解析，补晚会导致机器渲染状态与烘焙模型
            // 的 stateDefinition 不一致（机器透明）
            org.ringclouds.gtnecore.material.GtneMaterials.bindAvaritia();
        }));

        // GT之环按键网络（回城/紫颂果传送）
        org.ringclouds.gtnecore.halo.HaloNetwork.register();

        // 配方规划器（纯客户端）：背包界面新按钮入口
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            org.ringclouds.gtnecore.planner.client.PlannerEntry.initClient();
            // 反曲率引擎动态渲染器（蓝色方块 + 聚变环动画）：注册 DynamicRenderType 供模型 JSON 解析
            org.ringclouds.gtnecore.client.render.AntigravityEngineRender.init();
            // 火箭发射台动态渲染器（glTF 勘矿火箭停靠/升空/降落）：同上注册
            org.ringclouds.gtnecore.client.render.RocketLaunchPadRender.init();
            // 火箭引擎震动相机抖动（Forge EVENT_BUS，发射台震动源由渲染器每帧上报）
            org.ringclouds.gtnecore.client.render.RocketLaunchCameraFX.register();
        }

        LOGGER.info("GTNEcore loading");
    }
}
