#!/usr/bin/env python3
"""把太阳贴图转成灰度图，供人造恒星"一张图 + 调色盘染色"使用。

源图 obj模型/恒星/star_layer.png 实际是 JPEG（魔数 ffd8ffe1），4096x2048 equirectangular。
输出灰度 PNG 到 assets/gtnecore/textures/obj/ 下（与 demonic_source.png 同目录约定）。

灰度公式用 Rec.709 亮度权重（0.2126/0.7152/0.0722），比 PIL 的 convert("L") 更符合
人眼感知；同时不做对比度拉伸——保留原图动态范围，染色时才不会把高光压平。

用法:
  python tools/make-star-texture.py [--size 1024x512]
"""
import argparse
import os
import struct
import sys

from PIL import Image

SRC = os.path.join("obj模型", "恒星", "star_layer.png")
DST_DIR = os.path.join("src", "main", "resources", "assets", "gtnecore", "textures", "obj")
DST = os.path.join(DST_DIR, "star_gray.png")

# 预览图**不能**放在 assets 下：会被打进 jar，而它只是给人看的（1024x4096 约 5MB 死重）。
# 放到 tools/ 下，仓库内可查看，不参与打包。
PREVIEW = os.path.join("tools", "star_tint_preview.png")

# 调色盘：人造恒星各级的代表色（用于生成预览条，便于确认染色效果）
STAR_TINTS = [
    ("I 红矮星", (255, 120, 80)),
    ("II 橙矮星", (255, 170, 90)),
    ("III 黄矮星(G2V=太阳)", (255, 240, 200)),
    ("IV 红巨星", (255, 110, 70)),
    ("V 红超巨星", (255, 90, 60)),
    ("VI 蓝超巨星", (150, 190, 255)),
    ("VII 沃尔夫-拉叶星", (190, 210, 255)),
    ("VIII 中子星", (220, 235, 255)),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--size", default="1024x512", help="输出尺寸 WxH，默认 1024x512")
    ap.add_argument("--preview", action="store_true", help="同时输出染色预览条")
    args = ap.parse_args()

    w, h = (int(x) for x in args.size.lower().split("x"))

    if not os.path.exists(SRC):
        sys.exit("源图不存在: %s" % SRC)

    im = Image.open(SRC)
    print("源图: format=%s mode=%s size=%s" % (im.format, im.mode, im.size))
    im = im.convert("RGB")

    # 降采样（LANCZOS 保细节）
    im = im.resize((w, h), Image.LANCZOS)

    # Rec.709 亮度 → 灰度（保持 RGB 三通道，便于 GPU 直读）
    px = im.load()
    gray = Image.new("RGB", (w, h))
    gp = gray.load()
    lo, hi = 255, 0
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            lum = int(0.2126 * r + 0.7152 * g + 0.0722 * b + 0.5)
            if lum < lo:
                lo = lum
            if lum > hi:
                hi = lum
            gp[x, y] = (lum, lum, lum)

    os.makedirs(DST_DIR, exist_ok=True)
    gray.save(DST, "PNG", optimize=True)
    print("输出: %s  %dx%d  亮度范围 %d..%d" % (DST, w, h, lo, hi))

    # 直方图粗看：确认不是全白/全黑
    hist = gray.convert("L").histogram()
    total = sum(hist)
    print("亮度分布: <64=%.1f%%  64-128=%.1f%%  128-192=%.1f%%  >=192=%.1f%%" % (
        100.0 * sum(hist[:64]) / total,
        100.0 * sum(hist[64:128]) / total,
        100.0 * sum(hist[128:192]) / total,
        100.0 * sum(hist[192:]) / total))

    if args.preview:
        prev = Image.new("RGB", (w, h * len(STAR_TINTS)))
        for i, (name, c) in enumerate(STAR_TINTS):
            tinted = Image.new("RGB", (w, h))
            tp = tinted.load()
            for y in range(h):
                for x in range(w):
                    v = gp[x, y][0]
                    tp[x, y] = (v * c[0] // 255, v * c[1] // 255, v * c[2] // 255)
            prev.paste(tinted, (0, i * h))
        out = PREVIEW
        prev.save(out, "PNG", optimize=True)
        print("预览: %s（从上到下：%s）" % (out, " / ".join(n for n, _ in STAR_TINTS)))


if __name__ == "__main__":
    main()
