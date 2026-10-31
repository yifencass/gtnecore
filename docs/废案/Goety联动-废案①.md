# Goety 联动 - 废案①

> 移除日期：2026-10-31
> 原因：整合包不再引入 Goety（`curse.maven:goety-586095`），本工程对它的全部代码耦合一并删除。
> 保留项：GT之环（Halo）本身，以及它对 Goety `UnholyHatModel` 的 MIT 署名（见仓库根 `THIRD-PARTY-NOTICES.md`）。

---

## 一、移除清单

### 1. 构建依赖

`build.gradle` 原第 522–523 行：

```groovy
//Goety（依赖 Curios）
implementation fg.deobf("curse.maven:goety-586095:8647359")
```

两行均已删除。**Curios 保留**（`top.theillusivec4.curios:curios-forge:5.14.1+1.20.1`，compileOnly + runtimeOnly）——
它是 GT之环 `HaloAbilities.isWearingHalo()` 的佩戴判定通道，与 Goety 无关。

### 2. 三个 mixin（纯 Goety 类，且都是「戴环给 Goety 法术加成」）

| 文件 | 目标 | 行为 |
|---|---|---|
| `GoetyCastSpeedMixin.java` | `com.Polarice3.Goety.common.items.magic.DarkWand` | `@Redirect setSpellConditions` 里的 `ISpell.castDuration(...)`，戴环时除以 256（最少 1），施法整体压缩 256 倍 |
| `GoetyFocusCooldownMixin.java` | `com.Polarice3.Goety.common.capabilities.soulenergy.FocusCooldown` | `@Inject tick HEAD cancellable`，戴环时清空 `cooldowns` / `cooldownsSpecific` 两张表并跳过递减 |
| `GoetySoulCostMixin.java` | `com.Polarice3.Goety.utils.SEHelper` | `@ModifyVariable decreaseSouls`，戴环时消耗 ×0.000001，且 ≤0 的消耗归零（不扣灵魂、语义仍算成功） |

三者都带 `remap = false`（Goety 是 mod 类，无 SRG 映射），且 `HaloAbilities.isWearingHalo(player)` 为唯一开关。
源码存档：`docs/废案/Goety联动-废案①-源码/`。

同时从 `src/main/resources/gtnecore.mixins.json` 的 `mixins` 数组删除对应三行
（数组长度 26 → 23）。

### 3. `ForgeGuiOverlayGuardMixin`（客户端）

`@Mixin(ForgeGui.class)`，三条 `@Redirect` 包住 `lambda$render$0` 里的
`ForgeGui.pre` / `IGuiOverlay.render` / `ForgeGui.post`，**只对 Goety 命名空间**
（`NamedGuiOverlay.id().getNamespace() == "goety"` 或 overlay 类名以 `com.Polarice3.Goety` 开头）
做 try-catch 吞异常。起因是 Goety overlay 在「刚进入世界」那一瞬渲染 NPE，
Forge 只 log 不终止 → 每帧刷屏数百 MB 日志 → 渲染线程拖死（表现为卡在加入世界）。

Goety 移除后它是纯死代码，一并删除；`gtnecore.mixins.json` 的 `client` 数组 16 → 15。

### 4. 超净间 / 天文研究室的「幻影门」墙体

`src/patch/java/common/machine/multiblock/electric/CleanroomMachine.java`：

- 删除 `'W'`（墙体）谓词里的 `.or(goetyApparitionDoor().setMaxGlobalLimited(6))`
- 删除整个方法：
  ```java
  protected static TraceabilityPredicate goetyApparitionDoor() {
      Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("goety", "apparition_door"));
      if (block == null || block == Blocks.AIR) return Predicates.blocks(new Block[0]);
      return Predicates.blocks(block);
  }
  ```
  （它本来就是「Goety 未装时匹配空」的安全写法，移除后行为完全等价）
- 顺带清掉因此不再使用的 `import net.minecraft.resources.ResourceLocation;`
  与 `import net.minecraftforge.registries.ForgeRegistries;`

`src/main/java/org/ringclouds/gtnecore/machine/AstronomicalObservatoryMachine.java`：

- 删除 `'C'`（玻璃墙）谓词里的 `.or(goetyApparitionDoor().setMaxGlobalLimited(7))`

方块 id 备查：`goety:apparition_door`（原用途是当塑料混凝土墙的廉价替代，上限 6/7 块）。

### 5. 注释里的 Goety 引用

`src/main/java/org/ringclouds/gtnecore/mixin/GTCommonProxyMixin.java` 原注释
「按官方名锚定（参考 Goety mixin 先例）」→ 去掉括号里的 Goety 引用（先例本身已不存在）。

### 6. 文档

- `docs/Goety-2.5.56.4-API指导文档.md` → 移入 `docs/废案/`（245 行，含 SEHelper / FocusCooldown /
  灵魂能量 / 饰品渲染的调研，将来要重接直接看这份）
- `docs/GTNEcore-开发文档.md`：头部引用、§1 依赖表、§9「灵魂能量流体化」整节
  （原标 ★进行中，实际代码从未落地——`soulenergy/` 包并不存在）、§10 fork 修复清单里的幻影门条目
- `docs/超净间机制与天文研究室设计.md` §1.2 墙体谓词说明

---

## 二、保留项：GT之环的 MIT 署名

GT之环的模型与渲染照搬自 Goety 的 `UnholyHatModel`（作者 Polarice3，MIT License），
**这部分代码保留**，署名保留在三处：

1. `src/main/java/org/ringclouds/gtnecore/client/render/GtneHaloModel.java:16` 类注释
2. `src/main/java/org/ringclouds/gtnecore/client/render/GtneHaloRenderer.java:21` 类注释
3. 仓库根 `THIRD-PARTY-NOTICES.md`（集中声明，含 MIT 全文要点与派生范围）

照搬的具体内容：`HumanoidModel` 子类、head 空壳、halo 子部件挂在 `(0,-13,5)`、
x 旋转 45°、`halo_1` 为 16×16 薄片（cube + G 图标贴图）、缓慢 z 旋转。

---

## 三、若要重新接入 Goety

按上面清单倒序执行即可，注意四点：

1. `build.gradle` 的 `curse.maven` 坐标需要换成当时的文件 id（原 `8647359` 对应 Goety 2.5.56.4）。
2. 三个 mixin 全部 `remap = false`，且 `defaultRequire: 1` 会让**方法签名一变就启动崩溃**——
   升级 Goety 后先核对 `DarkWand.setSpellConditions` / `FocusCooldown.tick` /
   `SEHelper.decreaseSouls` 是否还在、参数是否一致。
3. `goetyApparitionDoor()` 用的是运行时 `ForgeRegistries.BLOCKS.getValue` 惰性解析，
   不要改成注册期常量——方块可能还没注册。
4. `CleanroomMachine.java` 在 **patch 层**（`src/patch/`），改动后要重建补丁链：
   `gradlew patchJar` → `python scripts/make-dev-patched.py <deobf jar> build/patched/gtceu-patch.jar build/patched/gtceu-1.20.1-7.5.3-patched.jar`，
   release 还要 `gradlew updateReleaseGtceuJar` 后再单独 `gradlew build`。
