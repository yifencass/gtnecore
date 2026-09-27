package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.DummyCleanroom;
import com.gregtechceu.gtceu.common.machine.multiblock.part.AutoMaintenanceHatchPartMachine;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import java.util.List;

/**
 * 全能维护仓（Omni Maintenance Hatch）：同时提供超净（CLEANROOM）与天文观测
 * （ASTRONOMICAL）两种环境的便携维护仓。
 *
 * 超净维护仓（CleaningMaintenanceHatchPartMachine）构造器只吃单类型（内部
 * singletonList），但其机制全部可复用：DummyCleanroom.createForTypes 吃集合——
 * 这里直接继承 AutoMaintenanceHatchPartMachine 重写绑定（绑定/解绑逻辑与原版
 * 字节码逐行一致）。
 */
public class OmniMaintenanceHatchPartMachine extends AutoMaintenanceHatchPartMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            OmniMaintenanceHatchPartMachine.class, AutoMaintenanceHatchPartMachine.MANAGED_FIELD_HOLDER);

    /** 恒洁净双环境提供者：CLEANROOM + ASTRONOMICAL */
    private final ICleanroomProvider DUMMY_ENVIRONMENT = DummyCleanroom.createForTypes(List.of(
            CleanroomType.CLEANROOM, AstronomicalObservatoryMachine.ASTRONOMICAL));

    public OmniMaintenanceHatchPartMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void addedToController(IMultiController controller) {
        super.addedToController(controller);
        if (controller instanceof ICleanroomReceiver receiver) {
            receiver.setCleanroom(DUMMY_ENVIRONMENT);
        }
    }

    @Override
    public void removedFromController(IMultiController controller) {
        super.removedFromController(controller);
        if (controller instanceof ICleanroomReceiver receiver && receiver.getCleanroom() == DUMMY_ENVIRONMENT) {
            receiver.setCleanroom(null);
        }
    }
}
