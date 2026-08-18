package org.ringclouds.gtnecore.soulenergy;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.ringclouds.gtnecore.Gtnecore;

/**
 * 液态灵魂能量（Liquid Soul Energy）：Goety 灵魂能量的流体化载体。
 *
 * 设计：流体是科技模组的通用语言（GTM 流体管道/仓、其他科技 mod 的流体体系都能
 * 处理），把灵魂能量注册为流体即打通"灵魂能量 ↔ 科技"的关键桥。后续对接逻辑：
 * 流体桶/容器中的液态灵魂能量 ↔ 玩家/实体的灵魂能量（Goety ISoulEnergy capability）。
 *
 * 视觉参考 Goety 的灵魂能量：青绿色（#4FE3C1），带灵魂火特性的发光感。
 */
public final class SoulEnergyFluids {

    /** 流体 id。 */
    public static final String FLUID_ID = "liquid_soul_energy";

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, Gtnecore.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Gtnecore.MODID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Gtnecore.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Gtnecore.MODID);

    /** 客户端扩展：青绿色 tint（Goety 灵魂能量配色）+ 纹理（先用水纹理，专属贴图后续）。 */
    private static final net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions SOUL_ENERGY_EXTENSIONS =
            new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
                @Override
                public int getTintColor() {
                    return 0xFF4FE3C1;
                }

                @Override
                public int getTintColor(net.minecraftforge.fluids.FluidStack stack) {
                    return 0xFF4FE3C1;
                }

                @Override
                public net.minecraft.resources.ResourceLocation getStillTexture() {
                    return new net.minecraft.resources.ResourceLocation(Gtnecore.MODID,
                            "block/fluids/fluid." + FLUID_ID + "_still");
                }

                @Override
                public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                    return new net.minecraft.resources.ResourceLocation(Gtnecore.MODID,
                            "block/fluids/fluid." + FLUID_ID + "_flow");
                }
            };

    public static final RegistryObject<FluidType> LIQUID_SOUL_ENERGY_TYPE = FLUID_TYPES.register(FLUID_ID,
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.gtnecore.liquid_soul_energy")
                    .canConvertToSource(false)
                    .canDrown(true)
                    .canExtinguish(false)
                    .supportsBoating(true)
                    .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)
                    .density(1500)
                    .viscosity(2000)
                    .temperature(800)) {
                @Override
                public Object getRenderPropertiesInternal() {
                    return SOUL_ENERGY_EXTENSIONS;
                }
            });

    public static final RegistryObject<ForgeFlowingFluid> LIQUID_SOUL_ENERGY = FLUIDS.register(FLUID_ID,
            () -> new ForgeFlowingFluid.Source(SoulEnergyFluids.PROPERTIES));
    public static final RegistryObject<ForgeFlowingFluid> LIQUID_SOUL_ENERGY_FLOWING = FLUIDS.register(
            FLUID_ID + "_flowing", () -> new ForgeFlowingFluid.Flowing(SoulEnergyFluids.PROPERTIES));

    public static final RegistryObject<LiquidBlock> LIQUID_SOUL_ENERGY_BLOCK = BLOCKS.register(FLUID_ID,
            () -> new LiquidBlock(LIQUID_SOUL_ENERGY, Block.Properties.of()
                    .noCollission()
                    .strength(100.0F)
                    .noLootTable()));

    public static final RegistryObject<BucketItem> LIQUID_SOUL_ENERGY_BUCKET = ITEMS.register(
            FLUID_ID + "_bucket",
            () -> new BucketItem(LIQUID_SOUL_ENERGY, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final ForgeFlowingFluid.Properties PROPERTIES = new ForgeFlowingFluid.Properties(
            LIQUID_SOUL_ENERGY_TYPE, LIQUID_SOUL_ENERGY, LIQUID_SOUL_ENERGY_FLOWING)
            .bucket(LIQUID_SOUL_ENERGY_BUCKET)
            .block(LIQUID_SOUL_ENERGY_BLOCK)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(2)
            .explosionResistance(100.0F);

    private SoulEnergyFluids() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(Gtnecore.MODID, path);
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }
}
