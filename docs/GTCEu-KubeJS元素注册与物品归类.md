# GTCEu KubeJS 元素注册与物品归类（附加页）

> GTCEu 7.5.3（Forge 1.20.1）。本文是《GTCEu-KubeJS开发文档.md》的**附加页**，
> 专讲两件事：① 注册新元素（Element）与新材料（Material）；② 把**已有物品**归类到
> 材料体系（`ItemMaterialData` 静态 API + `Java.loadClass` 直接调用）。
>
> 源码依据：`gtceu-1.20.1-7.5.3` 的 `Material.java`、`Element.java`、
> `ItemMaterialData.java`、`MaterialModificationEventJS.java`（空）、
> `GTRegistryInfo.java`。

---

## 0. 概念链（先搞清层级）

```
元素 Element（质子/中子/符号，如 Fe）
   ↓ Material.Builder.element(...)
材料 Material（铁，可拥有多种"形态"）
   ↓ TagPrefix（锭/粉/块/流体…）
形态物品/流体（gtceu:ingot_iron、gtceu:fluid_iron 等）
   ↓ ItemMaterialData.registerMaterialEntry(...)
已有物品（任意 mod 的物品）→ 声明为某材料的某形态
```

**GTCEu 没有"把已有物品直接替换成材料默认物品"的 API**——归类是"映射"不是"替换"。
想让已有物品成为某材料的形态：材料 Builder 里**不生成**该形态（不调 `.ingot()`），
再手动 `registerMaterialEntry` 补上（见 §4 示例）。

---

## 1. 注册新元素（KJS 原生方式）

注册表键：`gtceu:element`（`GTRegistryInfo.ELEMENT`），Builder 是 `ElementBuilder`：

```js
// startup_scripts/elements.js
GTCEuStartupEvents.registry('gtceu:element', event => {
    event.create('myonium')          // 元素 id（不含命名空间时自动补 gtceu:）
        .protons(119)                // 质子数
        .neutrons(178)               // 中子数
        .halfLifeSeconds(-1)         // 半衰期秒数；-1 = 稳定（默认）
        .decayTo('plumbum')          // 衰变产物元素 id（可选，稳定元素不填）
        .symbol('My')                // 化学符号（显示用）
        .name('Myonium')             // 元素名（显示用）
})
```

`ElementBuilder` 字段（`kjs/builders/ElementBuilder.java`）：`protons`、`neutrons`、
`halfLifeSeconds = -1`、`decayTo`、`symbol`、`isIsotope`。

> 注册后元素存进 `GTElements` 注册表，材料关联时按 id 引用。

---

## 2. 注册新材料并关联元素（KJS 原生方式）

注册表键：`gtceu:material`，桥接的是 Java 的 `Material.Builder`（`Material.java:564`）：

```js
GTCEuStartupEvents.registry('gtceu:material', event => {
    event.create('myonium')
        .element('myonium')          // ← 关联元素：按元素 id（字符串）
        .ingot().dust().fluid()      // 生成形态：锭/粉/流体（各自注册物品）
        .color(0x00ff00)             // 颜色（物品染色）
        .iconSet('metallic')         // 图标集（MaterialIconSet）
        .flags(...)                  // 可选：生成更多形态（GENERATE_*）
        .buildAndRegister()          // 可选：KJS 的 create 一般自动注册
})
```

- `.element()` 注释（`Material.java:1278`）：**每个元素只能有一个材料认领**。
- 形态方法：`ingot()` / `dust()` / `gem()` / `ore()` / `fluid()` / `liquid()` 等；
  更多形态用 `.flags(MaterialFlags.GENERATE_...)` 或 Builder 的对应方法。
- 材料注册后进 `GTMaterials`，`GTMaterials.get('myonium')` 可查。

---

## 3. 已有物品归类：`Java.loadClass` 直接调静态方法（重点）

