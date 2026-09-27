# GTNEcore 开发指南：GTM 7.5.3 API 全览（含 KubeJS 联动）

> 本文档为整合包/附属模组开发者的 GTM API 指导草稿。所有类名与方法签名均来自对
> `gtceu-1.20.1-7.5.3.jar` 的 javap 实际输出（本地权威素材），网络文档仅用于补充
> KubeJS 用法示例。凡无法从 jar 验证的内容均标注 ⚠️。

---

## 版本与环境

| 项 | 值 |
|---|---|
| 模组 | GregTech Modern（GTM / GTCEu Modern） |
| 版本 | `com.gregtechceu.gtceu:gtceu-1.20.1:7.5.3` |
| Minecraft / Forge | 1.20.1 / Forge 47.x |
| JDK | 17（javap 用 JDK 21 亦可） |
| 前置 | LDLib（`com.lowdragmc.ldlib:ldlib`，GTM 自动传递）、KubeJS 6.x（可选，联动用） |
| KJS 联动 | `dev.latvian.mods.kubejs:kubejs-forge-1.20.1:6.x` |

依赖声明（`build.gradle`）：

```groovy
repositories {
    maven {
        name = 'GTCEu Maven'
        url = 'https://maven.gtceu.com'
    }
}
dependencies {
    implementation fg.deobf("com.gregtechceu.gtceu:gtceu-1.20.1:7.5.3")
}
```

> ⚠️ 官方文档（GitHub `1.20.1` 分支 `docs/`）部分内容比 7.5.3 新（如
> `MultiblockPatternBuilder`、`MultiPredicate`、`MetaMachine#attachPersistentTrait` 等），
> 在 7.5.3 jar 中不存在，本文以 jar 实测为准，差异处已标注。

---

## 第 1 部分 总览

### 1.1 API 包结构（`com.gregtechceu.gtceu.api`）

```
api
├── GTCEuAPI / GTValues                    # 全局 API 入口与常量
├── addon                                  # @GTAddon / IGTAddon / AddonFinder / events
├── block / block.property                 # 方块基类与方块状态属性
├── blockentity                            # MetaMachineBlockEntity / PipeBlockEntity
├── capability
│   ├── (顶层接口) IEnergyContainer / IWorkable / ICoverable / ...
│   ├── compat                             # EU↔FE 兼容（EUToFEProvider / FeCompat）
│   ├── forge                              # GTCapability（Forge 能力注册）
│   └── recipe                             # RecipeCapability 体系（配方能力）
├── codec                                  # Mojang codec 辅助
├── cosmetics / cosmetics.event            # 披风系统
├── cover / cover.filter                   # 覆盖板 API
├── data
│   ├── chemical                           # Element / ChemicalHelper
│   │   └── material                       # Material 及属性/事件/注册表/stack
│   ├── damagesource                       # DamageTypeData
│   ├── medicalcondition                   # MedicalCondition / Symptom
│   ├── tag                                # TagPrefix / TagType / TagUtil
│   └── worldgen                           # 矿脉 / 基岩矿脉 / 生成器 / 修饰器
├── fluids / fluids.attribute / fluids.store / fluids.forge
├── gui（editor / factory / fancy / widget / misc / texture / util）
├── item（tool / armor / component / capability）
├── machine
│   ├── feature / feature.multiblock       # IMachineFeature 接口族
│   ├── fancyconfigurator
│   ├── multiblock / multiblock.part
│   ├── property / steam / trait
├── misc（EnergyContainerList 等 + virtualregistry + forge 辅助）
├── pattern（predicates / util / error）    # 多块结构模式
├── pipenet（含 longdistance）
├── placeholder（含 exceptions）
├── recipe（category / chance / condition / content / ingredient / lookup / modifier / ui）
├── registry（registrate 注册辅助）
├── sound
└── transfer（fluid / item）
```

### 1.2 核心注册表 `GTRegistries`（`com.gregtechceu.gtceu.api.registry.GTRegistries`）

```java
public static final GTRegistry<String, Element>              ELEMENTS;                    // 元素
public static final GTRegistry<RL, GTRecipeType>             RECIPE_TYPES;                // 配方类型
public static final GTRegistry<RL, GTRecipeCategory>         RECIPE_CATEGORIES;           // 配方分类
public static final GTRegistry<RL, CoverDefinition>          COVERS;                      // 覆盖板
public static final GTRegistry<RL, MachineDefinition>        MACHINES;                    // 机器
public static final GTRegistry<String, RecipeCapability<?>>  RECIPE_CAPABILITIES;         // 配方能力
public static final GTRegistry<String, RecipeConditionType<?>> RECIPE_CONDITIONS;        // 配方条件
public static final GTRegistry<String, ChanceLogic>         CHANCE_LOGICS;               // 概率逻辑
public static final GTRegistry<RL, SoundEntry>              SOUNDS;                      // 音效
public static final GTRegistry<RL, BedrockFluidDefinition>  BEDROCK_FLUID_DEFINITIONS;    // 基岩流体矿脉
public static final GTRegistry<RL, BedrockOreDefinition>    BEDROCK_ORE_DEFINITIONS;      // 基岩矿矿脉
public static final GTRegistry<RL, GTOreDefinition>         ORE_VEINS;                   // 矿物矿脉
public static final GTRegistry<RL, DimensionMarker>         DIMENSION_MARKERS;           // 维度图标
public static void init(IEventBus bus);          // 在 mod 构造中调用，注册全部注册表与事件监听
public static void updateFrozenRegistry(RegistryAccess access);
public static RegistryAccess builtinRegistry();
public static <V, T extends V> T register(Registry<V> reg, RL id, T value);
```

`GTRegistry<K, V>` 基类提供：`register / replace / registerOrOverride / remap / freeze /
unfreeze / get / getOrDefault / getKey / values / keys / entries / remove / codec()`。

`GTCEuAPI`（`com.gregtechceu.gtceu.api.GTCEuAPI`）：

```java
public static IMaterialRegistryManager materialManager;
public static final Map<ICoilType, Supplier<CoilBlock>> HEATING_COILS;     // 加热线圈注册
public static final Map<IFilterType, Supplier<Block>>   CLEANROOM_FILTERS; // 洁净室过滤器
public static final Map<IBatteryData, Supplier<BatteryBlock>> PSS_BATTERIES;
public static void initializeHighTier();   // 启用 UHV 以上档位（requireHighTier 联动）
public static boolean isHighTier();
```

`GTValues` 常用常量：`V[]`（各档 EU/t）、`VA[]`（含线损）、`VH[]`、`VHA[]`、`VEX[]`、
`VN[]/VNF[]/VLVH[]`（档位名）、`ULV..OpV` 档位索引、`ALL_TIERS`、`tiersBetween(int,int)`、
时间常量 `SECONDS/MINUTES/.../YEARS`。

### 1.3 GTAddon 生命周期（addon/events）

`@GTAddon` 注解 + `IGTAddon` 接口是附属模组的注册入口，由 `AddonFinder` 通过 SPI 自动发现：

```java
@GTAddon
public class MyAddon implements IGTAddon {
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MyMod.MOD_ID);

    @Override public GTRegistrate getRegistrate() { return REGISTRATE; }
    @Override public String addonModId() { return MyMod.MOD_ID; }

    @Override public void initializeAddon() {   // 最先调用：注册 GTCEuAPI 常量等
        // GTCEuAPI.materialManager.createRegistry(MyMod.MOD_ID);
    }
    @Override public boolean requiresHighTier() { return true; } // 需要 UHV+ 档位

    // 各注册回调（全部有 default 空实现，按需覆写）：
    @Override public void registerTagPrefixes() {}
    @Override public void registerElements() {}
    @Override public void registerMaterials() {}
    @Override public void registerSounds() {}
    @Override public void registerCovers() {}
    @Override public void registerRecipeCapabilities() {}
    @Override public void registerWorldgenLayers() {}
    @Override public void registerVeinGenerators() {}
    @Override public void registerIndicatorGenerators() {}
    @Override public void addRecipes(Consumer<FinishedRecipe> registry) {}   // 数据生成时
    @Override public void removeRecipes(Consumer<ResourceLocation> registry) {}
    @Override public void registerOreVeins() {}          // GTRegistries.ORE_VEINS 注册矿脉
    @Override public void registerFluidVeins() {}
    @Override public void registerBedrockOreVeins() {}
    @Override public void collectMaterialCasings(MaterialCasingCollectionEvent event) {}
    @Override public void registerRecipeKeys(KJSRecipeKeyEvent event) {}    // 自定义配方能力注册 KJS 键
}
```

addon/events 两个事件类：

```java
// KJSRecipeKeyEvent：给自定义 RecipeCapability 绑定 KJS 输入/输出组件（供 GTRecipeSchema 用）
public void registerKey(RecipeCapability<?> cap,
        Pair<ContentJS<?>, ContentJS<?>> inOutPair);
// MaterialCasingCollectionEvent：收集“材料→外壳方块”映射（用于部分机器外观）
public void add(Material material, BlockEntry<Block> casing);
```

主模组初始化模板（Mod 构造中）：

```java
@Mod(MyMod.MOD_ID)
public class MyMod {
    public MyMod(IEventBus bus) {
        GTRegistries.init(bus);
        GTCEuAPI.materialManager.createRegistry(MOD_ID);  // 可选：独立材料注册表
    }
}
```

---

## 第 2 部分 材料与元素系统

### 2.1 Material 类（`api.data.chemical.material.Material`）

常用实例方法（javap 实测）：

```java
// 属性/查询
Material setFormula(String formula);            // setFormula(String, boolean isGas)
String getChemicalFormula();
ImmutableList<MaterialStack> getMaterialComponents();
boolean isElement();  Element getElement();  long getProtons();  long getNeutrons();  long getMass();
boolean isRadioactive();  int getBlastTemperature();
boolean hasFlag(MaterialFlag flag);  void addFlags(MaterialFlag...);
<T extends IMaterialProperty> T getProperty(PropertyKey<T> key);
<T extends IMaterialProperty> void setProperty(PropertyKey<T> key, IMaterialProperty value);
<T extends IMaterialProperty> void removeProperty(PropertyKey<T> key);
boolean hasProperty(PropertyKey<?> key);
boolean isSolid();  boolean hasFluid();
// 流体
Fluid getFluid();  Fluid getFluid(FluidStorageKey key);
FluidStack getFluid(int amount);  FluidStack getFluid(FluidStorageKey key, int amount);
TagKey<Fluid> getFluidTag();  FluidBuilder getFluidBuilder();
Fluid getHotFluid();  Item getBucket();
// 颜色/图标
int getMaterialARGB();  int getMaterialRGB();  int getLayerARGB(int layer);
void setMaterialARGB(int);  void setMaterialSecondaryARGB(int);
MaterialIconSet getMaterialIconSet();  void setMaterialIconSet(MaterialIconSet);
// 其他
ResourceLocation getResourceLocation();  String getName();  String getUnlocalizedName();
MaterialStack multiply(long);  boolean shouldGenerateRecipesFor(TagPrefix prefix);
```

`Material.Builder`（`Material$Builder`，继承 `BuilderBase<Material>`）关键方法（按 javap 全量列出）：

```java
new Material.Builder(ResourceLocation id)
    .langValue(String)                    // 本地化名（默认取 id 路径）
    // —— 形态（决定生成哪些物品/流体）——
    .dust()  .dust(int harvestLevel)  .dust(int harvestLevel, int burnTime)
    .ingot() .ingot(int) .ingot(int, int)
    .gem()   .gem(int)   .gem(int, int)
    .polymer() .polymer(int) .polymer(int, int)
    .wood()  .wood(int) .wood(int, int)
    .fluid() .liquid() .liquid(FluidBuilder) .liquid(int temperature)
    .gas()   .gas(FluidBuilder) .gas(int)
    .plasma() .plasma(FluidBuilder) .plasma(int)
    .fluid(FluidStorageKey key, FluidState state) / .fluid(FluidStorageKey, FluidBuilder)
    .ore()   .ore(boolean emissive)  .ore(int oreMultiplier, int byproductMultiplier[, boolean emissive])
    // —— 外观 ——
    .color(int argb)  .color(int, boolean)  .secondaryColor(int)  .colorAverage()
    .iconSet(MaterialIconSet)
    // —— 组成 ——
    .components(Object... strings)   // 例：.components("2x copper", "1x tin")
    .componentStacks(MaterialStack...)
    .element(Element)  .formula(String[, boolean])
    // —— 标志/标签 ——
    .flags(MaterialFlag...)  .appendFlags(Collection, MaterialFlag...)
    .ignoredTagPrefixes(TagPrefix...)  .customTags(TagKey<Item>)
    // —— 属性 ——
    .toolStats(ToolProperty)  .armorStats(ArmorProperty)
    .rotorStats(int speed, int efficiency, float durabilityMultiplier, int level)
    .blastTemp(int temp)  .blastTemp(int, BlastProperty.GasTier)  .blastTemp(int, GasTier, int EUt[, int duration])
    .blast(int temp)  .blast(int, GasTier)  .blast(UnaryOperator<BlastProperty.Builder>)
    .hazard(HazardProperty.HazardTrigger, MedicalCondition[, float dmgPerSec, boolean isMild])
    .radioactiveHazard(float)  .removeHazard()
    .oreSmeltInto(Material)  .polarizesInto(Material)  .arcSmeltInto(Material)
    .macerateInto(Material)  .ingotSmeltInto(Material)
    .washedIn(Material[, int amount])  .separatedInto(Material...)
    .addOreByproducts(Material...)
    .cableProperties(long voltage, int amperage, int lossPerBlock[, boolean isSuperConductor[, int criticalTemp]])
    .fluidPipeProperties(int capacity, int maxTemperature, boolean gasProof[, boolean acidProof, boolean cryoProof, boolean plasmaProof])
    .itemPipeProperties(int priority, float rate)
    .burnTime(int)  .addDefaultEnchant(Enchantment, int level)
    // —— 收尾 ——
    .buildAndRegister()  // 等价 .register()
```

### 2.2 材料注册事件（`api.data.chemical.material.event`，全部为 IModBusEvent）

```java
MaterialRegistryEvent      // mod 总线：创建自定义 MaterialRegistry（按 modid）
MaterialEvent              // mod 总线：注册材料（Material.Builder）
PostMaterialEvent          // mod 总线：所有材料注册完成后（改属性/加配方）
```

```java
@SubscribeEvent
public static void registerMaterials(MaterialEvent event) {
    MyMaterials.REGISTER();
}
@SubscribeEvent
public static void postMaterials(PostMaterialEvent event) {
    // 此时 GTMaterials 全部可用，可安全修改
}
```

材料注册表：`MaterialRegistry`（继承 `GTRegistry$String<Material>`，提供
`register/getAllMaterials/setFallbackMaterial/getFallbackMaterial/getNetworkId/getModid`）；
`IMaterialRegistryManager`（`GTCEuAPI.materialManager`）提供
`createRegistry(String)/getRegistry(String)/getRegisteredMaterials()/
getMaterial(String)/getKey(Material)/getPhase()/canModifyMaterials()`。

### 2.3 材料标志 `MaterialFlags`（常用常量，全量见 javap）

```java
NO_UNIFICATION, DISABLE_MATERIAL_RECIPES, DECOMPOSITION_BY_ELECTROLYZING,
DECOMPOSITION_BY_CENTRIFUGING, DISABLE_DECOMPOSITION, EXPLOSIVE, FLAMMABLE, STICKY,
PHOSPHORESCENT, FIRE_RESISTANT,
GENERATE_PLATE, GENERATE_DENSE, GENERATE_ROD, GENERATE_BOLT_SCREW, GENERATE_FRAME,
GENERATE_GEAR, GENERATE_LONG_ROD, FORCE_GENERATE_BLOCK,
EXCLUDE_BLOCK_CRAFTING_RECIPES, EXCLUDE_PLATE_COMPRESSOR_RECIPE,
EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES, MORTAR_GRINDABLE,
NO_WORKING, NO_SMASHING, NO_SMELTING, NO_ORE_SMELTING, NO_ORE_PROCESSING_TAB,
BLAST_FURNACE_CALCITE_DOUBLE, BLAST_FURNACE_CALCITE_TRIPLE,
DISABLE_ALLOY_BLAST, DISABLE_ALLOY_PROPERTY,
SOLDER_MATERIAL, SOLDER_MATERIAL_BAD, SOLDER_MATERIAL_GOOD,
GENERATE_FOIL, GENERATE_RING, GENERATE_SPRING, GENERATE_SPRING_SMALL,
GENERATE_SMALL_GEAR, GENERATE_FINE_WIRE, GENERATE_ROTOR, GENERATE_ROUND,
IS_MAGNETIC, CRYSTALLIZABLE, GENERATE_LENS, HIGH_SIFTER_OUTPUT
```

`MaterialFlag.getByName(String)` 可反向查询。`MaterialIconSet` 常量：
`DULL/METALLIC/MAGNETIC/SHINY/BRIGHT/DIAMOND/EMERALD/GEM_HORIZONTAL/GEM_VERTICAL/RUBY/OPAL/
GLASS/NETHERSTAR/FINE/SAND/WOOD/ROUGH/FLINT/LIGNITE/QUARTZ/CERTUS/LAPIS/FLUID/RADIOACTIVE`；
`MaterialIconType` 常量：`dust/ingot/plate/gem/ore/block/rod/gear/rotor/...`（全量见 javap）。

### 2.4 材料属性 `PropertyKey`（`api.data.chemical.material.properties`）

```java
PropertyKey<IngotProperty>    INGOT      // ingot() 生成
PropertyKey<DustProperty>     DUST
PropertyKey<GemProperty>      GEM
PropertyKey<FluidProperty>    FLUID      // getStorage().enqueueRegistration(FluidStorageKey, FluidBuilder)
PropertyKey<OreProperty>      ORE        // oreMultiplier/byproductMultiplier/washedIn/...
PropertyKey<BlastProperty>    BLAST      // 高炉温度/气体档位/EUt
PropertyKey<AlloyBlastProperty> ALLOY_BLAST
PropertyKey<PolymerProperty>  POLYMER
PropertyKey<WireProperties>   WIRE       // 电压/电流/线损
PropertyKey<FluidPipeProperties> FLUID_PIPE
PropertyKey<ItemPipeProperties> ITEM_PIPE
PropertyKey<ToolProperty>     TOOL       // 工具耐久/效率/攻击/附魔
PropertyKey<ArmorProperty>    ARMOR
PropertyKey<RotorProperty>    ROTOR
PropertyKey<HazardProperty>   HAZARD     // 环境危害
PropertyKey<WoodProperty>     WOOD
```

### 2.5 元素 `Element` 与 `GTMaterials`

```java
// Element（api.data.chemical.Element）
new Element(long protons, long neutrons, long halfLifeSeconds,
            String decayTo, String name, String symbol, boolean isIsotope)
// 读取：mass()/protons()/neutrons()/halfLifeSeconds()/decayTo()/name()/symbol()
```

`GTMaterials`（`common.data.GTMaterials`）内置数百个静态 `Material` 字段（全部化学元素 +
合金 + 化工中间产物，javap 全量确认）：`Iron/Copper/Gold/Steel/StainlessSteel/Titanium/
Tungsten/Naquadah/Neutronium/.../Water/Steam/DistilledWater/SulfuricAcid/...`。另有：

```java
public static Material[] CHEMICAL_DYES;
public static Material[] VOLTAGE_COMMON_MATERIALS;
public static final List<MaterialFlag> STD_METAL;   // 标准金属默认标志组
public static final List<MaterialFlag> EXT_METAL;   // 附加金属标志组
public static final MarkerMaterial NULL;            // 空材料标记
```

`MarkerMaterials` 提供 `Empty` 等标记材料（镜头/染料系统用）。

### 2.6 ChemicalHelper 与材料栈

```java
// ChemicalHelper（api.data.chemical.ChemicalHelper）—— 材料物品统一入口
getMaterialInfo(Object) / getMaterialStack(ItemStack) / getMaterial(Fluid) / getPrefix(ItemLike)
getDust(Material, long)  getIngot(Material, long)  getIngotOrDust(Material, long)
getGem(MaterialStack)  getMaterialEntry(ItemLike) / getMaterialEntry(TagKey<Item>)
get(TagPrefix, Material, int)  get(MaterialEntry, int)
getBlock(TagPrefix, Material)  getBlockTag(TagPrefix, Material)  getTag(TagPrefix, Material)
getAllItemInfos()  getOrePrefix(BlockState)
// MaterialStack（不可变 record）：Material + long amount，支持 copy/add/multiply/divide/fromString("2x iron")
// MaterialEntry（record）：TagPrefix + Material，ChemicalHelper.get(MaterialEntry) 取物品
// ItemMaterialInfo：物品→材料成分映射，GTItems.materialInfo(...) 挂到物品上
```

### 2.7 TagPrefix 前缀体系（`api.data.tag.TagPrefix`）

