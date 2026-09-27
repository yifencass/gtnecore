package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.*;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CrateMachine extends MetaMachine implements IUIMachine, IMachineLife,
                          IDropSaveMachine, IInteractedMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(CrateMachine.class,
            MetaMachine.MANAGED_FIELD_HOLDER);

    public static final BooleanProperty TAPED_PROPERTY = GTMachineModelProperties.IS_TAPED;

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Getter
    private final Material material;
    @Getter
    private final int inventorySize;
    @Getter
    @RequireRerender
    @Persisted
    @DescSynced
    private boolean isTaped;

    @Persisted
    public final NotifiableItemStackHandler inventory;

    public CrateMachine(IMachineBlockEntity holder, Material material, int inventorySize) {
        super(holder);
        this.material = material;
        this.inventorySize = inventorySize;
        this.inventory = new NotifiableItemStackHandler(this, inventorySize, IO.BOTH);
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        int xOffset = inventorySize >= 90 ? 162 : 0;
        int yOverflow = xOffset > 0 ? 18 : 9;
        int yOffset = inventorySize > 3 * yOverflow ?
                (inventorySize - 3 * yOverflow - (inventorySize - 3 * yOverflow) % yOverflow) / yOverflow * 18 : 0;
        var modularUI = new ModularUI(176 + xOffset, 166 + yOffset, this, entityPlayer)
                .background(GuiTextures.BACKGROUND)
                .widget(new LabelWidget(5, 5, getBlockState().getBlock().getDescriptionId()))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT, 7 + xOffset / 2,
                        82 + yOffset, true));
        int x = 0;
        int y = 0;
        for (int slot = 0; slot < inventorySize; slot++) {
            modularUI.widget(new SlotWidget(inventory, slot, x * 18 + 7, y * 18 + 17)
                    .setBackgroundTexture(GuiTextures.SLOT));
            x++;
            if (x == yOverflow) {
                x = 0;
                y++;
            }
        }
        return modularUI;
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isCrouching() && !isTaped) {
            if (stack.is(GTItems.DUCT_TAPE.asItem()) || stack.is(GTItems.BASIC_TAPE.asItem())) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                isTaped = true;
                setRenderState(getRenderState().setValue(GTMachineModelProperties.IS_TAPED, isTaped));
                return InteractionResult.sidedSuccess(world.isClientSide);
            }
        }
        return IInteractedMachine.super.onUse(state, world, pos, player, hand, hit);
    }

    /**
     * 放下箱子时恢复"已封箱"状态。
     *
     * <p>
     * GTNEcore 修复（2026-09-25，用户实测）：原版这里读到物品 NBT 的 {@code taped=true} 后
     * <b>只反序列化了内容物，没有把 {@link #isTaped} 置回 true</b>，随后却用（仍为 false 的）
     * {@code isTaped} 去设渲染状态 —— 结果是"从背包里拿出来的已封箱箱子"带着 taped 物品数据、
     * 外观却按未封箱渲染，玩家看到的就是<b>凭空出现的胶带 X 形贴图与实际状态不符</b>。
     *
     * <p>
     * 另注：{@code isTaped} 带 {@code @Persisted @DescSynced @RequireRerender}，
     * 所以存盘后重新加载字段值本身是对的；但 {@code renderState} 是<b>另一个</b>
     * {@code @Persisted} 字段，渲染状态不会自动从字段重算，两者会各存各的而长期不一致，
     * 故另补 {@link #onLoad()} 用字段重算（见下），并在放置时统一走
     * {@link #syncTapedRenderState()}。
     */
    @Override
    public void onMachinePlaced(@Nullable LivingEntity player, ItemStack stack) {
        IMachineLife.super.onMachinePlaced(player, stack);
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.contains("taped") && tag.getBoolean("taped")) {
                // 修复点：先把状态恢复，再反序列化内容物（顺序与原版一致，只是补回赋值）
                this.isTaped = true;
                this.inventory.storage.deserializeNBT(tag.getCompound("inventory"));
            }
            syncTapedRenderState();
        }
    }

    /**
     * 用 {@link #isTaped} 重算 {@code IS_TAPED} 渲染状态。
     *
     * <p>
     * GTNEcore 修复（2026-09-25，取自 dev 存档实证）：{@code renderState} 是
     * {@code @Persisted @DescSynced} 字段，和 {@code isTaped} <b>分开存盘</b>。
     * 存档里出现过 {@code isTaped=0} 而 {@code renderState.taped=true} 的箱子
     * （早期一次错误改动把 true 写了进去），撤掉那处代码后 NBT 里的 true 依旧被读回来，
     * 于是世界里的箱子永远显示胶带 —— 光改写入侧是治不好的，必须在加载时按字段重算。
     *
     * <p>
     * 这与 GTCEu 自己对 {@code IS_PAINTED} 的处理方式一致：见
     * {@code MetaMachine#onLoad()} 里"update the painted model property if the machine is painted"。
     */
    private void syncTapedRenderState() {
        var renderState = getRenderState();
        if (renderState.getValue(GTMachineModelProperties.IS_TAPED) != isTaped) {
            setRenderState(renderState.setValue(GTMachineModelProperties.IS_TAPED, isTaped));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 存档里的 renderState 可能和 isTaped 不一致（历史 bug 写入的脏数据），以字段为准重算
        syncTapedRenderState();
    }

    @Override
    public void saveToItem(CompoundTag tag) {
        if (isTaped) {
            IDropSaveMachine.super.saveToItem(tag);
            tag.putBoolean("taped", isTaped);
            tag.put("inventory", inventory.storage.serializeNBT());
        }
    }

    @Override
    public boolean saveBreak() {
        return isTaped;
    }

    @Override
    public void onMachineRemoved() {
        if (!isTaped) clearInventory(inventory.storage);
    }
}
