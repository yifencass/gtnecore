package org.ringclouds.gtnecore.client.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 最小 glTF 2.0 解析器（零依赖，客户端专用）—— 为火箭发射台 glTF 火箭渲染设计：
 *
 * 支持的子集（够 VoxelBridge 导出 / Blockbench 导出用）：
 * - 容器：外部 .gltf（相对 uri 引用 .bin/图片）与内嵌单文件 .glb（JSON chunk + BIN chunk）
 * - 数据：buffers/bufferViews（支持 byteStride）/accessors（顶点只收 FLOAT 5126，
 *   索引收 USHORT 5123/UINT 5125；type 只收 SCALAR/VEC2/VEC3/VEC4）
 * - mesh：primitives 只收 mode=4（TRIANGLES，缺省也是 4）；attributes 只要
 *   POSITION（必需）+ TEXCOORD_0（缺省 (0,0)）+ COLOR_0（缺省 1,1,1,1）；
 *   TEXCOORD_1/法线等其余语义一律忽略（entity shader 不用法线）
 * - 贴图：materials→pbrMetallicRoughness.baseColorTexture→textures→images；
 *   image 支持外部 PNG uri 或 GLB bufferView 内嵌（image/png）
 * - 坐标：glTF 与 MC 同为 Y-up；UV v=0 同为图像顶 → 数据直用。导出单位约定 = 1 方块。
 *
 * 不做：nodes/scenes/animations 层级（约定导出时烘焙为 identity，带变换则 warn 一次）、
 * sparse accessor、data:/ 外链、索引外的 componentType、三角扇/带。
 *
 * 输出：Model —— 全部顶点按三角形展开成"角点流"（每三角形 3 角连续存放），
 * 且已按并集包围盒自动对齐（水平居中到 x/z=0、最低点贴 y=0），绘制端无需手调偏移。
 */
