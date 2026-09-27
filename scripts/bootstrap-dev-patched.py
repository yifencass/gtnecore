# -*- coding: utf-8 -*-
"""从 libs/gtceu-release-7.5.3.jar 恢复 dev patched jar（最小覆盖法）。

关键：只覆盖**补丁源码树里真正存在的那 109 个类**（类名由每个 .java 的
package 声明推导），其余全部沿用 deobf maven jar —— 这样不会把 srg/official
的映射差异类混进来（混进来会让 MachineModel 报 m_6922_ 之类的错）。

需要把这个脚本保留下来：它是 clean 事故后的自举手段。
"""
import os
import re
import zipfile

DEOBF = (r"C:\Users\Administrator\.gradle\caches\forge_gradle\deobf_dependencies"
         r"\com\gregtechceu\gtceu\gtceu-1.20.1\7.5.3_mapped_official_1.20.1"
         r"\gtceu-1.20.1-7.5.3_mapped_official_1.20.1.jar")
PATCHED_SRC_JAR = r"libs\gtceu-release-7.5.3.jar"   # 含 109 补丁类（srg 映射）
PATCH_SRC = r"src\patch\java"
OUT = r"build\patched\gtceu-1.20.1-7.5.3-patched.jar"

PKG = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)

# 1) 补丁源码树 -> 类全名（用 package 声明，不信目录结构）
patched = set()
for root, _, files in os.walk(PATCH_SRC):
    for f in files:
        if not f.endswith(".java"):
            continue
        text = open(os.path.join(root, f), encoding="utf-8", errors="replace").read()
        m = PKG.search(text)
        if m:
            patched.add((m.group(1) + "." + f[:-5]).replace(".", "/"))
print("补丁源码类:", len(patched))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with zipfile.ZipFile(PATCHED_SRC_JAR) as p, zipfile.ZipFile(DEOBF) as d:
    p_names, d_names = set(p.namelist()), set(d.namelist())
    overlay = set()
    for cls in patched:
        for n in p_names:
            if (n == cls + ".class" or n.startswith(cls + "$")) and n in d_names:
                overlay.add(n)
    print("命中并覆盖:", len(overlay))
    if not overlay:
        raise SystemExit("没找到任何补丁类，中止")

    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as o:
        for info in d.infolist():
            name = info.filename
            blob = p.read(name) if name in overlay else d.read(name)
            ni = zipfile.ZipInfo(name, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.internal_attr = info.internal_attr
            ni.create_system = info.create_system
            ni.flag_bits = info.flag_bits
            o.writestr(ni, blob)
            o.filelist[-1].flag_bits = info.flag_bits

gv = zipfile.ZipFile(OUT).read("com/gregtechceu/gtceu/api/GTValues.class")
print("重建:", OUT, os.path.getsize(OUT), "字节  VA/VHA:", b"VA" in gv, b"VHA" in gv)
