package org.ringclouds.gtnecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.ringclouds.gtnecore.Gtnecore;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * GT之环渲染器 —— 套用 Goety UnholyHatModel 的 halo 结构（Polarice3，MIT License）：
 * halo 挂在头部 (0,-13,5)，x 旋转 45°，16×16 薄片贴 G 图标，缓慢 z 旋转
 * （模型动画在 GtneHaloModel.setupAnim 里，渲染直接走模型）。
 *
 * 用 entityCutout（默认开背面剔除）：
 * - 薄 cube 只有朝向相机的一面可见 → 不会出现"前后两面镜像图叠一块"
 *   （非剔除渲染类型两面都画、背面 UV 镜像的通病）
 * - 贴图 textures/models/curios/gt_halo.png 是透明背景的青色 G（黑底会显示成纯黑方块）
 */
public class GtneHaloRenderer implements ICurioRenderer {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Gtnecore.id("gt_halo"), "main");
    private static final ResourceLocation TEXTURE =
            Gtnecore.id("textures/models/curios/gt_halo.png");

    private final GtneHaloModel model;

    public GtneHaloRenderer() {
        this.model = new GtneHaloModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(LAYER));
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent, MultiBufferSource buffer,
            int light, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity entity = slotContext.entity();
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        ICurioRenderer.followBodyRotations(entity, model);

        // 恒定满亮度（FULL_BRIGHT）：光环在任何光照环境下都高亮显示
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        model.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
    }
}
