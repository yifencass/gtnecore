# GTNEcore 开发文档

> GTNEcore 是 GregTech-NewEra 整合包的核心 mod（Forge 1.20.1 / 47.4.22 / Java 17）：
> 扩展 GTM 电压体系到 22 档（突破 int 上限），强化 Re-Avaritia 装备。
>
> 本文件记录**本项目自己的开发内容**（patch 机制、电压体系、装备强化、构建流程）。
> GTCEu 的 API 参考见同目录下《GTM-7.5.3-API指导文档》；
> GTCEu 的 KubeJS 支持见同目录下《GTCEu-KubeJS开发文档》（详细版）、《GTCEu-KubeJS快速上手》（简单版）与《GTCEu-KubeJS多方块开发文档》（专题）；
> Goety 灵魂能量对接参考见同目录下《Goety-2.5.56.4-API指导文档》。

---

## 1. 项目概览

| 项 | 值 |
|---|---|
| mod_id | `gtnecore` |
| 包名 | `org.ringclouds.gtnecore` |
| 依赖 | GTCEu 7.5.3（**patch 版**，见 §4）、Re-Avaritia 1.4.1、Goety 2.5.56.4（+Curios）、JEI、Jade |
| 目标 | GTM 电压 22 档 + 21 亿 EU/t 突破、EU↔FE 能量转换、无尽剑强化、灵魂能量流体化（§9） |
| 许可 | LGPL 3.0 |

源码结构：

```
src/main/java/org/ringclouds/gtnecore/
├── Gtnecore.java                  # 主类：注册、事件挂钩
├── block/                         # 高电压机壳方块（CYV 等，gtceu 命名空间）
├── config/GTNEcoreConfig.java     # 服务端配置（FE↔EU 比率等）
├── item/                          # 高电压部件物品（UMV 电动马达等）
├── machine/                       # 能量立方 + 六面模式处理器
├── mixin/                         # GTMachineUtils/GTValues/TagLoader + 无尽剑三件套
├── handler/VoidMarkHandler.java   # 虚空诅咒标记执行器
├── recipe/TestVoltageRecipes.java # 电压上限测试配方（临时，测完删）
├── soulenergy/SoulEnergyFluids.java # 液态灵魂能量（Goety 兼容，§9）
└── voltages/GtneVoltages.java     # 22 档电压表（Unsafe 安装 + 名称/颜色表）
src/patch/java/                    # GTCEu patch 层（§4，路径不带 com.gregtechceu.gtceu 前缀）
```

---

## 2. 开发环境

- **JDK**：Microsoft JDK 17.0.20（`C:\Users\Administrator\.jdks\ms-17.0.20`，Gradle 自动检测）
- **S302 代理**：gradle.properties 的 jvmargs 必须保留
  `-Dnet.minecraftforge.gradle.check.certs=false` + `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT`，
  否则 PKIX 证书错误
- **Gradle**：wrapper 指向缓存版 `gradle-8.14.3`（services.gradle.org 被墙，勿改回官方 URL）
- **构建命令**：
  - `./gradlew.bat compileJava` — 主工程
  - `./gradlew.bat patchJar` — 编译 GTCEu patch 层（产物 `build/patched/gtceu-patch.jar`）
  - `./gradlew.bat genIntellijRuns` — **改过 build.gradle 的 runs 配置后必须重跑**，
    IDEA 才会拿到新的启动参数（如 mixin.config）
  - `./gradlew.bat runClient` / IDEA runClient — 进游戏
- **curse.maven 被墙**（connection reset）；modmaven.dev / maven.gtceu.com 可达，
  但部分构件（AE2、TOP）无 pom → 下载 jar 放 `libs/` 用 flatDir

---

## 3. 电压体系（22 档）

档位 `0=ULV .. 21=MAX`，电压 `V[i] = 8 × 4^i`（MAX = 2⁴⁵ ≈ 3.5×10¹³）。

```
0  ULV  Ultra Low              8              12 UMV  Ultimate Matter      134,217,728
1  LV   Low                    32             13 SWV  Stellar Warp         536,870,912
2  MV   Medium                 128            14 GCV  Galactic Core        2,147,483,648
3  HV   High                   512            15 UXV  Ultra eXtreme        8,589,934,592
4  EV   Extreme                2,048          16 QGV  Quantum Gravity      34,359,738,368
5  IV   Insane                 8,192          17 CYV  Calabi-Yau           137,438,953,472
6  LuV  Ludicrous              32,768         18 OpV  Overpowered          549,755,813,888
7  ZPM  ZPM                    131,072        19 VEV  Vacuum Expectation   2,199,023,255,552
8  UV   Ultimate               524,288        20 SGV  SuperGravity         8,796,093,022,208
9  UHV  Ultra High             2,097,152      21 MAX  Maximum              35,184,372,088,832
10 UEV  Ultra Excessive        8,388,608
11 UIV  Ultra Immense          33,554,432
```

