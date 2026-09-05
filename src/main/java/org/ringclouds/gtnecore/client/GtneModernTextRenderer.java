package org.ringclouds.gtnecore.client;

import icyllis.modernui.mc.text.ModernTextRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.lang.reflect.Field;

/**
 * Modern UI 渲染管线兼容层（客户端专用，服务端不加载）。
 *
 * Modern UI 的 MixinFontRenderer @Overwrite 了原版 Font 的全部 drawInBatch ——
 * 原版 Font$StringRenderOutput 逐字符回调不再触发，波浪/双层效果失效。
 * 本类对带特效的文字改用 Modern UI 的 ModernTextRenderer 逐字符渲染
 * （保留 Modern UI 的养眼字体），复刻原版效果语义：
 * - 波浪：每字符 y 按 WaveNameState.computeYOffset(index) 偏移（逐字符独立渲染，
 *   无"撤销上一个偏移"需求）
 * - 双层：每字符两遍 —— 底层灰（0.4×dim，波浪时反向半幅度：y - 0.5×offset）
 *   + 顶层主色（正向波浪）；shadow 原样传参（Modern UI 自行处理阴影层）
 *
 * ModernTextRenderer 实例 = MixinFontRenderer 注入到 Font 的
 * modernUI_MC$textRenderer 实例字段（反射读取，mixin 注入字段名固定）。
 */
public final class GtneModernTextRenderer {

    private static Field modernRendererField;

    private GtneModernTextRenderer() {
    }

    /** Modern UI 是否接管了字体渲染（反射字段存在且非空）。 */
    public static boolean isModernUi(Font font) {
        return getRenderer(font) != null;
    }

    /** 反射获取 ModernTextRenderer 实例；无 Modern UI（字段不存在）时返回 null。 */
    private static ModernTextRenderer getRenderer(Font font) {
        try {
            if (modernRendererField == null) {
                modernRendererField = Font.class.getDeclaredField("modernUI_MC$textRenderer");
                modernRendererField.setAccessible(true);
            }
            return (ModernTextRenderer) modernRendererField.get(font);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * 用 Modern UI 管线渲染一行文字（波浪/双层语义对齐原版实现）。
     * 返回渲染宽度。非 Modern UI 环境返回 0（调用方应回退原路径）。
     */
    public static float drawWaveLine(Font font, FormattedCharSequence text, float x, float y,
                                     int color, boolean shadow, Matrix4f pose, MultiBufferSource buffer,
                                     Font.DisplayMode mode, int bgColor, int light) {
        ModernTextRenderer renderer = getRenderer(font);
        if (renderer == null) {
            return 0.0F;
        }
        boolean wave = WaveNameState.isWave();
        boolean doubleLayer = WaveNameState.isDoubleLayer();
        if (!wave && !doubleLayer) {
            return renderer.drawText(text, x, y, color, shadow, pose, buffer, mode, bgColor, light);
        }
        // 逐字符渲染：波浪需要每字符独立 y 偏移（逐字符调用无连字，波浪动画下可接受）
        float dim = shadow ? 0.25f : 1.0f;
        int gray = (int) (0.4f * dim * 255.0f);
        int grayColor = (color & 0xFF000000) | (gray << 16) | (gray << 8) | gray;
        final float[] cursor = {x};
        text.accept((index, style, codePoint) -> {
            float offset = wave ? WaveNameState.computeYOffset(index) : 0.0F;
            float charY = y + offset;
            String chars = new String(Character.toChars(codePoint));
            Component ch = Component.literal(chars).withStyle(style);
            FormattedCharSequence fcs = ch.getVisualOrderText();
            if (doubleLayer) {
                // 底层灰：波浪时反向半幅度（y - 0.5×offset，相对波浪 y 即 -1.5×offset，对齐原版双层）。
                // 必须去掉 style 颜色——drawText 的 color 参数只是默认色，style 色优先，
                // 带颜色的灰层会继承物品名的 rarity 色（"灰影带颜色"）
                float bottomY = wave ? y - 0.5f * offset : y;
                FormattedCharSequence grayFcs = Component.literal(chars)
                        .withStyle(style.withColor((net.minecraft.network.chat.TextColor) null)).getVisualOrderText();
                renderer.drawText(grayFcs, cursor[0], bottomY, grayColor, shadow, pose, buffer, mode, bgColor, light);
            }
            // 注意：drawText(FormattedCharSequence) 返回"x + 宽度"（绝对终点），不是增量宽度——
            // 必须直接赋值（写成 += 会让光标翻倍偏移，后续字符画到屏幕外）
            cursor[0] = renderer.drawText(fcs, cursor[0], charY, color, shadow, pose, buffer, mode, bgColor, light);
            return true;
        });
        return cursor[0] - x;
    }
}
