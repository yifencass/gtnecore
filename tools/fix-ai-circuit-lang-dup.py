"""修掉电路等级 tooltip 重复：删掉 lang 里由代码生成的那条 .tooltip.0。

等级行由 GtnecoreItems 代码生成（含白色 §f），lang 的同名条目会被 getMultiLang 再读一次
→ 重复显示。这里只删 .tooltip.0，保留 .tooltip.1（AGI 粉色开发者行）。
"""
import json
import os

ITEMS = [
    "ai_general_purpose_computing_cluster",
    "general_ai_full_stack_computing",
    "agi_self_solving_logic_unit",
]
LANGS = ["zh_cn", "zh_tw", "en_us", "ja_jp"]

for lang in LANGS:
    p = os.path.join("src", "main", "resources", "assets", "gtnecore", "lang", lang + ".json")
    lines = open(p, encoding="utf-8").read().splitlines()

    drop = {"item.gtnecore.%s.tooltip.0" % it for it in ITEMS}
    kept, removed = [], []
    for l in lines:
        stripped = l.strip()
        hit = None
        for key in drop:
            if stripped.startswith('"%s"' % key):
                hit = key
                break
        if hit:
            removed.append(hit)
        else:
            kept.append(l)

    out = "\n".join(kept)
    if not out.endswith("\n"):
        out += "\n"
    json.loads(out)  # 校验
    open(p, "w", encoding="utf-8", newline="\n").write(out)
    print("%-6s 删除 %d 条: %s" % (lang, len(removed), ", ".join(sorted(removed))))
