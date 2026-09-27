package org.ringclouds.gtnecore.client.gltf;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * glTF baseColorTexture 贴图生命周期管理（仅 GL/渲染线程调用，方法幂等可每帧调）：
 *
 * - 图集常是 8192×8192 巨型 PNG，全图解码瞬时占用 ~256MB 原生内存 → 必须
 *   decode → 按 UV 使用范围裁剪成小图 → 立刻 close 全图，任何路径不得滞留全图。
 * - 裁剪后 UV 在 Model.uv 上原地 remap 成小图坐标系（0..1），只 remap 一次。
 * - 用 DynamicTexture + NEAREST（保留图集无 mipmap 语义，防像素出血），
 *   注册到 TextureManager，之后 RenderType 的 TextureStateShard 每帧自动绑定。
 * - 资源重载（F3+T）会释放纹理 id → 每帧检查 getId()==-1 重建（PNG 字节缓存于本类）。
 * - 失败 warn-once，渲染端永久跳过该模型。
 */
@OnlyIn(Dist.CLIENT)
public final class RocketTextureRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(RocketTextureRegistrar.class);

    private final ResourceLocation registeredLoc;  // 注册用纹理 RL（渲染 RenderType 引用它）
    private byte[] pngBytes;                       // 源 PNG（外链资源或 GLB 内嵌），重建用缓存
    private DynamicTexture texture;                // 裁剪小图纹理（NEAREST，注册后 TM 持有）
    private int cropW, cropH;                      // 裁剪尺寸
    private float imgW, imgH;                      // 原图尺寸（remap 依据）
    private boolean uvRemapped;                    // Model.uv 已 remap 过（模型缓存期内勿二次）
    private boolean loaded;
    private boolean failed;

    public RocketTextureRegistrar(ResourceLocation registeredLoc) {
        this.registeredLoc = registeredLoc;
    }

    /**
     * 每帧调用：确保纹理已注册可用。返回 false = 永久失败（warn 已打）或尚未就绪，调用方跳过绘制。
     * 必须运行在渲染线程（GL 线程）。
     */
    public boolean ensure(GltfParser.Model model) {
        if (failed) return false;
        if (!loaded) {
            if (!RenderSystem.isOnRenderThreadOrInit()) {
                return false; // 非渲染线程帧（理论不会走到：BER 只在主线程），等下一帧
            }
            try {
                load(model);
            } catch (Throwable e) {
                failed = true;
                LOGGER.warn("火箭贴图加载失败（渲染将跳过）: {}", e.toString());
                return false;
            }
        }
        if (texture != null && texture.getId() == -1) {
            // 资源重载/纹理释放后重建（TextureManager close 后 id 回 -1，实例仍在）
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            tm.register(registeredLoc, texture);
            texture.upload();
        }
        return loaded;
    }

    private void load(GltfParser.Model model) throws IOException {
        if (pngBytes == null) {
            if (model.atlasBytes != null) {
                pngBytes = model.atlasBytes;
            } else if (model.atlasRl != null) {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(model.atlasRl);
                if (res.isEmpty()) {
                    throw new IOException("贴图资源缺失: " + model.atlasRl);
                }
                try (InputStream in = res.get().open()) {
                    pngBytes = in.readAllBytes();
                }
            } else {
                throw new IOException("模型无贴图来源");
            }
        }

        if (model.uv == null) {
            // 无 UV 的模型无法按图集裁剪 remap——整张 8192² 直接上传会炸显存，宁可拒绝
            throw new IOException("模型无 TEXCOORD_0，贴图裁剪不可用");
        }
        NativeImage full = NativeImage.read(new ByteArrayInputStream(pngBytes));
        try {
            float[] b = model.uvBounds;
            this.imgW = full.getWidth();
            this.imgH = full.getHeight();
            // UV 使用范围 → 像素裁剪框（0..1 × 像素尺寸；越界收敛）
            int x0 = clamp((int) Math.floor(b[0] * imgW), 0, (int) imgW - 1);
            int x1 = clamp((int) Math.ceil(b[1] * imgW), x0 + 1, (int) imgW);
            int y0 = clamp((int) Math.floor(b[2] * imgH), 0, (int) imgH - 1);
            int y1 = clamp((int) Math.ceil(b[3] * imgH), y0 + 1, (int) imgH);
            this.cropW = x1 - x0;
            this.cropH = y1 - y0;

            NativeImage cropped = new NativeImage(cropW, cropH, false);
            for (int x = 0; x < cropW; x++) {
                for (int y = 0; y < cropH; y++) {
                    cropped.setPixelRGBA(x, y, full.getPixelRGBA(x0 + x, y0 + y));
                }
            }

            // UV 原地 remap：图集像素坐标 → 裁剪小图坐标（只 remap 一次；若模型对象重建则新实例重来）
            if (!uvRemapped && model.uv != null) {
                for (int i = 0; i < model.cornerCount; i++) {
                    model.uv[i * 2] = (model.uv[i * 2] * imgW - x0) / cropW;
                    model.uv[i * 2 + 1] = (model.uv[i * 2 + 1] * imgH - y0) / cropH;
                }
                uvRemapped = true;
            }

            this.texture = new DynamicTexture(cropped);
            this.texture.setFilter(false, false); // NEAREST：图集语义，无 mipmap 防出血
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            tm.register(registeredLoc, texture);
            texture.upload();
            this.loaded = true;
            LOGGER.debug("火箭贴图就绪: {}x{}（裁剪自 {}x{}）", cropW, cropH, (int) imgW, (int) imgH);
        } finally {
            full.close(); // 8192² 全图立即释放，绝不滞留
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
