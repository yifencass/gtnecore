package org.ringclouds.gtnecore.mixin;

import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.world.entity.player.Player;
import org.ringclouds.gtnecore.halo.HaloAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Goety 灵魂能量消耗减免（佩戴 GT之环）：
 * - 消耗 × 0.000001（-99.9999%）
 * - 减免后 ≤1 的消耗归零（不扣灵魂）
 * decreaseSESouls 的布尔语义保留（0 消耗恒成功）。
 *
 * remap=false：SEHelper 是 Goety mod 类（无 SRG 映射，Goety 注入先例）。
 */
@Mixin(SEHelper.class)
public abstract class GoetySoulCostMixin {

    private static final double SOUL_REDUCTION = 0.000001;

    // @ModifyVariable 处理器签名：被修改值（int）在前，随后是目标方法的全部参数
    @ModifyVariable(method = "decreaseSouls", ordinal = 0, argsOnly = true, at = @At("HEAD"), remap = false)
    private static int gtne$reduceSoulCost(int value, Player player, int amount) {
        return reducedSoulCost(player, value);
    }

    @ModifyVariable(method = "decreaseSESouls", ordinal = 0, argsOnly = true, at = @At("HEAD"), remap = false)
    private static int gtne$reduceSoulCostBool(int value, Player player, int amount) {
        return reducedSoulCost(player, value);
    }

    private static int reducedSoulCost(Player player, int amount) {
        if (HaloAbilities.isWearingHalo(player)) {
            int reduced = (int) (amount * SOUL_REDUCTION);
            return reduced <= 1 ? 0 : reduced;
        }
        return amount;
    }
}
