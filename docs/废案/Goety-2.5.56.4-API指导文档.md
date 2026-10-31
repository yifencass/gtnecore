# GTNEcore 开发指南：Goety 2.5.56.4 灵魂能量 API 对接

> 本文档为 GTNEcore 对接 Goety 灵魂能量体系的 API 指导草稿。所有类名与方法签名均来自对
> `goety-586095-8647359_mapped_official_1.20.1.jar` 的 javap 实际输出（本地权威素材）。
> 凡无法从 jar 验证的内容均标注 ⚠️。本地 jar 路径：
> `~/.gradle/caches/forge_gradle/deobf_dependencies/curse/maven/goety-586095/8647359_mapped_official_1.20.1/`
>
> 机制研究（灵魂方舟绑定/传送/死亡回生原理）见同目录《Goety-灵魂方舟Arca-原理.md》。

---

## 版本与环境

| 项 | 值 |
|---|---|
| 模组 | Goety（亡灵/灵魂法术模组，作者 Polarice3） |
| 版本 | `2.5.56.4`（jar `Implementation-Version`） |
| CurseForge | `curse.maven:goety-586095:8647359` |
| Minecraft / Forge | 1.20.1 / Forge 47.x（`loaderVersion="[47,)"`） |
| 强制前置 | Curios（`curios-forge:5.14.1+1.20.1`） |
| 许可 | MIT（mods.toml） |
| 官方仓库 | https://github.com/Polarice3/Goety-2 |

依赖声明（`build.gradle`）：

```groovy
// Goety（依赖 Curios；modmaven 无 pom，用 curse.maven 引入）
implementation fg.deobf("curse.maven:goety-586095:8647359")
// Curios（modmaven 无 pom → 用户以 fg.deobf 直接引入 5.14.1 成功）
compileOnly fg.deobf("top.theillusivec4.curios:curios-forge:5.14.1+1.20.1")
runtimeOnly fg.deobf("top.theillusivec4.curios:curios-forge:5.14.1+1.20.1")
```

> ⚠️ curse.maven 仓库被墙（connection reset）。本项目目前能拉下来，是本地 gradle 缓存已有
> 构件；新机器构建若失败需走代理或手动放 `libs/`。

---

## 1. Goety 简介

Goety 是亡灵法术模组：玩家可通过**灵魂能量（Soul Energy）**施法，可亡灵化（lichdom）、
召唤/驯服仆人（servant）、建造阿尔卡（Arca）仪式等。对 GTNEcore 而言，Goety 的价值点是其
**灵魂能量体系**——一种与现有科技能量体系（EU/FE）完全正交的"魔法能量"，本项目将其
**流体化**为 `liquid_soul_energy` 以接入科技管道（详见 §7）。

---

## 2. 灵魂能量核心机制

- **单位**：int（无独立单位名，UI 直接显示数字；玩家 SE 上限 = `MaxArcaSouls`，默认
  **100000**（范围 [10, 2^31-1]）；复活阈值/一次性消耗 = `MaxSouls`，默认 **10000**）
- **获得**：击杀生物（`SEHelper.handleKill` 按生物类型给量）、灵魂吸收方块（Soul Absorber，
  把物品/实体转化）、其他魔法物品
- **消耗**：施法（法术有 soul cost）、灵魂维修（`ISoulRepair` 物品）、折扣（`ISoulDiscount`）
- **反噬（Recoil）**：过度消耗触发的惩罚状态
- **休息期（Rest Period）**：魔法物品使用后的冷却窗口
- **存储位置**：玩家实体上的 capability（`ISoulEnergy`），玩家数据的一部分
- **联网**：服务端持有权威数据，`SEUpdatePacket` 按玩家 UUID 全量同步到客户端

---

## 3. 核心 API：`ISoulEnergy` capability

包：`com.Polarice3.Goety.common.capabilities.soulenergy`

```java
// SEProvider.CAPABILITY —— 静态 Capability<ISoulEnergy> 字段
public static Capability<ISoulEnergy> SEProvider.CAPABILITY;
```

`ISoulEnergy` 接口（javap 实测全量）：

