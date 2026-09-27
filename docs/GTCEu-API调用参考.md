# GTCEu 1.20.1 API 调用参考（详细签名与示例）

> 配套《GTCEu-API实践手册.md》——手册讲原理与坑，本篇给出每个 API 的**详细签名、参数语义、完整调用示例**（均经 javap 反编译验证，GTCEu 7.5.3）。

---

## 一、机器注册链

### 1.1 入口与事件

```java
// Gtnecore.java（mod 构造）
public static final GTRegistrate REGISTRATE = GTRegistrate.create(MODID);
modEventBus.addGenericListener(MachineDefinition.class, GtnecoreMachines::onMachineRegister);

// 注册方法签名
public static void onMachineRegister(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event)
```

### 1.2 简单机器 — 完整链式调用

```java
public static MachineDefinition MY_MACHINE = Gtnecore.REGISTRATE
        .machine("my_machine", holder -> new MyMachine(holder))   // Function<IMachineBlockEntity, MetaMachine>
        .tier(GTValues.HV)                                        // int：电压等级（LV=1..MAX=21）
        .abilities(PartAbility.EXPORT_FLUIDS)                     // PartAbility...：部件能力
        .rotationState(RotationState.NONE)                        // NONE=无朝向 / ALL=6向 / ALL_Y
        .simpleModel(Gtnecore.id("block/machine/my_machine"))     // ResourceLocation：模型 json 路径
        .modelProperty(GTMachineModelProperties.IS_FORMED, false) // Property, Comparable：loader 变体属性
        .modelProperty(GTMachineModelProperties.IS_PAINTED, false)
        .tooltips(Component.translatable("block.gtnecore.my_machine.tooltip"))
        .register();                                              // MachineDefinition
```

**MachineBuilder 常用方法签名**（返回 TYPE=自身，可链式）：

```java
TYPE tier(int)
TYPE abilities(PartAbility...)
TYPE rotationState(RotationState)
TYPE recipeType(GTRecipeType)
TYPE recipeModifier(RecipeModifier) / recipeModifier(RecipeModifier, boolean)
TYPE recipeModifiers(RecipeModifier...)
TYPE addOutputLimit(RecipeCapability<?>, int)
TYPE model(MachineBuilder.ModelInitializer)
TYPE simpleModel(ResourceLocation)
TYPE defaultModel()
TYPE overlaySteamHullModel(ResourceLocation)
TYPE workableCasingModel(ResourceLocation casing, ResourceLocation overlay)
TYPE colorOverlayTieredHullModel(String overlay, String texture, String emissive)  // ⚠ datagen 依赖
TYPE appearanceBlock(Supplier<? extends Block>)
TYPE modelProperty(Property<?>, Comparable<?>) / modelProperty(Property<?>)
TYPE tooltips(Component...)
DEFINITION register()
```

### 1.3 多方块

```java
REGISTRATE.multiblock("steam_oven_64", GtneSteamOvenMachine::new)   // Function<IMachineBlockEntity, MultiblockControllerMachine>
        .rotationState(RotationState.ALL)
        .appearanceBlock(() -> gtBlock("steam_machine_casing"))     // 成型后部件外观
        .defaultModel()
        .recipeType(GTRecipeTypes.FURNACE_RECIPES)
        .recipeModifier(SteamParallelMultiblockMachine::recipeModifier, true)
        .addOutputLimit(ItemRecipeCapability.CAP, 1)
        .pattern(def -> FactoryBlockPattern.start()
                .aisle("#ABA#", "#CCC#", "#DDD#", "#E#E#", "#####", "#####", "#####")
                .aisle("AAAAA", "DFGFC", "DFGFC", "EFEFE", "#EEE#", "##E##", "##E##")
                .aisle("BBABB", "DGGGC", "DGGGC", "#EGE#", "#EFE#", "#E#E#", "#E#E#")
                .aisle("AAAAA", "DFGFC", "DFGFC", "EFEFE", "#EEE#", "##E##", "##E##")
                .aisle("#ABA#", "#EHE#", "#EEE#", "#E#E#", "#####", "#####", "#####")
                .where('H', Predicates.controller(Predicates.blocks(def.getBlock())))
                .where('E', gtBlockPredicate("steam_machine_casing"))
                .where('#', Predicates.any())
                .build())
        .register();
```

