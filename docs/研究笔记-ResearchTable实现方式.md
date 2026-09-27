# Research Table（1.12.2）实现研究 · 笔记

> 目的：**只研究实现方式**（用户 2026-09-27 要求）。
> 依赖形态：`compileOnly fg.deobf("curse.maven:research-table-307346:3479716")` —— 仅编译期可见。
>
> **许可提醒**：jar 内**没有** LICENSE / COPYING / NOTICE / README 文件，
> 仅 `mcmod.info` 写了 `authorList: ["Snownee"]`。
> 用户提到该项目是 **MIT**（应来自项目页面），**若要复用代码请署名 Snownee**，
> 并以项目页面声明的许称为准——不要只凭 jar 内容判断。
>
> 上游：`snownee.researchtable`，作者 Snownee，版本 1.0.1，MC 1.12.2
> 依赖：Forge、[Kiwi](https://github.com/Snownee/Kiwi)、GameStages、CraftTweaker

---

## 一、类结构总览（79 个类）

```
core/                          ← 机制本体（与加载器/脚本无关）
  Research                    一条研究：名称+分类+图标+条件+前置+奖励
  ResearchCategory            分类（图标 + 名称键）
  ResearchList                静态注册表：CATEGORIES + LIST(Map<String,Research>)
  ICondition<T>               条件：匹配进度 / 目标值
  ICriterion                  前置：满不满足 + 失败文案
  IReward                     奖励：earn(world, pos, player)
  ConditionTypes              ITEM / FLUID / ENERGY 的类型令牌
  CriterionResearches         前置：已完成某几项研究
  CriterionScore              前置：某计分维度在 [min,max] 内
  CriterionResearchCount      前置：某研究完成次数 ≥ N
  RewardExecute               奖励：跑命令
  DataStorage                 按 UUID 的进度存档（NBT）+ 客户端同步
  team/TeamProvider           团队抽象（getOwner / getMembers / getTeamName）
block/    TileTable + 三个 Wrapper（Item/Fluid/Energy）—— 检定玩家与机器里的资源
client/   列表 / 详情 / 进度条 三个 GUI 组件 + 渲染器
network/  PacketResearchChanged / PacketSyncClient
command/  CommandResearch（/research …）
plugin/   crafttweaker / gamestages / itemstages / forge能量 / grandeconomy / hwyla
```

**值得注意的分层**：`core` 完全不依赖 CraftTweaker / GameStages；
那些是 `plugin/` 下的**可选扩展**，注册进条件类型与奖励类型表。

---

## 二、核心数据结构

### 2.1 一条研究 = 容器

```java
public class Research {
    String name;
    ResearchCategory category;
    String title, description;
    List<ItemStack> icons;

    List<ICondition>        conditions;   // 消耗与进度
    Collection<ICriterion>  criteria;     // 前置门槛
    Collection<IReward>     triggers;     // 开始时触发
    Collection<IReward>     rewards;      // 完成时触发

    boolean canResearch(EntityPlayer, NBTTagCompound);   // 全部 criteria 通过
    void start(World, BlockPos, EntityPlayer);           // 跑 triggers
    void complete(World, BlockPos, EntityPlayer);        // 跑 rewards
}
```

运行模型：**消耗条件满足 → 可研究 → 开始（触发 triggers）→ 完成（触发 rewards）**。

### 2.2 三类扩展点（这是设计精华）

```java
// 条件：按"匹配什么类型"泛化，天然支持新资源种类
public interface ICondition<T> {
    Supplier<Class<T>> getMatchType();   // ITEM / FLUID / ENERGY / 自定义
    long matches(T);                     // 当前匹配到多少
    long getGoal();                      // 需要多少
}

// 前置：不只是布尔，还负责"差什么"的文案 —— UI 直接用
public interface ICriterion {
    boolean matches(EntityPlayer, NBTTagCompound);
    String getFailingText(EntityPlayer, NBTTagCompound);
}

// 奖励：把"完成后做什么"彻底抽象掉
public interface IReward {
    void earn(World, BlockPos, EntityPlayer);
}
```

**为什么这个设计好**：`Research` 不需要知道存在"物品条件""流体条件""GameStage 奖励"。
加一种新条件/奖励 = 写一个实现 + 注册类型，**不动 `Research`**。

### 2.3 注册表（静态、运行期可加）

```java
public final class ResearchList {
    public static final List<ResearchCategory> CATEGORIES;
    public static final Map<String, Research> LIST;
    public static synchronized boolean add(Research);   // 脚本运行期加
}
```

### 2.4 进度存储

```java
public class DataStorage {
    // UUID → (研究id → 完成次数)     注意是**计数**不是布尔
    Map<UUID, Object2IntMap<String>> records;
    // 团队名 → (研究id → 计数)
    Map<String, Object2IntMap<String>> players;

    static int complete(UUID, Research);
    static int count(UUID, String);
    static boolean hasAllOf(UUID, Collection<String>);
    // world load/save、玩家登录同步、客户端镜像 clientData
}
```

**按"完成次数"而不是布尔**，所以支持"可重复研究"（`maxCount` / `setNoMaxCount()`）。

### 2.5 团队抽象

```java
public interface TeamProvider {
    UUID getOwner(UUID player);
    Collection<UUID> getMembers(UUID player);
    String getTeamName(UUID player);
}
```

进度查询一律过这一层 → 单人时 `Stub` 实现返回自己，装团队 mod 时换实现。
**这正是我们撤掉那版想砍掉、但其实是正确的做法**。

---

## 三、CraftTweaker 侧（脚本驱动）

```java
ResearchBuilder(String name, ResearchCategoryWrapper category)
    .setIcons(IIngredient...)
    .setRequiredResearches(String...)          // 前置
    .setOptionalResearches(int, String...)     // N 选 M
    .setRequiredScore(String, String, int, int)
    .addCondition(IIngredient...)              // 物品消耗
    .addCondition(IIngredient, long, String)   // 带自定义失败文案
    .addCondition(ILiquidStack, long)          // 流体消耗
    .addEnergyCondition(long)                  // 能量消耗
    .setRewardItems(IItemStack...)
    .setRewardCommands(String...)
    .setTriggerItems / setTriggerCommands
    .setMaxCount(int) / setNoMaxCount()
    .build()
```

读起来就是脚本语言，**整合包作者不用写 Java**。这是它能被广泛使用的关键。

---

## 四、对照我们撤掉的那版（`研究系统-废案①`）

| 方面 | Research Table | 我们的废案① | 结论 |
|---|---|---|---|
| 研究本质 | **容器**：条件+前置+奖励 | 只是 `id + 标题` | ⬅ 我们太薄 |
| 解锁手段 | **通用** `IReward`（跑命令 / 加 GameStage / 给物品） | 只能当 `RecipeCondition` | ⬅ **最大差距** |
| 条件类型 | 泛化 + 可扩展（物品/流体/能量/自定义） | 无（"配了就解锁"） | ⬅ 我们缺 |
| 失败文案 | `ICriterion.getFailingText` 由条件自己给 | 无 | ⬅ 我们缺 |
| 团队 | `TeamProvider` 接口 | 直接砍掉 | 人家做对了 |
| 进度 | 计数（支持可重复研究） | 布尔 Set | 我们太简单 |
| 脚本 | CraftTweaker 流式 Builder | Java 静态方法 | 我们要接 KubeJS |
| 与机器的关系 | 研究台 GUI 里检定 | 配方条件自动拦机器 | 见下 |

### 最该吸取的一条

**我们把"研究"焊死在"配方条件"上了。**
用户的原始需求只是"一些配方需要研究才能解锁"，但我们的实现里
`研究 = 配方条件`，于是研究除了锁配方干不了别的，也没法表达"研究要消耗什么"。

Research Table 的做法是：**研究是独立的一层数据，锁配方只是它众多奖励之一**。
这样同一套研究既能锁配方、又能给物品、又能开 GameStage。

---

## 五、如果要移植到 GTCEu 1.20.1，可借鉴的点

按**价值从高到低**排：

1. **`IReward` 通用化**（最该抄）
   - `RewardGrantResearch`（解锁其他研究的前置）
   - `RewardUnlockRecipes`（把配方 id / 类型记进"已解锁"集合，由 `RecipeCondition` 查询）
   - `RewardRunCommand` / `RewardGiveItems`
   - **锁配方变成奖励的一种，而不是研究的定义**

2. **`ICondition<T>` 泛化 + 类型注册**
   - `ItemCondition` / `FluidCondition` / `EnergyCondition` 三类起步
   - 在 GTCEu 里这三类都能从机器部件读（输入总线 / 输入仓 / 能源仓）

3. **`ICriterion` 带失败文案**
   - 直接解决了"玩家不知道为什么不能研究"
   - 我们的废案①在 JEI 侧拿到了"需要研究：XXX"，但机器侧只有 GTCEu 的条件失败原因

4. **`TeamProvider` 抽象**
   - 默认 `Stub`（等于世界/单人共享），装 FTB Teams 时换实现
   - **接口先行**是关键：先有抽象，反射适配就只是换一个实现

5. **计数而非布尔**
   - 支持"可重复研究"和"完成次数门槛"

6. **脚本层**
   - 他走 CraftTweaker；我们应走 KubeJS
   - 关键是**注册 API 要能在脚本里读得顺**（用户这次点名了这个）

### 需要**不要**照搬的点

- 他依赖 Kiwi / GameStages / ItemStages —— 我们是不同生态，这些换成 GTCEu 自己的能力
- 他的研究是"在 GUI 里花钱换解锁"，我们已决定要做**专门的研究机器（多方块）**
  —— 两者可结合：机器 = 检定条件与消耗的场所，`Research` = 数据

---

## 六、研究源码的查看方式

```bash
JAR="$HOME/.gradle/caches/forge_gradle/deobf_dependencies/curse/maven/\
research-table-307346/3479716_mapped_official_1.20.1/\
research-table-307346-3479716_mapped_official_1.20.1.jar"

# 类清单
unzip -l "$JAR" | grep '\.class'

# 看某个类
javap -p -cp "$JAR" snownee.researchtable.core.Research
javap -p -c -cp "$JAR" snownee.researchtable.core.Research   # 带字节码
```

**注意**：该 jar 是 **1.12.2 的 Forge 环境**经 `fg.deobf` 重映射的，
方法名已是 MCP 名（`EntityPlayer` / `NBTTagCompound` / `WorldServer`），
但**混淆/重映射可能不完全**，读字节码时留意。

---

*研究笔记 2026-09-27 · 仅研究实现方式，未复制代码*
