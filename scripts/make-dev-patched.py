#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 dev 用 patched gtceu jar（deobf official 版，开发环境运行）。

输入 deobf gtceu（FG 反混淆产物，official 映射）+ official patch 类（patchJar 产物），
输出 build/patched/gtceu-1.20.1-7.5.3-patched.jar（主工程 implementation files 依赖）。

用法: python make-dev-patched.py <deobf-gtceu.jar> <official-patch.jar> <out.jar>

======================================================================
2026-09-27 修正：写回时必须沿用原始 ZipInfo（含 general purpose bit flag）
======================================================================
症状（Mixin 启动即崩）：
    MixinInitialisationError: Error initialising mixin config gtceu.mixins.json
    Caused by: IllegalArgumentException:
        The specified resource 'gtceu.mixins.json' was invalid or could not be read
而配置内容本身完全正常：JSON 合法、引用的类齐全、refmap 在、Java 的 ZipFile 也能读。

根因：旧版写 `dst.writestr(name, data)`（只传文件名），**丢掉了原 jar 条目的元数据**，
其中 general purpose bit flag 的 data-descriptor 位（0x08）是 Mixin 读资源所依赖的。
实测对照（同一个 mixins.json，内容一字不差）：
    maven 原始 jar            14426/14426 条目带 data descriptor  -> 可读
    旧版 Python 产物             0/14426                         -> Mixin 读不了
    JDK ZipOutputStream 产物     0/14426                         -> 同样读不了
    JDK ZipFile 拷贝（release 链）14426/14426                    -> 正常

修法：用原始 info 造 ZipInfo（date_time / external_attr / create_system），
**并显式把 flag_bits 设回来**——Python 的 writestr 会把 flag 清成 0，
必须在 writestr 之后对 filelist 里那条再兜一次。
生成后会自检 data descriptor 数量，不为 0 才打印 OK。
"""
import os
import sys
import zipfile

if len(sys.argv) != 4:
    sys.exit("用法: make-dev-patched.py <deobf-gtceu.jar> <official-patch.jar> <out.jar>")

deobf, patch, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)

with zipfile.ZipFile(deobf) as src, zipfile.ZipFile(patch) as p:
    # 只取类条目 —— patch jar 自带的 manifest 只有 Manifest-Version，
    # 会覆盖 deobf jar 的 MixinConfigs 属性（GTCEu mixin 被发现的依据之一）
    patch_entries = {n: p.read(n) for n in p.namelist() if n.endswith('.class')}
    if not patch_entries:
        sys.exit(f'ERROR: patch jar 没有类条目: {patch}')

    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            name = info.filename
            data = patch_entries.get(name)
            if data is None:
                data = src.read(name)
            ni = zipfile.ZipInfo(name, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            ni.internal_attr = info.internal_attr
            ni.create_system = info.create_system
            ni.flag_bits = info.flag_bits
            dst.writestr(ni, data)
            # writestr 会把 flag 清 0，这里兜回来（关键）
            dst.filelist[-1].flag_bits = info.flag_bits

with zipfile.ZipFile(out) as check:
    infos = check.infolist()
    dd = sum(1 for e in infos if e.flag_bits & 0x08)

print(f'OK: {out}')
print(f'    patch 类覆盖 {len(patch_entries)} 个，条目 {len(infos)}')
print(f'    带 data descriptor 的条目 {dd} / {len(infos)}')
if dd == 0:
    print('    [!] 警告：没有任何条目带 data descriptor —— Mixin 可能读不了本 jar 的配置')
    sys.exit(1)
