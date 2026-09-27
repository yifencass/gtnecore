package org.ringclouds.gtnecore.research;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.Nullable;
import org.ringclouds.gtnecore.Gtnecore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * 研究条目注册表。
 *
 * <h2>职责划分（用户 2026-09-26 定的模型）</h2>
 * <b>注册</b>和<b>归类</b>是两件独立的事，各自一个入口：
 *
 * <pre>
 * // ① 注册研究条目 —— 启动阶段
 * GtneResearch.register("chemical", "gtnecore.research.chemical");
 *
 * // ② 把配方加进条目 —— 服务端
 * GtneResearch.of("chemical").addAll(GTRecipeTypes.CHEMICAL_RECIPES);
 * GtneResearch.of("chemical").add("gtceu:chemical_reactor/xxx");
 * </pre>
 *
 * 这样脚本里读起来就是"注册 化工研究"、"化学反应釜配方加入化工研究"，
 * 与 {@code ServerEvents.recipes} 那类按配方类型批量操作的 KubeJS 习惯一致。
 *
 * <h2>KubeJS 用法</h2>
 * <pre>
 * const Research = Java.loadClass('org.ringclouds.gtnecore.research.GtneResearch')
 * const Types    = Java.loadClass('com.gregtechceu.gtceu.common.data.GTRecipeTypes')
 *
 * // 启动期
 * Research.register('chemical', 'gtnecore.research.chemical')
 * Research.register('cracking', 'gtnecore.research.cracking')
 *
 * // 服务端（配方已加载）
 * Research.of('chemical').addAll(Types.CHEMICAL_RECIPES)
 * Research.of('cracking').addAll(Types.CRACKING_RECIPES)
 * </pre>
 *
 * <h2>匹配规则（{@link RecipePattern}）</h2>
 * <ul>
 * <li><b>精确</b>：{@code gtceu:macerator/macerator_aluminium_ingot}</li>
 * <li><b>前缀通配</b>：{@code gtceu:macerator/*}</li>
 * </ul>
 * 大小写敏感的全等 / 字符串前缀，**刻意不用正则**——正则写错会静默锁掉一堆
 * 不该锁的配方，那种问题极难查。
 *
 * <h2>一条配方归谁</h2>
 * 只归**一个**条目：多项命中时以**先注册的条目**为准（注册顺序即优先级）。
 */
public final class GtneResearch {

    /** 通配后缀。 */
    public static final String WILDCARD = "*";

    /** 一条配方匹配规则：精确 id，或以 {@code *} 结尾的前缀。 */
    public record RecipePattern(String raw, @Nullable String prefix) {

        public static RecipePattern of(String raw) {
            if (raw.endsWith(WILDCARD)) {
                return new RecipePattern(raw, raw.substring(0, raw.length() - WILDCARD.length()));
            }
            return new RecipePattern(raw, null);
        }

        /** 这条配方 id 是否命中本规则。 */
        public boolean matches(ResourceLocation recipeId) {
            String id = recipeId.toString();
            return prefix == null ? id.equals(raw) : id.startsWith(prefix);
        }
    }

    /** 注册表：注册顺序保留（决定展示顺序与命中优先级）。 */
    private static final Map<ResourceLocation, ResearchCategory> CATEGORIES = new LinkedHashMap<>();

    // ==================== ① 注册研究条目（启动阶段） ====================

    /**
     * 注册一个研究条目。
     *
     * <p>可重复调用：同 id 后注册的**覆盖**先前的（方便脚本覆盖内置条目）。
     *
     * @param path     研究 id（不带命名空间，如 {@code chemical}）
     * @param titleKey 本地化键（如 {@code gtnecore.research.chemical}）
     * @return 该条目对象，可直接链式 {@code .addAll(...)}
     */
    public static ResearchCategory register(String path, String titleKey) {
        ResourceLocation id = Gtnecore.id(path);
        ResearchCategory category = new ResearchCategory(id, titleKey);
        CATEGORIES.put(id, category);
        return category;
    }

    /** 注册并指定前置研究。 */
    public static ResearchCategory register(String path, String titleKey, String... prerequisites) {
        ResearchCategory category = register(path, titleKey);
        for (String p : prerequisites) {
            if (p != null && !p.isBlank()) {
                category.prerequisites.add(Gtnecore.id(p));
            }
        }
        return category;
    }

    /** 反注册（调试 / 脚本覆盖用）。 */
    public static void unregister(String path) {
        CATEGORIES.remove(Gtnecore.id(path));
    }

    /**
     * 内置条目。
     *
     * <p>由 {@code Gtnecore} 构造器调用（启动最早期）。**目前只放示例条目**——
     * 真正的清单等你定下来再填，或由数据包 / KubeJS 覆盖。
     */
    public static void bootstrapBuiltin() {
        register("basic", "gtnecore.research.basic");
        register("chemical", "gtnecore.research.chemical");
        register("cracking", "gtnecore.research.cracking", "chemical");
    }

    // ==================== ② 归类：把配方加进条目 ====================

    /** 取条目并以实例方法调 {@code add} / {@code addAll}（脚本里读起来最顺）。 */
    public static @Nullable ResearchCategory of(String path) {
        return CATEGORIES.get(Gtnecore.id(path));
    }

    public static @Nullable ResearchCategory byId(ResourceLocation id) {
        return id == null ? null : CATEGORIES.get(id);
    }

    // ==================== 查询 ====================

    public static Collection<ResearchCategory> all() {
        return Collections.unmodifiableCollection(CATEGORIES.values());
    }

    public static int size() {
        return CATEGORIES.size();
    }

    public static boolean exists(@Nullable ResourceLocation id) {
        return id != null && CATEGORIES.containsKey(id);
    }

    /**
     * 找出**第一条**管理该配方的研究条目（注册顺序优先）。
     *
     * <p>走缓存映射，O(1)；缓存由 {@link ResearchCategory#add} 等修改操作失效重建。
     */
    public static @Nullable ResearchCategory researchFor(ResourceLocation recipeId) {
        ResourceLocation owner = membership().get(recipeId);
        return owner == null ? null : CATEGORIES.get(owner);
    }

    /** 某条目的显示名；未注册时退回 id 字符串。 */
    public static String displayNameOf(@Nullable ResourceLocation id) {
        ResearchCategory category = byId(id);
        return category != null ? category.displayName() : String.valueOf(id);
    }

    // ==================== 成员缓存 ====================

    private static Map<ResourceLocation, ResourceLocation> membershipCache = null;

    /** 配方 id → 归属条目 id。改动过归类就重建。 */
    private static Map<ResourceLocation, ResourceLocation> membership() {
        if (membershipCache == null) {
            Map<ResourceLocation, ResourceLocation> map = new LinkedHashMap<>();
            // 按注册顺序遍历 → 先注册的条目优先占位（putIfAbsent 保证不被后来者抢）
            for (ResearchCategory category : CATEGORIES.values()) {
                category.collectInto(map);
            }
            membershipCache = map;
        }
        return membershipCache;
    }

    /** 归类有变动时调（由 {@link ResearchCategory} 的修改方法触发）。 */
    static void invalidateMembership() {
        membershipCache = null;
    }

    // ==================== 条目 ====================

    /**
     * 一个研究条目：一个 id、一个标题、以及"它管哪些配方"。
     *
     * <p>成员记录三种形式，各自独立：
     * <ul>
     * <li>{@code exactIds} —— 直接列出的配方 id（来自一条条 {@code add}）</li>
     * <li>{@code patterns} —— 带通配的规则</li>
     * <li>{@code bulk} —— 批量登记（来自 {@code addAll(配方类型)}），
     * 存的是"当时这个类型下的全部配方 id"</li>
     * </ul>
     */
    public static final class ResearchCategory {

        private final ResourceLocation id;
        private final String titleKey;
        private final Set<ResourceLocation> exactIds = new LinkedHashSet<>();
        private final List<RecipePattern> patterns = new ArrayList<>();
        /** 批量登记：配方 id → 它来自哪个配方类型（只为日志好读）。 */
        private final Map<ResourceLocation, ResourceLocation> bulk = new LinkedHashMap<>();
        private final Set<ResourceLocation> prerequisites = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        /** 附加说明（可空），供 UI 显示。 */
        private String descriptionKey = "";

        private ResearchCategory(ResourceLocation id, String titleKey) {
            this.id = id;
            this.titleKey = titleKey;
        }

        public ResourceLocation id() {
            return id;
        }

        public String titleKey() {
            return titleKey;
        }

        public Set<ResourceLocation> prerequisites() {
            return Collections.unmodifiableSet(prerequisites);
        }

        public String descriptionKey() {
            return descriptionKey;
        }

        public ResearchCategory describe(String descriptionKey) {
            this.descriptionKey = descriptionKey == null ? "" : descriptionKey;
            return this;
        }

        /** 给玩家看的名字（客户端本地化）。 */
        public String displayName() {
            return Component.translatable(titleKey).getString();
        }

        // ---------- 归类入口 ----------

        /**
         * 把**一条配方 id** 加进本条目。
         *
         * <p>末尾带 {@code *} 时按前缀通配处理。
         *
         * @return this（可链式）
         */
        public ResearchCategory add(String recipeId) {
            if (recipeId == null || recipeId.isBlank()) {
                return this;
            }
            if (recipeId.endsWith(WILDCARD)) {
                patterns.add(RecipePattern.of(recipeId));
            } else {
                ResourceLocation parsed = ResourceLocation.tryParse(recipeId);
                if (parsed == null) {
                    org.slf4j.LoggerFactory.getLogger("GTNEcore[研究]")
                            .warn("研究 {} 收到非法配方 id: {}", id, recipeId);
                    return this;
                }
                exactIds.add(parsed);
            }
            invalidateMembership();
            return this;
        }

        public ResearchCategory add(ResourceLocation recipeId) {
            if (recipeId != null) {
                exactIds.add(recipeId);
                invalidateMembership();
            }
            return this;
        }

        /**
         * 把**某个配方类型下的全部配方**加进本条目。
         *
         * <p>等价于"化学反应釜配方加入化工研究"。用过滤器可只取一部分，
         * 例如 {@code addAll(CHEMICAL_RECIPES, r -> r.id.getPath().contains("sulfuric"))}。
         *
         * <p>⚠ 必须在**服务端配方加载之后**调用（如
         * {@code ServerAboutToStartEvent} 或 {@code ServerEvents.recipes} 的后续阶段）；
         * 配方还没加载时这里会拿到空表并打警告，**不会静默失效**。
         *
         * @param recipeType 配方类型（GTCEu 的 {@code GTRecipeType}）
         * @param filter     可选过滤器；传 {@code null} 表示全要
         * @return this（可链式）
         */
        public ResearchCategory addAll(com.gregtechceu.gtceu.api.recipe.GTRecipeType recipeType,
                                       @Nullable Predicate<com.gregtechceu.gtceu.api.recipe.GTRecipe> filter) {
            var logger = org.slf4j.LoggerFactory.getLogger("GTNEcore[研究]");
            MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                logger.warn("研究 {}: addAll({}) 在服务器就绪前调用 —— 未登记任何配方",
                        id, recipeType == null ? "null" : recipeType.registryName);
                return this;
            }
            if (recipeType == null) {
                logger.warn("研究 {}: addAll 收到 null 配方类型，忽略", id);
                return this;
            }
            var loaded = server.getRecipeManager().getAllRecipesFor(recipeType);
            if (loaded.isEmpty()) {
                logger.warn("研究 {}: 配方类型 {} 当前加载到 0 条 —— "
                                + "是否在配方加载之前调用了？未登记任何配方",
                        id, recipeType.registryName);
                return this;
            }
            int count = 0;
            for (var recipe : loaded) {
                if (filter != null && !filter.test(recipe)) {
                    continue;
                }
                bulk.put(recipe.id, recipeType.registryName);
                count++;
            }
            invalidateMembership();
            logger.info("研究 {}: {} 下登记了 {} / {} 条配方",
                    id, recipeType.registryName, count, loaded.size());
            return this;
        }

        /** 便捷重载：整个配方类型的配方全要。 */
        public ResearchCategory addAll(com.gregtechceu.gtceu.api.recipe.GTRecipeType recipeType) {
            return addAll(recipeType, null);
        }

        /** 清空本条目已登记的配方（不动条目本身）。 */
        public ResearchCategory clear() {
            exactIds.clear();
            patterns.clear();
            bulk.clear();
            invalidateMembership();
            return this;
        }

        // ---------- 查询 ----------

        /** 本条目管多少条配方（精确 + 通配 + 批量登记）。 */
        public int membershipCount() {
            return exactIds.size() + patterns.size() + bulk.size();
        }

        public Set<ResourceLocation> exactIds() {
            return Collections.unmodifiableSet(exactIds);
        }

        public List<RecipePattern> patterns() {
            return Collections.unmodifiableList(patterns);
        }

        /** 本条目登记的批量配方：配方 id → 来源配方类型。 */
        public Map<ResourceLocation, ResourceLocation> bulkMembers() {
            return Collections.unmodifiableMap(bulk);
        }

        /**
         * 把本条目管理的配方写进全局映射。
         *
         * <p>用 {@code putIfAbsent} —— 先注册的条目优先，后注册的不会抢走已有归属。
         */
        private void collectInto(Map<ResourceLocation, ResourceLocation> map) {
            for (ResourceLocation recipeId : exactIds) {
                map.putIfAbsent(recipeId, id);
            }
            for (ResourceLocation recipeId : bulk.keySet()) {
                map.putIfAbsent(recipeId, id);
            }
            // 通配规则无法预先展开（不知道全局配方表），由 ResearchRecipeInjector
            // 在扫到具体配方时回退查询 patterns —— 见 matchesPattern()
        }

        /** 通配规则是否命中某配方 id（供注入器回退查询）。 */
        public boolean matchesPattern(ResourceLocation recipeId) {
            for (RecipePattern pattern : patterns) {
                if (pattern.matches(recipeId)) {
                    return true;
                }
            }
            return false;
        }
    }
}