常量（全量见 javap）：`ore/oreGranite/oreDeepslate/oreNetherrack/oreEndstone/...`、
`rawOre/rawOreBlock/crushed/crushedPurified/crushedRefined`、`dust/dustSmall/dustTiny/
dustImpure/dustPure`、`ingot/ingotHot/gem/gemChipped/gemFlawed/gemFlawless/gemExquisite`、
`nugget/plate/plateDouble/plateDense/foil/rod/rodLong/bolt/screw/ring/spring/springSmall/
wireFine/rotor/gear/gearSmall/lens/round/frameGt/block/log/planks/rock/surfaceRock`、
管道类 `pipeTinyFluid..pipeNonupleFluid/pipeSmallItem..pipeHugeItem/pipe*Restrictive`、
线缆类 `wireGtSingle/Double/Quadruple/Octal/Hex`、`cableGtSingle..Hex`、工具头 `toolHead*`。

核心方法（javap 实测）：

```java
getPrefix(String) / get(String)          // 查前缀
getMaterialAmount(Material)              // 该前缀对某材料生成物品的材质用量（如 ingot=144）
doGenerateItem(Material) / doGenerateBlock(Material)
getItemTags(Material) / getBlockTags(Material)
setIgnored(Material, ItemLike...)        // 跳过某些材料的该前缀物品生成
modifyMaterialAmount(Material, float)    // 调整数量（如半锭）
// 7.5.3 无独立 Builder 类；直接 new TagPrefix(String) 或
// oreTagPrefix(String, TagKey<Block>) 后链式设置（字段即链式方法）：
prefix.langValue("x").idPattern("...").materialAmount(144).unificationEnabled(true)
      .generateItem(true).itemConstructor(...).generateBlock(...).blockConstructor(...)
      .generationCondition(mat -> ...).materialIconType(...).tooltip(...).maxStackSize(64)
      .defaultTagPath("dusts")            // 生成 c:forge 标签路径
      .prefixTagPath("ingots")  .prefixOnlyTagPath("ingots")  .unformattedTagPath("...")
      .customTagPath(...)  .miningToolTag(TagKey<Block>)  .enableRecycling()
```

`TagType`：`withDefaultFormatter/withPrefixFormatter/withPrefixOnlyFormatter/withNoFormatter/
withCustomFormatter/withCustomFilter`。`TagUtil`：`createItemTag/createBlockTag/
createFluidTag/createModItemTag/optionalTag/createTag` 等标签工具。

---

## 第 3 部分 物品与方块

### 3.1 GTItems / GTBlocks / GTMaterialItems（`common.data`）

- `GTItems`：所有非材料物品静态字段（`ItemEntry<ComponentItem>` 等），如
  `FLUID_CELL/FLUID_CELL_UNIVERSAL`、`SHAPE_MOLD_*/SHAPE_EXTRUDER_*`、`PROGRAMMED_CIRCUIT`、
  `ELECTRIC_MOTOR_LV..OpV`、`BATTERY_*`、`ENERGY_LAPOTRONIC_ORB`、`TOOL_DATA_STICK/ORB/MODULE`、
  电路系列、`COVER_*` 系列、`ITEM_FILTER/TAG_FILTER/SMART_ITEM_FILTER/FLUID_FILTER/TAG_FLUID_FILTER`、
  装甲 `NANO_*/QUANTUM_*/HAZMAT_*`、`TURBINE_ROTOR`、`VOLTAGE_COIL_*` 等（javap 全量确认）。
  注册助手：

```java
public static void init();
attach(IItemComponent...)                              // 给 ComponentItem 挂组件（registrate consumer）
unificationItem(TagPrefix, Material)                   // 物品→统一化映射
materialInfo(ItemMaterialInfo)                         // 物品材料成分
modelPredicate(RL, StackProperty)                     // 物品模型谓词（电量条等）
createFluidCell(Material, capacity, maxTemp, color)    // 创建流体单元
burnTime(int)  cauldronInteraction(T item)
```

- `GTBlocks`：`CASING_*` 系列（`CASING_STEEL_SOLID/CASING_TITANIUM_STABLE/...`）、
  `COIL_*`（线圈方块）、`CASING_TEMPERED_GLASS/CASING_LAMINATED_GLASS`、`LASER_PIPES/
  OPTICAL_PIPES/DUCT_PIPES`、`LD_ITEM_PIPE/LD_FLUID_PIPE`、`MATERIALS_TO_CASINGS` 映射等。
- `GTMaterialItems`：`MATERIAL_ITEMS` 表（TagPrefix×Material→ItemEntry）、`TOOL_ITEMS` 表、
  `ARMOR_ITEMS` 表、`generateMaterialItems()/generateTools()/generateArmors()`。

### 3.2 自定义物品：ComponentItem 组件体系（重点）

`ComponentItem` 是 GTM 现代版自定义物品的统一基类：`ComponentItem.create(Properties)` 创建，
再 `attachComponents(IItemComponent...)` 组合行为。组件接口（`api.item.component`，全量）：

```java
IItemComponent                       // 基接口：onAttached(Item)
IAddInformation                      // appendHoverText 工具提示
ICustomDescriptionId                 // 自定义描述 id
ICustomRenderer                      // 自定义渲染器
IDataItem                            // 数据物品（数据棒/球）
IDurabilityBar                       // 耐久条
IEdibleItem                          // 食物行为
IEnchantableItem                     // 附魔行为
IInteractionItem                     // use/onItemUseFirst 等交互
IItemAttributes                      // 属性修饰
IItemDecoratorComponent              // GUI 中物品装饰绘制
IItemHUDProvider                     // HUD 提供（护甲 UI）
IItemLifeCycle                       // inventoryTick/onCrafted 生命周期
IItemUIFactory                       // 手持 UI（LDLib ModularUI）
IMaterialPartItem                    // 材料部件
IMonitorModuleItem                   // 中央监控模块
IRecipeRemainder                     // 合成残留物
ISubItemHandler                      // 子物品（如满/空电池）
// forge 侧：
IComponentCapability                 // getCapability(ItemStack, Capability<T>)
```

内置组件实现（直接可用的“零件”）：

```java
// ElectricStats —— 电动物品
ElectricStats.create(long maxCharge, int tier, boolean chargeable, boolean dischargeable)
ElectricStats.createElectricItem(long maxCharge, int tier)
ElectricStats.createRechargeableBattery(long maxCharge, int tier)
ElectricStats.createBattery(long maxCharge, int tier, boolean rechargeable)
// ThermalFluidStats —— 流体容器（温度/耐性）
ThermalFluidStats.create(int capacity, int maxFluidTemperature,
                         boolean gasProof, boolean acidProof, boolean cryoProof,
                         boolean plasmaProof, boolean allowPartialFill)
// FoodStats —— 食物
new FoodStats(FoodProperties, boolean isDrink, boolean alwaysEdible, Supplier<ItemStack> container)
// FilteredFluidContainer —— 带过滤的流体容器
```

示例：

```java
public static final ItemEntry<ComponentItem> MY_ITEM = REGISTRATE
    .item("my_item", ComponentItem::create)
    .properties(p -> p.stacksTo(64))
    .onRegister(GTItems.attach(
        ElectricStats.createElectricItem(100000, GTValues.MV),
        new FoodStats(new FoodProperties.Builder().nutrition(2).build())
    ))
    .register();
```

### 3.3 材料物品与机器物品

```java
TagPrefixItem(Properties, TagPrefix, Material)     // 材料物品（粉尘/锭/板...）
MaterialBlockItem(Block, Properties, TagPrefix, Material)  // 材料方块物品（含染色）
MetaMachineItem(IMachineBlock block, Properties)   // 机器物品，getDefinition() 取 MachineDefinition
// 常用：
GTItems.unificationItem(TagPrefix.ingot, GTMaterials.Iron)  // 挂到 registrate builder 上做统一化
```

### 3.4 工具系统（`api.item.tool`）

`GTToolType` 常量：`SWORD/PICKAXE/SHOVEL/AXE/HOE/MINING_HAMMER/SPADE/SCYTHE/SAW/HARD_HAMMER/
SOFT_MALLET/WRENCH/FILE/CROWBAR/SCREWDRIVER/MORTAR/WIRE_CUTTER/KNIFE/BUTCHERY_KNIFE/PLUNGER/
SHEARS` + 电动系列 `DRILL_LV..IV/CHAINSAW_LV..IV/WRENCH_LV..IV/WIRE_CUTTER_LV..IV/BUZZSAW/
SCREWDRIVER_LV..IV`。字段：`name/idFormat/symbol/materialAmount/electricTier/soundEntry/...`。

```java
// 自定义工具类型（Java）
GTToolType.builder("my_tool")
    .toolTag(TagUtil.createItemTag("my_tools"))         // 工具标签
    .harvestTag(TagUtil.createBlockTag("mineable/my_tool"))  // 挖掘标签
    .defaultActions(ToolActions.PICKAXE_DIG)            // 默认 ToolAction
    .toolClasses(GTToolType.PICKAXE)                    // 继承其他工具类
    .toolStats(b -> b.blockBreaking().crafting().aoe(3, 3, 1))  // 定义
    .sound(GTSoundEntries.WRENCH_TOOL)
    .build();

// IGTToolDefinition 构建（ToolDefinitionBuilder）：
b.blockBreaking()   // 可挖方块
  .attacking()      // 可攻击
  .crafting()       // 可用于合成（锤/扳手类）
  .aoe(int x, int y, int z)          // 范围挖掘（AoESymmetrical）
  .effectiveBlocks(...)  .effectiveStates(Predicate<BlockState>)
  .baseDurability(int)  .durabilityMultiplier(float)
  .baseEfficiency(float)  .efficiencyMultiplier(float)
  .attackDamage(float)  .attackSpeed(float)
  .defaultEnchantment(Enchantment, int)
  .canApplyEnchantment(BiPredicate<ItemStack, Enchantment>)
  .damagePerAction(int)  .damagePerCraftingAction(int)
  .brokenStack(Supplier<ItemStack>)
```

`MaterialToolTier`：由材料 `ToolProperty` 自动生成原版 `Tier`（`new MaterialToolTier(material)`）。
`IGTTool` 接口：工具统一行为（`getToolType()/getMaterial()/isElectric()/getElectricTier()/
getToolStats()/definition$*` 系列默认方法）。`TreeFellingHelper`：砍树工具辅助。

---

## 第 4 部分 机器系统

### 4.1 MachineDefinition 与 Builder

`MachineDefinition`（`api.machine.MachineDefinition`）全部属性（javap 实测，均为
`getX/setX` 对）：`id/getName/getLangValue`、`recipeTypes`、`tier`、`defaultPaintingColor`、
`recipeModifier`、`alwaysTryModifyRecipe`、生命周期回调 `beforeWorking(BiPredicate<
IRecipeLogicMachine,GTRecipe>)/onWorking(Predicate)/onWaiting(Consumer)/afterWorking(Consumer)`、
`regressWhenWaiting`、`rotationState`、`shape(VoxelShape)`、`renderWorldPreview/renderXEIPreview`、
`tooltipBuilder`、`appearance(Supplier<BlockState>)`、`allowCoverOnFront`、`editableUI`、
`recipeOutputLimits(Reference2IntMap<RecipeCapability<?>>)`、`allowExtendedFacing`；
工厂：`createMetaMachine(IMachineBlockEntity)`、`asStack()`、`getBlock()/getItem()/
getBlockEntityType()`。

`MultiblockMachineDefinition` 增加：`patternFactory(Supplier<BlockPattern>)`、`shapes`、
`generator`（输出能量机）、`allowFlip`、`recoveryItems`（维护机回收物）、`partSorter`、
`partAppearance`、`additionalDisplay(BiConsumer<IMultiController,List<Component>>)`、
`renderXEIPreview`。

### 4.2 注册机器（GTRegistrate + MachineBuilder）

```java
public static final MachineDefinition MY_MACHINE = REGISTRATE
    .machine("my_machine", holder -> new SimpleTieredMachine(holder, 1, tank -> 4 * tank))
    .rotationState(RotationState.NON_Y_AXIS)
    .recipeType(GTRecipeTypes.ASSEMBLER_RECIPES)      // 或 .recipeTypes(...)
    .tier(GTValues.MV)
    .defaultModel()                                    // 或 overlayTieredHullModel("...")
    .register();
```

`MachineBuilder` 全参数（javap 实测，链式均返回 TYPE）：

```java
machine(Function<IMachineBlockEntity, MetaMachine>)   // 机器实现类
rotationState(RotationState)  allowExtendedFacing(boolean)
tier(int)  paintingColor(int)
recipeType(GTRecipeType)  recipeTypes(GTRecipeType...)
recipeModifier(RecipeModifier[, boolean alwaysTry])  recipeModifiers(RecipeModifier...)  noRecipeModifier()
beforeWorking(...)  onWorking(...)  onWaiting(...)  afterWorking(...)  regressWhenWaiting(boolean)
addOutputLimit(RecipeCapability<?>, int)  recipeOutputLimits(map)
shape(VoxelShape)  appearance(Supplier<BlockState>)  allowCoverOnFront(boolean)
blockProp(UnaryOperator<BlockBehaviour.Properties>)  itemProp(...)
blockBuilder(Consumer<BlockBuilder>)  itemBuilder(...)  onBlockEntityRegister(...)
model(...)  simpleModel(RL)  defaultModel()  tieredHullModel(RL)
overlayTieredHullModel(String|RL)  colorOverlayTieredHullModel(...)
workableTieredHullModel(RL)  simpleGeneratorModel(RL)
overlaySteamHullModel(...)  workableSteamHullModel(boolean, RL)
workableCasingModel(RL base, RL active)  sidedOverlayCasingModel(...)  sidedWorkableCasingModel(...)
appearanceBlock(Supplier<? extends Block>)
abilities(PartAbility...)             // 多块部件能力注册
modelProperty(Property<?>)  modelProperties(...)  removeModelProperty(...)
tooltips(Component...)  conditionalTooltip(Component, boolean)
tooltipBuilder(BiConsumer<ItemStack,List<Component>>)
editableUI(EditableMachineUI)  langValue(String)
renderMultiblockWorldPreview(boolean)  renderMultiblockXEIPreview(boolean)
multiblockPreviewRenderer(boolean, boolean)
hasBER(boolean)  itemColor(BiFunction<ItemStack,Integer,Integer>)
// KJS 专用：kjs$modelPropertyBool/Int/Enum
```

`MultiblockMachineBuilder` 增加：

```java
pattern(Function<MultiblockMachineDefinition, BlockPattern>)
shapeInfo(Function<..., MultiblockShapeInfo>)  shapeInfos(...)
generator(boolean)  allowFlip(boolean)
partSorter(Comparator<IMultiPart> | Function<MultiblockControllerMachine, Comparator<IMultiPart>>)
partAppearance(TriFunction<IMultiController, IMultiPart, Direction, BlockState>)
additionalDisplay(BiConsumer<IMultiController, List<Component>>)
recoveryItems(Supplier<ItemLike[]>)  recoveryStacks(Supplier<ItemStack[]>)
```

### 4.3 机器类层级

```java
MetaMachine（一切机器基类：holder、traits、tick 订阅、工具交互 onWrenchClick/onScrewdriverClick...）
├── TieredMachine（tier 档位）
│   ├── TieredEnergyMachine（energyContainer + 爆炸检查 IExplosionMachine）
│   │   └── WorkableTieredMachine（RecipeLogic + 配方 IO trait：importItems/exportItems/
│   │       importFluids/exportFluids/importComputation/exportComputation，capabilitiesProxy 查找表）
│   │       ├── SimpleTieredMachine（标准单块：电路槽+充电槽+自动输出）
│   │       └── SimpleGeneratorMachine（发电机：配方产电）
├── MultiblockControllerMachine（多块控制器：isFormed、parts、pattern 检查）
│   └── WorkableMultiblockMachine（RecipeLogic + capabilitiesProxy 聚合部件能力）
│       └── WorkableElectricMultiblockMachine（能量输入输出 + FancyUI + 显示文本）
│           ├── CoilWorkableElectricMultiblockMachine（线圈温度）
│           └── TieredWorkableElectricMultiblockMachine（定档）
└── （多块部件）multiblock.part.MultiblockPartMachine
    ├── TieredPartMachine
    └── TieredIOPartMachine（IO 舱体：IO.IN/OUT 方向配置）
```

蒸汽机器（`machine.steam`）：`SteamMachine（高低压 STEEL_PROPERTY + steamTank）→
SteamWorkableMachine → SimpleSteamMachine（需要排气）/ SteamBoilerMachine（锅炉，温度模拟）`；
`SteamEnergyRecipeHandler` 把蒸汽换算成能量配方 handler（构造参数 `(NotifiableFluidTank, double conversionRate)`）。

### 4.4 feature 接口（`machine.feature`）

单块特征（全量 javap 确认）：`IMachineFeature`（基）、`IRecipeLogicMachine`（配方逻辑机，
含 `getRecipeTypes/getRecipeType/doModifyRecipe/fullModifyRecipe/keepSubscribing/beforeWorking/
onWorking/onWaiting/afterWorking/isWorkingEnabled/getProgress/isActive`）、`ITieredMachine`、
`IOverclockMachine`（`getOverclockTier/setOverclockTier/getMaxOverclockTier/getMinOverclockTier/
getOverclockVoltage`）、`IMufflableMachine`、`IFancyUIMachine`、`IHasCircuitSlot`、`IVoidable`
（`VoidingMode` 溢出模式）、`IAutoOutputItem/IAutoOutputFluid/IAutoOutputBoth`、
`IExplosionMachine`（`doExplosion/checkWeatherOrTerrainExplosion`）、`IDataInfoProvider`
（便携扫描仪数据）、`ICleanroomProvider`（洁净室）、`IInteractedMachine`、
`IEnvironmentalHazardEmitter/Cleaner`、`IExhaustVentMachine`（排气）、`IRedstoneSignalMachine`、
`IUIMachine`、`IMachineLife`、`IMachineModifyDrops`、`IDropSaveMachine`、`IDataStickInteractable`、
`ILocalizedHazardEmitter`。

多块特征（`feature.multiblock`）：`IMultiController`（控制器：`checkPattern/isFormed/
getParts/onStructureFormed/onStructureInvalid/getParallelHatch/isBatchEnabled`）、`IMultiPart`
（部件：`hasController/getControllers/addedToController/removedFromController/
getRecipeHandlers/modifyRecipe/addMultiText`）、`IWorkableMultiController`、
`IDisplayUIMachine`（`addDisplayText/handleDisplayClick`）、`IMaintenanceMachine`
（维护：`getMaintenanceProblems/causeRandomMaintenanceProblems/calculateMaintenance/...
getDurationMultiplier/getTimeMultiplier`）、`IMufflerMachine`（`recoverItemsTable` 回收物）、
`IRotorHolderMachine`（转子：`getRotorStack/getRotorSpeed/getTotalEfficiency/getTotalPower`）、
`IDistinctPart`（区分总线）、`IFluidRenderMulti`、`IMufflerMechanic`。

### 4.5 trait 系统（`machine.trait`）

```java
MachineTrait                            // trait 基类（machine 引用、capabilityValidator、同步存储）
├── NotifiableRecipeHandlerTrait<T>     // 可参与配方匹配的 trait（distinct/监听器）
│   ├── NotifiableItemStackHandler      // 物品（handlerIO/capabilityIO、handleRecipeInner、setFilter）
│   ├── NotifiableFluidTank             // 流体（getStorages、setLocked、importFromNearby/exportToNearby）
│   ├── NotifiableEnergyContainer       // 能量（emitterContainer/receiverContainer 工厂、amps）
│   ├── NotifiableComputationContainer  // 计算力（CWU）
│   └── NotifiableLaserContainer        // 激光
├── RecipeLogic                         // 配方逻辑（searchRecipe/findAndHandleRecipe/handleRecipeWorking/
│                                       //  onRecipeFinish/setStatus(Status)/getLastRecipe/...）
├── RecipeHandlerList                   // 配方 handler 分组（distinct、优先级、subscribe）
├── RecipeAmperageEnergyContainer       // 电流容器
└── ItemHandlerProxyTrait / FluidTankProxyTrait   // 代理 trait
// 分组常量
RecipeHandlerGroup（ITEM/FLUID/ENERGY...）  RecipeHandlerGroupColor / Distinctness
```

```java
// 用法：自定义机器构造函数里创建并挂载
public MyMachine(IMachineBlockEntity holder, int tier, Object... args) {
    super(holder, tier, args);
    this.importItems = createImportItemHandler(args);
    // 7.5.3 挂载方式：MetaMachine.attachTraits(MachineTrait)（⚠️ attachTrait/attachPersistentTrait 为更新版本 API）
}
```

`RecipeLogic.Status` 枚举：`IDLE/WORKING/WAITING/SUSPEND`。tick 订阅：
`TickableSubscription` + `ConditionalSubscriptionHandler`。

### 4.6 多块结构模式（`api.pattern`）

7.5.3 使用 `FactoryBlockPattern`（⚠️ 官方新文档的 `MultiblockPatternBuilder` 为更新版本）：

