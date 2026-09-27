package org.ringclouds.gtnecore.client.render;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 维格纳-亨廷顿对顶砧"压砧运动学"（纯函数，无 MC 渲染/GL 依赖，只 import Mth/常数）。
 *
 * 配方进度 p∈[0,1]（客户端 {@code RecipeLogic.getProgressPercent}，与配方时长无关）驱动：
 *
 *   静息（成型未开工）：上下砧各自停在砧体两端（下砧贴底、上砧顶贴顶）
 *   压制段 p∈[CLOSE_START_P, CLOSE_END_P]：两砧平滑相向移动，贴到只剩 PRESS_GAP 格
 *   保压段 p∈[CLOSE_END_P, RETURN_START_P]：保持压紧
 *   归位段 p∈[RETURN_START_P, 1]：**配方结束前**两砧就平滑退回静息位（p=1 时已到位，无跳变）
 *   蓝球：p∈[ORB_VISIBLE_P, RETURN_START_P) 渲染 —— 早期预备涨起、随配方推进缩小，
 *         砧一开始归位就不再渲染（金属氢已经"压进去了"）
 *   中断兜底：配方中途停下（断电/断结构）由渲染器按 RELEASE_SECONDS 线性平滑回位
 *
 * 所有常数集中在类顶，目视校准只改这里、勿动配方。
 */
@OnlyIn(Dist.CLIENT)
public final class AnvilPressKinematics {

    // ======================= 相位窗口（配方进度占比，200t 配方 1%≈0.1s） =======================
    /** 开始闭合（0.5s） */
    public static final float CLOSE_START_P = 0.05F;
    /** 完全闭合（4.0s）——闭合段时长 ≈3.5 秒（用户 2026-09-12 定） */
    public static final float CLOSE_END_P = 0.40F;
    /**
     * 开始归位（5.5s）：闭合到位后**愣神 ~1.5 秒**再松手（用户要求"愣神一小下"）。
     * 归位从这一刻一直走到配方结束（p=1，≈4.5 秒的缓松），末尾不留空档。
     */
    public static final float RETURN_START_P = 0.55F;
    /** 蓝球出现（配方一开工就预备渲染） */
    public static final float ORB_VISIBLE_P = 0.0F;
    /** 蓝球涨到最大：开工 1~2 秒内（200t 配方 p=0.12 ≈ 1.2s）——此后立即开始缩小 */
    public static final float ORB_FULL_P = 0.12F;
    /**
     * 蓝球缩到 ORB_END_FRAC 的时刻：**与闭合段同步**（砧压到位时球也缩到位）。
     * 原先铺满整段配方（缩到 RETURN_START_P）会导致"砧都不动了球才开始缩"（用户实测）。
     */
    public static final float ORB_SHRINK_END_P = 0.40F;
    /** 缩小段结束时球缩到的比例（相对 ORB_RADIUS） */
    public static final float ORB_END_FRAC = 0.10F;
    /** 工作中断时的松手回位时长（秒）：线性平滑，0 = 瞬回 */
    public static final float RELEASE_SECONDS = 0.9F;

    // ======================= 几何（格 / 图案坐标系） =======================
    /** 两砧面压到底时的最小间隙 */
    public static final float PRESS_GAP = 0.5F;
    /** 静息位相对砧体端面的内缩微调（正 = 更靠中间） */
    public static final float ANVIL_INSET = 0.0F;
    /** 模型倍率（glTF 单位 = 1 方块，GltfParser 已自动对齐：水平居中、最低点 y=0） */
    public static final float MODEL_SCALE = 1.0F;
    /** 模型朝向校准（导出模型的前向 ≠ 结构前向时目视调：0/90/180/270） */
    public static final float MODEL_YAW_DEG = 0.0F;
    /**
     * 两砧/球水平位置微调（**图案系** X/Z，格）：导出模型按自身包围盒居中，与砧体中心可能差半格。
     * 用图案系而非世界系 —— 模型本身跟着结构朝向转，微调也必须跟着转才一致。
     * 方向若反了直接改符号（用户 2026-09-12 首轮实测：正号偏得更远，正确方向为负）。
     */
    public static final float MODEL_NUDGE_X = -0.5F;
    public static final float MODEL_NUDGE_Z = -0.5F;
    /** 金属氢球半径（格）：最大尺寸时（开工 ~1.2 秒内）的实际半径 */
    public static final float ORB_RADIUS = 1.5F;
    /**
     * 球心 Y：**相对控制器方块角**的固定偏移（用户 2026-09-12 定）。
     * 本值现在就是**真正的球心**高度（渲染端把球几何竖直居中了，缩放绕球心而非球底）。
     * 注：修复"越缩越低"之前，模型最低点贴在锚点上 → 最大尺寸时视觉球心 = 锚点 + ORB_RADIUS，
     * 故此处从 10.5 补偿到 11.5（≈10.5+1.15），让满径时的观感与用户刚调好的那版一致。
     */
    public static final float ORB_Y_FROM_CONTROLLER = 11.0F;
    /** 球心叠加微调（格；正 = 上移） */
    public static final float ORB_Y_NUDGE = 0.0F;

