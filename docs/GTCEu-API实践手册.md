# GTCEu 1.20.1 API 实践手册（知识蒸馏）

> 基于 GTCEu 7.5.3（特供 patch 版）实战踩坑整理。Forge 1.20.1 47.4.22 / Java 17。
> 面向：自定义机器、多方块、材料、流体、配方、模型、时序陷阱。

---

## 一、机器注册（GTRegistrate）

### 1.1 注册时机与事件

机器注册挂在 **mod 总线 generic listener**（GTCEu 冻结注册表前触发）：

```java
modEventBus.addGenericListener(MachineDefinition.class, GtnecoreMachines::onMachineRegister);
```

**⚠ 时序坑**：此事件（common_setup 阶段）时 **GTCEu 方块（GTBlocks）可能尚未注册**——
`GTBlocks.CASING_X.get()` 抛 `Registry entry not present`（RegistryEntry.get() 的 requireNonNull）。
规避：**注册时绝不直接取 GTCEu 方块**，改用运行时惰性解析（见 1.4）。

### 1.2 简单机器

```java
REGISTRATE.machine("name", holder -> new MyMachine(holder))
        .tier(GTValues.HV)
        .abilities(PartAbility.EXPORT_FLUIDS)      // 部件能力（仓/总线）
        .rotationState(RotationState.NONE)          // 或 ALL（facing 属性）
        .simpleModel(id("block/machine/name"))      // 指定模型 json
        .modelProperty(GTMachineModelProperties.IS_FORMED, false)   // loader 变体匹配必需！
        .modelProperty(GTMachineModelProperties.IS_PAINTED, false)
        .tooltips(Component.translatable("..."))
        .register();
```

### 1.3 多方块

```java
REGISTRATE.multiblock("name", MyMultiblock::new)
        .rotationState(RotationState.ALL)
        .appearanceBlock(() -> someBlock)           // 成型后部件外观
        .defaultModel()                             // 或复制 steam_oven 的 loader 模型
        .recipeType(GTRecipeTypes.FURNACE_RECIPES)
        .recipeModifier(SteamParallelMultiblockMachine::recipeModifier, true)
        .addOutputLimit(ItemRecipeCapability.CAP, 1)
        .pattern(def -> FactoryBlockPattern.start()
                .aisle("###", "XSX", "###")
                .where('S', Predicates.controller(Predicates.blocks(def.getBlock())))
                .where('X', Predicates.blocks(steamCasingPredicate()))  // 见 1.4
                .where('#', Predicates.any())
                .build())
        .register();
```

### 1.4 pattern 谓词（TraceabilityPredicate）—— 重点

**可选槽位 + 最少数量**（`or` 的 limited 各自独立计数）：

```java
// 槽位 X：蒸汽仓 1~4 个，其余可填火箱砖（or 的 limited 互不影响）
Predicates.abilities(PartAbility.STEAM)
        .setMinGlobalLimited(1).setMaxGlobalLimited(4)
        .or(Predicates.blocks(firebox))
// 槽位 D：输出总线+输入总线可互换、至少 1 个、最多 3 个，其余可填墙
Predicates.abilities(PartAbility.EXPORT_ITEMS, PartAbility.IMPORT_ITEMS)
        .setMinGlobalLimited(1).setMaxGlobalLimited(3)
        .or(steamCasingPredicate())
```

**⚠ `setMinGlobalLimited` 会作用于该谓词 limited 列表里所有子谓词**——所以先对
`abilities` 设置 min/max，再 `or(blocks(...))`（blocks 是 common 不限），否则会把
`blocks` 也卡上 min/max。

**⚠⚠ 反过来也要小心：`.or(abilities(...))` 串成一长条会变成「每种都要一个」**

`Predicates.abilities(...)` 内部是 `blocks(Block...)` → `PredicateBlocks`，其
`minCount`/`maxCount` 都是 **-1**；而 `TraceabilityPredicate` 构造函数按
「`-1/-1` → `common`，否则 → `limited`」分流。所以：

```java
// ❌ 每个 abilities 都落进 limited，setMinGlobalLimited 给**每一个**设了下限
//    → 玩家必须"每种仓各放一个"才能成型
blocks(CASING)
    .or(abilities(INPUT_ENERGY))
    .or(abilities(IMPORT_ITEMS))
    .or(abilities(EXPORT_FLUIDS))
    .setMinGlobalLimited(1)

// ✅ 所有可接受方块并进**一个** blocks()，只留一个谓词
blocks(allAcceptedBlocks())          // 数量不限，任一即可
```

想让**某一个**能力独享下限（如"维护仓至少 1 个"），必须让它**单独一次**
`abilities(...)` 调用、不与其它 `abilities` 串在同一条 `.or()` 链上。
GTCEu 自己的 `autoAbilities()` 就是这么写的：每个能力各一次独立调用。