```java
FactoryBlockPattern.start()                          // 或 start(RelativeDirection up, down, right)
    .aisle("XXX", "CCC", "CCC", "XXX")               // 一层
    .aisleRepeatable(min, max, "XXX", "C#C", ...)    // 可重复层
    .setRepeatable(int)                              // 整体重复
    .where('X', predicate)                           // 字符→谓词
    .build();                                        // → BlockPattern
```

`TraceabilityPredicate` 方法：`setController()/setMinGlobalLimited(int)/setMaxGlobalLimited/
setMinLayerLimited/setMaxLayerLimited/setExactLimit(int)/setPreviewCount(int)/
disableRenderFormed()/setIO(IO)/setSlotName(String)/setNBTParser(String)/or(TraceabilityPredicate)/
addTooltips(Component...)/sort()`。

`Predicates` 静态工厂（javap 实测）：

```java
controller(TraceabilityPredicate)  states(BlockState...)  blocks(Block...)
machines(MachineDefinition...)  blockTag(TagKey<Block>)  fluids(Fluid...)
fluidTag(TagKey<Fluid>)  custom(Predicate<MultiblockState>, Supplier<BlockInfo[]>)
any()  air()  lamps(...)  anyLamp()  lampsByColor(DyeColor)
abilities(PartAbility...)  ability(PartAbility, int...)
autoAbilities(GTRecipeType...)                        // 按配方类型自动注入能量/物品/流体舱
autoAbilities(GTRecipeType[], boolean energyIn, boolean energyOut, boolean itemIn,
              boolean itemOut, boolean fluidIn, boolean fluidOut)
autoAbilities(boolean maintenance, boolean muffler, boolean parallel)
heatingCoils()  cleanroomFilters()  powerSubstationBatteries()  dataHatchPredicate(...)
frames(Material...)
```

底层谓词（`pattern.predicates`）：`PredicateBlocks/PredicateStates/PredicateBlockTag/
PredicateFluids/PredicateFluidTag/SimplePredicate`（字段 `candidates/predicate/minCount/
maxCount/minLayerCount/maxLayerCount/previewCount/io/slotName/nbtParser`）。
`BlockPattern`：`checkPatternAt(MultiblockState, boolean)/getPreview(int[])/autoBuild`。
`MultiblockShapeInfo.builder()`：JEI/REI 预览结构（`.aisle(...).where(char, block, Direction)`）。
`PatternMatchContext`：模式匹配上下文（`get/getOrCreate/increment`，线圈温度等放这里）。
错误：`PatternError/PatternStringError/SinglePredicateError`。`RelativeDirection`：相对方向枚举。

### 4.7 部件能力 `PartAbility` 与洁净室

```java
PartAbility.EXPORT_ITEMS / IMPORT_ITEMS / EXPORT_FLUIDS / IMPORT_FLUIDS
PartAbility.INPUT_ENERGY / OUTPUT_ENERGY / SUBSTATION_INPUT_ENERGY / ...
PartAbility.ROTOR_HOLDER / PUMP_FLUID_HATCH / STEAM / MAINTENANCE / MUFFLER / TANK_VALVE
PartAbility.PASSTHROUGH_HATCH / PARALLEL_HATCH / INPUT_LASER / OUTPUT_LASER
PartAbility.COMPUTATION_DATA_RECEPTION / TRANSMISSION / OPTICAL_DATA_RECEPTION / TRANSMISSION
PartAbility.DATA_ACCESS / HPCA_COMPONENT / OBJECT_HOLDER
new PartAbility("my_ability").register(int tier, Block block)   // 自定义能力
// CleanroomType：CLEANROOM / STERILE_CLEANROOM + getByName
```

### 4.8 维护与显示文本

`MultiblockDisplayText.builder(List<Component>, boolean isFormed)` 链式构建多块状态文本。
`IMaintenanceMachine`：`MAINTENANCE_TAPED_PROPERTY`、问题位掩码 `ALL_PROBLEMS`、`getDurationMultiplier`
（每问题 +10% 时长）等。

### 4.9 机器模型属性

`GTMachineModelProperties`（`machine.property`）：`IS_PAINTED/IS_FORMED/IS_TAPED/
RECIPE_LOGIC_STATUS/IS_WORKING_ENABLED/IS_ACTIVE/IS_STEEL_MACHINE/VENT_DIRECTION/CHARGER_STATE/
IS_FE_TO_EU/IS_TRANSFORM_UP/DIODE_AMP_MODE/IS_HPCA_PART_DAMAGED/IS_RANDOM_TICK_MODE/HAS_ROTOR/
IS_ROTOR_SPINNING/IS_EMISSIVE_ROTOR`。`GTBlockStateProperties`：`UPWARDS_FACING/
NORTH_ONLY_FACING/VERTICAL_FACING/ACTIVE/BLOOM/INVERTED/NATURAL`。

---

## 第 5 部分 配方系统

### 5.1 GTRecipeType（`api.recipe.GTRecipeType`）

```java
new GTRecipeType(RL registryName, String group, RecipeType<?>... proxyTypes)
    .setMaxIOSize(int itemIn, int itemOut, int fluidIn, int fluidOut)
    .setEUIO(IO)                                       // 能量进出方向
    .setMaxSize(IO, RecipeCapability<?>, int)          // 单能力数量上限
    .setSlotOverlay(boolean isItem, boolean isOutput, IGuiTexture)   // 槽位贴图
    .setProgressBar(ResourceTexture, ProgressTexture.FillDirection)
    .setSteamProgressBar(SteamTexture, ...)
    .setUiBuilder(BiConsumer<GTRecipe, WidgetGroup>)   // JEI 自定义 UI
    .setMaxTooltips(int)
    .setXEIVisible(boolean)
    .addDataInfo(Function<CompoundTag, String>)        // 扫描仪额外信息行
    .setMinRecipeConditions(int)
    .addCustomRecipeLogic(ICustomRecipeLogic)          // 自定义匹配逻辑
    .setSound(SoundEntry)
    .setScanner(boolean)  .setHasResearchSlot(boolean)
    .setRecipeUI(GTRecipeTypeUI)  .setSmallRecipeMap(GTRecipeType)
    .setIconSupplier(Supplier<ItemStack>)
    .setChanceFunction(ChanceBoostFunction)            // 概率提升函数（默认 OVERCLOCK）
    .setRecipeBuilder(GTRecipeBuilder)                 // 自定义 builder 类
    // 配方构建：
    GTRecipeBuilder recipeBuilder(String id)  /  recipeBuilder(RL id, Object...)
    // 查询：
    searchRecipe(IRecipeCapabilityHolder, Predicate<GTRecipe>)
    db()  // → RecipeDB
```

内置类型见 `GTRecipeTypes`（`common.data.GTRecipeTypes`）：`FURNACE_RECIPES/ALLOY_SMELTER/
ASSEMBLER/MACERATOR/CHEMICAL_RECIPES/BLAST_RECIPES/FUSION_RECIPES/ASSEMBLY_LINE_RECIPES/
DISTILLATION_RECIPES/...`（全量 javap 确认），以及分组字符串
`STEAM/ELECTRIC/GENERATOR/MULTIBLOCK/DUMMY`。`GTRecipeTypes.register(String, String, RecipeType...)`
可注册自定义类型（Java 侧）。

### 5.2 GTRecipe 与 GTRecipeBuilder（`data.recipe.builder.GTRecipeBuilder`）

`GTRecipe` 结构（javap 实测）：`inputs/outputs/tickInputs/tickOutputs`（能力→Content 列表）、
`inputChanceLogics/outputChanceLogics/tickInputChanceLogics/tickOutputChanceLogics`、
`conditions`（RecipeCondition 列表）、`data`（NBT）、`duration/parallels/subtickParallels/
batchParallels/ocLevel`、`recipeCategory`。方法：`copy()/copy(ContentModifier)/getInputContents/
getOutputContents/hasTick()/getInputEUt()/getOutputEUt()/getTotalRuns()`。

`GTRecipeBuilder` 核心方法（javap 全量，均返回 builder 自身）：

```java
// 能量
inputEU(long EUt)  inputEU(long voltage, long amperage)  EUt(long)  EUt(long, long)
outputEU(long)  outputEU(long, long)
// 计算力
inputCWU(int)  CWUt(int)  totalCWU(int)  outputCWU(int)
// 物品输入
inputItems(ItemStack|Item|TagKey<Item>|Ingredient|Supplier<? extends Item>|MaterialEntry|TagPrefix+Material|MachineDefinition, [int count])
inputItemRanged(IntProviderIngredient)  inputItemsRanged(ItemStack|Item|TagPrefix+Material|MachineDefinition, IntProvider)
inputItemNbtPredicate(ItemStack, NBTPredicate)
// 物品输出
outputItems(...同 inputItems 系列...)  outputItemRanged(...)  outputItemsRanged(...)
// 流体
inputFluids(FluidStack|FluidIngredient|Material+int)  inputFluidsRanged(FluidStack|FluidIngredient, IntProvider)
outputFluids(FluidStack|FluidIngredient)  outputFluidsRanged(...)
// 不消耗/电路
notConsumable(ItemStack|Ingredient|Item|Supplier|TagPrefix+Material)   // 输入不消耗
notConsumableFluid(FluidStack|FluidIngredient)
circuitMeta(int)                       // 编程电路（1-32）
// 概率
chancedInput(Ingredient|FluidIngredient|ItemStack|FluidStack, int chance, int tierChanceBoost)
chancedOutput(ItemStack|FluidStack|Ingredient|FluidIngredient|TagPrefix+Material, int chance, int boost)
chancedOutput(ItemStack, String tierBoostKey, int chance)    // 按配方数据键提升
chancedOutputLogic(RecipeCapability<?>, ChanceLogic)         // OR/AND/FIRST/XOR/NONE
chancedItemOutputLogic(...) chancedFluidOutputLogic(...) chancedInputLogic(...)
// 条件（对应 RecipeCondition）
blastFurnaceTemp(int)  explosivesAmount(int)  explosivesType(ItemStack)
solderMultiplier(int)  disableDistilleryRecipes(boolean)
fusionStartEU(long)  researchScan(boolean)  durationIsTotalCWU(boolean)  hideDuration(boolean)
cleanroom(CleanroomType)  dimension(RL|ResourceKey<Level>[, boolean reverse])
biome(RL|ResourceKey<Biome>[, boolean])  biomeTag(TagKey<Biome>[, boolean])
rain(float[, boolean])  thunder(float[, boolean])  posY(int min, int max[, boolean])
environmentalHazard(MedicalCondition[, boolean])
adjacentFluids(Fluid...|TagKey<Fluid>...[, boolean])  adjacentBlocks(Block...|TagKey<Block>...[, boolean])
daytime(boolean)  gameStage(String[, boolean])  ftbQuest(String[, boolean])  heraclesQuest(String[, boolean])
// 研究
scannerResearch(UnaryOperator<ResearchRecipeBuilder.ScannerRecipeBuilder>)  scannerResearch(ItemStack)
stationResearch(UnaryOperator<ResearchRecipeBuilder.StationRecipeBuilder>)
researchWithoutRecipe(String[, ItemStack])
// 其他
addData(String, Tag|int|long|float|boolean|String)   // 配方自定义数据（扫描仪/条件用）
duration(int)  perTick(boolean)  chance(int)  maxChance(int)  tierChanceBoost(int)
category(GTRecipeCategory)  onSave(BiConsumer<GTRecipeBuilder, Consumer<FinishedRecipe>>)
addMaterialInfo(boolean[, boolean])  removePreviousMaterialInfo()
id(RL)  recipeType(GTRecipeType)
build()  save(Consumer<FinishedRecipe>)  buildRawRecipe()  toJson(JsonObject)
```

### 5.3 RecipeCapability（`api.capability.recipe`）

```java
// 基类
RecipeCapability<T>(String name, int color, boolean doRenderSlot, int sortIndex,
                    IContentSerializer<T> serializer)
    .copyInner(T) .copyWithModifier(T, ContentModifier) .of(Object)
    .getMaxParallelByInput(holder, recipe, parallel, onlyFullInput)   // 并行计算钩子
    .limitMaxParallelByOutput(...)
    .compressIngredients(Collection<Object>)  .getDefaultMapIngredient(Object)
    .isRecipeSearchFilter()  .shouldBypassDistinct()
// 内置实现（GTRecipeCapabilities，common.data）：
RecipeCapability<Ingredient>      ITEM          // ItemRecipeCapability.CAP
RecipeCapability<FluidIngredient> FLUID         // FluidRecipeCapability.CAP
RecipeCapability<EnergyStack>     EU            // EURecipeCapability.CAP
RecipeCapability<Integer>         CWU           // CWURecipeCapability.CAP（计算力）
RecipeCapability<BlockState>      BLOCK_STATE   // BlockStateRecipeCapability.CAP
// IO 枚举：IN / OUT / BOTH / NONE（support(IO) 判断包含）
```

```java
// 自定义配方能力（Java addon，KJS 侧需配合 KJSRecipeKeyEvent 注册键）
public class MyCapability extends RecipeCapability<MyType> {
    public static final MyCapability CAP = new MyCapability();
    protected MyCapability() { super("my_type", 0xFF0000, true, 0,
        new IContentSerializer<MyType>() { ... }); }
    // GTRegistries.RECIPE_CAPABILITIES.register("my_type", CAP); 在 registerRecipeCapabilities 中注册
}
```

### 5.4 Content / ContentModifier / 概率逻辑

```java
Content(Object content, int chance, int maxChance, int tierChanceBoost)
// chance == maxChance 时非概率。isChanced() 判断。
ContentModifier.multiplier(double)  ContentModifier.addition(double)
// ChanceLogic：OR（任一成功即出）、AND（全成功才出）、FIRST（首个成功）、XOR（恰好一个）、NONE
// ChanceBoostFunction：OVERCLOCK（超频提升）、NONE；getBoostedChance(Content, int baseChance, int ocLevel)
```

### 5.5 RecipeCondition（`api.recipe` + `api.recipe.condition`）

```java
// 自定义条件：继承 RecipeCondition<T>，实现
RecipeConditionType<T> getType();   // 工厂+codec，注册到 GTRegistries.RECIPE_CONDITIONS
Component getTooltips();  boolean testCondition(GTRecipe, RecipeLogic);
T createTemplate();
// 内置条件（builder 方法见 5.2）：Biome/Dimension/PosY/Rain/Thunder/AdjacentFluids/AdjacentBlocks/
// Cleanroom/FusionStartEU/StationResearch/ScannerResearch/EnvironmentalHazard/Daytime/
// FTBQuest/GameStage/HeraclesQuest/Vent（蒸汽机自动添加）
```

### 5.6 RecipeModifier 与超频/并行

```java
// 接口：getModifier(MetaMachine, GTRecipe) → ModifierFunction
// ModifierFunction：apply(GTRecipe) → GTRecipe；compose/andThen；cancel(Component) 取消配方
ModifierFunction.builder()                        // 构建器
// 工具类
ParallelLogic.getParallelAmount(MetaMachine, GTRecipe, int maxParallel)
ParallelLogic.getMaxByInput(holder, recipe, maxParallel, caps)
ParallelLogic.limitByOutputMerging(...)
OverclockingLogic.PERFECT_OVERCLOCK / NON_PERFECT_OVERCLOCK / _SUBTICK 系列
OverclockingLogic.create(double voltageFactor, double durationFactor, boolean subtick)
OverclockingLogic.standardOC(params, voltage, voltageFactor, durationFactor)
OverclockingLogic.heatingCoilOC(params, voltage, currentTemp, recipeTemp)   // 线圈配方
EfficiencyModifier.of(double euDiscount, double speedBoost, double maxSpeed)
// 内置修改器集合：GTRecipeModifiers（common.data）—— ELECTRIC_OVERCLOCK.apply(PERFECT_OVERCLOCK) 等
// KJS 侧别名：MachineModifiers（见第 11 部分）
```

### 5.7 配方查找（lookup）

```java
RecipeDB.find(IRecipeCapabilityHolder[, Predicate<GTRecipe>])   // 主查询入口
RecipeDB.iterator(holder, predicate)                            // 遍历
StagingRecipeDB.add(GTRecipe)  →  populateDB(RecipeDB)          // 数据生成分批入库
RecipeAdditionHandler.beginStaging()/addStaging(recipe)/completeStaging()
MapIngredientPool.applyPooling(...)                             // 标签合并优化
// 内部：Branch / AbstractMapIngredient（ItemStack/ItemTag/NBTPredicate/FluidStack/FluidTag/...）
```

### 5.8 配方分类与 UI

```java
GTRecipeCategory(RL, GTRecipeType) / registerDefault(GTRecipeType)
   .setIcon(IGuiTexture)  .setXEIVisible(boolean)  .addRecipe(GTRecipe)
GTRecipeTypeUI(GTRecipeType)  // JEI 页面；createUITemplate/createEditableUITemplate/setSlotOverlay...
// 配方序列化：GTRecipeSerializer（json/network codec）、SerializerIngredient/FluidIngredient/
// EnergyStack/BlockState/Integer/Long/Float/Double/BigInteger（IContentSerializer 各实现）
```

---

## 第 6 部分 能力与交互

### 6.1 顶层能力接口（`api.capability`）

```java
IEnergyContainer            // 能量容器：acceptEnergyFromNetwork/inputsEnergy/outputsEnergy/
                            //   changeEnergy/addEnergy/removeEnergy/getEnergyStored/getEnergyCapacity/
                            //   getInputVoltage/getInputAmperage/getOutputVoltage/getOutputAmperage/
                            //   getInputPerSec/getOutputPerSec/getEnergyInfo
IWorkable                   // getProgress/getMaxProgress/isActive（ACTIVE_PROPERTY）
IControllable               // isWorkingEnabled/setWorkingEnabled/setSuspendAfterFinish（WORKING_ENABLED_PROPERTY）
IElectricItem               // 电动物品：charge/discharge/canProvideChargeExternally/getTransferLimit/
                            //   getMaxCharge/getCharge/getTier
ICoverable                  // 覆盖板宿主：canPlaceCoverOnSide/setCoverAtSide/getCoverAtSide/
                            //   placeCoverOnSide/removeCover/getCovers/...（详见 6.4）
IToolable                   // Pair<GTToolType, InteractionResult> onToolClick(Set<GTToolType>, ItemStack, UseOnContext)
IDataAccessHatch            // 数据舱：isRecipeAvailable/modifyRecipe/isCreative
ILaserContainer             // 激光（继承 IEnergyContainer）
IOpticalComputationProvider // 计算力：requestCWUt/getMaxCWUt/canBridge
IParallelHatch              // getCurrentParallel
IThermalFluidHandlerItemStack // 容器温限：getMaxFluidTemperature/isGasProof/isAcidProof/isCryoProof/isPlasmaProof
ICleanroomReceiver / ICentralMonitor / IMonitorComponent / IMedicalConditionTracker
IMiner / ITurbineMachine / IHPCA*（HPCA 部件/计算/冷却）/ IObjectHolder / IPropertyFluidFilter
IHazardParticleContainer / IEnergyInfoProvider（+EnergyInfo）
```

### 6.2 Forge 能力注册（`capability.forge.GTCapability`）

```java
CAPABILITY_ENERGY_CONTAINER / CAPABILITY_ENERGY_INFO_PROVIDER / CAPABILITY_COVERABLE /
CAPABILITY_TOOLABLE / CAPABILITY_WORKABLE / CAPABILITY_CONTROLLABLE / CAPABILITY_RECIPE_LOGIC /
CAPABILITY_ELECTRIC_ITEM / CAPABILITY_CLEANROOM_RECEIVER / CAPABILITY_MAINTENANCE_MACHINE /
CAPABILITY_TURBINE_MACHINE / CAPABILITY_LASER / CAPABILITY_COMPUTATION_PROVIDER /
CAPABILITY_DATA_ACCESS / CAPABILITY_HAZARD_CONTAINER / CAPABILITY_MONITOR_COMPONENT /
CAPABILITY_CENTRAL_MONITOR / CAPABILITY_MEDICAL_CONDITION_TRACKER
GTCapability.register(RegisterCapabilitiesEvent)   // mod 总线
```

查询助手 `GTCapabilityHelper`：`getEnergyContainer(level,pos,dir)/getWorkable/getControllable/
getCoverable/getToolable/getRecipeLogic/getForgeEnergy/getElectricItem(item)/getItemHandler/
getFluidHandler/getLaser/getOpticalComputationProvider/getDataAccess/...`。

### 6.3 EU↔FE 兼容（`capability.compat`）

```java
FeCompat.toFe(long eu, int ratio)  FeCompat.toEu(long fe, int ratio)
FeCompat.insertEu(IEnergyStorage, long, boolean)  extractEu(...)
EUToFEProvider(BlockEntity)        // 给机器方块实体自动提供 IEnergyStorage 包装
CapabilityCompatProvider           // 兼容提供器基类
// 默认比例：Forge 能量 1EU = 4FE（config 可调，ratio(isForgeEnergySide)）
```