    // ======================= 亮度（砧体在结构内部，机器光照 + 导出顶点 AO 容易偏暗） =======================
    /** 方块光照下限（0..15；0 = 用真实光照）。把内部偏暗的砧提到可看清 */
    public static final int ANVIL_LIGHT_FLOOR = 8;
    /** 顶点乘色下限（0..1；1 = 不提升）。导出 COLOR_0 常烘焙了 AO 暗部，抬底防死黑 */
    public static final float TINT_FLOOR = 0.5F;

    // ======================= 视距/包围盒 =======================
    public static final int VIEW_DISTANCE = 192;
    /** 渲染包围盒：以控制器为中心的水平半宽 / 顶部高度（砧体在中段，斜跨 ~10 格） */
    public static final float RENDER_HALF_W = 14F;
    public static final float RENDER_TOP_Y = 28F;

    private AnvilPressKinematics() {
    }

    /**
     * 压制紧度 0..1：闭合段上升 → 保压段保持 1 → 归位段（配方结束前）平滑回 0。
     * p=1 时精确回到 0（无跳变，配方完成瞬间砧已就位）。
     */
    public static float closeFor(float progress) {
        float p = Mth.clamp(progress, 0.0F, 1.0F);
        if (p <= CLOSE_START_P) return 0.0F;
        if (p < CLOSE_END_P) {
            return easeInOutCubic((p - CLOSE_START_P) / (CLOSE_END_P - CLOSE_START_P));
        }
        if (p < RETURN_START_P) return 1.0F;
        return 1.0F - easeInOutCubic((p - RETURN_START_P) / Math.max(1.0E-4F, 1.0F - RETURN_START_P));
    }

    /** 金属氢球可见性：配方早期出现，砧开始归位即消失。 */
    public static boolean orbVisible(float progress, boolean working) {
        float p = Mth.clamp(progress, 0.0F, 1.0F);
        return working && p >= ORB_VISIBLE_P && p < RETURN_START_P;
    }

    /**
     * 金属氢球缩放（相对 {@link #ORB_RADIUS}）：
     * 开工涨起 → 与压砧同步缩小（ORB_FULL_P→ORB_SHRINK_END_P，同款缓动）→ 之后保持 ORB_END_FRAC。
     */
    public static float orbScale(float progress) {
        float p = Mth.clamp(progress, 0.0F, 1.0F);
        if (p <= ORB_VISIBLE_P) return 0.0F;
        if (p < ORB_FULL_P) {
            return easeInOutCubic((p - ORB_VISIBLE_P) / Math.max(1.0E-4F, ORB_FULL_P - ORB_VISIBLE_P));
        }
        if (p >= ORB_SHRINK_END_P) return ORB_END_FRAC;
        float u = easeInOutCubic((p - ORB_FULL_P) / Math.max(1.0E-4F, ORB_SHRINK_END_P - ORB_FULL_P));
        return Mth.lerp(u, 1.0F, ORB_END_FRAC);
    }

    /** 静息时下砧底平面 Y（图案系，相对控制器方块角；下砧贴砧体底）。 */
    public static float restLowerY(int[] bounds) {
        return bounds[2] + ANVIL_INSET;
    }

    /** 静息时上砧底平面 Y（上砧顶面贴砧体顶部上方一格）。 */
    public static float restUpperY(int[] bounds, float modelUpHeight) {
        return bounds[3] + 1.0F - modelUpHeight - ANVIL_INSET;
    }

    /** 每侧可压缩的总行程（两砧面对面距离 - 保压间隙；封闭不足时为 0）。 */
    public static float travel(float restLowerY, float restUpperY, float modelDownHeight) {
        return Math.max(0.0F, restUpperY - (restLowerY + modelDownHeight) - PRESS_GAP);
    }

    /** 三次缓入缓出（标准公式，端点精确 0/1）。 */
    private static float easeInOutCubic(float t) {
        return t < 0.5F ? 4F * t * t * t : 1.0F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
    }
}