**MultiblockMachineBuilder 额外方法**：

```java
TYPE pattern(Function<MultiblockMachineDefinition, BlockPattern>)   // def -> FactoryBlockPattern...build()
TYPE generator(boolean)
TYPE allowFlip(boolean)
TYPE partSorter(Comparator<IMultiPart>)
TYPE additionalDisplay(BiConsumer<IMultiController, List<Component>>)
TYPE recoveryItems(Supplier<ItemLike[]>)
TYPE shapeInfo(Function<MultiblockMachineDefinition, MultiblockShapeInfo>)
DEFINITION register()
```

---

## 二、Pattern 系统

### 2.1 Predicates（静态工厂）

```java
static TraceabilityPredicate controller(TraceabilityPredicate)      // 控制器位置
static TraceabilityPredicate blocks(Block...) / blocks(IMachineBlock...) / machines(MachineDefinition...)
static TraceabilityPredicate blockTag(TagKey<Block>)
static TraceabilityPredicate any()                                  // 任意方块
static TraceabilityPredicate air()
static TraceabilityPredicate abilities(PartAbility...)             // 部件能力（任意等级）
static TraceabilityPredicate ability(PartAbility, int...)
static TraceabilityPredicate autoAbilities(GTRecipeType...)        // 自动仓
static TraceabilityPredicate custom(Predicate<MultiblockState>, Supplier<BlockInfo[]>)  // 自定义+预览
static TraceabilityPredicate frames(Material...)
```

### 2.2 TraceabilityPredicate（链式）

```java
TraceabilityPredicate or(TraceabilityPredicate)                    // 多选一（limited 独立计数）
TraceabilityPredicate setMinGlobalLimited(int)                      // ⚠ 作用到本谓词所有 limited 子项
TraceabilityPredicate setMaxGlobalLimited(int)
TraceabilityPredicate setMinLayerLimited(int) / setMaxLayerLimited(int)
TraceabilityPredicate setExactLimit(int)
TraceabilityPredicate setPreviewCount(int)
TraceabilityPredicate setIO(IO)
TraceabilityPredicate addTooltips(Component...)
```

**典型组合**（可选槽 + 计数）：

```java
// B 槽：蒸汽仓 1~4 个，其余填火箱（A）
.where('B', Predicates.abilities(PartAbility.STEAM)
        .setMinGlobalLimited(1).setMaxGlobalLimited(4)
        .or(gtBlockPredicate("bronze_firebox_casing")))
// D 槽：输出/输入总线互放，至少 1 最多 3，其余填墙（E）
.where('D', Predicates.abilities(PartAbility.EXPORT_ITEMS, PartAbility.IMPORT_ITEMS)
        .setMinGlobalLimited(1).setMaxGlobalLimited(3)
        .or(gtBlockPredicate("steam_machine_casing")))
```

### 2.3 FactoryBlockPattern

```java
static FactoryBlockPattern start()
FactoryBlockPattern aisle(String... rows)        // 每行 5 字符=一层横截面；多行=多层
FactoryBlockPattern aisleRepeatable(int min, int max, String... rows)
FactoryBlockPattern where(char, TraceabilityPredicate) / where(String, ...)
BlockPattern build()
```

---

## 三、机器行为覆写（签名）

### 3.1 蒸汽多方块

```java
// 构造：可传默认并行（Object... args[0]=Integer 会覆盖 ConfigHolder 默认）
public SteamParallelMultiblockMachine(IMachineBlockEntity holder, Object... args)
public double getConversionRate()                      // 蒸汽换算（蒸汽烤箱=2.0）
public void setMaxParallels(int) / public int getMaxParallels()
public static ModifierFunction recipeModifier(MetaMachine, GTRecipe)   // 蒸汽机配方修饰
public void addDisplayText(List<Component>)            // 状态文本
```

### 3.2 通用机器

```java
public Direction getFrontFacing() / public void setFrontFacing(Direction)
public boolean isFacingValid(Direction)                // wrench 朝向合法性
public void onLoad()                                   // 放置/加载（服务端调用）
public boolean isRemote()                              // 客户端判断
public BlockPos getPos()
public InteractionResult tryToOpenUI(Player, InteractionHand, BlockHitResult)  // 返回 FAIL = 无 UI
```

### 3.3 IDropSaveMachine（破坏保留）