**`MaterialModificationEventJS` 是空的**——物品归类没有 KJS 封装，只能用
`Java.loadClass` 桥接 Java 静态 API。KubeJS 6（Rhino 引擎）的桥接规则如下。

### 3.1 基础语法

```js
// 加载类（全限定名）
const ItemMaterialData = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData')

// 调用静态方法（参数类型按 Java 签名）
ItemMaterialData.registerMaterialEntry(() => Item.of('minecraft:iron_ingot').item, ...)

// 访问静态字段
ItemMaterialData.ITEM_MATERIAL_ENTRY_COLLECTED    // Map<Item, MaterialEntry>（直接可读）

// 访问枚举 / 常量
TagPrefix.INGOT        // 枚举实例（bindings 里已注入，也可 Java.loadClass 后取）
GTValues.MAX           // 静态 int 常量
```

### 3.2 参数类型对应（Rhino → Java）

| Java 签名 | KJS 写法 |
|---|---|
| `Supplier<? extends ItemLike>` | **箭头函数** `() => Item.of('mod:id').item`（Rhino 自动转 lambda） |
| `TagPrefix` | `TagPrefix.INGOT`（bindings 已注入）或 `Java.loadClass(...).INGOT` |
| `Material` | `GTMaterials.get('myonium')`（bindings 注入） |
| `ItemLike` | `Item.of('mod:id').item` 或 `Block.id` 等 |
| `MaterialEntry` | `new MaterialEntry(TagPrefix.INGOT, GTMaterials.get('myonium'))` |
| `MaterialStack[]` | JS 数组自动转（`[new MaterialStack(material, 144)]`） |

### 3.3 函数式接口桥接（关键）

Java 方法参数是 `Supplier`/`Function`/`Consumer` 时，KJS 里直接传**箭头函数**：

```js
// Supplier<? extends ItemLike> —— 惰性求值，物品在注册后才实例化也安全
ItemMaterialData.registerMaterialEntry(
    () => Item.of('mekanism:ingot_uranium').item,
    TagPrefix.INGOT,
    GTMaterials.get('uranium')
)
```

> **为什么用 Supplier**：GTCEu 在**注册阶段后期**才解析这些 Supplier（`ITEM_MATERIAL_ENTRY`
> 列表在 `ChemicalHelper` 首次查询时收集到 `ITEM_MATERIAL_ENTRY_COLLECTED`）。
> 用 Supplier 可以引用"当时还不存在的物品/材料"而不炸。

### 3.4 完整调用链（元素 → 材料 → 归类）

```js
// ===== startup_scripts/element_myonium.js =====

// ① 注册元素
GTCEuStartupEvents.registry('gtceu:element', event => {
    event.create('myonium').protons(119).neutrons(178).symbol('My').name('Myonium')
})

// ② 注册材料（不生成锭——锭形态用已有物品替代）
GTCEuStartupEvents.registry('gtceu:material', event => {
    event.create('myonium')
        .element('myonium')
        .dust().fluid()              // 只自动生成粉和流体
        .color(0x00ff00)
})

// ③ 已有物品归类（startup 阶段，材料注册完即可调）
const ItemMaterialData = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData')
const { ItemMaterialInfo, MaterialStack } = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo')

// 3a. 声明"我的锭" = myonium 的锭形态（配方、JEI、统一化全识别）
ItemMaterialData.registerMaterialEntry(
    () => Item.of('mekanism:ingot_whatever').item,
    TagPrefix.INGOT,
    GTMaterials.get('myonium')
)

// 3b. 声明"我的粉" 也属于 myonium 粉形态（批量）
ItemMaterialData.registerMaterialEntries(
    [() => Item.of('mekanism:dust_whatever').item],
    TagPrefix.DUST,
    GTMaterials.get('myonium')
)

// 3c. 拆解信息：这个物品拆解后得到 144L myonium（回收配方/材料统计用）
ItemMaterialData.registerMaterialInfo(
    () => Item.of('mekanism:ingot_whatever').item,
    new ItemMaterialInfo([new MaterialStack(GTMaterials.get('myonium'), 144)])
)
```

