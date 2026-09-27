# -*- coding: utf-8 -*-
"""精确核对：用户 JS 里每个符号的坐标，和生成的 StarForgePattern.java 是否一致。

JS 的 aisle 形式：.aisle('row0','row1',...)，每个 aisle = 一层 UP，
aisle 里的第 f 个字符串 = FRONT=f，字符串第 l 个字符 = LEFT=l。
GTCEu: aisle(...) 加一层 UP；builder.aisle(String...) 的字符索引 = LEFT，参数索引 = FRONT。
"""
import io, re, collections

js = io.open(r"kjs版多方块结构\人造恒星.js", encoding="utf-8", newline="", errors="replace").read()

# 抽出所有 .aisle( ... ) 块
aisles = []
for m in re.finditer(r"\.aisle\((.*?)\)\s*(?=\n|$)", js, re.S):
    body = m.group(1)
    rows = re.findall(r"'([^']*)'", body)
    if rows:
        aisles.append(rows)

print("JS aisles =", len(aisles))
if aisles:
    print("每层 rows =", collections.Counter(len(a) for a in aisles))
    print("row 长度 =", collections.Counter(len(r) for a in aisles for r in a))

# 各符号坐标
coord = collections.defaultdict(list)
for up, rows in enumerate(aisles):
    for f, row in enumerate(rows):
        for l, ch in enumerate(row):
            coord[ch].append((up, f, l))

print("\n=== JS 里各符号数量 ===")
for k in sorted(coord):
    print("  %r : %d" % (k, len(coord[k])))

print("\n=== JS 里的 B（UP,FRONT,LEFT）===")
for c in coord.get("B", [])[:80]:
    print("  ", c)
print("  B 总数:", len(coord.get("B", [])))

print("\n=== JS 里的 D ===")
print("  ", coord.get("D", []))

# 现在读生成的 Java
src = io.open(r"src\main\java\org\ringclouds\gtnecore\machine\StarForgePattern.java",
              encoding="utf-8", newline="").read()
strs = re.findall(r'"((?:[^"\\]|\\.)*)"', src)
rows65 = [s for s in strs if len(s) == 65]
print("\nJava 65 长字符串 =", len(rows65), " => aisles =", len(rows65) // 44)

jcoord = collections.defaultdict(list)
for up in range(len(rows65) // 44):
    for f in range(44):
        row = rows65[up * 44 + f]
        for l, ch in enumerate(row):
            jcoord[ch].append((up, f, l))

print("=== Java 里各符号数量 ===")
for k in sorted(jcoord):
    print("  %r : %d" % (k, len(jcoord[k])))

# 对比：JS 的 B 是否等于 Java 的 B（键映射后）
def norm(ch):
    # 生成器把 B/G/C/H 合并成 B，I -> #
    return "B" if ch in ("B", "G", "C", "H") else ("#" if ch == "I" else ch)

jsB = sorted(c for ch, lst in coord.items() for c in lst if norm(ch) == "B")
javaB = sorted(jcoord.get("B", []))
print("\n=== B 坐标对比 ===")
print("  JS(归一化后) B 数:", len(jsB))
print("  Java         B 数:", len(javaB))
print("  完全一致:", jsB == javaB)
if jsB != javaB:
    only_js = set(jsB) - set(javaB)
    only_java = set(javaB) - set(jsB)
    print("  只在 JS:", sorted(only_js)[:20])
    print("  只在 Java:", sorted(only_java)[:20])

# 对比 D
print("\n=== D 坐标 ===")
print("  JS  :", coord.get("D"))
print("  Java:", jcoord.get("D"))
