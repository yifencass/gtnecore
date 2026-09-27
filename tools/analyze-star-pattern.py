# -*- coding: utf-8 -*-
"""分析 StarForgePattern.PATTERN 的实际几何：每层（aisle）里各符号的分布。

GTCEu 的 FactoryBlockPattern 轴序是 (LEFT, UP, FRONT)：
  aisle(...) 一次 = 一层 UP
  每个字符串 = 一个 FRONT 坐标
  字符串里第 i 个字符 = 一个 LEFT 坐标
所以 PATTERN[up][front][left]。这里按这个轴序统计。
"""
import io, re, collections

src = io.open(r"src\main\java\org\ringclouds\gtnecore\machine\StarForgePattern.java",
              encoding="utf-8", newline="").read()

# 抓出所有字符串字面量（按文件顺序）
strs = re.findall(r'"((?:[^"\\]|\\.)*)"', src)
# 去掉文件头注释里可能出现的东西：真正的数据是长度 65 的串
rows = [s for s in strs if len(s) == 65]
print("抓到 65 长字符串:", len(rows))

if not rows:
    print("按长度过滤失败，前 20 个串长度：", [len(s) for s in strs[:20]])
    raise SystemExit(1)

aisles = len(rows) // 44
print("推得 aisles(UP 层数) = %d / 44 = %s" % (aisles, aisles == len(rows) / 44))

# 每层
for up in range(aisles):
    layer = rows[up * 44:(up + 1) * 44]
    flat = "".join(layer)
    c = collections.Counter(flat)
    # 有内容的层才打印
    interesting = {k: v for k, v in c.items() if k not in "# " and v > 0}
    if interesting:
        print("UP=%2d  front=%d  %s" % (up, len(layer), interesting))

print("\n=== 全局统计 ===")
allc = collections.Counter("".join(rows))
for k, v in sorted(allc.items()):
    print("  %r : %d" % (k, v))

# B 位 / D 位坐标
print("\n=== 'D'(控制器) 坐标 (up, front, left) ===")
for up in range(aisles):
    for f in range(44):
        row = rows[up * 44 + f]
        for l, ch in enumerate(row):
            if ch == "D":
                print("  ", (up, f, l))

print("\n=== 'B' 坐标数量 ===")
bs = []
for up in range(aisles):
    for f in range(44):
        row = rows[up * 44 + f]
        for l, ch in enumerate(row):
            if ch == "B":
                bs.append((up, f, l))
print("  总数:", len(bs), " 前 15:", bs[:15])
