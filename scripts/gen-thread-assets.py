#!/usr/bin/env python3
"""生成线程机制资产（阶段 1）：
- 27 台线程仓（3 品位 × LV..UHV 九档）的 blockstate + 机器模型 JSON
- 3 个品位 part overlay 模型 + 6 张 16×16 overlay 贴图（PIL）
- 线程熔炉测试机的 blockstate + 模型
- lang 键并入 en_us / zh_cn
运行：python scripts/gen-thread-assets.py
"""
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/gtnecore"

TIERS = ["lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv"]  # LV(1)..UHV(9)
GRADES = {  # suffix -> 中文品位名
    "thread_hatch": "线程仓",
    "split_thread_hatch": "均分线程仓",
    "mult_thread_hatch": "倍频线程仓",
}

# ---------- 1. 27 仓 blockstate（facing×6 旋转） ----------
bs_template = {
    "variants": {
        "facing=down": {"model": "gtnecore:block/machine/{id}", "x": 90},
        "facing=up": {"model": "gtnecore:block/machine/{id}", "x": 270},
        "facing=north": {"model": "gtnecore:block/machine/{id}"},
        "facing=south": {"model": "gtnecore:block/machine/{id}", "y": 180},
        "facing=west": {"model": "gtnecore:block/machine/{id}", "y": 270},
        "facing=east": {"model": "gtnecore:block/machine/{id}", "y": 90},
    }
}

# ---------- 2. 27 仓机器模型（loader + is_formed 双变体 + 档位壳） ----------
def machine_model_json(machine_id: str, overlay_parent: str, tier: str) -> dict:
    variants = {}
    for formed in ("false", "true"):
        variants[f"is_formed={formed}"] = {
            "model": {
                "parent": f"gtnecore:block/machine/part/{overlay_parent}",
                "textures": {
                    "bottom": f"gtceu:block/casings/voltage/{tier}/bottom",
                    "side": f"gtceu:block/casings/voltage/{tier}/side",
                    "top": f"gtceu:block/casings/voltage/{tier}/top",
                },
            }
        }
    return {
        "parent": "minecraft:block/block",
        "loader": "gtceu:machine",
        "machine": f"gtnecore:{machine_id}",
        "variants": variants,
    }

# ---------- 3. 品位 part overlay 模型 ----------
part_overlay = {
    "parent": "gtceu:block/overlay/2_layer/front_emissive",
    "textures": {
        "overlay": "gtnecore:block/overlay/machine/overlay_thread_{g}",
        "overlay_emissive": "gtnecore:block/overlay/machine/overlay_thread_{g}_emissive",
    },
}

# ---------- 贴图（PIL） ----------
try:
    from PIL import Image, ImageDraw
except ImportError:
    raise SystemExit("需要 PIL: pip install pillow")

GRADE_COLORS = {  # 品位 -> (normal, emissive)
    "thread_hatch": ((0x4F, 0xB8, 0xFF), (0x8F, 0xD9, 0xFF)),      # 青蓝
    "split_thread_hatch": ((0x4F, 0xE0, 0x8F), (0x9A, 0xF0, 0xC0)),  # 绿
    "mult_thread_hatch": ((0xC4, 0x7F, 0xFF), (0xE2, 0xC4, 0xFF)),   # 紫
}


