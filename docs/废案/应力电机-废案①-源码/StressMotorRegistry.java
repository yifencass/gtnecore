package org.ringclouds.gtnecore.kinetics;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import org.ringclouds.gtnecore.Gtnecore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应力电机注册：LV..UHV 九档，方块共用同一个方块实体类型（档位由方块自己携带）。
 *
 * <p>
 * 模型/blockstate 是**手写**的（{@code assets/gtnecore/blockstates/<tier>_stress_motor.json}
 * 与 {@code models/block/<tier>_stress_motor.json}），不走 Registrate 的 datagen——
 * 本项目的 {@code runData} 因 ModernUI/Embeddium 在无客户端环境下 NPE 而跑不起来
 * （与 mod 代码无关），现有资源也全是手写。九档贴图统一取 GT 电压机壳
 * （{@code gtceu:block/casings/voltage/<tier>/*}），天然有档位外观区分。
 */
public final class StressMotorRegistry {

    private StressMotorRegistry() {}

    /** 九档：LV..UHV（与星象编程仪同档位段） */
    public static final int[] TIERS = {
            GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV,
            GTValues.LuV, GTValues.ZPM, GTValues.UV, GTValues.UHV
    };

    public static final Map<Integer, BlockEntry<StressMotorBlock>> MOTORS = new LinkedHashMap<>();

    public static BlockEntityEntry<StressMotorBlockEntity> STRESS_MOTOR_BE;

    /** 档位 → 方块注册名（lv_stress_motor 等），供资源文件名引用 */
    public static String blockName(int tier) {
        return GTValues.VN[tier].toLowerCase() + "_stress_motor";
    }

    public static void register() {
        if (!MOTORS.isEmpty()) return;

        for (int tier : TIERS) {
            BlockEntry<StressMotorBlock> entry = Gtnecore.REGISTRATE
                    .block(blockName(tier), properties -> new StressMotorBlock(properties, tier))
                    .initialProperties(() -> Blocks.IRON_BLOCK)
                    .properties(BlockBehaviour.Properties::noOcclusion)
                    .item()
                    .build()
                    .register();

            MOTORS.put(tier, entry);
        }

        BlockEntityBuilder<StressMotorBlockEntity, ?> builder = Gtnecore.REGISTRATE
                .blockEntity("stress_motor", StressMotorBlockEntity::new);
        for (BlockEntry<StressMotorBlock> entry : MOTORS.values()) {
            builder = builder.validBlock(entry);
        }
        STRESS_MOTOR_BE = builder.register();
    }
}
