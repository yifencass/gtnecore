package org.ringclouds.gtnecore.halo;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.ringclouds.gtnecore.Gtnecore;
import org.ringclouds.gtnecore.item.GtnecoreItems;
import top.theillusivec4.curios.api.CuriosApi;

import java.lang.reflect.Method;

/**
 * GT之环公共辅助：能力判定（多通道复合，与佩戴解耦）。
 *
 * 2026-08-20 改版：GT之环的全部功能只需要玩家"获得过" GT之环，不需要佩戴。
 * 判定走三通道 OR（任一命中即有能力），尽量不被 mod 封锁：
 * 1) HaloUnlockData（世界存档级 UUID 记录）——主通道，由服务端背包扫描触发，
 *    不经过成就系统/事件链/网络包，一般 mod 无法拦截
 * 2) GT之环成就——兼容通道（成就系统被 mod 禁用时自动失效，不影响主通道）
 * 3) Curios 实际佩戴——兜底通道
 *
 * 客户端渲染（光环/夜视色调等）用 成就 OR 佩戴（存档数据不同步到客户端）。
 */
public final class HaloAbilities {

    /** 获得 GT之环的成就 id。 */
    public static final ResourceLocation HALO_ADVANCEMENT = Gtnecore.id("gt_halo_obtained");

    /** 免死动画实体事件 id（避开原版 0~67 已用值）。 */
    public static final byte TOTEM_ANIM_EVENT_ID = 70;

    private HaloAbilities() {
    }

    /** 实体是否拥有 GT之环能力（多通道复合，双端）。 */
    public static boolean isWearingHalo(LivingEntity entity) {
        return entity instanceof Player player && hasHaloAbility(player);
    }

    /** 每 tick 判定用（记录/成就/佩戴查询都廉价且状态永久，无需缓存）。 */
    public static boolean isWearingHaloCached(Player player) {
        return isWearingHalo(player);
    }

    /** 玩家是否拥有 GT之环能力：存档记录 OR 成就 OR 实际佩戴。
     *  服务端命中成就/佩戴时自动写入存档记录（自愈）——首次判定后永久走记录通道，
     *  之后成就/佩戴/Curios 被 mod 移除也不影响能力。 */
    public static boolean hasHaloAbility(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            HaloUnlockData data = HaloUnlockData.get(serverLevel);
            if (data.isUnlocked(player.getUUID())) {
                return true;
            }
            // 自愈：成就/佩戴命中即写记录（一次性迁移；涵盖旧成就玩家与仅佩戴的玩家）
            if (hasHaloAchievement(player) || isActuallyWearingHalo(player)) {
                data.unlock(player.getUUID());
                return true;
            }
            return false;
        }
        // 客户端：成就 OR 佩戴（存档数据不同步到客户端，仅影响渲染表现）
        return hasHaloAchievement(player) || isActuallyWearingHalo(player);
    }

    /** Curios 实际佩戴检查（兜底通道，双端可用）。 */
    public static boolean isActuallyWearingHalo(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return CuriosApi.getCuriosInventory(entity)
                .map(inv -> inv.findFirstCurio(GtnecoreItems.GT_HALO.get()).isPresent())
                .orElse(false);
    }

    /** 玩家是否获得过 GT之环成就（双端）。 */
    public static boolean hasHaloAchievement(Player player) {
        if (player == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            var advancement = serverPlayer.getServer().getAdvancements().getAdvancement(HALO_ADVANCEMENT);
            return advancement != null
                    && serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone();
        }
        return hasHaloAchievementClient(player);
    }

    /**
     * 客户端：Player 基类没有 getAdvancements()（在 LocalPlayer 上，客户端专用类），
     * 且 ClientAdvancements.getAdvancement 不在公共接口里——全反射调用。
     * LocalPlayer.getAdvancements() → ClientAdvancements（成就数据由服务器同步）。
     */
    private static boolean hasHaloAchievementClient(Player player) {
        try {
            Method getAdvancements = player.getClass().getMethod("getAdvancements");
            Object advancements = getAdvancements.invoke(player);
            if (advancements == null) {
                return false;
            }
            Method getAdvancement = advancements.getClass().getMethod("getAdvancement", ResourceLocation.class);
            Object advancement = getAdvancement.invoke(advancements, HALO_ADVANCEMENT);
            if (advancement == null) {
                return false;
            }
            Method getProgress = advancements.getClass().getMethod("getOrStartProgress", Advancement.class);
            Object progress = getProgress.invoke(advancements, advancement);
            return (boolean) progress.getClass().getMethod("isDone").invoke(progress);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
