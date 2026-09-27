# -*- coding: utf-8 -*-
"""修正 StarForgePattern.PATTERN 的 UP 轴顺序。

症状：用户 JS 里控制器 D 在 (UP=0,FRONT=2,LEFT=32)，生成的 Java 里在 (UP=64,...)
      —— 整个结构沿 UP 轴翻面，B 功能舱位全错位。

本文件格式：每个 aisle 是**一行** `{ "..","..", ... },`（44 个 65 长字符串）。
所以修正 = 把这 65 行**整行倒序**。
"""
import io, re

P = r"src\main\java\org\ringclouds\gtnecore\machine\StarForgePattern.java"
src = io.open(P, encoding="utf-8", newline="").read()

m = re.search(r"(public static final String\[\]\[\] PATTERN = \{\n)(.*?)(\n\s*\};)", src, re.S)
assert m, "PATTERN 数组未找到"
head, body, tail = m.group(1), m.group(2), m.group(3)

# 每个 aisle 一行
lines = [l for l in body.split("\n") if l.strip()]
print("aisle 行数 =", len(lines))
assert len(lines) == 65, len(lines)

# 每行的字符串数量与长度
per = [len(re.findall(r'"(?:[^"\\]|\\.)*"', l)) for l in lines]
assert all(p == 44 for p in per), set(per)
lens = set()
for l in lines:
    for s in re.findall(r'"((?:[^"\\]|\\.)*)"', l):
        lens.add(len(s))
assert lens == {65}, lens

# 校验当前 D 位置（应错在 UP=64）
def d_up(ordered_lines):
    for up, l in enumerate(ordered_lines):
        rows = re.findall(r'"((?:[^"\\]|\\.)*)"', l)
        for f, row in enumerate(rows):
            if "D" in row:
                return (up, f, row.index("D"))
    return None

print("修正前 D 坐标:", d_up(lines))
lines.reverse()
print("修正后 D 坐标:", d_up(lines))
assert d_up(lines) == (0, 2, 32), "D 位置仍不对"

# 行的缩进与结尾逗号
new_body = []
for i, l in enumerate(lines):
    text = l.strip()
    if not text.endswith(",") and i < len(lines) - 1:
        text += ","
    new_body.append("            " + text)
new_body = "\n".join(new_body)

out = src[:m.start(2)] + "\n" + new_body + src[m.end(2):]

note = (" * <p><b>2026-09-26 修正</b>：生成脚本把 aisle 顺序弄反了，整个结构沿 UP 轴翻面，\n"
        " * 控制器实际落到 (UP=64,FRONT=2,LEFT=32)、B 功能舱位全部错位 —— 玩家把仓放对位置\n"
        " * 也不被算作部件，而 {@code #} 的 {@code Predicates.any()} 会把仓当普通方块收下，\n"
        " * 于是「结构成型但 parts=0」。现已把 65 层 aisle 整体倒序；\n"
        " * 校验点：{@code D} 必须落在 UP=0, FRONT=2, LEFT=32。\n")
out = out.replace("public final class StarForgePattern {",
                  note + "public final class StarForgePattern {", 1)

io.open(P, "w", encoding="utf-8", newline="").write(out)
print("已写回", P)
