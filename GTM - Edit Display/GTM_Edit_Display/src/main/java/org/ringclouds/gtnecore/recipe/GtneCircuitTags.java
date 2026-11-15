package org.ringclouds.gtnecore.recipe;

import com.gregtechceu.gtceu.data.recipe.GTCraftingComponents;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

/**
 * GTNEcore 把电压重排为 22 档后，GTCEu 的电路组件（GTCraftingComponents.CIRCUIT /
 * BETTER_CIRCUIT）仍只绑定了索引 0..14（原版 ulv..max tag）。重排后 15..21 档
 * （UXV/QGV/CYV/OpV/VEV/SGV/MAX）槽位为 null，CraftingComponent.get(tier) 对空槽
 * 返回 fallback(null)，导致电路配方止步于 GCV（=原版 MAX 槽位）。
 *
 * 这里把 15..21 档绑定到自定义 tag gtnecore:circuits/{tier}：以后整合包往这些 tag
 * 塞对应档位的电路物品（注册物品或复用现有物品均可），机器配方即自动识别，
 * 无需改动 GTCEu 本体。10..14 档（UEV..GCV）沿用原版 tag（uev/uiv/uxv/opv/max），不动。
 *
 * 绑定时机：AddPackFindersEvent 是 IModBusEvent（只在 mod 总线分发），注册到
 * FMLJavaModLoadingContext.get().getModEventBus() 的 EventPriority.LOWEST —— 保证在
 * GTCEu 的 NORMAL 优先级监听（内部调用 GTCraftingComponents.init() 重建组件）之后
 * 执行，且之后没有其他重建点，绑定永久生效。
 */
public final class GtneCircuitTags {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 首个重新绑定档位（12=UMV）。12..14 档被重排插入 UMV/SWV/GCV，但 GTCEu 的
     * 电路组件仍按原版索引绑定 uxv/opv/max tag（语义错位），这里改绑到与新档名
     * 一致的 gtnecore tag。 */
    public static final int RENAMED_FIRST_TIER = 12;

    /** 12..14 档（UMV/SWV/GCV）电路 tag：gtnecore:circuits/{umv,swv,gcv} */
    public static final TagKey<Item>[] RENAMED_TIER_TAGS = createTags("umv", "swv", "gcv");

    /** 首个扩展绑定档位（15=UXV）。GTCEu 只绑到原版 MAX(14)，15..21 无绑定。 */
    public static final int FIRST_TIER = 15;

    /** 15..21 档电路 tag：gtnecore:circuits/{uxv,qgv,cyv,opv,vev,sgv,max} */
    public static final TagKey<Item>[] TIER_TAGS = createTags("uxv", "qgv", "cyv", "opv", "vev", "sgv", "max");

    private GtneCircuitTags() {
    }

    private static TagKey<Item>[] createTags(String... paths) {
        TagKey<Item>[] tags = new TagKey[paths.length];
        for (int i = 0; i < paths.length; i++) {
            tags[i] = TagKey.create(Registries.ITEM, new ResourceLocation("gtnecore", "circuits/" + paths[i]));
        }
        return tags;
    }

    /** 给电路组件的 12..21 档绑定自定义 tag。 */
    public static void install() {
        // 防御：CIRCUIT 字段在 GTCraftingComponents.init() 里赋值，类加载时是 null。
        // mods.toml 已声明 gtceu 依赖保证 GTCEu 先收到 AddPackFindersEvent（init()
        // 已执行），这里兜底避免极端顺序下 NPE（此时 init() 幂等重建后绑定仍生效；
        // 若 GTCEu 之后才 init() 会重建组件覆盖本次绑定，但依赖声明已排除该顺序）。
        if (GTCraftingComponents.CIRCUIT == null) {
            GTCraftingComponents.init();
        }
        // 12..14 档改绑到与新档名一致的 tag（覆盖原版 uxv/opv/max 错位绑定）
        for (int i = 0; i < RENAMED_TIER_TAGS.length; i++) {
            int tier = RENAMED_FIRST_TIER + i;
            GTCraftingComponents.CIRCUIT.add(tier, RENAMED_TIER_TAGS[i]);
            GTCraftingComponents.BETTER_CIRCUIT.add(tier, RENAMED_TIER_TAGS[i]);
        }
        // 15..21 档扩展绑定
        for (int i = 0; i < TIER_TAGS.length; i++) {
            int tier = FIRST_TIER + i;
            GTCraftingComponents.CIRCUIT.add(tier, TIER_TAGS[i]);
            GTCraftingComponents.BETTER_CIRCUIT.add(tier, TIER_TAGS[i]);
        }
        LOGGER.info("GTNEcore: 电路组件 12..21 档已绑定 gtnecore:circuits/{{umv,swv,gcv,uxv,qgv,cyv,opv,vev,sgv,max}} tag");
        // 验证：读回绑定值（若 GTCEu 的 init() 后于本方法执行，此处会打印旧值/空）
        LOGGER.info("GTNEcore: 电路绑定验证 get(12)={} get(15)={} get(21)={}",
                GTCraftingComponents.CIRCUIT.get(12), GTCraftingComponents.CIRCUIT.get(15),
                GTCraftingComponents.CIRCUIT.get(21));
    }
}
