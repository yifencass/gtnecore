package org.ringclouds.gtnecore.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 火箭发射台"引擎震动"相机抖动（纯客户端，Forge EVENT_BUS 的 ComputeCameraAngles）：
 *
 * 渲染器每帧把各发射台的震动强度上报进来（升空段 = 恒强震、降落段 = 越近地面越震、
 * 其余 = 0 即移除源），本类在相机角度事件里按距离衰减叠加成 yaw/pitch/roll 抖动。
 *
 * 设计要点：
 * - 多台发射台同时震 → 各自独立源，事件里求和后再钳制幅度；
 * - 源上报带时间戳，>1.5s 没再上报（机器被拆/区块卸载/发射台远离视野）自动清掉，
 *   不会在空地凭空抖动；
 * - 全部在渲染线程执行（render() 与相机事件同线程），无并发问题；
 * - 幅度按真实秒做正弦叠加，帧率无关且连续。
 */
@OnlyIn(Dist.CLIENT)
public final class RocketLaunchCameraFX {

    /** 全强度半径（格） */
    private static final double FULL_RADIUS = 14.0;
    /** 衰减到 0 的半径（格） */
    private static final double MAX_RADIUS = 96.0;
    /** 单源最大角度幅度（度，距离=0 且多源求和前） */
    private static final float MAX_ANGLE_DEG = 0.5F;
    /** 源超过该时长未刷新即视为失效（毫秒） */
    private static final long STALE_MS = 1500;

    private static final Map<BlockPos, Source> SOURCES = new HashMap<>();

    private record Source(float intensity, long lastUpdateMs) {
    }

    private RocketLaunchCameraFX() {
    }

    /** 客户端初始化时注册（@Mod 构造 CLIENT 分支调用一次）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(RocketLaunchCameraFX::onComputeCameraAngles);
    }

    /**
     * 渲染器每帧上报震动源。intensity ≤ 0 表示该台当前无震动（移除源）。
     * 只在渲染线程调用。
     */
    public static void setSource(BlockPos padPos, float intensity) {
        if (intensity <= 0.0F) {
            SOURCES.remove(padPos);
        } else {
            SOURCES.put(padPos, new Source(Math.min(intensity, 1.0F), System.currentTimeMillis()));
        }
    }

    private static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (SOURCES.isEmpty()) return;
        long now = System.currentTimeMillis();
        Vec3 camPos = event.getCamera().getPosition();
        double total = 0.0;
        Iterator<Map.Entry<BlockPos, Source>> it = SOURCES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Source> e = it.next();
            if (now - e.getValue().lastUpdateMs() > STALE_MS) {
                it.remove(); // 源失效（机器被拆/区块卸载）
                continue;
            }
            double dx = e.getKey().getX() + 0.5 - camPos.x;
            double dy = e.getKey().getY() + 0.5 - camPos.y;
            double dz = e.getKey().getZ() + 0.5 - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double falloff = dist <= FULL_RADIUS ? 1.0 : Math.max(0.0, 1.0 - (dist - FULL_RADIUS) / (MAX_RADIUS - FULL_RADIUS));
            total += falloff * e.getValue().intensity();
        }
        if (total <= 0.01) return;

        float mag = MAX_ANGLE_DEG * (float) Math.min(total, 1.0);
        double t = now / 1000.0; // 真实秒，连续无帧率依赖
        float yawOff = (float) (Math.sin(t * 7.0) * mag);
        float pitchOff = (float) (Math.cos(t * 5.7) * mag * 0.7);
        float rollOff = (float) (Math.sin(t * 9.3) * mag * 0.45);
        event.setYaw(event.getYaw() + yawOff);
        event.setPitch(event.getPitch() + pitchOff);
        event.setRoll(event.getRoll() + rollOff);
    }
}
