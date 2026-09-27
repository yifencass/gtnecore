package org.ringclouds.gtnecore.research;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 研究进度存储（**世界级共享**）。
 *
 * <h2>为什么不做团队共享（暂时）</h2>
 * 用户决策：先不做团队共享机制，把研究本身跑通更重要。
 * 团队共享是乘法复杂度（FTB Teams 是 ARR 不能硬依赖 → 只能反射软适配 + 回退），
 * 等核心链路验证完再考虑。
 *
 * <h2>存储</h2>
 * 走 {@code SavedData}（世界 dataStorage），骨架参考
 * {@code org.ringclouds.gtnecore.halo.HaloUnlockData}，
 * 但**去掉 UUID 维度**——那是按玩家永久解锁，语义不同；这里是存档级共享。
 *
 * <h2>放置位置</h2>
 * 主世界（{@link Level#OVERWORLD}）的 dataStorage。多人时所有维度共用同一份，
 * 因为存档级共享没有"哪个维度"的概念。
 */
public final class ResearchProgress extends SavedData {

    public static final String NAME = "gtnecore_research";

    private static final String TAG_KEY = "completed";

    private final Set<ResourceLocation> completed = new LinkedHashSet<>();

    private ResearchProgress() {
    }

    // ==================== 取用 ====================

    /** 从服务器取（推荐入口，任何上下文都能用）。 */
    public static ResearchProgress get(MinecraftServer server) {
        return get(server.overworld());
    }

    /** 从任意维度取（内部统一落到主世界，保证全局一份）。 */
    public static ResearchProgress get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage()
                .computeIfAbsent(ResearchProgress::load, ResearchProgress::new, NAME);
    }

    /** 从可能为 null 的 Level 取；拿不到就返回 null（调用方需判空）。 */
    public static @Nullable ResearchProgress getOrNull(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null; // 客户端没有进度数据
        }
        return get(serverLevel);
    }

    // ==================== 查询 / 修改 ====================

    /** 是否已完成。**未注册的研究 id 返回 false**（宁可不解锁，也不放行可疑配方）。 */
    public boolean isCompleted(@Nullable ResourceLocation research) {
        return research != null && completed.contains(research);
    }

    /** 标记完成；返回是否发生了变化。 */
    public boolean complete(ResourceLocation research) {
        if (completed.add(research)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** 撤销完成（调试用）。 */
    public boolean revoke(ResourceLocation research) {
        if (completed.remove(research)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** 已完成的全部研究（只读）。 */
    public Set<ResourceLocation> all() {
        return Collections.unmodifiableSet(completed);
    }

    // ==================== 序列化 ====================

    public static ResearchProgress load(CompoundTag tag) {
        ResearchProgress data = new ResearchProgress();
        ListTag list = tag.getList(TAG_KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                data.completed.add(id);
            }
            // 解析失败的条目静默忽略，不让坏存档卡住加载
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ResourceLocation id : completed) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_KEY, list);
        return tag;
    }
}