### 6.4 覆盖板 API（`api.cover`）

```java
// 注册
new CoverDefinition(RL id, CoverBehaviourProvider provider, Supplier<Supplier<ICoverRenderer>> renderer)
// 或 CoverDefinition(RL, (def, coverable, side) -> new MyCover(def, coverable, side), ...)
// 在 IGTAddon.registerCovers() 中注册到 GTRegistries.COVERS
// CoverBehavior 基类（重点覆写点）：
canAttach()  onAttached(ItemStack, ServerPlayer)  onLoad/onUnload/onRemoved()
onNeighborChanged(Block, BlockPos, boolean)  onScrewdriverClick/onSoftMalletClick(...)
canPipePassThrough()  shouldRenderPlate()  getConfigurator()          // FancyUI 配置器
getItemHandlerCap(IItemHandlerModifiable)  getFluidHandlerCap(...)   // 管道侧 IO 包装
setRedstoneSignalOutput(int)  canConnectRedstone()
// 接口
IIOCover：getTransferRate()/getIo()/getManualIOMode()
IUICover：createUIWidget() 等
// 过滤器（cover.filter）
FilterHandler / FilterHandlers.item(managed)/fluid(managed)     // 过滤器槽位+配置 UI
ItemFilter / FluidFilter / TagFilter / TagItemFilter / TagFluidFilter /
SimpleItemFilter / SimpleFluidFilter / SmartItemFilter（智能物品过滤）
// 内置覆盖板物品见 GTItems.COVER_*；放置/移除：ICoverable.placeCoverOnSide / removeCover
```

### 6.5 transfer 与 misc

```java
// 流体
CustomFluidTank(int capacity[, Predicate<FluidStack>])      // Forge FluidTank + setFluidInTank/变更回调
IFluidHandlerModifiable                                       // +setFluidInTank/supportsFill/supportsDrain
FluidHandlerList(IFluidHandler...) / ModifiableFluidHandlerWrapper / FluidHandlerDelegate
// 物品
CustomItemStackHandler(int size[, filter])                   // Forge ItemStackHandler + 变更回调/过滤
ItemHandlerDelegate(IItemHandlerModifiable)                  // 委托基类
// 杂项
EnergyContainerList(List<IEnergyContainer>)                  // 多块能量聚合（calculateVoltageAmperage）
IOFluidHandlerList(handlers, IO, inFilter, outFilter)        // 方向过滤流体列表
ItemRecipeHandler(IO, int size)                              // 轻量配方物品 handler
EnergyInfoProviderList / LaserContainerList / IOFilteredInvWrapper / IgnoreEnergyRecipeHandler
ImageCache / RateCounter(LongSupplier, int window)
```

---

## 第 7 部分 流体与化学

### 7.1 流体体系（`api.fluids`）

```java
GTFluid（抽象基类，继承 FlowingFluid + IAttributedFluid）
  new GTFluid(FluidState state, Supplier<Fluid> still, Supplier<Fluid> flowing,
              Supplier<LiquidBlock> block, Supplier<Item> bucket, int burnTime)
FluidState：LIQUID / GAS / PLASMA（带各自标签与译名）
FluidConstants：ROOM_TEMPERATURE/SOLID_LIQUID_TEMPERATURE/BASE_PLASMA_TEMPERATURE/...
FluidBuilder  （材料流体构建）
  .temperature(int) .color(int) .disableColor() .density(...) .luminosity(int)
  .viscosity(...) .burnTime(int) .attribute(FluidAttribute) .attributes(...)
  .customStill() .textures(boolean[, boolean]) .block() .disableBucket()
  .still(RL) .flowing(RL) .name(String) .translation(String) .state(FluidState)
  .build(Material, FluidStorageKey, GTRegistrate)
// 流体属性（attribute）
FluidAttribute(RL, Consumer<Consumer<Component>> fluidTooltips, Consumer<...> containerTooltips)
FluidAttributes.ACID        // 内置：酸属性
IAttributedFluid            // getAttributes()/addAttribute()/getState()
PropertyFluidFilter(int maxTemp, boolean gasProof, boolean cryoProof, boolean plasmaProof, boolean acidProof)
```

### 7.2 流体存储（`fluids.store`）

```java
FluidStorageKey            // 一种流体"槽位"键（RL + 标签 + 图标类型 + 注册优先级）
FluidStorageKeys.LIQUID / GAS / PLASMA / MOLTEN
FluidStorage / FluidStorageImpl
  .enqueueRegistration(FluidStorageKey, FluidBuilder)   // 登记待注册流体
  .get(FluidStorageKey)  .store(FluidStorageKey, Supplier<Fluid>, FluidBuilder)
// Material.getFluid(FluidStorageKey) 取特定形态流体；getFluidBuilder(key) 改属性
// GTFluids（common.data）：POTION 等非材料流体；handleNonMaterialFluids 挂钩
// forge.GTFluidImpl（$Source/$Flowing）：Forge 实现
// 注册：GTRegistrate.createFluid(...) / fluid(registrate, material, name, lang, still, flowing) → GTFluidBuilder
// GTFluidBuilder 链式：temperature/density/luminance/viscosity/color/burnTime/state/
//   defaultBlock()/defaultBucket()/defaultLang()/tag(...)/noBlock()/noBucket()
```

### 7.3 化学公式

`Material.setFormula("H2O")` / `setFormula(String, boolean isGas)`；`getChemicalFormula()`；
`MaterialStack.fromString("2x iron")`；成分自动生成分解配方（配合
`DECOMPOSITION_BY_ELECTROLYZING/CENTRIFUGING` 标志）。

---

## 第 8 部分 世界生成

### 8.1 矿脉 GTOreDefinition（`api.data.worldgen.GTOreDefinition`）

```java
GTOreDefinition 的 Builder 风格方法（全链式返回自身）：
  .clusterSize(IntProvider|int)  .density(float)  .weight(int)     // 权重
  .layer(IWorldGenLayer)          // STONE/DEEPSLATE/NETHERRACK/ENDSTONE 或自定义
  .dimensions(Set<ResourceKey<Level>>) / .dimensions(RL...)
  .biomes(TagKey<Biome>) / .biomes(Supplier<HolderSet<Biome>>) / .kjs$biomes(String, String...)
  .heightRangeUniform(int min, int max)  .heightRangeTriangle(int, int)
  .heightRange(HeightRangePlacement)
  .discardChanceOnAirExposure(float)
  .biomeWeightModifier(BiomeWeightModifier)
  // 生成器：
  .standardVeinGenerator(Consumer<StandardVeinGenerator>)
  .layeredVeinGenerator(...)  .geodeVeinGenerator(...)  .dikeVeinGenerator(...)
  .veinedVeinGenerator(...)  .classicVeinGenerator(...)  .cuboidVeinGenerator(...)
  .veinGenerator(RL)          // 按注册名选生成器
  // 地表指示物：
  .surfaceIndicatorGenerator(Consumer<SurfaceIndicatorGenerator>)
  .register(RL)               // 注册进 GTRegistries.ORE_VEINS
```

### 8.2 生成器与指示物（`worldgen.generator`）

```java
VeinGenerator（抽象）            // getAllEntries/getAllBlocks/getAllMaterials/generate(...)/codec()
VeinGenerators                   // NO_OP/STANDARD/LAYER/GEODE/DIKE/VEINED/CLASSIC/CUBOID
                                 // register(RL, Codec<T>, Function<GTOreDefinition,T>) 注册自定义
StandardVeinGenerator            // withBlock/withNetherBlock/withMaterial
ClassicVeinGenerator             // primary/secondary/between/sporadic(层)/yRadius/rules
VeinedVeinGenerator              // oreBlock(Material|BlockState, int weight)/rareBlock/
                                 // fillerBlock/minYLevel/maxYLevel/veininessThreshold/...
GeodeVeinGenerator / DikeVeinGenerator / LayeredVeinGenerator / CuboidVeinGenerator / NoopVeinGenerator
IndicatorGenerator（抽象）       // generate → Map<ChunkPos, OreIndicatorPlacer>
IndicatorGenerators              // NO_OP / SURFACE
SurfaceIndicatorGenerator        // IndicatorPlacement（地表/水底等）
```

### 8.3 基岩流体/基岩矿矿脉

```java
// BedrockFluidDefinition（基岩流体矿脉，fluid 钻机抽取）
BedrockFluidDefinition.builder(RL)
    .weight(int)  .yield(int min, int max)  .depletionAmount(int)
    .depletionChance(int)  .depletedYield(int)
    .fluid(Supplier<Fluid>)  .dimensions(Set<ResourceKey<Level>>)
    .register();   // 注册到 GTRegistries.BEDROCK_FLUID_DEFINITIONS
// BedrockOreDefinition（基岩矿脉，需开启 doBedrockOres 配置）
BedrockOreDefinition.builder(RL)
    .weight(int)  .size(int)  .yield(IntProvider)  .depletionAmount(int)
    .depletionChance(int)  .depletedYield(int)
    .materials(List<WeightedMaterial>)   // new WeightedMaterial(Material, chance)
    .dimensions(...)  .register();
// 数据保存：BedrockFluidVeinSavedData / BedrockOreVeinSavedData / FluidVeinWorldEntry / OreVeinWorldEntry
```

### 8.4 层、修饰器与生成执行

```java
WorldGenLayers：STONE / DEEPSLATE / NETHERRACK / ENDSTONE（getTarget() RuleTest、getLevels()）
IWorldGenLayer：isApplicableForLevel(RL) / getLevels() / getTarget()
SimpleWorldGenLayer  // 自定义层：new SimpleWorldGenLayer(name, targets, dimensions)
GTLayerPattern.builder(RuleTest...)  // 层模式（含 rollNext 概率）
BiomeWeightModifier(Supplier<HolderSet<Biome>>, int addedWeight)
modifier.BiomePlacement / DimensionFilter / FrequencyModifier(float)  // PlacementModifier 实现
ores.OreGenerator.generateMetadata/generateIndicators/generateOres
ores.GeneratedVein / GeneratedIndicators / OreBlockPlacer / OreIndicatorPlacer / OreGenCache
ores.OreVeinUtil.canPlaceOre/getVeinCenter/resolveBiomes
WorldGeneratorUtils：VEIN_GENERATORS / INDICATOR_GENERATORS 注册表、getCachedBiomeVeins、
  isSameDimension、generateChunks、invalidateOreVeinCache
// 自定义矿脉生成器：extends VeinGenerator，实现 codec() + generate() + build() + copy()，
// 在 IGTAddon.registerVeinGenerators() 中 VeinGenerators.register(RL, codec, factory)
```

### 8.5 其他

`DimensionMarker(int tier, RL, String overrideName)`（维度图标，注册到
`GTRegistries.DIMENSION_MARKERS`）、`RotationState`（ALL/NONE/Y_AXIS/NON_Y_AXIS）。

---

## 第 9 部分 GUI 与外观

### 9.1 GUI 组件（`api.gui`）

```java
GuiTextures            // 内置贴图常量（KJS 中名为 GTGuiTextures）：
                       //   BACKGROUND/BACKGROUND_INVERSE/SLOT/SLOT_DARK/FLUID_SLOT/
                       //   DISPLAY/TOGGLE_BUTTON_BACK/TOOL_*/BUTTON_*/PROGRESS_* 等
SteamTexture           // 蒸汽版贴图（高低压两态）
UITemplate.bindPlayerInventory(Inventory, IGuiTexture, int x, int y, boolean includeHotbar)
WidgetUtils            // 小工具辅助
// 机器 UI 编辑（gui.editor）
EditableMachineUI(String groupName, RL uiPath, Supplier<WidgetGroup> supplier,
                  BiConsumer<WidgetGroup, MetaMachine> binder)   // 机器可编辑 UI
GTUIEditor             // LDLib UI 编辑器扩展（GT_CONTAINER 工具箱）
MachineUIProject / RecipeTypeUIProject / TemplateTab / UIMainPanel / IEditableUI
// Fancy UI（gui.fancy）
FancyMachineUIWidget(IFancyUIProvider, int w, int h)   // 机器主界面（含侧栏标签页）
IFancyUIProvider       // createMainPage/attachSideTabs/attachConfigurators/attachTooltips/getTitle
IFancyConfigurator     // getTitle/getIcon/createConfigurator + 网络同步默认方法
ConfiguratorPanel / TabsWidget / VerticalTabsWidget / TitleBarWidget / TooltipsPanel / PageSwitcher
IFancyConfiguratorButton(+Toggle) / IFancyCustomClientActionHandler /
IFancyCustomMiddleClickAction / IFancyCustomMouseWheelAction / IFancyTooltip
// 常用 widget（api.gui.widget，基于 LDLib Widget）
SlotWidget(IItemHandlerModifiable|Container, int slot, int x, int y[, boolean canTake, boolean canPut])
TankWidget(IFluidHandler, int tank, int x, int y[, int w, int h, boolean showAmount, boolean allowClick])
PhantomFluidWidget / PhantomSlotWidget / GhostCircuitSlotWidget / BlockableSlotWidget
EnumSelectorWidget / IntInputWidget / LongInputWidget / NumberInputWidget / ColorBlockWidget
DualProgressWidget / ExtendedProgressWidget / PredicatedButtonWidget / PredicatedImageWidget
ToggleButtonWidget / ConfirmTextInputWidget / CoverConfigurator / PatternPreviewWidget /
PatternPreviewSlotWidget / ProspectingMapWidget / ScrollablePhantomFluidWidget
// 方向配置器（gui.widget.directional）
IDirectionalConfigHandler(+ScreenSide) / CombinedDirectionalConfigurator /
handlers.AutoOutputItemConfigHandler / AutoOutputFluidConfigHandler / CoverableConfigHandler
// 其他
gui.factory.MachineUIFactory / CoverUIFactory / GTUIEditorFactory
gui.misc.ProspectorMode（ORE/FLUID/BEDROCK_ORE，探测仪模式）
gui.texture.ProspectingTexture / gui.util.TimedProgressSupplier
```

```java
// 给机器挂可编辑 UI 的示例（SimpleTieredMachine 的默认写法）：
MachineDefinition def = REGISTRATE.machine(...)
    .editableUI(SimpleTieredMachine.EDITABLE_UI_CREATOR.apply(id, recipeType))
    ...
```

### 9.2 音效（`api.sound` + `common.data.GTSoundEntries`）

```java
// Java 注册（官方文档确认：自定义音效仅支持 Java）
public static final SoundEntry MY_SOUND = REGISTRATE.sound(GTCEu.id("my_sound"))
    .subtitle("sound.gtceu.my_sound")
    .addVariant("my_sound")            // assets/<modid>/sounds/my_sound.ogg（建议单声道）
    .build();
// SoundEntry 播放：play(level, player, pos, vol, pitch) / playOnServer / playAt / playAutoReleasedSound
// 其他实现：CustomSoundEntry / ExistingSoundEntry / WrappedSoundEntry（+CompiledSoundEvent）
// 数据生成：new SoundEntryBuilder.SoundEntryProvider(packOutput, modid) 加到 GatherDataEvent
// 内置音效：GTSoundEntries（common.data）—— MACHINE_START/MACHINE_STOP/COMPRESSOR_TOOL/
//   WRENCH_TOOL/CUT_TOOL/TURBINE/BOILER 等
```

### 9.3 披风（`api.cosmetics`）

```java
// Forge 事件 RegisterGTCapesEvent（FMLCommonSetupEvent 阶段触发）
event.registerCape(RL capeId, RL texturePath);
event.registerFreeCape(RL capeId, RL texturePath);   // 免费发放
event.unlockCapeFor(UUID player, RL capeId);
// 运行期：CapeRegistry.registerCape/getPlayerCapeId/getUnlockedCapes/unlockCape/setActiveCape
```

### 9.4 placeholder（屏幕/中央监控文本占位符）

```java
Placeholder（抽象）：apply(PlaceholderContext, List<MultiLineComponent>) throws PlaceholderException
PlaceholderUtils.checkArgs/checkRange/toInt/toLong/toDouble/toFloat
PlaceholderContext（level/pos/side/itemStackHandler/cover/previousText/uuid/index）
IPlaceholderRenderer / MultiLineComponent / GraphicsComponent / PlaceholderHandler
// 异常子类：InvalidArgs/InvalidNumber/MissingItem/NoMENetwork/NotEnoughArgs/NotSupported/
//   OutOfRange/UnclosedBracket/UnexpectedBracket/UnknownPlaceholder/WrongNumberOfArgs
```

---

## 第 10 部分 其他 API

### 10.1 虚拟寄存器（`misc.virtualregistry`，末影链路用）

```java
VirtualEnderRegistry.getInstance()          // SavedData 单例
   .getOrCreateEntry(UUID, EntryTypes<T>, String name) / getEntry / hasEntry / deleteEntry
EntryTypes.ENDER_FLUID / ENDER_ITEM / ENDER_REDSTONE
EntryTypes.addEntryType(RL, Supplier<E>)    // 注册自定义条目类型
VirtualEntry（setColor/setDescription/canRemove/serializeNBT）
entries.VirtualTank（getFluidTank）/ VirtualItemStorage（getHandler）/ VirtualRedstone
```

### 10.2 医疗条件与环境危害（`data.medicalcondition`）

```java
MedicalCondition(String name, int color, int maxProgression,
                 IdleProgressionType type, float idleRate, boolean canBePermanent,
                 ConfiguredSymptom... symptoms)
   .setRecipeModifier(Consumer<GTRecipeBuilder>)     // 该环境下的配方修改（如空气洗涤器）
   .getDamageSource(Level)
Symptom（内置常量）：DEATH/RANDOM_DAMAGE/HEALTH_DEBUFF/ATTACK_SPEED_DEBUFF/WEAKNESS/
   SLOWNESS/AIR_SUPPLY_DEBUFF/BLINDNESS/NAUSEA/MINING_FATIGUE/WITHER/POISONING/HUNGER/...
// 追踪器：capability.IMedicalConditionTracker + GTCapabilityHelper.getMedicalConditionTracker(entity)
// 配方条件 environmentalHazard(MedicalCondition) 见第 5 部分
```

### 10.3 伤害类型（`data.damagesource`）

```java
DamageTypeData.builder()      // key/id/type(爆炸/火焰...)/tags
   .source(LevelAccessor[, Entity attacker[, Entity indirect]])
   .is(DamageSource)  .register(BootstapContext<DamageType>)
// ⚠️ 7.5.3 无独立的 DamageSources 常量类（官方仓库早期版本有）；伤害实例经 MedicalCondition.getDamageSource 创建
```

### 10.4 codec 与数据辅助（`api.codec`）

```java
GTCodecUtils.longRange(min, max) / longRangeWithMessage / lazyParsingCodec / unboxEither
DispatchedMapCodec(keyCodec, key -> valueCodec)   // 分派 Map codec
```

### 10.5 管道网络（`api.pipenet`）

```java
PipeNet<NodeData>（抽象）         // 节点网络：getAllNodes/getNodeAt/updateNodeData/
                                  //   removeNode/uniteNetworks/serializeNBT
LevelPipeNet<NodeData, T>         // 维度级管道数据（SavedData）
IPipeNode<PipeType, NodeData>     // 管道方块实体接口：setConnections/isConnected/setBlocked/...
IPipeType<NodeData>               // getThickness/modifyProperties/isPaintable
Node<NodeData>（data/openConnections/mark/isActive）
PipeNetWalker<T, NodeData, Net>   // 网络遍历器（物品/流体网络路径搜索用）
longdistance：LongDistanceNetwork / LongDistancePipeType（fluid()/item()）/ ILDEndpoint /
  ILDNetworkPart / NetworkBuilder / LongDistancePipeBlock
// 材料管道类型实现：common.pipelike.fluidpipe.FluidPipeType / itempipe.ItemPipeType
//   （fluidPipeProperties/itemPipeProperties 属性决定速率与耐温）
```

### 10.6 注册辅助 GTRegistrate（`registry.registrate`）

```java
GTRegistrate.create(String modid).registerRegistrate().registerEventListeners(bus)
// 机器：machine(name, machineFactory[, blockFactory, itemFactory, beFactory])
//       multiblock(name, controllerFactory[, ...])
// 方块：block(String, NonNullFunction<BlockBehaviour.Properties, T>) → GTBlockBuilder
//        （gtBlockstate/exBlockstate/addLayer/simpleItem/blockEntity/color/defaultLoot...）
// 流体：createFluid(String, String lang, Material, still, flowing) → GTFluidBuilder
// 音效：sound(RL) → SoundEntryBuilder
// 创意标签：creativeModeTab()
BuilderBase<T>（id/value/generateDataJsons/generateAssetJsons/generateLang/register/get）
// 数据生成：GTBlockstateProvider（+$BlockStateGeneratorWrapper）—— GT 方块状态数据生成
```

### 10.7 方块实体与方块基类

