# PneumaticCraft: Repressurized 集成规格

> 状态：**地基已落地并验证** — 2026-09-26
> 目标 mod：PneumaticCraft: Repressurized **6.0.23+mc1.20.1**（MC 1.20.1 / Forge 47.4.22）
> 本文是长期参考：API 清单、许可边界、代码纪律。具体玩法设计另见
> `巨型热力蒸汽锅炉-设计规格.md`。

---

## 一、许可：为什么可以链接，边界在哪

PnC 在仓库 README 的 "Licensing Information" 中明确声明：

| 部分 | 许可 |
|---|---|
| PnC 本体 | **GPLv3** |
| `me.desht.pneumaticcraft.api.*` | **LGPLv3** |
| 音频资源 | CC BY 3.0 / CC0 1.0（与本议题无关） |

关键原文：

> The separate API licensing is intended to allow other mods to **link against the API
> without needing to be licensed under the GPLv3**.

**推论**：本工程（LGPL-3.0）链接 PnC 的 API **完全合规**，不需要变成 GPLv3。

### 1.1 独立 API 制品（已逐一验证）

PnC 发布独立的 api classifier 制品，这是上述授权的物理载体：

```
me.desht.pneumaticcraft:pneumaticcraft-repressurized:6.0.23+mc1.20.1:api
```

| 项 | 实测 |
|---|---|
| 大小 | 178 KB（deobf 后 185 KB） |
| class 数 | **174** |
| 非 `api` 包的 class | **0** |
| 依赖（pom） | **无**（且不存在独立 `-api.pom`，用主 pom） |

对照主 jar：2180 个 class，其中 174 个与 api 制品**完全重叠** —— 即 api 是 main 的真子集。

> 注：`PressureTier` 等接口在 api 制品内定义为匿名类实现，因此 api jar 里也含少量 `$N.class` 内部类，属正常。

### 1.2 三条不可越的线

1. **只 import `me.desht.pneumaticcraft.api.*`**
   碰 `me.desht.pneumaticcraft.common.*` 就越界到 GPLv3 区域。
   → 由 `tools/check-pnc-boundary.py` 自动审计（见 §5）
2. **依赖只能是 `compileOnly`**，运行时用 `runtimeOnly` 本体
3. **严禁 `jarJar` / shade PnC**
   本工程 `jarJar` 名单里只有 GTCEu 与 GTMThings。把 PnC 塞进去会让 GTNEcore 整体被 GPLv3 传染。

---

## 二、构建配置（已落地）

### 2.1 `gradle.properties`

```properties
pnc_version=6.0.23+mc1.20.1
```

### 2.2 `build.gradle`

```gradle
// 编译期：只要 LGPLv3 的 api 子集
compileOnly fg.deobf("me.desht.pneumaticcraft:pneumaticcraft-repressurized:${pnc_version}:api")
// dev 运行期：本体（自带 api 包）
runtimeOnly fg.deobf("me.desht.pneumaticcraft:pneumaticcraft-repressurized:${pnc_version}")
```

**为什么不是 `implementation api` + `runtimeOnly jar`**：
api 是 main 的真子集（174 ⊆ 2180），那样写会让类路径出现两份同名类。
`compileOnly` 与 `runtimeOnly` 分属 compileClasspath / runtimeClasspath，互不相遇，零重复；
且 `compileOnly` 不进发布产物、不传递、不参与 `jarJar`。

仓库无需新增：工程已有 `https://modmaven.dev/`。

### 2.3 `mods.toml`

```toml
[[dependencies."${mod_id}"]]
   modId="pneumaticcraft"
   mandatory=false
   versionRange="[6.0.0,)"
   ordering="AFTER"
   side="BOTH"
```

注意 modId 用**本体 id**（`pneumaticcraft`），不是 maven 制品名。

> **版本对齐规则**：`compileOnly` 的版本必须 **≤ 整合包实际运行版本**，
> 否则用到新增 API 会在旧版上 `NoSuchMethodError`。

---

## 三、可用 API 清单

### 3.1 入口与 Capability

