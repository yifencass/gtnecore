"""patch 层 vs fork HEAD 的逐文件差异报告（行尾归一化，git 路径用正斜杠）。"""
import difflib
import os
import subprocess

PATCH_ROOT = os.path.join("src", "patch", "java")
FORK = r"D:\create_ringclouds\GTCEu-fork"
PREFIX = os.path.join("com", "gregtechceu", "gtceu")
FORK_SRC = os.path.join(FORK, "src", "main", "java")


def head_text(fork_rel):
    git_rel = "src/main/java/" + fork_rel.replace("\\", "/")
    r = subprocess.run(["git", "-C", FORK, "show", "HEAD:" + git_rel],
                       capture_output=True)
    if r.returncode != 0:
        return None
    return r.stdout.replace(b"\r\n", b"\n")


def norm(b):
    return b.replace(b"\r\n", b"\n")


rows = []
for dp, _, fs in os.walk(PATCH_ROOT):
    for f in fs:
        if not f.endswith(".java"):
            continue
        src = os.path.join(dp, f)
        rel = os.path.relpath(src, PATCH_ROOT)
        tail = rel[len(PREFIX) + 1:] if rel.startswith(PREFIX + os.sep) else rel
        fork_rel = os.path.join(PREFIX, tail)
        cur = norm(open(src, "rb").read())
        head = head_text(fork_rel)
        if head is None:
            rows.append((tail, "fork HEAD 无此文件", 0, 0))
            continue
        if head == cur:
            rows.append((tail, "与 HEAD 相同", 0, 0))
            continue
        d = list(difflib.unified_diff(head.decode("utf-8", "ignore").splitlines(),
                                      cur.decode("utf-8", "ignore").splitlines(),
                                      lineterm="", n=0))
        add = sum(1 for l in d if l.startswith("+") and not l.startswith("+++"))
        rm = sum(1 for l in d if l.startswith("-") and not l.startswith("---"))
        rows.append((tail, "patch 层有改动", add, rm))

print("总计 patch 文件:", len(rows))
for kind in ("fork HEAD 无此文件", "patch 层有改动", "与 HEAD 相同"):
    sel = [r for r in rows if r[1] == kind]
    print("\n### %s — %d 个" % (kind, len(sel)))
    for tail, _, add, rm in sorted(sel, key=lambda r: -(r[2] + r[3]))[:20]:
        print("     %-70s +%d/-%d" % (tail, add, rm))
    if len(sel) > 20:
        print("      ... 另 %d 个" % (len(sel) - 20))
