package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * 可回收小行星勘矿火箭发射台：
 * - 控制器自带物品槽（火箭 + 天体星图，配方道具不消耗）
 * - 配方形式采矿：火箭物品 + 天体星图（均不消耗）+ 液体（消耗） → 星图对应元素的矿物
 * - 运行时消耗 EU；能源仓任意等级（最多 4 个），B 槽可放流体输入仓/物品输出仓/solid_machine_casing
 * - 虚拟电路（编程电路配置器，fancy 面板滚轮/中键设置）
 */
public class RocketLaunchPadMachine extends WorkableElectricMultiblockMachine implements IHasCircuitSlot {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            RocketLaunchPadMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** 控制器物品槽：0=火箭、1=天体星图（配方输入，不消耗） */
    @Persisted
    private NotifiableItemStackHandler rocketSlots;

    /** 虚拟电路槽（编程电路） */
    @Persisted
    private NotifiableItemStackHandler circuitInventory;

    /**
     * 服务端每 tick 维护：火箭槽（槽 0）是否有火箭。
     * 渲染端（RocketLaunchPadRender）据此判定"待机停靠是否可见"——机内无火箭
     * （或未成型）时不渲染火箭。@DescSynced 随方块同步给客户端。
     */
    @Persisted
    @DescSynced
    private boolean rocketPresent;

    /**
     * 失重环境加成：配方时间 1/4、产出 ×2。
     * 注册为机器 recipeModifier（alwaysTryModify=true，每次匹配都检查失重状态）。
     */
    public static final RecipeModifier SPACE_BONUS_MODIFIER = (machine, recipe) -> {
        if (machine instanceof RocketLaunchPadMachine rlp && rlp.isInWeightless()) {
            return ModifierFunction.builder()
                    .durationModifier(ContentModifier.multiplier(0.25)) // 时间 1/4
                    .outputModifier(ContentModifier.multiplier(2.0))    // 产出 ×2
                    .build();
        }
        return ModifierFunction.IDENTITY;
    };

    /**
     * 失重状态判断：机器被绑定 WEIGHTLESS 环境即获得加成。
     * 与配方 .cleanroom() 条件同一管线——轨道维度由 OrbitEnvironmentHandler 自动绑定，
     * 重力正常化器覆盖范围会剥夺（stripWeightless）。
     */
    public boolean isInWeightless() {
        var cleanroom = getCleanroom();
        return cleanroom != null && cleanroom.getTypes().contains(GtneEnvironments.WEIGHTLESS);
    }

    public RocketLaunchPadMachine(IMachineBlockEntity holder) {
        super(holder);
        this.rocketSlots = new NotifiableItemStackHandler(this, 2, IO.IN);
        this.rocketSlots.setFilter(stack -> true);
        this.circuitInventory = new NotifiableItemStackHandler(this, 1, IO.IN, IO.NONE)
                .setFilter(IntCircuitBehaviour::isIntegratedCircuit);
    }

    /**
     * 每 tick 订阅：不能用 RecipeLogic tick 驱动（IDLE 会退订），机器级订阅永不退订。
     * 维护 rocketPresent（渲染停靠判定用；@DescSynced 随方块同步给客户端）。
     */
    @Override
    public void onLoad() {
        super.onLoad();
        var self = self();
        if (self.getLevel() != null && !self.getLevel().isClientSide) {
            self.subscribeServerTick(() -> {
                this.rocketPresent = this.rocketSlots != null
                        && !this.rocketSlots.getStackInSlot(0).isEmpty();
            });
        }
    }

    /** 槽 0 是否有火箭（客户端渲染停靠判定用，随 @DescSynced 同步） */
    public boolean isRocketPresent() {
        return rocketPresent;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** NONE 无朝向（超净间同款）：结构水平 4 向检测；禁扩展朝向（否则 getUpwardsFacing 读不存在的属性崩溃） */
    @Override
    public boolean allowExtendedFacing() {
        return false;
    }

    @Override
    public NotifiableItemStackHandler getCircuitInventory() {
        return circuitInventory;
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        super.attachConfigurators(configuratorPanel);
        configuratorPanel.attachConfigurators(new CircuitFancyConfigurator(circuitInventory.storage));
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup(4, 4, 182, 90)
                .setBackground(getScreenTexture())
                .addWidget(new com.lowdragmc.lowdraglib.gui.widget.LabelWidget(4, 5,
                        self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget(4, 17, this::addDisplayText)
                        .textSupplier(this.getLevel().isClientSide ? null : this::addDisplayText)
                        .setMaxWidthLimit(200)
                        .clickHandler(this::handleDisplayClick)));
        group.addWidget(new SlotWidget(rocketSlots.storage, 0, 8, 98).setBackground(GuiTextures.SLOT));
        group.addWidget(new SlotWidget(rocketSlots.storage, 1, 8 + 18, 98).setBackground(GuiTextures.SLOT));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }
}