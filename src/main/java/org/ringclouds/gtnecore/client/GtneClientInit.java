package org.ringclouds.gtnecore.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.client.render.GtneHaloModel;
import org.ringclouds.gtnecore.client.render.GtneHaloRenderer;
import org.ringclouds.gtnecore.item.GtnecoreItems;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * 客户端初始化：注册 GT之环 的模型层定义与 Curios 渲染器。
 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GtneClientInit {

    private GtneClientInit() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GtneHaloRenderer.LAYER, GtneHaloModel::createLayer);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                CuriosRendererRegistry.register(GtnecoreItems.GT_HALO.get(), GtneHaloRenderer::new));
    }
}
