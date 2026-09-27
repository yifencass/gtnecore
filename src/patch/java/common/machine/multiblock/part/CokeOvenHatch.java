package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.FluidTankProxyTrait;
import com.gregtechceu.gtceu.api.machine.trait.ItemHandlerProxyTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.CokeOvenMachine;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;
import org.ringclouds.gtnecore.util.FluidTankComposite;
import org.ringclouds.gtnecore.util.ItemHandlerComposite;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 焦炉仓（patch 层副本）。
 *
 * <h2>本项目改动：允许<b>多台焦炉共享</b>同一个仓（2026-09-24，用户指定）</h2>
 *
 * 原版 {@link #canShared()} 返回 {@code false}，配合 GTCEu 的
 * {@code BlockPattern} 判定（{@code part.isFormed() && !part.canShared() && !part.hasController(pos)}），
 * 相邻焦炉共壁时第二个结构会因 {@code multiblocked.pattern.error.share} 而无法成型。
 *
 * <p>
 * 放开限制后必须同时解决<b>单值 proxy</b> 的问题：原 {@code addedToController} 把
 * {@code inputInventory/outputInventory/tank} 的 proxy 指向<b>某一台</b>焦炉的库存，
 * 共享时后成型者会覆盖前者，另一台的管道自动化就失效了。
 *
 * <p>
 * 这里改为在<b>构造期</b>就把 proxy 指向 {@link ItemHandlerComposite} / {@link FluidTankComposite}
 * ——合并视图。它们的委托列表由 {@link #sharedCokeOvens()} <b>实时</b>从 {@code controllers}
 * 集合里筛出焦炉，因此：
 * <ul>
 * <li>成型/解体/增减共享者都<b>不需要重新绑定</b>；</li>
 * <li>也就不必改动 GTCEu 的 {@code addedToController}/{@code removedFromController}
 * ——那两个方法体里是 lambda，Mixin 定位不可靠，能不动就不动。</li>
 * </ul>
 *
 * <p>
 * 语义：仓对外表现为"共享它的所有焦炉的合并库存"（槽位/罐位统一编号，见两个组合器的类注释）。
 * 吞吐不会翻倍——共享的是通道，各焦炉仍有自己的内部缓冲。
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CokeOvenHatch extends MultiblockPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(CokeOvenHatch.class,
            MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    public final ItemHandlerProxyTrait inputInventory, outputInventory;
    public final FluidTankProxyTrait tank;

    /**
     * 三个"合并视图"实例**建一次、长期复用**。
     *
     * <p>
     * 为什么不能每次 {@code new}：{@link #onUnload()} 里会把 proxy 置 {@code null}（GTCEu 原逻辑），
     * 若组合器是临时对象就再也拿不回来了 → 重载后 NPE/失效。固定实例后，
     * {@link #onLoad()} 复位成同一个即可。
     */
    private final ItemHandlerComposite importComposite;
    private final ItemHandlerComposite exportComposite;
    private final FluidTankComposite fluidComposite;

    @Nullable
    protected TickableSubscription autoIOSubs;
    @Nullable
    protected ISubscription outputInventorySubs, outputTankSubs;

    public CokeOvenHatch(IMachineBlockEntity holder, Object... args) {
        super(holder);
        // 共享支持（2026-09-24，用户指定）：焦炉仓可被多台焦炉共用。
        // 原版 proxy 是单值字段，addedToController 会让"后成型的焦炉"覆盖前一台的绑定，
        // 共享等于半残。这里改为指向**合并视图**，其委托列表由 supplier **实时**从
        // controllers 里筛出焦炉 —— 成型/解体/共享关系变化都不需要重新绑定。
        this.importComposite = new ItemHandlerComposite(this::sharedImportHandlers);
        this.exportComposite = new ItemHandlerComposite(this::sharedExportHandlers);
        this.fluidComposite = new FluidTankComposite(this::sharedFluidHandlers);
        this.inputInventory = new ItemHandlerProxyTrait(this, IO.IN).setProxy(importComposite);
        this.outputInventory = new ItemHandlerProxyTrait(this, IO.OUT).setProxy(exportComposite);
        this.tank = new FluidTankProxyTrait(this, IO.BOTH).setProxy(fluidComposite);
    }

    /**
     * 当前共享本仓的焦炉列表（**每次调用现算**）。
     *
     * <p>
     * 基类 {@code controllers} 是 {@code Set<IMultiController>}，天生支持"一个部件属于多个控制器"，
     * 所以共享时这里会自然返回多台焦炉。
     */
    private List<CokeOvenMachine> sharedCokeOvens() {
        List<CokeOvenMachine> list = new ArrayList<>(2);
        for (IMultiController controller : controllers) {
            if (controller instanceof CokeOvenMachine oven) list.add(oven);
        }
        return list;
    }

    /**
     * 焦炉**输入**物品的合并视图委托（对应原版 {@code inputInventory.setProxy(cokeOven.importItems)}）。
     *
     * <p>
     * 组合器每次调用都会重新求值，所以焦炉成型/解体、共享关系增减都不需要重新绑定 proxy。
     */
    private List<NotifiableItemStackHandler> sharedImportHandlers() {
        List<NotifiableItemStackHandler> list = new ArrayList<>(2);
        for (CokeOvenMachine oven : sharedCokeOvens()) {
            list.add(oven.importItems);
        }
        return list;
    }

    /** 焦炉**输出**物品的合并视图委托（对应原版 {@code outputInventory.setProxy(cokeOven.exportItems)}）。 */
    private List<NotifiableItemStackHandler> sharedExportHandlers() {
        List<NotifiableItemStackHandler> list = new ArrayList<>(2);
        for (CokeOvenMachine oven : sharedCokeOvens()) {
            list.add(oven.exportItems);
        }
        return list;
    }

    /** 输出流体的合并视图委托（对应原版 {@code tank.setProxy(cokeOven.exportFluids)}）。 */
    private List<NotifiableFluidTank> sharedFluidHandlers() {
        List<NotifiableFluidTank> list = new ArrayList<>(2);
        for (CokeOvenMachine oven : sharedCokeOvens()) {
            list.add(oven.exportFluids);
        }
        return list;
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////
    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 复位组合器（onUnload 会把 proxy 置 null）
        this.inputInventory.setProxy(importComposite);
        this.outputInventory.setProxy(exportComposite);
        this.tank.setProxy(fluidComposite);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        inputInventory.setProxy(null);
        outputInventory.setProxy(null);
        tank.setProxy(null);
        if (outputInventorySubs != null) {
            outputInventorySubs.unsubscribe();
            outputInventorySubs = null;
        }
        if (outputTankSubs != null) {
            outputTankSubs.unsubscribe();
            outputTankSubs = null;
        }
    }

    /**
     * ⚠️ 与原版的关键差异：**不再在这里 {@code setProxy(...)}**。
     *
     * <p>
     * 原版把 proxy 指向"某一台焦炉"的库存，共享时后成型者覆盖前者。现在 proxy 常驻指向
     * 合并视图（构造期设定、onLoad 复位），委托列表实时从 {@code controllers} 求值，
     * 因此这里只维护 autoIO 所需的**变化订阅**。
     */
    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        // 订阅只用于驱动 autoIO（把内容推给相邻管道）；共享时订"最后一台"即可，
        // 因为任何一台的库存变化都值得触发一次检查。
        if (controller instanceof CokeOvenMachine cokeOven) {
            if (outputInventorySubs != null) outputInventorySubs.unsubscribe();
            if (outputTankSubs != null) outputTankSubs.unsubscribe();
            outputInventorySubs = cokeOven.exportItems.addChangedListener(this::updateAutoIOSubscription);
            outputTankSubs = cokeOven.exportFluids.addChangedListener(this::updateAutoIOSubscription);
            this.updateAutoIOSubscription();
        }
    }

    /**
     * 同样**不再重绑 proxy**（组合器实时查询 controllers），只处理订阅：
     * 解体的那台若刚好是订阅对象，就改订到剩余焦炉；都没有了才清空。
     */
    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        if (controller instanceof CokeOvenMachine) {
            if (outputInventorySubs != null) {
                outputInventorySubs.unsubscribe();
                outputInventorySubs = null;
            }
            if (outputTankSubs != null) {
                outputTankSubs.unsubscribe();
                outputTankSubs = null;
            }
            for (CokeOvenMachine oven : sharedCokeOvens()) {
                outputInventorySubs = oven.exportItems.addChangedListener(this::updateAutoIOSubscription);
                outputTankSubs = oven.exportFluids.addChangedListener(this::updateAutoIOSubscription);
                break;
            }
        }
    }

    /** 共享支持：原版返回 false（禁止共享），本项目放开。 */
    @Override
    public boolean canShared() {
        return true;
    }

    @Override
    public boolean replacePartModelWhenFormed() {
        return false;
    }

    //////////////////////////////////////
    // ******** Auto IO *********//
    //////////////////////////////////////

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoIOSubscription();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        updateAutoIOSubscription();
    }

    protected void updateAutoIOSubscription() {
        if ((!outputInventory.isEmpty() &&
                GTTransferUtils.hasAdjacentItemHandler(getLevel(), getPos(), getFrontFacing())) ||
                (!tank.isEmpty() && GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getPos(), getFrontFacing()))) {
            autoIOSubs = subscribeServerTick(autoIOSubs, this::autoIO);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    protected void autoIO() {
        if (getOffsetTimer() % 5 == 0) {
            outputInventory.exportToNearby(getFrontFacing());
            tank.exportToNearby(getFrontFacing());
            updateAutoIOSubscription();
        }
    }

    //////////////////////////////////////
    // ********* GUI *********//
    //////////////////////////////////////
    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }
}
