# GTNEcore

**MekaTech 整合包的核心扩展 mod**（Forge 1.20.1 / Java 17）

GTNEcore 扩展 GTCEu（GregTech Modern）电压体系到 22 档、突破 21 亿 EU/t 上限，
强化 Re-Avaritia 无尽剑，并提供 EU↔FE 能量转换与 GT 附属 mod 的二进制兼容支持。

> ⚠️ **基于 GTCEu 7.5.3 的修改作品（LGPL-3.0）**：本仓库包含 GTCEu 的全部修改源码
> ——`src/patch/`（字节码修改层，经 `patchJar` 合并进 gtceu-patched.jar）、
> `src/main/` 的 mixin 注入（GTValues/GTMachineUtils）与 gtceu 命名空间资产。
> 修改内容为 GTCEu 电压体系扩展（22 档 / 21 亿 EU/t 突破）、高电压机器与部件补全等。
> GTCEu 版权归 GregTechCEu 团队所有。本 mod 与 GTCEu 均以 **LGPL-3.0** 授权（见 `LICENSE`）。

---

## 功能

- **22 档电压体系**：ULV..MAX（0-21 档），突破 GTCEu 的 int 上限（21 亿 EU/t），
  通过 `patchJar` 机制修改 GTCEu 字节码实现
- **高电压机器补全**：CYV+ 机壳方块、UMV+ 电动马达等部件（gtceu 命名空间注册）
- **能量立方**：EU↔FE 双向转换，GTM "方向设置"页可视化配置
- **无尽剑强化**：无视无敌帧 + 虚空诅咒标记 + 右键虚空处决
- **附属 mod 兼容**：GTMThings / gregic_tinkering 的二进制兼容补丁随发布 jar 内嵌，
  把 `int[]` 电压字段访问改写为 `long[]`（工具源码见 `tools/PatchGtseJar.java`）

## 构建

编译目标 Java 17；**Gradle 自身需 JDK 21**——`gradle.properties` 的
`org.gradle.java.home` 已固定该路径（PATH 上的 Java 25 会让 Gradle 8.8 启动失败）。
wrapper 自带 Gradle 8.8。

```bash
./gradlew.bat patchJar        # 编译 GTCEu patch 层 → build/patched/gtceu-patch.jar
./gradlew.bat compileJava     # 编译主工程
./gradlew.bat runClient       # 启动 dev 客户端
```

`patchJar` 把 `src/patch/` 的修改合并进 GTCEu 的 `gtceu-1.20.1-7.5.3-patched.jar`
（完整链路见 `build.gradle` 的 `patchJar` / `makeReleaseGtceuJar` / `updateReleaseGtceuJar` 任务）。

## 依赖

| 依赖 | 版本 | 说明 |
|---|---|---|
| GTCEu | 7.5.3（**patch 版**） | 本 mod 修改的基底 |
| Re-Avaritia | 1.4.1 | 无尽剑强化目标 |
| Curios API | 5.14.1+1.20.1 | GT之环佩戴判定 |
| Applied Energistics 2 | 15.4.2+ | GTMThings 补丁版强制前置 |
| GTMThings | 1.6.0（特供补丁版） | 二进制兼容补丁，随发布 jar 内嵌 |
| gregic_tinkering | 1.3.0（特供补丁版） | 二进制兼容补丁，随发布 jar 内嵌 |
| JEI / Jade | — | 配方查看 / 方块信息 |

> ⚠️ 发布 jar（`gtnecore-0.1.0-Pre-alpha-all.jar`）已用 jarJar 内嵌**特供 GTCEu、补丁版 GTMThings、
> 补丁版 gregic_tinkering**：请勿再自行安装这三个 mod 的官方版本，重复供给会触发版本冲突检测、
> 游戏无法启动。

### 第三方作品署名与许可证（Acknowledgements）

GTNEcore 在开发与运行中依赖、参考或修改了以下开源项目。各项目版权归其
作者所有，按各自许可证分发（随整合包/运行时分发时请同样保留其许可声明）：

**直接修改 / 深度集成（许可证条款直接适用）**

| 项目 | 作者 | 许可证 |
|---|---|---|
| GregTech CEu Modern | GregTechCEu 团队 | LGPL-3.0（本 mod 的 patch/mixin 修改层基于其源码与字节码，同样以 LGPL-3.0 提供修改版源码） |
| Registrate | tterrag 等 | MIT |
| LDLib | LowDragMC | LGPL-3.0 |

**运行时依赖 / API 使用（按其许可证分发）**

