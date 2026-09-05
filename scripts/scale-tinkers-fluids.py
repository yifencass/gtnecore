#!/usr/bin/env python3
"""批量缩放匠魂（tconstruct）配方流体量：×1.6（90→144，对齐 GT 144mB/锭经济）。

读取 deobf tconstruct jar 的 smeltery/ 与 tools/materials/casting/ 全部含流体量
的 JSON，把所有 amount 字段 ×1.6，写到 src/main/resources/data/tconstruct/recipes/
同路径（数据包同 ID 覆盖，需 mods.toml 的 tconstruct ordering=AFTER）。

前置验证：所有值均为 5 的倍数，×1.6=×8/5 恒为整数（2026-08-27 全量扫描确认）。
匠魂版本变化后重跑前应先重验整除性（脚本内置断言）。
"""
import json
import os
import sys
import zipfile

JAR = r'C:\Users\Administrator\.gradle\caches\forge_gradle\deobf_dependencies\curse\maven\tinkers-construct-74072\7449219_mapped_official_1.20.1\tinkers-construct-74072-7449219_mapped_official_1.20.1.jar'
OUT = 'src/main/resources'
SCOPES = ('data/tconstruct/recipes/smeltery/', 'data/tconstruct/recipes/tools/materials/casting/')
SCALE = 1.6


def scale_amounts(obj):
    changed = False
    if isinstance(obj, dict):
        if isinstance(obj.get('amount'), int):
            v = obj['amount'] * SCALE
            assert v == int(v), f'非整数缩放: {obj["amount"]} × {SCALE}'
            obj['amount'] = int(v)
            changed = True
        for value in obj.values():
            changed = scale_amounts(value) or changed
    elif isinstance(obj, list):
        for value in obj:
            changed = scale_amounts(value) or changed
    return changed


def main():
    written = 0
    with zipfile.ZipFile(JAR) as z:
        for name in z.namelist():
            if not name.endswith('.json') or not name.startswith(SCOPES):
                continue
            data = json.loads(z.read(name).decode('utf-8'))
            if not scale_amounts(data):
                continue
            path = os.path.join(OUT, name)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write('\n')
            written += 1
    print(f'OK: 缩放并写入 {written} 个覆盖配方')


if __name__ == '__main__':
    sys.exit(main())
