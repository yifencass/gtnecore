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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.machine.AntigravityEngineMachine;

/**
 * 反曲率-重力引擎动态渲染器（GTCEu DynamicRender 体系，模型 JSON 的 dynamic_renders 挂载）：
 *
 * 1. 时空核心（Ad Astra 重力正常化器同款蓝色方块，BER 迁移）：
 *    控制器上方 3 格处渲染 ad_astra:block/gravity_normalizer_top 模型，6 倍缩放 + 三轴旋转动画。
 * 2. 反曲率环流（GTCEu 聚变堆 FusionRingRender 同款环，动画化）：
 *    运行时在控制器下方 18 格处不断诞生发光环，向上飘向控制器，同时缩小并变透明，
 *    5 个环相位错开同时存在；停机后整体淡出。
 */
@OnlyIn(Dist.CLIENT)
public class AntigravityEngineRender extends DynamicRender<AntigravityEngineMachine, AntigravityEngineRender> {

    public static final Codec<AntigravityEngineRender> CODEC = Codec.unit(AntigravityEngineRender::new);
    public static final DynamicRenderType<AntigravityEngineMachine, AntigravityEngineRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** Ad Astra 重力正常化器顶部蓝色核心方块模型（悬浮旋转） */
    private static final ResourceLocation GRAVITY_CORE_MODEL =
            new ResourceLocation("ad_astra", "block/gravity_normalizer_top");

    /** 环动画参数 */
    private static final int RING_COUNT = 5;            // 同时存在的环数
    private static final int RING_CYCLE = 80;           // 每环完整生命周期（tick）
    private static final float RING_START_Y = -18.0F;   // 诞生位：控制器下方 18 格（相对方块原点）
    private static final float RING_MAX_RADIUS = 6.0F;  // 诞生时半径
    private static final float RING_MIN_RADIUS = 2.0F;  // 消散时半径（勿缩成点，否则环组从侧面看像"点连成的线"）
    private static final float RING_TUBE = 0.2F;        // 环管粗细
    private static final int FADE_TICKS = 40;           // 停机淡出时长（tick）

    /** 停机后剩余淡出进度（渲染帧递减，1→0） */
    private float fade = 0.0F;