| 项目 | 作者 | 许可证 |
|---|---|---|
| Create | simibubi 与 Create 团队 | MIT |
| Flywheel | Jozufozu | MIT |
| Ponder | Create 团队 | MIT |
| Applied Energistics 2 | TeamAppliedEnergistics | 见其仓库（MIT 系条款，详见上游 LICENSE） |
| Ad Astra | Alex Nijjar 与 Terrarium 团队 | Terrarium License（MIT 系） |
| Botarium | CodexAdrian | MIT |
| Resourceful Lib / Resourceful Config | ThatGravyBoat, Epic_Oreo | MIT |
| Tinkers' Construct | Slime Knights | MIT |
| Mantle | Slime Knights | MIT |
| Goety（仅模型结构参考；运行时依赖已于 2026-10-31 移除） | Polarice3 | MIT（署名义务不因此失效，见 `THIRD-PARTY-NOTICES.md`） |
| Re-Avaritia / Avaritia 系 | cnlimiter, cu6, MHanHanBing, IAFEnvoy, Asek3, MikhailTapio 等（Re 版）；SpitefulFox（原版 Avaritia） | MIT（Re 版）/ 原版见其仓库 |
| Curios | TheIllusiveC4 | LGPL-3.0-or-later（仓库亦标注 MIT，以随附 LICENSE 为准） |
| JEI (Just Enough Items) | mezz | MIT |
| Jade | Snownee | CC BY-NC-SA 4.0 |
| KubeJS | LatvianModder | LGPL-3.0 |
| Rhino | latvian.dev, Mozilla（基于 Mozilla Rhino） | MPL-2.0 |
| Architectury API | shedaniel | LGPL-3.0 |
| GTm Things | liansishen | LGPL-3.0。**二进制兼容补丁**：其 1.6.0 编译目标 `GTValues.VA/VH/VHA` 为 `int[]`，与特供 GTCEu 的 `long[]` 不兼容（进存档 `NoSuchFieldError: VA`）；由 `tools/PatchGtseJar.java`（ASM，仓库内开源）自动改写为 `long[]` 访问，产物 `build/patched/gtmthings-1.6.0[-dev]-patched.jar`；发布 jar 以 jarjar 内嵌 SRG 补丁版（`META-INF/jarjar/gtmt-release-*.jar`），玩家勿再自装官方版。补丁仅做类型适配、无功能修改，补丁工具源码即修改之对应源（LGPL-3.0 合规） |
| Gregic Tinkering | 见上游仓库 | LGPL-3.0（见分发 jar 的 `mods.toml`）。**二进制兼容补丁**：与 GTMThings 同款，其 1.3.0 按 `int[]` 引用 `GTValues.VA/VH/VHA`，由 `tools/PatchGtseJar.java`（ASM，仓库内开源）改写为 `long[]` 访问；特供版源存于 `特供版/gregic_tinkering-1.3.0-patched.jar`，经 `./gradlew updateReleaseGregicJar` 同步为 `libs/gregic-release-1.3.0.jar` 后以 jarJar 内嵌（`META-INF/jarjar/gregic-release-1.3.0.jar`），玩家勿再自装官方版。补丁仅做类型适配、无功能修改，补丁工具源码即修改之对应源（LGPL-3.0 合规） |
| Modern UI | BloCamLimb | LGPL-3.0-or-later |
| Shimmer | KilaBash, zomb_676 | MIT |
| GeckoLib | Gecko, Eliot, AzureDoom 等 | MIT |
| Patchouli | Vazkii | CC BY-NC-SA 3.0 |
| Ars Nouveau | Bailey Hollingsworth | LGPL-3.0 |
| GuideME | shartte | 见其仓库（MIT 系） |
> 我需要声明的是我本人对于mod开发的流程并不熟悉，特别是大量使用别人的代码。这里的署名由AI帮我代工因为我可能写的格式不对，如果这里标注的任何信息出现了谬误或者冒犯了您，我向你道歉，在任何人指出后我会立刻更正。

**开发环境工具 mod（不随发布分发）**

| 项目 | 作者 | 许可证 |
|---|---|---|
| Oculus | NanoLive, dima_dencep, coderbot | LGPL-3.0-only |
| Embeddium | embeddedt | LGPL-3.0 |
| ModernFix | embeddedt | LGPL-3.0-or-later |

Minecraft 本体与 Minecraft Forge 归 Mojang AB 与 Forge 团队所有，
依其 EULA 与 LGPL-2.1 分发。

> 以上信息从各 mod 分发 jar 的 `mods.toml` 元数据整理；上游仓库地址未逐一核实，
> 故此处只保留项目名、不放链接。若作者/许可证信息有出入，以**上游最新声明**为准；欢迎提 issue 更正。

### 依赖来源与发布链

- 编译 / 运行依赖统一走 maven 仓库；仅 `Registrate` 与三个 jarJar 内嵌产物
  （特供 GTCEu、补丁版 GTMThings、补丁版 gregic_tinkering）保留在 `libs/` 走 `flatDir`。
- 改动 `src/patch/` 后有**两条**独立链路，漏跑任一条都会出现「改了没生效」：
  dev 链 `./gradlew patchJar` + `scripts/make-dev-patched.py`；发布链
  `./gradlew updateReleaseGtceuJar` / `updateReleaseGtmtJar` / `updateReleaseGregicJar`
  之后再 `./gradlew build`（发布链各步需分开执行）。

## 目录结构

```
src/
├── main/java/org/ringclouds/gtnecore/
│   ├── addon/          # GTCEu addon 入口（@GTAddon）
│   ├── block/ item/ machine/   # 高电压方块/部件/能量立方
│   ├── client/ halo/   # 客户端渲染、GT之环
│   ├── cover/ handler/ integration/ util/   # 覆盖板、事件、联动、工具
│   ├── material/ recipe/ planner/ config/  # 材料、配方、规划器、配置
│   ├── mixin/          # GTCEu / Re-Avaritia 注入
│   └── voltages/       # 22 档电压表
├── patch/java/         # ★ GTCEu 修改层（LGPL 修改版源码）
└── main/resources/     # 资产（blockstate/lang/模型/贴图）
```

## 许可

[LGPL-3.0](LICENSE) —— 与 GTCEu 一致。基于 GTCEu 的修改（`src/patch/`）按
LGPL-3.0 §4 提供源代码；使用/分发本 mod 请保留版权声明与许可证文本。

本 mod 使用了 AI 技术创作，故很难保证兼容性。
