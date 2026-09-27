package org.ringclouds.gtnecore.integration.pneumaticcraft.heat;

import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.PneumaticRegistry;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ringclouds.gtnecore.integration.pneumaticcraft.PncCompat;

/**
 * GTNEcore 机器与 PneumaticCraft 热系统的桥接器（方向 B）。
 *
 * <h2>用途</h2>
 * 把一个 {@link IHeatExchangerLogic} 挂到自有方块实体上，并通过 Forge Capability
 * {@link PNCCapabilities#HEAT_EXCHANGER_CAPABILITY} 暴露给 PnC 的热系统
 * （热管道、热力气动设备等都能与之交换热量）。
 *
 * <h2>与方向 A（气压）的对比</h2>
 * 热系统走**真正的 Forge Capability**，不需要注册工厂、不需要自己 tick 平衡，
 * 因此是三条线里最省事的一条 —— 这也是先做它的原因：用来验证整条适配层通路。
 *
 * <h2>生命周期（务必遵守）</h2>
 * <pre>
 * // 构造时
 * this.heatBridge = new PncHeatBridge(this::getLevel, this::getBlockPos, TARGET_TEMPERATURE);
 *
 * // 每 tick（服务端）—— 可选，PnC 内部也会推进温度
 * if (PncCompat.isAvailable()) heatBridge.tick();
 *
 * // getCapability 里
 * LazyOptional&lt;IHeatExchangerLogic&gt; heat = heatBridge.getHeatCapability(cap, side);
 * if (heat != null) return heat;
 *
 * // 存档
 * tag.put("pnc_heat", heatBridge.serializeNBT());
 * heatBridge.deserializeNBT(tag.getCompound("pnc_heat"));
 *
 * // 移除时（防止 LazyOptional 泄漏）
 * heatBridge.invalidate();
 * </pre>
 *
 * <h2>许可</h2>
 * 本类引用的全部是 {@code me.desht.pneumaticcraft.api.*}（LGPLv3）。
 * 切勿在此引入 {@code me.desht.pneumaticcraft.common.*}（GPLv3）。
 */
public class PncHeatBridge {

    /** PnC 的热交换逻辑实例。由 PnC 的注册表创建，保证与其内部实现（含环境温）一致。 */
    private final IHeatExchangerLogic heatLogic;

    /** Capability 句柄。方块实体移除时必须 invalidate，否则会留下悬空引用。 */
    private final LazyOptional<IHeatExchangerLogic> heatCap;

    /**
     * @param ambientTemperature 目标环境温度（K）。
     *                           PnC 的热平衡会以环境温为基准，因此这里应传机器所在维度的
     *                           期望温度，而不是 0。
     */
    public PncHeatBridge(double ambientTemperature) {
        this.heatLogic = PneumaticRegistry.getInstance().getHeatRegistry().makeHeatExchangerLogic();
        this.heatLogic.setTemperature(ambientTemperature);
        // 热阻/热容留给调用方按机器规格覆盖（见 setThermalResistance / setThermalCapacity）
        this.heatCap = LazyOptional.of(() -> this.heatLogic);
    }

    /**
     * 若 {@code cap} 就是 PnC 的热 Capability，返回可用的句柄；否则返回 {@code null}
     * 让调用方继续往下走自己的 capability 链。
     *
     * <p>
     * 返回 {@code null} 而不是 {@link LazyOptional#empty()}，是为了让调用点写成
     * {@code LazyOptional<T> r = bridge.getHeatCapability(cap, side); if (r != null) return r;}
     * —— 避免泛型强制转换造成的未检查警告。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> LazyOptional<T> getHeatCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PNCCapabilities.HEAT_EXCHANGER_CAPABILITY) {
            return heatCap.cast();
        }
        return null;
    }

    /**
     * 服务端每 tick 调用，推进 PnC 内部的热平衡。
     *
     * <p>
     * PnC 的 {@code tick()} 会依据热阻/热容与四周已连接的热交换器交换热量。若不调用，
     * 温度只在被显式修改时变化。
     */
    public void tick() {
        heatLogic.tick();
    }

    /** 用其他热交换器建立连接（例如机器自带的换热面）。 */
    public void connect(@NotNull IHeatExchangerLogic other) {
        heatLogic.addConnectedExchanger(other);
    }

    public void disconnect(@NotNull IHeatExchangerLogic other) {
        heatLogic.removeConnectedExchanger(other);
    }

    /** 当前温度（K）。 */
    public double getTemperature() {
        return heatLogic.getTemperature();
    }

    /**
     * 直接注入/抽取热量（J）。
     *
     * <p>
     * 正值为加热。这是 GTNEcore 侧"用电产热/用热发电"等玩法的接入点：
     * 把 EU 换算成 J 后调用本方法，而不必依赖外部热源。
     */
    public void addHeat(double joules) {
        heatLogic.addHeat(joules);
    }

    public void setThermalResistance(double thermalResistance) {
        heatLogic.setThermalResistance(thermalResistance);
    }

    public void setThermalCapacity(double capacity) {
        heatLogic.setThermalCapacity(capacity);
    }

    public CompoundTag serializeNBT() {
        return heatLogic.serializeNBT();
    }

    public void deserializeNBT(@NotNull CompoundTag tag) {
        heatLogic.deserializeNBT(tag);
    }

    /**
     * 方块实体移除时调用。
     *
     * <p>
     * <b>必须调用</b> —— LazyOptional 不 invalidate 会持有方块实体引用，造成内存泄漏，
     * 且 PnC 可能继续向已失效的机器推热。
     */
    public void invalidate() {
        heatCap.invalidate();
    }

    /** 便捷判定：只有 PnC 在场才可以构造本类（构造会触碰 PnC 类型）。 */
    public static boolean isUsable() {
        return PncCompat.isAvailable();
    }
}
