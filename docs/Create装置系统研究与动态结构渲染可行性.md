# Create 装置（Contraption）系统研究与动态结构渲染可行性

> 2026-08-27 · GTNEcore 动态结构前置研究（火箭发射动画/巨构视觉）。
> 对象：Create 6.0.8（curse 328085-7178761，dev 环境 deobf jar 反编译）。

---

## 一、"实体方块"的本质：快照 + 实体宿主 + 假世界渲染

Create 的移动结构（轴承、活塞、车厢）不是"方块真的在动"，而是三层数据替换：

```
装配期（服务端）                     运行/渲染期（客户端）
─────────────────                   ─────────────────
assemble() BFS 搜索结构              AbstractContraptionEntity 携带 Contraption 移动
→ Map<BlockPos, StructureBlockInfo>  → ContraptionEntityRenderer（BER）
   （位置→方块状态快照）               → ClientContraption：
removeBlocksFromWorld()                · readBlockEntity() 把快照 BE 反序列化
  真实方块从世界消失                      进 VirtualRenderWorld（假世界）
                                       · 静态几何：flywheel 实例化缓存
                                       · 动态：遍历 renderedBlockEntities
                                         逐个调其 BER（用 ContraptionMatrices 变换）
```

### 关键类速查

| 类 | 职责 |
|---|---|
| `Contraption` | 结构数据核心：`getBlocks()` 快照表、`assemble()` 抽象、`remove/addBlocksToWorld()`、NBT 调色板序列化 |
| `AbstractContraptionEntity` / `OrientedContraptionEntity` | 实体宿主 + 运动；**`create(Level, Contraption, Direction)` / `createAtYaw(...)` 是 addon 直接生成装置的公开入口** |
| `ContraptionCollider` | 实体携带的方块碰撞体（玩家能站在动的结构上） |
| `ContraptionWorld` / `VirtualRenderWorld` | 假世界：BE 重建/模型解析的宿主，不污染真实 Level |
| `ClientContraption` | 客户端副本：`renderedBlockEntities` 列表 + `shouldRenderBlockEntities` BitSet + `getModelData` |
| `ContraptionEntityRenderer` | BER：实体变换矩阵（`ContraptionMatrices`）整体渲染 |
| `ContraptionVisual` / `ActorVisual` | flywheel 静态几何 GPU 实例化 / 活动部件动态视觉 |

### 行为层（方块在装置上"活着"的方式）

- **`MovementBehaviour`**（`api/behaviour/movement`，`REGISTRY: Block → Behaviour`）：
  装置上的方块 **BE 不 tick**，只有注册了 MovementBehaviour 的方块有行为钩子
  （`tick/startMoving/visitNewPosition/onSpeedChanged` 等）——收割机、钻头就是这么动的
- **`BlockMovementChecks`**（`api/contraption`）：BRITTLE/ATTACHED/MOVABLE 三态
  可移动性判定链，事件可扩展（可用于声明 GT 机器不可被机械活塞推走）

## 二、可行性分析（按我们场景分流）

### 场景 A：火箭发射动画（发射台 → 火箭起飞）★最契合

**推荐：真用 Create 装置（路线 1）**
- 火箭是**纯装饰结构**（不需要 BE 逻辑活着）——完美避开装置系统最大的坑
  （GT 机器上装置就停摆）
- 实现路径：自定义 `Contraption` 子类（不用轴承 BFS，`assemble()` 里直接
  `getBlocks().put(...)` 塞入预定义的火箭方块快照，或从结构 NBT/模板读）→
  `OrientedContraptionEntity.createAtYaw(level, contraption, UP, yaw)` 在发射台上
  生成 → 给实体设向上速度/自定义运动 → 到达高度后 `disassemble()` 或静默销毁 +
  触发采矿结算
- 依赖：整合包必装 Create 6.0.8（已在），dev 环境已有依赖 ✓；装置渲染与
  embeddium/oculus 的兼容整合包已实跑验证 ✓
- 工作量：中等（结构快照定义 + 实体运动控制 + 与发射台配方逻辑对接）

### 场景 B：观测站穹顶开合/望远镜旋转、控制器 3×3 顶面大图

**推荐：不走装置系统**——这是"视觉动画"不是"结构位移"：
- 已有正解 = GTCEu `DynamicRender`（聚变环同款 BER，自转/发光/任意几何），
  上次 3×3 大图讨论的路线 A 就是它
- 杀鸡不用牛刀：为一个穹顶开合引入整套装置装配不值

### 场景 C：通用"动态结构渲染"（无 Create 依赖需求时）

**路线 2：借思想自建**
- 核心思想可移植：`StructureBlockInfo` 快照 + 假世界（ldlib 已有现成 dummy world
  ——`SceneWidget` 渲染多方块预览就是它）+ BER 遍历
- 但等于自造半个装置渲染器：BE 反序列化、光照、模型缓存、性能（flywheel 实例化）
  全要自己扛——除非明确要脱离 Create，否则不推荐

### 通用坑位备忘

1. **装置上 BE 不 tick**——任何"机器随结构移动仍工作"的需求直接死路（GTM 机器
   全军覆没），MovementBehaviour 只能做简化行为
2. 装置回放（`addBlocksToWorld`）是硬替换——火箭落地/崩溃回放会砸坏地形，需要
   选好落点或直接销毁
3. `Contraption` NBT 用 BlockState 调色板压缩——结构大小敏感度低，火箭几十~几百
   方块无压力
4. Create 6.0.8 的 API 包在 `com.simibubi.create.api.*`（addon 友好层），content
   包内部随时会动——尽量只碰 api 层 + 稳定的 Entity/Contraption 基类

## 三、结论

- **火箭发射 = Create 装置**（`OrientedContraptionEntity.createAtYaw` 入口成熟，
  装饰结构无 BE 停摆问题，整合包依赖已就位）
- **观测站/巨构视觉动画 = GTCEu DynamicRender**（已有管线，轻量）
- 两者互不冲突，按"要不要真的位移+碰撞"分流即可

## 四、速查

| 项 | 位置（create 6.0.8） |
|---|---|
| 结构数据 | `content/contraptions/Contraption` |
| 生成入口 | `OrientedContraptionEntity.create / createAtYaw` |
| 假世界 | `foundation/virtualWorld/VirtualRenderWorld` |
| 客户端渲染 | `content/contraptions/render/{ClientContraption, ContraptionEntityRenderer, ContraptionMatrices}` |
| 装置上方块行为 | `api/behaviour/movement/MovementBehaviour`（REGISTRY） |
| 可移动判定 | `api/contraption/BlockMovementChecks` |
| 本地 jar | gradle 缓存 deobf_dependencies/curse/maven/create-328085 |
