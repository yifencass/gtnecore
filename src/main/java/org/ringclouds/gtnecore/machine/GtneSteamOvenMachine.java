package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import net.minecraft.world.level.block.Block;

/**
 * 蒸汽熔炉：
 * - 并行 = 16 × 蒸汽输入仓数（最多 4 仓 = 64 并行），成型时按实际蒸汽仓数动态设置
 * - 耗蒸汽 = steam_oven（换算率 2.0）的两倍 → getConversionRate() 返回 4.0
 * - 成型附加校验：输出总线（EXPORT_ITEMS）≥1 且 输入流体仓（IMPORT_FLUIDS）≥1
 *   （D/C 槽位可互相放置对应部件，故按全局计数校验）
 */
public class GtneSteamOvenMachine extends SteamParallelMultiblockMachine {

    /** 每个蒸汽输入仓提供的并行 */
    private static final int PARALLEL_PER_HATCH = 16;

    public GtneSteamOvenMachine(IMachineBlockEntity holder) {
        super(holder, PARALLEL_PER_HATCH);
    }

    @Override
    public double getConversionRate() {
        return 4.0;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // 附加校验：输出总线 ≥1 且 输入总线 ≥1（D/C 槽位可互换，全局计数）
        int export = 0, importItems = 0;
        for (IMultiPart part : getParts()) {
            Block b = part.self().getDefinition().getBlock();
            if (PartAbility.EXPORT_ITEMS.isApplicable(b)) export++;
            if (PartAbility.IMPORT_ITEMS.isApplicable(b)) importItems++;
        }
        if (export < 1 || importItems < 1) {
            onStructureInvalid();
            isFormed = false;
            return;
        }
        // 并行 = 16 × 蒸汽仓数（pattern 已保证至少 1 个）
        int steam = 0;
        for (IMultiPart part : getParts()) {
            if (PartAbility.STEAM.isApplicable(part.self().getDefinition().getBlock())) steam++;
        }
        setMaxParallels(PARALLEL_PER_HATCH * Math.max(1, steam));
    }
}