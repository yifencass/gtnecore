package org.ringclouds.gtnecore.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.ringclouds.gtnecore.halo.HaloUnlockData;
import org.ringclouds.gtnecore.item.GtnecoreItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Player.tick 上的 GT之环每 tick 逻辑：
 * - 物品无冷却：佩戴时直接清空冷却表（原版物品即用即好）
 * - 创造飞行：佩戴时 mayfly=true（双击空格切换，同创造体验）；摘下恢复
 *   （只还原由我们置位的玩家，不干扰创造模式）
 * - 解锁记录（服务端）：低频扫描背包，持有 GT之环即写入世界存档解锁记录
 *   （主能力通道，不经过成就系统）
 */
@Mixin(Player.class)
public abstract class PlayerTickMixin {

    private static final Set<UUID> MAYFLY_GRANTED = new HashSet<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void gtne$haloTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        boolean wearing = HaloAbilities.isWearingHaloCached(self);

        if (!self.level().isClientSide() && self instanceof ServerPlayer serverPlayer) {
            // 解锁记录：每 200 tick（10 秒）扫一次背包，持有 GT之环即永久解锁。
            // 触发不依赖成就系统（成就被 mod 禁用也不影响解锁与能力判定）。
            if (self.tickCount % 200 == 0) {
                HaloUnlockData data = HaloUnlockData.get(serverPlayer.serverLevel());
                if (!data.isUnlocked(self.getUUID()) && hasHaloInInventory(self)) {
                    data.unlock(self.getUUID());
                }
            }
        }

        if (wearing) {
            // 物品无冷却：清空冷却表（两端一致，佩戴期间即用即好）
            ((ItemCooldownsAccessor) self.getCooldowns()).gtne$getCooldowns().clear();
        }

        if (!self.level().isClientSide()) {
            if (wearing) {
                if (!self.getAbilities().mayfly) {
                    self.getAbilities().mayfly = true;
                    MAYFLY_GRANTED.add(self.getUUID());
                    if (self instanceof ServerPlayer serverPlayer) {
                        serverPlayer.onUpdateAbilities();
                    }
                }
            } else if (MAYFLY_GRANTED.remove(self.getUUID())) {
                self.getAbilities().mayfly = false;
                self.getAbilities().flying = false;
                if (self instanceof ServerPlayer serverPlayer) {
                    serverPlayer.onUpdateAbilities();
                }
            }
        }
    }

    /** 背包/盔甲/副手/Curios 饰品栏是否持有 GT之环。 */
    private static boolean hasHaloInInventory(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(GtnecoreItems.GT_HALO.get())) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty() && stack.is(GtnecoreItems.GT_HALO.get())) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && stack.is(GtnecoreItems.GT_HALO.get())) {
                return true;
            }
        }
        // Curios 饰品栏（GT之环常见戴在 head 槽，背包扫描找不到）
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(GtnecoreItems.GT_HALO.get()).isPresent())
                .orElse(false);
    }
}
