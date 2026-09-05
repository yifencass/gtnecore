# GTNEcore
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Mod Loader](https://img.shields.io/badge/Loader-Forge-orange)

**MekaTech / GregTech-NewEra 整合包的核心扩展 mod**（Forge 1.20.1 / Java 17）

GTNEcore 扩展 GTCEu（GregTech Modern）电压体系到 **22 档**、突破 21 亿 EU/t 上限，
强化 Re-Avaritia 无尽系列装备（波浪/双层文字渲染等），并兼容 Goety 灵魂体系材料。

> ⚠️ **基于 GTCEu 7.5.3 的修改作品（LGPL-3.0）**：对 GTCEu 的全部修改源码（`src/patch/`
> 字节码修改层、`src/main/` 中锚定 GTCEu 的 mixin 与材料/组件修改）按 LGPL-3.0 §4 提供，
> 并额外整理在 `GTM - Edit Display/` 目录；GTCEu 版权归 GregTechCEu 团队所有。
> 本 mod 与 GTCEu 均以 **LGPL-3.0** 授权（见 `LICENSE`）。

---

## 功能

- **22 档电压体系**：ULV..MAX（0-21 档），突破 GTCEu 的 int 上限（21 亿 EU/t），
  通过 `patchJar` 机制修改 GTCEu 字节码实现
- **高电压机器补全**：CYV+ 机壳方块、UMV+ 电动马达等部件
- **能量立方**：EU↔FE 双向转换，GTM "方向设置"页可视化配置
- **自定义元素与材料**：Godium / Strium / Timeium / Infinite / Soulium / Magic / Soul /
  Unknown 等元素与材料，含幽匿（Sculk）成分体系重定义
- **无尽系列强化**：波浪文字 / 双层文字物品名渲染（tag 驱动，配置可调）

## 构建

要求：JDK 17、Gradle 8.x（wrapper 自带）

```bash
./gradlew.bat patchJar        # 编译 GTCEu patch 层 → build/patched/gtceu-patch.jar
./gradlew.bat compileJava     # 编译主工程
./gradlew.bat runClient       # 启动 dev 客户端
```

`patchJar` 把 `src/patch/` 的修改合并进 GTCEu 的 patched jar
（构建流程见 `docs/GTNEcore-开发文档.md` §4，docs 不随本仓库发布，可联系作者）。

## 依赖

| 依赖 | 版本 | 说明 |
|---|---|---|
| GTCEu | 7.5.3（patch 版） | 本 mod 修改的基底（见 `GTM - Edit Display/`） |
| Re-Avaritia | 1.4.1+ | 无尽系列强化目标 |
| Goety | 2.5.56.4（+Curios 5.14.1） | 灵魂能量体系 |
| JEI / Jade | — | 配方查看 / 方块信息 |

## 目录结构

```
src/
├── main/java/org/ringclouds/gtnecore/
│   ├── addon/          # GTCEu addon 入口（@GTAddon）
│   ├── block/ item/ machine/   # 高电压方块/部件/能量立方
│   ├── client/         # 波浪/双层文字渲染状态与配置
│   ├── material/       # 自定义材料（Timeium/Infinity/Magic/Soul/Unknown 等）
│   ├── mixin/          # GTCEu / Minecraft 注入
│   ├── recipe/         # 22 档电路/部件组件重绑
│   └── voltages/       # 22 档电压表
├── patch/java/         # ★ GTCEu 修改层源码（LGPL，另见 GTM - Edit Display/）
└── main/resources/     # 资产（blockstate/lang/模型/贴图）
```

## 协议说明

- **GTM - Edit Display/**：GTCEu 修改代码（`src/patch/` + 锚定 GTCEu 的 mixin /
  材料与组件修改）——LGPL-3.0，与 GTCEu 一致
- **gregic_tinkering - Edit Display/**：对 gregic_tinkering（开源，LGPL-3.0）的
  二进制强兼容工具（`PatchGtseJar.java`）——因该 mod 编译期按 `int[]` 引用
  `GTValues.VA/VH/VHA`，与本项目 long[] 改造后字段描述符不兼容（`[I`→`[J`），
  以 ASM 改写（`getstatic`→`[J`、`iaload`→`laload`、删 `i2l`、`i2d`→`l2d`）
- 其余代码：LGPL-3.0（见 `LICENSE`）
[LGPL-3.0](LICENSE)

本 mod 使用了 AI 技术创作，故很难保证兼容性。
Since the main creators of the modpack are Chinese, the focus will be on the Chinese community until GTNE is completely finished, so I want to say sorry to my foreign friends.
