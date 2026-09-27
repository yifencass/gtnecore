# GTCEu KubeJS 多方块开发文档（专题详解）

> GTCEu 7.5.3（Forge 1.20.1）多方块机器的完整开发指南。全部 API 来自
> `gtceu-1.20.1-7.5.3_mapped_official_1.20.1.jar` javap 实测。
> 前置阅读：《GTCEu-KubeJS开发文档.md》§3.6；快速复制见《GTCEu-KubeJS快速上手.md》模板 6。

---

## 目录

1. [多方块是什么](#1-多方块是什么)
2. [结构定义：pattern 系统](#2-结构定义pattern-系统)
3. [谓词体系：Predicates 全量](#3-谓词体系predicates-全量)
4. [谓词组合与限制](#4-谓词组合与限制)
5. [结构验证流程](#5-结构验证流程)
6. [控制器注册：MultiblockMachineBuilder 全方法](#6-控制器注册multibloblockmachinebuilder-全方法)
7. [JEI 预览：shapeInfo](#7-jei-预览shapeinfo)
8. [配方修改器：recipeModifiers](#8-配方修改器recipemodifiers)
9. [仓与能力：PartAbility 全量 + autoAbilities](#9-仓与能力partability-全量--autoabilities)
10. [高级主题](#10-高级主题)
11. [巨型/程序化结构](#11-巨型程序化结构)
12. [常见坑（多方块专属）](#12-常见坑多方块专属)
13. [完整示例集](#13-完整示例集)

---

## 1. 多方块是什么

多方块 = 一个**控制器方块**（放置后激活结构）+ 周围按**结构定义**（pattern）摆放的
普通方块/仓/配件。工作流程：

```
放置控制器 → 玩家右键 → 按 pattern 校验周围方块 → 校验通过 → 激活（多块结构）
                                                    └→ 校验失败 → 提示缺什么
```

KJS 注册多方块 = 两件事：
1. `pattern(...)`：定义结构（字母表 + 每层字符串）
2. `shapeInfo(...)`：定义 JEI 预览（可选但强烈推荐）

---

## 2. 结构定义：pattern 系统

### 2.1 最小结构

```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_controller', 'multiblock')
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('CCC', 'CKC', 'CCC')     // 每层一行字符串，从下往上
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
            .build())
})
```

**机制**：
- `aisle(String...)` 每行参数 = 结构的一层（z 轴），字符串的每个字符 = 一个方块位置
- 每层必须是**等宽矩形**（字符串等长、层数一致），否则 build 报错
- `where(char, predicate)` 把字符映射到"匹配规则"
- `build()` 返回 `BlockPattern`——**第一行执行 `checkMissingPredicates()`**：
  aisle 中出现但未 where 的字符（空格除外）→ 抛异常

### 2.2 字母表约定

| 字符 | 含义 | 写法 |
|---|---|---|
| `' '`（空格） | 空气 | 不用 where（GT 系默认） |
| 任意显式字符 | 空气（显式） | `where('.', Predicates.air())` |
| `'K'` | 控制器（惯例） | `where('K', Predicates.controller(...))` |
| `'C'` | 外壳/任意指定方块 | `where('C', Predicates.blocks(...))` |
| `'A'` | 任意方块（含空气） | `where('A', Predicates.any())` |
| `'M'/'S'/'E'/'I'/'O'` | 仓位（惯例） | `where('M', Predicates.abilities(PartAbility.MAINTENANCE))` 等 |

> 字符本身无强制含义——`where` 映射决定一切。惯例字母只为可读性。

### 2.3 层方向与多向结构

```js
// 默认方向：X=右、Y=上、Z=前（控制器面朝北）
FactoryBlockPattern.start(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT)
```
- `start()` 无参 = 默认三轴方向
- 机器 `rotationState(RotationState.NON_Y_AXIS)` 允许水平旋转 → pattern 自动按朝向旋转匹配

### 2.4 重复层：aisleRepeatable

```js
.pattern(definition => FactoryBlockPattern.start()
    .aisleRepeatable(2, 10, 'CCC', 'GGG', 'CCC')  // 2~10 层重复（烟囱/炉体）
    .aisle('CKC', 'GGG', 'CSC')                    // 顶部固定层
    .where('K', Predicates.controller(Predicates.blocks(definition.get())))
    .where('S', Predicates.abilities(PartAbility.MUFFLER))
    .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
    .where('G', Predicates.blocks('minecraft:glass'))
    .build())
```
- `aisleRepeatable(min, max, ...)`：该层组重复 min~max 次（玩家放置任意高度都识别）
- `setRepeatable(int)` / `setRepeatable(int, int)`：作用于**当前最后一行**（等价）

---

## 3. 谓词体系：Predicates 全量

（`com.gregtechceu.gtceu.api.pattern.Predicates`，全部静态方法，javap 实测）

### 3.1 方块匹配

| 方法 | 匹配 | 示例 |
|---|---|---|
| `blocks(Block...)` | 指定方块 | `Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())` |
| `blocks(IMachineBlock...)` | 机器方块 | `Predicates.blocks(GTMachines.ENERGY_INPUT_HATCH[1])` |
| `states(BlockState...)` | 精确状态 | `Predicates.states(Block.getBlock('minecraft:stone').defaultBlockState())` |
| `machines(MachineDefinition...)` | 机器定义 | `Predicates.machines(GTMachines.MAINTENANCE_HATCH)` |
| `blockTag(TagKey<Block>)` | 方块标签 | `Predicates.blockTag(TagKey.create(Registries.BLOCK, new ResourceLocation('forge:glass')))` |
| `frames(Material...)` | 框架材料 | `Predicates.frames(GTMaterials.Steel)` |

### 3.2 仓/能力匹配

| 方法 | 匹配 |
|---|---|
| `abilities(PartAbility...)` | 具备任一能力的仓 |
| `ability(PartAbility, int...)` | 指定能力的指定槽数 |
| `autoAbilities(GTRecipeType...)` | **自动匹配该配方类型所需全部仓**（输入/输出/能源/维护/消声） |
| `autoAbilities(boolean items, boolean fluids, boolean energy)` | 手动指定启用哪类自动仓 |
| `autoAbilities(recipeTypes, boolean, boolean, boolean, boolean, boolean, boolean)` | 完整 6 参版 |
| `heatingCoils()` | 任意加热线圈（自动按温度分级） |
| `cleanroomFilters()` | 洁净室过滤器 |
| `powerSubstationBatteries()` | 变电站电池 |
| `dataHatchPredicate(TraceabilityPredicate)` | 数据仓 |

### 3.3 特殊

| 方法 | 匹配 |
|---|---|
| `controller(TraceabilityPredicate)` | 标记控制器位置（**必须**且唯一） |
| `any()` | 任意方块（含空气） |
| `air()` | 仅空气 |
| `custom(Predicate<MultiblockState>, Supplier<BlockInfo[]>)` | 完全自定义（状态判断 + JEI 展示方块） |
| `fluids(Fluid...)` / `fluidTag(TagKey<Fluid>)` | 流体方块 |
| `lamps(BlockEntry...)` / `anyLamp()` / `lampsByColor(DyeColor)` | 灯 |

---

## 4. 谓词组合与限制

### 4.1 组合（⚠️ 版本差异）

```java
// 7.5.3 实测：TraceabilityPredicate 只有 or()，**没有 and()**！
// 官方文档的 .and(...) 写法在 7.5.3 会报 "Cannot find function and"
Predicates.blocks(A).or(Predicates.blocks(B))   // ✅ 或
```

"且"需求（如"外壳上自动找仓"）的 7.5.3 替代：
```js
// 方案 A：自定义谓词组合逻辑
.where('C', Predicates.custom(state => {
    // state.getBlockState() 判断方块 + 仓检查
    return true
}, () => [BlockInfo.create(Block.getBlock('gtceu:steel_machine_casing').defaultBlockState())]))
// 方案 B：分字符——外壳字符和仓字符分开 where（结构上错开）
```

### 4.2 数量限制（set*Limited 系列）

```js
.where('H', Predicates.abilities(PartAbility.INPUT_ENERGY)
    .setMaxGlobalLimited(2))        // 全结构最多 2 个
// setMinGlobalLimited(n)      —— 最少 n 个
// setMinGlobalLimited(n, min?) —— 带第二参数 = 提示文本可空
// setMaxGlobalLimited(n)      —— 最多 n 个
// setExactLimit(n)            —— 恰好 n 个
// setMinLayerLimited(n)       —— 每层最少 n 个
// setMaxLayerLimited(n)       —— 每层最多 n 个
// 另有 addTooltips(Component...)、disableRenderFormed()、sort()、setController()
```

---

## 5. 结构验证流程

```
build() → checkMissingPredicates()      ← 缺失 where 映射的字符 → IllegalStateException（启动即报）
       → BlockPattern（3D predicate 数组）

玩家右键控制器 → MultiblockState 匹配：
  1. 按方向展开 structure（含 repeatable 展开）
  2. 逐位置跑谓词（any/air/block 快速路径）
  3. 全部通过 → 激活；仓注册进多方块（自动接管输入输出）
```

关键行为：
- **匹配是一次性的**（激活时），不是每 tick——巨型结构耗时不卡服
- 匹配失败时**不报错**，控制器只是不激活（工具提示显示缺什么由 `shapeInfo`/默认 UI 决定）
- `Predicates.any()` 匹配空气——"不在乎"位置用 any 而不是 blocks

---

## 6. 控制器注册：MultiblockMachineBuilder 全方法

`event.create(id, 'multiblock')` 返回 KJS 包装（KJSWrappingMultiblockBuilder → 原生
`MultiblockMachineBuilder`）。全方法（javap 实测，除 pattern 外）：

| 方法 | 用途 |
|---|---|
| `pattern(Function<definition, BlockPattern>)` | 结构定义（§2） |
| `shapeInfo(Function<definition, MultiblockShapeInfo>)` | JEI 预览（§7） |
| `shapeInfos(Function<definition, List<MultiblockShapeInfo>>)` | 多预览（多档/多形态） |
| `recipeTypes([...])` / `recipeType('...')` | 配方类型（MachineBuilder 继承） |
| `appearanceBlock(Block)` | 激活后的外观方块（通常=外壳） |
| `rotationState(RotationState)` | 旋转自由度（`NON_Y_AXIS` = 水平 4 向） |
| `recipeModifiers([...])` | 配方修改器（§8） |
| `generator(boolean)` | 发电机多方块（输出能量） |
| `allowFlip(boolean)` | 允许镜像翻转结构 |
| `partSorter(Comparator)` | 仓排序（默认按注册顺序） |
| `partAppearance((controller, part, dir) -> BlockState)` | 仓外观覆盖（伪装成外壳） |
| `additionalDisplay((controller, List<Component>) -> ...)` | 控制器 UI 附加信息行 |
| `recoveryItems(Supplier<ItemLike[]>)` | 拆卸回收物品 |
| `recoveryStacks(Supplier<ItemStack[]>)` | 拆卸回收（带 NBT） |
| `tiered(boolean)` / 档位相关 | 'tiered_multiblock' 类型用 |

### 档位多方块（'tiered_multiblock'）

```js
event.create('my_tiered', 'tiered_multiblock')
    .tiers([GTValues.LV, GTValues.MV, GTValues.HV])
    .machine((be, tier) => new MyTieredMachine(be, tier))   // TieredCreationFunction
    .definition((tier, builder) => {
        builder.recipeTypes([...])
    })
```

---

## 7. JEI 预览：shapeInfo

```js
.shapeInfo(controller => MultiblockShapeInfo.builder()
    .aisle('eCe', 'GGG', 'CCC')        // 注意：预览字符与 pattern 字符**独立**
    .aisle('CCC', 'GDG', 'CSC')
    .aisle('iKo', 'GGG', 'CMC')
    .where('K', controller, Direction.SOUTH)          // 控制器（定义 + 朝向）
    .where('C', GTBlocks.CASING_STEEL_SOLID.get())
    .where('G', Block.getBlock('minecraft:glass'))
    .where('D', GTBlocks.COIL_CUPRONICKEL.get())
    .where('S', GTMachines.MUFFLER_HATCH[1], Direction.UP)
    .where('M', GTMachines.MAINTENANCE_HATCH, Direction.SOUTH)
    .where('e', GTMachines.ENERGY_INPUT_HATCH[1], Direction.NORTH)
    .where('i', GTMachines.ITEM_IMPORT_BUS[1], Direction.SOUTH)
    .where('o', GTMachines.ITEM_EXPORT_BUS[1], Direction.SOUTH)
    .build())
```

**ShapeInfoBuilder 方法**（javap 实测）：
- `aisle(String...)` —— 每层（预览自由绘制，不必与 pattern 一致，但建议一致便于核对）
- `where(char, BlockState)` / `where(char, Block)` / `where(char, Supplier<Block>)`
- `where(char, IMachineBlock, Direction)` / `where(char, Supplier<IMachineBlock>, Direction)` —— 机器方块带朝向
- `build()` → MultiblockShapeInfo

---

## 8. 配方修改器：recipeModifiers

```js
.recipeModifiers([
    GTRecipeModifiers.PARALLEL_HATCH,                                        // 并行仓（并多少配方）
    GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(GTRecipeModifiers.PERFECT_OVERCLOCK),  // 完美超频
])
```

**GTRecipeModifiers 内置**（javap 实测全量）：
- `SIMPLE_STEAM` / `SIMPLE_GENERATOR` / `STEAM_BOILER`
- `LARGE_BOILER` / `LARGE_TURBINE` / `LARGE_COMBUSTION_ENGINE`
- `STEAM_PARALLEL_MULTIBLOCK` / `FUSION_REACTOR`
- `PARALLEL_HATCH` / `PERFECT_OVERCLOCK` / `ELECTRIC_OVERCLOCK`（.apply 组合）
- `MachineModifiers`（KJS helpers）同源：`LARGE_TURBINE` 等 8 个

自定义修改器：`ModifierFunction`（绑定）+ `Predicates.custom` 组合；复杂逻辑走 Java addon。

---

## 9. 仓与能力：PartAbility 全量 + autoAbilities

### 9.1 PartAbility 全量（javap 实测）

| 组 | 能力 |
|---|---|
| 物品 | `IMPORT_ITEMS` `EXPORT_ITEMS` |
| 流体 | `IMPORT_FLUIDS` `EXPORT_FLUIDS` `IMPORT_FLUIDS_1X/4X/9X` `EXPORT_FLUIDS_1X/4X/9X` `PUMP_FLUID_HATCH` `TANK_VALVE` |
| 能量 | `INPUT_ENERGY` `OUTPUT_ENERGY` `SUBSTATION_INPUT_ENERGY` `SUBSTATION_OUTPUT_ENERGY` `INPUT_LASER` `OUTPUT_LASER` |
| 维护 | `MAINTENANCE` `MUFFLER` |
| 数据 | `DATA_ACCESS` `COMPUTATION_DATA_RECEPTION` `COMPUTATION_DATA_TRANSMISSION` `OPTICAL_DATA_RECEPTION` `OPTICAL_DATA_TRANSMISSION` `HPCA_COMPONENT` |
| 特殊 | `ROTOR_HOLDER` `STEAM` `STEAM_IMPORT_ITEMS` `STEAM_EXPORT_ITEMS` `PARALLEL_HATCH` `PASSTHROUGH_HATCH` `OBJECT_HOLDER` |

### 9.2 autoAbilities 的三个参数版

```js
// autoAbilities(boolean items, boolean fluids, boolean energy)
.where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))  // 注意 7.5.3 无 .and()
// 分开写：外壳字符 + 仓字符
.where('I', Predicates.autoAbilities(true, true, true))  // 输入输出物品+流体+能源 全自动
```
完整 6 参版：`autoAbilities(recipeTypes, items, fluids, energy, maintenance, muffler, ...)`（boolean 顺序按 javap 参数表）。

> ⚠️ `autoAbilities` 会扫描匹配区域找仓——巨型结构建议限定外壳字符 + 手动 `ability(PartAbility, slotCount)` 精确控制。

---

## 10. 高级主题

### 10.1 appearanceBlock：激活后外观

```js
.appearanceBlock(GTBlocks.CASING_STEEL_SOLID.get())
// 激活后控制器和仓在视觉上伪装成该方块（如化工厂控制器看着像钢壳）
```

### 10.2 allowFlip：镜像结构

```js
.allowFlip(true)  // 结构允许左右镜像（pattern 定义一侧即可）
```

### 10.3 partAppearance：仓伪装

```js
.partAppearance((controller, part, dir) => GTBlocks.CASING_STEEL_SOLID.get().defaultBlockState())
```

### 10.4 additionalDisplay：UI 附加信息

```js
.additionalDisplay((controller, lines) => {
    lines.add(Component.literal('我的多方块'))
})
```

### 10.5 维护机惩罚

多方块带 `MAINTENANCE` 仓 → 自动启用维护机制：维护度下降 → 配方时长惩罚
（`IMaintenanceMachine.getDurationMultiplier`），玩家用扳手维护。

### 10.6 加热线圈（EBF/合金高炉风格）

```js
.where('H', Predicates.heatingCoils())   // 任意线圈，按温度分级决定可炼配方
// 配方侧：.blastFurnaceTemp(1200) 要求线圈温度 ≥ 1200
```

---

## 11. 巨型/程序化结构

50×50×30 每层不重样 + 空气：**JS 生成字符串**（字母由代码产出，零手敲零拼错）：

```js
function layer(h, fn) {
    return Array.from({length: 50}, (_, r) =>
        Array.from({length: 50}, (_, c) => fn(r, c, h)).join(''))
}

.pattern(definition => {
    const p = FactoryBlockPattern.start()
    for (let h = 0; h < 30; h++) {
        p.aisle(...layer(h, (r, c, y) => {
            if (r === 0 || r === 49 || c === 0 || c === 49) return 'C'   // 四壁
            if (r === 25 && c === 25) return 'K'                         // 控制器柱
            if ((r + c + y) % 10 === 0) return 'D'                       // 装饰
            return ' '                                                   // 空气（空格即可）
        }))
    }
    return p
        .where('K', Predicates.controller(Predicates.blocks(definition.get())))
        .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
        .where('D', Predicates.blocks(GTBlocks.COIL_CUPRONICKEL.get()))
        .build()
})
```

**空气**：空格 `' '` 默认空气；怕误认用 `'.'` + `where('.', Predicates.air())`；
"中间是什么不在乎"用 `Predicates.any()`。

**性能**：pattern 匹配一次性（激活时），数万方块耗时可接受；巨型结构避免
`autoAbilities` 全壳扫描，用 `ability()` 精确限位。

---

## 12. 常见坑（多方块专属）

| 坑 | 现象 | 解法 |
|---|---|---|
| **`and()` 不存在**（7.5.3） | 官方示例 `.and(...)` 报 Cannot find function | 用 `or()` 或 `Predicates.custom(...)`；外壳+仓分字符 |
| 打错字母 | build 时 `checkMissingPredicates` 抛异常 | 日志直接指明字符 |
| 控制器不激活 | 结构差一个方块/方向不对 | 检查 `rotationState`、字母表、JEI 预览核对 |
| 层宽不一致 | build 报错 | 每层字符串等长 |
| `shapeInfo` 与 pattern 不一致 | 玩家照预览摆却激活不了 | 预览字符与 pattern 字符对齐（建议同一套） |
| 仓不工作 | 没写对应 `abilities` | 补 `Predicates.abilities(PartAbility.XXX)` |
| 多控制器 | 两个控制器都激活异常 | `controller()` 谓词全结构唯一 |
| 巨型结构卡 | `autoAbilities` 扫全壳 | 用 `ability()`/`blocks()` 限定区域 |
| 官方文档方法不存在 | 版本差异 | 以本机 jar javap 为准 |

---

## 13. 完整示例集

### 13.1 简单 3×3 炉（入门）

```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_furnace_controller', 'multiblock')
        .rotationState(RotationState.NON_Y_AXIS)
        .recipeTypes(['my_furnace_recipe'])
        .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('CCC', 'CCC', 'CCC')
            .aisle('CKC', 'C.C', 'CSC')
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
            .where('S', Predicates.abilities(PartAbility.MUFFLER))
            .where('.', Predicates.air())
            .build())
        .shapeInfo(controller => MultiblockShapeInfo.builder()
            .aisle('CCC', 'CCC', 'CCC')
            .aisle('CKC', 'C.C', 'CSC')
            .where('K', controller, Direction.SOUTH)
            .where('C', GTBlocks.CASING_STEEL_SOLID.get())
            .where('S', GTMachines.MUFFLER_HATCH[1], Direction.UP)
            .build())
})
```

### 13.2 自动仓多方块（autoAbilities）

```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_auto_controller', 'multiblock')
        .recipeTypes(['my_recipe'])
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('CCC', 'CAC', 'CCC')
            .aisle('CKC', 'CAC', 'CSC')
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
            // 仓位：自动匹配物品/流体/能源仓（7.5.3 无 .and，仓位用独立字符）
            .where('A', Predicates.autoAbilities(true, true, true)
                .setMaxGlobalLimited(4))
            .where('S', Predicates.abilities(PartAbility.MUFFLER))
            .build())
})
```

### 13.3 可变高度烟囱（aisleRepeatable）

```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_chimney', 'multiblock')
        .recipeTypes(['my_recipe'])
        .pattern(definition => FactoryBlockPattern.start()
            .aisleRepeatable(2, 12, 'CCC', 'C.C', 'CCC')   // 烟囱身 2~12 层
            .aisle('CKC', 'CSC', 'CCC')                     // 底部
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('S', Predicates.abilities(PartAbility.MUFFLER))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
            .where('.', Predicates.air())
            .build())
})
```

### 13.4 线圈加热多方块（EBF 风格）

```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_ebf', 'multiblock')
        .recipeTypes(['electric_blast_furnace'])
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('HHH', 'HHH', 'HHH')
            .aisle('HCH', 'H.H', 'HSH')
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('H', Predicates.heatingCoils())            // 任意线圈
            .where('S', Predicates.abilities(PartAbility.MUFFLER))
            .where('.', Predicates.air())
            .build())
        .shapeInfo(controller => MultiblockShapeInfo.builder()
            .aisle('HHH', 'HHH', 'HHH')
            .aisle('HCH', 'H.H', 'HSH')
            .where('K', controller, Direction.SOUTH)
            .where('H', GTBlocks.COIL_CUPRONICKEL.get())
            .where('S', GTMachines.MUFFLER_HATCH[1], Direction.UP)
            .build())
})
```

### 13.5 档位多方块

```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_tiered_multi', 'tiered_multiblock')
        .tiers([GTValues.LV, GTValues.MV, GTValues.HV])
        .definition((tier, builder) => {
            builder.recipeTypes(['my_recipe'])
            builder.appearanceBlock(GTBlocks.CASING_STEEL_SOLID.get())
        })
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('CCC', 'CKC', 'CCC')
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
            .build())
})
```
