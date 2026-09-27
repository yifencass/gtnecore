# GTCEu KubeJS 开发文档（超级详细版）

> GTCEu 7.5.3（Forge 1.20.1）的 KubeJS 支持全 API 参考。所有内容来自对
> `gtceu-1.20.1-7.5.3_mapped_official_1.20.1.jar` 的 javap 实测 + 官方文档交叉验证。
> 快速上手看同目录《GTCEu-KubeJS快速上手.md》。

---

## 目录

1. [环境与加载机制](#1-环境与加载机制)
2. [事件体系（GTCEuStartupEvents / GTCEuServerEvents）](#2-事件体系)
3. [注册体系（GTRegistryInfo + 全部 Builder）](#3-注册体系)
4. [配方体系（组件 / Schema / GTRecipeJS）](#4-配方体系)
5. [世界生成（矿脉 / 基岩矿 / 流体脉）](#5-世界生成)
6. [CraftingComponents 与披风](#6-craftingcomponents-与披风)
7. [绑定清单（62 个 bindings）](#7-绑定清单)
8. [配方组件注册名（20 个）](#8-配方组件注册名)
9. [常见坑](#9-常见坑)
10. [完整示例集](#10-完整示例集)
11. [附加页：元素注册与物品归类](#11-附加页元素注册与物品归类)

---

## 1. 环境与加载机制

| 项 | 值 |
|---|---|
| 模组 | GregTech Modern（GTCEu）7.5.3 |
| Minecraft / Forge | 1.20.1 / 47.x |
| KubeJS | 6.x（`kubejs-forge-1.20.1`） |
| 核心类 | `com.gregtechceu.gtceu.integration.kjs.GregTechKubeJSPlugin` |

**GTCEu 的 KJS 插件**（`GregTechKubeJSPlugin`，56KB）在加载时做四件事：
1. `registerBindings` —— 注入 **62 个绑定**（§7）
2. `registerRecipeComponents` —— 注册 **20 个配方组件名**（§8）
3. `registerRecipeSchemas` —— 把每个 `GTRecipeType` 的 schema 注册到 `event.recipes.gtceu.<类型>`
4. `registerEvents` —— 注册 `GTCEuStartupEvents` / `GTCEuServerEvents` 事件组

```groovy
// build.gradle 依赖
implementation fg.deobf("dev.latvian.mods.kubejs:kubejs-forge-1.20.1:6.x")
```

脚本目录：`startup_scripts/`（注册类，重启生效） + `server_scripts/`（玩法类，F3+T 可重载）。

---

## 2. 事件体系

### 2.1 GTCEuStartupEvents（startup 阶段）

| 事件 | 事件类 | 用途 |
|---|---|---|
| `registry` | `GTRegistryEventJS` | 注册材料/机器/配方类型等（§3） |
| `materialModification` | `MaterialModificationEventJS` | 材料全部注册完后修改已有材料 |
| `craftingComponents` | `CraftingComponentsEventJS` | 修改/新增配方组件（§6） |

### 2.2 GTCEuServerEvents（server 阶段）

| 事件 | 事件类 | 用途 |
|---|---|---|
| `oreVeins` | `GTOreVeinEventJS` | 添加/修改/删除矿脉 |
| `bedrockOreVeins` | `GTBedrockOreVeinEventJS` | 基岩矿脉（需 config doBedrockOres） |
| `fluidVeins` | `GTFluidVeinEventJS` | 基岩流体矿脉 |
| `registerCapes` | `RegisterCapesEventJS` | 披风注册 |

> ⚠️ 事件类完整方法签名见后续章节；本节为索引。

---

## 3. 注册体系

### 3.1 GTRegistryEventJS：注册表总览

`GTCEuStartupEvents.registry('<注册表id>', event => { event.create(id[, type]) })`

**注册表 id 全清单**（`GTRegistryInfo` 静态常量，javap 确认）：

| 注册表 id | 默认类型 | 可用类型（`create(id, type)` 第二参） |
|---|---|---|
| `gtceu:element` | `basic`（ElementBuilder） | — |
| `gtceu:material` | `basic`（Material$Builder） | — |
| `gtceu:recipe_type` | `basic`（GTRecipeTypeBuilder） | — |
| `gtceu:recipe_category` | `basic`（GTRecipeCategoryBuilder） | — |
| `gtceu:material_icon_set` | `basic` | — |
| `gtceu:material_icon_type` | `basic` | — |
| `gtceu:world_gen_layer` | `basic`（WorldGenLayerBuilder） | — |
| `gtceu:tag_prefix` | `basic`（BasicTagPrefixBuilder） | `basic` / `ore`（OreTagPrefixBuilder） |
| `gtceu:machine` | `simple`（KJSWrappingMachineBuilder） | `simple` / `custom` / `generator`（→KJSWrappingMachineBuilder）、`steam`（KJSSteamMachineBuilder）、`multiblock` / `primitive`（→原生 MultiblockMachineBuilder）、`tiered_multiblock`（KJSWrappingMultiblockBuilder） |
| `gtceu:dimension_marker` | `basic` | — |

**KJS 方块注册表扩展**（`StartupEvents.registry('block')` 的类型）：

| 类型 | Builder | 用途 |
|---|---|---|
| `gtceu:active` | ActiveBlockBuilder | 多方块运行状态切换模型 |
| `gtceu:coil` | CoilBlockBuilder | 加热线圈（EBF/合金高炉等） |

### 3.2 元素（ElementBuilder）

```js
GTCEuStartupEvents.registry('gtceu:element', event => {
   event.create('test_element')
        .protons(27).neutrons(177)        // 非元素用 -1
        .halfLifeSeconds(-1).decayTo(null) // 不衰变
        .symbol('test').isIsotope(false)
        .translatableName(Component.literal('Test Element'))
})
```
全方法：`protons(long)` `neutrons(long)` `halfLifeSeconds(long)` `decayTo(String)` `symbol(String)` `isIsotope(boolean)` `translatableName(Component)`。

### 3.3 材料（MaterialBuilder）

```js
GTCEuStartupEvents.registry('gtceu:material', event => {
    event.create('andesite_alloy')
        .ingot()                              // .ingot(2) 采掘等级 / .ingot(2, 2000) +燃料时长
        .components('1x andesite', '1x iron') // 'Kx material_name'
        .color(0x839689).iconSet(GTMaterialIconSet.DULL)
        .flags(GTMaterialFlags.GENERATE_PLATE, GTMaterialFlags.GENERATE_GEAR)

    event.create('mysterious_dust').dust().color(0x7D2DDB)
    event.create('purple_coal')
        .gem(2, 4000).element(GTElements.C)
        .ore(2, 3)                            // (矿乘数, 副产物乘数[, 发光])
        .color(0x7D2DDB).iconSet(GTMaterialIconSet.LIGNITE)
    event.create('mysterious_ooze')
        .fluid()                              // .liquid(温度K) / .gas() / .plasma()
        .color(0x500bbf)
})
```

### 3.4 修改已有材料（MaterialModificationEventJS）

```js
GTCEuStartupEvents.materialModification(event => {
    GTMaterials.BismuthBronze.setMaterialARGB(0x82AD92)
})
// 或 registry 事件内改属性（属性类需 Java.loadClass）
const $IngotProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.IngotProperty');
GTCEuStartupEvents.registry('gtceu:material', event => {
    GTMaterials.Zirconium.setProperty(PropertyKey.INGOT, new $IngotProperty());
    GTMaterials.RedAlloy.removeProperty(PropertyKey.WIRE);          // 先删后设
    GTMaterials.Lead.addFlags(GTMaterialFlags.GENERATE_GEAR);
})
```

### 3.5 配方类型（GTRecipeTypeBuilder）

```js
GTCEuStartupEvents.registry('gtceu:recipe_type', event => {
    event.create('test_recipe_type')
        .category('test')
        .setEUIO('in')                                            // 'in' 耗电 / 'out' 发电
        .setMaxIOSize(3, 3, 3, 3)                                 // 物入/物出/流入/流出
        .setItemSlotOverlay(IO.IN, 0, GTGuiTextures.SOLIDIFIER_OVERLAY)
        .setProgressBar(GTGuiTextures.PROGRESS_ARROW)
        .setSound(GTSoundEntries.COOLING)
        .addRecipeInfo(recipe => Text.translate('test_recipe_type.info'))
})
// 全量：category / setEUIO(IO) / setMaxIOSize / setMaxSize(IO, cap, n) /
//   setSlotOverlay(isOutput, isFluid, IGuiTexture) / setProgressBar(ResourceTexture, FillDirection) /
//   setSteamProgressBar(SteamTexture, FillDirection) / setSound / setHasResearchSlot /
//   setMaxTooltips / setSmallRecipeMap(GTRecipeType) / setIconSupplier / setUiBuilder
```

**GTRecipeCategoryBuilder**（registry('gtceu:recipe_category')）：`recipeType(GTRecipeType)` `icon(IGuiTexture)` `isXEIVisible(boolean)` `setItemIcon(ItemStack...)` `setCustomIcon(RL)` `langValue(String)`

### 3.6 机器（多类型 builder）

```js
// 蒸汽机
event.create('test_simple_steam_machine', 'steam', true)          // (id, 类型, 有高压变体)
// 电动单块
event.create('test_electric', 'simple', 0, GTValues.LV, GTValues.MV, GTValues.HV)
    .rotationState(RotationState.NON_Y_AXIS)
    .recipeType('test_recipe_type')
    .tankScalingFunction(tier => tier * 3200)
// 发电机
event.create('test_generator', 'generator', GTValues.LV, GTValues.MV)
    .recipeType('test_generator_recipe_type')
// 多方块
event.create('test_controller', 'multiblock')
    .tooltips(Component.translatable('your.langfile.entry.here'))
    .rotationState(RotationState.NON_Y_AXIS)
    .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
    .recipeTypes(['test_recipe_type_1', 'test_recipe_type_2'])
    .recipeModifiers([GTRecipeModifiers.PARALLEL_HATCH,
                      GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(GTRecipeModifiers.PERFECT_OVERCLOCK)])
    .pattern(definition => FactoryBlockPattern.start()
        .aisle('CCC', 'GGG', 'CCC')
        .aisle('CCC', 'GDG', 'CSC')
        .aisle('CKC', 'GGG', 'CMC')
        .where('K', Predicates.controller(Predicates.blocks(definition.get())))
        .where('M', Predicates.abilities(PartAbility.MAINTENANCE))
        .where('S', Predicates.abilities(PartAbility.MUFFLER))
        .where('D', Predicates.blocks(GTBlocks.COIL_CUPRONICKEL.get()))
        .where('G', Predicates.blocks('minecraft:glass'))
        .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
        .build())
    .shapeInfo(controller => MultiblockShapeInfo.builder()
        .aisle('eCe', 'GGG', 'CCC')
        .where('K', controller, Direction.SOUTH)
        .where('C', GTBlocks.CASING_STEEL_SOLID.get())
        .where('G', Block.getBlock('minecraft:glass'))
        .build())
})
```

**机器类型与 builder 回调**（javap 确认）：
- `KJSTieredMachineBuilder`（档位机基类）：`tiers(int[])` `machine(TieredCreationFunction)` `definition(DefinitionFunction)` `tankScalingFunction(Int2IntFunction)` `addDefaultTooltips(boolean)` `addDefaultModel(boolean)` `isGenerator(boolean)`
  - `TieredCreationFunction`：`MC create(IMachineBlockEntity, int tier, Int2IntFunction tankScaling)`
  - `DefinitionFunction`：`void apply(int tier, MachineBuilder<?,?>)`
- `KJSTieredMultiblockBuilder`（档位多方块）：`machine(TieredCreationFunction)` → `MultiblockControllerMachine create(IMachineBlockEntity, int tier)`
- `KJSSteamMachineBuilder`（'steam'）：`hasLowPressure(boolean)` `hasHighPressure(boolean)` `machine(SteamCreationFunction)`（`create(IMachineBlockEntity, boolean isHighPressure)`）
- `KJSWrappingMachineBuilder`（'simple'/'custom'/'generator'）：转发 `tiers/machine/definition/tankScalingFunction/...`
- `KJSWrappingMultiblockBuilder`（'tiered_multiblock'）：`tiers(int...)` + 静态 `createKJSMulti(RL)` 直接拿原生 `MultiblockMachineBuilder`
- `MachineConstructors`：`createLargeCombustionEngine/createLargeTurbine/createFusionReactor/createSteamMultiblock`
- `MachineModifiers`：`SIMPLE_STEAM/SIMPLE_GENERATOR/STEAM_BOILER/LARGE_BOILER/LARGE_TURBINE/LARGE_COMBUSTION_ENGINE/STEAM_PARALLEL_MULTIBLOCK/FUSION_REACTOR`

**pattern 简化技巧**（FactoryBlockPattern 全方法）：
- **空气**：默认 `' '` 空格 = 空气（免 where）；怕误认可用显式字符：
  `where('.', Predicates.air())`（任意字符皆可）；宽松匹配用 `Predicates.any()`
- `aisleRepeatable(min, max, String...)` —— **可重复层**（可变高度/长度，如烟囱/深炉体），
  省去手敲 N 层；`setRepeatable(int[, int])` 等价（作用于当前层）
- `Predicates.autoAbilities(recipeTypes)` —— 外壳自动匹配输入/输出/能源仓等（免逐个 where）
- `Predicates.any()` —— 任意方块
- `FactoryBlockPattern.start(RelativeDirection, RelativeDirection, RelativeDirection)` —— 定制朝向
- 官方机器的 pattern 不暴露 API，可打开 GTCEu 源码（如 `LargeChemicalReactor`）抄 aisle 字符串
- **巨型/每层不同的结构：用 JS 生成 aisle 字符串**（KJS 是 JavaScript）：
  ```js
  function layer(h, fn) {  // 生成一层：50 行 × 50 字符
      return Array.from({length: 50}, (_, r) =>
          Array.from({length: 50}, (_, c) => fn(r, c, h)).join(''))
  }
  // pattern 里循环 30 层：p.aisle(...layer(h, (r,c,y) => ...公式...))
  // 字母由代码产出，零手敲、零拼错
  ```
- **打错字母不会静默失败**：`build()` 第一行执行 `checkMissingPredicates()`——
  任何未 `where` 映射的字符直接抛异常（日志指出字符），配合
  `shapeInfo(...)` 的 JEI 预览核对结构
- ⚠️ 巨型结构（数万方块）pattern 匹配为一次性结构校验（加入多方块时），
  内存/耗时可接受；`autoAbilities` 会扫描整个外壳，巨型结构建议改用
  `Predicates.blocks(...)` 限定区域

### 3.7 方块（active / coil 扩展类型）

```js
StartupEvents.registry('block', event => {
    event.create('custom_engine_intake_casing', 'gtceu:active')
        .simple('kubejs:block/casings/custom_engine_intake')   // 激活纹理加 _active 后缀
    event.create('custom_firebox', 'gtceu:active')
        .firebox('gtceu:block/casings/solid/machine_casing_bronze_plated_bricks',
                 'gtceu:block/casings/firebox/machine_casing_firebox_bronze',
                 'gtceu:block/casings/solid/machine_casing_bronze_plated_bricks')
    event.create('custom_bloom', 'gtceu:active')
        .bloom('gtceu:block/casings/fusion/superconducting_coil')  // 发光纹理加 _bloom 后缀

    event.create('infinity_coil_block', 'gtceu:coil')
        .temperature(100).level(0).energyDiscount(1).tier(10)
        .coilMaterial(() => GTMaterials.get('infinity'))
        .texture('kubejs:block/example_block')
        .hardness(5).requiresTool(true).material('metal')
})
```

**ActiveBlockBuilder**（'gtceu:active'）内部类型：`SIMPLE`（`.simple(modelPath)`，激活纹理加 `_active` 后缀）/ `BLOOM`（`.bloom(modelPath)`，发光纹理加 `_bloom`）/ `FIREBOX`（`.firebox(modelPath, framePath, texture)`）/ `CUSTOM`
**CoilBlockBuilder**（'gtceu:coil'）：`coilMaterial(Supplier<Material>)` `temperature(int)` `level(int)` `energyDiscount(int)` `tier(int)` `texture(String)`

### 3.8 TagPrefix（TagPrefixBuilder）

```js
GTCEuStartupEvents.registry('gtceu:tag_prefix', event => {
    event.create('test_dust', 'basic')
        .idPattern('dust{0}')                  // 物品 id 模式
        .langValue('{0} Dust')                 // 显示名（{0}=材料名）
        .materialAmount(100)                   // 材料用量（mb）
        .materialIconType(GTMaterialIconType.dust)
        .unificationEnabled(true)
        .generateItem(true)
        .generationCondition(mat => true)
        .defaultTagPath('dusts')
        .tooltip((mat, list) => { })
    event.create('test_ore', 'ore')
        .stateSupplier(() => Block.getBlock('minecraft:stone').defaultBlockState())
        .baseModelLocation('minecraft:block/stone')
        .doubleDrops(true).isSand(false).shouldDropAsItem(true)
})
```

**TagPrefixBuilder 全链式方法**：`idPattern` `langValue` `materialAmount(long)` `unificationEnabled` `generateItem` `generationCondition(Predicate<Material>)` `materialIconType` `tooltip(BiConsumer<Material, List<Component>>)` `maxStackSize` `setIgnored(Material, ItemLike...)` `addSecondaryMaterial(MaterialStack)` `defaultTagPath` `prefixTagPath` `prefixOnlyTagPath` `unformattedTagPath` `customTagPath(String, BiFunction)` `miningToolTag(String|TagKey<Block>)`

**OreTagPrefixBuilder 额外**：`stateSupplier(Supplier<BlockState>)` `materialSupplier` `baseModelLocation(RL)` `templateProperties` `doubleDrops` `isSand` `shouldDropAsItem`

### 3.9 其他 Builder

- `MaterialIconSetBuilder`（registry('gtceu:material_icon_set')）：`parent(MaterialIconSet)`
- `MaterialIconTypeBuilder`（registry('gtceu:material_icon_type')）：无链式方法
- `WorldGenLayerBuilder`（registry('gtceu:world_gen_layer')）：`targets(IWorldGenLayer.RuleTestSupplier...)` `dimensions(RL...)`
- `FluidVeinBuilderJS`：基岩流体矿脉定义（见 §5.2）

---

## 4. 配方体系

### 4.1 组件两层结构（关键理解）

配方方法分两层：
1. **schema 级 RecipeKey**（`GTRecipeSchema`）：`ID/DURATION/DATA/CONDITIONS/CATEGORY/ALL_INPUTS/ALL_TICK_INPUTS/ALL_OUTPUTS/ALL_TICK_OUTPUTS/INPUT_CHANCE_LOGICS/OUTPUT_CHANCE_LOGICS/TICK_INPUT_CHANCE_LOGICS/TICK_OUTPUT_CHANCE_LOGICS/SCHEMA`
   —— JS 里 `duration(120)`、`category(...)` 直接按方法名调用（`DURATION` 是 `RecipeKey<Long>`，**没有 Java 同名方法**）
2. **GTRecipeJS 手写便捷方法**：`itemInputs/chancedOutput/inputEU` 等（见 4.3）

### 4.2 GTRecipeComponents 全字段

| 组 | 字段 | 含义 |
|---|---|---|
| 基础 | `TAG` / `RESOURCE_LOCATION` / `RECIPE_CAPABILITY` / `CHANCE_LOGIC` / `RECIPE_CONDITION` / `FLUID_INGREDIENT` / `EXTENDED_OUTPUT` / `ENERGY_STACK` | 各 RecipeComponent |
| 内容 | `ITEM_IN` / `ITEM_OUT` / `FLUID_IN` / `FLUID_OUT` / `EU_IN` / `EU_OUT` / `CWU_IN` / `CWU_OUT` | ContentJS 包装 |
| perTick | `IN` / `TICK_IN` / `OUT` / `TICK_OUT` | CapabilityMapComponent |
| 其他 | `CHANCE_LOGIC_MAP` / `VALID_CAPS` | 几率映射 / 能力→组件对 |

### 4.3 GTRecipeJS 方法全集

```js
// ===== 通用 =====
.id(id)  .category(category)  .perTick(boolean)  .chance(int)  .maxChance(int)
.tierChanceBoost(int)  .onSave(b => {})
.addData(key, value)  .addDataString / addDataNumber / addDataBool
.input(cap, ...)  .output(cap, ...)        // 任意 RecipeCapability
.addCondition(RecipeCondition)

// ===== 能量 =====
.inputEU(voltage, amperage)  .EUt(voltage[, amperage])  .outputEU(voltage[, amperage])

// ===== 计算力（CWU）=====
.inputCWU(int)  .CWUt(int)  .totalCWU(int)  .outputCWU(int)

// ===== 物品输入 =====
.itemInputs(...) / .inputItems(...)          // 等价；支持 字符串/Item/标签#/TagKey
.inputItems(TagPrefix, Material)             // 材料输入：'plate', 'gtceu:iron'
.inputItems(MaterialEntry)  .inputItems(MachineDefinition)
.itemInputRanged(ing, min, max)              // 随机数量
.inputItemNbtPredicate(stack, predicate)
.notConsumable(item)  .notConsumable(TagPrefix, Material)
.circuit(n)                                  // 编程电路 1-32
.chancedInput(item, chance, boost)  .chancedFluidInput(fluid, chance, boost)

// ===== 物品输出 =====
.itemOutputs(...) / .outputItems(...)
.outputItems(TagPrefix, Material)  .outputItems(MachineDefinition)
.itemOutputRanged(...)  .outputItemsRanged(...)
.chancedOutput(item, chance[, boost])        // 概率（分数或 /10000）
.chancedOutput(item, '1/4', boost)           // 字符串分数形式

// ===== 流体 =====
.inputFluids(Fluid.of(...))  .inputFluidsRanged(...)
.outputFluids(...)  .outputFluidsRanged(...)
.chancedFluidOutput(fluid, chance[, boost])
.notConsumableFluid(fluid)

// ===== 几率逻辑（ChanceLogic）=====
.chancedItemOutputLogic('or'|'and'|'xor'|'first'|'none')
.chancedFluidOutputLogic(...)  .chancedItemInputLogic(...)  .chancedFluidInputLogic(...)
.chancedOutputLogic(cap, ChanceLogic)  .chancedInputLogic(cap, ChanceLogic)
.chancedTickOutputLogic / chancedTickInputLogic

// ===== 配方条件 =====
.cleanroom(CleanroomType.CLEANROOM)
.dimension('minecraft:the_end'[, true])  .biome('minecraft:plains'[, true])
.biomeTag('#minecraft:is_overworld'[, true])
.rain(1.0)  .thunder(1.0)  .posY(min, max)  .daytime(true)
.adjacentFluids('minecraft:water', 'minecraft:lava')  .adjacentFluidTag('forge:water')
.adjacentBlocks(...)  .adjacentBlockTag(...)
.gameStage('stage')  .ftbQuest('id')  .heraclesQuest('id')
.environmentalHazard('carbon_monoxide_poisoning')

// ===== 数据/机器专属 =====
.blastFurnaceTemp(1200)  .explosivesAmount(n)  .explosivesType(stack)
.solderMultiplier(n)  .disableDistilleryRecipes(bool)  .fusionStartEU(n)
.researchScan(bool)  .durationIsTotalCWU(bool)  .hideDuration(bool)
.addMaterialInfo(true)  .removePreviousMaterialInfo()

// ===== 研究 =====
.scannerResearch(b => b.researchStack(Item.of('minecraft:coal_block'))
                     .EUt(GTValues.VA[GTValues.IV]).duration(420))
.stationResearch(b => b.researchStack(...).EUt(...).CWUt(24, 12000))
.researchWithoutRecipe('id')
```

> ⚠️ `scannerResearch` 需索引式调用：`["scannerResearch(java.util.function.UnaryOperator)"](b => ...)`

### 4.4 配方移除/修改

```js
ServerEvents.recipes(event => {
    event.remove({ id: 'gtceu:smelting/sticky_resin_from_slime' })
    event.remove({ mod: 'gtceu' })
    event.remove({ type: 'gtceu:arc_furnace' })
    event.remove({ input: '#forge:ingots/iron' })
    event.remove({ output: 'minecraft:cobblestone' })
    event.replaceInput({ mod: 'gtceu' }, 'minecraft:sand', '#forge:sand')
    event.replaceOutput({ type: 'gtceu:arc_furnace' }, 'gtceu:wrought_iron_ingot', 'minecraft:dirt')
})
```

### 4.5 自定义合成配方（gtceu:shaped）

`GTShapedRecipeSchema.SCHEMA` 注册在 `gtceu:shaped` 下（`WrappingRecipeSchemaType` 包装
`GTRecipeSerializer`），`ShapedRecipeJS` 支持 `addMaterialInfo()`（合成配方材料信息）。

### 4.6 自定义配方类型 → 配方

```js
// 配方类型注册后，recipes 名 = 类型 id
ServerEvents.recipes(event => {
    event.recipes.gtceu.test_recipe_type('test_recipe')
        .itemInputs('minecraft:dirt')
        .itemOutputs('minecraft:diamond')
        .duration(100).EUt(30)
})
```

---

## 5. 世界生成

### 5.1 矿脉（GTOreVeinEventJS）

```js
GTCEuServerEvents.oreVeins(event => {
    event.add("kubejs:custom_vein", vein => {
        vein.weight(200).clusterSize(40).density(0.25).discardChanceOnAirExposure(0)
        vein.layer("deepslate")                    // stone/deepslate/netherrack/endstone
        vein.dimensions("minecraft:overworld")
        vein.biomes("#minecraft:is_overworld")
        vein.heightRangeUniform(-60, 20)           // 或 heightRangeTriangle / heightRange(json)
        vein.generator(b => b.standard().withBlock('gtceu:iron_ore'))
        vein.addIndicator(...)
    })
    event.remove("gtceu:magnetite_vein_ow")
    event.removeAll()
    event.removeAll((id, vein) => id.path != "magnetite_vein_ow")
    event.modify("gtceu:cassiterite_vein", vein => vein.density(1.0))
    event.modifyAll((id, vein) => { console.log("Modifying vein: " + id); vein.density(1.0) })
})
```

> ⚠️ `vein.generator(...)` 链式写法以官方 `02-Generators.md` 为准（7.5.3 对应
> `standardVeinGenerator(consumer)/surfaceIndicatorGenerator(consumer)` 等）。

**GTOreVeinEventJS 方法**：`add(RL, Consumer<GTOreDefinition>)` `modify(RL, Consumer<GTOreDefinition>)` `modifyAll(BiConsumer<RL, GTOreDefinition>)` `remove(RL)` `removeAll()` `removeAll(BiPredicate<RL, GTOreDefinition>)`

### 5.2 基岩流体矿脉（GTFluidVeinEventJS）

```js
GTCEuServerEvents.fluidVeins(event => {
    event.add('gtceu:custom_bedrock_fluid_vein', vein => {
        vein.dimensions('minecraft:overworld')
        vein.fluid(() => Fluid.of('gtceu:custom_fluid').fluid)
        vein.weight(600)
        vein.minimumYield(120)  vein.maximumYield(720)
        vein.depletionAmount(2)  vein.depletionChance(1)  vein.depletedYield(50)
    })
    event.modify('gtceu:heavy_oil_deposit', vein => vein.setWeight(9999))
    event.remove('gtceu:heavy_oil_deposit')
})
```

**GTFluidVeinEventJS**：`add(RL, Consumer<BedrockFluidDefinition.Builder>)` `modify(RL, Consumer<BedrockFluidDefinition>)` `remove(RL)`
**GTBedrockOreVeinEventJS**：`add(RL, Consumer<BedrockOreDefinition.Builder>)` `modify(RL, Consumer<BedrockOreDefinition>)` `remove(RL)`
**FluidVeinBuilderJS 全方法**：`yield(min, max)` `weight(int)` `minimumYield/maximumYield` `depletionAmount` `depletionChance` `depletedYield` `addSpawnDimension(RL...)` `biomes(weight, String... biomeTag)` `fluid(Supplier<Fluid>)`

### 5.3 基岩矿脉（GTBedrockOreVeinEventJS，需 config doBedrockOres）

```js
GTCEuServerEvents.bedrockOreVeins(event => {
    event.add('kubejs:overworld_bedrock_ore_vein_iron', vein => {
        vein.weight(100).size(3).yield(10, 20)
        vein.material(GTMaterials.Goethite, 5)
        vein.material(GTMaterials.Limonite, 2)
        vein.dimensions('minecraft:overworld')
    })
    event.modify('gtceu:example_ore', vein => vein.weight(9999))
    event.remove('gtceu:example_ore')
})
```

---

## 6. CraftingComponents 与披风

### 6.1 CraftingComponentsEventJS

```js
const Map = Java.loadClass("java.util.Map")

GTCEuStartupEvents.craftingComponents(event => {
    // 修改内置组件
    event.setItem(GTCraftingComponents.CIRCUIT, GTValues.MV, Item.of('minecraft:dirt'))
    event.setItems(GTCraftingComponents.PUMP, Map.of(
        GTValues.LV, Item.of('gtceu:lv_robot_arm'),
        GTValues.MV, Item.of('gtceu:mv_robot_arm')))
    event.setTag(GTCraftingComponents.CASING, GTValues.EV, 'minecraft:logs')  // 注意无 #
    event.setMaterialEntry(GTCraftingComponents.PLATE, GTValues.UEV,
        new MaterialEntry('plate', 'gtceu:infinity'))
    event.removeTier('robot_arm', GTValues.EV)
    event.removeTiers('pump', GTValues.EV, GTValues.IV, GTValues.LuV)

    // 创建新组件（ComponentWrapper）
    event.createItem("item_component", 'minecraft:cyan_stained_glass')
        .add(GTValues.LV, Item.of('minecraft:cyan_stained_glass'))
        .addItem(GTValues.MV, Item.of('minecraft:diamond'))
    event.createTag("tag_component", 'forge:barrels/wooden')
        .addTag(GTValues.LV, 'forge:chests/wooden')
    event.createMaterialEntry("material_entry_component", new MaterialEntry('plate', 'gtceu:infinity'))
        .addMaterialEntry(GTValues.LV, 'ingot', 'gtceu:infinity')
})
```

**完整方法**（javap 确认）：
- 修改：`setItem/setTag/setMaterialEntry(component, tier, value)`、`set/setItems/setTags/setMaterialEntries(component, Map<Object,Object>)`、`setFallbackItem/setFallbackTag/setFallbackMaterialEntry`、`removeTier(component, tier)`、`removeTiers(component, tier...)`
- 新建：`createItem(id, ItemStack)` / `createTag(id, RL)` / `createMaterialEntry(id, MaterialEntry)` → `ComponentWrapper`
- `ComponentWrapper`：`add(int tier, Object)` `addItem/addTag/addMaterialEntry(int, ...)`（`addMaterialEntry(int, TagPrefix, Material)` 也支持）

### 6.2 披风（RegisterCapesEventJS）

```js
GTCEuServerEvents.registerCapes(event => {
    event.registerCape('kubejs:my_cape', 'kubejs:textures/capes/my_cape.png')
    event.registerFreeCape('kubejs:free_cape', 'kubejs:textures/capes/free_cape.png')
    event.unlockCapeFor('00000000-0000-0000-0000-000000000000', 'kubejs:my_cape')
})
```

**RegisterCapesEventJS**：`registerCape(RL, RL)`（id+纹理）`registerFreeCape(RL, RL)` `unlockCapeFor(UUID, RL)`

---

## 7. 绑定清单

（62 个绑定，全部 javap 确认；分组列举）

| 分组 | 绑定 |
|---|---|
| 核心 | `GTCEu` `GTCEuAPI` `GTRegistries` `GTValues` `PropertyKey` `RecipeCapability` `ChanceLogic` `CleanroomType` |
| 材料 | `GTElements` `GTMaterials` `GTMaterialRegistry` `MaterialEntry` `GTMaterialFlags` `TagPrefix` `ItemGenerationCondition` `GTMaterialIconSet` `GTMaterialIconType` `ChemicalHelper` `ToolProperty` `ArmorProperty` `GTToolType` `HazardProperty` `MedicalCondition` `Symptom` |
| 流体 | `GTFluidAttributes` `GTFluidBuilder` `GTFluidStorageKeys` `GTFluidState` |
| 内容 | `GTBlocks` `GTMaterialBlocks` `GCYMBlocks` `GTItems` `GTMaterialItems` `GTMachines` `GTMultiMachines` `GCYMMachines` `GTMachineUtils` `GTSoundEntries` `GuiTextures` `GTModels` `GTMachineModels` `GTModelProperties` `GTDynamicRenders` |
| 机器 | `RotationState` `FactoryBlockPattern` `MultiblockShapeInfo` `Predicates` `PartAbility` `GTRecipeModifiers` `OverclockingLogic` `MachineConstructors` `MachineModifiers` `ModifierFunction` `CraftingComponent` `GTCraftingComponents` `EnergyStack` `IOEnergyStack` `NBTPredicates` |
| 配方 | `GTRecipeTypes` `GTRecipeCategories` `GTMedicalConditions` |
| 世界 | `GTOreVein` `GTLayerPattern` `GTDikeBlockDefinition` `GTOres` `GTWorldGenLayers` `GTCapes` `CapeRegistry` |
| 其他 | `SoundType`（MC 类） |

---

## 8. 配方组件注册名

（`registerRecipeComponents` 注册的 20 个名，KJS `registerRecipeComponents` 事件里可用）

| 注册名 | 组件 | 注册名 | 组件 |
|---|---|---|---|
| `compoundTag` | TAG | `recipeCondition` | RECIPE_CONDITION |
| `resourceLocation` | RESOURCE_LOCATION | `recipeCapability` | RECIPE_CAPABILITY |
| `chanceLogic` | CHANCE_LOGIC | `gtRecipeInputs` | IN |
| `gtRecipeTickInputs` | TICK_IN | `gtRecipeOutputs` | OUT |
| `gtRecipeTickOutputs` | TICK_OUT | `gtItemIn` | ITEM_IN |
| `gtItemOut` | ITEM_OUT | `gtFluidIn` | FLUID_IN |
| `gtFluidOut` | FLUID_OUT | `gtEuIn` | EU_IN |
| `gtEuOut` | EU_OUT | `gtCwuIn` | CWU_IN |
| `gtCwuOut` | CWU_OUT | `gtChance` | CHANCE_LOGIC_MAP |
| `extendedOutputItem` | EXTENDED_OUTPUT | `fluidIngredient` | FLUID_INGREDIENT |

---

## 9. 常见坑

| 坑 | 说明 |
|---|---|
| 配方类型必须先于机器注册 | 机器 `recipeType('...')` 引用已注册类型 |
| `duration()` 没有 Java 方法 | 是 schema 级 `RecipeKey<Long>`，JS 层直接调用即可 |
| `scannerResearch` 索引调用 | `["scannerResearch(java.util.function.UnaryOperator)"](b => ...)` |
| `EUt` 正负 | 正数耗电、负数发电；`perTick(true)` 后输入输出变每 tick，用完置回 false |
| chance 分母 | 0-10000 整数或分数串 '1/4'；chance=0 的输入 = 不消耗（notConsumable） |
| 材料没物品 | 加形态（ingot/dust/...）+ `GENERATE_*` flags |
| 改属性先删后设 | `removeProperty` 再 `setProperty` |
| 自定义配方能力 | KJS 键在 `IGTAddon.registerRecipeKeys(KJSRecipeKeyEvent)` 里 `registerKey` |
| 机器模型 | 补 `models/block/machine/<id>.json`，动画贴图带 `.mcmeta` |
| 官方文档差异 | 以 7.5.3 jar javap 为准（`FactoryBlockPattern.start().aisle`、`Predicates.controller(Predicates.blocks(...))`） |

---

## 10. 完整示例集

### 10.1 一条龙：材料 → 配方类型 → 机器 → 配方

```js
// ========== startup_scripts ==========
// 1. 材料
GTCEuStartupEvents.registry('gtceu:material', event => {
    event.create('my_alloy').ingot()
        .components('1x iron', '1x copper')
        .color(0x839689).iconSet(GTMaterialIconSet.METALLIC)
        .flags(GTMaterialFlags.GENERATE_PLATE, GTMaterialFlags.GENERATE_GEAR)
})

// 2. 配方类型（必须先于机器）
GTCEuStartupEvents.registry('gtceu:recipe_type', event => {
    event.create('my_alloy_smelter')
        .category('test')
        .setEUIO('in')
        .setMaxIOSize(3, 3, 2, 1)
        .setProgressBar(GTGuiTextures.PROGRESS_ARROW)
})

// 3. 机器
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_alloy_smelter', 'simple', 0, GTValues.LV, GTValues.MV)
        .recipeType('my_alloy_smelter')
        .rotationState(RotationState.NON_Y_AXIS)
        .tankScalingFunction(tier => tier * 3200)
})

// ========== server_scripts ==========
// 4. 配方
ServerEvents.recipes(event => {
    event.recipes.gtceu.my_alloy_smelter('my_alloy_from_parts')
        .itemInputs('2x gtceu:iron_dust', 'gtceu:copper_dust')
        .itemOutputs('gtceu:my_alloy_ingot')
        .duration(200).EUt(30)
})
```

### 10.2 概率 + 条件 + 数据

```js
ServerEvents.recipes(event => {
    event.recipes.gtceu.macerator('rich_ore')
        .itemInputs('gtceu:rich_iron_ore')
        .chancedOutput('2x gtceu:iron_dust', 9000, 100)
        .chancedOutput('gtceu:gold_dust', '1/8')
        .dimension('minecraft:overworld')
        .blastFurnaceTemp(1200)
        .duration(60).EUt(30)
})
```

### 10.3 多方块 + 配方修改器

```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_parallel_controller', 'multiblock')
        .recipeTypes(['my_alloy_smelter'])
        .recipeModifiers([GTRecipeModifiers.PARALLEL_HATCH])
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('CCC', 'GGG', 'CCC')
            .aisle('CKC', 'GGG', 'CSC')
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('M', Predicates.abilities(PartAbility.MAINTENANCE))
            .where('S', Predicates.abilities(PartAbility.MUFFLER))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                .and(Predicates.autoAbilities(definition.getRecipeTypes())))
            .build())
})
```


---

## 11. 附加页：元素注册与物品归类

元素（Element）→ 材料（Material）→ 形态物品的完整注册流程，以及把**已有物品**归类到
材料体系的 `ItemMaterialData` 静态 API 详解（含 `Java.loadClass` 直接调静态方法的
Rhino 桥接规则、Supplier lambda、反查验证、常见坑）。

➡️ [GTCEu-KubeJS元素注册与物品归类.md](./GTCEu-KubeJS元素注册与物品归类.md)