**✅ `#`（any）位里的机器方块不会被当成部件**（2026-09-26 实测）

GTCEu 按**匹配到的 pattern 字符**决定角色：匹配到 `B` 的格子才走部件收集路径
（`abilities` / `getParts()`）；匹配到 `#` 的格子只是一次普通方块匹配。
因此**把功能仓放在 `#` 位，它永远不会进 `getParts()`、永远不会被当部件用**——
`Predicates.any()` 用在空洞位是安全的，不需要额外加"拒绝机器方块"的防护。

推论：若出现「结构成型但 `getParts()` 为空」，**先怀疑 pattern 坐标/朝向错了**
（见废案一号坑 1 的 aisle 翻面），而不是怀疑 `#` 位漏了防护。

**运行时惰性方块解析**（规避注册时序，关键技巧）：

```java
static TraceabilityPredicate gtBlockPredicate(String id) {
    return Predicates.custom(state -> {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", id));
        return b != null && state.getBlockState().is(b);
    }, () -> {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", id));
        return b == null ? new BlockInfo[]{} : new BlockInfo[]{new BlockInfo(b.defaultBlockState())};
    });
}
```
- `test(MultiblockState)` 在成型检查时调用（方块已注册）✓
- `BlockInfo[]` Supplier（预览）同样惰性 ✓
- custom 谓词无 limited（作为 or 的填空项，不参与计数）

### 1.5 成型后校验（onStructureFormed）

```java
@Override
public void onStructureFormed() {
    super.onStructureFormed();
    // 全局部件计数校验（pattern 无法表达"跨槽位合计"时用）
    int a = 0, b = 0;
    for (IMultiPart part : getParts()) {
        Block blk = part.self().getDefinition().getBlock();
        if (PartAbility.EXPORT_ITEMS.isApplicable(blk)) a++;
        if (PartAbility.IMPORT_ITEMS.isApplicable(blk)) b++;
    }
    if (a < 1 || b < 1) {
        onStructureInvalid();          // 撤销成型（清理能力）
        isFormed = false;              // protected 字段，子类可直接写
        return;
    }
    // 按部件数动态设置参数（如并行）
    setMaxParallels(16 * Math.max(1, steamCount));
}
```

---

## 二、机器类继承与行为

### 2.1 蒸汽多方块（SteamParallelMultiblockMachine）

| 覆写点 | 作用 |
|---|---|
| `getConversionRate()` | 蒸汽换算率（蒸汽烤箱 2.0；×2 耗汽 = 4.0） |
| `setMaxParallels(int)` | 并行数（构造可传默认值，成型时动态覆盖） |
| `onStructureFormed()` | 成型绑定蒸汽仓 + 附加校验 |

蒸汽消耗 = 配方 EU × 并行 × conversionRate，来源是 `PartAbility.STEAM` 部件仓。

### 2.2 仓 / 部件（FluidHatchPartMachine）

```java
// 构造：注意参数顺序！(holder, tier, io, initialCapacity, maxCapacity)
super(holder, GTValues.HV, IO.OUT, CAPACITY, 1);

// ⚠ 大坑：NotifiableFluidTank(machine, tankCount, capacityPerTank, io)
//     第一个 int 是【槽数】！传 576 会得到 576 个槽（几百桶库存）
@Override
protected NotifiableFluidTank createTank(int initialCapacity, int maxCapacity, Object... args) {
    this.tank = new NotifiableFluidTank(this, 1, CAPACITY, io);  // 1 槽 × 容量
    return tank;
}
```

**固定朝向**（不闪、不消失）：

```java
// RotationState.NONE：方块无 facing 属性
// ⚠ NONE 下不要调用 setFrontFacing（hasFrontFacing=false 会异常导致方块消失）
@Override
public Direction getFrontFacing() { return Direction.DOWN; }   // 恒向下

@Override
public boolean isFacingValid(Direction facing) { return facing == Direction.DOWN; }  // wrench 限制
```

**无 UI**：覆写 `tryToOpenUI(Player, InteractionHand, BlockHitResult)` 返回 `InteractionResult.FAIL`。

### 2.3 破坏保留库存（IDropSaveMachine，同青铜桶）

```java
class X extends FluidHatchPartMachine implements IDropSaveMachine {
    @Override public boolean saveBreak() { return true; }       // 破坏掉落带数据
    @Override public boolean savePickClone() { return true; }   // 中键拾取带数据
    @Override public void saveToItem(CompoundTag tag) {         // 写 NBT（顶层 "Fluid"）
        FluidStack fs = tank.getStorages()[0].getFluid();
        if (!fs.isEmpty()) tag.put("Fluid", fs.writeToNBT(new CompoundTag()));
    }
    @Override public void loadFromItem(CompoundTag tag) {       // 放置恢复（tank 构造时已建）
        if (tag != null && tag.contains("Fluid") && tank != null) {
            tank.getStorages()[0].setFluid(FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid")));
        }
    }
}
```

