package org.ringclouds.gtnecore.item;

import committee.nova.mods.avaritia.init.registry.ModRarities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * GT之环（Curios 饰品）：头后渲染 GTCEu 的青色 G 图标光环。
 * 能力逻辑见 halo/ 包与各 mixin；本文只负责佩戴规则：
 * - 右键快速佩戴（canEquipFromUse）
 * - 只有创造模式能摘下（canUnequip），防缴械
 */
public class GtneHaloItem extends Item implements ICurioItem {

    public GtneHaloItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return slotContext.entity() instanceof Player player && player.isCreative();
    }

    /** 与无尽剑同款 COSMIC 稀有度：名字红色（波浪/双层渲染由 #gtnecore:wave_name 等 tag 驱动）。 */
    @Override
    public Rarity getRarity(ItemStack stack) {
        return ModRarities.COSMIC;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gtnecore.gt_halo.lore").withStyle(ChatFormatting.GRAY));
        // 按键提示用玩家实际绑定的键（getTranslatedKeyMessage 实时反映改键）
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            tooltip.add(Component.translatable("item.gtnecore.gt_halo.key_home",
                    org.ringclouds.gtnecore.halo.HaloKeybinds.HOME.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.gtnecore.gt_halo.key_chorus",
                    org.ringclouds.gtnecore.halo.HaloKeybinds.CHORUS.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
