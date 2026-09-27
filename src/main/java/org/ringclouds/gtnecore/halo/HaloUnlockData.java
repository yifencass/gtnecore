package org.ringclouds.gtnecore.halo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GT之环解锁记录的存档数据（世界存档级）。
 *
 * 能力判定的主通道：玩家曾持有 GT之环即记录 UUID，之后永久解锁。
 * 走 SavedData（世界 dataStorage），不经过成就系统/事件链/网络包——
 * 一般 mod 无法拦截（除非针对 GTNEcore 特意封锁，即"被特意针对"）。
 */
public final class HaloUnlockData extends SavedData {

    public static final String NAME = "gtnecore_halo_unlocked";

    private static final String TAG_KEY = "halo_unlocked";

    private final Set<UUID> unlocked = new HashSet<>();

    private HaloUnlockData() {
    }

    public static HaloUnlockData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(HaloUnlockData::load, HaloUnlockData::new, NAME);
    }

    public static HaloUnlockData load(CompoundTag tag) {
        HaloUnlockData data = new HaloUnlockData();
        ListTag list = tag.getList(TAG_KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.unlocked.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // 损坏条目忽略
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID uuid : unlocked) {
            list.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put(TAG_KEY, list);
        return tag;
    }

    public boolean isUnlocked(UUID uuid) {
        return unlocked.contains(uuid);
    }

    public void unlock(UUID uuid) {
        if (unlocked.add(uuid)) {
            setDirty();
        }
    }
}