**中文译名（社区翻译用，不进游戏）**：超低压…超过度电压/超巨电压/**极剧差压（UMV）**/恒星曲速压/银河核心压/超越极限电压/量子引力压/**卡丘压（CYV）**/超规格电压/**真空期望压（VEV）**/超引力压/最大电压。

### 安装机制（`GtneVoltages` + 两个 mixin）

- `GTValuesMixin` 在 `GTValues.<clinit>` 结束后调 `GtneVoltages.install()`，
  用 **Unsafe 覆写 static final 字段**（V/VN/VNF/VC/VCM/VLVH/VLVT/VCF/VOLTAGE_NAMES/LVT/ALL_TIERS/TIER_COUNT）
- `GTMachineUtilsMixin` 同理覆写 GTMachineUtils 的 5 个档位数组
  （ALL/ELECTRIC/HIGH/MULTI_HATCH/DUAL_HATCH_TIERS），使原生机器/部件注册延伸到 21 档
- **VH/VA/VHA 不再由 install() 覆写**——已由 patch 层直接改字段（见 §4），
  Unsafe 换回 int[] 会引发 IncompatibleClassChangeError
- `GtneVoltages` 静态初始化禁止读 GTValues/GTMachineUtils（mixin 注入点在它们 clinit 内，重入死锁）——所有表硬编码

---

## 4. GTCEu patch-jar 改造（突破 21 亿 EU/t）

### 背景与结论

- GTCEu 7.5.3 的配方能量层**本来就是 long**（`EnergyStack(voltage, amperage)`、`GTRecipeBuilder.EUt(long)`），
  "21 亿封顶"只是 `GTValues.VH/VA/VHA` 三张 **int[]** 派生表导致（配方生成器取 `VA[tier]` 当 EUt，
  15+ 档真实值超 int 会溢出负数）
- **不 fork 完整构建链**（S302 环境下 GTCEu 依赖下载断流，完整编译不可行）；
  用 patch-jar：只编译要改的类，合并进 deobf 官方 jar

### 目录与构建

```
src/patch/java/                    # patch 类源码（路径不带 com/gregtechceu/gtceu 前缀！）
├── api/                           # GTValues、BlastProperty、IHPCAComponentHatch、ResearchRecipeBuilder
├── common/                        # 机器逻辑（HPCA/DataBank/NetworkSwitch、trait 逻辑、材料）
└── data/                          # 配方生成器全系（generated/misc/serialized 60+ 类）
```

build.gradle 关键块：

```groovy
sourceSets {
    patch {
        java { srcDir 'src/patch/java' }
        compileClasspath += sourceSets.main.compileClasspath
    }
}
tasks.register('patchJar', Jar) {
    archiveFileName = 'gtceu-patch.jar'
    destinationDirectory = file('build/patched')
    from sourceSets.patch.output
}
// 依赖：
patchCompileOnly 'org.projectlombok:lombok:1.18.30'          // GTCEu 源码大量 lombok
patchAnnotationProcessor 'org.projectlombok:lombok:1.18.30'
patchCompileOnly "blank:appliedenergistics2-forge:15.4.9"    // libs/ flatDir（modmaven 无 pom）
patchCompileOnly "blank:theoneprobe:1.20.1-10.0.1-3"         // libs/ flatDir
```

### 合并流程（必须用 python zipfile！）

```bash
cd build/patched
rm -rf merged && mkdir merged && cd merged
unzip -q <deobf gtceu jar> -d .          # ~/.gradle/caches/forge_gradle/deobf_dependencies/...
unzip -qo ../gtceu-patch.jar -d .        # patch 类覆盖
cd .. && python 打包（os.walk 全量写 zip） # ⚠️ jar 命令会漏根目录 json（gtceu.mixins.json 等）！
```

产物 `build/patched/gtceu-1.20.1-7.5.3-patched.jar`，GTNEcore 依赖：

```groovy
implementation files('build/patched/gtceu-1.20.1-7.5.3-patched.jar')
```

### mixin 注册（关键）

files() 依赖时 MixinGradle 不自动扫描 mixin 配置，**必须手动注册**，否则 GTCEu 的
mixin（AbstractRegistrateAccessor/IntegerPropertyAccessor 等）不生效 → 各种 ClassCastException：

```groovy
runs { configureEach {
    args '--mixin.config', 'gtceu.mixins.json'
} }
```

改完必须 `genIntellijRuns`，否则 IDEA 旧 run 配置缺参数。

### patch 内容（75 个类）

1. `GTValues`：VH/VA/VHA `int[]`→`long[]`，22 档真实值
   （VH=4×4^i；VA=8×4^i×15/16；VHA=VA/2）