```java
public interface ISoulEnergy {
  // —— 阿尔卡（Arca，重生仪式点）——
  BlockPos getArcaBlock();
  void setArcaBlock(BlockPos);
  ResourceKey<Level> getArcaBlockDimension();
  void setArcaBlockDimension(ResourceKey<Level>);

  // —— 灵魂能量本体（对接核心）——
  boolean getSEActive();
  void setSEActive(boolean);
  int getSoulEnergy();
  void setSoulEnergy(int);
  boolean increaseSE(int);      // 增加，返回是否成功
  boolean decreaseSE(int);      // 减少，返回是否成功

  // —— 反噬 / 休息期 ——
  int getRecoil();
  void setRecoil(int);
  boolean apostleWarned();
  void setApostleWarned(boolean);
  int getRestPeriod();
  void setRestPeriod(int);
  boolean increaseRestPeriod(int);
  boolean decreaseRestPeriod(int);

  // —— 宿敌（grudge）/ 盟友（ally）/ 压制（grounded）名单 ——
  Set<UUID> grudgeList();
  void addGrudge(UUID);
  void removeGrudge(UUID);
  List<EntityType<?>> grudgeTypeList();
  void addGrudgeType(EntityType<?>);
  void removeGrudgeType(EntityType<?>);
  Set<UUID> allyList();
  void addAlly(UUID);
  void removeAlly(UUID);
  List<EntityType<?>> allyTypeList();
  void addAllyType(EntityType<?>);
  void removeAllyType(EntityType<?>);
  Set<UUID> groundedList();
  void addGrounded(UUID);
  void removeGrounded(UUID);
  List<EntityType<?>> groundedTypeList();
  void addGroundedType(EntityType<?>);
  void removeGroundedType(EntityType<?>);

  // —— 研究（Research）——
  List<Research> getResearch();
  void addResearch(Research);
  void removeResearch(Research);

  // —— 杂项状态 ——
  FocusCooldown cooldowns();
  void setCooldowns(FocusCooldown);
  Projectile getGrappling();
  void setGrappling(Projectile);
  int bottling();
  void setBottling(int);
  int wardingLeft();
  int maxWarding();
  void setWarding(int);
  void setMaxWarding(int);
  DyeColor bannerBaseColor();
  void setBannerBaseColor(DyeColor);
  ListTag bannerPattern();
  void setBannerPattern(ListTag);
  int getTicksInAir();
  int getAirJumps();
  int getAirJumpCooldown();
  void setTicksInAir(int);
  void setAirJumps(int);
  void setAirJumpCooldown(int);
  int getMiningProgress();
  void setMiningProgress(int);
  BlockPos getMiningPos();
  void setMiningPos(BlockPos);
  UUID getCameraUUID();
  void setCameraUUID(UUID);
  BlockPos getEndWalkPos();
  void setEndWalkPos(BlockPos);
  ResourceKey<Level> getEndWalkDimension();
  void setEndWalkDimension(ResourceKey<Level>);
}
```

实现类 `SEImp`（玩家数据持久化走 `serializeNBT`/`deserializeNBT`，字段即上表状态）。

---

## 4. 工具类 `SEHelper`（对接首选）

包：`com.Polarice3.Goety.utils.SEHelper`

**不要直接操作 capability**——Goety 官方用法是经 `SEHelper` 静态方法（内部取
`LazyOptional` 并做 `resolve` 判空）。对接液态灵魂能量最常用的方法：

```java
// —— 取 capability（一切的前提）——
ISoulEnergy getCapability(Player);

// —— 灵魂能量读写（与流体互转的核心）——
int  getSESouls(Player);
void setSESouls(Player, int);        // 直接设值
void setSoulsAmount(Player, int);    // 设值（含 UI 刷新）
boolean increaseSESouls(Player, int); // 增加（推荐）
boolean decreaseSESouls(Player, int); // 减少（推荐，失败=能量不足）
boolean getSoulsContainer(Player);   // 是否有灵魂容器物品
boolean getSoulsAmount(Player, int); // 检测是否达到指定量
int    getSoulAmountInt(Player);

// —— 击杀/获得灵魂（杀怪流）——
void   handleKill(LivingEntity, LivingEntity, DamageSource); // 击杀→加灵魂（含规则）
void   rawHandleKill(LivingEntity, LivingEntity, int, DamageSource);
void   increaseSouls(Player, int);
void   decreaseSouls(Player, int);
int    getSoulGiven(LivingEntity);
int    SoulMultiply(LivingEntity, DamageSource);
float  soulDiscount(LivingEntity);

// —— 反噬 / 休息期 ——
int  getRecoil(Player);  void setRecoil(Player, int);
int  getRestPeriod(Player);  void setRestPeriod(Player, int);

// —— 阿尔卡传送 ——
boolean teleportToArca(Player);

// —— 存档（仇敌/盟友名单存到世界级 grimoire 数据）——
boolean isSavedGrudge(ServerLevel, UUID, LivingEntity);
boolean isSavedAlly(ServerLevel, UUID, LivingEntity);
```

---

## 5. capability 生命周期与同步

| 环节 | 类/事件 | 说明 |
|---|---|---|
| 注册 | `InitEvents.registerCapabilities(RegisterCapabilitiesEvent)` | 注册 `SEProvider.CAPABILITY` |
| 挂载 | `InitEvents.attachCapabilities(AttachCapabilitiesEvent<Entity>)` | 挂到实体上（玩家） |
| 实现 | `SEProvider implements ICapabilitySerializable<CompoundTag>` | NBT 持久化 |
| 数据复制 | `ModEvents.onPlayerClone` | 死亡/回生时复制全部状态 |
| 客户端同步 | `SEUpdatePacket(Player)` → consume | 按玩家 UUID 全量下发 NBT |

> 对接要点：**服务端写入 → 自动同步**。Goety 在 `increaseSE/decreaseSE` 等写操作路径上
> 会自己发 `SEUpdatePacket`，第三方直接调用 `SEHelper.increaseSESouls(serverPlayer, n)`
> 即可，客户端 UI 自动刷新，无需自建网络包。⚠️（该结论依据 SEUpdatePacket 构造与
> SEHelper 调用链推断，未逐行反编译验证。）

