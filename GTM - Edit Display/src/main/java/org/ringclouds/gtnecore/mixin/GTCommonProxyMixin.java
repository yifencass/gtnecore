package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.common.CommonProxy;
import net.minecraftforge.event.AddPackFindersEvent;
import org.ringclouds.gtnecore.recipe.GtneCircuitTags;
import org.ringclouds.gtnecore.recipe.GtnePartBindings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 GTCEu 的 CommonProxy.registerPackFinders（AddPackFindersEvent 监听）内部、
 * GTCraftingComponents.init() 重建组件之后立即绑定 GTNEcore 的 12..21 档组件映射。
 *
 * 为什么必须注入到 GTCEu 方法内部而不是自己监听事件：
 * GTCEu 的 registerPackFinders 是同步的——init() 重建组件 + 全部配方生成（数秒）
 * 一气呵成。mod 总线按容器顺序分发事件，无论 GTNEcore 的监听排在容器前（绑定被
 * 后续 init() 重建覆盖）还是容器后（绑定晚于配方生成读取），结果都是配方读旧值。
 * 注入到 init() 调用之后，绑定必然先于配方生成（recipeAddition）执行。
 *
 * remap=false：registerPackFinders 与 GTCraftingComponents.init 都是 mod 类方法
 * （非 Minecraft 类），无 SRG 映射，按官方名锚定（参考 Goety mixin 先例）。
 */
@Mixin(CommonProxy.class)
public abstract class GTCommonProxyMixin {

    @Inject(method = "registerPackFinders",
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/data/recipe/GTCraftingComponents;init()V",
                    shift = At.Shift.AFTER),
            remap = false)
    private void gtne$bindComponents(AddPackFindersEvent event, CallbackInfo ci) {
        GtneCircuitTags.install();
        GtnePartBindings.install();
    }
}
