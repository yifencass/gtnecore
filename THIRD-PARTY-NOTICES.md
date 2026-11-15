# 第三方代码署名 / Third-Party Notices

本仓库（GTNEcore）**代码部分**以 GNU LGPL 3.0 发布（见 `LICENSE`）；
本仓库自行创作的图形、模型等非代码资产以 **CC BY-NC-SA** 协议传播。
下列条目包含来自第三方项目的代码或资产，按其原始许可证使用，并在此保留署名。

---

## Goety (Minecraft mod)

- **上游项目**：Goety
- **作者**：Polarice3
- **许可证**：MIT License
- **上游地址**：https://www.curseforge.com/minecraft/mc-mods/goety

### 本仓库使用范围

仅 **`UnholyHatModel` 的模型结构与 halo 渲染方式**（不含其运行时代码、不含其任何类引用）：

| 本仓库文件 | 使用内容 |
|---|---|
| `src/main/java/org/ringclouds/gtnecore/client/render/GtneHaloModel.java` | `HumanoidModel` 子类骨架；head 为空壳；`halo` 子部件挂载于 `(0, -13, 5)` 并绕 x 旋转 45°；`halo_1` 为 16×16 薄片（cube），贴「G」图标，缓慢 z 轴旋转 |
| `src/main/java/org/ringclouds/gtnecore/client/render/GtneHaloRenderer.java` | 上述模型的渲染调用方式 |

对应文件内的类注释同样标注了「结构照搬 Goety 的 UnholyHatModel（Polarice3，MIT License）」。

### 说明

- 2026-10-31 起，本工程**不再依赖 Goety mod**：`build.gradle` 的 Goety 依赖、3 个针对 Goety 类的
  mixin、Goety 幻影门墙体谓词、Goety overlay 守卫均已移除。
- 移除记录与相关源码存档保留在本地开发档案中，不随本仓库分发。
- **本署名与许可证声明不因依赖移除而失效**：只要上述两个渲染文件仍留在仓库中，MIT 署名即须保留。

### MIT License 要点（全文见上游仓库）

> Permission is hereby granted, free of charge, to any person obtaining a copy of this software
> and associated documentation files (the "Software"), to deal in the Software without restriction,
> including without limitation the rights to use, copy, modify, merge, publish, distribute,
> sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or
> substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
> BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
> NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
> DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Copyright (c) Polarice3 — Goety

---

## 其他依赖

以下项目以**依赖形式**（非源码复制）使用，各自遵循其原许可证；本仓库不分发其源码，
发布 jar 中以 jarJar 内嵌二进制的三个 mod 见下节：

| 项目 | 用途 | 许可证（以其仓库为准） |
|---|---|---|
| GregTech CEu Modern 7.5.3（特供/patched 分叉） | 核心前置 | LGPL 3.0 |
| Re-Avaritia | 无尽装备强化 | 见上游 |
| Curios API | GT之环佩戴判定 | LGPL 3.0 / 见上游 |
| Applied Energistics 2 | 样板携带虚拟电路号 | LGPL 3.0 |
| PneumaticCraft: Repressurized | 仅用 `me.desht.pneumaticcraft.api.*`（LGPLv3），compileOnly，不 jarJar | GPLv3（API 部分 LGPLv3） |
| GTMThings 1.6.0（patched 版） | 随发布 jar 以 jarJar 内嵌 | LGPL 3.0 |
| Gregic Tinkering 1.3.0（特供/patched 版） | 随发布 jar 以 jarJar 内嵌 | LGPL 3.0（见分发 jar 的 `mods.toml`） |

### jarJar 内嵌的第三方 mod（二进制分发）

发布 jar `gtnecore-0.1.0-Pre-alpha-all.jar` 的 `META-INF/jarjar/` 内嵌三个 mod 的二进制：
特供 GTCEu 7.5.3、补丁版 GTMThings 1.6.0、补丁版 Gregic Tinkering 1.3.0。
后两者是**二进制兼容补丁**：因按 `int[]` 引用 `GTValues.VA/VH/VHA`，与特供 GTCEu 的
`long[]` 不兼容（进存档会 `NoSuchFieldError: VA`），由 `tools/PatchGtseJar.java`
（ASM，仓库内开源）改写为 `long[]` 访问；补丁仅做类型适配、无功能修改，
补丁工具源码即修改之对应源（LGPL-3.0 合规）。
Gregic Tinkering 的特供版源存放于 `特供版/gregic_tinkering-1.3.0-patched.jar`，
由 `./gradlew updateReleaseGregicJar` 同步为 `libs/gregic-release-1.3.0.jar` 后内嵌。

**玩家请勿再自行安装这三个 mod 的官方版本**——重复供给会触发版本冲突检测、游戏无法启动。
