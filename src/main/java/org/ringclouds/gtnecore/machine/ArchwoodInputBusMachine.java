package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 至高木（Ars Archwood）输入总线——魔力再锻的"石器时代"输入总线：
 *
 * 真总线语义（extends ItemBusPartMachine，ULV 档）：
 * - 注册 PartAbility.IMPORT_ITEMS（注册链 abilities）→ 多方块识别 + 配方自动拉料
 * - 标准总线输入面渲染/过滤/自动 IO 全继承
 * - 容量随档位 = ULV 1 槽（(1+min(9,0))²）——外观为 Ars 至高木皮
 */
public class ArchwoodInputBusMachine extends ItemBusPartMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ArchwoodInputBusMachine.class, ItemBusPartMachine.MANAGED_FIELD_HOLDER);

    public ArchwoodInputBusMachine(IMachineBlockEntity holder) {
        super(holder, 0, com.gregtechceu.gtceu.api.capability.recipe.IO.IN); // ULV 档 → 1 槽
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
