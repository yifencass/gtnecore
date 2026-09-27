package org.ringclouds.gtnecore.mixin;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.ringclouds.gtnecore.machine.StructureTransparencyHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 结构透明化：在 MultiblockState.getBlockState() 的方块获取处重定向——
 * 若该位置在 {@link StructureTransparencyHook} 幽灵表内且实际为空气，
 * 返回隐藏前的原方块状态 → 成型重检把"已清空的环装饰"判为原方块，结构保持成型。
 *
 * 只改这一处调用点（getBlockState 缓存入口，结构校验必经）；世界渲染仍显示空气
 * （方块已物理清空），实现"成型即隐形且不解体"，可被未来多方块复用。
 */
@Mixin(MultiblockState.class)
public class StructureTransparencyMixin {

    @Redirect(method = "getBlockState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState gtnecore$ghostFallback(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || StructureTransparencyHook.isGhostState(state)) {
            BlockState ghost = StructureTransparencyHook.ghostAt(level, pos);
            if (ghost != null) {
                return ghost;
            }
        }
        return state;
    }
}
