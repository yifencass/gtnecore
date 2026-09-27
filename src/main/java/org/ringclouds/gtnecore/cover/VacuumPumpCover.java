package org.ringclouds.gtnecore.cover;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.ringclouds.gtnecore.machine.GtneEnvironments;

import java.util.Set;

/**
 * 真空泵覆盖板：贴在任意 GT 配方机器上，为该机器提供<b>真空</b>环境。
 *
 * <p>
 * 与超净间/轨道环境同一条管线（{@code ICleanroomProvider} / {@code ICleanroomReceiver}），
 * 所以配方侧的 {@code .cleanroom(CleanroomType)} 直接可用。
 *
 * <p>
 * 设计取舍：
 * <ul>
 * <li><b>无运行消耗</b>——不订阅能量、不 tick，只在装上/拆下时绑定与解绑；
 * 门槛全部落在合成材料上（HV 级配方）。</li>
 * <li>绑定走"直接引用"：拿宿主机器的 {@link ICleanroomReceiver} 塞入本覆盖板的 provider。
 * 拆卸时用<b>身份比较</b>守卫，避免误清超净间/轨道环境的绑定
 * （与 {@code OmniMaintenanceHatchPartMachine} 同款做法）。</li>
 * <li>机器上 provider 字段是单值：若机器已在超净间内（或处于月球/轨道天然环境），
 * <b>后绑定者生效</b>——本覆盖板会顶掉原有环境，拆下后不自动恢复。</li>
 * </ul>
 */
public class VacuumPumpCover extends CoverBehavior {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(VacuumPumpCover.class);

    /** 真空提供者（恒生效、无消耗） */
    private static final ICleanroomProvider VACUUM_PROVIDER = new ICleanroomProvider() {

        @Override
        public Set<CleanroomType> getTypes() {
            return Set.of(GtneEnvironments.VACUUM);
        }

        @Override
        public boolean isClean() {
            return true;
        }
    };

    public VacuumPumpCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    /**
     * ⚠️ **不要**在这里加"宿主必须是配方机器"的限制（2026-09-24 踩坑）。
     *
     * <p>
     * {@code ICoverable.placeCoverOnSide()} 会先调 {@code coverBehavior.canAttach()}，
     * 返回 false 就<b>整次放置失败</b>（不消耗物品、毫无反馈）。而这里依赖的
     * {@code GTCapabilityHelper.getCleanroomReceiver(...)} 是"放置那一刻"的 capability 解析，
     * 一旦取不到（方块不是配方机器，或 capability 尚未就绪），表现就是**覆盖板根本装不上去**。
     *
     * <p>
     * 现在的策略：放置不做额外限制（朝向/正面交给 {@code super.canAttach()}），
     * 装错地方只是不生效，由 {@link #onLoad()} 打告警说明原因。
     */
    @Override
    public void onLoad() {
        super.onLoad();
        var receiver = findReceiver();
        if (receiver == null) {
            LOGGER.warn("真空泵覆盖板: 装在了非配方机器上（{}）—— 不会提供真空环境", coverHolder.getPos());
            return;
        }
        receiver.setCleanroom(VACUUM_PROVIDER);
        LOGGER.info("真空泵覆盖板: 已为 {} 提供真空环境", coverHolder.getPos());
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        var receiver = findReceiver();
        // 身份比较：只清自己绑的那个，别动超净间/轨道环境
        if (receiver != null && receiver.getCleanroom() == VACUUM_PROVIDER) {
            receiver.setCleanroom(null);
        }
    }

    /**
     * 宿主机器（覆盖板所在方块）的环境接收者；客户端或非配方机器返回 null。
     *
     * <p>
     * 侧向取两次：先按覆盖板所在面取，取不到再试 {@code null}。GTCEu 的
     * {@code MetaMachineBlockEntity.getCapability} 对 CLEANROOM_RECEIVER 分支未按 side 过滤，
     * 两种情况都能拿到；但不同版本/其它实现可能只认其中一种，这里做兼容以免静默失效。
     */
    @Nullable
    private ICleanroomReceiver findReceiver() {
        if (coverHolder.isRemote()) return null;
        var bySide = GTCapabilityHelper.getCleanroomReceiver(
                coverHolder.getLevel(), coverHolder.getPos(), attachedSide);
        if (bySide != null) return bySide;
        return GTCapabilityHelper.getCleanroomReceiver(coverHolder.getLevel(), coverHolder.getPos(), null);
    }
}