```java
// 总入口（含 14 个子注册表）
PneumaticRegistry.getInstance()          // → IPneumaticCraftInterface
  .getHeatRegistry()                     // IHeatRegistry
  .getAirHandlerMachineFactory()         // IAirHandlerMachineFactory
  .getFuelRegistry()                     // IFuelRegistry
  .getRecipeRegistry()                   // IPneumaticRecipeRegistry
  // 另有 getDroneRegistry / getSensorRegistry / getItemRegistry /
  //      getUpgradeRegistry / getWrenchRegistry / getMiscHelpers / ...

// Forge Capability（仅两个走标准 Capability 体系）
PNCCapabilities.HEAT_EXCHANGER_CAPABILITY       // Capability<IHeatExchangerLogic>
PNCCapabilities.HACKING_CAPABILITY              // Capability<IHacking>
// 另有 AIR_HANDLER / AIR_HANDLER_MACHINE / AIR_HANDLER_ITEM 三个 Capability 常量，
// 但常规 PnC 方块不靠它们暴露气压，而是靠工厂注册（见 §3.3）
```

### 3.2 热（最简单，走真 Capability）

```java
interface IHeatExchangerLogic extends INBTSerializable<CompoundTag> {
    void tick();
    void setTemperature(double t);  double getTemperature();  int getTemperatureAsInt();
    double getAmbientTemperature();
    void setThermalResistance(double r);  double getThermalResistance();
    void setThermalCapacity(double c);    double getThermalCapacity();
    void addHeat(double amount);                    // ← 注热/抽热接入点（单位 J）
    boolean isSideConnected(Direction side);
    void addConnectedExchanger(IHeatExchangerLogic other);
    // + addTemperatureListener / getHeatBehaviour / initializeAsHull
}

interface IHeatRegistry {
    IHeatExchangerLogic makeHeatExchangerLogic();   // ← 造实例
    void registerHeatBehaviour(ResourceLocation id, Supplier<? extends HeatBehaviour>);
}
```

实现：`org.ringclouds.gtnecore.integration.pneumaticcraft.heat.PncHeatBridge`（已落地）

### 3.3 气压（最重，需要自定义参数）

```java
interface IAirHandler {
    float getPressure();  int getAir();  void addAir(int amount);
    int getBaseVolume();  void setBaseVolume(int newVolume);  int getVolume();
    float maxPressure();
}

interface IAirHandlerMachine extends IAirHandler, IManoMeasurable, INBTSerializable<CompoundTag> {
    float getDangerPressure();      // ← 危险阈值（自爆判定的下界）
    float getCriticalPressure();    // ← 临界阈值（自爆判定）
    void setPressure(float p);
    void setVolumeUpgrades(int n);
    void enableSafetyVenting(FloatPredicate pressureCheck, Direction dir);  // ← 安全泄压
    void disableSafetyVenting();
    void tick(BlockEntity ownerTE);                    // ← 每 tick 推进压力状态
    void setSideLeaking(@Nullable Direction dir);
    Direction getSideLeaking();
    List<IAirHandlerMachine.Connection> getConnectedAirHandlers(BlockEntity ownerTE);
    void setConnectedFaces(List<Direction> sides);
}

interface IAirHandlerMachineFactory {
    IAirHandlerMachine createTierOneAirHandler(int volume);
    IAirHandlerMachine createTierTwoAirHandler(int volume);
    IAirHandlerMachine createAirHandler(PressureTier tier, int volume);   // ← 关键
}
```

#### ★ `PressureTier` 是接口，可以自定义任意档位

```java
interface PressureTier {
    float getDangerPressure();
    float getCriticalPressure();
}
```

PnC 自带三档（**注意这是它们的设备规格，不是系统上限**）：

| 常量 | danger | critical |
|---|---|---|
| `TIER_ONE` | 5 | 7 |
| `TIER_ONE_HALF` | 10 | 12 |
| `TIER_TWO` | 20 | 25 |

**推论**：传自己的 `PressureTier` 实现给 `createAirHandler(...)`，就能定义任意压力规模。
本工程的高压设备（如巨型热力蒸汽锅炉）用远超 25 bar 的档位，**bar 数值由我们自由定义**。

#### ★★ 爆炸逻辑必须自研

整个 `api` 包中**没有**任何 explode 工具：

- `PressureHelper` 只有 `getUpgradedVolume(int baseVolume, int upgradeCount)`
- `IMiscHelpers` 只有 `playMachineBreakEffect(BlockEntity)`（纯视觉）与 `ParticleOptions airParticle()`
- `DamageSources` / `DamageTypes` 只提供伤害类型

超压爆炸实现在 PnC 的 `common` 包（**GPLv3 区域，不可引用**）。
→ 所以**超压自爆由本工程自己实现**，这反而是好事：我们的爆炸语义（多方块解体、
按压力当量、自爆阈值随结构升级）与 PnC 机器的爆炸完全不同。

**自爆判定接法**：用 `getDangerPressure()` / `getCriticalPressure()` 表达档位，
`enableSafetyVenting(predicate, dir)` 做正常泄压；越过临界且泄压不足 → 触发自研爆炸。