---

## 6. 本项目现状（2026-08-15）

已完成（见 `src/main/java/org/ringclouds/gtnecore/soulenergy/SoulEnergyFluids.java`）：

- 注册 `gtnecore:liquid_soul_energy`：FluidType（青绿 tint `#4FE3C1`，温度 800，密度 1500，
  粘度 2000）+ ForgeFlowingFluid（source/flowing）+ LiquidBlock + 桶
- `forge:fluids/liquid_soul_energy` 标签
- 专属动画贴图：青绿波浪，4 帧长条 + mcmeta（frametime=6）
- 踩坑：`FluidType.Properties` 无 color()，客户端扩展走 `getRenderPropertiesInternal`
  返回 `IClientFluidTypeExtensions`；纹理必须提供（null → Jade 渲染 NPE）

**待办**：液态灵魂能量 ↔ 玩家灵魂能量（ISoulEnergy）对接逻辑（§7 方案）。

---

## 7. 对接方案（液态灵魂能量 ↔ ISoulEnergy）

设计目标：科技管道（GTM 流体仓/管道）搬运的 `liquid_soul_energy` 与玩家灵魂能量互转。

### 7.1 方向与换算

| 方向 | 操作 | 建议换算 |
|---|---|---|
| 流体 → 玩家 | 桶/仓排液时 `SEHelper.increaseSESouls(player, n)` | 1 mB = 1 灵魂能量（⚠️ 数值待定，需平衡） |
| 玩家 → 流体 | `SEHelper.decreaseSESouls(player, n)` 成功则产出流体 | 同上 |
| 玩家 → 流体（被动抽取） | 玩家持有某种容器物品时按 tick 转移 | 参照 Goety 自身灵魂容器节奏 |

### 7.2 接入点候选

1. **流体管道自动输出**（GTM 仓/管道拉取流体时回调）——最"科技"的接入，需查 GTM 管道
   提取侧的扩展点（`IFluidTransfer` 相关），工作量大
2. **专用方块/机器**：GTNEcore 自建"灵魂接口"方块（如能源仓风格，但面向玩家而非机器），
   对接 `Player` capability——实现可控，推荐先做
3. **物品右键**：桶右键玩家实体（类似挤奶），最简单，可作 Demo

### 7.3 服务端/客户端注意

- 只能在**服务端**写 `ISoulEnergy`（权威数据），客户端只读显示
- 写后由 Goety 自身同步（§5），无需额外发包
- 流体侧的 mB 数值同步走现有 GTM/管道机制，与 capability 解耦

### 7.4 风险点

- 灵魂能量有上限，流体注入超上限时 `increaseSouls` 可能失败——需有溢出返回路径
  （流体退回管道/仓）
- 反噬（Recoil）与休息期（RestPeriod）机制会拦截部分魔法消耗，注入时不受影响，
  但注意玩家处于亡灵化/实验状态时的行为 ⚠️
- 多人：每个玩家独立 capability，流体必须按"玩家归属"路由（方块绑定玩家
  UUID，参考 Goety Arca 的做法）

---

## 8. 其他扩展点索引（`api/` 包，供后续参考）

```
com.Polarice3.Goety.api.
├── blocks/IEnchanteableBlock, IEnchantedBlock, ISeat     # 附魔/可坐方块
├── blocks/entities/IOwnedBlock, ISoulCandle, ITrainingBlock,
│                     IWaystoneBlock, IWindPowered, IBarrack
├── entities/IOwned (26KB 大接口), ITrainable, IChunkLoader,  # 仆人/驯服/区块加载
│             IHungry, IMobCrafter, IHeretic, IGolem, ...
├── entities/ally/IServant, IAquaServant                  # 水裔仆人
├── entities/ally/illager/ILooter, ITrainIllager, IllagerType
└── items/IPersist, ISoulRepair, ISoulDiscount            # 灵魂维修/折扣
```

关键辅助类：

- `com.Polarice3.Goety.common.capabilities.soulenergy.FocusCooldown` —— 法术冷却表
- `com.Polarice3.Goety.common.research.Research` —— 玩家研究条目
- `com.Polarice3.Goety.common.capabilities.lichdom.ILichdom` —— 亡灵化（lich）capability
  （另一独立 capability，ModEvents.onPlayerClone 中有复制逻辑）
- `com.Polarice3.Goety.common.blocks.SoulAbsorberBlock` —— 灵魂吸收方块（物品/实体 →
  灵魂能量，流体化之外的另一条获得路径）
- `com.Polarice3.Goety.common.crafting.SoulAbsorberRecipes` —— 吸收配方序列化器

---

## 9. 验证手段

- 对接后进游戏（`runClient`）：JEI 看流体、GTM 管道搬运、玩家身上 SE UI 数值变化
- 服务端日志：`SEHelper.increaseSESouls` 返回值日志（false = 上限/规则拦截）
- 反编译复核：本文所有签名可用本地 jar 复核：
  `"$JDK17/bin/javap.exe" -p -classpath <goety jar> <类名>`
