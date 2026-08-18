package org.ringclouds.gtnecore.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 访问 Font.renderChar（1.20.1 为包私有 void 方法）：
 * 双层文字渲染需要同一字符绘制两次，直接调用需绕过可见性。
 * （1.21.1 项目同款设计）
 */
@Mixin(Font.class)
public interface FontInvoker {

    @Invoker("renderChar")
    void gtne$invokeRenderChar(BakedGlyph glyph, boolean bold, boolean italic, float boldOffset,
                               float x, float y, Matrix4f matrix, VertexConsumer consumer,
                               float r, float g, float b, float a, int light);
}
