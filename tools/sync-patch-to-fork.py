"""patch 层 -> fork 单向同步（仅用于保持开发侧镜像一致；发布产物只用 patch 层）。"""
import os
import shutil
import sys

PATCH_ROOT = os.path.join("src", "patch", "java")
FORK_ROOT = r"D:\create_ringclouds\GTCEu-fork\src\main\java"

synced = []
missing = []
for dp, _, fs in os.walk(PATCH_ROOT):
    for f in fs:
        if not f.endswith(".java"):
            continue
        src = os.path.join(dp, f)
        rel = os.path.relpath(src, PATCH_ROOT)
        # patch 层两种布局：
        #   <pkg>.java                    -> fork 的 src/main/java/com/gregtechceu/gtceu/<pkg>.java
        #   com/gregtechceu/gtceu/<pkg>.java -> fork 的 src/main/java/com/gregtechceu/gtceu/<pkg>.java
        prefix = os.path.join("com", "gregtechceu", "gtceu")
        if rel.startswith(prefix + os.sep):
            tail = rel[len(prefix) + 1:]
        else:
            tail = rel
        cands = [os.path.join(FORK_ROOT, prefix, tail),
                 os.path.join(FORK_ROOT, tail)]
        dst = next((c for c in cands if os.path.exists(c)), None)
        if dst is None:
            missing.append(rel)
            continue
        a = open(src, "rb").read()
        b = open(dst, "rb").read()
        if a != b:
            shutil.copyfile(src, dst)
            synced.append(rel)

print("同步 %d 个:" % len(synced))
for s in synced:
    print("   ", s)
if missing:
    print("fork 中找不到对应文件 %d 个（这些是 patch 独有类）:" % len(missing))
    for m in missing[:20]:
        print("   ", m)
