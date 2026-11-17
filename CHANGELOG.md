# GTNEcore 更新日志

条目前缀：**【增量】** = 与上一版兼容的增强/新增；**【破坏性】** = 与上一版不兼容
（存档、配方、物品/流体 id 或行为变化）。

---

## v0.0.1（2026-08-20 基线快照）

首个公开版本（对应仓库初始提交内容，Forge 1.20.1 / GTCEu 7.5.3 / Java 17）。

- **22 档电压体系**：ULV..MAX（0-21 档），经 `patchJar` 字节码层突破 GTCEu int 上限
  （21 亿 EU/t）
- **高电压部件补全**：CYV+ 机壳方块、UMV+ 电动马达等部件资源与注册
- **能量立方**：EU↔FE 双向转换（4 EU = 1 FE，80% 效率），六面方向"方向设置"页可视化配置
- **自定义元素与材料**：Godium / Strium / Timeium / Infinite / Soulium / Soul / Unknown 等，
  幽匿（Sculk）成分体系重定义与化学式补全
- **无尽系列强化**：波浪文字 / 双层文字物品名渲染（tag 驱动，配置可调）
- **液态灵魂能量流体**：Goety 灵魂能量的流体化载体 `gtnecore:liquid_soul_energy`
  （注册 + 动画纹理；对接逻辑未完成——见 v0.0.2 破坏性说明）
- **LGPL 合规**：GTCEu 修改源码按 LGPL-3.0 §4 陈列于 `GTM - Edit Display/` 目录

---

## v0.0.2（2026-09-05）

> 本版含 1 项破坏性变更，升级前请确认无依赖该内容的存档。

### 【破坏性】

- **移除液态灵魂能量流体**（`gtnecore:liquid_soul_energy` 及其 forge 流体 tag、桶物品、
  流体资产与语言键）：该桥接的对接逻辑从未完成，功能有意下线。0.0.1 中存有该流体
  的存量机器/流体槽在升级后不再识别；Soul / Soulium 材料体系本体保留不受影响。

### 【增量】

- **火箭发射台 · 星际采矿（发射台产出体系重构）**
  - 6 元素星图（Al / Au / Cu / Fe / Ni / Sn）采矿任务：产出从"元素粉/碎屑"重构为
    **该元素矿石的原矿组（raw）**，"一矿一电路"（档位数 = 矿石种类数）：
    Cu 5 档 / Fe 6 档 / Al 6 档(HV) / Ni 3 档 / Sn 3 档 / Au 单档(HV)
  - 修复发射台配方不吃超频（modifier 链替换了默认 OC，已组链恢复）
  - 配套：MK1 火箭、天体星图系列、星象编程仪（天文观测站环境编程星图）、空白星图组装
- **勘矿火箭 glTF 动态渲染**（GTCEu DynamicRender 体系）
  - 运行时解析 VoxelBridge 导出的 glTF 火箭模型（零依赖、自动包围盒对齐）
  - 运动学：待机停靠台顶 → 点火慢爬急升（顶点 ~110 格）→ 硬隐藏巡航 → 进度 90%
    高空重现快降、触底减速、配方完成瞬间精确落台；无淡入淡出（布尔硬显隐）
  - 引擎视野震动（升空/降落窗口，距离衰减、多台独立）、几何自发光尾焰锥
  - 相关修复：DynamicRender 实例跨多台串台（状态按机器隔离）、vanilla 实体
    RenderType QUADS/256 缓冲与三角形流错配（自定义 TRIANGLES 渲染类型）
- **反曲率-重力引擎（终版）**
  - 环境判定统一为 cleanroom 绑定（WEIGHTLESS / SPATIAL_DISTORTION，与发射台同管线）
  - 能量输出改为电压等级语义（失重 = LuV 96A / 黑洞 + UHV 仓 = UHV 96A，满仓自停）
  - 时空核心与聚变环流动态渲染、avaritia 钻石晶格成型修复、JEI 多方块预览
- **EU Ad Astra 适配机**
  - 氧分配器：供氧 + 恒温 22°C 双标记（原版字节码对齐），正面朝向判定修复
  - 重力正常化器：重力剥夺/恢复与轨道环境联动；PlanetHandler 逐格氧机制适配
- **轨道环境绑定**：玩家 32 格内机器每 100 tick 绑定/剥夺失重环境
- **GT之环（Halo）**：存档记录 / 成就 / 佩戴三通道解锁能力体系，回城/传送按键网络，
  环渲染与 Curios 接入