```java
public interface IDropSaveMachine {
    default boolean saveBreak() { return false; }       // 破坏掉落带数据
    default boolean savePickClone() { return false; }   // 中键拾取带数据
    default void saveToItem(CompoundTag tag) {}         // 写 NBT
    default void loadFromItem(CompoundTag tag) {}       // 放置/加载 NBT
}
```

### 3.4 仓类构造

```java
// FluidHatchPartMachine
public FluidHatchPartMachine(IMachineBlockEntity holder, int tier, IO io,
        int initialCapacity, int maxCapacity, Object... args)
protected NotifiableFluidTank createTank(int initialCapacity, int maxCapacity, Object... args)

// ⚠ NotifiableFluidTank：第一个 int 是【槽数】，第二个是【每槽容量】
public NotifiableFluidTank(MetaMachine, int tankCount, int capacityPerTank, IO capabilityIO)
// 访问：tank.getStorages()[0].getFluid() / setFluid(FluidStack)
```

---

## 四、流体构建

### 4.1 FluidBuilder

```java
public FluidBuilder temperature(int)                  // 开尔文（0=绝对零度）
public FluidBuilder color(int)
public FluidBuilder density(double) / viscosity(double) / luminosity(int)
public FluidBuilder attribute(FluidAttribute) / attributes(FluidAttribute...)
public FluidBuilder state(FluidState)                 // LIQUID / GAS / PLASMA
public FluidBuilder translation(String key)           // 自定义翻译键（覆盖默认 %s等离子体 格式）
public FluidBuilder burnTime(int)
public FluidBuilder block() / disableBucket()
```

### 4.2 材料流体注册（Material.Builder）

```java
TYPE fluid()                                          // 默认液体（LIQUID）
TYPE fluid(FluidStorageKey key, FluidBuilder builder) // 指定 key + 自定义 builder
TYPE fluid(FluidStorageKey key, FluidState state)
// FluidStorageKeys：LIQUID / GAS / PLASMA / MOLTEN
// FluidState：LIQUID / GAS / PLASMA
```

**完整示例（仅等离子体 + 0K + 腐蚀 + 自定义名）**：

```java
MATRIX = new Material.Builder(Gtnecore.id("matrix"))
        .element(GTElements.get("Matrix"))
        .dust()
        .fluid(FluidStorageKeys.PLASMA,
                new FluidBuilder()
                        .state(FluidState.PLASMA)
                        .temperature(0)
                        .attribute(FluidAttributes.ACID)
                        .translation("gtnecore.fluid.matrix_supercritical"))
        .buildAndRegister();
```

---

## 五、材料构建（Material.Builder）

```java
public Material.Builder(ResourceLocation id)
TYPE langValue(String)
TYPE element(Element)                                 // 元素材料（化学式=元素符号）
TYPE dust() / ingot() / gem() / plate() / fluid() ...
TYPE fluid(FluidStorageKey, FluidBuilder)
TYPE liquid(FluidBuilder)                             // = fluid(LIQUID, builder.state(LIQUID))
TYPE componentStacks(MaterialStack...)                // 成分（分解配方/化学式来源）
TYPE formula(String)                                  // ⚠ 显式化学式（builder 级，稳定）
TYPE formula(String, boolean formatFormula)
TYPE flags(MaterialFlag...)                           // GENERATE_PLATE / DISABLE_DECOMPOSITION...
TYPE hazard(HazardTrigger, MedicalCondition)          // 危险（致癌等）
TYPE color(int) / secondaryColor(int) / iconSet(MaterialIconSet)
TYPE cableProperties(long voltage, int amperage, int loss, boolean superconductor, int insulation)
Material buildAndRegister()
```

```java
// MaterialStack
public MaterialStack(Material material, long amount)
// HazardTrigger：INHALATION / ANY / SKIN_CONTACT / NONE
// GTMedicalConditions：CARCINOGEN / POISON / WEAK_POISON / CHEMICAL_BURNS / ASBESTOSIS ...
// GTElements：createAndRegister(long protons, long neutrons, long halfLife, String name, String displayName, String symbol, boolean isIsotope)
```

---

## 六、配方（GTRecipeBuilder）

