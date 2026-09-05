package org.ringclouds.gtnecore.config;

import org.ringclouds.gtnecore.client.WaveNameState;

/**
 * GTNEcore client config 门面。
 * <p>
 * 注意：Forge 一个 mod 只能注册一个 {@code ModConfig.Type.CLIENT} 配置（重复注册抛
 * "Config conflict detected!"），所以所有客户端项（波浪文字、供氧区域开关）都挂在
 * {@link WaveNameState#CLIENT_SPEC} 里，这里只做统一引用。
 */
public final class GTNEcoreClientConfig {

    /** EU 氧气分配机供氧区域覆盖层开关（默认开，与 Ad Astra 行为一致） */
    public static final net.minecraftforge.common.ForgeConfigSpec.BooleanValue SHOW_OXYGEN_AREA =
            WaveNameState.SHOW_OXYGEN_AREA;

    private GTNEcoreClientConfig() {
    }
}