```java
MetaMachineBlockEntity（机器方块实体，含 metaMachine 实例与能力分发）
   .getCapability(Level, BlockPos, Direction) 静态重载
PipeBlockEntity<PipeType, NodeData>（管道方块实体）
ICopyable（配置复制：copyConfig/pasteConfig/getItemsRequiredToPaste —— 数据棒/软锤）
IPaintable（染色）  ITickSubscription（subscribeServerTick/unsubscribe）
MetaMachineBlock / IMachineBlock / MaterialBlock / OreBlock / MaterialPipeBlock / PipeBlock /
ActiveBlock（active 纹理切换）/ AppearanceBlock / IAppearance / RendererMaterialBlock /
SimpleCoilType（线圈：temperature/level/tier/energyDiscount）/ ICoilType / BlockAttributes /
ICoilType.ALL_COILS_TEMPERATURE_SORTED / ICoilType.getMinRequiredType(int)
```

---

## 第 11 部分 KubeJS 联动（重点）

### 11.1 事件总览（`integration.kjs`）

```java
// GTCEuStartupEvents（启动阶段）：
GROUP.registry('gtceu:...')          // 各注册表事件，见 11.2
GROUP.materialModification(...)      // GTCEuStartupEvents.materialModification
GROUP.craftingComponents(...)        // GTCEuStartupEvents.craftingComponents
// GTCEuServerEvents（服务端）：
oreVeins              // GTCEuServerEvents.oreVeins(event => ...)
bedrockOreVeins
fluidVeins
registerCapes
```

```js
// 事件用法一览
GTCEuStartupEvents.registry('gtceu:material', event => { ... })
GTCEuStartupEvents.materialModification(event => { ... })
GTCEuStartupEvents.craftingComponents(event => { ... })
GTCEuServerEvents.oreVeins(event => { ... })
GTCEuServerEvents.bedrockOreVeins(event => { ... })
GTCEuServerEvents.fluidVeins(event => { ... })
GTCEuServerEvents.registerCapes(event => { ... })
```

### 11.2 GTRegistryEventJS：可注册的注册表（`GTRegistryInfo` 确认）

```js
// event.create(id[, type])，type 缺省用注册表默认类型
GTCEuStartupEvents.registry('gtceu:element', event => { event.create('my_element') ... })
GTCEuStartupEvents.registry('gtceu:material', event => { ... })       // 默认类型 material
GTCEuStartupEvents.registry('gtceu:recipe_type', event => { ... })
GTCEuStartupEvents.registry('gtceu:recipe_category', event => { ... })
GTCEuStartupEvents.registry('gtceu:machine', event => { ... })        // 类型：simple/steam/multiblock/generator/kinetic
GTCEuStartupEvents.registry('gtceu:material_icon_set', event => { ... })
GTCEuStartupEvents.registry('gtceu:material_icon_type', event => { ... })
GTCEuStartupEvents.registry('gtceu:world_gen_layer', event => { ... })
GTCEuStartupEvents.registry('gtceu:tag_prefix', event => { ... })     // 类型：basic/ore
GTCEuStartupEvents.registry('gtceu:dimension_marker', event => { ... })
```

### 11.3 元素（ElementBuilder）

```js
GTCEuStartupEvents.registry('gtceu:element', event => {
   event.create('test_element')
        .protons(27)          // 非元素用 -1
        .neutrons(177)        // 非元素用 -1
        .halfLifeSeconds(-1)  // 不衰变用 -1
        .decayTo(null)        // 不衰变用 null
        .symbol('test')
        .isIsotope(false)
})
```

### 11.4 材料创建（Material 注册事件，官方文档示例）

```js
GTCEuStartupEvents.registry('gtceu:material', event => {
    event.create('andesite_alloy')
        .ingot()                              // 可选参数：.ingot(2) 采掘等级；.ingot(2, 2000) +燃料时长
        .components('1x andesite', '1x iron') // 'Kx material_name'
        .color(0x839689).iconSet(GTMaterialIconSet.DULL)
        .flags(GTMaterialFlags.GENERATE_PLATE, GTMaterialFlags.GENERATE_GEAR,
               GTMaterialFlags.GENERATE_SMALL_GEAR)

    event.create('mysterious_dust')
        .dust()                               // 可选 .dust(2, 4000)
        .color(0x7D2DDB)

    event.create('purple_coal')
        .gem(2, 4000)
        .element(GTElements.C)
        .ore(2, 3)                            // (矿乘数, 副产物乘数[, 发光])
        .color(0x7D2DDB).iconSet(GTMaterialIconSet.LIGNITE)

    event.create('mysterious_ooze')
        .fluid()                              // 或 .liquid(温度K) / .gas() / .plasma()
        .color(0x500bbf)
})
// 可用链式（与 Java Builder 对应）：ingot/dust/gem/polymer/wood/fluid/liquid/gas/plasma/ore/
//   burnTime/fluidBurnTime/components/element/iconSet/color/secondaryColor/
//   flags/addDefaultEnchant/...
// 液体方块：.liquid(new $FluidBuilder().block().temperature(3100))
// 电压常量：GTValues.V[EV] / VA / VH / VHA（含线损）
```

### 11.5 修改已有材料（MaterialModificationEventJS + registry 事件内 setProperty）

```js
// 方式一：materialModification 事件（材料已全部注册完成后）
GTCEuStartupEvents.materialModification(event => {
    GTMaterials.BismuthBronze.setMaterialARGB(0x82AD92)
})

// 方式二：registry 事件内改属性（需 Java.loadClass 属性类）
const $IngotProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.IngotProperty');
const $BlastProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.BlastProperty');
const $WireProperties = Java.loadClass("com.gregtechceu.gtceu.api.data.chemical.material.properties.WireProperties");

GTCEuStartupEvents.registry('gtceu:material', event => {
    GTMaterials.Zirconium.setProperty(PropertyKey.INGOT, new $IngotProperty());
    GTMaterials.Zirconium.setProperty(PropertyKey.BLAST, new $BlastProperty(8000, 'higher', GTValues.VA[GTValues.MV], 8000));
    GTMaterials.RedAlloy.removeProperty(PropertyKey.WIRE);           // 先删后设
    GTMaterials.RedAlloy.setProperty(PropertyKey.WIRE, new $WireProperties(8, 1, 0, true));
    GTMaterials.Lead.addFlags(GTMaterialFlags.GENERATE_GEAR);
    GTMaterials.get('custom_material_name').addFlags(GTMaterialFlags.GENERATE_FOIL);
})

// 给已有材料加流体（FluidStorage 体系）：
const $FluidProperty = Java.loadClass('com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty');
const $FluidBuilder = Java.loadClass('com.gregtechceu.gtceu.api.fluids.FluidBuilder');
const $FluidStorageKeys = Java.loadClass('com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys');
let addFluid = (mat, key) => {
    let prop = new $FluidProperty();
    prop.getStorage().enqueueRegistration(key, new $FluidBuilder());
    mat.setProperty(PropertyKey.FLUID, prop);
}
GTCEuStartupEvents.registry('gtceu:material', event => {
    addFluid(GTMaterials.Iodine, $FluidStorageKeys.LIQUID);   // LIQUID/GAS/PLASMA/MOLTEN
});
```

### 11.6 自定义配方类型（GTRecipeTypeBuilder，官方示例）

```js
// 必须在注册机器之前注册配方类型！
GTCEuStartupEvents.registry('gtceu:recipe_type', event => {
    event.create('test_recipe_type')
        .category('test')
        .setEUIO('in')
        .setMaxIOSize(3, 3, 3, 3)                                  // 物入/物出/流入/流出
        .setItemSlotOverlay(IO.IN, 0, GTGuiTextures.SOLIDIFIER_OVERLAY)
        .setProgressBar(GTGuiTextures.PROGRESS_ARROW)
        .setSound(GTSoundEntries.COOLING)
        // 配方信息行（配方查看器里配方下方文本）：
        .addRecipeInfo(recipe => Text.translate('test_recipe_type.info'))
        .addRecipeInfo(recipe => recipe.data.contains('RequiredTemp') ?
            Text.literal(`Temperature: ${recipe.data.getInt('RequiredTemp')}K`) : Text.empty())
})
// 方法全量：category/setEUIO(IO)/setMaxIOSize/setMaxSize(IO, cap, n)/
//   setSlotOverlay/setProgressBar/setSteamProgressBar/setSound/setHasResearchSlot/
//   setMaxTooltips/setSmallRecipeMap/setIconSupplier/setUiBuilder
// KJS 别名 setItemSlotOverlay/setFluidSlotOverlay（索引单槽）与
// setItemSlotsOverlay/setFluidSlotsOverlay（闭区间多槽）⚠️ 部分为较新版本方法
```

自定义配方分类：

```js
GTCEuStartupEvents.registry('gtceu:recipe_category', event => {
    event.create('test_category')
        .recipeType(GTRecipeTypes.get('test_recipe_type'))
        .setItemIcon(Item.of('minecraft:diamond'))
        // 或 .setCustomIcon('kubejs:textures/gui/test.png')
        .isXEIVisible(true)
        .langValue('测试分类')
})
```

### 11.7 自定义机器（KJS 机器 builder，官方示例）

```js
// 蒸汽机：'steam' 类型
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_simple_steam_machine', 'steam', true)   // id, 类型, 是否有高压变体
})

// 电动单块：'simple' 类型（第 2 参数为污染值，之后是档位列表）
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_electric', 'simple', 0, GTValues.LV, GTValues.MV, GTValues.HV)
        .rotationState(RotationState.NON_Y_AXIS)
        .recipeType('test_recipe_type')
        .tankScalingFunction(tier => tier * 3200)
})

// 发电机：'generator' 类型
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_generator', 'generator', GTValues.LV, GTValues.MV, GTValues.HV)
        .recipeType('test_generator_recipe_type')
        .tankScalingFunction(tier => tier * 3200)
})

// 动能机：'kinetic' 类型（需要 kinetic 配方类型）
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_kinetic', 'kinetic', GTValues.LV, GTValues.MV, GTValues.HV)
        .rotationState(RotationState.NON_Y_AXIS)
        .recipeType('test_kinetic_recipe_type')
        .tankScalingFunction(tier => tier * 3200)
})

// 多块：'multiblock' 类型
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('test_generator', 'multiblock')
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
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get())
                .and(Predicates.autoAbilities(definition.getRecipeTypes())))
            .build())
        .workableCasingModel(
            "gtceu:block/casings/solid/machine_casing_inert_ptfe",
            "gtceu:block/multiblock/large_chemical_reactor"
        )
        // JEI 预览结构：
        .shapeInfo(controller => MultiblockShapeInfo.builder()
            .aisle('eCe', 'GGG', 'CCC')
            .aisle('CCC', 'GDG', 'CSC')
            .aisle('iKo', 'GGG', 'CMC')
            .where('K', controller, Direction.SOUTH)
            .where('C', GTBlocks.CASING_STEEL_SOLID.get())
            .where('G', Block.getBlock('minecraft:glass'))
            .where('D', GTBlocks.COIL_CUPRONICKEL.get())
            .where('S', GTMachines.MUFFLER_HATCH[1], Direction.UP)
            .where('M', GTMachines.MAINTENANCE_HATCH, Direction.SOUTH)
            .where('e', GTMachines.ENERGY_INPUT_HATCH[1], Direction.NORTH)
            .where('i', GTMachines.ITEM_IMPORT_BUS[1], Direction.SOUTH)
            .where('0', GTMachines.ITEM_EXPORT_BUS[1], Direction.SOUTH)
            .build())
})
```

KJS 侧实现类（javap 确认）：`KJSTieredMachineBuilder`（tiers/machine/definition/
tankScalingFunction/addDefaultTooltips/addDefaultModel/isGenerator）、`KJSTieredMultiblockBuilder`、
`KJSSteamMachineBuilder`（hasLowPressure/hasHighPressure）、`KJSWrappingMachineBuilder/
KJSWrappingMultiblockBuilder`（包装同一多档定义）。预置实现：
`MachineConstructors.createLargeCombustionEngine/createLargeTurbine/createFusionReactor/
createSteamMultiblock`；`MachineModifiers` 提供 `SIMPLE_STEAM/SIMPLE_GENERATOR/STEAM_BOILER/
LARGE_BOILER/LARGE_TURBINE/LARGE_COMBUSTION_ENGINE/STEAM_PARALLEL_MULTIBLOCK/FUSION_REACTOR`。

### 11.8 自定义方块（Block 注册事件扩展）

```js
// 活跃方块（'gtceu:active'）：随多块运行切换模型
StartupEvents.registry('block', event => {
    event.create('custom_engine_intake_casing', 'gtceu:active')
        .simple('kubejs:block/casings/custom_engine_intake')   // 激活纹理加 _active 后缀
    event.create('custom_firebox', 'gtceu:active')
        .firebox('gtceu:block/casings/solid/machine_casing_bronze_plated_bricks',
                 'gtceu:block/casings/firebox/machine_casing_firebox_bronze',
                 'gtceu:block/casings/solid/machine_casing_bronze_plated_bricks')
    event.create('custom_bloom', 'gtceu:active')
        .bloom('gtceu:block/casings/fusion/superconducting_coil')  // 发光纹理加 _bloom 后缀
})

// 加热线圈（'gtceu:coil'）：EBF/合金高炉/多炉/裂解机使用
StartupEvents.registry('block', event => {
    event.create('infinity_coil_block', 'gtceu:coil')
        .temperature(100).level(0).energyDiscount(1).tier(10)
        .coilMaterial(() => GTMaterials.get('infinity'))
        .texture('kubejs:block/example_block')
        .hardness(5).requiresTool(true).material('metal')
})
// 标准线圈参数表：CUPRONICKEL 1800K/Lv1/Disc1；KANTHAL 2700/2/1；NICHROME 3600/2/2；
// RTM_ALLOY 4500/4/2；HSS_G 5400/4/4；NAQUADAH 7200/8/4；TRINIUM 9001/8/8；TRITANIUM 10800/16/8
```

### 11.9 配方：event.recipes.gtceu.<类型>（GTRecipeSchema / GTRecipeJS）

```js
// 添加
ServerEvents.recipes(event => {
    event.recipes.gtceu.assembler('test')
        .itemInputs('64x minecraft:dirt', '32x minecraft:diamond')
        .inputFluids(Fluid.of('minecraft:lava', 1500))
        .itemOutputs('minecraft:stick')
        .duration(100)
        .EUt(30)
})
```

调用全集（javap 确认的 GTRecipeJS 方法，官方文档亦列出）：

```js
// 通用
.id(id)  .category(category)  .perTick(boolean)  .chance(int)  .maxChance(int)  .tierChanceBoost(int)
.onSave(b => {})  .addData(key, value)  .addDataString / addDataNumber / addDataBool
// 能量
.inputEU(voltage, amperage)  .EUt(voltage[, amperage])  .outputEU(voltage[, amperage])
// 计算力
.inputCWU(int)  .CWUt(int)  .totalCWU(int)  .outputCWU(int)
// 物品输入（别名 .itemInputs 与 .inputItems 等价）
.itemInputs(...)  .inputItems('64x minecraft:dirt')       // 字符串/Item.of/标签#均可
.inputItems(TagPrefix, Material)  .inputItems('plate', 'gtceu:iron')  // 材料输入
.itemInputRanged(ing, min, max)  .inputItemsRanged(ing, min, max)     // 随机数量
.inputItemNbtPredicate(stack, predicate)
.notConsumable(item)                                       // 不消耗
.circuit(n)                                                // 编程电路 1-32
.chancedInput(item, chance, boost)  .chancedFluidInput(fluid, chance, boost)
// 物品输出
.itemOutputs(...)  .outputItems(...)  .itemOutputRanged(...)  .outputItemsRanged(...)
.chancedOutput(item, chance[, boost])                      // 概率输出（分数或 /10000）
// 流体
.inputFluids(Fluid.of(...))  .inputFluidsRanged(...)  .outputFluids(...)  .outputFluidsRanged(...)
.chancedFluidOutput(fluid, chance[, boost])
.notConsumableFluid(fluid)
// 概率逻辑
.chancedItemOutputLogic('or'|'and'|'xor'|'first'|'none')   // 以及 chancedFluidOutputLogic /
.chancedItemInputLogic(...)  .chancedTickInputLogic(...)   // chancedFluidInputLogic / chancedTickOutputLogic
// 条件
.cleanroom(CleanroomType.CLEANROOM)  .dimension('minecraft:the_end'[, true])
.biome('minecraft:plains'[, true])  .biomeTag('#minecraft:is_overworld'[, true])
.rain(1.0)  .thunder(1.0)  .posY(min, max)
.adjacentFluids('minecraft:water', 'minecraft:lava')       // 岩石破碎机条件
.adjacentFluidTag('forge:water')  .adjacentBlocks(...)  .adjacentBlockTag(...)
.daytime(true)  .gameStage('stage')  .ftbQuest('id')  .heraclesQuest('id')
.fusionStartEU(600000)
.environmentalHazard('carbon_monoxide_poisoning')
// 研究
.scannerResearch(b => b.researchStack(Item.of('minecraft:coal_block'))
                     .EUt(GTValues.VA[GTValues.IV]).duration(420))
.stationResearch(b => b.researchStack(Item.of('minecraft:coal_block'))
                     .EUt(GTValues.VA[GTValues.IV]).CWUt(24, 12000))
.researchWithoutRecipe('id')
// 特殊数据
.blastFurnaceTemp(1200)  .explosivesAmount(n)  .explosivesType(stack)
.solderMultiplier(n)  .disableDistilleryRecipes(bool)  .fusionStartEU(n)
.durationIsTotalCWU(bool)  .hideDuration(bool)
.addMaterialInfo(true)  .removePreviousMaterialInfo()
```

注意：
1. `scannerResearch` 因 JS 方法名解析问题需用索引式调用：
   `["scannerResearch(java.util.function.UnaryOperator)"](b => ...)`（官方文档确认）。
2. `EUt` 正数耗电、负数发电；`perTick(true)` 后添加的输入输出变为每 tick 消耗/产出，
   用完记得 `perTick(false)`。
3. 概率用分数或 0-10000 整数；chance=0 表示不消耗（等价 notConsumable）。

配方移除/修改（官方示例）：

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

### 11.10 TagPrefix 注册（TagPrefixBuilder）

```js
// 基础前缀
GTCEuStartupEvents.registry('gtceu:tag_prefix', event => {
    event.create('test_dust', 'basic')
        .idPattern('dust{0}')                  // 物品 id 模式
        .langValue('{0} Dust')                 // 显示名（{0}=材料名）
        .materialAmount(100)                   // 该前缀的材料用量（mb）
        .materialIconType(GTMaterialIconType.dust)
        .unificationEnabled(true)
        .generateItem(true)
        .generationCondition(mat => true)      // 生成条件
        .defaultTagPath('dusts')               // 标签路径
        .tooltip((mat, list) => { ... })
        .maxStackSize(64)
})
// 矿前缀（OreTagPrefixBuilder 额外参数）
event.create('test_ore', 'ore')
    .stateSupplier(() => Block.getBlock('minecraft:stone').defaultBlockState())
    .baseModelLocation('minecraft:block/stone')
    .doubleDrops(true).isSand(false).shouldDropAsItem(true)
```

### 11.11 矿脉事件（官方示例）

```js
// 添加/修改/删除矿脉
GTCEuServerEvents.oreVeins(event => {
    event.add("kubejs:custom_vein", vein => {
        vein.weight(200)                  // 权重
        vein.clusterSize(40)              // 簇直径
        vein.density(0.25)                // 密度
        vein.discardChanceOnAirExposure(0)
        vein.layer("deepslate")           // stone/deepslate/netherrack/endstone
        vein.dimensions("minecraft:overworld")
        vein.biomes("#minecraft:is_overworld")
        vein.heightRangeUniform(-60, 20)  // 或 heightRangeTriangle / heightRange(height json)
        vein.generator(b => b.standard().withBlock('gtceu:iron_ore')...)   // ⚠️ 生成器写法见 02-Generators 文档
        vein.addIndicator(...)
    })
    event.remove("gtceu:magnetite_vein_ow")
    event.removeAll()
    event.removeAll((id, vein) => id.path != "magnetite_vein_ow")
    event.modify("gtceu:cassiterite_vein", vein => { vein.density(1.0) })
    event.modifyAll((id, vein) => { console.log("Modifying vein: " + id); vein.density(1.0) })
})
```

> ⚠️ 上述 `vein.generator(...)`/`vein.addIndicator(...)` 的 JS 链式写法以官方
> `02-Generators.md` 为准；7.5.3 jar 中 GTOreDefinition 对应
> `standardVeinGenerator(consumer)/surfaceIndicatorGenerator(consumer)` 等方法（见第 8 部分）。

