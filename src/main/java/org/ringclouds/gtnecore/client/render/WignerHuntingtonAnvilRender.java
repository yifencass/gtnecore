package org.ringclouds.gtnecore.client.render;

import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.client.gltf.GltfParser;
import org.ringclouds.gtnecore.client.gltf.RocketTextureRegistrar;
import org.ringclouds.gtnecore.machine.WignerHuntingtonAnvilMachine;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;

/**
 * 维格纳-亨廷顿对顶砧"对撞砧 + 金属氢球"动态渲染（用户 2026-09-12 二阶段）：
 *
 * - 成型后砧体（钻石晶格）已被 {@code StructureGhostBlock} 隐形（服务端隐藏，见机器类），
 *   这里按同一份结构数据把两个 glTF 对撞砧摆到砧体位置：上砧在上、下砧在下
 * - 配方进行时两砧相向压合（{@link AnvilPressKinematics} 曲线），快完成时中间渲染蓝色金属氢球
 * - 工作中止/解体 → 按 RELEASE_SECONDS 线性平滑回位
 *
 * 摆放用**结构朝向档**（{@code machine.getGhostRotation()}，服务端识别并同步）驱动
 * pose 的 Y 轴旋转，与图案系偏移一一对应，无需再做世界朝向换算。
 *
 * DynamicRender 实例全 world 共享 → 松开动画状态按机器 pos 隔离（Map）。
 */
