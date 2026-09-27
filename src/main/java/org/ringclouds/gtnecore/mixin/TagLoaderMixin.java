package org.ringclouds.gtnecore.mixin;

import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/**
 * GTNEcore 把 12-14 档重排为 UMV/SWV/GCV 后，GTCEu 自带数据包标签里引用的旧档名
 * （如 uxv_machine_casing）指向不存在的方块，vanilla TagLoader 会因此把整个标签
 * 置空并刷错误日志（数据包标签只能追加、无法移除，无法从数据侧修复）。
 *
 * 这里把 TagEntry.build 的解析失败视为成功（缺失条目被静默忽略）：
 * 标签照常包含所有有效条目，不再报错、不再整标签置空。
 */
@Mixin(TagLoader.class)
public abstract class TagLoaderMixin<T> {

    @Redirect(method = "build",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/tags/TagEntry;build(Lnet/minecraft/tags/TagEntry$Lookup;Ljava/util/function/Consumer;)Z"))
    private boolean gtne$ignoreMissingEntries(TagEntry entry, TagEntry.Lookup<T> lookup, Consumer<T> consumer) {
        // 解析失败的条目（旧档名引用）直接当作已解析，避免整个标签被置空
        return entry.build(lookup, consumer) || true;
    }
}
