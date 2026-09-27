package org.ringclouds.gtnecore.client.render;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 火箭发射台"勘矿火箭"往返运动学（纯函数，无 MC 渲染/GL 依赖，只 import Mth/常数）。
 *
 * 配方进度 p∈[0,1]（客户端 DescSynced 的 getProgressPercent，9600t 与失重 2400t
 * 同样适用——只消费 p，绝不假设 duration）驱动两程循环：
 *
 *   待机停靠 → 发射段[0, LAUNCH_END_P]：慢爬（点火感）→ 急加速升至 APEX_HEIGHT(110 格)
 *   → **到顶直接消失（硬切换，无淡出）** → 巡航隐藏（"已入轨勘矿"，视觉缺席）→
 *   降落段[DESCENT_START_P, 1]：从 ENTRY_HEIGHT(100 格) 高空**直接出现**、快降、
 *   触底前减速、p=1 瞬间精确落台（y=BASE_Y、速度 0，下一配方 p 归 0 时停在台上无跳变）。
 *
 * 显隐 = sample.visible() 布尔硬切换，渲染端不做任何透明度渐变（透明状态在多台
 * 发射台共享渲染器实例上会互相感染，已废弃 fade 体系）。
 * 断电/断结构的冻结语义也不在此类（渲染器持 lastSample 快照回填上一帧）。
 * 所有常数集中在类顶，目视校准只改这里、勿动配方。
 */
@OnlyIn(Dist.CLIENT)
public final class RocketLaunchKinematics {

    // ======================= 相位窗口（配方进度占比） =======================
    /** 发射段终点：p∈[0,0.10] 完成"慢爬→急加速→到顶" */
    public static final float LAUNCH_END_P = 0.10F;
    /**
     * 发射段之后"冻结在顶点"的尾段宽度——置 0 = 到顶立即进入淡出（推荐观感：
     * 急升到顶点即曲速消失；>0 时火箭会在顶点悬停整个窗口（9600t 配方下
     * 0.01 ≈ 5 秒），目检调试时可临时调大观察顶点位姿）
     */
    public static final float LAUNCH_TAIL_P = 0.0F;
    /** 降落段起点：p∈[0.90,1] 高空重现 → 触底 */
    public static final float DESCENT_START_P = 0.90F;

    // ======================= 高度（世界 Y，相对机器方块角；1 单位 = 1 格） =======================
    /** 停靠底平面：机器方块顶面 */
    public static final float BASE_Y = 1.0F;
    /** 发射顶点高度：台面以上 ~110 格（到顶直接消失，无淡出） */
    public static final float APEX_HEIGHT = 110F;
    /** 降落段入场高度：~100 格高空直接出现（无淡入） */
    public static final float ENTRY_HEIGHT = 100F;
    /** 慢爬升段结束高度占比（相对 APEX；≈5 格） */
    public static final float H_SLOW_FRAC = 0.06F;

    // ======================= 发射段内曲线 =======================
    /** 发射窗口内"慢爬升"占比（u<U_SLOW 段 easeIn 起速，后段急加速） */
    public static final float U_SLOW = 0.35F;

    // 注意：显隐为硬切换，不做透明度淡入淡出（渲染器实例全 world 共享，
    // 透明度改值在多台发射台间易互相感染；且用户要求"直接隐藏"）。

    // ======================= 几何校准 =======================
    public static final float MODEL_SCALE = 1.0F;       // 模型倍率（glTF 单位 = 1 方块，已自动对齐）
    public static final float YAW_CORRECTION_DEG = 0.0F; // 朝向校准（模型前向 ≠ MC 前向时目视调）
    public static final boolean TEXTURE_V_FLIP = false;  // 贴图上下颠倒时置 true

    // ======================= 视距/包围盒 =======================
    /** 视距须覆盖顶点：玩家在水平 ~100 格外看 110 格高的火箭，距离 ~150 格 */
    public static final int VIEW_DISTANCE = 160;
    /** bbox 顶：BASE_Y + APEX_HEIGHT + 模型高(~14) + 余量 */
    public static final float RENDER_MAX_Y = 130F;
    public static final float RENDER_HALF_W = 6F;       // bbox 水平半宽（火箭 9 格宽 + 余量）

