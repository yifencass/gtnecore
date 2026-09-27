# AE2 样板携带虚拟电路号 · 实现说明

> 状态：✅ **已实现并可用**（不是设计稿）
> 位置：`src/main/java/org/ringclouds/gtnecore/mixin/ae2/`（5 个 mixin）+ `util/Ae2CircuitSupport.java`
> 依赖：AE2（`curse.maven:ae2-223794:7148487`，`implementation`）

---

## 一、解决什么问题

GTCEu 的机器**用"编程电路"（Integrated Circuit）区分配方分支**——同一台机器，
电路号不同就跑不同配方（典型：电路装配机、组装机、化学反应釜）。

在 AE2 自动化里，传统做法很笨：**每个电路号都要克隆一台机器 + 一个专属供应器**。
一条产线要占 8 台同型号机器、8 个样板供应器，布线爆炸。

本系统让**样板自己携带电路号**：

> **一张样板 = 一条产线切换指令。**
> 供应器推送这张样板时，自动把目标面 GT 机器的幽灵电路槽设成样板记录的号。

于是同一个供应器、同一台机器，可以按样板切换跑不同分支。

---

## 二、完整链路

```
① 编码端（玩家在 AE2 样板编码终端里点「+」或手工摆电路）
   ├─ EncodingHelperMixin            净化电路候选：只留"带号"电路
   ├─ EncodePatternTransferHandlerMixin  「+」填充后主动补一个带号电路进编码格
   └─ PatternEncodingTermMenuMixin   保存时：电路从输入列表摘出 → 号写进样板 NBT
                                                    │
                                          NBT 键 gtnecore_circuit
                                                    │
② 推送端（供应器推送样板给机器）                          ▼
   └─ PatternProviderLogicMixin      推送前把目标面所有 IHasCircuitSlot 机器的
                                     幽灵电路槽设成该号
```

### 关键数据

样板物品 NBT 上一个整数键：

```java
// util/Ae2CircuitSupport.java
public static final String CIRCUIT_KEY = "gtnecore_circuit";
```

> 常量单独放工具类的原因：**Mixin 类不允许非 private 静态字段**（Mixin 限制），
> 所以跨 mixin 共享的常量必须外置。

### 各 mixin 职责

| Mixin | 目标类 | 时机 | 做什么 |
|---|---|---|---|
| `PatternEncodingTermMenuMixin` | `PatternEncodingTermMenu` | `encodeProcessingPattern` 前/后 | **写端**：把编码格里的电路摘出来（不让它进样板输入），编码完成后把号写进样板 NBT，再把电路放回编码格 |
| `EncodePatternTransferHandlerMixin` | `EncodePatternTransferHandler` | `transferRecipe` 返回后 | **「+」键**：从 GT 配方语义直接读电路号，主动注入带号电路 |
| `EncodingHelperMixin` | `EncodingHelper` | `findBestIngredient` 开头 | **净化**：候选里既有裸电路又有带号电路时，剔除裸的 |
| `PatternProviderLogicMixin` | `PatternProviderLogic` | `pushPattern` 开头 | **读端**：把目标面机器的幽灵电路槽设成样板号 |
| `PatternEncodingTermMenuAccessor` | `PatternEncodingTermMenu` | — | `@Accessor` 拿 `encodedInputsInv` |

**为什么"电路被摘出样板输入"很重要**：电路是 `notConsumable` 语义（不消耗），
若留在样板输入列表里，**AE2 会去网络里找这个电路当材料**，导致样板永远凑不齐。

---

## 三、玩家怎么用

1. 在 AE2 **样板编码终端**里放一张空白处理样板
2. 用 JEI 的 **「+」键**从 GT 配方页填充，或手工摆材料
   - 用「+」时，电路号由系统从配方语义自动读出并写入
   - 手工摆时，把**带号的编程电路**放进编码格
3. 保存 → 样板 NBT 里带上 `gtnecore_circuit`
4. 样板放进**样板供应器**，对准 GT 机器
5. 推送时，供应器会把目标面所有机器的幽灵电路槽设成样板号

**注意**：`PatternProviderLogicMixin` 是**对目标面所有机器统一切号**
（`host.getTargets()` 遍历），只处理 `isCircuitSlotEnabled()` 为真的机器。
设计意图是"材料进任何一台都跑样板对应产线"。

---

## 四、踩过的四个坑（**这节最值钱**）

### 坑 1 · 写编码格必须走 AE2 的正规同步包