@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WignerHuntingtonAnvilRender
        extends DynamicRender<WignerHuntingtonAnvilMachine, WignerHuntingtonAnvilRender> {

    public static final Codec<WignerHuntingtonAnvilRender> CODEC = Codec.unit(WignerHuntingtonAnvilRender::new);
    public static final DynamicRenderType<WignerHuntingtonAnvilMachine, WignerHuntingtonAnvilRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** 上/下对撞砧 glTF（两个导出共用同一套图集，资源目录见 assets/gtnecore/models/anvil/gltf） */
    private static final ResourceLocation MODEL_UP =
            Gtnecore.id("models/anvil/gltf/region_-448_65_615__-440_70_623.gltf");
    private static final ResourceLocation MODEL_DOWN =
            Gtnecore.id("models/anvil/gltf/region_-448_66_626__-440_71_634.gltf");
    /** 金属氢球：魔源球模型的蓝色副本（贴图 HSV 色相旋转，见 models/anvil/orb） */
    private static final ResourceLocation MODEL_ORB =
            Gtnecore.id("models/anvil/orb/demonic_source_converted.gltf");
    private static final ResourceLocation ORB_TEXTURE =
            Gtnecore.id("models/anvil/orb/textures/demonic_source.png");

    /** 动态图集注册名（GltfParser 的 8192² 图集按 UV 裁剪后注册到这里） */
    private static final ResourceLocation ATLAS_UP = Gtnecore.id("dynamic/anvil_up_atlas");
    private static final ResourceLocation ATLAS_DOWN = Gtnecore.id("dynamic/anvil_down_atlas");

    /**
     * 对撞砧渲染类型：TRIANGLES + 大缓冲的"实体半透明"（照抄发射台火箭的那套）。
     * 不能用 RenderType.entityTranslucent（QUADS + 256 顶点小缓冲），三角形流必错位。
     */
    private static RenderType anvilRenderType(ResourceLocation texture) {
        return RenderType.create(
                "gtnecore_anvil_" + texture.getPath().replace('/', '_'),
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES,
                0x30000,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(
                                GameRenderer::getRendertypeEntityTranslucentShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                        .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                        .createCompositeState(false));
    }

    /** 金属氢球渲染类型：全亮自发光（照抄摄取仪魔源球：emissive 着色器 + 不透明写深度） */
    private static final RenderType ORB_RENDER_TYPE = RenderType.create(
            "gtnecore_anvil_orb",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            0x10000,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            GameRenderer::getRendertypeEntityTranslucentEmissiveShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(ORB_TEXTURE, false, false))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                    .createCompositeState(true));

    public static void init() {
        DynamicRenderManager.register(Gtnecore.id("wigner_huntington_anvil"), TYPE);
    }

    /** 每台机器的松开动画状态（共享渲染器实例 → 按 pos 隔离） */
    private static final class Anim {
        float closeness;
        boolean releasing;
        float releaseFrom;
        float releaseT;
    }

    private final Map<BlockPos, Anim> anims = new HashMap<>();

    private GltfParser.Model upModel;
    private GltfParser.Model downModel;
    private GltfParser.Model orbModel;
    private boolean modelsTried;

    /** 模型包围盒高度（GltfParser 已把最低点归零 → maxY 即高度） */
    private float upHeight;
    private float downHeight;

    private final RocketTextureRegistrar upTextureRegistrar = new RocketTextureRegistrar(ATLAS_UP);
    private final RocketTextureRegistrar downTextureRegistrar = new RocketTextureRegistrar(ATLAS_DOWN);

    public WignerHuntingtonAnvilRender() {
    }

    @Override
    public DynamicRenderType<WignerHuntingtonAnvilMachine, WignerHuntingtonAnvilRender> getType() {
        return TYPE;
    }

    @Override
    public boolean shouldRender(WignerHuntingtonAnvilMachine machine, Vec3 cameraPos) {
        return true;
    }

    @Override
    public boolean shouldRenderOffScreen(WignerHuntingtonAnvilMachine machine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return AnvilPressKinematics.VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(WignerHuntingtonAnvilMachine machine) {
        BlockPos p = machine.getPos();
        float h = AnvilPressKinematics.RENDER_HALF_W;
        return new AABB(p.getX() - h, p.getY() - 2.0, p.getZ() - h,
                p.getX() + h + 1.0, p.getY() + AnvilPressKinematics.RENDER_TOP_Y, p.getZ() + h + 1.0);
    }

    @Override
    public void render(WignerHuntingtonAnvilMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockPos pos = machine.getPos();
        if (!machine.isFormed()) {
            anims.remove(pos);
            return;   // 未成型：砧体还是真实方块（幽灵未生效），不画模型
        }

        var logic = machine.getRecipeLogic();
        boolean working = logic != null && logic.isWorking();
        float progress = logic != null ? (float) logic.getProgressPercent() : 0.0F;

        Anim anim = anims.computeIfAbsent(pos, k -> new Anim());
        float dt = Minecraft.getInstance().getDeltaFrameTime() / 1000.0F;
        if (working) {
            anim.closeness = AnvilPressKinematics.closeFor(progress);
            anim.releasing = false;
        } else if (anim.closeness > 0.001F) {
            // 工作结束/中断：线性平滑回位
            if (!anim.releasing) {
                anim.releasing = true;
                anim.releaseFrom = anim.closeness;
                anim.releaseT = 0.0F;
            }
            anim.releaseT += dt;
            float u = AnvilPressKinematics.RELEASE_SECONDS <= 0.0F ? 1.0F
                    : Mth.clamp(anim.releaseT / AnvilPressKinematics.RELEASE_SECONDS, 0.0F, 1.0F);
            anim.closeness = anim.releaseFrom * (1.0F - u);
            if (u >= 1.0F) {
                anim.closeness = 0.0F;
                anim.releasing = false;
            }
        }

        loadModels();
        if (upModel == null || downModel == null) return;
        if (!upTextureRegistrar.ensure(upModel) || !downTextureRegistrar.ensure(downModel)) return;

        int[] bounds = WignerHuntingtonAnvilMachine.diamondBounds();
        int rot = Math.max(0, machine.getGhostRotation());
        float restLower = AnvilPressKinematics.restLowerY(bounds);
        float restUpper = AnvilPressKinematics.restUpperY(bounds, upHeight);
        float travel = AnvilPressKinematics.travel(restLower, restUpper, downHeight);
        float half = anim.closeness * travel * 0.5F;
        float lowerY = restLower + half;
        float upperY = restUpper - half;

        // 砧体中心（图案系偏移，相对控制器）：pose 先转到结构朝向档，再按图案系平移
        float centerX = (bounds[0] + bounds[1] + 1) * 0.5F;
        float centerZ = (bounds[4] + bounds[5] + 1) * 0.5F;

        RenderType anvilUpType = anvilRenderType(ATLAS_UP);
        RenderType anvilDownType = anvilRenderType(ATLAS_DOWN);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(rot * 90.0F + AnvilPressKinematics.MODEL_YAW_DEG));
        poseStack.translate(centerX + AnvilPressKinematics.MODEL_NUDGE_X, 0.0,
                centerZ + AnvilPressKinematics.MODEL_NUDGE_Z);

        drawModel(poseStack, buffer, downModel, anvilDownType, lowerY, packedLight, packedOverlay);
        drawModel(poseStack, buffer, upModel, anvilUpType, upperY, packedLight, packedOverlay);

        if (AnvilPressKinematics.orbVisible(progress, working) && orbModel != null) {
            // 早期涨起 → 随配方推进缩小；砧开始归位后 orbVisible 直接为假（不再渲染）
            float orbScale = AnvilPressKinematics.orbScale(progress);
            if (orbScale > 0.02F) {
                // 球心：相对控制器方块角的固定高度（不再由两砧面推算，见 AnvilPressKinematics 注释）
                float orbY = AnvilPressKinematics.ORB_Y_FROM_CONTROLLER + AnvilPressKinematics.ORB_Y_NUDGE;
                float r = AnvilPressKinematics.ORB_RADIUS * orbScale;
                poseStack.pushPose();
                poseStack.translate(0.0, orbY, 0.0);
                poseStack.scale(r * 2.0F, r * 2.0F, r * 2.0F);
                Matrix4f mat = poseStack.last().pose();
                VertexConsumer vc = buffer.getBuffer(ORB_RENDER_TYPE);
                float[] xyz = orbModel.xyz, uv = orbModel.uv;
                for (int i = 0; i < orbModel.cornerCount; i++) {
                    float u = uv != null ? uv[i * 2] : 0.0F;
                    float v = uv != null ? uv[i * 2 + 1] : 0.0F;
                    vc.vertex(mat, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                            .color(1.0F, 1.0F, 1.0F, 1.0F)
                            .uv(u, v)
                            .overlayCoords(OverlayTexture.NO_OVERLAY)
                            .uv2(0xF000F0)
                            .normal(0.0F, 1.0F, 0.0F)
                            .endVertex();
                }
                poseStack.popPose();
            }
        }
        poseStack.popPose();
    }

    /** 画一个砧：pose 已处在"砧体中心 + 结构朝向"的局部系，这里只做 Y 平移与缩放。 */
    private void drawModel(PoseStack poseStack, MultiBufferSource buffer, GltfParser.Model model,
                           RenderType type, float baseY, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.0, baseY, 0.0);
        poseStack.scale(AnvilPressKinematics.MODEL_SCALE, AnvilPressKinematics.MODEL_SCALE,
                AnvilPressKinematics.MODEL_SCALE);
        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(type);
        float[] xyz = model.xyz, uv = model.uv, tint = model.tint;
        int light = lightWithFloor(packedLight);
        float floor = AnvilPressKinematics.TINT_FLOOR;
        for (int i = 0; i < model.cornerCount; i++) {
            float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;
            if (tint != null) {
                r = Math.max(tint[i * 4], floor);
                g = Math.max(tint[i * 4 + 1], floor);
                b = Math.max(tint[i * 4 + 2], floor);
                a = tint[i * 4 + 3];
            }
            float u = uv != null ? uv[i * 2] : 0.0F;
            float v = uv != null ? uv[i * 2 + 1] : 0.0F;
            vc.vertex(mat, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .color(r, g, b, a)
                    .uv(u, v)
                    .overlayCoords(packedOverlay)
                    .uv2(light)
                    .normal(0.0F, 1.0F, 0.0F)
                    .endVertex();
        }
        poseStack.popPose();
    }

    /** 提亮：把方块光照抬到 ANVIL_LIGHT_FLOOR 以上（砧在结构内部，真实光照常接近黑）。 */
    private static int lightWithFloor(int packedLight) {
        int floor = AnvilPressKinematics.ANVIL_LIGHT_FLOOR;
        if (floor <= 0) return packedLight;
        int block = LightTexture.block(packedLight);
        int sky = LightTexture.sky(packedLight);
        return LightTexture.pack(Math.max(block, floor), sky);
    }

    /** 惰性加载三个模型（失败 warn-once 后永久跳过，不影响其余渲染）。 */
    private void loadModels() {
        if (modelsTried) return;
        modelsTried = true;
        var rm = Minecraft.getInstance().getResourceManager();
        upModel = loadQuiet(rm, MODEL_UP);
        downModel = loadQuiet(rm, MODEL_DOWN);
        orbModel = loadQuiet(rm, MODEL_ORB);
        upHeight = heightOf(upModel);
        downHeight = heightOf(downModel);
        // 球几何竖直居中（缩放才绕球心）；对撞砧不做 —— 它们要"底平面在 baseY"的锚点
        GltfParser.centerVertically(orbModel);
    }

    private boolean ensureOrb() {
        return orbModel != null;   // 魔源球走静态贴图（RenderType 直接引 PNG），无需 Registrar
    }

    private static GltfParser.Model loadQuiet(net.minecraft.server.packs.resources.ResourceManager rm,
                                             ResourceLocation file) {
        try {
            GltfParser.Model model = GltfParser.load(rm, file);
            return model != null && model.cornerCount > 0 ? model : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 模型高度（GltfParser 已把最低点归零 → 顶点最大 Y）。 */
    private static float heightOf(GltfParser.Model model) {
        if (model == null || model.xyz == null) return 0.0F;
        float max = 0.0F;
        for (int i = 0; i < model.cornerCount; i++) {
            max = Math.max(max, model.xyz[i * 3 + 1]);
        }
        return max;
    }
}
