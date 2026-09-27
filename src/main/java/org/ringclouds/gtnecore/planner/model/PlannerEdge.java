package org.ringclouds.gtnecore.planner.model;

/**
 * 一条连接：上游节点输出口 → 下游节点输入口（端口按下标引用各自节点的
 * recipe.outputs()/recipe.inputs()）。语义：下游该输入的物料由上游该输出供给，
 * 剩余差额才是外部需求。
 */
public record PlannerEdge(int fromNode, int fromOutput, int toNode, int toInput) {
}
