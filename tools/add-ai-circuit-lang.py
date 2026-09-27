"""在四个语言文件中最小插入 AI 电路板的 lang 条目（不重排现有 JSON）。"""
import io
import json
import os

LANGS = {
    "zh_cn": {
        "names": {
            "ai_general_purpose_computing_cluster": "AI通用计算集群",
            "general_ai_full_stack_computing": "通用AI全栈计算",
            "agi_self_solving_logic_unit": "AGI自解逻辑单元",
        },
        "tiers": {
            "ai_general_purpose_computing_cluster": "§fUV级电路",
            "general_ai_full_stack_computing": "§fUHV级电路",
            "agi_self_solving_logic_unit": "§fUEV级电路",
        },
        "dev": "§d--开发者物品--",
    },
    "zh_tw": {
        "names": {
            "ai_general_purpose_computing_cluster": "AI通用計算集群",
            "general_ai_full_stack_computing": "通用AI全棧計算",
            "agi_self_solving_logic_unit": "AGI自解邏輯單元",
        },
        "tiers": {
            "ai_general_purpose_computing_cluster": "§fUV級電路",
            "general_ai_full_stack_computing": "§fUHV級電路",
            "agi_self_solving_logic_unit": "§fUEV級電路",
        },
        "dev": "§d--開發者物品--",
    },
    "en_us": {
        "names": {
            "ai_general_purpose_computing_cluster": "AI General-Purpose Computing Cluster",
            "general_ai_full_stack_computing": "General AI Full-Stack Computing",
            "agi_self_solving_logic_unit": "AGI Self-Solving Logic Unit",
        },
        "tiers": {
            "ai_general_purpose_computing_cluster": "§fUV-Tier Circuit",
            "general_ai_full_stack_computing": "§fUHV-Tier Circuit",
            "agi_self_solving_logic_unit": "§fUEV-Tier Circuit",
        },
        "dev": "§d--Developer Item--",
    },
    "ja_jp": {
        "names": {
            "ai_general_purpose_computing_cluster": "AI汎用計算クラスタ",
            "general_ai_full_stack_computing": "汎用AIフルスタック計算",
            "agi_self_solving_logic_unit": "AGI自己解決論理ユニット",
        },
        "tiers": {
            "ai_general_purpose_computing_cluster": "§fUV級回路",
            "general_ai_full_stack_computing": "§fUHV級回路",
            "agi_self_solving_logic_unit": "§fUEV級回路",
        },
        "dev": "§d--開発者アイテム--",
    },
}

ORDER = [
    "ai_general_purpose_computing_cluster",
    "general_ai_full_stack_computing",
    "agi_self_solving_logic_unit",
]

ANCHOR = '"item.gtnecore.vacuum_pump_cover.tooltip"'
DEV_ITEM = "agi_self_solving_logic_unit"

for lang, data in LANGS.items():
    p = os.path.join("src", "main", "resources", "assets", "gtnecore", "lang", lang + ".json")
    text = open(p, encoding="utf-8").read()
    lines = text.splitlines()

    if any("ai_general_purpose_computing_cluster" in l for l in lines):
        print("%-6s 已存在，跳过" % lang)
        continue

    block = []
    for name in ORDER:
        base = "item.gtnecore." + name
        block.append('  "%s": "%s",' % (base, data["names"][name]))
        block.append('  "%s.tooltip.0": "%s",' % (base, data["tiers"][name]))
        if name == DEV_ITEM:
            block.append('  "%s.tooltip.1": "%s",' % (base, data["dev"]))

    # 找锚点行下标
    idx = next(i for i, l in enumerate(lines) if ANCHOR in l)
    # 锚点行之后（保持 item.* 聚集）插入
    new_lines = lines[:idx + 1] + block + lines[idx + 1:]
    # 原来锚点行若有尾逗号则不变；若锚点是最后一条（无逗号）则需要补逗号
    if not lines[idx].rstrip().endswith(","):
        new_lines[idx] = new_lines[idx].rstrip() + ","

    out = "\n".join(new_lines)
    if not out.endswith("\n"):
        out += "\n"
    # 校验仍是合法 JSON
    json.loads(out)
    open(p, "w", encoding="utf-8", newline="\n").write(out)
    print("%-6s 插入 %d 行，JSON 校验通过" % (lang, len(block)))
