package org.ringclouds.gtnecore.planner.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.ringclouds.gtnecore.planner.api.PlannerRecipe;
import org.ringclouds.gtnecore.planner.api.PlannerStack;
import org.ringclouds.gtnecore.planner.api.RecipeCategory;
import org.ringclouds.gtnecore.planner.api.RecipeLookup;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 规划图持久化：纯客户端，写入 config/gtnecore/recipe_planner.json。
 * 配方本体不存（查询结果），存"期望产物 + 签名"，加载时重新查配方恢复。
 */
public final class PlannerPersistence {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("gtnecore/recipe_planner.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PlannerPersistence() {
    }

    public static void save(PlannerGraph graph) {
        JsonObject root = new JsonObject();
        JsonArray nodeArr = new JsonArray();
        for (PlannerNode n : graph.nodes()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", n.id);
            o.addProperty("x", n.x);
            o.addProperty("y", n.y);
            o.addProperty("h", n.height);
            o.addProperty("m", n.multiplier);
            o.addProperty("category", n.category.id());
            o.add("desired", stacksToJson(n.desiredOutputs));
            if (n.recipe != null) o.addProperty("sig", recipeSignature(n.recipe));
            nodeArr.add(o);
        }
        JsonArray edgeArr = new JsonArray();
        for (PlannerEdge e : graph.edges()) {
            JsonObject o = new JsonObject();
            o.addProperty("from", e.fromNode());
            o.addProperty("fo", e.fromOutput());
            o.addProperty("to", e.toNode());
            o.addProperty("ti", e.toInput());
            edgeArr.add(o);
        }
        root.add("nodes", nodeArr);
        root.add("edges", edgeArr);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.error("规划图保存失败", ex);
        }
    }

    public static PlannerGraph load(RecipeLookup lookup) {
        PlannerGraph g = new PlannerGraph();
        if (!Files.exists(FILE)) return g;
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) return g;
            Map<Integer, PlannerNode> byId = new HashMap<>();
            for (JsonElement el : root.getAsJsonArray("nodes")) {
                JsonObject o = el.getAsJsonObject();
                PlannerNode n = g.addNode(o.get("id").getAsInt(), o.get("x").getAsInt(), o.get("y").getAsInt());
                if (o.has("h")) n.height = o.get("h").getAsInt();
                if (o.has("m")) n.multiplier = o.get("m").getAsInt();
                n.category = RecipeCategory.byId(o.get("category").getAsString());
                n.desiredOutputs = jsonToStacks(o.getAsJsonArray("desired"));
                if (o.has("sig")) n.recipe = findRecipe(lookup, n.category, n.desiredOutputs, o.get("sig").getAsString());
                byId.put(n.id, n);
            }
            if (root.has("edges")) {
                for (JsonElement el : root.getAsJsonArray("edges")) {
                    JsonObject o = el.getAsJsonObject();
                    g.addEdge(o.get("from").getAsInt(), o.get("fo").getAsInt(), o.get("to").getAsInt(), o.get("ti").getAsInt());
                }
            }
            g.recalc();
        } catch (Exception ex) {
            LOGGER.error("规划图加载失败（忽略，从空图开始）", ex);
        }
        return g;
    }

    // ---- 配方恢复：按 期望产物 + 输入/输出签名 反查 ----

    private static PlannerRecipe findRecipe(RecipeLookup lookup, RecipeCategory cat, List<PlannerStack> wanted, String sig) {
        if (!lookup.isAvailable()) return null;
        for (PlannerRecipe r : lookup.find(cat, wanted, List.of())) {
            if (recipeSignature(r).equals(sig)) return r;
        }
        return null;
    }

    /** 配方签名 = 输入/输出物料 id+数量 的有序串（识别同一配方） */
    private static String recipeSignature(PlannerRecipe r) {
        StringJoiner j = new StringJoiner("|");
        for (PlannerStack s : r.inputs()) j.add("i:" + s.idString() + ":" + s.amount());
        for (PlannerStack s : r.outputs()) j.add("o:" + s.idString() + ":" + s.amount());
        return j.toString();
    }

    // ---- 栈 JSON 序列化 ----
    // 物品: {t:"item", id, c, n?}；流体: {t:"fluid", id, a, n?}；n 为 NBT(JsonOps 转换)

    private static JsonArray stacksToJson(List<PlannerStack> stacks) {
        JsonArray arr = new JsonArray();
        for (PlannerStack s : stacks) {
            if (s.isEmpty()) continue;
            JsonObject o = new JsonObject();
            if (s.isFluid()) {
                o.addProperty("t", "fluid");
                o.addProperty("id", s.idString());
                o.addProperty("a", s.fluid().getAmount());
                if (s.fluid().getTag() != null) o.add("n", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, s.fluid().getTag()));
            } else {
                o.addProperty("t", "item");
                o.addProperty("id", s.idString());
                o.addProperty("c", s.item().getCount());
                if (s.item().getTag() != null) o.add("n", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, s.item().getTag()));
            }
            arr.add(o);
        }
        return arr;
    }

    private static List<PlannerStack> jsonToStacks(JsonArray arr) {
        List<PlannerStack> list = new ArrayList<>();
        if (arr == null) return list;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String id = o.get("id").getAsString();
            CompoundTag tag = o.has("n") ? (CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, o.get("n")) : null;
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null) continue;
            if ("fluid".equals(o.get("t").getAsString())) {
                Fluid f = ForgeRegistries.FLUIDS.getValue(loc);
                if (f == null || f == Fluids.EMPTY) continue;
                list.add(PlannerStack.of(new FluidStack(f, o.get("a").getAsInt(), tag)));
            } else {
                Item i = ForgeRegistries.ITEMS.getValue(loc);
                if (i == null || i == Items.AIR) continue;
                list.add(PlannerStack.of(new ItemStack(i, o.get("c").getAsInt(), tag)));
            }
        }
        return list;
    }
}
