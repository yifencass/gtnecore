package org.ringclouds.gtnecore.cover;

import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.common.data.GTCovers;

import net.minecraft.resources.ResourceLocation;
import org.ringclouds.gtnecore.Gtnecore;

/**
 * GTNEcore 自建覆盖板的定义注册。
 *
 * <p>
 * <b>唯一合法调用点</b>：{@code GtnecoreAddon.registerCovers()}——该方法由 GTCEu 在
 * {@code GTCovers.init()} 内部经 {@code AddonFinder.getAddons().forEach(...)} 调用，
 * 位置在 {@code GTRegistries.COVERS.unfreeze()} 之后、{@code COVERS.freeze()} 之前，
 * 是唯一还没冻结的窗口。
 *
 * <p>
 * ⚠️ <b>不要在 mod 构造器里调</b>：GTRegistry.frozen 初值为 {@code true}，GTCEu 先于
 * 本 mod 完成构造并冻结 COVERS，此处会抛
 * {@code IllegalStateException: [register] registry gtceu:cover has been frozen}
 * （2026-09-24 的实际启动崩溃）。
 *
 * <p>
 * 注意加载顺序：{@code GTCovers.PUMPS} 等档位数组在 {@code GTCovers.<clinit>} 里注册
 * （即 addon hook 之前），所以本方法**不要**去读那些数组，否则会把 GTCovers 提前初始化、
 * 当场解冻再注册一遍并失败。这里只用 {@code GTCovers.register(...)} 这个静态方法
 * （读它必然早已初始化完毕——在 GTCEu 自己的构造里）。
 */
public final class GtnecoreCovers {

    private GtnecoreCovers() {}

    /** 真空泵覆盖板：给所在机器提供真空环境（无运行消耗，门槛在材料） */
    public static CoverDefinition VACUUM_PUMP;

    public static synchronized void register() {
        if (VACUUM_PUMP != null) return;

        ResourceLocation id = Gtnecore.id("vacuum_pump");
        ResourceLocation texture = Gtnecore.id("block/cover/vacuum_pump");
        VACUUM_PUMP = GTCovers.register(id, VacuumPumpCover::new,
                () -> () -> new com.gregtechceu.gtceu.client.renderer.cover.SimpleCoverRenderer(texture));
    }
}