def draw_overlay(path: Path, color: tuple, emissive: bool) -> None:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 中央三条竖线（线程示意）；emissive 画成实心同色（自发光层）
    base = 2 if emissive else 3  # 普通层细线、发光层实条，视觉叠出"灯条"
    for x in (5, 7, 9):
        d.rectangle([x - base // 2, 2, x + base // 2, 13], fill=color + (255,))
    # 底部横线（接地/并行槽位）
    d.rectangle([3, 12, 12, 14], fill=color + (255,))
    img.save(path)


os.makedirs(ASSETS / "blockstates", exist_ok=True)
os.makedirs(ASSETS / "models/block/machine/part", exist_ok=True)
os.makedirs(ASSETS / "models/block/machine", exist_ok=True)
os.makedirs(ASSETS / "textures/block/overlay/machine", exist_ok=True)

zh_keys = {}
en_keys = {}

for grade_suffix, zh_name in GRADES.items():
    # part overlay 模型
    (ASSETS / f"models/block/machine/part/{grade_suffix}.json").write_text(
        json.dumps({**part_overlay, "textures": {
            k: v.format(g=grade_suffix) for k, v in part_overlay["textures"].items()}},
            indent=2), encoding="utf-8")
    # 贴图（normal + emissive）
    normal, emissive = GRADE_COLORS[grade_suffix]
    draw_overlay(ASSETS / f"textures/block/overlay/machine/overlay_thread_{grade_suffix}.png", normal, False)
    draw_overlay(ASSETS / f"textures/block/overlay/machine/overlay_thread_{grade_suffix}_emissive.png", emissive, True)
    for tier in TIERS:
        mid = f"{tier}_{grade_suffix}"
        (ASSETS / f"blockstates/{mid}.json").write_text(
            json.dumps({k: {kk: {kkk: vv.format(id=mid) if isinstance(vv, str) else vv
                                 for kkk, vv in v.items()} for kk, v in val.items()}
                        for k, val in bs_template.items()}, indent=2), encoding="utf-8")
        (ASSETS / f"models/block/machine/{mid}.json").write_text(
            json.dumps(machine_model_json(mid, grade_suffix, tier), indent=2), encoding="utf-8")
        zh_keys[f"block.gtnecore.{mid}"] = f"{tier.upper()} {zh_name}"
        en_keys[f"block.gtnecore.{mid}"] = f"{tier.upper()} {zh_name}"

# ---------- 线程熔炉测试机 ----------
tier = "hv"
tf_bs = {"variants": {"": {"model": "gtnecore:block/machine/thread_furnace"}}}
(ASSETS / "blockstates/thread_furnace.json").write_text(json.dumps(tf_bs, indent=2), encoding="utf-8")

tf_variants = {}
for formed in ("false", "true"):
    for status in ("idle", "suspend", "waiting", "working"):
        tf_variants[f"is_formed={formed},recipe_logic_status={status}"] = {
            "model": {"parent": "gtceu:block/casings/solid/machine_casing_solid_steel"}}
(ASSETS / "models/block/machine/thread_furnace.json").write_text(json.dumps({
    "parent": "minecraft:block/block",
    "loader": "gtceu:machine",
    "machine": "gtnecore:thread_furnace",
    "variants": tf_variants,
}, indent=2), encoding="utf-8")

zh_keys.update({
    "block.gtnecore.thread_furnace": "线程熔炉",
    "block.gtnecore.thread_furnace.tooltip": "线程机制测试机（阶段 1）· 熔炼配方",
    "gtnecore.machine.thread_furnace.threads": "线程: %s 个, 每线程并行 ×%s",
    "gtnecore.machine.thread_furnace.active": "活动槽: %s/%s",
    "gtnecore.machine.thread_hatch.tooltip.0": "为多方块提供线程: 可同时运行多种配方 (右键调线程数)",
    "gtnecore.thread_grade.normal": "线程仓: 与并行仓相斥, 每线程并行 ×1",
    "gtnecore.thread_grade.split": "均分: 线程均分机器并行数",
    "gtnecore.thread_grade.mult": "倍频: 每线程独享机器全部并行数",
})
en_keys.update({
    "block.gtnecore.thread_furnace": "Thread Furnace",
    "block.gtnecore.thread_furnace.tooltip": "Thread mechanism test machine (phase 1) · smelting",
    "gtnecore.machine.thread_furnace.threads": "Threads: %s, parallel per thread ×%s",
    "gtnecore.machine.thread_furnace.active": "Active slots: %s/%s",
    "gtnecore.machine.thread_hatch.tooltip.0": "Provides threads to a multiblock: run multiple recipes at once (right-click to adjust)",
    "gtnecore.thread_grade.normal": "Thread hatch: exclusive with parallel hatch, parallel ×1 per thread",
    "gtnecore.thread_grade.split": "Split: divides machine parallel among threads",
    "gtnecore.thread_grade.mult": "Mult: each thread gets full machine parallel",
})


def merge_lang(lang_file: str, keys: dict) -> None:
    path = ASSETS / f"lang/{lang_file}"
    data = json.loads(path.read_text(encoding="utf-8"))
    data.update(keys)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


merge_lang("zh_cn.json", zh_keys)
merge_lang("en_us.json", en_keys)

print(f"生成完成: blockstates/models/贴图 × {len(TIERS) * len(GRADES)} 仓 + 线程熔炉;lang 已并入")
