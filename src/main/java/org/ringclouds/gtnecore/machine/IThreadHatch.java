package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

/**
 * 线程仓能力接口（仿官方 {@code IParallelHatch} 先例）：
 * 多方块控制器成型时对部件做 {@code instanceof} 收集（见 ThreadFurnaceMachine.formStructure 同款逻辑），
 * ThreadRecipeLogic 据此计算线程总数 T 与品位。
 */
public interface IThreadHatch {

    /** 线程仓结构能力位（自定义 PartAbility——构造 public + registry 自注册，官方同款机制） */
    PartAbility THREAD_ABILITY = new PartAbility("thread_hatch");

    /** 品位（NORMAL 与并行仓相斥 / SPLIT 均分 / MULT 倍频） */
    ThreadGrade getThreadGrade();

    /** 当前启用的线程数（1..max，玩家可在 UI 调小以观察） */
    int getThreads();

    /** 该仓档位最大线程数（LV=1、每档 ×2） */
    int getMaxThreads();
}