```java
// 入口：GTRecipeTypes.<类型>.recipeBuilder(String id, Object... data)
GTRecipeBuilder inputItems(ItemStack...) / inputItems(TagPrefix, Material, int) / inputItems(ItemIngredient...)
GTRecipeBuilder outputItems(ItemStack...)
GTRecipeBuilder inputFluids(FluidStack...) / outputFluids(FluidStack...)
GTRecipeBuilder duration(int)                          // tick
GTRecipeBuilder EUt(long)                              // EU/t（正=耗电）
GTRecipeBuilder save(Consumer<FinishedRecipe>)         // addRecipes 的 consumer
```

**NBT 物品输出（装罐产出带流体）**：

```java
CompoundTag nbt = new CompoundTag();
nbt.put("Fluid", uranium.writeToNBT(new CompoundTag()));
ItemStack filled = new ItemStack(outlet);
filled.setTag(nbt);

GTRecipeTypes.CANNER_RECIPES.recipeBuilder("rbmk_fuel_outlet_fill")
        .inputItems(new ItemStack(outlet))
        .inputItems(new ItemStack(rod, 8))
        .outputItems(filled)
        .duration(600).EUt(32)
        .save(consumer);
```

---

## 七、外部物品归类为材料形态

```java
// FMLCommonSetup（物品注册后）执行
ItemMaterialData.registerMaterialEntry(Item item, TagPrefix prefix, Material mat);
ItemMaterialData.registerMaterialEntry(Supplier<? extends ItemLike>, TagPrefix, Material);  // 惰性
ItemMaterialData.registerMaterialEntries(Collection<Supplier>, TagPrefix, Material);

// 材料无该形态时手动注册 tag 映射（ChemicalHelper 靠它识别）
ItemMaterialData.TAG_MATERIAL_ENTRY.putIfAbsent(
        TagKey.create(Registries.ITEM, new ResourceLocation("gtceu", "ingots/infinity")),
        new MaterialEntry(TagPrefix.ingot, INFINITY));

// 查询（tooltip/配方识别用）
MaterialEntry entry = ChemicalHelper.getMaterialEntry(Item);  // entry.isEmpty() / entry.material() / entry.tagPrefix()
```

---

## 八、模型 JSON 详细结构（机器方块）

```json
{
  "parent": "minecraft:block/block",
  "loader": "gtceu:machine",
  "machine": "gtnecore:rbmk_fuel_outlet",
  "replaceable_textures": ["bottom", "top", "side"],
  "variants": {
    "is_formed=false,is_painted=false": {
      "model": {
        "parent": "gtceu:block/machine/template/part/hatch_machine_emissive",
        "textures": {
          "bottom": "gtceu:block/casings/voltage/iv/bottom",
          "top": "gtceu:block/casings/voltage/iv/top",
          "side": "gtceu:block/casings/voltage/iv/side",
          "overlay": "gtceu:block/overlay/machine/overlay_fluid_hatch_output",
          "overlay_emissive": "gtceu:block/overlay/machine/overlay_pipe_out_emissive"
        }
      }
    },
    "is_formed=false,is_painted=true":  { "model": { "parent": "gtceu:block/machine/template/part/hatch_machine_emissive_color_ring", "textures": { ... } } },
    "is_formed=true,is_painted=false":  { "model": { ... 同 is_formed=false 版 } },
    "is_formed=true,is_painted=true":   { "model": { ... 同 color_ring 版 } }
  }
}
```

```json
// blockstate（RotationState.NONE = 无 facing 属性，变体 ""）
{
  "variants": {
    "": { "model": "gtnecore:block/machine/rbmk_fuel_outlet", "x": 90 }
  }
}
// RotationState.ALL 时（facing 6 变体 + 旋转）
{ "variants": { "facing=north": { "model": "..." }, "facing=east": { "model": "...", "y": 90 }, ... } }
```

---

## 九、通用工具

```java
// 运行时取 GTCEu 方块（惰性）
Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "steam_machine_casing"));

// RegistryEntry 安全取值（防注册时序 NPE）
GTBlocks.CASING_BRONZE_BRICKS.getUnchecked();   // null 安全（get() 会抛）

// 流体 NBT
tag.put("Fluid", fluidStack.writeToNBT(new CompoundTag()));
FluidStack fs = FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));

// 材料流体
GTMaterials.Uranium238.getFluid(576);           // 576 mB 液态铀238
// 材料单位：L = 144 mB（GTValues.L）
```