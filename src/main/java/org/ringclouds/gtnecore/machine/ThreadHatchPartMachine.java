package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;

import lombok.Getter;

/**
 * 线程仓部件：给多方块提供"线程"（同时运行多种配方）。
 *
 * 三品位（构造参数 grade）：NORMAL（与并行仓相斥）/ SPLIT（均分并行）/ MULT（倍频并行）。
 * 线程数随档位 ×2 递进：LV=1、MV=2、HV=4…UHV=256（玩家可在 UI 调小到 1）。
 *
 * 接入：注册声明 {@link IThreadHatch#THREAD_ABILITY} 能力；机器 pattern 用
 * abilities(THREAD_ABILITY) 开仓位；控制器成型时 instanceof IThreadHatch 收集。
 */
public class ThreadHatchPartMachine extends TieredPartMachine implements IFancyUIMachine, IThreadHatch {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ThreadHatchPartMachine.class, MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    private final ThreadGrade grade;
    private final int maxThreads;

    @Persisted
    @Getter
    private int currentThreads = 1;

    public ThreadHatchPartMachine(IMachineBlockEntity holder, int tier, ThreadGrade grade) {
        super(holder, tier);
        this.grade = grade;
        // 线程数 = 2^(tier - LV)：LV=1、MV=2、HV=4、EV=8、IV=16、LuV=32、ZPM=64、UV=128、UHV=256
        this.maxThreads = 1 << Math.max(0, tier - GTValues.LV);
        this.currentThreads = this.maxThreads;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public ThreadGrade getThreadGrade() {
        return grade;
    }

    @Override
    public int getThreads() {
        return currentThreads;
    }

    @Override
    public int getMaxThreads() {
        return maxThreads;
    }

    public void setCurrentThreads(int threads) {
        this.currentThreads = Mth.clamp(threads, 1, this.maxThreads);
        // 线程数变化会影响配方选择结果 → 让宿主机器的配方逻辑重新搜索
        for (IMultiController controller : this.getControllers()) {
            if (controller instanceof IRecipeLogicMachine rlm) {
                rlm.getRecipeLogic().markLastRecipeDirty();
            }
        }
    }

    /** 简易 UI：线程数输入（1..max）+ 品位/档位只读文本 */
    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 120, 40);
        group.addWidget(new IntInputWidget(this::getCurrentThreads, this::setCurrentThreads)
                .setMin(1)
                .setMax(maxThreads));
        return group;
    }
}
