package org.ringclouds.gtnecore.util;

/**
 * AE2 样板虚拟电路号的支持逻辑（mixin 间共享的常量/键名）。
 * mixin 类不允许非 private 静态字段，常量放这里。
 */
public final class Ae2CircuitSupport {

    /** 样板 NBT 里的虚拟电路号键（编码端写入，推送端读取） */
    public static final String CIRCUIT_KEY = "gtnecore_circuit";

    private Ae2CircuitSupport() {
    }
}
