package org.ringclouds.gtnecore.addon;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.common.data.GTElements;
import org.ringclouds.gtnecore.Gtnecore;

/**
 * GTCEu addon 入口（@GTAddon 注解扫描发现，AddonFinder 自动实例化）。
 *
 * GTNEcore 的框架定位：做 KJS 做不到的事 + 提供接口。
 * 注：GTM↔Mek 化学联动（化学品配方能力 + 输入/输出仓）已暂停——Mek 化学品需要
 * 专用管道交互才能把化学品送进仓，工作量与收益不匹配，代码已清除。恢复时：
 * RecipeCapability 需注册进 GTRegistries.RECIPE_CAPABILITIES（钩子调用时已 unfrozen，
 * 直接 register，勿再 unfreeze）；仓部件继承 TieredIOPartMachine（api 包）；
 * PartAbility 构造器 public 可直接 new。
 */
@GTAddon
public class GtnecoreAddon implements IGTAddon {

    @Override
    public com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate getRegistrate() {
        return Gtnecore.REGISTRATE;
    }

    @Override
    public String addonModId() {
        return Gtnecore.MODID;
    }

    @Override
    public void initializeAddon() {
        // 初始化钩子（GTCEu 各注册阶段开始前调用）
    }

    /**
     * 自定义元素（虚构神性元素，全部稳定不衰变）。
     *
     * 注册 key = 元素 name（GTElements.get(name) 查询）；符号仅显示用。
     * 注意：GTCEu 原版已有 Sp=Space（无材料/物品），Spacium 直接复用，不重复注册。
     * Element 无描述字段——描述等注册材料时走 Material.tooltip。
     */
    @Override
    public void registerElements() {
        // Godium：质子=中子=333333，质量 666666
        GTElements.createAndRegister(333333, 333333, -1, null, "Godium", "God", false);
        // Strium：纯弦构成的原子（无核子），质子=中子=0；真实弦质量 1.3e43 超出 long 范围，用 0 象征
        GTElements.createAndRegister(0, 0, -1, null, "Strium", "Str", false);
        // Tiemium：时间元素（质子=中子=0）
        GTElements.createAndRegister(0, 0, -1, null, "Timeium", "Time", false);
        // Infinite：质子 1729（哈代-拉马努金数，呼应无尽锭全元素统合），中子 0
        GTElements.createAndRegister(1729, 0, -1, null, "Infinite", "If", false);
        // Soulium：质子 0 + 中子 21 = 质量 21（双关灵魂重量 21 克）
        GTElements.createAndRegister(0, 21, -1, null, "Soulium", "Sl", false);
        // Unknown：问号元素（纯成分占位，无形态材料、无成分 → 电解/离心不产出）
        GTElements.createAndRegister(0, 0, -1, null, "Unknown", "?", false);
    }
}
