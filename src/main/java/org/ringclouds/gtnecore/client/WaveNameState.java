package org.ringclouds.gtnecore.client;

import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 波浪文字渲染的共享状态与参数（普通类，非 mixin）。
 *
 * 完整移植 1.21.1 项目（re_avaritia_enhance）AnimatedTextContext 设计：
 * PENDING → 首个文字行 consume → active → 渲染后 clear。三个 mixin 配合：
 * 1. GuiGraphicsWaveCaptureMixin —— renderTooltipInternal HEAD 读 tooltipStack
 *    判断物品是否带 #gtnecore:wave_name tag，设 PENDING（set-or-clear）；
 *    TAIL 清 PENDING + tooltipStack（对齐 1.21.1）。
 * 2. ClientTextTooltipConsumeMixin —— 第一个 renderText consume PENDING → active，
 *    RETURN 时 clear（后续行无效果）。
 * 3. FontStringRenderOutputMixin —— Font 逐字符回调 accept() 里按字符索引
 *    调整 y 坐标（字符提交前修改，不 cancel 不重绘）。
 *
 * 状态存 ThreadLocal：客户端渲染线程单线程执行，天然安全。
 */
public final class WaveNameState {

    /** 触发 tag：带此 tag 的物品，其翻译物品名逐字符波浪位移。 */
    public static final TagKey<Item> WAVE_TAG =
            TagKey.create(Registries.ITEM, new ResourceLocation("gtnecore", "wave_name"));

    /** 双层文字 tag：带此 tag 的物品名渲染双层（灰影底层 + 主色顶层）。 */
    public static final TagKey<Item> DOUBLE_LAYER_TAG =
            TagKey.create(Registries.ITEM, new ResourceLocation("gtnecore", "double_layer_text"));

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private WaveNameState() {
    }

    static final class State {
        /** tooltip 入口捕获：物品带 WAVE_TAG。 */
        boolean pending;
        /** 首个文字行消费后生效：当前行逐字符波浪。 */
        boolean wave;
        /** tooltip 入口捕获：物品带 DOUBLE_LAYER_TAG。 */
        boolean pendingDoubleLayer;
        /** 首个文字行消费后生效：当前行双层渲染。 */
        boolean doubleLayer;
    }

    /** 捕获点设置：带 tag 设 true，不带设 false（防止上次的 true 残留）。 */
    public static void setPending(boolean pending) {
        STATE.get().pending = pending;
    }

    /** 捕获点设置：双层文字。 */
    public static void setPendingDoubleLayer(boolean pending) {
        STATE.get().pendingDoubleLayer = pending;
    }

    /** renderTooltipInternal TAIL 清理（对齐 1.21.1）：PENDING → active 只消费一次。 */
    public static void clearPending() {
        State state = STATE.get();
        state.pending = false;
        state.pendingDoubleLayer = false;
    }

    /**
     * 第一个文字行调用：PENDING → active（只消费一次，之后 pending 为 false，
     * 后续文字行 consume 后 active 被清，天然只作用于物品名行）。
     */
    public static void consume() {
        State state = STATE.get();
        state.wave = state.pending;
        state.pending = false;
        state.doubleLayer = state.pendingDoubleLayer;
        state.pendingDoubleLayer = false;
    }

    /** 文字行渲染结束后清除 active。 */
    public static void clear() {
        State state = STATE.get();
        state.wave = false;
        state.doubleLayer = false;
    }

    /** 当前文字行是否需要波浪。 */
    public static boolean isWave() {
        return STATE.get().wave;
    }

    /** 当前文字行是否需要双层渲染。 */
    public static boolean isDoubleLayer() {
        return STATE.get().doubleLayer;
    }

    // ==================== 时间源 ====================

    /**
     * 平滑时间（tick 单位）：墙钟毫秒 / 50。
     *
     * 用墙钟而非 level.getGameTime() + partialTick —— 后者在渲染帧率低于游戏
     * tick 率（整合包掉帧）时 partialTick 每帧阶梯跳变，波浪会"一卡一卡"；
     * 墙钟时间毫秒级连续，任意帧率下都平滑。
     */
    public static float timeTicks() {
        return Util.getMillis() / 50.0F;
    }

    // ==================== 配置项（客户端配置，游戏内可改） ====================

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.DoubleValue WAVE_AMPLITUDE;
    public static final ForgeConfigSpec.DoubleValue WAVE_SPEED;
    public static final ForgeConfigSpec.DoubleValue WAVE_WAVELENGTH;
    /** EU 氧气分配机供氧区域覆盖层开关（同属 mod 客户端配置，默认开） */
    public static final ForgeConfigSpec.BooleanValue SHOW_OXYGEN_AREA;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        WAVE_AMPLITUDE = builder.comment("Wave animation amplitude in pixels (vertical offset range)")
                .defineInRange("waveAmplitude", 3.0, 0.0, 20.0);
        WAVE_SPEED = builder.comment("Wave animation speed in radians per tick (higher = faster)")
                .defineInRange("waveSpeed", 0.15, 0.01, 1.0);
        WAVE_WAVELENGTH = builder.comment("Wavelength in characters (larger = smoother, fewer characters per cycle)")
                .defineInRange("waveWavelength", 5.0, 2.0, 20.0);
        // 注意：一个 mod 只能注册一个 ModConfig.Type.CLIENT 配置——所有客户端项都放这一个 spec
        SHOW_OXYGEN_AREA = builder.comment("Render the EU oxygen distributor's distributed area as a translucent shell",
                        " (same as Ad Astra's Show Oxygen Distributor Area). Default: true.")
                .define("showOxygenArea", true);
        CLIENT_SPEC = builder.build();
    }

    /**
     * 字符 Y 轴偏移：-A × cos(t × speed + i × 2π / wavelength)
     * （与 1.21.1 项目公式一致）
     *
     * 配置值 250ms 缓存一次（热重载生效延迟可忽略），避免每字符多次
     * ForgeConfigSpec.get() 的开销（叠加渲染时每字符调用频繁）。
     */
    public static float computeYOffset(int charIndex) {
        long now = System.currentTimeMillis();
        if (now - lastConfigRefresh > 250) {
            lastConfigRefresh = now;
            cachedAmplitude = WAVE_AMPLITUDE.get().floatValue();
            cachedSpeed = WAVE_SPEED.get().floatValue();
            cachedWavelength = WAVE_WAVELENGTH.get().floatValue();
        }
        float phase = (float) (charIndex * 2.0 * Math.PI / cachedWavelength);
        return -cachedAmplitude * (float) Math.cos(timeTicks() * cachedSpeed + phase);
    }

    private static volatile float cachedAmplitude = 3.0f;
    private static volatile float cachedSpeed = 0.15f;
    private static volatile float cachedWavelength = 5.0f;
    private static volatile long lastConfigRefresh;
}
