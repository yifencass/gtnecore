package org.ringclouds.gtnecore.research;

import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ringclouds.gtnecore.Gtnecore;

/**
 * 服务端**归类流水线**：先登记"哪些配方归哪个研究"，再让注入器挂条件。
 *
 * <h2>为什么合成一个类</h2>
 * 归类（本类）和注入（{@link ResearchRecipeInjector}）都必须在
 * 「配方已加载」之后跑，而且**归类必须先行**。
 * 如果两个类各自监听同一个事件，执行顺序是**不确定的**——一旦注入先跑，
 * 那一轮就锁不住任何配方（要等下次重启才生效）。所以这里把顺序写死：
 *
 * <pre>
 * ① ResearchRecipeInjector.resetInjectedFlag()   // 清"已处理"标记
 * ② assign()                                     // 登记配方 → 研究
 * ③ ResearchRecipeInjector.inject()              // 扫配方挂条件
 * </pre>
 *
 * <h2>改清单只动这一个文件</h2>
 * 下面 {@link #assign()} 就是"哪些配方归哪个研究"的全部内容。
 * 将来换成数据包 / KubeJS 驱动时，删掉本类、让它们调同样的 API 即可。
 *
 * <h2>当前内容是示例</h2>
 * 会真的锁住下面这些配方类型，用来验证链路；清单定下来后替换。
 */
@Mod.EventBusSubscriber(modid = Gtnecore.MODID)
public final class GtneResearchRecipeAssignments {

    private static final Logger LOGGER = LoggerFactory.getLogger("GTNEcore[研究归类]");

    private static MinecraftServer lastAssignedForServer = null;

    private GtneResearchRecipeAssignments() {
    }

    // ==================== 生命周期 ====================

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || lastAssignedForServer == server) {
            return;
        }
        lastAssignedForServer = server;
        ResearchRecipeInjector.resetInjectedFlag();
        assign();
        ResearchRecipeInjector.inject();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // 只认服务端数据加载那次（/reload 后配方会重建，需要重新登记 + 注入）
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        lastAssignedForServer = server;
        ResearchRecipeInjector.resetInjectedFlag();
        // /reload 后配方是新对象，旧的成员记录指向已失效的 id，先清再登记
        GtneResearch.all().forEach(GtneResearch.ResearchCategory::clear);
        assign();
        ResearchRecipeInjector.inject();
    }

    @SubscribeEvent
    public static void onReloadStart(AddReloadListenerEvent event) {
        lastAssignedForServer = null;
    }

    // ==================== 归类清单（改这里） ====================

    /**
     * 把配方类型登记进研究条目。
     *
     * <p>读法就是目标模型：
     * <pre>
     * 化工研究 ← 化学反应釜 + 大型化学反应釜
     * 裂化研究 ← 裂化机
     * 基础研究 ← 离心机 + 搅拌机
     * </pre>
     *
     * <p>只想锁某类配方的一部分时用过滤器：
     * {@code addAll(GTRecipeTypes.CHEMICAL_RECIPES, r -> r.id.getPath().contains("sulfuric"))}
     */
    public static void assign() {
        GtneResearch.ResearchCategory chemical = GtneResearch.of("chemical");
        if (chemical != null) {
            chemical.addAll(GTRecipeTypes.CHEMICAL_RECIPES);
            chemical.addAll(GTRecipeTypes.LARGE_CHEMICAL_RECIPES);
        }

        GtneResearch.ResearchCategory cracking = GtneResearch.of("cracking");
        if (cracking != null) {
            cracking.addAll(GTRecipeTypes.CRACKING_RECIPES);
        }

        GtneResearch.ResearchCategory basic = GtneResearch.of("basic");
        if (basic != null) {
            basic.addAll(GTRecipeTypes.CENTRIFUGE_RECIPES);
            basic.addAll(GTRecipeTypes.MIXER_RECIPES);
        }

        LOGGER.info("GTNEcore[研究归类]: 登记完成 —— 化工 {} 条 / 裂化 {} 条 / 基础 {} 条",
                chemical == null ? -1 : chemical.membershipCount(),
                cracking == null ? -1 : cracking.membershipCount(),
                basic == null ? -1 : basic.membershipCount());
    }
}
