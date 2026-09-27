"""校验 AI 电路板：注册名、lang、模型、贴图、tag 注入是否齐全且未破坏原版 tag。"""
import io
import json
import os
import zipfile

jar = r"build\libs\gtnecore-0.0.3.jar"
z = zipfile.ZipFile(jar)
names = set(z.namelist())

ITEMS = [
    "ai_general_purpose_computing_cluster",
    "general_ai_full_stack_computing",
    "agi_self_solving_logic_unit",
]
LANGS = ["zh_cn", "zh_tw", "en_us", "ja_jp"]

print("=== 1. 贴图 ===")
for it in ITEMS:
    tex = "assets/gtnecore/textures/item/%s.png" % it
    mc = tex + ".mcmeta"
    print("   %-42s png=%s mcmeta=%s" % (it, tex in names, mc in names))

print()
print("=== 2. 物品模型 ===")
for it in ITEMS:
    m = "assets/gtnecore/models/item/%s.json" % it
    ok = m in names
    detail = ""
    if ok:
        d = json.loads(z.read(m))
        detail = d.get("textures", {}).get("layer0", "")
    print("   %-42s %s  %s" % (it, ok, detail))

print()
print("=== 3. lang（等级行白 §f + AGI 粉色行 §d）===")
for lg in LANGS:
    p = "assets/gtnecore/lang/%s.json" % lg
    d = json.loads(z.read(p))
    miss = [k for it in ITEMS for k in
            ("item.gtnecore." + it, "item.gtnecore." + it + ".tooltip.0")]
    miss = [k for k in miss if k not in d]
    dev = d.get("item.gtnecore.agi_self_solving_logic_unit.tooltip.1", "")
    tier0 = d.get("item.gtnecore.ai_general_purpose_computing_cluster.tooltip.0", "")
    print("   %-6s 缺 %d 项 | UV等级行=%r | AGI开发者行=%r" % (lg, len(miss), tier0, dev))

print()
print("=== 4. 电路 tag 注入文件（引用式）===")
for t in ["ai_uv", "ai_uhv", "ai_uev"]:
    p = "data/gtnecore/tags/items/circuits/%s.json" % t
    if p in names:
        print("   %-48s %s" % (p, json.loads(z.read(p))["values"]))
    else:
        print("   *** 缺失", p)

print()
print("=== 5. 原版 gtceu 电路 tag 是否被破坏（应仍为原版内容，jar 内无我们的覆盖文件）===")
for t in ["uv", "uhv", "uev"]:
    p = "data/gtceu/tags/items/circuits/%s.json" % t
    print("   data/gtceu/.../%s.json 在我们的 jar 里: %s（应为 False，否则会覆盖原版）" % (t, p in names))

print()
print("=== 6. 已编译类是否含三块板子 ===")
cls = [n for n in names if n.endswith("GtnecoreItems.class")]
for c in cls:
    d = z.read(c)
    for it in ITEMS:
        print("   %-42s %s" % (it, it.encode() in d))
