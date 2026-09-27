package org.ringclouds.gtnecore.halo;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.ringclouds.gtnecore.Gtnecore;

/**
 * GT之环客户端每 tick：
 * - 伽马覆写：佩戴时 gamma=1.0（夜视效果），摘下恢复原值
 * - 按键处理：H=回城 / V=紫颂果传送（发包）
 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HaloClientEvents {

    private static double savedGamma = -1.0;

    private HaloClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean wearing = HaloAbilities.isWearingHalo(mc.player);
        var gamma = ((org.ringclouds.gtnecore.mixin.OptionsAccessor) mc.options).gtne$getGamma();

        if (wearing) {
            if (savedGamma < 0) {
                savedGamma = gamma.get();
            }
            if (gamma.get() != 1.0) {
                gamma.set(1.0);
            }
        } else if (savedGamma >= 0) {
            gamma.set(savedGamma);
            savedGamma = -1.0;
        }

        if (HaloKeybinds.HOME.consumeClick()) {
            HaloNetwork.sendHome();
        }
        if (HaloKeybinds.CHORUS.consumeClick()) {
            HaloNetwork.sendChorus();
        }
    }
}
