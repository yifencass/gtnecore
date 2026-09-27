# GTCEu KubeJS 快速上手（傻子都能看懂版）

> 目标是：**复制就能用**。所有示例都经过 GTCEu 7.5.3 jar 验证。
> 想深入看《GTCEu-KubeJS开发文档.md》（超级详细版）。

---

## 第 0 步：先搞懂三件事

1. **脚本放哪**：`run/kubejs/startup_scripts/`（注册物品/材料/机器）和
   `run/kubejs/server_scripts/`（加配方/改世界）。改了**要重启游戏**（F3+T 只重载 server 脚本）。
2. **三个最常用入口**：
   ```js
   GTCEuStartupEvents.registry('gtceu:material', event => { ... })  // 注册材料
   GTCEuStartupEvents.registry('gtceu:machine', event => { ... })   // 注册机器
   ServerEvents.recipes(event => { ... })                            // 加配方
   ```
3. **报错了看日志**：`run/logs/latest.log` 搜 `KubeJS` 或脚本文件名。

---

## 模板 1：给现有机器加配方（最高频）

```js
ServerEvents.recipes(event => {
    event.recipes.gtceu.assembler('我的配方id')
        .itemInputs('2x minecraft:iron_ingot', '#forge:dusts/coal')  // 物品输入（可带数量/标签）
        .inputFluids(Fluid.of('gtceu:lubricant', 100))               // 流体输入
        .itemOutputs('4x gtceu:steel_ingot')                          // 物品输出
        .circuit(1)                                                   // 编程电路（1-32）
        .duration(200)                                                // 时长（tick）
        .EUt(GTValues.VA[GTValues.HV])                                // 耗电（正数=耗电，负数=发电）
})
```

**机器名去哪找**：JEI 里看配方类型，或用 `event.recipes.gtceu.` 后按 tab（ProbeJS 会给全列表）。
常见：`assembler` `arc_furnace` `chemical_reactor` `macerator` `centrifuge` `electric_blast_furnace` `extruder` `assembling` ...

## 模板 2：删配方

```js
ServerEvents.recipes(event => {
    event.remove({ id: 'gtceu:smelting/sticky_resin_from_slime' })  // 按 id
    event.remove({ mod: 'gtceu' })                                   // 删一个 mod 的全部
    event.remove({ type: 'gtceu:arc_furnace' })                      // 按配方类型
    event.remove({ output: 'minecraft:cobblestone' })                // 按产物
})
```

## 模板 3：新建材料

```js
GTCEuStartupEvents.registry('gtceu:material', event => {
    event.create('my_alloy')
        .ingot()                                                     // 会生成锭
        .components('1x iron', '1x copper')                          // 成分（'数量x 材料'）
        .color(0x839689)                                             // 颜色
        .iconSet(GTMaterialIconSet.DULL)                             // 图标风格
        .flags(GTMaterialFlags.GENERATE_PLATE, GTMaterialFlags.GENERATE_GEAR)  // 自动生成哪些部件
})
```
**注意**：不加 `.ingot()/.dust()` 等形态，材料就是"空气"——什么都没有。

## 模板 4：改已有材料

```js
GTCEuStartupEvents.materialModification(event => {
    GTMaterials.Iron.addFlags(GTMaterialFlags.GENERATE_GEAR)   // 给铁加齿轮
    GTMaterials.get('my_alloy').setMaterialARGB(0x123456)      // 改颜色
})
```

## 模板 5：新建单块机器

```js
// 第一步：先注册配方类型（必须在机器之前！）
GTCEuStartupEvents.registry('gtceu:recipe_type', event => {
    event.create('my_recipe_type')
        .category('test')
        .setEUIO('in')                              // 耗电机器；'out' 是发电
        .setMaxIOSize(3, 3, 3, 3)                   // 物入/物出/流入/流出 槽数
        .setProgressBar(GTGuiTextures.PROGRESS_ARROW)
})

// 第二步：注册机器
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_machine', 'simple', 0, GTValues.LV, GTValues.MV)  // 0=污染值，后跟档位
        .recipeType('my_recipe_type')
        .rotationState(RotationState.NON_Y_AXIS)
})

// 第三步：给机器加配方（配方类型名就是 recipes 名）
ServerEvents.recipes(event => {
    event.recipes.gtceu.my_recipe_type('test_recipe')
        .itemInputs('minecraft:dirt')
        .itemOutputs('minecraft:diamond')
        .duration(100).EUt(30)
})
```

