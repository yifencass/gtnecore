#!/usr/bin/env python3
"""合成天文辅镜顶面贴图：plascrete 底 + overlay_front（alpha-over）→ top.png（全不透明）。
改底座贴图或 overlay 后重跑本脚本即可。"""
import io
import zipfile

from PIL import Image

JAR = 'build/patched/gtceu-1.20.1-7.5.3-patched.jar'
BASE_TEX = 'assets/gtceu/textures/block/casings/cleanroom/plascrete.png'
OVERLAY = 'src/main/resources/assets/gtceu/textures/block/astronomical_finderscope/overlay_front.png'
OUT = 'src/main/resources/assets/gtceu/textures/block/astronomical_finderscope/top.png'

with zipfile.ZipFile(JAR) as z:
    base = Image.open(io.BytesIO(z.read(BASE_TEX))).convert('RGBA')
overlay = Image.open(OVERLAY).convert('RGBA')

top = Image.alpha_composite(base, overlay)
semi = sum(1 for p in top.getdata() if p[3] < 255)
assert semi == 0, f'半透明残留 {semi} 像素'
top.save(OUT)
print(f'OK: {OUT} {top.size}')
