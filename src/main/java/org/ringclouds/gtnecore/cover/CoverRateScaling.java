package org.ringclouds.gtnecore.cover;

/**
 * 覆盖板每档传输速率（普通类，非 mixin）。
 *
 * GTCEu 原版公式（tier 6 后封顶，新档位不再增长）：
 * - 泵系（电动泵/流体调节器）：64 × 4^(tier-1)，min(tier-1, 5) 封顶 → 65536
 * - 传送带系（传送带/机械臂）：2 × 4^tier，min(tier, 6) 封顶 → 8192
 *
 * 重写：去掉封顶、按原倍率（×4/档）延续到新档位，int 安全封顶（2³¹-1）。
 */
public final class CoverRateScaling {

    private CoverRateScaling() {
    }

    /** 泵系：64 × 4^(tier-1)（tier 1=LV 起 64 mB/t，每档 ×4）。 */
    public static int pumpRate(int tier) {
        return (int) Math.min(Integer.MAX_VALUE, 64L << (2 * Math.max(0, tier - 1)));
    }

    /** 传送带系：2 × 4^tier（tier 1=LV 起 8 个/t，每档 ×4）。 */
    public static int conveyorRate(int tier) {
        return (int) Math.min(Integer.MAX_VALUE, 2L << (2 * Math.max(0, tier)));
    }
}