## 模板 6：新建多方块机器

```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('my_controller', 'multiblock')
        .rotationState(RotationState.NON_Y_AXIS)
        .recipeTypes(['my_recipe_type'])
        .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)      // 外壳外观
        .pattern(definition => FactoryBlockPattern.start()
            .aisle('CCC', 'GGG', 'CCC')                     // 每一层用字符串画
            .aisle('CCC', 'GDG', 'CSC')
            .aisle('CKC', 'GGG', 'CMC')
            .where('K', Predicates.controller(Predicates.blocks(definition.get())))
            .where('M', Predicates.abilities(PartAbility.MAINTENANCE))
            .where('S', Predicates.abilities(PartAbility.MUFFLER))
            .where('D', Predicates.blocks(GTBlocks.COIL_CUPRONICKEL.get()))
            .where('G', Predicates.blocks('minecraft:glass'))
            .where('C', Predicates.blocks(GTBlocks.CASING_STEEL_SOLID.get()))
            .build())
})
```
**字母表**：`K`=控制器（必须） `M`=维护仓 `S`=消声器 `E`=能源仓 `I`=输入总线 `O`=输出总线 `D`/`C`/`G`=自定义方块。

## 模板 7：给机器配方加条件/概率

```js
ServerEvents.recipes(event => {
    event.recipes.gtceu.macerator('chance_recipe')
        .itemInputs('minecraft:gold_ore')
        .chancedOutput('minecraft:gold_ingot', 5000)     // 50% 概率（分母 10000）
        .chancedOutput('minecraft:gold_nugget', 8000, 100)  // 80% + 每档 +1%
        .dimension('minecraft:the_nether')               // 只能在某个维度
        .cleanroom(CleanroomType.CLEANROOM)              // 需要洁净室
        .duration(50).EUt(30)
})
```

## 模板 8：矿脉

```js
GTCEuServerEvents.oreVeins(event => {
    event.add('kubejs:my_vein', vein => {
        vein.weight(200)                       // 权重（越大越常见）
        vein.clusterSize(40)
        vein.density(0.25)
        vein.layer('deepslate')                // stone / deepslate / netherrack / endstone
        vein.dimensions('minecraft:overworld')
        vein.biomes('#minecraft:is_overworld')
        vein.heightRangeUniform(-60, 20)
        vein.generator(b => b.standard().withBlock('gtceu:iron_ore'))
    })
    event.remove('gtceu:magnetite_vein_ow')    // 删原版矿脉
})
```

## 模板 9：加热线圈方块（给合金高炉/EBF 用）

```js
StartupEvents.registry('block', event => {
    event.create('my_coil', 'gtceu:coil')
        .temperature(5000)                     // 温度（K）——决定能炼什么
        .level(5)                              // 线圈等级
        .energyDiscount(2)                     // 能耗折扣
        .tier(6)
        .texture('kubejs:block/my_coil')
        .hardness(5)
})
```

## 模板 10：披风

```js
GTCEuServerEvents.registerCapes(event => {
    event.registerCape('kubejs:my_cape', 'kubejs:textures/capes/my_cape.png')
})
```

---

## 常见报错速查

| 报错/现象 | 原因 | 修法 |
|---|---|---|
| `Cannot find function xxx in object` | 方法名拼错/该版本没有 | 对照《开发文档》方法清单 |
| 材料没有物品生成 | 没加 `.ingot()/.dust()` 或没加 `GENERATE_*` flags | 补形态 + flags |
| 机器紫黑 | 模型文件缺失 | 补 `assets/kubejs/models/block/machine/<id>.json` |
| 配方不生效 | 配方类型还没注册（顺序错） | 配方类型必须先于机器注册 |
| 机器 GUI 空白 | recipeType 引用错误 | 检查 `recipeType('...')` 与注册 id 一致 |
| F3+T 后 startup 改动不生效 | startup 脚本不热重载 | 重启游戏 |
| 线圈不加热 | 温度/等级不匹配配方要求 | 检查 `.temperature()` 是否 ≥ 配方要求 |

---

## 三个黄金法则

1. **改注册类的东西**（材料/机器/配方类型）→ `startup_scripts` + 重启游戏
2. **改玩法类的东西**（配方/矿脉/事件）→ `server_scripts` + F3+T 或重启
3. **不确定的 API** → `Java.loadClass('类名')` + `javap`，或翻《GTCEu-KubeJS开发文档.md》
