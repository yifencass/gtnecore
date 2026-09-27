package org.ringclouds.gtnecore.machine;

import net.minecraft.util.StringRepresentable;

/**
 * 线程仓三品位：
 * - NORMAL 线程仓：提供线程，与并行仓相斥（机器有并行时并行作废，每线程并行 1）
 * - SPLIT 均分线程仓：线程均分机器并行（每线程 floor(P/T)，无并行时 1）
 * - MULT 倍频线程仓：每线程独享全部机器并行（总吞吐 T×P，无并行时 1）
 */
public enum ThreadGrade implements StringRepresentable {
    NORMAL("normal"),
    SPLIT("split"),
    MULT("mult");

    private final String serializedName;

    ThreadGrade(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** 品位对应的物品/机器 id 后缀 */
    public String idSuffix() {
        return switch (this) {
            case NORMAL -> "thread_hatch";
            case SPLIT -> "split_thread_hatch";
            case MULT -> "mult_thread_hatch";
        };
    }

    public String translationKey() {
        return "gtnecore.thread_grade." + serializedName;
    }
}
