package org.ringclouds.gtnecore.halo;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.ringclouds.gtnecore.Gtnecore;

/** GT之环按键注册（客户端）：H=回城，V=紫颂果传送。按键处理在 HaloClientEvents。 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HaloKeybinds {

    public static final KeyMapping HOME = new KeyMapping(
            "key.gtnecore.halo_home", GLFW.GLFW_KEY_H, "key.categories.gtnecore");
    public static final KeyMapping CHORUS = new KeyMapping(
            "key.gtnecore.halo_chorus", GLFW.GLFW_KEY_V, "key.categories.gtnecore");

    private HaloKeybinds() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(HOME);
        event.register(CHORUS);
    }
}
