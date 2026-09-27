"""把 avaritia 的无尽锭/粒也加入 forge 惯例 tag，与 GTCEu 生成的共用。

理由（用户 2026-09-26）：无尽锭很好看，tag 是让"无尽贪婪的无尽锭"与
"GTCEu 生成的无尽锭"公用的关键点。
"""
import json

TARGETS = {
    r"src\main\resources\data\forge\tags\items\ingots\infinity.json": [
        "gtceu:infinity_ingot",
        "avaritia:infinity_ingot",
    ],
    r"src\main\resources\data\forge\tags\items\nuggets\infinity.json": [
        "gtceu:infinity_nugget",
        "avaritia:infinity_nugget",
    ],
}

for path, values in TARGETS.items():
    content = json.dumps({"replace": False, "values": values}, indent=2, ensure_ascii=False) + "\n"
    open(path, "w", encoding="utf-8", newline="\n").write(content)
    parsed = json.loads(open(path, encoding="utf-8").read())
    print("%-62s -> %s" % (path.split("items\\")[-1], parsed["values"]))
