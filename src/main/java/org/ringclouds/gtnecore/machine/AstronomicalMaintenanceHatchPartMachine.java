package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CleaningMaintenanceHatchPartMachine;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 天文维护仓：便携提供天文观测站同款环境的维护仓（捏他超净维护仓）。
 *
 * 机制与超净维护仓完全同源（CleaningMaintenanceHatchPartMachine）：作为部件加入
 * 多方块控制器时，把 DUMMY 环境提供者（恒"观测就绪"的 ASTRONOMICAL 类型）注入
 * 宿主——宿主多方块的配方即视为处于天文观测环境；移除时解绑。
 * 同时继承自动维护仓的全部行为（自动清除维护问题）。
 */
public class AstronomicalMaintenanceHatchPartMachine extends CleaningMaintenanceHatchPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AstronomicalMaintenanceHatchPartMachine.class, CleaningMaintenanceHatchPartMachine.MANAGED_FIELD_HOLDER);

    public AstronomicalMaintenanceHatchPartMachine(IMachineBlockEntity holder) {
        super(holder, AstronomicalObservatoryMachine.ASTRONOMICAL);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
