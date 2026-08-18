package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.voltages.GtneVoltages;

import java.util.Locale;


/**
 * GTM-style machines registered into GTCEu's registries.
 * Registered via GTCEuAPI.RegisterEvent (fired before GTCEu freezes its registries).
 */
public final class GtnecoreMachines {

    private GtnecoreMachines() {
    }

    /** Energy Cubes for every tier LV..MAX(21) (indexed by tier, null where not registered). */
    public static MachineDefinition[] ENERGY_CUBES = new MachineDefinition[GTValues.TIER_COUNT];

    /** LV Energy Cube (first tiered example). */
    public static MachineDefinition LV_ENERGY_CUBE;

    /** 22-tier re-ladder: register LV(1) .. MAX(21); ULV(0) not registered. */
    private static int[] energyCubeTiers() {
        int[] tiers = new int[GtneVoltages.TIER_COUNT - GTValues.LV];
        for (int i = 0; i < tiers.length; i++) {
            tiers[i] = GTValues.LV + i;
        }
        return tiers;
    }

    public static void onMachineRegister(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        ENERGY_CUBES = new MachineDefinition[GTValues.TIER_COUNT];
        for (int tier : energyCubeTiers()) {
            // 注册名用 GTNEcore 自己的 22 档阶梯名（GTValues.VN 0-14 保持原版名，
            // 避免 GTCEu 原生 12-14 档机器被连带改名；显示名由 lang 键翻译嫁接）
            ENERGY_CUBES[tier] = Gtnecore.REGISTRATE
                    .machine(GtneVoltages.VN[tier].toLowerCase(Locale.ROOT) + "_energy_cube",
                            holder -> new EnergyCubeMachine(holder, tier))
                    .tier(tier)
                    .rotationState(RotationState.NONE)
                    .defaultModel()
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_DOWN, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_UP, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_NORTH, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_SOUTH, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_WEST, EnergyCubeMachine.EnergyIOMode.NONE)
                    .modelProperty(EnergyCubeMachine.ENERGY_IO_EAST, EnergyCubeMachine.EnergyIOMode.NONE)
                    .tooltips(Component.translatable("block.gtnecore.energy_cube.tooltip"))
                    .register();
        }
        LV_ENERGY_CUBE = ENERGY_CUBES[GTValues.LV];

    }
}
