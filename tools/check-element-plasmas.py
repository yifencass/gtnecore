# -*- coding: utf-8 -*-
"""核对 3..26 号元素是否都挂了 .plasma()（人造恒星等离子体可选清单的依据）。"""
import io, re

p = r"src\patch\java\common\data\materials\ElementMaterials.java"
s = io.open(p, encoding="utf-8", newline="").read()

want = ["Lithium", "Beryllium", "Boron", "Carbon", "Nitrogen", "Oxygen", "Fluorine", "Neon",
        "Sodium", "Magnesium", "Aluminium", "Silicon", "Phosphorus", "Sulfur", "Chlorine",
        "Argon", "Potassium", "Calcium", "Scandium", "Titanium", "Vanadium", "Chromium",
        "Manganese", "Iron"]

ok, bad = [], []
for w in want:
    m = re.search(r"Element\.%s\b" % w, s)
    if not m:
        bad.append((w, "no element()"))
        continue
    seg = s[m.start():m.start() + 900]
    # 截到该 builder 链结束
    cut = seg.find(";")
    if cut > 0:
        seg = seg[:cut]
    if ".plasma(" in seg:
        ok.append(w)
    else:
        bad.append((w, "no .plasma()"))

print("带 plasma 的：%d / %d" % (len(ok), len(want)))
for w, why in bad:
    print("  ✗ %-12s %s" % (w, why))
if not bad:
    print("  全部齐了")