    private RocketLaunchKinematics() {
    }

    public enum RocketPhase { PARKED, LAUNCH, APEX_FREEZE, CRUISE_HIDDEN, DESCENT }

    /** 一帧飞行采样：visible=false 时渲染器负责淡出/不画；baseY 相对机器方块角。 */
    public record FlightSample(RocketPhase phase, boolean visible, float baseY) {
        public static final FlightSample PARKED_VISIBLE = new FlightSample(RocketPhase.PARKED, true, BASE_Y);
        public static final FlightSample HIDDEN = new FlightSample(RocketPhase.CRUISE_HIDDEN, false, BASE_Y);
    }

    /**
     * @param p       配方归一化进度 0..1（由调用方 clamp 后传入）
     * @param formed  机器成型（客户端 isFormed）
     * @param working 配方工作中（recipeLogic.isWorking()）
     * @return 本帧采样；无成型/待机 → PARKED 停靠。
     *         断电等待（WAITING/SUSPEND 且 p>0）的"画面冻结"由渲染器回填上一帧快照实现，
     *         本函数对冻结中的输入不保证平滑——调用方勿在冻结态调用即可。
     */
    public static FlightSample sample(float p, boolean formed, boolean working) {
        if (!formed) {
            return FlightSample.HIDDEN; // 结构散架：不可见（渲染器按 PARK_FADE_SEC 淡出）
        }
        p = Mth.clamp(p, 0.0F, 1.0F);
        if (!working || p <= 0.0F) {
            // IDLE / 刚开工 p=0：停靠台顶
            return FlightSample.PARKED_VISIBLE;
        }
        if (p <= LAUNCH_END_P) {
            // 发射段：慢起 → 急加速 → 顶点
            float u = p / LAUNCH_END_P;
            return new FlightSample(RocketPhase.LAUNCH, true, BASE_Y + launchHeight(u));
        }
        if (p <= LAUNCH_END_P + LAUNCH_TAIL_P) {
            // 顶点冻结（淡出交给渲染器）
            return new FlightSample(RocketPhase.APEX_FREEZE, true, BASE_Y + APEX_HEIGHT);
        }
        if (p < DESCENT_START_P) {
            // 巡航：入轨勘矿，视觉缺席
            return FlightSample.HIDDEN;
        }
        // 降落段：p=1 → u=1 → 精确落 BASE_Y（速度 0）
        float u = (p - DESCENT_START_P) / (1.0F - DESCENT_START_P);
        float drop = (ENTRY_HEIGHT - BASE_Y) * (1.0F - easeOutCubic(u));
        return new FlightSample(RocketPhase.DESCENT, true, BASE_Y + drop);
    }

    /**
     * 发射段高度曲线：u=0 起速为 0（慢爬 ~H_SLOW 格）→ 急加速 → u=1 到顶点（缓出便于冻结淡出）。
     */
    private static float launchHeight(float u) {
        float slowH = APEX_HEIGHT * H_SLOW_FRAC;
        if (u <= U_SLOW) {
            return slowH * easeInQuad(u / U_SLOW);
        }
        return slowH + (APEX_HEIGHT - slowH) * easeInOutCubic((u - U_SLOW) / (1.0F - U_SLOW));
    }

    private static float easeInQuad(float t) {
        return t * t;
    }

    /** 三次缓入缓出（标准公式，端点值精确 0/1，无累计误差） */
    private static float easeInOutCubic(float t) {
        return t < 0.5F ? 4F * t * t * t : 1.0F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
    }

    /** 三次缓出：导数 3(1-t)² 从 1 → 0（快进慢停，触底精确） */
    private static float easeOutCubic(float t) {
        return 1.0F - (float) Math.pow(1.0F - t, 3);
    }
}