```js
// 基岩流体矿脉
GTCEuServerEvents.fluidVeins(event => {
    event.add('gtceu:custom_bedrock_fluid_vein', vein => {
        vein.dimensions('minecraft:overworld')
        vein.fluid(() => Fluid.of('gtceu:custom_fluid').fluid)
        vein.weight(600)
        vein.minimumYield(120)  vein.maximumYield(720)
        vein.depletionAmount(2)  vein.depletionChance(1)  vein.depletedYield(50)
    })
    event.modify('gtceu:heavy_oil_deposit', vein => { vein.setWeight(9999) })
    event.remove('gtceu:heavy_oil_deposit')
})
// 基岩矿脉（需开启 config 的 doBedrockOres）
GTCEuServerEvents.bedrockOreVeins(event => {
    event.add('kubejs:overworld_bedrock_ore_vein_iron', vein => {
        vein.weight(100).size(3).yield(10, 20)
        vein.material(GTMaterials.Goethite, 5)
        vein.material(GTMaterials.Limonite, 2)
        vein.material(GTMaterials.Hematite, 2)
        vein.material(GTMaterials.Malachite, 1)
        vein.dimensions('minecraft:overworld')
    })
    event.modify('gtceu:example_ore', vein => { vein.weight(9999) })
    event.remove('gtceu:example_ore')
})
```

### 11.12 CraftingComponentsEventJS（官方示例）

```js
const Map = Java.loadClass("java.util.Map")

// 修改内置组件
GTCEuStartupEvents.craftingComponents(event => {
    event.setItem(GTCraftingComponents.CIRCUIT, GTValues.MV, Item.of('minecraft:dirt'))
    event.setItems(GTCraftingComponents.PUMP, Map.of(
        GTValues.LV, Item.of('gtceu:lv_robot_arm'),
        GTValues.MV, Item.of('gtceu:mv_robot_arm'), ...))
    event.setTag(GTCraftingComponents.CASING, GTValues.EV, 'minecraft:logs')  // 注意无 #
    event.setMaterialEntry(GTCraftingComponents.PLATE, GTValues.UEV, new MaterialEntry('plate', 'gtceu:infinity'))
    event.removeTier('robot_arm', GTValues.EV)
    event.removeTiers('pump', GTValues.EV, GTValues.IV, GTValues.LuV)
})

// 创建新组件
GTCEuStartupEvents.craftingComponents(event => {
    event.createItem("item_component", 'minecraft:cyan_stained_glass')
        .addItem(GTValues.LV, Item.of('minecraft:cyan_stained_glass')) ...
    event.createTag("tag_component", 'forge:barrels/wooden')
        .addTag(GTValues.LV, 'forge:chests/wooden') ...
    event.createMaterialEntry("material_entry_component", new MaterialEntry('plate', 'gtceu:infinity'))
        .addMaterialEntry(GTValues.LV, 'ingot', 'gtceu:infinity') ...
})
// 内置组件：GTCraftingComponents.CIRCUIT/WIRE_ELECTRIC/CABLE/HULL/MOTOR/PUMP/PISTON/
//   EMITTER/SENSOR/CONVEYOR/ROBOT_ARM/FIELD_GENERATOR/COIL_HEATING/PLATE/...（全量见官方文档）
```

### 11.13 披风注册（RegisterCapesEventJS）

```js
GTCEuServerEvents.registerCapes(event => {
    event.registerCape('kubejs:my_cape', 'kubejs:textures/capes/my_cape.png')
    event.registerFreeCape('kubejs:free_cape', 'kubejs:textures/capes/free_cape.png')
    event.unlockCapeFor('00000000-0000-0000-0000-000000000000', 'kubejs:my_cape')
})
```

### 11.14 KJS 常用绑定（GregTechKubeJSPlugin 注入，官方文档使用确认）

`GTMaterials`、`GTElements`、`GTValues`、`GTItems`、`GTBlocks`、`GTMachines`、
`GTRecipeTypes`、`GTRecipeModifiers`、`GTMaterialFlags`、`GTMaterialIconSet`、
`GTMaterialIconType`、`GTGuiTextures`（= GuiTextures）、`GTSoundEntries`、
`GTCraftingComponents`、`GTRecipeComponents`、`GTRegistryInfo`、`PropertyKey`、
`MaterialEntry`、`PartAbility`、`Predicates`、`FactoryBlockPattern`、
`MultiblockShapeInfo`、`RotationState`、`IO`、`CleanroomType`、`Direction`、
`Block`、`Component`、`Text`、`Fluid`、`Item` 等（KubeJS 自动加载 Forge 类 +
GT 插件注册的绑定；部分需 `Java.loadClass` 显式加载，如 `FluidBuilder`、
`BlastProperty` 等属性类）。

GTRecipeComponents 内部（javap 确认）：`ITEM_IN/ITEM_OUT/FLUID_IN/FLUID_OUT/EU_IN/EU_OUT/
CWU_IN/CWU_OUT`（ContentJS）、`IN/OUT/TICK_IN/TICK_OUT`（CapabilityMapComponent）、
`EXTENDED_OUTPUT/FLUID_INGREDIENT/ENERGY_STACK/RECIPE_CAPABILITY/RECIPE_CONDITION/CHANCE_LOGIC`。
`CapabilityMap` 支持 `replaceInput/replaceOutput`（即 KJS 的 replaceInput/replaceOutput 对
GT 配方生效的实现基础）。`ExtendedOutputItem`：支持 `withCount/withRolls` 与范围输出。
`MaterialStackWrapper`（helpers）：`fromString('2x iron')/toMatStack()/EMPTY`。

---

## 第 12 部分 常见坑与示例（GTNEcore 视角）

### 12.1 版本差异坑（官方文档 vs 7.5.3）

| 官方 1.20.1 分支文档 | 7.5.3 jar 实测 |
|---|---|
| `MultiblockPatternBuilder.start().slice(...)` | `FactoryBlockPattern.start().aisle(...)` |
| `MultiPredicate` / `Predicate.blocks(...)` | `TraceabilityPredicate` / `Predicates.blocks(...)` |
| `Predicates.controller(def)`（直接传 definition） | `Predicates.controller(Predicates.blocks(...))`（套一层） |
| `MetaMachine.attachTrait/attachPersistentTrait/getTrait` | `MetaMachine.attachTraits(MachineTrait)`（⚠️ 无持久化/查询变体，需自持字段） |
| `KJSTieredMachineBuilder` 等 | 7.5.3 已内置（本草案 javap 确认） |

结论：写代码前以本机依赖的 jar 为准，`javap` 是最终仲裁。

### 12.2 材料/物品生成坑

- 材料标志决定自动生成物品：只有加了 `GENERATE_PLATE/GENERATE_GEAR/...` 或前缀对应属性
  （ingot/dust/gem 等）才会生成对应物品；只建材料不设形态 = 无物品。
- 改已有材料属性必须先 `removeProperty` 再 `setProperty`（WireProperties 官方示例）。
- 覆盖原版/他模物品的统一化：`GTItems.unificationItem(prefix, material)` 挂 registrate builder。
- 部件贴图是动画贴图（如电动泵旋转）：资产复制必须带 `.mcmeta`，否则显示为静态完整帧。
- 重排档位机器模型：必须补 `models/block/machine/<档位>_<机器>.json`，缺了全紫黑。
- `TagPrefix.setIgnored(material, items...)` 可跳过某些材料的特定前缀物品生成。

### 12.3 配方坑

- 配方条件在配方匹配之后、执行之前运行；条件不满足机器进入 SUSPEND 等待状态，
  直到输入输出变化才会重新检查。
- `chance=0` 的输入 = 不消耗（等价 notConsumable）；概率分母 10000。
- `EUt` 与 `inputEU` 区别：EUt 是每秒速率，inputEU 是一次性启动消耗（聚变）。
- KJS 里 `scannerResearch` 必须用 `["scannerResearch(...)"](b => ...)` 索引调用。
- 自定义配方类型必须在机器之前注册；机器用 `recipeType('...')` 字符串引用。
- RecipeCapability 的 KJS 键需要在 `IGTAddon.registerRecipeKeys(KJSRecipeKeyEvent)` 里
  用 `registerKey(cap, Pair.of(ContentJS, ContentJS))` 注册，否则 KJS 里无法 `.input(cap, ...)`。
- `CountableIngredient` 类在 7.5.3 不存在（旧版 API），范围输入请用
  `IntProviderIngredient` / `inputItemsRanged`。

### 12.4 机器坑

- 机器 trait 必须在构造函数中创建并 attach；未 attach 的 trait 不生效。
- `SimpleTieredMachine` 构造参数 `(holder, tier, tankScalingFunction, args...)`：
  tankScalingFunction 是 Int2IntFunction（档位→槽容量）。
- 多块配方修改走 `IMultiPart.modifyRecipe` 链与 `definition.recipeModifier`；
  维护机自动加时长惩罚（`IMaintenanceMachine.getDurationMultiplier`）。
- 线圈机用 `CoilWorkableElectricMultiblockMachine` + `Predicates.heatingCoils()`；
  自定义线圈须同时注册进 `GTCEuAPI.HEATING_COILS`（或经 CoilBlockBuilder/KJS 注册）。
- 机器 GUI 建议用 `editableUI(...)` 让玩家可在 GUI 编辑器里自定义布局（LDLib UI Editor）。

### 12.6 GTNEcore 项目速查

```java
// 扩展电压档位（示例骨架，⚠️ 具体实现以项目实际为准）
// GTValues.V/VN 为 public static 数组，可在构造阶段替换为更长数组以支持 OpV 以上，
// 并配合 GTCEuAPI.initializeHighTier() 使用；
// GTValues.ALL_TIERS / tiersBetween(int, int) 是现成的档位遍历工具。
// Mixin 通常用于：拦截 GTValues 静态初始化、给自定义档位补配方/工具等级等。
```

```js
// 快速给现有机器加自定义配方
ServerEvents.recipes(event => {
    event.recipes.gtceu.assembler('mekatech:example')
        .itemInputs('2x minecraft:iron_ingot', '#forge:dusts/coal')
        .inputFluids(Fluid.of('gtceu:lubricant', 100))
        .itemOutputs('4x gtceu:steel_ingot')
        .circuit(1)
        .duration(200).EUt(GTValues.VA[GTValues.HV])
})
```

---

## 附录 A：API 全类清单（801 类，按包分组，来自 gtm_api_classes.txt）

> 说明：`$` 内部类已并入父类行；标注 ⚠️ 的为用途推测/存疑，其余由 javap 或类名语义确认。

### A.1 `com.gregtechceu.gtceu.api`（顶层）

- `GTCEuAPI`（+`$RegisterEvent`/`$RL`/`$String`）— 全局 API：materialManager、HEATING_COILS、CLEANROOM_FILTERS、PSS_BATTERIES、high tier 开关
- `GTValues` — 电压数组/档位索引/时间常量/模组 id 常量/档位名数组

### A.2 `api.addon` + `api.addon.events`

- `AddonFinder` — 通过 SPI 发现所有 @GTAddon 实现（getAddons/getAddon(modId)）
- `GTAddon` — 附属模组标注注解（空注解接口）
- `IGTAddon` — 附属模组全部注册回调接口（initializeAddon/registerMaterials/addRecipes/...）
- `events.KJSRecipeKeyEvent` — 自定义 RecipeCapability 注册 KJS 输入/输出键
- `events.MaterialCasingCollectionEvent` — 收集材料→外壳方块映射

### A.3 `api.block` + `api.block.property`

- `ActiveBlock` — 活跃纹理方块（多块运行时切换模型）
- `AppearanceBlock` — 外观伪装方块（配合 IAppearance）
- `BlockAttributes` — 方块属性聚合（burnTime 等）⚠️
- `IAppearance` — 方块外观提供接口
- `ICoilType` — 加热线圈类型接口（温度/等级/折扣/档位）
- `IFilterType` — 洁净室过滤器类型接口
- `IFusionCasingType` — 聚变外壳类型接口 ⚠️（聚变线圈等级）
- `IMachineBlock` — 机器方块接口
- `MaterialBlock` — 材料方块（TagPrefix+Material，可染色/可加框架）
- `MaterialPipeBlock` — 材料管道方块（框架/管道复合）⚠️
- `MetaMachineBlock` — 元机器方块（放置→MetaMachineBlockEntity）
- `OreBlock` — 矿石方块（继承 MaterialBlock，右键行为）
- `PipeBlock` — 管道方块基类 ⚠️
- `RendererMaterialBlock` — 带自定义渲染器的材料方块 ⚠️
- `SimpleCoilType` — 简单线圈实现（name/temp/level/tier/energyDiscount/texture）
- `property.GTBlockStateProperties` — UPWARDS_FACING/ACTIVE/BLOOM/INVERTED/NATURAL 等方块状态属性

### A.4 `api.blockentity`

- `ICopyable` — 配置复制接口（copyConfig/pasteConfig，数据棒粘贴用）
- `IPaintable` — 染色接口
- `ITickSubscription` — tick 订阅接口（subscribeServerTick/unsubscribe）
- `MetaMachineBlockEntity`（+`$AE2CallWrapper`）— 机器方块实体，能力分发入口
- `PipeBlockEntity` — 管道方块实体（连接位掩码/覆盖板/网格高亮）

### A.5 `api.capability`（顶层）

- `CombinedCapabilityProvider` — 多 ICapabilityProvider 聚合
- `GTCapabilityHelper` — 世界/物品能力查询静态助手
- `ICentralMonitor` — 中央监控接口 ⚠️
- `ICleanroomReceiver` — 洁净室接收者（getCleanroom/setCleanroom）
- `IControllable` — 工作开关控制（WORKING_ENABLED_PROPERTY）
- `ICoverable`（+`$1`）— 覆盖板宿主（放置/移除/查询覆盖板）
- `IDataAccessHatch` — 数据访问舱（配方可用性/修改）
- `IElectricItem` — 电动物品能力（charge/discharge）
- `IEnergyContainer`（+`$1`）— 能量容器核心接口
- `IEnergyInfoProvider`（+`$EnergyInfo`）— 能量信息提供（电压/电流/输入输出速率）
- `IHPCAComponentHatch` — HPCA 部件舱 ⚠️
- `IHPCAComputationProvider` — HPCA 计算力提供 ⚠️
- `IHPCACoolantProvider` — HPCA 冷却液提供 ⚠️
- `IHazardParticleContainer`（+`$1`）— 环境危害粒子容器 ⚠️
- `ILaserContainer` — 激光容器（继承 IEnergyContainer）
- `IMedicalConditionTracker` — 医疗条件追踪器（实体上的疾病状态）
- `IMiner` — 采矿机接口 ⚠️
- `IMonitorComponent` — 监控组件 ⚠️
- `IObjectHolder` — 物品持有者（HPCA 用）⚠️
- `IOpticalComputationHatch` — 光计算舱 ⚠️
- `IOpticalComputationProvider` — 光计算力提供（requestCWUt/getMaxCWUt/canBridge）
- `IOpticalComputationReceiver` — 光计算接收 ⚠️
- `IOpticalDataAccessHatch` — 光数据访问舱 ⚠️
- `IParallelHatch` — 并行舱（getCurrentParallel）
- `IPropertyFluidFilter` — 流体属性过滤（温度/耐性）
- `IThermalFluidHandlerItemStack` — 物品流体容器温限接口
- `IToolable` — 工具点击接口（onToolClick）
- `ITurbineMachine` — 涡轮机接口 ⚠️
- `IWorkable` — 工作进度接口（getProgress/getMaxProgress/isActive）
- `compat.CapabilityCompatProvider` — 兼容能力提供器基类
- `compat.EUToFEProvider`（+`$GTEnergyWrapper`）— EU→Forge Energy 包装提供器
- `compat.FeCompat` — EU/FE 换算工具（toFe/toEu/insertEu/extractEu）
- `forge.GTCapability`（+`$1`–`$18`）— 全部 Forge Capability 常量与注册
- `recipe.BlockStateRecipeCapability` — 方块状态配方能力（BLOCK_STATE）
- `recipe.CWURecipeCapability`（+`$ICustomParallel`）— 计算力配方能力（CWU）
- `recipe.EURecipeCapability`（+`$ICustomParallel`）— 能量配方能力（EU）
- `recipe.FluidRecipeCapability`（+`$ICustomParallel`）— 流体配方能力（FLUID）
- `recipe.IFilteredHandler` — 过滤 handler（优先级常量 HIGHEST..LOWEST）
- `recipe.IO` — 输入/输出枚举（IN/OUT/BOTH/NONE）
- `recipe.IRecipeCapabilityHolder` — 配方能力持有者（capabilitiesProxy 查询）
- `recipe.IRecipeHandler` — 配方 handler（handleRecipeInner/getContents/getCapability）
- `recipe.ItemRecipeCapability`（+`$ICustomParallel`）— 物品配方能力（ITEM）
- `recipe.RecipeCapability` — 配方能力抽象基类（自定义能力继承点）

### A.6 `api.codec`

- `DispatchedMapCodec` — 按 key 分派的 Map codec
- `GTCodecUtils`（+`$LazyParsingCodec`）— codec 工具（longRange/lazyParsingCodec）

### A.7 `api.cosmetics` + `api.cosmetics.event`

- `CapeRegistry`（+`$KJSCallWrapper`）— 披风注册表（SavedData，解锁/设置）
- `event.RegisterGTCapesEvent` — 披风注册事件（registerCape/registerFreeCape/unlockCapeFor）

### A.8 `api.cover` + `api.cover.filter`

- `CoverBehavior` — 覆盖板行为基类（全部生命周期回调，自定义覆盖板继承点）
- `CoverDefinition`（+`$ClientHelper`/`$CoverBehaviourProvider`/`$TieredCoverBehaviourProvider`）— 覆盖板定义
- `IIOCover` — IO 覆盖板接口（传输速率/IO 模式）
- `IUICover` — 带 UI 的覆盖板接口
- `filter.Filter` — 过滤器基接口 ⚠️
- `filter.FilterHandler`（+`$1`）— 过滤器处理器（槽位+配置 UI）
- `filter.FilterHandlers`（+`$1`/`$2`）— 过滤器工厂（item()/fluid()）
- `filter.FluidFilter`（+`$1`）— 流体过滤器接口
- `filter.ItemFilter`（+`$1`）— 物品过滤器接口
- `filter.SimpleFluidFilter`（+`$1`）— 简单流体过滤器实现
- `filter.SimpleItemFilter`（+`$1`）— 简单物品过滤器实现
- `filter.SmartItemFilter`（+`$SmartFilteringMode`）— 智能物品过滤器（按模式）
- `filter.TagFilter` — 标签表达式过滤器基类（oreDict 表达式）
- `filter.TagFluidFilter` — 标签流体过滤器
- `filter.TagItemFilter` — 标签物品过滤器

### A.9 `api.data` + `api.data.chemical`

- `DimensionMarker`（+`$Builder`）— 维度图标标记（JEI 维度页）
- `RotationState` — 旋转状态枚举（ALL/NONE/Y_AXIS/NON_Y_AXIS）
- `chemical.ChemicalHelper` — 材料↔物品/方块/流体换算工具类
- `chemical.Element` — 化学元素（质子/中子/半衰期/衰变）

### A.10 `api.data.chemical.material`

- `IMaterialRegistryManager`（+`$Phase`）— 材料注册表管理器（GTCEuAPI.materialManager）
- `ItemMaterialData` — 物品材料数据 ⚠️
- `MarkerMaterial` — 标记材料（无色材料，用于 lens/染料等）
- `MarkerMaterials`（+`$Color`）— 内置标记材料（Empty/Color 系列）
- `Material`（+`$Builder`/`$MaterialInfo`）— 材料类与 Builder
- `event.MaterialEvent` — 材料注册事件（IModBusEvent）
- `event.MaterialRegistryEvent` — 材料注册表创建事件
- `event.PostMaterialEvent` — 材料注册完成后事件
- `info.MaterialFlag`（+`$Builder`）— 材料标志
- `info.MaterialFlags` — 全部内置标志常量
- `info.MaterialIconSet` — 图标集（DULL/METALLIC/SHINY/...）
- `info.MaterialIconType` — 图标类型（dust/ingot/plate/ore/...）
- `properties.AlloyBlastProperty` — 合金高炉属性 ⚠️
- `properties.ArmorProperty`（+`$ArmorMaterial`/`$Builder`/`$CustomTextureGetter`）— 护甲属性
- `properties.BlastProperty`（+`$Builder`/`$GasTier`）— 高炉属性（温度/气体档位/EUt）
- `properties.DustProperty` — 粉尘属性（采掘等级/燃料时长）
- `properties.FluidPipeProperties`（+`$1`）— 流体管道属性（容量/耐温/耐性）
- `properties.FluidProperty` — 流体属性（FluidStorage 登记）
- `properties.GemProperty` — 宝石属性
- `properties.HazardProperty`（+`$HazardTrigger`/`$ProtectionType`）— 危害属性
- `properties.IMaterialProperty` — 属性接口基类
- `properties.IngotProperty` — 锭属性
- `properties.ItemPipeProperties` — 物品管道属性（优先级/速率）
- `properties.MaterialProperties` — 材料属性容器
- `properties.OreProperty` — 矿石属性（洗矿/副产物/分离）
- `properties.PolymerProperty` — 聚合物属性 ⚠️
- `properties.PropertyKey`（+`$EmptyProperty`）— 属性键（INGOT/DUST/BLAST/WIRE/...）
- `properties.RotorProperty` — 转子属性
- `properties.ToolProperty`（+`$Builder`）— 工具属性
- `properties.WireProperties` — 线缆属性（电压/电流/损耗/超导）
- `properties.WoodProperty` — 木材属性 ⚠️
- `registry.MaterialRegistry` — 材料注册表（继承 GTRegistry$String）
- `stack.ItemMaterialInfo` — 物品材料成分信息
- `stack.MaterialEntry` — 材料条目（TagPrefix+Material record）
- `stack.MaterialStack` — 材料栈（Material+amount record）