**机制**：`MetaMachineBlock.getDrops` 检查机器实现 `IDropSaveMachine` → `saveBreak()` true 时把 `saveToItem` 的 NBT 写进掉落物。

---

## 三、模型系统（机器方块）

### 3.1 机器模型 = `gtceu:machine` loader

```json
{
  "loader": "gtceu:machine",
  "machine": "gtnecore:my_machine",           // ← 必须指向本机！绑定别的机器会透明/错乱
  "variants": {
    "is_formed=false,is_painted=false": { "model": { "parent": "gtceu:block/machine/template/part/hatch_machine_emissive", "textures": { ... } } }
  }
}
```

**要点**：
- `machine` 字段 = 本机注册 id（GTMachineModel 用它绑定渲染状态）
- `is_formed/is_painted` 变体由 loader 处理（**不是**方块状态属性）
- 注册必须配 `.modelProperty(IS_FORMED/IS_PAINTED, false)`，否则变体匹配失败 → **紫黑纹理**
- `colorOverlayTieredHullModel`/`defaultModel` 的模型 json 由 **datagen 生成**——dev 未跑 runData 时缺失；要么跑 datagen，要么手写 loader 模型 json

### 3.2 常用模板

| 模板 | 用途 |
|---|---|
| `gtceu:block/machine/template/part/hatch_machine_emissive` | 仓/总线（机壳 + overlay + 发光面） |
| `gtceu:block/machine/template/cube_all/sided` | 多块控制器（cube_all + overlay_front） |
| `gtceu:block/machine/template/part/hatch_machine_emissive_color_ring` | 可喷涂仓（is_painted=true 变体） |

### 3.3 贴图路径

模型里引用 `gtceu:block/...` 直接对应 jar 里 `assets/gtceu/textures/block/...png`。
常用：`casings/voltage/<tier>/bottom|side|top`、`overlay/machine/overlay_fluid_hatch_output`、`overlay/machine/overlay_pipe_out_emissive`。

### 3.4 手写 elements 模型不适用于机器方块

机器方块渲染走 GTMachineModel 管线，手写 `elements` 普通模型会不渲染/闪烁——**机器方块不要手写几何**。

---

## 四、材料与元素

### 4.1 元素

```java
// createAndRegister(protons, neutrons, halfLife, name?, displayName, symbol, isIsotope)
GTElements.createAndRegister(0, 0, -1, null, "Matrix", "Mx", false);
// 查询：GTElements.get("Matrix")
```

### 4.2 材料

```java
new Material.Builder(id("matrix"))
        .langValue("Matrix")
        .element(GTElements.get("Matrix"))
        .dust()
        .fluid(FluidStorageKeys.PLASMA, new FluidBuilder()
                .state(FluidState.PLASMA)
                .temperature(0)                              // 0K
                .attribute(FluidAttributes.ACID)             // 腐蚀
                .translation("gtnecore.fluid.matrix_supercritical"))  // 自定义显示名
        .hazard(HazardProperty.HazardTrigger.INHALATION, GTMedicalConditions.CARCINOGEN)  // 致癌
        .flags(GENERATE_PLATE)
        .formula("Mx4C4004")                                 // 显式化学式（builder 级，不会被覆盖）
        .componentStacks(new MaterialStack[]{ new MaterialStack(MATRIX, 4), new MaterialStack(GTMaterials.Carbon, 4004) })
        .flags(MaterialFlags.DISABLE_DECOMPOSITION)          // 禁电解/离心分解
        .buildAndRegister();
```

**要点**：
- `FluidBuilder.translation(key)`：自定义流体翻译键（默认等离子体共用 `gtceu.fluid.plasma` = `%s等离子体`）
- `formula(String)` 在 builder 构建时生效；`setFormula`（build 后）可能被覆盖
- 危险属性用 `hazard(trigger, condition)`（GTMedicalConditions：CARCINOGEN/POISON/CHEMICAL_BURNS…），不是自定义

### 4.3 外部物品归类为材料形态（无尽锭/水晶矩阵锭）

**识别机制**：`ChemicalHelper.getMaterialEntry(Item)` **完全靠「物品 tag → TAG_MATERIAL_ENTRY」**。

```java
// 1. FMLCommonSetup（物品注册后）绑定——PostMaterialEvent 时物品未注册会解析到 AIR！
ItemMaterialData.registerMaterialEntry(avaritiaIngot, TagPrefix.ingot, INFINITY);

// 2. 材料无该形态时 getAllItemTags 不含 tag → 手动注册映射（public static 可直接操作）
ItemMaterialData.TAG_MATERIAL_ENTRY.putIfAbsent(
        TagKey.create(Registries.ITEM, new ResourceLocation("gtceu", "ingots/infinity")),
        new MaterialEntry(TagPrefix.ingot, INFINITY));

// 3. 数据包 tag：data/gtceu/tags/items/ingots/infinity.json（追加 avaritia 物品）
```

