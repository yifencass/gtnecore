package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;

/**
 * GTNEcore 环境类型注册表：超净间式的"状态"体系扩展。
 *
 * - WEIGHTLESS（失重）：所有非黑洞轨道维度的机器自动获得
 * - SPATIAL_DISTORTION（强扭曲空间）：黑洞轨道的机器获得
 *
 * 用 CleanroomType 实现（自注册 + CODEC），配方可用 .cleanroom() 挂条件。
 * 机器通过 ICleanroomReceiver 的绑定机制感知环境（与超净间同一管线）。
 */
public final class GtneEnvironments {

    /** 失重：轨道维度的标准环境（配方加成/条件用） */
    public static final CleanroomType WEIGHTLESS =
            new CleanroomType("weightless", "gtnecore.recipe.weightless.display_name");

    /** 强扭曲空间：黑洞轨道环境（高级配方条件/惩罚用） */
    public static final CleanroomType SPATIAL_DISTORTION =
            new CleanroomType("spatial_distortion", "gtnecore.recipe.spatial_distortion.display_name");

    private GtneEnvironments() {
    }

    /** 判断维度是否为轨道维度（Ad Astra isSpace） */
    public static boolean isOrbitDimension(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        var planet = earth.terrarium.adastra.api.planets.PlanetApi.API.getPlanet(dim);
        return planet != null && planet.isSpace();
    }

    /** 判断维度是否为黑洞轨道 */
    public static boolean isBlackHoleOrbit(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        return dim.location().getNamespace().equals("gtnecore")
                && dim.location().getPath().equals("blackhole_orbit");
    }

    /** 轨道维度对应的环境类型：黑洞 → 强扭曲空间，其余 → 失重 */
    public static CleanroomType orbitEnvironment(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        if (isBlackHoleOrbit(dim)) return SPATIAL_DISTORTION;
        return WEIGHTLESS;
    }
}
