package org.ringclouds.gtnecore.client.render;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRender;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderManager;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderType;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
import org.ringclouds.gtnecore.machine.RocketLaunchPadMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 火箭发射台"勘矿火箭"动态渲染器（GTCEu DynamicRender 体系，模型 JSON 的 dynamic_renders 挂载）。
 *
 * 渲染一枚运行时 glTF 解析的火箭（VoxelBridge 区域导出，见 {@link GltfParser}）：
 * 待机停靠台顶 → 配方开工后按 {@link RocketLaunchKinematics} 两程循环升空/巡航/降落；
 * 断电（WAITING/SUSPEND）冻结画面；结构散架/火箭被拿走直接消失。
 *
 * 约定（勿破）：
 * - 闸门读 recipeLogic（isWorking/status/progress 均 @Persisted+DescSynced 专用同步通道），
 *   不用机器自定义 @Persisted 字段做客户端判定（同反曲率引擎注释）。
 * - **显隐 = 布尔硬切换，无任何透明度渐变**（渲染器实例全 world 共享，透明度值互相感染；
 *   用户已否决淡入淡出）。visible=false 即一帧都不画。
 * - 解析/贴图只在渲染线程首次 render() 惰性做（@Mod 构造期 ResourceManager 未就绪，
 *   不能放 init()）；失败 warn-once 永久跳过。
 * - 只消费配方归一化进度 p（9600t 与失重 SPACE_BONUS 2400t 同样适用），不假设 duration。
 * - 每帧把升空/降落段震动强度上报 {@link RocketLaunchCameraFX}（引擎抖动视野）。
 */
