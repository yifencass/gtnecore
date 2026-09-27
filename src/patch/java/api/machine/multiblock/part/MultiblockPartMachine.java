package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * GTNEcore patch 版：官方 MultiblockPartMachine 的 replacePartModelWhenFormed 原实现
 * 要求渲染状态含 IS_FORMED 属性且为 true 才伪装机壳——官方部件（能源仓/输出总线等）
 * 注册时无此属性 → 永远 false → 成型后部件保持自身外观（appearanceBlock 形同虚设）。
 * patch：无 IS_FORMED 属性时默认返回 true，配合 IMultiController.getPartAppearance 的
 * appearanceBlock fallback，让注册了 appearanceBlock 的控制器（反曲率引擎）成型后
 * 部件机壳同步控制器机壳贴图。
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockPartMachine extends MetaMachine implements IMultiPart {
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MultiblockPartMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);
    @DescSynced
    @UpdateListener(methodName = "onControllersUpdated")
    protected final Set<BlockPos> controllerPositions = new ObjectOpenHashSet<>(8);
    protected final SortedSet<IMultiController> controllers = new ReferenceLinkedOpenHashSet<>(8);
    @Nullable
    private RecipeHandlerList handlerList;

    public MultiblockPartMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public boolean hasController(BlockPos controllerPos) {
        return this.controllerPositions.contains(controllerPos);
    }

    @Override
    public boolean isFormed() {
        return !this.controllerPositions.isEmpty();
    }

    public void onControllersUpdated(Set<BlockPos> newPositions, Set<BlockPos> old) {
        this.controllers.clear();
        for (BlockPos blockPos : newPositions) {
            if (MetaMachine.getMachine(this.getLevel(), blockPos) instanceof IMultiController controller) {
                this.controllers.add(controller);
            }
        }
    }

    @Override
    @UnmodifiableView
    public SortedSet<IMultiController> getControllers() {
        if (this.controllers.size() != this.controllerPositions.size()) {
            this.onControllersUpdated(this.controllerPositions, Collections.emptySet());
        }
        return Collections.unmodifiableSortedSet(this.controllers);
    }

    @Override
    public List<RecipeHandlerList> getRecipeHandlers() {
        return List.of(this.getHandlerList());
    }

    protected RecipeHandlerList getHandlerList() {
        if (this.handlerList == null) {
            List<IRecipeHandler<?>> handlers = new ArrayList<>();
            IO handlerIO = null;
            for (MachineTrait trait : this.traits) {
                if (trait instanceof IRecipeHandlerTrait<?> rht) {
                    if (handlerIO == null) {
                        handlerIO = rht.getHandlerIO();
                    }
                    handlers.add(rht);
                }
            }
            this.handlerList = handlers.isEmpty()
                    ? RecipeHandlerList.NO_DATA
                    : RecipeHandlerList.of(handlerIO, this.getPaintingColor(), handlers);
        }
        return this.handlerList;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (this.getLevel() instanceof ServerLevel serverLevel) {
            for (IMultiController controller : this.controllers.size() > 1
                    ? new ObjectOpenHashSet<>(this.controllers) : this.controllers) {
                if (serverLevel.isLoaded(controller.self().getPos())) {
                    this.removedFromController(controller);
                    controller.onPartUnload();
                }
            }
        }
        this.controllerPositions.clear();
        this.controllers.clear();
    }

    @Override
    @MustBeInvokedByOverriders
    public void removedFromController(IMultiController controller) {
        this.controllerPositions.remove(controller.self().getPos());
        this.controllers.remove(controller);
        if (this.controllers.isEmpty()) {
            MachineRenderState renderState = this.getRenderState();
            if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
                this.setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, false));
            }
        }
    }

    @Override
    @MustBeInvokedByOverriders
    public void addedToController(IMultiController controller) {
        this.controllerPositions.add(controller.self().getPos());
        this.controllers.add(controller);
        MachineRenderState renderState = this.getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
            this.setRenderState(renderState.setValue(GTMachineModelProperties.IS_FORMED, true));
        }
    }

    @Override
    public boolean replacePartModelWhenFormed() {
        MachineRenderState renderState = this.getRenderState();
        if (renderState.hasProperty(GTMachineModelProperties.IS_FORMED)) {
            return (Boolean) renderState.getValue(GTMachineModelProperties.IS_FORMED);
        }
        // GTNEcore patch：无 IS_FORMED 渲染属性的部件（官方能源仓/输出总线/维护仓等）
        // 默认伪装成控制器机壳（配合 IMultiController.getPartAppearance 的 fallback）
        return true;
    }

    @Override
    @Nullable
    public BlockState getFormedAppearance(BlockState sourceState, BlockPos sourcePos, Direction side) {
        return !this.replacePartModelWhenFormed() ? null
                : IMultiPart.super.getFormedAppearance(sourceState, sourcePos, side);
    }
}
