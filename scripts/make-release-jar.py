#!/usr/bin/env python3
"""生成发布用 patched gtceu jar（jarjar 嵌入版，顶层+嵌套全 srg）。

reobfJarJar 只重映射 gtnecore 自身类、不递归处理 META-INF/jarjar/ 里的嵌套 jar，
所以嵌入 jar 必须在 jarjar 之前就整体 srg 化：
- 顶层：maven 原始（srg）gtceu + reobf 后的 patch 类（覆盖同名条目）
- 嵌套：maven 原始（srg）gtceu 自带的 META-INF/jarjar/（Registrate/LDlib/mixinextras 等）

用法: python make-release-jar.py <srg-gtceu.jar> <srg-patch.jar> <out.jar>
"""
import sys
import zipfile
import os

srg, srg_patch, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)

with zipfile.ZipFile(srg) as src, zipfile.ZipFile(srg_patch) as p:
    # 只取类条目——patchJar 的 META-INF/MANIFEST.MF 只有
    # "Manifest-Version: 1.0"，会覆盖 srg 原始 jar 的
    # "MixinConfigs: gtceu.mixins.json"（FML 靠它注册 mixin，丢了整个
    # gtceu 的 mixin 不生效，报 ClassCastException: ...Accessor）
    patch_entries = {n: p.read(n) for n in p.namelist() if n.endswith('.class')}
    if not patch_entries:
        sys.exit(f'ERROR: patch jar 没有类条目: {srg_patch}')

    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as dst:
        for name in src.namelist():
            # patch 类覆盖 srg gtceu 顶层同名条目
            if name in patch_entries:
                dst.writestr(name, patch_entries[name])
            else:
                dst.writestr(name, src.read(name))

print(f'OK: {out}')
print(f'    patch 类覆盖条目: {sorted(patch_entries.keys())[:5]} ... 共 {len(patch_entries)} 个')
