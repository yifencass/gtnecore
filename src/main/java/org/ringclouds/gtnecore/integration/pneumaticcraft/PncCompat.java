package org.ringclouds.gtnecore.integration.pneumaticcraft;

import net.minecraftforge.fml.ModList;

import org.jetbrains.annotations.Nullable;

/**
 * PneumaticCraft: Repressurized 集成的**唯一守卫点**。
 *
 * <h2>为什么需要这个类</h2>
 * PnC 是可选依赖。任何直接引用 {@code me.desht.pneumaticcraft.api.*} 的类，一旦在
 * PnC 缺席时被 JVM 加载，就会抛 {@link NoClassDefFoundError}（类加载期解析引用）。
 * 因此所有 PnC 引用都必须被隔离在「只有确认 PnC 在场才会被加载」的类里，
 * 而那个确认动作本身**不能**触碰任何 PnC 类型 —— 这就是本类存在的理由。
 *
 * <h2>许可边界（改动前必读）</h2>
 * <ul>
 * <li>PnC 本体 = <b>GPLv3</b></li>
 * <li>{@code me.desht.pneumaticcraft.api.*} = <b>LGPLv3</b>（独立发布的
 * {@code ...:api} classifier 制品，174 个类全部落在 api 包内）</li>
 * <li>作者在 README 的 "Licensing Information" 中明确声明：API 单独授权的目的
 * 就是让其他 mod <b>链接 API 而不必变成 GPLv3</b></li>
 * </ul>
 * 故本工程（LGPL-3.0）引用其 API 合规。但**必须**遵守：
 * <ol>
 * <li>只 import {@code me.desht.pneumaticcraft.api.*} —— 碰
 * {@code me.desht.pneumaticcraft.common.*} 就越界到 GPLv3 了</li>
 * <li>依赖只能是 {@code compileOnly}，<b>严禁</b> {@code jarJar}／shade</li>
 * </ol>
 */
public final class PncCompat {

    /** PnC 的 mod id（注意：不是 maven 制品名 pneumaticcraft-repressurized）。 */
    public static final String MOD_ID = "pneumaticcraft";

    /**
     * 缓存的加载状态。
     * <p>
     * {@code null} = 尚未查询。用包装类型而非 {@code boolean}，是为了区分
     * "还没查" 与 "查了，不在场" —— 集成代码可能在极早期（ModList 尚未就绪）被调用。
     */
    @Nullable
    private static Boolean loaded;

    private PncCompat() {}

    /**
     * PnC 是否可用。
     *
     * <p>
     * 本方法**只**使用 Forge 自己的 {@link ModList}，不触碰任何 PnC 类型，
     * 因此可以被任意代码安全调用，包括 PnC 缺席时。
     */
    public static boolean isAvailable() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    /**
     * 供 {@code FMLCommonSetupEvent} 之后重置缓存。
     *
     * <p>
     * {@link ModList} 在 mod 发现阶段之后即稳定，正常无需重置；保留此方法是为了
     * 应对将来在 datagen / gametest 等 ModList 可能不完整的场景。
     */
    public static void invalidateCache() {
        loaded = null;
    }
}