### 3.5 反查验证

```js
const ChemicalHelper = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper')

// 物品 → (TagPrefix, Material) 对；没归类返回 null
const entry = ChemicalHelper.getMaterialEntry(Item.of('mekanism:ingot_whatever').item)
if (entry) {
    console.log('prefix:', entry.tagPrefix().name, 'material:', entry.material().getName())
}

// 物品 → 材料组成信息
const info = ChemicalHelper.getMaterialInfo(Item.of('mekanism:ingot_whatever').item)

// 材料 → 它的所有形态物品（反向）
const stack = ChemicalHelper.getMaterialStack(GTMaterials.get('myonium'))  // 主形态
```

---

## 4. 常见坑

| 坑 | 说明 / 解法 |
|---|---|
| `class ... not found` | 类名写错或少了包名。`ItemMaterialInfo` 在 `...material.stack` 包（不是 `...material`） |
| 方法参数是 `Supplier` 却传了物品对象 | 必须传 `() => ...` 箭头函数，传对象会类型不匹配（Rhino 有时静默传 undefined → NPE） |
| 元素被两个材料认领 | `Material.Builder.element()` 注释明确"每元素仅一个材料"；重复注册第二个材料会警告/失败 |
| 时机不对 | 全部放 **startup_scripts**（`GTCEuStartupEvents.registry` 或紧随其后）；server_scripts 里材料注册表已冻结 |
| 想"替换"默认形态物品 | 材料 Builder 里不生成该形态（不调 `.ingot()`），再 `registerMaterialEntry` 手动指已有物品 |
| `getClass()` 冲突 | KJS 里别用 `.getClass()` 查类型（Rhino 的 getClass 被劫持），需要类型用 `Java.type(...)` 或直接看文档 |
| 物品 id 错误静默 | 归类不会报错，用 §3.5 反查确认 entry 非 null 才生效 |

---

## 5. 与 GTNEcore Java 侧的选择

| 场景 | 推荐 |
|---|---|
| 一次性原型 / 快速验证 / 玩家可控内容 | KJS（本文 §1-3） |
| 与 GTNEcore 机器/配方/联动同生命周期、要类型检查 | **GTNEcore Java 代码**：`ItemMaterialData.registerMaterialEntry(...)` 在 mod 初始化时调用（API 完全一样） |
| GTM↔Mek 化学统一（批量映射） | Java 侧循环 `registerMaterialEntries`（`Collection<Supplier>` 一次全注册） |

```java
// GTNEcore Java 侧等价写法（参考）
ItemMaterialData.registerMaterialEntries(
        List.of(() -> MekanismItems.INGOT_X),
        TagPrefix.ingot,
        GTMaterials.get("myonium"));
```

---

## 附：相关类速查

| 类 | 用途 |
|---|---|
| `com.gregtechceu.gtceu.api.data.chemical.Element` | 元素（质子/中子/半衰期/符号） |
| `com.gregtechceu.gtceu.api.data.chemical.GTElements` | 元素注册表 |
| `com.gregtechceu.gtceu.api.data.chemical.material.Material`（`Builder` 内部类） | 材料 + 注册 Builder |
| `com.gregtechceu.gtceu.api.data.chemical.material.GTMaterials` | 材料注册表（`get(id)` 查询） |
| `com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData` | **已有物品归类核心 API**（registerMaterialEntry / registerMaterialInfo / registerMaterialEntries） |
| `com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry` | (TagPrefix, Material) 对 |
| `com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo` / `MaterialStack` | 物品材料组成信息 |
| `com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper` | 反查：物品 → 材料条目/信息 |
| `com.gregtechceu.gtceu.integration.kjs.builders.ElementBuilder` | KJS 元素 Builder |
| `com.gregtechceu.gtceu.integration.kjs.events.MaterialModificationEventJS` | **空类**（无物品归类封装，故用 Java.loadClass） |
