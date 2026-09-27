package org.ringclouds.gtnecore.research;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

/**
 * 「需要完成某项研究」配方条件。
 *
 * <p>挂上它的配方，未完成对应研究时机器不会运行；JEI 配方页会**自动**多出一行
 * {@link #getTooltips()} 的文字（GTCEu 的 {@code GTRecipeWidget} 对所有
 * {@code RecipeCondition} 一视同仁地渲染 tooltip——这一点由
 * {@code VacuumRecipeInjector} 的实战确认，不需要我们碰 XEI 渲染）。
 *
 * <h2>命名</h2>
 * GTCEu 自己已有 {@code gtceu:research}（{@code common.recipe.condition.ResearchCondition}），
 * 服务于 GT 研究站。本类服务于我们自己的研究系统，注册 id 用 {@code gtnecore:research}，
 * 类名也刻意区分。
 *
 * <h2>⚠ 必须自己覆写下面三个方法</h2>
 * {@code RecipeConditionType} **没有 name 字段**，其 {@code toString()} 是 Object 默认实现。
 * 而基类的默认实现是：
 * <pre>
 * getTranslationKey()   → "gtceu.recipe.condition." + getType()   // → 对象地址，永远匹配不到
 * getInValidTexture()   → "block/condition/" + getType() + "_invalid"
 * getValidTexture()     → "block/condition/" + getType() + "_valid"
 * </pre>
 * 所以直接用基类版本会拿到无效路径与无效翻译键。**三个都要覆写**。
 */
public class GtneResearchCondition extends RecipeCondition<GtneResearchCondition> {

    /** 字段名（JSON 里写 {@code "research": "gtnecore:advanced_alloy_smelting"}）。 */
    public static final String FIELD = "research";

    /**
     * 带字段的条件 codec。
     *
     * <p>写法照官方带字段的条件（如 {@code DimensionCondition}）：
     * {@code RecordCodecBuilder.create(inst -> inst.group(
     *     RecipeCondition.isReverse(inst),
     *     Xxx.CODEC.fieldOf("字段名").forGetter(...)
     * ).apply(inst, 构造函数))}。
     *
     * <p>{@code research} 用 {@code Codec.STRING} 而不是 {@code ResourceLocation.CODEC}：
     * 坏 id 不会让整条配方反序列化失败，解析留给运行时（{@link #getResearch()} 会返回 null）。
     */
    /**
     * 条件 codec。
     *
     * <p>不用 {@code RecordCodecBuilder} + {@code RecipeCondition.isReverse(inst)}：
     * 那个写法在 Java 泛型推断上过不去（{@code Mu<RC>} 与 {@code RC} 无法统一）。
     * 改用直白的 {@code RecordCodecBuilder.mapCodec}：
     * <pre>
     * { "is_reverse": false, "research": "gtnecore:xxx", "display_name": "XXX" }
     * </pre>
     *
     * <p>{@code research} 用 {@code Codec.STRING} 而不是 {@code ResourceLocation.CODEC}：
     * 坏 id 不会让整条配方反序列化失败，解析留给运行时
     * （{@link #getResearch()} 对非法 id 返回 null）。
     */
    public static final Codec<GtneResearchCondition> CODEC =
            RecordCodecBuilder.<GtneResearchCondition>mapCodec(inst -> inst.group(
                    Codec.BOOL.optionalFieldOf("is_reverse", false)
                            .forGetter(GtneResearchCondition::isReverse),
                    Codec.STRING.optionalFieldOf(FIELD, "").forGetter(GtneResearchCondition::getResearchId),
                    Codec.STRING.optionalFieldOf("display_name", "").forGetter(GtneResearchCondition::getDisplayName)
            ).apply(inst, (isReverse, researchId, displayName) -> {
                GtneResearchCondition condition = new GtneResearchCondition(isReverse);
                condition.researchId = orEmpty(researchId);
                condition.displayName = orEmpty(displayName);
                return condition;
            })).codec();

    /** 不需要反向条件时用的安全读法（{@code tryParse} 对坏值返回 null）。 */
    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /** 研究 id 的字符串形式（序列化用；解析失败时为空串）。 */
    private String researchId = "";
    /** 客户端展示用：研究名（服务端同步过来，避免客户端拿不到定义表）。 */
    private String displayName = "";

    /** 反序列化用的空构造（字段随后由 codec 填充）。 */
    public GtneResearchCondition() {
    }

    public GtneResearchCondition(boolean isReverse) {
        super(isReverse);
    }

    public GtneResearchCondition(@Nullable ResourceLocation research, @Nullable String displayName) {
        this.researchId = research == null ? "" : research.toString();
        this.displayName = displayName == null ? "" : displayName;
    }

    @Override
    public RecipeConditionType<GtneResearchCondition> getType() {
        return GtneResearchConditions.RESEARCH;
    }

    // ==================== 判定 ====================

    @Override
    protected boolean testCondition(GTRecipe recipe, RecipeLogic logic) {
        // 客户端没有进度数据 → 返回 false（JEI 那边只看 tooltip，不看这个返回值）
        var progress = ResearchProgress.getOrNull(
                logic == null || logic.machine == null || logic.machine.self() == null
                        ? null
                        : logic.machine.self().getLevel());
        if (progress == null) {
            return false;
        }
        return progress.isCompleted(getResearch());
    }

    // ==================== 展示（必须覆写，见类注释） ====================

    @Override
    public String getTranslationKey() {
        return "gtnecore.recipe.condition.research";
    }

    @Override
    public Component getTooltips() {
        // displayName 由注入器在服务端填好并随条件同步到客户端；
        // 万一为空（手写 JSON 没填）就退回 id 字符串，别显示空白。
        String name = displayName.isEmpty() ? researchId : displayName;
        return Component.translatable("gtnecore.recipe.condition.research.tooltip", name);
    }

    /**
     * 未满足时的角标。
     *
     * <p>复用 GTCEu 自己的研究条件贴图（{@code gtceu:textures/gui/...}），
     * 省一张自制贴图；将来要专属图标时换这里的路径即可。
     */
    @Override
    public IGuiTexture getInValidTexture() {
        return new ResourceTexture("gtceu:textures/block/condition/research_invalid.png");
    }

    @Override
    public IGuiTexture getValidTexture() {
        return new ResourceTexture("gtceu:textures/block/condition/research_valid.png");
    }

    // ==================== 序列化 ====================

    /** 研究 id；字符串非法时返回 null。 */
    public @Nullable ResourceLocation getResearch() {
        return ResourceLocation.tryParse(researchId);
    }

    /** 研究 id 的字符串形式（可能为空串）。 */
    public String getResearchId() {
        return researchId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setResearch(@Nullable ResourceLocation research) {
        this.researchId = research == null ? "" : research.toString();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
    }

    @Override
    public GtneResearchCondition createTemplate() {
        return new GtneResearchCondition();
    }
}
