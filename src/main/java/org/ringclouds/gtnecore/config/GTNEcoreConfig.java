package org.ringclouds.gtnecore.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * GTNEcore server-side config.
 * Values are per-world and synced to clients on join.
 */
public final class GTNEcoreConfig {

    public static final ForgeConfigSpec SPEC;
    /** Base FE <-> EU ratio: how many FE equal 1 EU. Default 4 (official GT ratio), max 65536. */
    public static final ForgeConfigSpec.IntValue FE_PER_EU;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Energy cube FE <-> EU conversion settings").push("energyCube");
        FE_PER_EU = builder
                .comment("  Base conversion ratio: how many FE equal 1 EU.",
                        "  Range: [1, 65536]. Default: 4 (official GregTech ratio).",
                        "  Conversion always keeps the 80% efficiency:",
                        "    FE -> EU: 1 EU costs 5/4 of the nominal ratio in FE (loses 20%).",
                        "    EU -> FE: 1 EU yields 4/5 of the nominal ratio in FE (loses 20%).")
                .defineInRange("fePerEu", 4, 1, 65536);
        builder.pop();
        SPEC = builder.build();
    }

    private GTNEcoreConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}