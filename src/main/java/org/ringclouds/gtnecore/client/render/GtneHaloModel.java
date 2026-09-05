package org.ringclouds.gtnecore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

/**
 * GT之环 模型 —— 结构照搬 Goety 的 UnholyHatModel（Polarice3，MIT License）：
 * HumanoidModel 子类，head 空壳，halo 子部件（头后上方 x 旋转 45°），
 * halo_1 是 16×16 薄片（cube，贴 G 图标，缓慢旋转）。
 * 用 cube 渲染（前后面 UV 镜像折叠属 cube 固有行为），能正确反映光源。
 */
public class GtneHaloModel extends HumanoidModel<LivingEntity> {

    public final ModelPart halo;

    public GtneHaloModel(ModelPart root) {
        super(root);
        this.halo = root.getChild("head").getChild("halo");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition halo = head.addOrReplaceChild("halo",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0, -13, 5, 0.7854F, 0, 0));
        // 16×16 薄片（微小厚度避免退化面），贴 G 图标（16×16 纹理）
        halo.addOrReplaceChild("halo_1",
                CubeListBuilder.create().texOffs(0, 0).addBox(-8, -8, -0.001F, 16, 16, 0.002F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.halo.getChild("halo_1").zRot = ageInTicks * 0.01F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light,
                               int overlay, float r, float g, float b, float a) {
        this.head.render(poseStack, consumer, light, overlay, r, g, b, a);
    }
}
