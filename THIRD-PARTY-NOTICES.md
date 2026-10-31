# 第三方代码署名 / Third-Party Notices

本仓库（GTNEcore）自身以 **GNU LGPL 3.0** 发布。下列部分包含来自第三方项目的代码或资产，
按其原始许可证使用，并在此保留署名。

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
- 移除记录与源码存档：`docs/废案/Goety联动-废案①.md`、`docs/废案/Goety联动-废案①-源码/`。
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

以下项目以**依赖形式**（非源码复制）使用，各自遵循其原许可证，本仓库不分发其代码：

| 项目 | 用途 | 许可证（以其仓库为准） |
|---|---|---|
| GregTech CEu Modern 7.5.3（特供/patched 分叉） | 核心前置 | LGPL 3.0 |
| Re-Avaritia | 无尽装备强化 | 见上游 |
| Curios API | GT之环佩戴判定 | LGPL 3.0 / 见上游 |
| Applied Energistics 2 | 样板携带虚拟电路号 | LGPL 3.0 |
| PneumaticCraft: Repressurized | 仅用 `me.desht.pneumaticcraft.api.*`（LGPLv3），compileOnly，不 jarJar | GPLv3（API 部分 LGPLv3） |