@OnlyIn(Dist.CLIENT)
public final class GltfParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(GltfParser.class);

    /** GLB 魔数 "glTF"（小端读作 0x46546C67） */
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_CHUNK_JSON = 0x4E4F534A; // "JSON"
    private static final int GLB_CHUNK_BIN = 0x004E4942;  // "BIN\0"
    private static final int COMP_FLOAT = 5126, COMP_USHORT = 5123, COMP_UINT = 5125;

    private GltfParser() {
    }

    /** 解析产物：纯数据容器，无渲染状态；uv 之后可被贴图注册器按裁剪结果原地 remap。 */
    public static final class Model {
        /** 角点流顶点坐标，3 float/角（已自动对齐：水平居中、最低点 y=0） */
        public float[] xyz;
        /** TEXCOORD_0，2 float/角（0..1 图集归一化，未 remap） */
        public float[] uv;
        /** COLOR_0 顶点乘色，4 float/角（rgba 0..1；缺省 1,1,1,1） */
        public float[] tint;
        /** 角点数（三角形数 ×3） */
        public int cornerCount;
        /** 首个 baseColorTexture 贴图资源（.gltf 外部 uri 解析出的 RL；GLB 内嵌图为 null） */
        public ResourceLocation atlasRl;
        /** GLB 内嵌 PNG 字节（atlasRl 为 null 时用）；.gltf 外链为 null */
        public byte[] atlasBytes;
        /** 图集 UV 使用范围 [minU, maxU, minV, maxV]，供裁剪 remap */
        public float[] uvBounds;
    }

    private static boolean warnedNodes = false;

    /** 从资源管理器加载模型（主文件 .gltf 或 .glb 均可），失败抛 IOException。 */
    public static Model load(ResourceManager rm, ResourceLocation fileRl) throws IOException {
        byte[] file = readAll(rm, fileRl);
        if (file == null) {
            throw new IOException("glTF 资源缺失: " + fileRl);
        }
        ByteBuffer head = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        boolean glb = head.capacity() >= 4 && head.getInt(0) == GLB_MAGIC;

        String jsonText = null;
        byte[] binChunk = null;
        if (glb) {
            // 12B header：magic/version/length；其后按 chunk：length(LE) + type(LE) + data
            if (head.capacity() < 12 || head.getInt(4) != 2) {
                throw new IOException("不支持的 GLB 版本: " + fileRl);
            }
            int pos = 12;
            while (pos + 8 <= head.capacity()) {
                int len = head.getInt(pos);
                int type = head.getInt(pos + 4);
                pos += 8;
                if (pos + len > head.capacity()) {
                    throw new IOException("GLB chunk 越界: " + fileRl);
                }
                byte[] data = new byte[len];
                head.position(pos);
                head.get(data);
                pos += len;
                if (type == GLB_CHUNK_JSON) {
                    jsonText = new String(data, StandardCharsets.UTF_8).trim();
                } else if (type == GLB_CHUNK_BIN) {
                    binChunk = data;
                }
            }
            if (jsonText == null) {
                throw new IOException("GLB 无 JSON chunk: " + fileRl);
            }
        } else {
            jsonText = new String(file, StandardCharsets.UTF_8);
        }

        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        if (!"2.0".equals(obj(root, "asset").get("version").getAsString())) {
            throw new IOException("非 glTF 2.0: " + fileRl);
        }
        String dir = fileRl.getPath().contains("/") ? fileRl.getPath().substring(0, fileRl.getPath().lastIndexOf('/') + 1) : "";

        // nodes 带非默认变换 → warn 一次（本解析器约定烘焙 identity）
        JsonArray nodes = arr(root, "nodes");
        if (!warnedNodes && nodes != null && !nodes.isEmpty()) {
            for (JsonElement ne : nodes) {
                JsonObject n = ne.getAsJsonObject();
                if (n.has("translation") || n.has("rotation") || n.has("scale") || n.has("matrix")) {
                    LOGGER.warn("glTF [{}] 的 nodes 带非默认变换，解析器忽略层级——请烘焙后重导出（Blender: Apply All Transforms）", fileRl);
                    warnedNodes = true;
                    break;
                }
            }
        }

        // ---- buffers ----
        JsonArray buffers = arr(root, "buffers");
        if (buffers == null || buffers.isEmpty()) {
            throw new IOException("glTF 无 buffers: " + fileRl);
        }
        byte[][] bufferData = new byte[buffers.size()][];
        for (int bi = 0; bi < buffers.size(); bi++) {
            JsonObject b = buffers.get(bi).getAsJsonObject();
            if (b.has("uri")) {
                String uri = b.get("uri").getAsString();
                if (uri.startsWith("data:")) {
                    throw new IOException("glTF data: 外链不支持: " + fileRl);
                }
                bufferData[bi] = readAll(rm, resolve(fileRl.getNamespace(), dir, uri));
                if (bufferData[bi] == null) {
                    throw new IOException("glTF buffer 资源缺失: " + uri);
                }
            } else if (binChunk != null) {
                bufferData[bi] = binChunk; // GLB 内嵌 BIN（buffer[0]）
            } else {
                throw new IOException("glTF buffer 既无 uri 也无内嵌数据: " + fileRl);
            }
        }

        JsonArray views = arr(root, "bufferViews");
        JsonArray accessors = arr(root, "accessors");
        List<float[]> posList = new ArrayList<>();
        List<float[]> uvList = new ArrayList<>();
        List<float[]> colList = new ArrayList<>();
        int cornerTotal = 0;
        boolean haveUv = false, haveCol = false;

        JsonArray meshes = arr(root, "meshes");
        if (meshes != null) {
            for (JsonElement me : meshes) {
                JsonArray prims = arr(me.getAsJsonObject(), "primitives");
                if (prims == null) continue;
                for (JsonElement pe : prims) {
                    JsonObject prim = pe.getAsJsonObject();
                    if (prim.has("mode") && prim.get("mode").getAsInt() != 4) {
                        throw new IOException("glTF 只支持 TRIANGLES(mode=4) mesh: " + fileRl);
                    }
                    JsonObject attrs = obj(prim, "attributes");
                    int posAcc = attrs.has("POSITION") ? attrs.get("POSITION").getAsInt() : -1;
                    if (posAcc < 0) {
                        throw new IOException("glTF primitive 缺 POSITION: " + fileRl);
                    }
                    int uvAcc = attrs.has("TEXCOORD_0") ? attrs.get("TEXCOORD_0").getAsInt() : -1;
                    int colAcc = attrs.has("COLOR_0") ? attrs.get("COLOR_0").getAsInt() : -1;
                    int idxAcc = prim.has("indices") ? prim.get("indices").getAsInt() : -1;

                    float[] pos = readFloats(accessors, views, bufferData, posAcc, 3, fileRl);
                    float[] uv = uvAcc >= 0 ? readFloats(accessors, views, bufferData, uvAcc, 2, fileRl) : null;
                    float[] col = colAcc >= 0 ? readFloats(accessors, views, bufferData, colAcc, 4, fileRl) : null;
                    haveUv |= uv != null;
                    haveCol |= col != null;

                    int[] idx = idxAcc >= 0 ? readIndices(accessors, views, bufferData, idxAcc, fileRl) : null;
                    int triCount = (idx != null ? idx.length : pos.length / 3) / 3;

                    // 就地按索引展开为角点流（pos/uv/col 各自展开，绘制端顺序直读）
                    float[] triPos = new float[triCount * 9];
                    float[] triUv = uv != null ? new float[triCount * 6] : null;
                    float[] triCol = col != null ? new float[triCount * 12] : null;
                    int vertexCount = pos.length / 3;
                    for (int t = 0; t < triCount; t++) {
                        int[] c = idx != null ? new int[] { idx[t * 3], idx[t * 3 + 1], idx[t * 3 + 2] }
                                : new int[] { t * 3, t * 3 + 1, t * 3 + 2 };
                        for (int k = 0; k < 3; k++) {
                            int v = c[k];
                            System.arraycopy(pos, v * 3, triPos, t * 9 + k * 3, 3);
                            if (triUv != null) {
                                System.arraycopy(uv, v * 2, triUv, t * 6 + k * 2, 2);
                            }
                            if (triCol != null) {
                                System.arraycopy(col, v * 4, triCol, t * 12 + k * 4, 4);
                            }
                        }
                    }
                    posList.add(triPos);
                    if (triUv != null) uvList.add(triUv);
                    if (triCol != null) colList.add(triCol);
                    cornerTotal += triCount * 3;
                }
            }
        }
        if (cornerTotal == 0) {
            throw new IOException("glTF 无可用三角形: " + fileRl);
        }

        // ---- 合并 ----
        Model model = new Model();
        model.cornerCount = cornerTotal;
        model.xyz = new float[cornerTotal * 3];
        model.uv = haveUv ? new float[cornerTotal * 2] : null;
        model.tint = haveCol ? new float[cornerTotal * 4] : null;
        int base = 0;
        for (int i = 0; i < posList.size(); i++) {
            float[] p = posList.get(i);
            System.arraycopy(p, 0, model.xyz, base * 3, p.length);
            if (model.uv != null) {
                System.arraycopy(uvList.get(i), 0, model.uv, base * 2, uvList.get(i).length);
            }
            if (model.tint != null) {
                System.arraycopy(colList.get(i), 0, model.tint, base * 4, colList.get(i).length);
            }
            base += p.length / 3;
        }

        // ---- 自动对齐：水平居中 x/z、最低点贴 y=0（角点流上一次性算/写）----
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < cornerTotal; i++) {
            minX = Math.min(minX, model.xyz[i * 3]);
            maxX = Math.max(maxX, model.xyz[i * 3]);
            minY = Math.min(minY, model.xyz[i * 3 + 1]);
            maxY = Math.max(maxY, model.xyz[i * 3 + 1]);
            minZ = Math.min(minZ, model.xyz[i * 3 + 2]);
            maxZ = Math.max(maxZ, model.xyz[i * 3 + 2]);
        }
        float shiftX = -(minX + maxX) / 2F;
        float shiftZ = -(minZ + maxZ) / 2F;
        float shiftY = -minY;
        for (int i = 0; i < cornerTotal; i++) {
            model.xyz[i * 3] += shiftX;
            model.xyz[i * 3 + 1] += shiftY;
            model.xyz[i * 3 + 2] += shiftZ;
        }

        // ---- baseColorTexture 贴图（只解第一个被引用的 PNG）----
        collectBaseColor(root, model, fileRl, bufferData, views);
        if (model.atlasRl == null && model.atlasBytes == null) {
            throw new IOException("glTF 无 baseColorTexture: " + fileRl);
        }
        // 图集 UV 使用范围（供裁剪 remap；无 UV 时退化为整图 [0,0,1,1] 假范围）
        if (model.uv != null) {
            float minU = 1, maxU = 0, minV = 1, maxV = 0;
            for (int i = 0; i < cornerTotal; i++) {
                minU = Math.min(minU, model.uv[i * 2]);
                maxU = Math.max(maxU, model.uv[i * 2]);
                minV = Math.min(minV, model.uv[i * 2 + 1]);
                maxV = Math.max(maxV, model.uv[i * 2 + 1]);
            }
            model.uvBounds = new float[] { minU, maxU, minV, maxV };
        } else {
            model.uvBounds = new float[] { 0F, 1F, 0F, 1F };
        }
        LOGGER.debug("glTF [{}] 加载完成: {} 三角形, 对齐平移 ({},{},{}), uv范围 {}", fileRl,
                cornerTotal / 3, shiftX, shiftY, shiftZ, model.uvBounds);
        return model;
    }

    /** 收集 material→baseColorTexture→images 引用的首个贴图资源/字节（忽略法线/高光/colormap）。 */
    private static void collectBaseColor(JsonObject root, Model model, ResourceLocation fileRl,
                                         byte[][] bufferData, JsonArray views) throws IOException {
        JsonArray materials = arr(root, "materials");
        JsonArray textures = arr(root, "textures");
        JsonArray images = arr(root, "images");
        if (materials == null || textures == null || images == null) return;
        String dir = fileRl.getPath().contains("/") ? fileRl.getPath().substring(0, fileRl.getPath().lastIndexOf('/') + 1) : "";
        for (JsonElement me : materials) {
            JsonObject mat = me.getAsJsonObject();
            JsonObject pbr = mat.has("pbrMetallicRoughness") ? mat.getAsJsonObject("pbrMetallicRoughness") : null;
            if (pbr == null || !pbr.has("baseColorTexture")) continue;
            int texIdx = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
            if (texIdx >= textures.size()) continue;
            int srcIdx = textures.get(texIdx).getAsJsonObject().has("source")
                    ? textures.get(texIdx).getAsJsonObject().get("source").getAsInt() : -1;
            if (srcIdx < 0 || srcIdx >= images.size()) continue;
            JsonObject img = images.get(srcIdx).getAsJsonObject();
            if (img.has("uri") && model.atlasRl == null) {
                model.atlasRl = resolve(fileRl.getNamespace(), dir, img.get("uri").getAsString());
            } else if (img.has("bufferView") && model.atlasBytes == null) {
                // GLB 内嵌图：按 bufferView 切字节（v1 只认 PNG，不做校验）
                JsonObject view = views.get(img.get("bufferView").getAsInt()).getAsJsonObject();
                int buf = view.get("buffer").getAsInt();
                int off = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
                int len = view.get("byteLength").getAsInt();
                byte[] data = bufferData[buf];
                if (off + len > data.length) {
                    throw new IOException("glTF 内嵌图片越界: " + fileRl);
                }
                byte[] out = new byte[len];
                System.arraycopy(data, off, out, 0, len);
                model.atlasBytes = out;
            }
        }
    }

    /** 相对 uri 解析：贴主文件所在目录（图片在子目录 textures/atlas/... 也照拼）。 */
    private static ResourceLocation resolve(String namespace, String dir, String uri) {
        return new ResourceLocation(namespace, dir + uri);
    }

    private static float[] readFloats(JsonArray accessors, JsonArray views, byte[][] buffers,
                                      int accIdx, int components, ResourceLocation fileRl) throws IOException {
        JsonObject acc = accessors.get(accIdx).getAsJsonObject();
        if (acc.get("componentType").getAsInt() != COMP_FLOAT) {
            throw new IOException("glTF 顶点数据非 FLOAT: " + fileRl);
        }
        int count = acc.get("count").getAsInt();
        ByteBuffer slice = sliceAccessor(acc, views, buffers, fileRl);
        float[] out = new float[count * components];
        for (int i = 0; i < count; i++) {
            for (int c = 0; c < components; c++) {
                out[i * components + c] = slice.getFloat();
            }
        }
        return out;
    }

    private static int[] readIndices(JsonArray accessors, JsonArray views, byte[][] buffers,
                                     int accIdx, ResourceLocation fileRl) throws IOException {
        JsonObject acc = accessors.get(accIdx).getAsJsonObject();
        int comp = acc.get("componentType").getAsInt();
        if (comp != COMP_USHORT && comp != COMP_UINT) {
            throw new IOException("glTF 索引非 USHORT/UINT: " + fileRl);
        }
        int count = acc.get("count").getAsInt();
        ByteBuffer slice = sliceAccessor(acc, views, buffers, fileRl);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = comp == COMP_UINT ? slice.getInt() : slice.getShort() & 0xFFFF;
        }
        return out;
    }

    /** 按 bufferView(byteOffset/byteStride) + accessor(byteOffset) 切出小端可读切片。 */
    private static ByteBuffer sliceAccessor(JsonObject acc, JsonArray views, byte[][] buffers,
                                            ResourceLocation fileRl) throws IOException {
        int viewIdx = acc.has("bufferView") ? acc.get("bufferView").getAsInt() : -1;
        if (viewIdx < 0 || viewIdx >= views.size()) {
            throw new IOException("glTF accessor 无 bufferView: " + fileRl);
        }
        JsonObject view = views.get(viewIdx).getAsJsonObject();
        byte[] data = buffers[view.get("buffer").getAsInt()];
        int viewOff = view.has("byteOffset") ? view.get("byteOffset").getAsInt() : 0;
        int accOff = acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0;
        int start = viewOff + accOff;
        int count = acc.get("count").getAsInt();
        int compBytes = compBytes(acc.get("componentType").getAsInt());
        int comps = typeComponents(acc.get("type").getAsString());
        int stride = view.has("byteStride") ? view.get("byteStride").getAsInt() : compBytes * comps;
        if (start + (count - 1) * stride + compBytes * comps > data.length) {
            throw new IOException("glTF accessor 越界: " + fileRl);
        }
        // 忽略 byteStride（VoxelBridge 无 stride 实测）；若未来出现交错布局在此扩展
        ByteBuffer buf = ByteBuffer.wrap(data, start, count * compBytes * comps).order(ByteOrder.LITTLE_ENDIAN);
        // 对齐修正：slice 起点须按元素类型对齐到 4 字节（float/uint）或 2（ushort）——wrap 无对齐限制，跳过
        return buf;
    }

    private static int compBytes(int componentType) {
        return switch (componentType) {
            case COMP_FLOAT, COMP_UINT -> 4;
            case COMP_USHORT -> 2;
            default -> throw new IllegalStateException("不支持 componentType " + componentType);
        };
    }

    private static int typeComponents(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            default -> throw new IllegalStateException("不支持 type " + type);
        };
    }

    private static byte[] readAll(ResourceManager rm, ResourceLocation rl) {
        Optional<Resource> res = rm.getResource(rl);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            // 循环读满（资源流可能来自压缩包，available() 不可信）
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(65536);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        return parent.has(key) ? parent.getAsJsonObject(key) : null;
    }

    private static JsonArray arr(JsonObject parent, String key) {
        return parent.has(key) ? parent.getAsJsonArray(key) : null;
    }
}
