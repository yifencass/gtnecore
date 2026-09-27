# Create 包裹系统 × AE2 自动合成 × GTCEu 融合研究

> 2026-09-04 · 新功能前置研究。
> 对象：Create 6.0.8（deobf 7178761）、AE2 15.4.9（deobf 7148487）。

---

## 一、Create 6.0 包裹（Package）物流系统

### 核心概念

```
Stock Ticker（库存终端，全局网络视图）
   ↓ 请求（PackageOrderWithCrafts = 物品订单 + 合成订单）
Logistically Linked 网络（无线频道）
   ↓ 派发
Packager（打包机：贴在任意容器上，把物品打包成 Package 实体投递）
   ↓ 包裹实体飞行 / 蛙嘴动画
Frogport（蛙港：接收包裹→按地址送进目标容器）
Postbox（邮筒：玩家手动寄包裹）
Repackager（二次打包：合单）
```

### 关键 API（全部公开可扩展）

| 类 | 用途 |
|---|---|
| `PackageItem.containing(List<ItemStack>)` | 把物品列表打包成包裹物品（NBT 持有，18 槽上限） |
| `PackageItem.addAddress(stack, "freq:name")` | 包裹寻址（网络频率:地址名） |
| `PackageOrderWithCrafts` | record(物品订单 + `CraftingEntry` 合成订单列表)——**Create 的自动合成协议** |
| `UnpackingHandler` 接口 + `REGISTRY: Block→Handler` | **包裹拆包扩展点**：注册到方块上，包裹送达时自定义接收行为 |
| `PackagerBlockEntity` | 打包机（`InventorySummary getAvailableItems()` 库存视图） |
| `InventoryIdentifier` API | 让任意方块被 Packager 识别为库存源 |

### Create 自动合成（Factory Board / 蛙嘴面板）

- `FactoryPanelBehaviour`：挂在工厂面板上的"配方位"，缺料时向网络发合成请求
- 合成请求走 `PackageOrderWithCrafts.orderedCrafts`——由网络里注册了对应配方的
  面板/机器认领执行

## 二、AE2 自动合成

### 核心 API（公开接口，addon 友好）

```java
ICraftingProvider {          // 实现并挂到 GridNode → AE2 视你为合成供应方
    List<IPatternDetails> getAvailablePatterns();  // 你提供哪些样板
    boolean pushPattern(IPatternDetails, KeyCounter[] inputs); // AE2 推给你执行
    boolean isBusy();
}

IPatternDetails {            // 一份样板（输入/主输出/副产物）
    AEItemKey getDefinition();
    IInput[] getInputs();
    GenericStack[] getOutputs();
}
```

- AE2 CPU 发起任务 → 找空闲 provider → `pushPattern`（推料让机器跑）
- 完成品由机器自行推回网络（或经接口存入）
- **AE2 官方没有 Create 包裹集成**（Create 只有磨粉兼容配方）

## 三、融合功能设计空间

三方各自的强项：**GTCEu 机器（配方执行）** × **Create 包裹（实体物流+寻址）** × **AE2（样板+CPU 编排）**。

### 方案 A：包裹式样板供应器（Package Pattern Provider）★推荐

**一个方块，把 AE2 合成任务转成 Create 包裹物流。**

- 实现 `ICraftingProvider`：从相邻 AE2 样板供应器/或自身样板槽读样板
- `pushPattern()` 时：把配方输入 **`PackageItem.containing()` 打包成包裹**，
  交给后方/下方 Packager 投递到**目标 GT 机器旁的蛙港**
- GT 机器（组装机等）收到包裹（UnpackingHandler 注册到机器方块）→ 自动进料
  → 产出再打包寄回
- 效果：**AE2 的 ME 网络订单 → 包裹在工厂里飞 → GT 机器干活 → 包裹寄回网络**

### 方案 B：GT 机器的包裹进料口（GTNEcore UnpackingHandler）

- 给 GTCEu 全部单方块机器注册 `UnpackingHandler`：包裹投到机器面 →
  拆包进输入槽（复用机器的物品 handler）
- 配合 Create 原生 Stock Ticker 请求链即可实现"库存终端下单 → 打包机取料 →
  包裹塞进 GT 机器"，不需要 AE2
- 轻量；AE2 的角色由 Factory Board 替代

### 方案 C：蛙港直连 ME 网络（Package↔ME Bridge）

- 蛙港注册进 AE2 网络作为存储提供方：包裹 = ME 里的一个"压缩物品"
- 收包裹自动解压入库；ME 缺货时自动下单打包
- 等于"包裹是 ME 网络的实体缓存"，物流全走包裹

### 方案 D：GT 配方的 AE2 样板自动生成器

- 扫描 GTRecipeTypes（组装机等）→ 自动生成 AE2 编码样板
- 配合任意方案使用，解决"GT 配方手动写样板太痛苦"的痛点

## 四、技术风险评估

| 风险点 | 评估 |
|---|---|
| ICraftingProvider 注册 | 低——公开接口，挂 IManagedGridNode 即可 |
| 包裹寻址（freq:name） | 低——PackageItem API 公开 |
| UnpackingHandler 注册到 GT 机器 | 中——REGISTRY 按方块注册，GT 机器方块是统一的 KFSBlock/MetaMachineBlock，需确认注册到哪一层 |
| GT 机器自动进料 | 低——NotifiableItemStackHandler 公开 |
| Create 无 AE2 集成先例 | 无现成代码可抄，全部自己写（但两侧 API 都干净） |

## 五、建议

**A + B 组合**：
1. 先做 B（GT 机器收包裹）——Create 原生链路立刻可用，量小
2. 再做 A（AE2 样板转包裹）——打通三方
3. D 作为锦上添花（样板自动生成）

先从 B 开工的风险最低，且做完后即使不做 A 也已经有完整玩法（Stock Ticker → GT 机器）。