2. `BlastProperty`：EUtOverride/vacuumEUtOverride 字段、构造器、Builder 的
   blastStats/vacuumStats（含双参重载）int→long（duration 保持 int）
3. `IHPCAComponentHatch`：getUpkeepEUt/getMaxEUt int→long
4. 全部 VH/VA/VHA 引用类（配方生成器、机器逻辑、Jade/TOP provider）——编译错误驱动修
5. 连锁类：HPCAMachine（内部类调用 getUpkeepEUt）、DataBankMachine
   （calculateEnergyUsage/getEnergyUsage、EUT_PER_HATCH 常量——getEnergyUsage 是 lombok getter 自动 long）

### 踩坑清单

- **引用类清单必须全量 javap 扫描**（`GTValues.(VH|VA|VHA)` 的 getstatic），
  head/tail 截断会漏类 → 运行期 `NoSuchFieldError`（漏了 ReactorRecipes 等 5 个）
- 同一 FQCN 两个源码副本（带/不带 com/gregtechceu/gtceu 前缀）会编译混乱——目录必须统一
- 编译错误日志是 GBK 编码（python 用 `encoding='gbk'` 读）
- lombok 必须给 patch sourceSet 单独配 annotationProcessor
- 配方 EUt 是 EnergyStack(long)，`EUt(VA[tier])` 直接兼容，无需额外改动

---

## 5. 无尽剑强化（Re-Avaritia）

`InfinitySwordItemMixin`（+ `ToolUtilsMixin` + `VoidMarkHandler`），Mixin 0.8.5 规则详见
`docs/api-material` 相关笔记或记忆：

| 功能 | 实现 |
|---|---|
| 左键攻击前清目标无敌帧 | `@Redirect` custom `hurt()` 首行 `LivingEntity.level()` 调用 |
| 攻击后无敌帧归零 | `@Redirect` `hurt()` 内 `invulnerableTime` PUTFIELD（剑设的 20→0） |
| 无尽处决补刀 | `@Redirect` `Player.killedEntity(...)`：流程走完目标仍存活 → persistentData 打 `gtnecore_void_mark`（玩家除外） |
| 虚空诅咒执行 | `VoidMarkHandler`：LivingTickEvent 每 tick `hurt(fellOutOfWorld(), Float.MAX_VALUE)`；LivingDeathEvent 清标记 |
| 右键范围伤害 | `ToolUtilsMixin` 把 `aoeAttack` 的 `DamageSources.source(...)` 换成 `fellOutOfWorld()` |

设计说明：Re-Avaritia 自家 InfinityHandler 免疫非 INFINITY 伤害（含 void/kill）——**刻意保留**，
无尽套玩家只吃剑的固定伤害。

---

## 6. 能量立方（EU↔FE）

- `EnergyCubeMachine extends TieredEnergyMachine implements IFancyUIMachine`（LV..MAX 14 档）
- EU 存储（容量 = V[tier]×64，1A）+ 六面模式（NONE/EU_IN/EU_OUT/FE_IN/FE_OUT）
- EU↔FE 转换：**4 FE = 1 EU**（`GeneralConfig.forgeConversionRate`/`ic2ConversionRate` 可配），
  80% 效率（fe→eu ÷5，eu→fe ×16/5）
- 六面模型 overlay：机器渲染状态属性（EnumProperty ×6）+ multipart 模型 json
- 详细机制见记忆 `mekatech-dev-env`（IFancyUIMachine、方向配置器、NotifiableEnergyContainer 覆写等）

---

## 7. 测试配方（临时）

`TestVoltageRecipes.java`：AddPackFindersEvent（SERVER_DATA）→ `GTDynamicDataPack::addRecipe`，
注册 8 个组装机配方（EUt = 2³¹-1 → VA[15..21]，泥土→草方块，20t），用于验证电压突破。
**测完删除**（文件 + Gtnecore 里 `TestVoltageRecipes::register` 与 import 两处）。

---

## 8. 路线图（core mod 定位）

GTNEcore 的定位：**做 KJS 做不到的事 + 提供接口**（给整合包开发者）。版本背景：
原定 1.21.1（GTM 8.0），因 8.0 未出正式版且不稳定 → 降级 1.20.1（GTM 7.5.3）。

### 主线二：机械动力（Create）应力仓 —— ⚠️ 已暂停（2026-08-15）

曾实现到 GUI 阶段后放弃（代码/资产/依赖已清除）。**恢复要点**（实测）：
- 应力网络只认 `KineticBlockEntity`（`RotationPropagator`/`KineticNetwork` 全 KBE 签名）；
  `IRotate` 是 Block 级可连接接口；仓必须是 Create 方块（单继承，与 GTM 机器不可兼得）