    /**
     * 环渲染类型：TRIANGLES 模式（非 GTCEu LIGHT_RING 的 TRIANGLE_STRIP）。
     * 原因：TRIANGLE_STRIP 的三角形是"滑动窗口"（v[i]v[i+1]v[i+2]），多个环画进同一
     * buffer 时，环 A 的尾顶点会与环 B 的头两个顶点组成跨环三角形——即"新环与旧环
     * 连着的边"；插入退化顶点也无法消除（窗口滑到 (尾, 头1, 头2) 必然非退化）。
     * TRIANGLES 每 3 个顶点独立成三角形，环与环完全隔离。配置照抄 GTRenderTypes.LIGHT_RING
     * （NO_CULL + POSITION_COLOR + translucent），视觉一致。
     */
    private static final RenderType RING_RENDER_TYPE = RenderType.create(
            "gtnecore_antigravity_ring",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                            "gtnecore_ring_translucent",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.defaultBlendFunc();
                            },
                            RenderSystem::disableBlend))
                    .createCompositeState(false));

    /** 客户端初始化时注册 DynamicRenderType（模型 JSON 的 dynamic_renders 按此 id 解析） */
    public static void init() {
        DynamicRenderManager.register(Gtnecore.id("antigravity_engine"), TYPE);
    }

    public AntigravityEngineRender() {
    }

    @Override
    public DynamicRenderType<AntigravityEngineMachine, AntigravityEngineRender> getType() {
        return TYPE;
    }

    @Override
    public boolean shouldRender(AntigravityEngineMachine machine, Vec3 cameraPos) {
        // 恒渲染：蓝色方块不受运行状态限制；环的显隐在 render() 内按 recipeLogic 状态判定
        // （勿用 @Persisted 机器字段做闸门——客户端同步时序不可靠，聚变堆环同款用
        //  RecipeLogic.isWorking()，status 是 @Persisted + onStatusSynced 专用同步通道）
        return true;
    }

    @Override
    public void render(AntigravityEngineMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // 第一条件：结构成型 —— 蓝色方块（时空核心）显示
        boolean formed = machine.isFormed();
        // 第二条件：控制器工作中（结构成型 + 发电输出实际入仓；动力仓满时输出无法
        // 满足 → AntigravityLogic 置 IDLE → isWorking() 为 false，环随之停止）
        boolean running = formed && machine.getRecipeLogic().isWorking();
        // 停机淡出（聚变堆环同款：FADE_TICKS 内 alpha 线性衰减）
        if (running) {
            this.fade = FADE_TICKS;
        } else {
            this.fade = Math.max(0, this.fade - Minecraft.getInstance().getDeltaFrameTime());
        }
        float fadeAlpha = this.fade / FADE_TICKS;

        if (formed) {
            renderGravityCore(machine, partialTick, poseStack, buffer, packedLight, packedOverlay);
        }
        if (fadeAlpha > 0) {
            renderRings(machine, partialTick, poseStack, buffer, fadeAlpha);
        }
    }

    //////////////////////////////////////
    // ****** 时空核心（蓝色方块）******//
    //////////////////////////////////////

    private void renderGravityCore(AntigravityEngineMachine machine, float partialTick, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(GRAVITY_CORE_MODEL);
        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) {
            return; // ad_astra 缺失时静默跳过
        }

        float anim = (machine.self().getOffsetTimer() + partialTick) * 6.0F; // 每秒 120°，持续旋转

        poseStack.pushPose();
        // 变换顺序（先写的后作用于点）：T(目标中心) → S(6×) → R(绕模型中心) → T(-模型中心)
        // 关键：旋转中心平移必须在 scale 之前（此处 scale 在前、-P 在后），
        // 否则 scale 会把 (0.5,0.7,0.5) 放大成 (1.0,1.4,1.0)，X/Z 偏移被放大 1 格、Y 偏高
        poseStack.translate(0.5, 0.5 + 3.0, 0.5); // 目标旋转中心：控制器中心上方 3 格（最后作用于点）
        poseStack.scale(4.0F, 4.0F, 4.0F);        // 4 倍缩放（原 2 倍 ×2）
        // 绕模型中心 (0.5, 0.7, 0.5) 三轴旋转（重力正常化器同款旋转动画）
        poseStack.mulPose(Axis.XP.rotationDegrees(anim));
        poseStack.mulPose(Axis.YP.rotationDegrees(anim * 0.7F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(anim * 0.5F));
        poseStack.translate(-0.5, -0.7, -0.5);    // 模型旋转中心对齐原点（最先作用于点）

        Minecraft.getInstance().getBlockRenderer().getModelRenderer()
                .renderModel(poseStack.last(), buffer.getBuffer(Sheets.cutoutBlockSheet()),
                        machine.self().getBlockState(), model, 1.0F, 1.0F, 1.0F,
                        packedLight, packedOverlay);
        poseStack.popPose();
    }

    //////////////////////////////////////
    // ****** 反曲率环流（聚变环动画化）******//
    //////////////////////////////////////

    private void renderRings(AntigravityEngineMachine machine, float partialTick, PoseStack poseStack,
                             MultiBufferSource buffer, float fadeAlpha) {
        VertexConsumer ringBuffer = buffer.getBuffer(RING_RENDER_TYPE);
        long tick = machine.self().getOffsetTimer();

        for (int i = 0; i < RING_COUNT; i++) {
            // 相位错开：每 16 tick 诞生一个新环，生命周期 80 tick → 恒有 5 环
            float age = (tick + i * (RING_CYCLE / RING_COUNT) + partialTick) % RING_CYCLE;
            float progress = age / RING_CYCLE; // 0=诞生（最下/最大/最实），1=消散（最上/最小/全透明）

            // 从控制器下方 18 格处升至控制器中心
            float y = 0.5F + RING_START_Y + progress * (-RING_START_Y);
            float radius = Mth.lerp(progress, RING_MAX_RADIUS, RING_MIN_RADIUS);
            // alpha 提前归零（progress≥0.8 即消失）：环不会贴到控制器附近，
            // 避免"环/点连到控制器"的线感；同时 5 环错开仍保持 4~5 个同屏
            float alpha = Mth.clamp(1.0F - progress * 1.25F, 0.0F, 1.0F) * fadeAlpha;
            if (alpha <= 0) continue;

            // 蓝紫基色 → 白色闪烁（聚变环同款呼吸）
            float flash = Math.abs(((tick + i * 10) % 50 + partialTick) - 25.0F) / 25.0F;
            float r = Mth.lerp(flash, 0.45F, 1.0F);
            float g = Mth.lerp(flash, 0.60F, 1.0F);
            float b = Mth.lerp(flash, 1.00F, 1.0F);

            renderRingTriangles(poseStack, ringBuffer,
                    0.5F, y, 0.5F,                    // 环心（机器方块中心，水平环）
                    radius, RING_TUBE, 10, 20,          // 半径/管径/侧面数/分段数
                    r, g, b, alpha);
        }
    }

    /**
     * 水平环（法线 Y）管状自绘：RenderBufferHelper.renderRing 的 TRIANGLES 版——
     * 每段 (theta,theta1)×(phi,phi1) 一个管面四边形 = 2 个独立三角形，
     * 多环画进同一 buffer 互不干扰（无 TRIANGLE_STRIP 的跨环三角形）。
     */
    private static void renderRingTriangles(PoseStack poseStack, VertexConsumer buffer,
                                            float x, float y, float z,
                                            float radius, float tubeRadius, int sides, int segments,
                                            float r, float g, float b, float alpha) {
        Matrix4f mat = poseStack.last().pose();
        float sideDelta = (float) (Math.PI * 2 / sides);
        float ringDelta = (float) (Math.PI * 2 / segments);
        float theta = 0;
        for (int i = 0; i < segments; i++) {
            float theta1 = theta + ringDelta;
            float cosT = Mth.cos(theta), sinT = Mth.sin(theta);
            float cosT1 = Mth.cos(theta1), sinT1 = Mth.sin(theta1);
            float phi = 0;
            for (int j = 0; j < sides; j++) {
                float phi1 = phi + sideDelta;
                float cosP = Mth.cos(phi), sinP = Mth.sin(phi);
                float cosP1 = Mth.cos(phi1), sinP1 = Mth.sin(phi1);
                // 管面四角（水平环：dist 在 XZ 平面，管截面在竖直方向）
                float d0 = radius + tubeRadius * cosP;
                float d1 = radius + tubeRadius * cosP1;
                float ax = x + sinT * d0, ay = y + tubeRadius * sinP, az = z + cosT * d0;
                float bx = x + sinT1 * d0, by = y + tubeRadius * sinP, bz = z + cosT1 * d0;
                float cx = x + sinT1 * d1, cy = y + tubeRadius * sinP1, cz = z + cosT1 * d1;
                float dx = x + sinT * d1, dy = y + tubeRadius * sinP1, dz = z + cosT * d1;
                // 三角形 1: A B C
                buffer.vertex(mat, ax, ay, az).color(r, g, b, alpha).endVertex();
                buffer.vertex(mat, bx, by, bz).color(r, g, b, alpha).endVertex();
                buffer.vertex(mat, cx, cy, cz).color(r, g, b, alpha).endVertex();
                // 三角形 2: A C D
                buffer.vertex(mat, ax, ay, az).color(r, g, b, alpha).endVertex();
                buffer.vertex(mat, cx, cy, cz).color(r, g, b, alpha).endVertex();
                buffer.vertex(mat, dx, dy, dz).color(r, g, b, alpha).endVertex();
                phi = phi1;
            }
            theta = theta1;
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AntigravityEngineMachine machine) {
        return true; // 环在最下方 18 格处诞生，必须允许屏幕外渲染
    }

    @Override
    public int getViewDistance() {
        return 64; // 环最远在下方 18 格，需大视距
    }

    @Override
    public AABB getRenderBoundingBox(AntigravityEngineMachine machine) {
        return new AABB(machine.getPos()).inflate(24);
    }
}
