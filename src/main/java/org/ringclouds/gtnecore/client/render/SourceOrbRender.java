package org.ringclouds.gtnecore.client.render;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.client.gltf.GltfParser;
import org.ringclouds.gtnecore.client.gltf.RocketTextureRegistrar;
import net.minecraft.resources.ResourceLocation;
import org.ringclouds.gtnecore.machine.SourceExtractorMachine;

import java.util.HashMap;
import java.util.Map;

/**
 * 魔源子素摄取仪"存量魔源球"动态渲染：obj 模型（DemonicSource，单位立方
 * 中心化），位于魔力环内（局部中心 (0.5, +4.5, 0.5)），半径随机器魔源池存量
 * 平滑变化（0.3→1.2 格 + 轻微呼吸）。贴图 emissive 全亮。
 *
 * DynamicRender 实例全 world 共享 → 平滑半径缓存按机器 pos 隔离（Map）。
 */
@OnlyIn(Dist.CLIENT)
public class SourceOrbRender extends DynamicRender<SourceExtractorMachine, SourceOrbRender> {

    public static final Codec<SourceOrbRender> CODEC = Codec.unit(SourceOrbRender::new);
    public static final DynamicRenderType<SourceExtractorMachine, SourceOrbRender> TYPE =
            new DynamicRenderType<>(CODEC);

    private static final float ORB_CENTER_Y = 4.5F;
    /** 空池/满池半径（模型单位球半径 0.5 → scale = 半径 × 2） */
    private static final float R_MIN = 0.3F, R_MAX = 1.2F;
    private static final ResourceLocation MODEL_FILE = Gtnecore.id(
            "models/obj/demonic_source_converted.gltf");

    private static final RenderType ORB_RENDER_TYPE = RenderType.create(
            "gtnecore_source_orb",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            0x10000,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            GameRenderer::getRendertypeEntityTranslucentEmissiveShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            Gtnecore.id("textures/obj/demonic_source.png"), false, false))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                    .createCompositeState(true));

    public static void init() {
        DynamicRenderManager.register(Gtnecore.id("source_extractor_orb"), TYPE);
    }

    /** 每台机器的当前平滑半径 */
    private final Map<BlockPos, Float> currentRadius = new HashMap<>();

    private GltfParser.Model model;
    private boolean modelFailed;
    private final RocketTextureRegistrar textureRegistrar = new RocketTextureRegistrar(
            Gtnecore.id("textures/obj/demonic_source.png"));

    public SourceOrbRender() {
    }

    @Override
    public DynamicRenderType<SourceExtractorMachine, SourceOrbRender> getType() {
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
            currentRadius.remove(machine.getPos());
            return;
        }
        float ratio = Mth.clamp(machine.getStoredSource() / (float) SourceExtractorMachine.MAX_SOURCE, 0.0F, 1.0F);
        float target = Mth.lerp(ratio, R_MIN, R_MAX);
        BlockPos pos = machine.getPos();
        float cur = currentRadius.getOrDefault(pos, 0.2F);
        float dt = Minecraft.getInstance().getDeltaFrameTime() / 1000.0F;
        float next = cur + (target - cur) * Math.min(1.0F, dt * 2.0F);
        currentRadius.put(pos, next);
        if (next < 0.02F) {
            return;
        }
        // 呼吸微调（offsetTimer）
        long tick = machine.self().getOffsetTimer();
        float radius = next * (1.0F + 0.04F * Mth.sin((tick + partialTick) * 0.35F));

        // glTF 管线（GltfParser 角点流，obj→gltf 预转换；贴图 Registrar 注册）
        if (model == null && !modelFailed) {
            try {
                this.model = GltfParser.load(Minecraft.getInstance().getResourceManager(), MODEL_FILE);
                if (this.model == null || this.model.cornerCount == 0) {
                    this.modelFailed = true;
                } else {
                    // 几何竖直居中：GltfParser 默认"最低点贴 y=0"，会让 scale 绕球底缩放——
                    // 池量一变球心就上下漂（半径 0.3→1.2 时球心 4.8→5.7，与固定 4.5 的环心失同心）
                    GltfParser.centerVertically(this.model);
                }
            } catch (Exception e) {
                this.modelFailed = true;
            }
        }
        if (model == null || !textureRegistrar.ensure(model)) {
            poseStack.popPose();
            return;
        }

        // 角点流渲染；模型中心化 + 池量缩放（半径 0.3→1.2 = scale 0.6→2.4）
        poseStack.pushPose();
        poseStack.translate(0.5, ORB_CENTER_Y, 0.5);
        poseStack.scale(radius * 2.0F, radius * 2.0F, radius * 2.0F);
        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(ORB_RENDER_TYPE);
        float[] xyz = model.xyz, uv = model.uv, tint = model.tint;
        for (int i = 0; i < model.cornerCount; i++) {
            float u = uv != null ? uv[i * 2] : 0.0F;
            float v = uv != null ? uv[i * 2 + 1] : 0.0F;
            vc.vertex(mat, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .color(1.0F, 1.0F, 1.0F, 1.0F)
                    .uv(u, v)
                    .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(0xF000F0)
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
        return new AABB(p.getX() - 3.0, p.getY() + 1.0, p.getZ() - 3.0,
                p.getX() + 4.0, p.getY() + 7.0, p.getZ() + 4.0);
    }
}
