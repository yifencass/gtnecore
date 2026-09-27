#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""重建 dev 用的 patched gtceu jar（自举脚本，clean 事故后的恢复手段）。

背景
====
`build/patched/gtceu-1.20.1-7.5.3-patched.jar` 是**手工两步产物**：
    ./gradlew patchJar
    python scripts/make-dev-patched.py <deobf jar> build/patched/gtceu-patch.jar <out>
`build.gradle` 只 `implementation files(...)` **引用**它，没有任何 Gradle 任务生成它。
一旦被 `gradlew clean` 删掉就会死锁：
    compileJava      需要 patched jar（主工程用 VA/VHA 等补丁新增的字段）
    compilePatchJava 需要 main 输出
    patchJar         需要 compilePatchJava
（`build.gradle` 里已加 clean 防护：删前备份到 patched-backup/，删后还原。）

可用的"尸体"来源（按可靠性排序）
================================
1. **`libs/gtceu-release-7.5.3.jar`**  ← 首选，且是**当前**版本
   由 `./gradlew updateReleaseGtceuJar` 维护（srg 映射，顶层 = 原始 + 补丁覆盖）。
   本脚本就是拿它当"补丁类来源"。

2. `特供版/gtnecore-0.0.2-all.jar`
   -> `META-INF/jarjar/gtceu-release-7.5.3.jar`（17963217 字节，2026-08-27）

3. `github需要上传项/gtnecore-0.0.2-all.bak-0818.jar`
   -> `META-INF/jarjar/gtceu-release-7.5.3.jar`（17963957 字节，2026-08-31）

⚠ 2 和 3 是**旧快照**，只作最后兜底。实测它们缺当前版有的成员
（例如 `BaseBakedModel.getOverrides()`），直接当依赖会让 `compilePatchJava`
报「MachineModel 未覆盖抽象方法」。真要动用它们，注意这一点。

4. `~/.gradle/caches/forge_gradle/minecraft_user_repo/.../forge-...jar` 等缓存
   属于 Forge/MC，不是 GTCEu 补丁来源。

原理（为什么"最小覆盖"是对的）
==============================
* deobf maven jar 的 GTCEu API 是 **official 映射**，与 `src/patch` 源码一致，
  所以补丁编译只需要它；它唯一缺的就是补丁新增的字段（如 `GTValues.VA/VHA`）。
* 如果把 release jar 与 deobf jar 的**所有**差异类都覆盖上去，会混入大量
  **srg vs official 的映射差异**（例如接口里的 `m_6922_`），反而编译不过。
* 因此：**只覆盖补丁源码树里真正存在的类**（类名由每个 .java 的 package 声明推导），
  实测命中 109 个类，与文档记载的"109 个补丁类"完全吻合。

用法
====
    python scripts/bootstrap-dev-patched.py
之后按正常流程：
    ./gradlew patchJar        # 会编译 main + patch
    python scripts/make-dev-patched.py <deobf> build/patched/gtceu-patch.jar <out>
    ./gradlew build
"""
import os
import re
import sys
import zipfile

DEOBF_DEFAULT = (
    r"C:\Users\Administrator\.gradle\caches\forge_gradle\deobf_dependencies"
    r"\com\gregtechceu\gtceu\gtceu-1.20.1\7.5.3_mapped_official_1.20.1"
    r"\gtceu-1.20.1-7.5.3_mapped_official_1.20.1.jar"
)
# 补丁类来源，按优先级尝试
SOURCES = [
    r"libs\gtceu-release-7.5.3.jar",
    r"特供版\gtnecore-0.0.2-all.jar",
    r"github需要上传项\gtnecore-0.0.2-all.bak-0818.jar",
]
NESTED = "META-INF/jarjar/gtceu-release-7.5.3.jar"
PATCH_SRC = r"src\patch\java"
OUT = r"build\patched\gtceu-1.20.1-7.5.3-patched.jar"

PKG = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)


def pick_source(deobf_path):
    """返回 (来源说明, 打开的 ZipFile)。"""
    for cand in SOURCES:
        if not os.path.exists(cand):
            continue
        if cand.endswith("-all.jar") or "bak" in os.path.basename(cand):
            # 从 -all.jar 里抠嵌套 jar
            try:
                z = zipfile.ZipFile(cand)
                if NESTED in z.namelist():
                    import io
                    data = z.read(NESTED)
                    return "%s -> %s" % (cand, NESTED), zipfile.ZipFile(io.BytesIO(data))
            except Exception:
                continue
        else:
            return cand, zipfile.ZipFile(cand)
    return None, None


def main():
    deobf = sys.argv[1] if len(sys.argv) > 1 else DEOBF_DEFAULT
    if not os.path.exists(deobf):
        sys.exit("找不到 deobf gtceu jar: %s" % deobf)

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
    print("补丁源码类: %d" % len(patched))

    desc, src_zip = pick_source(deobf)
    if src_zip is None:
        sys.exit("找不到任何可用的补丁类来源（见本文件顶部清单）")
    print("补丁类来源: %s" % desc)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with src_zip as p, zipfile.ZipFile(deobf) as d:
        p_names, d_names = set(p.namelist()), set(d.namelist())
        overlay = set()
        for cls in patched:
            for n in p_names:
                if (n == cls + ".class" or n.startswith(cls + "$")) and n in d_names:
                    overlay.add(n)
        print("命中并覆盖: %d 个类" % len(overlay))
        if not overlay:
            sys.exit("没找到任何补丁类，中止")

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

    with zipfile.ZipFile(OUT) as z:
        infos = z.infolist()
        dd = sum(1 for e in infos if e.flag_bits & 0x08)
        gv = z.read("com/gregtechceu/gtceu/api/GTValues.class")
    print("重建: %s  %d 字节" % (OUT, os.path.getsize(OUT)))
    print("  条目 %d，带 data descriptor %d" % (len(infos), dd))
    print("  GTValues 含 VA/VHA: %s %s" % (b"VA" in gv, b"VHA" in gv))
    if dd == 0:
        print("  [!] 警告：无 data descriptor，Mixin 可能读不了本 jar 的配置")


if __name__ == "__main__":
    main()
