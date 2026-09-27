# GT × AE2 深度联动 · 设计规格

> 状态：**设计稿，待定方向**（2026-09-27）
> 前提：AE2 **已在**（`implementation fg.deobf("curse.maven:ae2-223794:7148487")`），
> 且 GTCEu 强依赖它（GTMThings 嵌入包要求），**不会缺席**。
>
> 已有基础：**样板携带虚拟电路号**（5 个 mixin，已实现）——
> 见 [`AE2-样板携带虚拟电路号-实现说明.md`](./AE2-样板携带虚拟电路号-实现说明.md)。
> 本文档做的是**在此之上新增内容**，不重复那套。
>
> 用户点名方向：**量子存储 / GT 电路当升级**。

---

## 一、AE2 侧的能力边界（已核实，1.20.1 / AE2 15.x）

### 1.1 两个干净的扩展口

| 接口 | 作用 | 用它做什么 |
|---|---|---|
| `IStorageProvider` | `mountInventories(IStorageMounts)` —— 把存储挂进 ME 网络 | **方块/多方块形式的存储** |
| `IStorageMounts` | 挂载点 | 同上 |
| `MEStorage` | `insert` / `extract` / `getAvailableStacks` / `isPreferredStorageFor` | 自定义存储的**实现体** |
| `IStorageService` | `getInventory` / `getCachedInventory` / `addGlobalStorageProvider` / `invalidateCache` | 查询网络、注册全局存储 |
| `IBasicCellItem` | `getBytes` / `getBytesPerType` / `getTotalTypes` / `getIdleDrain` | **物品形式**的存储元件 |
| `StorageCell` | `getStatus` / `persist` | 元件的运行时状态 |

**结论**：AE2 把"往网络里加存储"和"做存储元件"都留成了公开 API，
**不需要 mixin**——这是它设计得好。

### 1.2 AE2 自己已有量子网络（我们的参照物）

```
appeng.blockentity.qnb.QuantumBridgeBlockEntity      ← 量子网络桥（跨维度连接两个 ME 网络）
appeng.block.qnb.QuantumRingBlock / QuantumLinkChamberBlock / QuantumBaseBlock
appeng.blockentity.spatial.SpatialIOPortBlockEntity  ← 空间存储 IO
appeng.api.networking.spatial.ISpatialService
```

所以"量子"这件事 AE2 已经做了——**我们的机会不是重做，而是用 GT 的科技树去"升级"它**。

### 1.3 GTCEu 已有的 ME 内容（别重复造）

`GTAEMachines`：物品输入/输出总线、流体输入/输出仓、**库存输入**变体、
`ME_PATTERN_BUFFER` / `ME_PATTERN_BUFFER_PROXY`（样板缓冲器）。
`GTAEPlaceholders`：AE2 数据 → GT 电脑屏幕的占位符。

---

## 二、三个候选方案（按推荐度排）

### 方案 A · **GT 分级的量子存储核心**（推荐先做）

**形态**：一个**多方块**（或单方块，视你想要的分量），实现 `IStorageProvider`，
向 ME 网络投递一个巨大的 `MEStorage`。

**"深度联动"体现在哪**：

| 维度 | 由 GT 什么决定 |
|---|---|
| **存储容量（bytes）** | 结构里的**存储核心方块**数量 × 每块容量；每块容量按 **GT 材料等级**分档（如 不锈钢 / 钛 / 钨钢 / 中子素…） |
| **可存种类数（types）** | 由 **GT 电路** 设定（电路号 = 类型配额档位）|
| **闲置耗电（idle drain）** | 按容量阶梯，接 GT 能源仓 |
| **解锁** | 用 GT 材料合成，天然卡在科技树上 |