### A.11 `api.data.damagesource` / `api.data.medicalcondition`

- `damagesource.DamageTypeData`（+`$Builder`）— 伤害类型数据（source/is/register）
- `medicalcondition.MedicalCondition`（+`$IdleProgressionType`）— 医疗条件（环境危害）
- `medicalcondition.Symptom`（+`$ConfiguredSymptom`/`$Effect`）— 症状（DEATH/POISONING/...）

### A.12 `api.data.tag`

- `TagPrefix`（+`$BlockConstructor`/`$BlockItemConstructor`/`$BlockProperties`/`$Conditions`/`$ItemConstructor`/`$OreType`）— 前缀体系（矿石/粉尘/锭/线缆/管道/工具头）
- `TagType` — 标签格式类型（默认/前缀/无格式/自定义）
- `TagUtil` — 标签创建工具（createItemTag/createBlockTag/optionalTag...）

### A.13 `api.data.worldgen`

- `BiomeWeightModifier` — 生物群系权重修饰（biomes+addedWeight）
- `GTLayerPattern`（+`$Builder`/`$Layer`/`$Layer$Builder`）— 分层矿脉图案（含概率）
- `GTOreDefinition`（+`$InferredProperties`）— 矿脉定义（weight/density/layer/generator/indicator）
- `IWorldGenLayer`（+`$1`/`$RuleTestSupplier`）— 世界生成层接口
- `RTTTree` — RuleTest 树 ⚠️
- `SimpleWorldGenLayer` — 简单生成层实现
- `WorldGenLayers` — 内置层（STONE/DEEPSLATE/NETHERRACK/ENDSTONE）
- `WorldGeneratorUtils`（+`$WeightedVein`/`$WorldOreVeinCache`）— 世界生成工具（生成器注册表/缓存）
- `bedrockfluid.BedrockFluidDefinition`（+`$1`/`$2`/`$Builder`）— 基岩流体矿脉定义
- `bedrockfluid.BedrockFluidVeinSavedData` — 基岩流体矿脉存档
- `bedrockfluid.FluidVeinWorldEntry` — 单矿脉世界条目 ⚠️
- `bedrockore.BedrockOreDefinition`（+`$1`/`$2`/`$Builder`）— 基岩矿定义
- `bedrockore.BedrockOreVeinSavedData` — 基岩矿存档
- `bedrockore.OreVeinWorldEntry` — 单矿条目 ⚠️
- `bedrockore.WeightedMaterial` — 加权材料（材料+概率）
- `generator.IndicatorGenerator` — 地表指示物生成器基类
- `generator.IndicatorGenerators` — 指示物生成器注册（NO_OP/SURFACE）
- `generator.VeinGenerator`（+`$VeinEntry`）— 矿脉生成器基类
- `generator.VeinGenerators` — 矿脉生成器注册（STANDARD/LAYER/GEODE/DIKE/VEINED/CLASSIC/CUBOID）
- `generator.indicators.NoopIndicatorGenerator` — 无指示物生成器
- `generator.indicators.SurfaceIndicatorGenerator`（+`$IndicatorPlacement`）— 地表指示物生成器
- `generator.veins.ClassicVeinGenerator`（+`$Layer`/`$Layer$Builder`）— 经典分层矿脉生成器
- `generator.veins.CuboidVeinGenerator` — 立方体矿脉生成器 ⚠️
- `generator.veins.DikeVeinGenerator`（+`$DikeBlockDefinition`）— 岩墙矿脉生成器
- `generator.veins.GeodeVeinGenerator`（+`$GeodeBlockSettings`）— 晶洞矿脉生成器
- `generator.veins.LayeredVeinGenerator` — 分层矿脉生成器 ⚠️
- `generator.veins.NoopVeinGenerator` — 无矿脉生成器
- `generator.veins.StandardVeinGenerator` — 标准矿脉生成器（withBlock/withMaterial）
- `generator.veins.VeinedVeinGenerator`（+`$1`/`$2`/`$VeinBlockDefinition`）— 脉络矿脉生成器（1.20.1 默认）
- `modifier.BiomePlacement` — 生物群系放置修饰器（PlacementModifier）
- `modifier.DimensionFilter` — 维度过滤修饰器
- `modifier.FrequencyModifier` — 频率修饰器
- `ores.GeneratedIndicators` — 已生成指示物集合
- `ores.GeneratedVein` — 已生成矿脉（consumeOres）
- `ores.GeneratedVeinMetadata` — 矿脉元数据 ⚠️
- `ores.OreBlockPlacer` — 矿块放置器接口
- `ores.OreGenCache` — 矿脉生成缓存 ⚠️
- `ores.OreGenerator`（+`$VeinConfiguration`）— 矿脉生成执行器
- `ores.OreIndicatorPlacer` — 指示物放置器 ⚠️
- `ores.OrePlacer` — 矿放置器基类 ⚠️
- `ores.OreVeinUtil` — 矿脉工具（canPlaceOre/getVeinCenter/resolveBiomes）

### A.14 `api.fluids`

- `FluidBuilder`（+`$1`）— 流体构建器（温度/密度/属性/贴图）
- `FluidConstants` — 流体常数（温度/密度阈值）
- `FluidState` — 流体形态枚举（LIQUID/GAS/PLASMA）
- `GTFluid` — 流体基类（FlowingFluid+IAttributedFluid）
- `PropertyFluidFilter`（+`$1`）— 流体属性过滤器实现
- `attribute.FluidAttribute` — 流体属性（酸等，含 tooltip）
- `attribute.FluidAttributes` — 内置属性常量（ACID）
- `attribute.IAttributedFluid` — 属性流体接口
- `forge.GTFluidImpl`（+`$Flowing`/`$Source`）— Forge 流体实现
- `store.FluidStorage`（+`$FluidEntry`）— 流体存储接口（enqueueRegistration）
- `store.FluidStorageImpl` — 流体存储实现
- `store.FluidStorageKey` — 流体存储键
- `store.FluidStorageKeys` — 内置键（LIQUID/GAS/PLASMA/MOLTEN）

### A.15 `api.gui`

- `GuiTextures` — 内置 GUI 贴图常量（KJS 名 GTGuiTextures）
- `SteamTexture` — 蒸汽双态贴图
- `UITemplate` — UI 模板（bindPlayerInventory）
- `WidgetUtils` — widget 工具 ⚠️
- `editor.EditableMachineUI` — 机器可编辑 UI 定义
- `editor.EditableUI` — 可编辑 UI 泛型 ⚠️
- `editor.GTUIEditor` — LDLib UI 编辑器扩展
- `editor.IEditableUI`（+`$Normal`）— 可编辑 UI 接口
- `editor.MachineUIProject` — 机器 UI 工程 ⚠️
- `editor.RecipeTypeUIProject` — 配方类型 UI 工程 ⚠️
- `editor.TemplateTab` — 模板标签页 ⚠️
- `editor.UIMainPanel`（+`$1`）— UI 主面板 ⚠️
- `factory.CoverUIFactory` — 覆盖板 UI 工厂
- `factory.GTUIEditorFactory` — UI 编辑器工厂
- `factory.MachineUIFactory` — 机器 UI 工厂
- `fancy.ConfiguratorPanel`（+`$FloatingTab`/`$Tab`/`$Tab$1`/`$Tab$2`）— FancyUI 配置面板
- `fancy.FancyMachineUIWidget`（+`$NavigationEntry`）— FancyUI 主 widget
- `fancy.IFancyConfigurator` — FancyUI 配置器接口
- `fancy.IFancyConfiguratorButton`（+`$Toggle`）— 按钮配置器
- `fancy.IFancyCustomClientActionHandler` — 自定义客户端动作 ⚠️
- `fancy.IFancyCustomMiddleClickAction` — 中键点击动作 ⚠️
- `fancy.IFancyCustomMouseWheelAction` — 滚轮动作 ⚠️
- `fancy.IFancyTooltip`（+`$Basic`）— 高级 tooltip 接口
- `fancy.IFancyUIProvider`（+`$PageGroupingData`）— FancyUI 页面提供接口
- `fancy.PageSwitcher` — 页面切换器
- `fancy.TabsWidget` — 标签页 widget
- `fancy.TitleBarWidget` — 标题栏 widget
- `fancy.TooltipsPanel` — tooltip 面板
- `fancy.VerticalTabsWidget` — 垂直标签页 widget
- `misc.IGhostFluidTarget`（+`$1`）— 幽灵流体目标（JEI 拖放）
- `misc.IGhostItemTarget`（+`$1`）— 幽灵物品目标
- `misc.PacketProspecting` — 探测仪数据包 ⚠️
- `misc.ProspectorMode`（+`$1`/`$2`/`$3`/`$FluidInfo`/`$OreInfo`）— 探测仪模式（ORE/FLUID/BEDROCK_ORE）
- `texture.ProspectingTexture` — 探测贴图 ⚠️
- `util.TimedProgressSupplier` — 计时进度提供 ⚠️
- `widget.BlockableSlotWidget` — 可禁用槽位
- `widget.ColorBlockWidget` — 颜色选择块 ⚠️
- `widget.ConfirmTextInputWidget` — 确认文本输入框 ⚠️
- `widget.CoverConfigurator` — 覆盖板配置器 ⚠️
- `widget.DualProgressWidget` — 双进度条 ⚠️
- `widget.EnumSelectorWidget`（+`$SelectableEnum`）— 枚举选择器
- `widget.ExtendedProgressWidget` — 扩展进度条 ⚠️
- `widget.GhostCircuitSlotWidget` — 幽灵电路槽
- `widget.IntInputWidget` / `widget.LongInputWidget` / `widget.NumberInputWidget`（+`$ChangeValues`）— 数字输入
- `widget.PatternPreviewSlotWidget` — 结构预览槽 ⚠️
- `widget.PatternPreviewWidget`（+`$1`/`$MBPattern`/`$PartInfo`）— 结构预览 widget（终端预览）
- `widget.PhantomFluidWidget`（+`$1`）— 幽灵流体槽（过滤配置）
- `widget.PhantomSlotWidget`（+`$1`）— 幽灵物品槽
- `widget.PredicatedButtonWidget` — 条件按钮 ⚠️
- `widget.PredicatedImageWidget` — 条件图片 ⚠️
- `widget.ProspectingMapWidget`（+`$WaypointItem`）— 探测地图 widget
- `widget.ScrollablePhantomFluidWidget` — 可滚动幽灵流体槽 ⚠️
- `widget.SlotWidget`（+`$1`/`$EMICallWrapper`/`$JEICallWrapper`/`$REICallWrapper`/`$WidgetSlotItemHandler`）— 物品槽
- `widget.TankWidget`（+`$1`/`$EMICallWrapper`/`$JEICallWrapper`/`$REICallWrapper`）— 流体槽
- `widget.ToggleButtonWidget` — 切换按钮 ⚠️
- `widget.directional.CombinedDirectionalConfigurator`（+`$1`/`$2`）— 方向组合配置器 ⚠️
- `widget.directional.IDirectionalConfigHandler`（+`$ScreenSide`）— 方向配置处理器接口
- `widget.directional.handlers.AutoOutputFluidConfigHandler`（+`$1`/`$2`）— 流体自动输出配置
- `widget.directional.handlers.AutoOutputItemConfigHandler`（+`$1`/`$2`）— 物品自动输出配置
- `widget.directional.handlers.CoverableConfigHandler`（+`$1`/`$2`/`$3`）— 覆盖板配置 ⚠️

### A.16 `api.item`

- `ComponentItem` — 组件化物品基类（attachComponents 组合行为）
- `DrumMachineItem` — 桶机器物品 ⚠️
- `GTBucketItem` — GT 流体桶物品
- `IComponentItem`（+`$1`）— 组件物品接口
- `IGTTool`（+`$1`/`$2`/`$3`）— 工具统一接口（电动/材料/音效/定义）
- `LampBlockItem`（+`$1`/`$ClientCallWrapper`/`$ClientCallWrapper$1`）— 灯方块物品 ⚠️
- `LaserPipeBlockItem` — 激光管道物品 ⚠️
- `MaterialBlockItem` — 材料方块物品
- `MaterialPipeBlockItem` — 材料管道方块物品 ⚠️
- `MetaMachineItem`（+`$1`）— 机器物品
- `PipeBlockItem` — 管道物品 ⚠️
- `QuantumTankMachineItem` — 量子箱/量子罐物品 ⚠️
- `SurfaceRockBlockItem` — 地表岩石物品 ⚠️
- `TagPrefixItem` — 材料物品（TagPrefix+Material）
- `armor.ArmorComponentItem`（+`$1`）— 护甲组件物品
- `armor.ArmorLogicSuite`（+`$1`/`$2`）— 护甲逻辑套件（纳米/量子胸甲等）
- `armor.ArmorUtils`（+`$ModularHUD`）— 护甲工具（HUD）
- `armor.DummyArmorLogic` — 空护甲逻辑
- `armor.IArmorLogic` — 护甲逻辑接口
- `capability.ElectricItem` — 电动物品能力实现（IElectricItem）
- `component.ElectricStats` — 电动统计组件（电量/档位）
- `component.FilteredFluidContainer` — 过滤流体容器组件 ⚠️
- `component.FoodStats` — 食物组件
- `component.IAddInformation` — 工具提示组件接口
- `component.ICustomDescriptionId` — 自定义描述 id 组件接口
- `component.ICustomRenderer` — 自定义渲染器组件接口
- `component.IDataItem` — 数据物品组件接口 ⚠️
- `component.IDurabilityBar` — 耐久条组件接口 ⚠️
- `component.IEdibleItem` — 可食用组件接口
- `component.IEnchantableItem` — 可附魔组件接口
- `component.IInteractionItem` — 交互组件接口
- `component.IItemAttributes` — 属性组件接口 ⚠️
- `component.IItemComponent` — 组件基接口
- `component.IItemDecoratorComponent` — 装饰组件接口 ⚠️
- `component.IItemHUDProvider` — HUD 提供组件接口 ⚠️
- `component.IItemLifeCycle` — 生命周期组件接口
- `component.IItemUIFactory` — 手持 UI 组件接口
- `component.IMaterialPartItem` — 材料部件组件接口 ⚠️
- `component.IMonitorModuleItem` — 监控模块组件接口 ⚠️
- `component.IRecipeRemainder` — 合成残留组件接口 ⚠️
- `component.ISubItemHandler` — 子物品组件接口
- `component.ThermalFluidStats` — 流体容器统计组件（容量/温限/耐性）
- `component.forge.IComponentCapability` — Forge 能力组件接口
- `tool.GTAxeItem` / `tool.GTHoeItem` / `tool.GTShovelItem` / `tool.GTSwordItem` — 各类 GT 工具物品
- `tool.GTToolItem` — 通用工具物品（DiggerItem + IGTTool）
- `tool.GTToolType`（+`$Builder`/`$ToolConstructor`/`$ToolItemTagType`）— 工具类型与 Builder
- `tool.IGTToolDefinition` — 工具定义接口
- `tool.IToolGridHighlight` — 工具网格高亮接口
- `tool.MaterialToolTier` — 材料工具等级（原版 Tier）
- `tool.ToolDefinitionBuilder`（+`$1`）— 工具定义构建器（行为/耐久/效率/AoE）
- `tool.ToolHelper` — 工具辅助 ⚠️
- `tool.TreeFellingHelper` — 砍树辅助 ⚠️
- `tool.aoe.AoESymmetrical` — 对称范围（3x3 等）
- `tool.behavior.IToolBehavior` — 工具行为接口（右键行为）
- `tool.behavior.IToolUIBehavior` — 工具 UI 行为接口 ⚠️

### A.17 `api.machine`

- `ConditionalSubscriptionHandler` — 条件订阅处理器（条件满足才 tick）
- `IMachineBlockEntity` — 机器方块实体接口
- `MachineCoverContainer`（+`$1`）— 机器覆盖板容器
- `MachineDefinition` — 机器定义（配方类型/档位/模型属性/生命周期回调）
- `MetaMachine` — 机器实例基类
- `MultiblockMachineDefinition` — 多块机器定义（pattern/shape/generator）
- `SimpleGeneratorMachine` — 简单发电机（配方产电）
- `SimpleTieredMachine`（+`$1`）— 标准单块机器（电路槽+充电槽+自动输出）
- `TickableSubscription` — tick 订阅句柄
- `TieredEnergyMachine` — 档位能量机器（能量容器+爆炸检查）
- `TieredMachine` — 档位机器基类
- `WorkableTieredMachine` — 工作档位机器（RecipeLogic + 配方 IO traits）
- `fancyconfigurator.AutoStockingFancyConfigurator` — 自动补货配置器 ⚠️
- `fancyconfigurator.ButtonConfigurator` — 按钮配置器 ⚠️
- `fancyconfigurator.CircuitFancyConfigurator` — 电路配置器
- `fancyconfigurator.CombinedDirectionalFancyConfigurator` — 方向配置器 ⚠️
- `fancyconfigurator.FancyInvConfigurator` — 物品栏配置器 ⚠️
- `fancyconfigurator.FancySelectorConfigurator` — 选择器配置器 ⚠️
- `fancyconfigurator.FancyTankConfigurator` — 流体槽配置器 ⚠️
- `fancyconfigurator.MachineModeFancyConfigurator`（+`$MachineModeConfigurator`）— 机器模式配置器
- `feature.IAutoOutputBoth` — 物品+流体自动输出
- `feature.IAutoOutputFluid` / `feature.IAutoOutputItem` — 自动输出接口
- `feature.ICleanroomProvider` — 洁净室提供（getTypes/isClean）
- `feature.IDataInfoProvider` — 扫描仪数据信息提供
- `feature.IDataStickInteractable` — 数据棒交互 ⚠️
- `feature.IDropSaveMachine` — 掉落保存 ⚠️
- `feature.IEnvironmentalHazardCleaner` — 环境危害清除 ⚠️
- `feature.IEnvironmentalHazardEmitter` — 环境危害发射（污染）
- `feature.IExhaustVentMachine` — 排气机器（蒸汽机）
- `feature.IExplosionMachine` — 爆炸机器（doExplosion）
- `feature.IFancyUIMachine`（+`$1`）— FancyUI 机器
- `feature.IHasCircuitSlot` — 电路槽机器
- `feature.IInteractedMachine` — 交互机器 ⚠️
- `feature.ILocalizedHazardEmitter` — 局部危害发射 ⚠️
- `feature.IMachineFeature` — 机器特征基接口
- `feature.IMachineLife` — 机器生命周期（onLoad/onUnload）
- `feature.IMachineModifyDrops` — 掉落修改 ⚠️
- `feature.IMufflableMachine` — 消音机器
- `feature.IOverclockMachine` — 超频机器
- `feature.IRecipeLogicMachine` — 配方逻辑机器（核心接口）
- `feature.IRedstoneSignalMachine` — 红石信号机器 ⚠️
- `feature.ITieredMachine` — 档位机器
- `feature.IUIMachine` — UI 机器 ⚠️
- `feature.IVoidable`（+`$VoidingMode`）— 可溢出机器
- `feature.multiblock.IDisplayUIMachine` — 多块显示屏 UI（addDisplayText）
- `feature.multiblock.IDistinctPart` — 区分部件
- `feature.multiblock.IFluidRenderMulti` — 流体渲染 ⚠️
- `feature.multiblock.IMaintenanceMachine` — 维护机（问题/修复/加速）
- `feature.multiblock.IMufflerMachine` — 消音器部件（回收物）
- `feature.multiblock.IMufflerMechanic` — 消音机制 ⚠️
- `feature.multiblock.IMultiController` — 多块控制器特征
- `feature.multiblock.IMultiPart` — 多块部件特征
- `feature.multiblock.IRotorHolderMachine` — 转子支架
- `feature.multiblock.IWorkableMultiController` — 可工作多块控制器
- `multiblock.CleanroomType` — 洁净室类型（CLEANROOM/STERILE_CLEANROOM）
- `multiblock.CoilWorkableElectricMultiblockMachine` — 线圈电力多块机（EBF）
- `multiblock.DummyCleanroom` — 空洁净室 ⚠️
- `multiblock.IBatteryData` — 电池数据（PSS）⚠️
- `multiblock.MultiblockControllerMachine` — 多块控制器机器基类
- `multiblock.MultiblockDisplayText`（+`$Builder`）— 多块状态文本构建
- `multiblock.PartAbility` — 部件能力（能量舱/物品舱/维护/消音...）
- `multiblock.TieredWorkableElectricMultiblockMachine` — 定档电力多块机
- `multiblock.WorkableElectricMultiblockMachine` — 电力多块机（能量列表+批量模式）
- `multiblock.WorkableMultiblockMachine` — 工作多块机基类
- `multiblock.part.MultiblockPartMachine` — 多块部件机器基类
- `multiblock.part.TieredIOPartMachine`（+`$1`）— 档位 IO 部件（输入/输出舱）
- `multiblock.part.TieredPartMachine` — 档位部件
- `property.GTMachineModelProperties` — 机器渲染状态属性常量
- `steam.SimpleSteamMachine` — 简单蒸汽机（需排气）
- `steam.SteamBoilerMachine` — 蒸汽锅炉（温度模拟）
- `steam.SteamEnergyRecipeHandler` — 蒸汽能量配方 handler
- `steam.SteamMachine` — 蒸汽机基类（高低压）
- `steam.SteamWorkableMachine` — 蒸汽工作机
- `trait.FluidTankProxyTrait` — 流体槽代理 trait ⚠️
- `trait.ICapabilityTrait` — 能力 trait 接口
- `trait.IRecipeHandlerTrait` — 配方 handler trait 接口
- `trait.ItemHandlerProxyTrait` — 物品槽代理 trait ⚠️
- `trait.MachineTrait` — 机器 trait 基类
- `trait.NotifiableComputationContainer` — 计算力容器 trait
- `trait.NotifiableEnergyContainer` — 能量容器 trait
- `trait.NotifiableFluidTank` — 流体槽 trait
- `trait.NotifiableItemStackHandler`（+`$KJSCallWrapper`）— 物品槽 trait
- `trait.NotifiableLaserContainer` — 激光容器 trait ⚠️
- `trait.NotifiableRecipeHandlerTrait` — 配方 handler trait 基类
- `trait.RecipeAmperageEnergyContainer` — 电流能量容器 ⚠️
- `trait.RecipeHandlerGroup` — 配方 handler 分组
- `trait.RecipeHandlerGroupColor` — 分组颜色 ⚠️
- `trait.RecipeHandlerGroupDistinctness` — 分组区分性 ⚠️
- `trait.RecipeHandlerList`（+`$Subscription`）— 配方 handler 列表
- `trait.RecipeLogic`（+`$Status`）— 配方逻辑（核心执行器）

