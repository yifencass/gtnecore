#!/usr/bin/env python3
"""生成 dev 用 patched gtceu jar（deobf official 版，开发环境运行）。

输入 deobf gtceu（FG 反混淆产物，official 映射）+ official patch 类（patchJar 产物），
输出 build/patched/gtceu-1.20.1-7.5.3-patched.jar（主工程 implementation files 依赖）。

用法: python make-dev-patched.py <deobf-gtceu.jar> <official-patch.jar> <out.jar>
"""
import sys
import zipfile
import os

deobf, patch, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)

with zipfile.ZipFile(deobf) as src, zipfile.ZipFile(patch) as p:
    # 只取类条目——patch jar 自带的 manifest 只有 Manifest-Version，
    # 会覆盖 deobf jar 的 MixinConfigs 属性
    patch_entries = {n: p.read(n) for n in p.namelist() if n.endswith('.class')}
    if not patch_entries:
        sys.exit(f'ERROR: patch jar 没有类条目: {patch}')

    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as dst:
        for name in src.namelist():
            if name in patch_entries:
                dst.writestr(name, patch_entries[name])
            else:
                dst.writestr(name, src.read(name))

print(f'OK: {out}')
print(f'    patch 类覆盖 {len(patch_entries)} 个')
