package org.ringclouds.gtnecore.client.render;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.client.gltf.GltfParser;
import org.ringclouds.gtnecore.client.gltf.RocketTextureRegistrar;
import org.ringclouds.gtnecore.machine.SourceExtractorMachine;

/**
 * 魔源子素摄取仪"魔力环"动态渲染：渲染 KJS 顶环的 glTF 替身（VoxelBridge 导出，
 * 7×1×7 环面，坐标替身顶环层——环底在控制器 Y+4）。
 *
 * 复用火箭 glTF 管线：{@link GltfParser} 惰性解析 + {@link RocketTextureRegistrar}
 * 图集裁剪注册（Model↔Registrar 一一配对，纹理 RL 独立不与火箭共用）。
 * 旋转动画参考反曲率引擎蓝色立方：offsetTimer 驱动三轴慢转，绕环心旋转。
 * 仅成型时渲染（未成型顶环方块可见，无需替身）。
 */
@OnlyIn(Dist.CLIENT)
public class SourceRingRender extends DynamicRender<SourceExtractorMachine, SourceRingRender> {

    public static final Codec<SourceRingRender> CODEC = Codec.unit(SourceRingRender::new);
    public static final DynamicRenderType<SourceExtractorMachine, SourceRingRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** 魔力环 glTF 主文件（相对 uri 的 .bin/贴图同目录解析；RL 须带 .gltf） */
    private static final ResourceLocation MODEL_FILE =
            Gtnecore.id("models/ring/gltf/region_-460_66_620__-454_66_626.gltf");
    private static final ResourceLocation TEXTURE_RL = Gtnecore.id("dynamic/source_ring_atlas");

    /** 环底平面世界偏移（顶环块层 = 控制器 Y+4..+5） */
    private static final float RING_BASE_Y = 4.0F;

    private static final RenderType RING_RENDER_TYPE = RenderType.create(
            "gtnecore_source_ring",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            0x10000,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE_RL, false, false))
                    .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                    .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                    .createCompositeState(false));

    public static void init() {
        DynamicRenderManager.register(Gtnecore.id("source_extractor_ring"), TYPE);
    }

    // ---------- 惰性装载（渲染器实例字段；Model↔Registrar 一一配对） ----------
    private GltfParser.Model model;
    private boolean modelFailed;
    private final RocketTextureRegistrar textureRegistrar = new RocketTextureRegistrar(TEXTURE_RL);

    public SourceRingRender() {
    }

    @Override
    public DynamicRenderType<SourceExtractorMachine, SourceRingRender> getType() {
        return TYPE;
    }

    @Override
    public boolean shouldRender(SourceExtractorMachine machine, Vec3 cameraPos) {
        return true;
    }

    @Override
    public void render(SourceExtractorMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!machine.isFormed()) {
            return; // 未成型：顶环方块本体可见，不渲染替身
        }
        if (model == null && !modelFailed) {
            try {
                this.model = GltfParser.load(Minecraft.getInstance().getResourceManager(), MODEL_FILE);
                if (model == null || model.cornerCount == 0) {
                    this.modelFailed = true;
                }
            } catch (Exception e) {
                this.modelFailed = true;
            }
        }
        if (model == null || !textureRegistrar.ensure(model)) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, RING_BASE_Y, 0.5); // 机器方块角为原点：环心移到 (0.5, +4.5, 0.5) 前先落底
        poseStack.translate(0.0, 0.5, 0.0);        // 旋转中心 = 环体中心 y+4.5
        float anim = (machine.self().getOffsetTimer() + partialTick) * 6.0F; // 反曲率蓝色立方同款角速
        poseStack.mulPose(Axis.XP.rotationDegrees(anim));
        poseStack.mulPose(Axis.YP.rotationDegrees(anim * 0.7F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(anim * 0.5F));
        // glTF 已自动水平居中（xz=0）、贴地(y0..1) → 旋转中心即原点

        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(RING_RENDER_TYPE);
        float[] xyz = model.xyz, uv = model.uv, tint = model.tint;
        for (int i = 0; i < model.cornerCount; i++) {
            float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;
            if (tint != null) {
                r = tint[i * 4];
                g = tint[i * 4 + 1];
                b = tint[i * 4 + 2];
                a = tint[i * 4 + 3]; // gem 素材 alpha≈0.85 半透明紫
            }
            float u = uv != null ? uv[i * 2] : 0.0F;
            float v = uv != null ? uv[i * 2 + 1] : 0.0F;
            vc.vertex(mat, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .color(r, g, b, a)
                    .uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT)
                    .normal(0.0F, 1.0F, 0.0F)
                    .endVertex();
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SourceExtractorMachine machine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(SourceExtractorMachine machine) {
        var p = machine.getPos();
        return new AABB(p.getX() - 5.0, p.getY() - 1.0, p.getZ() - 5.0,
                p.getX() + 6.0, p.getY() + 7.0, p.getZ() + 6.0);
    }
}