---

## 五、配方添加（IGTAddon.addRecipes）

```java
@Override
public void addRecipes(Consumer<FinishedRecipe> consumer) {
    GTRecipeTypes.CANNER_RECIPES.recipeBuilder("unique_recipe_id")
            .inputItems(new ItemStack(outlet))                  // 空输出口
            .inputItems(new ItemStack(rod, 8))                  // 8 根铀棒
            .outputItems(filledWithNbt)                         // NBT 物品（{"Fluid": ...}）
            .duration(600).EUt(32)
            .save(consumer);
}
```

- addRecipes 在 GTCEu 配方生成阶段调用，材料/物品已注册，可直接查 ForgeRegistries
- NBT 物品输出：`ItemStack.setTag(nbt)`（如装罐产出带 `Fluid` NBT 的机器物品）

---

## 六、时序陷阱汇总

| 场景 | 坑 | 解决 |
|---|---|---|
| 机器注册事件取 GTBlocks | `Registry entry not present` | 运行时惰性解析（custom 谓词 / 延迟 Supplier） |
| Class.forName 强制加载 GTBlocks | 静态块依赖初始化顺序 → `null key in entry` | 不要强制加载 |
| PostMaterialEvent 绑定外部 mod 物品 | 物品未注册 → 解析到 AIR | 移到 FMLCommonSetup |
| config 未加载读 ForgeConfigSpec | dev 抛 `Cannot get config value...` | mixin 防护（未加载返回默认值） |
| RegistryEntry.get() | 注册前 NPE | 用 `getUnchecked()` 判空 |
| `gtceu:recipe_capability` / `gtceu:cover` 注册 | 注册表已 freeze → `IllegalStateException`，**启动崩溃** | 只能走 `IGTAddon.registerRecipeCapabilities()` / `registerCovers()`；Forge 的 `GTRecipeType` 注册事件**太晚** |
| 多方块 `recipeTypes` 空数组 | 成型瞬间 `getRecipeType()` 越界崩溃 | 必须给真实 `recipeType`（调试期可占位） |
| 机器模型 `recipe_logic_status` 变体失效 | 报 `Unknown machine model state property` | 该属性只在机器**有配方逻辑**时存在；无配方类型只有 `is_formed` 两变体 |

---

## 七、调试手段

- **javap 反编译**：`javap -p -c -classpath <gtceu.jar> <class>`（`-v` 看常量池）——查 API 签名/字节码逻辑的首选
- **崩溃栈 → 反编译验证**：如 `getDrops` 的 IDropSaveMachine 分支、`onStructureFormed` 的部件绑定
- **诊断日志**：临时 mixin 打日志（如 hover 时打印 `getMaterialEntry` 结果），定位后删
- **模型资源**：直接查 jar 里 `assets/gtceu/`（blockstate/models/textures），复制改 machine 字段即可

---

## 附：本次开发沉淀的坑速查

1. `NotifiableFluidTank(machine, tankCount, capacityPerTank, io)`——**第一参是槽数**
2. 机器 loader 模型 `machine` 字段必须指向本机；注册要 `modelProperty(IS_FORMED/IS_PAINTED)`
3. `NONE` 旋转下不调 `setFrontFacing`（覆写 `getFrontFacing` 恒值即可）
4. `setMinGlobalLimited` 影响 or 链所有 limited 子谓词；**反过来**，把多个
   `.or(abilities(...))` 串成一条再设 min，会变成"每种仓各要一个"（见 §1.4）
5. 机器方块不要手写 elements 模型
6. 材料化学式用 builder `formula()`（`setFormula` 会被覆盖）
7. 蒸汽换算率 `getConversionRate()`；锅炉温度机制（升温/降温/蒸汽/节流）见 AI 工作日志第 7 节
8. `#`(any) 位里的机器方块**不会被当部件**——pattern 字符决定角色，无需额外防护（§1.4）
9. 多方块 `recipeTypes` 不能为空数组——一成型就 `getRecipeType()` 越界崩溃
10. `MetaMachine.serverTick()` 是 `final`；要周期逻辑用 `onLoad()` + `subscribeServerTick`
11. 自定义 `RecipeLogic` 覆写 `createRecipeLogic(Object...)` 即可，**不用改 patch**
12. 注册 `gtceu:recipe_capability` **只能**在 `IGTAddon.registerRecipeCapabilities()` 里
    （注册表冻结窗口极窄，写错位置直接启动崩溃；cover 同理走 `registerCovers()`）