```java
// ❌ 只改客户端，服务端编码时读不到 —— 现象是"电路出现了但无效"
inputs.setStack(target, circuitStack);

// ✅ 客户端本地回显 + 发同步包
inputs.setStack(target, circuitStack);
NetworkHandler.instance().sendToServer(
        new InventoryActionPacket(InventoryAction.SET_FILTER,
                slots[target].index, GenericStack.wrapInItemStack(circuitStack)));
```

`ConfigInventory` 是**服务端权威**的；直接改只动客户端副本。
而且 AE2 的 menu 同步**不会**对注入包回推 `FakeSlot` 显示，
所以本地回显那一步也不能省——两个都要做。

### 坑 2 · 目标槽必须选「已启用」的槽

```java
// ✗ 未启用的槽 getItem 返回 EMPTY，但点击却生效
//   → 服务端数据在、客户端不显示 =「隐藏电路」事故
if (slots[i].isSlotEnabled()) { ... }
```

AE2 的编码格是**按行递进启用**的。往未启用槽写数据会出现
"数据在但那格是空的"这种最难受的状态。

### 坑 3 · 注入时不能数空位（材料的同步包还在路上）

`transferRecipe` 返回时，**AE2 自己填充材料的那批 `SET_FILTER` 包还没到服务端**，
客户端 inventory 是填充**前**的状态。所以：

```java
// ✗ 数空位 → 会选到材料将要占用的槽
// ✓ 用配方里的材料数量推算：AE2 填充按材料数 n 依序占 slot[0..n-1]
int materialCount = ...;   // 非电路物品输入数
for (int i = materialCount; i < slots.length; i++) { ... }
```

### 坑 4 · 网络里的裸电路会污染候选

`EncodingHelper.findBestIngredient` 按 **ME 网络库存优先**挑候选。
网络里囤着的**无号裸电路**会压过配方语义的**带号电路**，填进去就丢号。

修法：候选里同时存在带号与裸电路时，**剔除裸的**（`EncodingHelperMixin`）。
注意 `option` 列表可能是不可变的，要 try/catch `UnsupportedOperationException`。

---

## 五、电路号从哪读（「+」键路径）

两条来源，优先 `data`：

```java
int circuit = gtRecipe.data.getInt("circuit");     // KJS/数据配方可能用这个
// 兜底：从输入的 IntCircuitIngredient 里读
for (var content : gtRecipe.getInputContents(ItemRecipeCapability.CAP)) {
    if (content.getContent() instanceof Ingredient ing && ing.getItems().length > 0
            && IntCircuitBehaviour.isIntegratedCircuit(ing.getItems()[0])) {
        if (circuit <= 0) circuit = IntCircuitBehaviour.getCircuitConfiguration(ing.getItems()[0]);
    }
}
```

GT 配方里的电路以 **`notConsumable(IntCircuitIngredient)`** 形式存在
（builder 的 `circuitMeta`）。

---

## 六、相关但**不属于**本系统的 GTCEu 现成能力

别重复造：

| 能力 | 在哪 |
|---|---|
| GT 机器的 ME 仓 | GTCEu `GTAEMachines`：物品输入/输出总线、流体输入/输出仓、**库存输入**变体、样板缓冲器 |
| AE2 数据 → GT 电脑屏幕 | GTCEu `GTAEPlaceholders`（`PlaceholderHandler.addPlaceholder`） |
| GT 机器的幽灵电路槽接口 | `IHasCircuitSlot`（`isCircuitSlotEnabled` / `getCircuitInventory`） |
| 电路读写 | `IntCircuitBehaviour`（`isIntegratedCircuit` / `getCircuitConfiguration` / `stack`） |

---

## 七、已知边界 / 将来可能踩的

1. **`getTargets()` 全切号**：多台机器不同配方时是设计意图，但若玩家只想让一台切号，
   目前没有"按机器定向"的手段
2. **电路号上限**：`IntCircuitBehaviour` 的号范围是 GT 定的（0..32 之类），
   样板里存 `int`，写入前没做范围校验（`circuit <= 0` 才跳过）
3. **只处理处理样板（processing pattern）**：合成样板（crafting pattern）不走这条链
4. **AE2 版本敏感**：`PatternProviderLogic.pushPattern` 签名、`ConfigInventory`、
   `InventoryActionPacket` 都是 AE2 内部 API（非 `appeng.api.*`），**升级 AE2 时优先检查这 5 个 mixin**
5. **`remap = false`**：这些类都在 AE2 命名空间下、不走 Mojang 映射，
   所以 mixin 必须 `remap = false`，否则注入点找不到

---

*整理 2026-09-27 · 实现为早期成果，本次仅补文档*
