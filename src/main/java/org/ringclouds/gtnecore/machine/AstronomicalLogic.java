package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.common.machine.trait.CleanroomLogic;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 天文观测站逻辑：超净间逻辑（校准 = 清洁度）+ 无线供电。
 *
 * 校准：沿用 CleanroomLogic 全套——校准期耗 VA[tier]/t，就绪后 3/16 V[tier]/t，
 * 维护问题 ≥6 或处于环境污染区时衰减（对应"观测条件被干扰"）。
 * 无线供电：每次 serverTick 在校准逻辑之后推送（校准耗电优先）。
 */
public class AstronomicalLogic extends CleanroomLogic {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AstronomicalLogic.class, CleanroomLogic.MANAGED_FIELD_HOLDER);

    public AstronomicalLogic(AstronomicalObservatoryMachine machine) {
        super(machine);
    }

    @Override
    public AstronomicalObservatoryMachine getMachine() {
        return (AstronomicalObservatoryMachine) super.getMachine();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void serverTick() {
        super.serverTick();
        getMachine().wirelessPowerTick();
    }
}