### A.18 `api.misc`

- `EnergyContainerList` — 能量容器聚合列表（多块能量）
- `EnergyInfoProviderList` — 能量信息聚合 ⚠️
- `IOFilteredInvWrapper` — IO 过滤物品栏包装 ⚠️
- `IOFluidHandlerList` — IO 过滤流体列表
- `IgnoreEnergyRecipeHandler` — 忽略能量配方 handler ⚠️
- `ImageCache` — 图片缓存 ⚠️
- `ItemRecipeHandler` — 轻量物品配方 handler
- `LaserContainerList` — 激光容器列表 ⚠️
- `RateCounter` — 速率计数器
- `forge.FilteredFluidHandlerItemStack` — 过滤流体容器物品（Forge）
- `forge.FilteredFluidHandlerItemStackSimple` — 简单过滤流体容器物品
- `forge.QuantumFluidHandlerItemStack` — 量子流体容器物品 ⚠️
- `forge.SimpleThermalFluidHandlerItemStack` — 简单温限流体容器物品
- `forge.ThermalFluidHandlerItemStack` — 温限流体容器物品
- `forge.VoidFluidHandlerItemStack` — 虚空流体容器物品 ⚠️
- `virtualregistry.EntryTypes` — 虚拟条目类型（ENDER_FLUID/ENDER_ITEM/ENDER_REDSTONE）
- `virtualregistry.VirtualEnderRegistry` — 末影虚拟寄存器（SavedData）
- `virtualregistry.VirtualEntry` — 虚拟条目基类
- `virtualregistry.VirtualRegistryMap` — 虚拟寄存器映射 ⚠️
- `virtualregistry.entries.VirtualItemStorage` — 虚拟物品存储（末影物品链路）
- `virtualregistry.entries.VirtualRedstone` — 虚拟红石（末影红石链路）
- `virtualregistry.entries.VirtualTank` — 虚拟流体槽（末影流体链路）

### A.19 `api.pattern`

- `BlockPattern`（+`$1`）— 多块结构模式（检查/预览/自动建造）
- `FactoryBlockPattern`（+`$1`）— 模式构建器（aisle/aisleRepeatable/where/build）
- `MultiblockShapeInfo`（+`$ShapeInfoBuilder`）— JEI 预览结构
- `MultiblockState` — 模式检查状态 ⚠️
- `MultiblockWorldSavedData` — 多块世界存档 ⚠️
- `Predicates` — 谓词工厂（controller/blocks/states/abilities/autoAbilities/...）
- `TraceabilityPredicate` — 可追踪谓词（限制/IO/预览）
- `error.PatternError` — 模式错误基类
- `error.PatternStringError` — 字符串错误 ⚠️
- `error.SinglePredicateError` — 单谓词错误 ⚠️
- `predicates.PredicateBlockTag` — 方块标签谓词
- `predicates.PredicateBlocks` — 方块列表谓词
- `predicates.PredicateFluidTag` — 流体标签谓词
- `predicates.PredicateFluids` — 流体列表谓词
- `predicates.PredicateStates` — 方块状态谓词
- `predicates.SimplePredicate` — 简单谓词基类
- `util.PatternMatchContext` — 匹配上下文（getOrCreate/递增）
- `util.RelativeDirection`（+`$1`）— 相对方向（UP/DOWN/LEFT/RIGHT/FRONT/BACK）

### A.20 `api.pipenet`

- `IAttachData` — 附着数据 ⚠️
- `IMaterialPipeType` — 材料管道类型 ⚠️
- `IPipeNode` — 管道节点接口
- `IPipeType` — 管道类型接口
- `IRoutePath` — 路由路径 ⚠️
- `ITickablePipeNet` — 可 tick 管道网络 ⚠️
- `LevelPipeNet` — 维度管道网络数据（SavedData）
- `Node` — 管道节点（data/openConnections/mark）
- `PipeCoverContainer`（+`$1`）— 管道覆盖板容器
- `PipeNet` — 管道网络基类
- `PipeNetWalker` — 管道网络遍历器
- `TickableLevelPipeNet` — 可 tick 维度管道网络 ⚠️
- `longdistance.ILDEndpoint` — 长途管道端点 ⚠️
- `longdistance.ILDNetworkPart` — 长途网络部件 ⚠️
- `longdistance.LongDistanceNetwork`（+`$WorldData`）— 长途网络（物流管道）
- `longdistance.LongDistancePipeBlock` — 长途管道方块
- `longdistance.LongDistancePipeType` — 长途管道类型（fluid()/item()）
- `longdistance.NetworkBuilder` — 网络构建线程

### A.21 `api.placeholder`

- `GraphicsComponent`（+`$1`）— 图形组件 ⚠️
- `IPlaceholderInfoProviderCover` — 占位符信息提供覆盖板 ⚠️
- `IPlaceholderRenderer` — 占位符渲染器
- `MultiLineComponent` — 多行文本组件 ⚠️
- `Placeholder` — 占位符基类（中央监控文本）
- `PlaceholderContext` — 占位符上下文（level/pos/side/uuid）
- `PlaceholderHandler`（+`$RendererHolder`）— 占位符处理器 ⚠️
- `PlaceholderUtils` — 占位符工具（checkArgs/toInt 等）
- `exceptions.*`（11 个异常类）— 占位符异常族：InvalidArgsException/InvalidNumberException/
  MissingItemException/NoMENetworkException/NotEnoughArgsException/NotSupportedException/
  OutOfRangeException/PlaceholderException/UnclosedBracketException/UnexpectedBracketException/
  UnknownPlaceholderException/WrongNumberOfArgsException

### A.22 `api.recipe`

- `ActionResult` — 配方动作结果（SUCCESS/FAIL_NO_REASON/PASS_NO_CONTENTS...）
- `DummyCraftingContainer` — 空合成容器 ⚠️
- `FacadeCoverRecipe`（+`$1`）— 伪装板合成配方 ⚠️
- `GTRecipe` — 配方数据（输入/输出/概率/条件/数据）
- `GTRecipeSerializer`（+`$KJSCallWrapper`）— 配方序列化器
- `GTRecipeType`（+`$ICustomRecipeLogic`）— 配方类型
- `OverclockingLogic`（+`$OCParams`/`$OCResult`）— 超频逻辑（标准/完美/线圈）
- `RecipeCondition` — 配方条件抽象基类
- `RecipeHelper` — 配方工具（getRealEUt/getInputItems/getOutputFluids/matchRecipe/...）
- `RecipeRunner` — 配方运行器 ⚠️
- `ResearchData`（+`$ResearchEntry`）— 研究数据（扫描/研究站存档）
- `ResearchRecipeBuilder`（+`$ScannerRecipeBuilder`/`$StationRecipeBuilder`）— 研究配方构建
- `ShapedEnergyTransferRecipe`（+`$Serializer`）— 能量转移有序配方 ⚠️
- `ShapedFluidContainerRecipe`（+`$Serializer`）— 流体容器有序配方 ⚠️
- `StrictShapedRecipe`（+`$Serializer`）— 严格有序配方 ⚠️
- `ToolHeadReplaceRecipe` — 工具头替换配方 ⚠️
- `category.GTRecipeCategory` — 配方分类（JEI 页面）
- `chance.boost.ChanceBoostFunction` — 概率提升函数（OVERCLOCK/NONE）
- `chance.logic.ChanceLogic`（+`$1`–`$5`）— 概率逻辑（OR/AND/FIRST/XOR/NONE）
- `condition.RecipeConditionType`（+`$ConditionFactory`）— 条件类型（codec+工厂）
- `content.Content`（+`$1`）— 配方内容（对象+概率+提升）
- `content.ContentModifier` — 内容修改器（乘/加）
- `content.IContentSerializer` — 内容序列化接口
- `content.SerializerBigInteger/SerializerBlockState/SerializerDouble/SerializerEnergyStack/
  SerializerFloat/SerializerFluidIngredient/SerializerIngredient/SerializerInteger/
  SerializerLong` — 各类型序列化实现
- `ingredient.EnergyStack`（+`$WithIO`）— 能量栈（电压+电流 record）
- `ingredient.FluidContainerIngredient`（+`$1`）— 流体容器物品 ingredient
- `ingredient.FluidIngredient`（+`$1`/`$FluidValue`/`$TagValue`/`$Value`）— 流体 ingredient
- `ingredient.IRangedIngredient` — 范围 ingredient 接口（随机数量）
- `ingredient.IntCircuitIngredient`（+`$1`）— 编程电路 ingredient（1-32）
- `ingredient.IntProviderFluidIngredient` — 随机数量流体 ingredient
- `ingredient.IntProviderIngredient`（+`$1`）— 随机数量物品 ingredient
- `ingredient.NBTPredicateIngredient`（+`$Serializer`）— NBT 谓词物品 ingredient
- `ingredient.SizedIngredient`（+`$1`）— 定量 ingredient（固定数量）
- `ingredient.nbtpredicate.AllNBTPredicate/AnyNBTPredicate/ComparisonNBTPredicate/
  EqualsNBTPredicate/NBTPredicate/NBTPredicateUtils/NBTPredicates/NotNBTPredicate/
  TrueNBTPredicate` — NBT 谓词体系（eq/neq/lt/gt/any/all/not）
- `lookup.Branch` — 查找树分支 ⚠️
- `lookup.MapIngredientPool` — 查找 ingredient 池（标签合并）
- `lookup.RecipeAdditionHandler` — 配方添加处理器（暂存）
- `lookup.RecipeDB`（+`$RecipeIterator`/`$SearchFrame`）— 配方数据库（主查找）
- `lookup.RecipeManagerHandler` — 配方管理器处理器 ⚠️
- `lookup.StagingRecipeDB` — 暂存配方库（数据生成批量入库）
- `lookup.ingredient.AbstractMapIngredient` — 查找 ingredient 基类
- `lookup.ingredient.MapIngredientFunction` — 映射函数 ⚠️
- `lookup.ingredient.MapIngredientTypeManager` — 类型管理器 ⚠️
- `lookup.ingredient.fluid.FluidStackMapIngredient` / `FluidTagMapIngredient` — 流体查找 ingredient
- `lookup.ingredient.item.CustomMapIngredient/IntersectionMapIngredient/ItemStackMapIngredient/
  ItemTagMapIngredient/NBTPredicateItemStackMapIngredient/PartialNBTItemStackMapIngredient/
  StrictNBTItemStackMapIngredient` — 物品查找 ingredient 族
- `modifier.EfficiencyModifier` — 效率修改器（EU 折扣+速度提升）
- `modifier.ModifierFunction`（+`$1`/`$FunctionBuilder`）— 修改函数
- `modifier.ParallelLogic` — 并行计算（getParallelAmount/getMaxByInput/...）
- `modifier.RecipeModifier` — 配方修改器接口
- `modifier.RecipeModifierList` — 修改器列表
- `ui.GTRecipeTypeUI`（+`$RecipeHolder`）— 配方类型 UI（JEI 页面布局）

### A.23 `api.registry` + `api.registry.registrate`

- `GTRegistries`（+`$ClientHelpers`）— 全部 GT 注册表入口
- `GTRegistry`（+`$RL`/`$String`）— 注册表基类
- `registrate.BuilderBase` — registrate builder 基类（KJS 集成点）
- `registrate.GTBlockBuilder` — 方块 builder（gtBlockstate/addLayer/simpleBlockEntity...）
- `registrate.GTRegistrate` — 主注册助手（machine/multiblock/block/fluid/sound/creativeTab）
- `registrate.IGTFluidBuilder` — 流体 builder 接口
- `registrate.MachineBuilder`（+`$BlockBuilderWrapper`/`$ItemBuilderWrapper`/`$KJSCallWrapper`/`$ModelInitializer`）— 机器 builder（全部参数见第 4 部分）
- `registrate.MultiblockMachineBuilder` — 多块机器 builder
- `registrate.SoundEntryBuilder`（+`$SoundEntryProvider`）— 音效 builder + 数据生成器
- `registrate.forge.GTClientFluidTypeExtensions` — 客户端流体类型扩展 ⚠️
- `registrate.forge.GTFluidBuilder`（+`$1`/`$FluidTypeFactory`）— Forge 流体 builder

### A.24 `api.sound`

- `AutoReleasedSound` — 自动释放音效（机器运行音）⚠️
- `ConfiguredSoundEvent` — 配置音效（事件+音量+音调）
- `CustomSoundEntry` — 自定义音效条目
- `ExistingSoundEntry` — 已有音效包装 ⚠️
- `SoundEntry` — 音效条目基类（播放/注册/数据生成）
- `WrappedSoundEntry`（+`$CompiledSoundEvent`）— 包装音效 ⚠️

### A.25 `api.transfer`

- `fluid.CustomFluidTank` — 自定义流体槽（Forge FluidTank 扩展）
- `fluid.FluidHandlerDelegate` — 流体 handler 委托 ⚠️
- `fluid.FluidHandlerList` — 流体 handler 列表（聚合）
- `fluid.IFluidHandlerModifiable` — 可修改流体 handler 接口
- `fluid.ModifiableFluidHandlerWrapper` — 可修改包装 ⚠️
- `item.CustomItemStackHandler` — 自定义物品槽（Forge ItemStackHandler 扩展）
- `item.ItemHandlerDelegate` — 物品 handler 委托
- `item.NotifiableAccountedInvWrapper` — 记录型物品栏包装 ⚠️

## 附录 B：KJS 插件类清单（68 类，`integration.kjs`，来自 gtm_kjs_classes.txt）

- `GTCEuStartupEvents` — 启动事件组（registry/materialModification/craftingComponents）
- `GTCEuServerEvents` — 服务端事件组（oreVeins/bedrockOreVeins/fluidVeins/registerCapes）
- `GTRegistryInfo`（+`$BuilderFactory`/`$BuilderType`）— 注册表信息（注册表 id 列表、builder 类型、postEvent）
- `GregTechKubeJSPlugin` — KJS 插件主类（registerEvents/registerRecipeSchemas/registerBindings/injectRuntimeRecipes）
- `Validator`（+`$ValidationResult`）— KJS 注册校验器（errorIfNull/warnRecommendedIfNull 等）
- `builders.ElementBuilder` — 元素 builder（protons/neutrons/halfLifeSeconds/decayTo/symbol/isIsotope）
- `builders.FluidVeinBuilderJS` — 流体矿脉 builder（yield/dimensions/biomes/weight/...）
- `builders.GTRecipeCategoryBuilder` — 配方分类 builder（setItemIcon/setCustomIcon/isXEIVisible）
- `builders.GTRecipeTypeBuilder` — 配方类型 builder（category/setEUIO/setMaxIOSize/setSlotOverlay/...）
- `builders.MaterialIconSetBuilder` — 图标集 builder（parent）
- `builders.MaterialIconTypeBuilder` — 图标类型 builder
- `builders.WorldGenLayerBuilder` — 世界生成层 builder（targets/dimensions）
- `builders.block.ActiveBlockBuilder`（+`$Type`）— 活跃方块 builder（simple/firebox/bloom）
- `builders.block.CoilBlockBuilder` — 线圈方块 builder（temperature/level/energyDiscount/tier/coilMaterial/texture）
- `builders.machine.KJSSteamMachineBuilder`（+`$SteamCreationFunction`/`$SteamDefinitionFunction`）— 蒸汽机 builder
- `builders.machine.KJSTieredMachineBuilder`（+`$CreationFunction`/`$DefinitionFunction`/`$TieredCreationFunction`）— 档位机器 builder（simple/generator/kinetic）
- `builders.machine.KJSTieredMultiblockBuilder`（+`$DefinitionFunction`/`$TieredCreationFunction`）— 档位多块 builder
- `builders.machine.KJSWrappingMachineBuilder` — 单块包装 builder
- `builders.machine.KJSWrappingMultiblockBuilder` — 多块包装 builder
- `builders.prefix.BasicTagPrefixBuilder` — 基础前缀 builder
- `builders.prefix.OreTagPrefixBuilder` — 矿前缀 builder（stateSupplier/doubleDrops/isSand/...）
- `builders.prefix.TagPrefixBuilder` — 前缀 builder 基类（idPattern/materialAmount/...）
- `built.KJSTagPrefix` — KJS 前缀实现类
- `events.CraftingComponentsEventJS`（+`$ComponentWrapper`）— 合成组件事件（createItem/createTag/...）
- `events.GTBedrockOreVeinEventJS` — 基岩矿事件（add/remove/modify）
- `events.GTFluidVeinEventJS` — 流体矿脉事件
- `events.GTOreVeinEventJS` — 矿脉事件（add/modify/modifyAll/remove/removeAll）
- `events.GTRegistryEventJS` — 注册表事件（create(id[, type])）
- `events.MaterialModificationEventJS` — 材料修改事件
- `events.RegisterCapesEventJS` — 披风注册事件
- `helpers.MachineConstructors` — 预置机器构造器（大型涡轮/聚变/大型内燃机/蒸汽多块）
- `helpers.MachineModifiers` — 预置配方修改器常量（SIMPLE_STEAM/LARGE_TURBINE/FUSION_REACTOR/...）
- `helpers.MaterialStackWrapper` — 材料栈包装（fromString('2x iron')/toMatStack）
- `recipe.GTRecipeSchema`（+`$GTRecipeJS`）— 配方 schema（ID/DURATION/DATA/CONDITIONS/ALL_INPUTS/...）
- `recipe.GTShapedRecipeSchema`（+`$ShapedRecipeJS`）— 成形配方 schema
- `recipe.KJSHelpers` — KJS 帮助（parseIOEnergyStack/parseEnergyStack）
- `recipe.WrappingRecipeSchemaType` — 包装 schema 类型 ⚠️
- `recipe.components.CapabilityMap` — 能力映射（IdentityHashMap，支持 replaceInput/replaceOutput）
- `recipe.components.CapabilityMapComponent` — 能力映射组件
- `recipe.components.ContentJS` — 内容包装组件（Content+能力）
- `recipe.components.ExtendedOutputItem` — 扩展输出物品（范围/次数）
- `recipe.components.GTRecipeComponents`（+`$1`–`$8`/`$FluidIngredientJS`）— 全部配方组件常量（ITEM_IN/FLUID_OUT/EU_IN/CWU_IN/IN/OUT/TICK_IN/TICK_OUT/...）
- `recipe.components.JavaMapRecipeComponent` — Java Map 配方组件

> 附：正文中引用的 common/data 常量类（不在 api 清单内，jar 实测存在）：
> `GTMaterials`、`GTItems`、`GTBlocks`、`GTMaterialItems`、`GTRecipeTypes`、
> `GTRecipeCapabilities`、`GTFluids`、`GTSoundEntries`、`GTMachines`、`GTElements`、
> `GTRecipeModifiers`、`data.recipe.GTCraftingComponents`、`data.recipe.builder.GTRecipeBuilder`。