**为什么这个方案最合适**：
1. **`IStorageProvider` 是公开 API**，不需要 mixin（维护成本低）
2. **容量/种类/耗电三个维度天然适合"用 GT 材料与电路分级"** —— 这正是"GT 电路当升级"的落点
3. 与"量子"的名分对得上：一个把 ME 存储做成巨型科技方块的东西
4. 可复用我们已有的多方块模式（热力反应釜、对顶砧的多方块写法）

**风险**：
- 巨型存储会被玩家当"无限仓库"，**需要认真的平衡**（容量 vs 耗电 vs 造价）
- 必须处理**存档**（`persist`）与**区块卸载**（存储数据不能丢）

---

### 方案 B · **GT 分级量子网络桥**（跨维度）

**形态**：多方块，用 GT 材料分级，替代/增强 AE2 自带量子网络桥。

| 维度 | 由 GT 什么决定 |
|---|---|
| 连接距离 / 是否跨维度 | 结构等级 |
| 可桥接的频道数 | GT 电路 |
| 耗电 | GT 能源仓 |

**风险**：AE2 自带量子网络桥已经够用，**做这个容易被说"换皮"**。
除非有明确的差异化点子（比如"桥接 N 个网络"而不是两个），否则不建议先做。

---

### 方案 C · **GT 材料存储元件**（`IBasicCellItem`）

**形态**：物品形式的存储元件，`getBytes` / `getTotalTypes` 由 **GT 材料与电路**决定。

**优点**：实现最轻（一个 Item + 几个方法），立刻能用。
**缺点**：AE2 本身已有 1k~256k 各种元件，**差异化空间小**——
除非做成"GT 特有材料才能造的、跨越数量级的"（比如中子素元件 = 天文数字容量）。

---

## 三、共同的技术要点（三个方案都会遇到）

### 3.1 GT 分级怎么表达

建议统一一张表，避免每个方案各写一套：

```java
public enum QuantumTier {
    // 材料 → 容量倍率 / 种类 / 闲置耗电
    STEEL(...), TITANIUM(...), TUNGSTENSTEEL(...), NEUTRONIUM(...);
}
```

### 3.2 电路当"配置"而非"材料"

沿用已有做法：**电路号作为参数**（样板携带 / 机器幽灵电路槽），
而不是消耗品。GTCEu 的 `IHasCircuitSlot` 已经能读。

### 3.3 存档与区块卸载

`MEStorage` 的实现体若挂在方块实体上，必须处理：
- 数据存 NBT（`persist()`）
- 区块卸载后网络查询不能崩（AE2 的 `IStorageService` 会缓存 inventory）
- 建议**只存"已用容量/种类索引"，不缓存在内存里全量物品**

### 3.4 平衡

参考 GTCEu 自己的 ME 仓设计。**容量不该无限**——
建议设定"存储核心数量 × 每块容量"的硬上限，并且**耗电随容量增长**，
让它成为"要喂电才能用的大仓库"，而不是免费的无限盘。

---

## 四、待你拍板

1. **先做哪个方案**（我推荐 A：GT 分级量子存储核心）
2. **形态**：多方块（有仪式感、可分级）还是单方块（实现快）
3. **容量基调**：是"比 AE2 现有元件大一个数量级"还是"大好几个数量级"
   （直接决定它是中后期主力还是终局内容）
4. **"量子"要不要真的跨维度**：方案 A 是单网络巨型存储；
   若你想要"同一份存储在多个维度可见"，那要接 AE2 的量子网络机制，复杂度上一个台阶

---

## 五、与既有系统的关系

- **不动** AE2 自带的量子网络桥 / 空间存储 / 存储元件
- **复用** 已有的样板携带电路号那套（电路读写在 `IntCircuitBehaviour` + `IHasCircuitSlot`）
- **可叠加** AE2 占位符：把量子存储的容量/占用显示到 GT 电脑屏幕
  （`PlaceholderHandler.addPlaceholder`，见 `GTAEPlaceholders` 的写法）

---

*设计稿 2026-09-27 · 用户方向：量子存储 / GT 电路当升级*
