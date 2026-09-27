package org.ringclouds.gtnecore.planner.client;

/**
 * 节点类型注册（右键弹列表选择要添加的类型）。
 * 目前只有"配方节点"一种，后续新增类型在这里扩展即可
 * （如"发电机节点""汇总节点"）。
 */
public enum PlannerNodeType {
    RECIPE("配方节点");

    public final String displayName;

    PlannerNodeType(String displayName) {
        this.displayName = displayName;
    }
}
