package org.ringclouds.gtnecore.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** 暴露 ItemCooldowns 的冷却表，供"直接清除冷却"使用。 */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {

    @Accessor("cooldowns")
    Map<Item, ?> gtne$getCooldowns();
}
