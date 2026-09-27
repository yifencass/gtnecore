"""审计 PnC 引用边界：找出所有引用了 PnC 类型的已编译类，并区分是否在集成包内。"""
import os
import re

OUT = os.path.join("build", "classes", "java", "main")
PNC = b"me/desht/pneumaticcraft"
API = b"me/desht/pneumaticcraft/api/"
COMMON = b"me/desht/pneumaticcraft/common/"

inside, outside, common_hits = [], [], []

for dp, _, fs in os.walk(OUT):
    for f in fs:
        if not f.endswith(".class"):
            continue
        p = os.path.join(dp, f)
        d = open(p, "rb").read()
        if PNC not in d:
            continue
        rel = os.path.relpath(p, OUT).replace("\\", "/")
        if COMMON in d:
            common_hits.append(rel)
        if rel.startswith("org/ringclouds/gtnecore/integration/pneumaticcraft/"):
            inside.append(rel)
        else:
            outside.append(rel)

print("=== 引用了 PnC 的已编译类 ===")
print("集成包内（合法）:", len(inside))
for x in sorted(inside):
    print("   ", x)
print()
print("集成包外（需为 0）:", len(outside))
for x in sorted(outside):
    print("   !!", x)
print()
print("=== 越界引用 me.desht.pneumaticcraft.common.*（GPLv3 区域，需为 0）===")
print(len(common_hits))
for x in sorted(common_hits):
    print("   !!", x)
