package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.FancySelectorConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import org.ringclouds.gtnecore.voltages.GtneVoltages;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import org.ringclouds.gtnecore.config.GTNEcoreConfig;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * GTM-style energy cube: stores EU, accepts EU/FE input and outputs EU/FE.
 * FE conversion ratio (N FE = 1 EU) is configurable (config fePerEu, default 4),
 * always keeping the 80% efficiency layer:
 *   FE -> EU: eu = fe * 4 / (5N)   (e.g. N=4: 4 FE -> 0.8 EU)
 *   EU -> FE: fe = eu * 4N / 5     (e.g. N=4: 1 EU -> 3.2 FE)
 * EU flows are lossless. Output EU voltage is configurable, downward-compatible only.
 * Each side can be set to NONE / EU_IN / EU_OUT / FE_IN / FE_OUT.
 */
public class EnergyCubeMachine extends TieredEnergyMachine implements IFancyUIMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(EnergyCubeMachine.class, TieredEnergyMachine.MANAGED_FIELD_HOLDER);

    // FE <-> EU conversion, ratio N = config fePerEu (default 4 FE = 1 EU), 80% efficiency:
    //   fe -> eu: eu = fe * 4 / (5N);  consumed FE per EU = ceil(5N / 4)
    //   eu -> fe: fe = eu * 4N / 5;    EU cost per FE = ceil(fe * 5 / (4N))
    private static final long EU_TO_FE_DIVISOR = 5;
    private static long feToEuDivisor() {
        return 5L * GTNEcoreConfig.FE_PER_EU.get();
    }
    private static long euToFeMultiplier() {
        return 4L * GTNEcoreConfig.FE_PER_EU.get();
    }

    /** Per-side I/O mode. */
    public enum SideMode {
        NONE, EU_IN, EU_OUT, FE_IN, FE_OUT
    }

    /** Front-face overlay mode for the machine model (energy_io render state property). */
    public enum EnergyIOMode implements StringRepresentable {
        NONE, IN, OUT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Render-state properties driving the per-face energy I/O overlays in the machine model. */
    public static final EnumProperty<EnergyIOMode> ENERGY_IO_DOWN = EnumProperty.create("energy_io_down", EnergyIOMode.class);
    public static final EnumProperty<EnergyIOMode> ENERGY_IO_UP = EnumProperty.create("energy_io_up", EnergyIOMode.class);
    public static final EnumProperty<EnergyIOMode> ENERGY_IO_NORTH = EnumProperty.create("energy_io_north", EnergyIOMode.class);
    public static final EnumProperty<EnergyIOMode> ENERGY_IO_SOUTH = EnumProperty.create("energy_io_south", EnergyIOMode.class);
    public static final EnumProperty<EnergyIOMode> ENERGY_IO_WEST = EnumProperty.create("energy_io_west", EnergyIOMode.class);
    public static final EnumProperty<EnergyIOMode> ENERGY_IO_EAST = EnumProperty.create("energy_io_east", EnergyIOMode.class);

    public static EnumProperty<EnergyIOMode> ioProperty(Direction side) {
        return switch (side) {
            case DOWN -> ENERGY_IO_DOWN;
            case UP -> ENERGY_IO_UP;
            case NORTH -> ENERGY_IO_NORTH;
            case SOUTH -> ENERGY_IO_SOUTH;
            case WEST -> ENERGY_IO_WEST;
            case EAST -> ENERGY_IO_EAST;
        };
    }

    /** Output EU voltage selection (downward-compatible tiers only). GTNEcore 22-tier re-ladder (0=ULV .. 21=MAX). */
    public enum OutputVoltage implements EnumSelectorWidget.SelectableEnum {
        ULV(0), LV(1), MV(2), HV(3), EV(4), IV(5), LUV(6), ZPM(7), UV(8), UHV(9), UEV(10), UIV(11),
        UMV(12), SWV(13), GCV(14), UXV(15), QGV(16), CYV(17), OPV(18), VEV(19), SGV(20), MAX(21);

        public final int tier;

        OutputVoltage(int tier) {
            this.tier = tier;
        }

        @Override
        public String getTooltip() {
            return GTValues.VNF[tier];
        }

        @Override
        public IGuiTexture getIcon() {
            return GuiTextures.BUTTON_ENERGY;
        }

        public static OutputVoltage of(int tier) {
            return values()[tier];
        }
    }

    @Persisted private int outputTier;
    @Persisted private SideMode sideDown = SideMode.NONE;
    @Persisted private SideMode sideUp = SideMode.NONE;
    @Persisted private SideMode sideNorth = SideMode.NONE;
    @Persisted private SideMode sideSouth = SideMode.NONE;
    @Persisted private SideMode sideWest = SideMode.NONE;
    @Persisted private SideMode sideEast = SideMode.NONE;

    protected final FEContainer feContainer;

    public EnergyCubeMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier);
        this.outputTier = tier;
        this.feContainer = new FEContainer(this);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ---------------- 侧面模式 ----------------

    public SideMode getSideMode(@Nullable Direction side) {
        if (side == null) {
            return SideMode.NONE; // 无方向查询（如 Jade/能力查询）不做限制
        }
        return switch (side) {
            case DOWN -> sideDown;
            case UP -> sideUp;
            case NORTH -> sideNorth;
            case SOUTH -> sideSouth;
            case WEST -> sideWest;
            case EAST -> sideEast;
        };
    }

    public void setSideMode(Direction side, SideMode mode) {
        switch (side) {
            case DOWN -> sideDown = mode;
            case UP -> sideUp = mode;
            case NORTH -> sideNorth = mode;
            case SOUTH -> sideSouth = mode;
            case WEST -> sideWest = mode;
            case EAST -> sideEast = mode;
        }
        onChanged();
        updateRenderStates();
    }

    /** Overlay mode derived from a side's I/O mode. */
    private static EnergyIOMode ioModeOf(SideMode mode) {
        return switch (mode) {
            case EU_IN, FE_IN -> EnergyIOMode.IN;
            case EU_OUT, FE_OUT -> EnergyIOMode.OUT;
            default -> EnergyIOMode.NONE;
        };
    }

    /** Push all six faces' overlay modes into the machine render state (drives the model multipart). */
    private void updateRenderStates() {
        MachineRenderState state = getRenderState();
        if (state == null) {
            return;
        }
        MachineRenderState next = state;
        for (Direction side : Direction.values()) {
            next = next.setValue(ioProperty(side), ioModeOf(getSideMode(side)));
        }
        setRenderState(next);
    }

    public int getOutputTier() {
        return outputTier;
    }

    public void setOutputTier(int tier) {
        this.outputTier = Math.max(0, Math.min(getTier(), tier));
        onChanged();
    }

    // ---------------- 能量容器（EU） ----------------

    @Override
    protected NotifiableEnergyContainer createEnergyContainer(Object... args) {
        long voltage = GTValues.V[getTier()];
        return new EnergyCubeEnergyContainer(this, voltage * 128, voltage, 1, voltage, 1);
    }

    protected class EnergyCubeEnergyContainer extends NotifiableEnergyContainer {

        public EnergyCubeEnergyContainer(MetaMachine machine, long capacity, long inputVoltage,
                                         long inputAmperage, long outputVoltage, long outputAmperage) {
            super(machine, capacity, inputVoltage, inputAmperage, outputVoltage, outputAmperage);
            setSideInputCondition(dir -> dir == null || getSideMode(dir) == SideMode.EU_IN);
            setSideOutputCondition(dir -> dir == null || getSideMode(dir) == SideMode.EU_OUT);
        }

        @Override
        public long getInputVoltage() {
            return GTValues.V[getTier()];
        }

        @Override
        public long getOutputVoltage() {
            return GTValues.V[outputTier];
        }

        @Override
        public void serverTick() {
            super.serverTick(); // EU 输出（向 EU_OUT 面邻居推电）
            pushFeToNeighbors(); // FE 输出（向 FE_OUT 面邻居推 FE）
        }

        /** Push stored EU as FE to neighbors on FE_OUT sides, rate-limited by output tier. */
        private void pushFeToNeighbors() {
            long rateFe = GTValues.V[outputTier] * euToFeMultiplier() / EU_TO_FE_DIVISOR;
            if (rateFe <= 0 || getEnergyStored() <= 0) {
                return;
            }
            for (Direction side : Direction.values()) {
                if (getSideMode(side) != SideMode.FE_OUT) {
                    continue;
                }
                BlockEntity neighbor = getLevel().getBlockEntity(getPos().relative(side));
                if (neighbor == null) {
                    continue;
                }
                IEnergyStorage target = GTCapabilityHelper.getForgeEnergy(getLevel(), getPos().relative(side), side.getOpposite());
                if (target == null || !target.canReceive()) {
                    continue;
                }
                long maxFe = Math.min(rateFe, getEnergyStored() * euToFeMultiplier() / EU_TO_FE_DIVISOR);
                if (maxFe <= 0) {
                    continue;
                }
                int accepted = target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, maxFe), false);
                if (accepted > 0) {
                    long euCost = (accepted * EU_TO_FE_DIVISOR + euToFeMultiplier() - 1) / euToFeMultiplier();
                    changeEnergy(-euCost);
                }
                if (getEnergyStored() <= 0) {
                    break;
                }
            }
        }
    }

    // ---------------- FE 桥（IEnergyStorage，80% 效率） ----------------

    protected class FEContainer extends MachineTrait implements IEnergyStorage {

        public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(FEContainer.class);

        public FEContainer(MetaMachine machine) {
            super(machine);
            capabilityValidator = dir -> dir == null
                    || getSideMode(dir) == SideMode.FE_IN
                    || getSideMode(dir) == SideMode.FE_OUT;
        }

        @Override
        public ManagedFieldHolder getFieldHolder() {
            return MANAGED_FIELD_HOLDER;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) {
                return 0;
            }
            long eu = maxReceive * 4 / feToEuDivisor(); // 80% 效率：N FE -> 0.8 EU
            if (eu <= 0) {
                return 0;
            }
            long accepted = Math.min(eu, energyContainer.getEnergyCanBeInserted());
            if (accepted > 0 && !simulate) {
                energyContainer.changeEnergy(accepted);
            }
            // 实际消耗的 FE（每 EU 向上取整 ceil(5N/4)）
            long consumed = (accepted * feToEuDivisor() + 3) / 4;
            return (int) Math.min(Integer.MAX_VALUE, consumed);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) {
                return 0;
            }
            long stored = energyContainer.getEnergyStored();
            long fe = Math.min(maxExtract, stored * euToFeMultiplier() / EU_TO_FE_DIVISOR); // 1 EU -> 4N/5 FE
            long euCost = (fe * EU_TO_FE_DIVISOR + euToFeMultiplier() - 1) / euToFeMultiplier(); // 向上取整防超扣
            if (euCost > stored) {
                fe = 0;
                euCost = 0;
            }
            if (euCost > 0 && !simulate) {
                energyContainer.changeEnergy(-euCost);
            }
            return (int) Math.min(Integer.MAX_VALUE, fe);
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energyContainer.getEnergyStored() * euToFeMultiplier() / EU_TO_FE_DIVISOR);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, energyContainer.getEnergyCapacity() * euToFeMultiplier() / EU_TO_FE_DIVISOR);
        }
    }

    // ---------------- UI ----------------

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 176, 166);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        createEnergyBar().setupUI(group, this);

        // 当前输出电压（实时刷新，可在左下角配置面板里调整）
        group.addWidget(new LabelWidget(38, 8, () -> Component.translatable(
                "gtnecore.gui.energy_cube.output", GtneVoltages.VNF[outputTier], GTValues.V[outputTier]).getString()));

        // 六面配置（水平居中）：每行 = 模式图标按钮（点击循环）+ 模式名文字（实时刷新）
        int row = 0;
        for (Direction side : Direction.values()) {
            int y = 23 + row * 20;
            row++;
            final Direction s = side;
            ButtonWidget[] btnRef = new ButtonWidget[1];
            btnRef[0] = new ButtonWidget(38, y, 18, 18, modeTexture(getSideMode(s)), cd -> {
                cycleSideMode(s);
                btnRef[0].setButtonTexture(modeTexture(getSideMode(s)));
            });
            btnRef[0].setHoverTooltips(Component.translatable(
                    "gtnecore.gui.energy_cube.cycle", side.getName()).getString());
            group.addWidget(btnRef[0]);
            group.addWidget(new LabelWidget(62, y + 5, () -> Component.translatable(
                    "gtnecore.gui.energy_cube.side", side.getName(),
                    Component.translatable(modeKey(getSideMode(s)))).getString()));
        }
        return group;
    }

    /** 正向循环面模式（NONE -> EU_IN -> EU_OUT -> FE_IN -> FE_OUT -> NONE）。 */
    public void cycleSideMode(Direction side) {
        setSideMode(side, SideMode.values()[(getSideMode(side).ordinal() + 1) % SideMode.values().length]);
    }

    /** 逆向循环面模式。 */
    public void cycleSideModeBackward(Direction side) {
        setSideMode(side, SideMode.values()[(getSideMode(side).ordinal() + SideMode.values().length - 1) % SideMode.values().length]);
    }

    static IGuiTexture modeTexture(SideMode mode) {
        return switch (mode) {
            case NONE -> GuiTextures.BUTTON;
            case EU_IN -> GuiTextures.TOOL_ALLOW_INPUT;
            case EU_OUT -> GuiTextures.TOOL_AUTO_OUTPUT;
            case FE_IN -> GuiTextures.BUTTON_ENERGY;
            case FE_OUT -> GuiTextures.BUTTON_POWER;
        };
    }

    static String modeKey(SideMode mode) {
        return switch (mode) {
            case NONE -> "gtnecore.gui.energy_cube.mode.none";
            case EU_IN -> "gtnecore.gui.energy_cube.mode.eu_in";
            case EU_OUT -> "gtnecore.gui.energy_cube.mode.eu_out";
            case FE_IN -> "gtnecore.gui.energy_cube.mode.fe_in";
            case FE_OUT -> "gtnecore.gui.energy_cube.mode.fe_out";
        };
    }

    @Override
    public IGuiTexture getTabIcon() {
        return GuiTextures.BUTTON_ENERGY;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.gtnecore." + GtneVoltages.VN[getTier()].toLowerCase() + "_energy_cube");
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        // 输出电压选择（仅向下兼容：ULV .. 当前档）
        OutputVoltage[] options = Arrays.stream(OutputVoltage.values())
                .filter(v -> v.tier <= getTier())
                .toArray(OutputVoltage[]::new);
        FancySelectorConfigurator<OutputVoltage> selector =
                new FancySelectorConfigurator<>(options, OutputVoltage.of(outputTier), v -> setOutputTier(v.tier));
        selector.setTooltip(v -> java.util.List.of(
                Component.literal(GtneVoltages.VNF[v.tier] + " - " + GTValues.V[v.tier] + " EU/t")));
        panel.attachConfigurators(selector);
    }
}