@OnlyIn(Dist.CLIENT)
public class RocketLaunchPadRender extends DynamicRender<RocketLaunchPadMachine, RocketLaunchPadRender> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketLaunchPadRender.class);

    public static final Codec<RocketLaunchPadRender> CODEC = Codec.unit(RocketLaunchPadRender::new);
    public static final DynamicRenderType<RocketLaunchPadMachine, RocketLaunchPadRender> TYPE =
            new DynamicRenderType<>(CODEC);

    /** glTF 主文件（相对 uri 的 .bin/贴图会自动按同目录解析）。
     *  注意：ResourceManager 按字面路径找文件、不做扩展名推断——路径必须带 .gltf 后缀。 */
    private static final ResourceLocation MODEL_FILE =
            Gtnecore.id("models/rocket/gltf/region_-461_64_596__-453_78_604.gltf");
    /** 裁剪后小图贴图的注册名（RenderType 按它绑定；纹理由 Registrar 动态注册） */
    private static final ResourceLocation TEXTURE_RL = Gtnecore.id("dynamic/rocket_atlas");

    /**
     * 贴图网格渲染类型：自定义 TRIANGLES 版"实体半透明"（组合状态照抄 vanilla entityTranslucent）。
     * 不能直接用 RenderType.entityTranslucent——那是 QUADS 模式 + 256 顶点小缓冲：
     * 本模型按三角形流(3 对齐)喂数据，MultiBufferSource 每 256 顶点切批后 quad(4 对齐)
     * 语义全部错位，几何与 UV 配对周期性错乱（表现为贴图"拉伸相连卷曲"）。
     * TRIANGLES + 大缓冲（>9276 顶，一帧一刷不切批）；实体着色器 + 动态贴图 + 半透明混合
     * + lightmap/overlay 状态齐全（缺失时实体着色器的采样器会取到脏单元）。
     */
    private static final RenderType ROCKET_RENDER_TYPE = RenderType.create(
            "gtnecore_rocket_entity",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            0x30000,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE_RL, false, false))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                            "gtnecore_rocket_translucent",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.defaultBlendFunc();
                            },
                            RenderSystem::disableBlend))
                    .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                    .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                    .createCompositeState(false));

    /** 尾焰锥体渲染类型：POSITION_COLOR TRIANGLES + additive 混合（自发光叠加），
     *  无深度写入（焰不挡后续），深度测试保留（被机壳遮挡时正常隐藏） */
    private static final RenderType FLAME_RENDER_TYPE = RenderType.create(
            "gtnecore_rocket_flame",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            0x20000,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                            "gtnecore_plume_additive",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                                        GlStateManager.DestFactor.ONE);
                            },
                            () -> {
                                RenderSystem.disableBlend();
                                RenderSystem.defaultBlendFunc();
                            }))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .createCompositeState(false));

    /** 客户端初始化时注册 DynamicRenderType（模型 JSON 的 dynamic_renders 按此 id 解析） */
    public static void init() {
        DynamicRenderManager.register(Gtnecore.id("rocket_launch_pad"), TYPE);
    }

    // ---------- 惰性装载缓存 ----------
    private GltfParser.Model model;
    private boolean modelFailed;
    private final RocketTextureRegistrar textureRegistrar = new RocketTextureRegistrar(TEXTURE_RL);

    // ---------- 画面状态（按机器位置隔离） ----------
    // DynamicRender 实例对同款机器是"全 world 共享一个"（模型烘焙一次）——
    // 采样存实例字段会被多台互相覆盖（火箭"瞬移/滞留"）。必须按机器 pos 各存一份。
    /** 单台发射台状态：上一帧采样（断电冻结/打断保护回填用） */
    private static final class PadState {
        RocketLaunchKinematics.FlightSample lastSample;
    }

    private final Map<BlockPos, PadState> padStates = new HashMap<>();

    private PadState stateOf(RocketLaunchPadMachine machine) {
        return padStates.computeIfAbsent(machine.getPos(), pos -> new PadState());
    }

    public RocketLaunchPadRender() {
    }

    @Override
    public DynamicRenderType<RocketLaunchPadMachine, RocketLaunchPadRender> getType() {
        return TYPE;
    }

    @Override
    public boolean shouldRender(RocketLaunchPadMachine machine, Vec3 cameraPos) {
        // 恒渲染：火箭显隐在 render() 内按结构成型 + 配方状态推进（同反曲率引擎）
        return true;
    }

    @Override
    public void render(RocketLaunchPadMachine machine, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (model == null && !modelFailed) {
            try {
                this.model = GltfParser.load(Minecraft.getInstance().getResourceManager(), MODEL_FILE);
                LOGGER.info("火箭模型加载完成: {} 三角形", model.cornerCount / 3);
            } catch (Exception e) {
                this.modelFailed = true;
                LOGGER.warn("火箭模型加载失败（渲染跳过）: {}", e.toString());
            }
        }
        if (model == null || !textureRegistrar.ensure(model)) {
            return;
        }

        // ---------- 状态采样（每台机器独立状态，防多台串台） ----------
        // 可见闸门：结构成型 且 机内火箭槽（槽 0）非空 才允许出现火箭。
        // 未成型/机内无火箭 → 隐藏（拿走火箭即取消展示，不会停在台上）。
        PadState st = stateOf(machine);
        RecipeLogic logic = machine.getRecipeLogic();
        boolean visibleAllowed = machine.isFormed() && machine.isRocketPresent();
        float p = (float) logic.getProgressPercent();
        boolean working = logic.isWorking();
        RecipeLogic.Status status = logic.getStatus();
        // 断电等待/挂起（进度停滞且火箭仍在槽内）→ 画面冻结：原样保留上一帧，悬停原地
        boolean waitingFreeze = visibleAllowed && p > 0 && !working
                && (status == RecipeLogic.Status.WAITING || status == RecipeLogic.Status.SUSPEND);

        RocketLaunchKinematics.FlightSample sample;
        if (waitingFreeze && st.lastSample != null) {
            sample = st.lastSample;
        } else {
            sample = RocketLaunchKinematics.sample(p, visibleAllowed, working);
            // 打断保护：配方中途失效/被取消/进度归零时，不要"瞬移回台面"。
            // 只有本就停靠（PARKED）或刚完成降落（DESCENT 已触底）才停在台上；
            // 其余情况保持隐藏，等下一配方开工时从台面重新点火（fade-in 掩盖出现）。
            if (visibleAllowed && !working && sample.phase() == RocketLaunchKinematics.RocketPhase.PARKED
                    && st.lastSample != null && st.lastSample.phase() != RocketLaunchKinematics.RocketPhase.PARKED
                    && !(st.lastSample.phase() == RocketLaunchKinematics.RocketPhase.DESCENT
                            && st.lastSample.baseY() <= RocketLaunchKinematics.BASE_Y + 0.1F)) {
                sample = RocketLaunchKinematics.FlightSample.HIDDEN;
            }
        }
        if (st.lastSample != null && st.lastSample.phase() != sample.phase()) {
            LOGGER.debug("发射台 {} 相位 {}→{} (p={} working={} allowed={} status={})",
                    machine.getPos(), st.lastSample.phase(), sample.phase(), p, working, visibleAllowed, status);
        }
        st.lastSample = sample;

        // ---------- 引擎震动上报 + 硬显隐 ----------
        // 震动源：升空段恒震（引擎轰鸣）、降落段越近地面越震；其余相位 = 0（移除源）
        float shake = 0.0F;
        if (sample.visible()) {
            if (sample.phase() == RocketLaunchKinematics.RocketPhase.LAUNCH) {
                shake = 1.0F;
            } else if (sample.phase() == RocketLaunchKinematics.RocketPhase.DESCENT) {
                shake = 1.0F - (sample.baseY() - RocketLaunchKinematics.BASE_Y)
                        / (RocketLaunchKinematics.ENTRY_HEIGHT - RocketLaunchKinematics.BASE_Y);
            }
        }
        RocketLaunchCameraFX.setSource(machine.getPos(), shake);

        if (!sample.visible()) {
            if (!visibleAllowed) {
                padStates.remove(machine.getPos()); // 无火箭/未成型：清状态，下次成型重新开始
            }
            return; // 硬隐藏：不可见帧一帧都不画（巡航段/到顶后在此直接消失）
        }

        // ---------- 绘制 ----------
        poseStack.pushPose();
        // 机器方块角为原点：水平中心 0.5、底平面 BASE_Y（模型已自动对齐：水平居中、最低点 y=0）
        poseStack.translate(0.5, sample.baseY(), 0.5);
        poseStack.scale(RocketLaunchKinematics.MODEL_SCALE, RocketLaunchKinematics.MODEL_SCALE,
                RocketLaunchKinematics.MODEL_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(RocketLaunchKinematics.YAW_CORRECTION_DEG));

        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(ROCKET_RENDER_TYPE);
        float[] xyz = model.xyz, uv = model.uv, tint = model.tint;
        boolean flipV = RocketLaunchKinematics.TEXTURE_V_FLIP;
        for (int i = 0; i < model.cornerCount; i++) {
            float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;
            if (tint != null) {
                r = tint[i * 4];
                g = tint[i * 4 + 1];
                b = tint[i * 4 + 2];
                a = tint[i * 4 + 3]; // 顶点乘色 alpha（本模型全 1 = 不透明，无透明度渐变）
            }
            float u = uv != null ? uv[i * 2] : 0.0F;
            float v = uv != null ? uv[i * 2 + 1] : 0.0F;
            if (flipV) v = 1.0F - v;
            vc.vertex(mat, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .color(r, g, b, a)
                    .uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT) // 高空无光源：全亮（星空舞台效果）
                    .normal(0.0F, 1.0F, 0.0F)
                    .endVertex();
        }
        poseStack.popPose();

        // ---------- 尾焰锥体（发射/降落窗口；火箭底局部系：translate(0.5, baseY, 0.5)） ----------
        RocketLaunchKinematics.RocketPhase phase = sample.phase();
        boolean flameOn = !waitingFreeze
                && (phase == RocketLaunchKinematics.RocketPhase.LAUNCH
                || phase == RocketLaunchKinematics.RocketPhase.DESCENT);
        if (flameOn) {
            float flameLen;
            if (phase == RocketLaunchKinematics.RocketPhase.LAUNCH) {
                // 焰长随爬升油门 3→6 格（前 55% 高度内线性到满焰）
                float throttle = Mth.clamp((sample.baseY() - RocketLaunchKinematics.BASE_Y)
                        / (RocketLaunchKinematics.APEX_HEIGHT * 0.55F), 0.0F, 1.0F);
                flameLen = 3.0F + 3.0F * throttle;
            } else {
                flameLen = 2.5F; // 降落反推短焰
            }
            // 净空钳制：焰长不得超过"火箭底到台面"的间隙（留 0.15 格），
            // 否则离地初期焰尖会刺穿发射台底盘；火箭升起后净空变大自然放长
            float clearance = sample.baseY() - RocketLaunchKinematics.BASE_Y - 0.15F;
            flameLen = Math.min(flameLen, clearance);
            if (flameLen > 0.05F) {
                poseStack.pushPose();
                poseStack.translate(0.5, sample.baseY(), 0.5); // 与火箭同局部系：火箭底平面 y=0
                renderFlame(poseStack, buffer, machine.self().getOffsetTimer(), flameLen);
                poseStack.popPose();
            }
        }
    }

    // ------------------------------------------
    // ****** 尾焰锥体（几何自发光，无贴图） ******//
    // ------------------------------------------
    /** 锥体侧面数 / 纵向分段数（12×6 = 72 面/层，两层共 144 面/帧，CPU 可忽略） */
    private static final int FLAME_SIDES = 12;
    private static final int FLAME_SEGMENTS = 6;
    /**
     * 锥顶藏进船体内部的高度（格，局部 y>0 方向）。尾焰本质是柱/锥，若顶环生成在
     * 船底平面下方，侧斜视角会看到"开口环与船底分离/遮不住底盘"的怪样；
     * 把原始大圆面放进船体内部后，船体深度（火箭 translucent 带深度写）会把舱内段
     * 裁掉，火焰看起来是从船底"长出来"的，边界无缝且被船底完美遮挡。
     */
    private static final float FLAME_HIDDEN_INSET = 0.5F;

    /**
     * 两层 additive 发光锥：外焰（橙红、全长、α 递减到尖 0）包内芯（白黄、60% 长、更亮）
     * 形成热核。锥轴 = 局部 -Y（喷口在火箭底 y≈0 下沿，向下喷）。
     * 长度/半径带低频抖动（offsetTimer 驱动，多台相位天然错开）。
     */
    private static void renderFlame(PoseStack poseStack, MultiBufferSource buffer, long tick, float length) {
        float flicker = 1.0F + 0.09F * Mth.sin(tick * 0.33F) + 0.06F * Mth.sin(tick * 1.9F + 1.0F);
        // 喷口半径按模型底部引擎区实测：火箭底足 9 格宽，引擎簇集中在中心 ~±2，
        // 单束小锥(0.3)盖不住喷口面 → 外焰从 1.9 起（覆盖引擎区）、略向外展开到 2.4
        renderFlameLayer(poseStack, buffer, length, 1.9F, 2.4F * flicker,
                1.0F, 0.72F, 0.30F, 0.85F, 0.15F);                 // 外焰：橙
        renderFlameLayer(poseStack, buffer, length * 0.6F, 0.9F, 1.3F * flicker,
                1.0F, 0.96F, 0.78F, 0.95F, 0.25F);                 // 内芯：白黄（短而亮）
    }

    /** 画一层锥：r0=喷口半径、r1=焰尾半径，颜色按纵向从亮(顶)渐变到暗/透明(尖)。 */
    private static void renderFlameLayer(PoseStack poseStack, MultiBufferSource buffer, float len,
                                         float r0, float r1,
                                         float cr, float cg, float cb, float alphaTop, float alphaTip) {
        VertexConsumer vc = buffer.getBuffer(FLAME_RENDER_TYPE);
        Matrix4f mat = poseStack.last().pose();
        // 锥顶藏在船体内（+FLAME_HIDDEN_INSET，被船底深度裁掉），可见焰长 ≈ len：
        // 总高 = INSET + len，y 从 +INSET 到 -len
        float top = FLAME_HIDDEN_INSET;
        float bottom = -len;         // 焰尖
        for (int s = 0; s < FLAME_SEGMENTS; s++) {
            float y0 = top + (bottom - top) * (s / (float) FLAME_SEGMENTS);
            float y1 = top + (bottom - top) * ((s + 1) / (float) FLAME_SEGMENTS);
            float t0 = (top - y0) / (top - bottom); // 0=尖 1=喷口
            float t1 = (top - y1) / (top - bottom);
            float rad0 = Mth.lerp(t0, r1, r0);
            float rad1 = Mth.lerp(t1, r1, r0);
            float a0 = Mth.lerp(t0, alphaTip, alphaTop);
            float a1 = Mth.lerp(t1, alphaTip, alphaTop);
            for (int i = 0; i < FLAME_SIDES; i++) {
                float ang0 = i * Mth.TWO_PI / FLAME_SIDES;
                float ang1 = (i + 1) * Mth.TWO_PI / FLAME_SIDES;
                float c0 = Mth.cos(ang0), s0 = Mth.sin(ang0);
                float c1 = Mth.cos(ang1), s1 = Mth.sin(ang1);
                // 四边形 (近环 i, 近环 i+1, 远环 i+1, 远环 i) → 两个三角形
                flameVertex(vc, mat, rad0 * c0, y0, rad0 * s0, cr, cg, cb, a0);
                flameVertex(vc, mat, rad0 * c1, y0, rad0 * s1, cr, cg, cb, a0);
                flameVertex(vc, mat, rad1 * c1, y1, rad1 * s1, cr, cg, cb, a1);
                flameVertex(vc, mat, rad0 * c0, y0, rad0 * s0, cr, cg, cb, a0);
                flameVertex(vc, mat, rad1 * c1, y1, rad1 * s1, cr, cg, cb, a1);
                flameVertex(vc, mat, rad1 * c0, y1, rad1 * s0, cr, cg, cb, a1);
            }
        }
        // 底部封口扇形（圆心→环边）：不封底时焰尾是空心环，从斜下方看很怪。
        // 圆心提亮形成"焰心聚光"，additive 叠加后截面呈填满的亮核。
        float capAlpha = Math.min(1.0F, alphaTip + 0.35F);
        float capR = 1.0F;
        float capG = Math.min(1.0F, cg + 0.22F);
        float capB = Math.min(1.0F, cb + 0.30F);
        for (int i = 0; i < FLAME_SIDES; i++) {
            float ang0 = i * Mth.TWO_PI / FLAME_SIDES;
            float ang1 = (i + 1) * Mth.TWO_PI / FLAME_SIDES;
            flameVertex(vc, mat, 0.0F, bottom, 0.0F, capR, capG, capB, capAlpha);
            flameVertex(vc, mat, r1 * Mth.cos(ang1), bottom, r1 * Mth.sin(ang1), cr, cg, cb, alphaTip);
            flameVertex(vc, mat, r1 * Mth.cos(ang0), bottom, r1 * Mth.sin(ang0), cr, cg, cb, alphaTip);
        }
    }

    private static void flameVertex(VertexConsumer vc, Matrix4f mat,
                                    float x, float y, float z,
                                    float r, float g, float b, float a) {
        vc.vertex(mat, x, y, z).color(r, g, b, a).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(RocketLaunchPadMachine machine) {
        return true; // 顶点在台顶上方 100+ 格，必须允许屏幕外渲染
    }

    @Override
    public int getViewDistance() {
        return RocketLaunchKinematics.VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(RocketLaunchPadMachine machine) {
        // 覆盖：底座（机器角 y..y+1）→ 顶点（BASE_Y+APEX≈y+81）+ 水平 9 格宽火箭
        var p = machine.getPos();
        return new AABB(p.getX() - RocketLaunchKinematics.RENDER_HALF_W,
                p.getY() - 2.0,
                p.getZ() - RocketLaunchKinematics.RENDER_HALF_W,
                p.getX() + 1.0 + RocketLaunchKinematics.RENDER_HALF_W,
                p.getY() + RocketLaunchKinematics.RENDER_MAX_Y,
                p.getZ() + 1.0 + RocketLaunchKinematics.RENDER_HALF_W);
    }
}