- **配方规划器**：节点式 GUI 配方规划（画布建节点、连线、电量汇总），规划器 JEI 集成
- **线程机制（阶段 1，内部预留未公开）**：三品位线程仓 ×27 + 线程熔炉，
  已从创造页 / JEI / 多方块预览隐藏
- **GTCEu patch 层修复与扩展**
  - 机器动态渲染器体系（模型 JSON `dynamic_renders` 挂载，聚变环同管线）
  - 官方部件机器成型 IS_FORMED 外观切换（机壳显示，GTM 1.12 风格）
  - PatternPreviewWidget 空谓词 NPE、挖机器粒子 NPE、清洁室组件绑定等修复
- **材料与资产整理**：统合物质分组与创造页 tab 归类、幽匿体系维持原生（SOUL/UNKNOWN
  材料保留）、批量 lang 全语言覆盖

---

## 0.1.0-Pre-alpha（2026-11-15）

> 首个 Pre-alpha 版本，整合 v0.0.2（2026-09-05）之后至 2026-11-15 的全部工作。
> 本版含多项破坏性变更，升级前请确认存档中无相关物品/流体。

### 【破坏性】

- **移除 Goety 联动**：依赖、3 个 mixin、幻影门与液态灵魂能量桥接整体下线；
  README 描述同步校正（模型结构参考的 MIT 署名仍保留）。
- **移除自建无尽材料体系**：不再注册 `gtnecore:infinity` /
  `gtnecore:crystal_matrix` / `gtnecore:matrix` 三个 GT Material 与
  `If`（Infinite）/ `Mx`（Matrix）两个虚构元素；相关材料归类 tag、
  `setIgnored` 绑定、自检、诊断 mixin、物品模型与语言键一并删除。
  无尽材料改由整合包中的 AvaritiaIntegration 等 mod 提供。
  **旧存档中上述物品/流体不再识别**；JEI 书签里残留的旧条目会打 WARN，
  手动清除书签即可。
- **依赖来源变更**：编译/运行依赖统一走 maven / cursemaven，仅 Registrate
  与三个 jarJar 内嵌产物保留 `libs/` flatDir。JEI 固定 `15.21.0.148`
  + `jei-copy-recipe` fileId `7364314`——升到 15.74 会与 GTCEu 的
  `jei.FluidHelperMixin` 注入描述符失配，导致加载阶段崩溃。
- **公开仓库内容收紧**：`docs/` 外置上下文档案、`ailog/` 会话记录、
  `.workbuddy/` 开发记录不再随公开仓库分发（本地保留）；CHANGELOG 与两个
  LGPL 修改源码展示目录收编进主仓库。

### 【增量】

- **发布包内嵌第三个 mod**：jarJar 加入 gregic_tinkering 特供补丁版
  （`META-INF/jarjar/gregic-release-1.3.0.jar`），并补齐 LGPL 合规声明与
  「勿再自装官方 GTCEu / GTMThings / gregic_tinkering」提示。
- **修复整合包启动崩溃**：发布 jar 的 MANIFEST 声明
  `MixinConfigs: gtnecore.mixins.json`。此前 mixin 配置未被加载 →
  `GTValuesMixin` 不执行 → 22 档电压表未安装 → 特供 GTCEu 的
  `GTMachineModels` 初始化时 `ArrayIndexOutOfBoundsException:
  Index 21 out of bounds for length 15`。dev 环境不受影响。
- **双许可**：代码部分 LGPL-3.0（`LICENSE`）；本仓库自创的非代码资产
  （贴图、模型、图形、界面素材等）以 CC BY-NC-SA 传播；第三方项目
  仍各按原许可证。
- **README 更迭**：去除未逐一核实的上游链接、移除已下线的 Goety 描述与
  不存在的 `build.gradle.github` 双版本说明、按现有源码包重列目录结构，
  并补上 Gradle 自身需 JDK 21 与 dev / 发布两条链需分别执行的说明。

### 【已知限制】

- 升级到本版需**替换**整合包中同名 `-all.jar`，启动日志应出现
  `GTNEcore: installed 22-tier voltage table (TIER_COUNT=22)`。
- JEI 升压预览自 0.0.2 起失效；诊断确认升压调用本身成功（tier 6→7），
  问题在显示层/事件层，尚未修复。
- 构建仍有 40 条编译警告（多为 `[removal]` 过时 API 与 mixin `@Shadow`
  映射提示），其中原始水泵结构与波浪文字渲染待实机验证。

---

### 备注

- 本仓库仅发布源码；运行需自备 GTCEu 7.5.3 及整合包运行时依赖（见 README 依赖说明）。
- docs/ 与本地开发记录（ailog 等）不随仓库发布。
