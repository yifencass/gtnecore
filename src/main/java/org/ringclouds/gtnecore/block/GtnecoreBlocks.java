package org.ringclouds.gtnecore.block;

import com.gregtechceu.gtceu.common.registry.GTRegistration;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * GTNEcore 补全的高压档位机壳方块。
 * GTCEu 原版 GTBlocks 注册了 0-14 档 machine_casing（重排后 = ULV..UIV + UXV/OpV/MAX 恰为
 * 15/18/21 档），重排后缺 12/13/14/16/17/19/20 档（UMV/SWV/GCV/QGV/CYV/VEV/SGV）。
 * 用 GTCEu 自己的 REGISTRATE 注册进 gtceu 命名空间，与原版机壳同 id 规则。
 * 显示名走 lang 文件（键 block.gtceu.{档名}_machine_casing），blockstate/模型/贴图已就位。
 */
public final class GtnecoreBlocks {

    /** CYV(17) 机壳方块。 */
    public static BlockEntry<Block> CYV_MACHINE_CASING;

    /** 天文辅镜（Astronomical Finderscope）：天文观测站建材方块。
     *  非机器、无方块实体——仅作结构件；顶面叠观测镜贴图（双层模型，GTM 同款 0.01px 悬浮层）。 */
    public static BlockEntry<Block> ASTRONOMICAL_FINDERSCOPE;

    /** 补注册的机壳方块（档位 → 方块条目）。 */
    public static final Map<Integer, BlockEntry<Block>> EXTRA_MACHINE_CASINGS = new HashMap<>();

    /** 需要补全的机壳档位：12=UMV, 13=SWV, 14=GCV, 16=QGV, 17=CYV, 19=VEV, 20=SGV
     * （15/18/21 = UXV/OpV/MAX 由 GTCEu 原生注册，名字恰好是重排后的档位名）。 */
    private static final int[] EXTRA_TIERS = {12, 13, 14, 16, 17, 19, 20};

    /** 档位名 → 注册名后缀（CYV 特殊：原注册名 cyv_machine_casing 不带档名前缀规则差异，统一用小写档名）。 */
    private static final String[] TIER_NAMES = {"umv", "swv", "gcv", "qgv", "cyv", "vev", "sgv"};

    private GtnecoreBlocks() {
    }

    /** 在 Gtnecore 构造器中调用（GTCEu 的 REGISTRATE 已就绪，注册事件尚未触发）。 */
    public static void register() {
        for (int i = 0; i < EXTRA_TIERS.length; i++) {
            String name = TIER_NAMES[i];
            BlockEntry<Block> entry = GTRegistration.REGISTRATE
                    .block(name + "_machine_casing", Block::new)
                    .initialProperties(() -> Blocks.IRON_BLOCK)
                    .properties(p -> p.isValidSpawn((state, level, pos, ent) -> false))
                    .item(BlockItem::new)
                    .build()
                    .register();
            if (EXTRA_TIERS[i] == 17) {
                CYV_MACHINE_CASING = entry;
            }
            EXTRA_MACHINE_CASINGS.put(EXTRA_TIERS[i], entry);
        }
        // 天文辅镜：天文观测站建材（铁块属性，无方块实体）；blockstate/模型/贴图手工静态资产
        ASTRONOMICAL_FINDERSCOPE = GTRegistration.REGISTRATE
                .block("astronomical_finderscope", Block::new)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.isValidSpawn((state, level, pos, ent) -> false))
                .item(BlockItem::new)
                .build()
                .register();
    }
}