- Create 6.0.8 把 Ponder/Registrate/flywheel jarjar 打包（编译期 Ponder 从 create jar 的
  `META-INF/jarjar/` 解出放 libs/）
- ldlib GUI：`BlockEntityUIFactory.INSTANCE.openUI(be, player)` + BE 实现 `IUIHolder`
  （`SceneWidget(x,y,w,h,level,true)` + `setRenderedCore` + `setOnSelected` 点面回调）

### 主线三：全套 KJS 接口

- 主线一/二的配方、机器、交互全部暴露给 KJS（`org.ringclouds.gtnecore.kjs`，
  参考 GTM 的 `GregTechKubeJSPlugin` 模式：GTRecipeComponents / 事件 / 绑定）
- dev 环境无 KJS——需要 kubejs-forge 2001.6.x 依赖（libs/ flatDir 备选）

### 分发（后续）

方案 B：jarjar 把 patched gtceu 内置进 GTNEcore（单 jar 核心，mods.toml 声明
`embedded=true`），整合包不装官方 gtceu——不急，先做框架。

## 9. 灵魂能量流体化（Goety 兼容）★进行中

> 详见《Goety-2.5.56.4-API指导文档》（同目录）。

- 已注册 `gtnecore:liquid_soul_energy`（FluidType 青绿 tint #4FE3C1 + ForgeFlowingFluid +
  方块 + 桶 + 标签 + 4 帧动画贴图），实现见 `soulenergy/SoulEnergyFluids.java`
- 踩坑：`FluidType.Properties` 无 color()（客户端扩展走 `getRenderPropertiesInternal`）；
  纹理必须提供（null → Jade 渲染 NPE）
- 依赖：Goety 2.5.56.4（curse 8647359）+ Curios 5.14.1（modmaven 无 pom，fg.deobf 直引）
- 待办：液态灵魂能量 ↔ 玩家 ISoulEnergy 对接（SEHelper.increase/decreaseSESouls），
  方案见 Goety 文档 §7（推荐先做"灵魂接口"方块）

## 10. GTCEu-fork 双轨架构（2026-08 起）

上游级修复在完整源码 fork 中进行：**`D:\create_ringclouds\GTCEu-fork`**（7.5.3，独立于本工程）。

- **职责边界**：fork = 上游修复与未来方向；本工程 `src/patch/` = **构建真源**
  （dev 的 `make-dev-patched.py` 合并、release 的 `make-release-jar.py` 打包均以
  patch 层产物为准）。**fork 的改动必须手动同步进 patch 层才会进游戏**
- fork 已合入 patch 层的修复（2026-08-27）：
  - 超净间幻影门（`CleanroomMachine.goetyApparitionDoor()`，'W' 墙体独立 6 块上限，
    Goety 未装时匹配空）
  - `PatternPreviewWidget` JEI 槽修复（候选槽 `IngredientIO.INPUT` + null 谓词防护）
  - `MultiblockInfoCategory` 越界防护（JEI 槽数 > widget 数）
- patch 版 `PatternPreviewWidget.updateScreen()` 省略了 fork 的 EMI/REI 屏幕分支
  （patch classpath 无 REI/EMI 依赖，同 AE2/TOP 裁剪惯例；整合包只用 JEI）——
  从 fork 同步该文件时注意保留此裁剪

## 11. 已知待办

- [ ] 主线二：Create 应力输入/输出仓
- [ ] 主线三：KJS 接口打包（依赖 dev 环境接入 kubejs）
- [x] 15+ 档机器/部件的**正式配方内容**（2026-08-28 已修复）：原版 PLATE/CABLE 只绑到
  9（10-21 走 fallback 铁板/红石合金="神秘材料"）；isHighTier 组件按原索引 12-14 绑定
  错位；机壳 id 拼接顺序反。现 10-21 全绑：电缆 10-16=铕 / 17-21=弦裸线（超导无
  cable 物品，按世代分流 wireGt*/cableGt* 前缀）、板 10-16=中子素 / 17-21=弦板、
  HULL_PLATE=PBI；UXV/OpV 部件物品补注册（gtceu 无原生部件物品）。
  配方错误 82→0（仅剩火箭采矿 16 条 crushed 旧账）
- [ ] 15+ 档电缆/线缆（WireProperties 按材料注册，确认覆盖）——上条的 CABLE 组件依赖此项
- [ ] 太空采矿配方修正（低优先）：`data/gtnecore/recipes/rocket_launch/*.json` 引用
  `gtceu:<mat>_crushed`（不存在），正确格式 `gtceu:crushed_<矿物>_ore`；需进游戏按
  星图元素核对目标矿物后逐个替换（或重跑 datagen）
- [ ] `TestVoltageRecipes` 删除
- [ ] 分发方案 B（jarjar 内置 patched gtceu）
