#!/usr/bin/env python3
"""给 3..26 号元素里缺等离子体的 20 个材料补 .plasma()。

位置：src/patch/java/common/data/materials/ElementMaterials.java
插入点：该材料定义块里 `.element(...)` 行的下一行（在 .buildAndRegister() 之前）。

已存在等离子体的（跳过）：Nitrogen / Oxygen / Argon / Iron
"""
import os
import re

P = os.path.join("src", "patch", "java", "common", "data", "materials", "ElementMaterials.java")

# 3..26 号元素
WANT = ["Lithium", "Beryllium", "Boron", "Carbon", "Fluorine", "Neon", "Sodium",
        "Magnesium", "Aluminium", "Silicon", "Phosphorus", "Sulfur", "Chlorine",
        "Potassium", "Calcium", "Scandium", "Titanium", "Vanadium", "Chromium",
        "Manganese"]
# 已有 plasma，不动
SKIP = {"Nitrogen", "Oxygen", "Argon", "Iron"}

src = open(P, encoding="utf-8").read()
lines = src.splitlines()

out = []
i = 0
added, skipped, notfound = [], [], []
while i < len(lines):
    line = lines[i]
    out.append(line)
    m = re.match(r"\s*(\w+) = new Material\.Builder\(", line)
    name = m.group(1) if m else None
    if name and name in WANT and name not in SKIP:
        # 收集这个材料块直到 .buildAndRegister();
        j = i + 1
        block = []
        while j < len(lines) and "buildAndRegister()" not in lines[j]:
            block.append(lines[j])
            j += 1
        # 紧跟 buildAndRegister 行也取进来（含它之后的分号处理）
        if j < len(lines):
            block.append(lines[j])
        blob = "\n".join(block)
        if ".plasma(" in blob or ".plasma()" in blob:
            skipped.append(name)
        else:
            # 找块内最后一个 .element(...) 行
            last_elem = None
            for k, bl in enumerate(block):
                if re.search(r"\.element\(", bl):
                    last_elem = k
            if last_elem is None:
                notfound.append(name)
            else:
                indent = re.match(r"(\s*)", block[last_elem]).group(1)
                block.insert(last_elem + 1, indent + ".plasma()")
                added.append(name)
        out.extend(block)
        i = j + 1
        continue
    i += 1

result = "\n".join(out)
if not result.endswith("\n"):
    result += "\n"
open(P, "w", encoding="utf-8", newline="\n").write(result)

print("已插入 .plasma() 的材料 (%d): %s" % (len(added), ", ".join(added)))
if skipped:
    print("块内已有 plasma，跳过 (%d): %s" % (len(skipped), ", ".join(skipped)))
if notfound:
    print("*** 找不到 .element() 行 (%d): %s ***" % (len(notfound), ", ".join(notfound)))
