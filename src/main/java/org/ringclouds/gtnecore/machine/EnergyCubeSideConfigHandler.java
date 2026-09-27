package org.ringclouds.gtnecore.machine;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.widget.directional.IDirectionalConfigHandler;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.BlockPosFace;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * GTM 风格的可视化方向配置处理器（挂在机器 UI 的"方向设置"页）。
 * <p>
 * 左侧是场景里的真实机器方块：点击某个面即选中（左下角出现模式按钮），
 * 再次点击该面（左键正向 / 右键逆向）循环切换 NONE/EU_IN/EU_OUT/FE_IN/FE_OUT；
 * 已配置的面会渲染彩色边框（绿=输入，红=输出）。
 */
public class EnergyCubeSideConfigHandler implements IDirectionalConfigHandler {

    private static final int COLOR_IN = 0xff00c853;  // 绿：输入
    private static final int COLOR_OUT = 0xfff44336; // 红：输出

    private final EnergyCubeMachine machine;
    private Direction side; // 当前选中的面

    public EnergyCubeSideConfigHandler(EnergyCubeMachine machine) {
        this.machine = machine;
    }

    @Override
    public Widget getSideSelectorWidget(SceneWidget scene, FancyMachineUIWidget machineUI) {
        WidgetGroup group = new WidgetGroup(0, 0, 18, 18);
        group.addWidget(new ButtonWidget(0, 0, 18, 18, cd -> {
            if (side != null) {
                machine.cycleSideMode(side);
            }
        }) {
            // 每次刷新根据选中面的模式换图标
            @Override
            public void updateScreen() {
                super.updateScreen();
                if (side == null) {
                    setButtonTexture(GuiTextures.BUTTON);
                    setHoverTooltips(Component.translatable("gtnecore.gui.energy_cube.side_config.unselected").getString());
                } else {
                    EnergyCubeMachine.SideMode mode = machine.getSideMode(side);
                    setButtonTexture(EnergyCubeMachine.modeTexture(mode));
                    setHoverTooltips(Component.translatable("gtnecore.gui.energy_cube.side", side.getName(),
                            Component.translatable(EnergyCubeMachine.modeKey(mode))).getString());
                }
            }
        });
        return group;
    }

    @Override
    public void onSideSelected(BlockPos pos, Direction side) {
        this.side = side;
    }

    @Override
    public ScreenSide getScreenSide() {
        return ScreenSide.LEFT;
    }

    /** 点击场景方块的面：左键正向循环模式，右键逆向循环。 */
    @Override
    public void handleClick(ClickData cd, Direction direction) {
        if (cd.button == 0) {
            machine.cycleSideMode(direction);
        } else if (cd.button == 1) {
            machine.cycleSideModeBackward(direction);
        }
    }

    /** 在场景方块上渲染模式边框：输入面绿色、输出面红色。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderOverlay(SceneWidget sceneWidget, BlockPosFace blockPosFace) {
        EnergyCubeMachine.SideMode mode = machine.getSideMode(blockPosFace.facing);
        if (mode == EnergyCubeMachine.SideMode.NONE) {
            return;
        }
        boolean input = mode == EnergyCubeMachine.SideMode.EU_IN || mode == EnergyCubeMachine.SideMode.FE_IN;
        sceneWidget.drawFacingBorder(new PoseStack(), blockPosFace, input ? COLOR_IN : COLOR_OUT, 1);
    }

    /** 左上角实时显示选中面的模式。 */
    @Override
    public void addAdditionalUIElements(WidgetGroup parent) {
        parent.addWidget(new LabelWidget(4, 4, () -> side == null
                ? Component.translatable("gtnecore.gui.energy_cube.side_config.select_hint").getString()
                : Component.translatable("gtnecore.gui.energy_cube.side", side.getName(),
                        Component.translatable(EnergyCubeMachine.modeKey(machine.getSideMode(side)))).getString()));
    }
}