### 3.4 配方与燃料（方向 C，未开工）

```java
interface IFuelRegistry { /* 见 sources */ }
interface IPneumaticRecipeRegistry { /* 见 sources */ }
PneumaticCraftRecipeTypes   // 配方类型常量
```
可用配方类：`AssemblyRecipe` / `PressureChamberRecipe` / `RefineryRecipe` /
`ThermoPlantRecipe` / `FluidMixerRecipe` / `HeatFrameCoolingRecipe` /
`HeatPropertiesRecipe` / `ExplosionCraftingRecipe` / `FuelQualityRecipe` 等。

> 完整 API 源码可解压 `...-sources.jar`（见 §5），比反编译准确。

---

## 四、代码组织

```
org.ringclouds.gtnecore.integration.pneumaticcraft
├── PncCompat.java                    唯一守卫点（零 PnC 引用，仅用 ModList）
├── heat/
│   └── PncHeatBridge.java            方向 B：热交换桥接
├── air/                              方向 A：气压（待建）
└── recipe/                           方向 C：配方/燃料（待建）
```

### 4.1 守卫点的必要性

任何直接 import PnC 的类，一旦在 PnC 缺席时被 JVM 加载，就会抛 `NoClassDefFoundError`
（类加载期解析引用）。因此：

- 所有 PnC 引用必须隔离在「确认 PnC 在场后才会被加载」的类里
- 那个确认动作本身**不能触碰任何 PnC 类型** → 这就是 `PncCompat` 只用 `ModList` 的原因

实测结果（`tools/check-pnc-boundary.py`）：

```
引用了 PnC 的已编译类
  集成包内（合法）: 1   → integration/pneumaticcraft/heat/PncHeatBridge.class
  集成包外（需为 0）: 0
越界引用 me.desht.pneumaticcraft.common.*（GPLv3）: 0
```

---

## 五、工具

### 5.1 `tools/check-pnc-boundary.py`

审计已编译 class，找出所有引用 PnC 类型的类并按包归属分类。

```bash
python tools/check-pnc-boundary.py
```

**期望输出**：集成包外 0 处、`common.*` 越界 0 处。新写 PnC 相关代码后必跑。

### 5.2 API 源码位置

`sources.jar` 解压后即完整 API 源码（含 javadoc 注释），比反编译准确：

```bash
curl.exe -sS -L -o sources.jar \
  "https://modmaven.dev/me/desht/pneumaticcraft/pneumaticcraft-repressurized/6.0.23+mc1.20.1/pneumaticcraft-repressurized-6.0.23+mc1.20.1-sources.jar"
```

> 本环境的 `Invoke-WebRequest` 对大文件会超时，**用 `curl.exe`**。
> `github.com` / `raw.githubusercontent.com` 在此环境 DNS 不可达。

---

## 六、验证状态

| 项 | 状态 |
|---|---|
| 依赖解析（`fg.deobf` → `_mapped_official_1.20.1`） | ✅ 通过 |
| api 制品内容（174 类 / 0 越界 / 已重映射） | ✅ 通过 |
| 类路径零重复类 | ✅ 通过 |
| `compileJava` | ✅ BUILD SUCCESSFUL |
| PnC 引用边界审计 | ✅ 通过 |
| **dev 客户端实际加载 PnC** | ✅ 用户实测「正常启动客户端」 |
| 热桥接挂到具体机器 | ⬜ 未开工（`PncHeatBridge` 目前无调用方） |
| 方向 A 气压实现 | ⬜ 未开工 |
| 方向 C 配方注册 | ⬜ 未开工 |

---

## 七、三条线的推进顺序

| 方向 | 内容 | 量级 | 状态 |
|---|---|---|---|
| **B. 热** | `IHeatExchangerLogic` 桥接 | 小（真 Capability，无需自 tick） | 适配层已落地 |
| **C. 配方/燃料** | `IFuelRegistry` / `IPneumaticRecipeRegistry` | 中（注册型，无运行时状态） | 待开工 |
| **A. 气压** | `IAirHandlerMachine` + `PressureTier` + 自研爆炸 | 重（有运行时状态机） | 待开工 |

建议顺序 **C → A**：C 是纯注册，用来验证配方注册通路；A 是唯一带状态机的，
等 C 验证完注册链路再动，风险最低。

**A 的首个落地对象是巨型热力蒸汽锅炉**（见 `巨型热力蒸汽锅炉-设计规格.md`）——
它把方向 A 从「接一根气管」升级为「用气压做机器机制」，是本工程对 PnC 最深度的复用。
