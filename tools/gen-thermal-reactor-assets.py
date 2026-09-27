#!/usr/bin/env python3
"""生成"热力反应釜"的机器模型 / 方块状态 / 物品模型 JSON。

设计（2026-09-25 定案）：
  前 / 后  = thermal_centrifuge 的 overlay_front（随 recipe_logic_status 切换）
  左 / 右  = thermal_centrifuge 的 overlay_side（随状态，**但该机器没有 _paused/_emissive 侧图**）
  顶面     = chemical_reactor 的 overlay_front（**固定，不随状态**）
  底       = 留空（模板默认 gtceu:block/void）

关键：**所有后缀都先探测该贴图是否真的存在**再拼。上一版盲目给侧面上 `_paused`，
而 thermal_centrifuge 只有 overlay_side / overlay_side_active / overlay_side_active_emissive，
于是模型报 "Missing textures ... overlay_side_paused"，进游戏就是缺贴图。

贴图来源：GTCEu jar 内 assets/gtceu/textures/block/machines/<machine>/*.png。
"""
import glob
import json
import os
import zipfile

TIERS = ["ulv", "lv", "mv", "hv", "ev", "iv", "luv", "zpm", "uv", "uhv"]

BASE = r"src\main\resources\assets\gtnecore"

TC_DIR = "block/machines/thermal_centrifuge/"
CR_DIR = "block/machines/chemical_reactor/"

# GTCEu jar 里资源前缀（探测用；模型里写的是 "gtceu:" + 上表路径）
_JAR_TEX = "assets/gtceu/textures/"


def load_available() -> set:
    """返回形如 'block/machines/thermal_centrifuge/overlay_side' 的可用贴图集合。"""
    jars = glob.glob(os.path.expanduser(
        r"~\.gradle\caches\modules-2\files-2.1\com.gregtechceu.gtceu\gtceu-1.20.1\7.5.3"
        r"\*\gtceu-1.20.1-7.5.3.jar"))
    if not jars:
        raise SystemExit("找不到 gtceu jar，无法探测贴图")
    z = zipfile.ZipFile(jars[0])
    out = set()
    for n in z.namelist():
        if n.startswith(_JAR_TEX) and n.endswith(".png"):
            out.add(n[len(_JAR_TEX):-4])
    return out


AVAILABLE = None


def tex(path_no_ns: str, *suffixes) -> str:
    """在候选后缀里挑第一个真实存在的；都不存在则退化为无后缀版本。"""
    base = path_no_ns
    for suf in suffixes:
        cand = base + suf
        if cand in AVAILABLE:
            return "gtceu:" + cand
    return "gtceu:" + base


def main() -> None:
    global AVAILABLE
    AVAILABLE = load_available()

    tc_front = TC_DIR + "overlay_front"          # gtceu:... 前缀在 tex() 里补
    tc_side = TC_DIR + "overlay_side"
    cr_front = CR_DIR + "overlay_front"

    # recipe_logic_status → (前脸后缀, 侧面后缀, 顶面后缀)
    # 后缀按"优先顺序"给，tex() 会挑第一个存在的
    statuses = [
        ("idle",    ("",),                      ("",),                      ("",)),
        ("working", ("_active", ""),            ("_active", ""),            ("_active", "")),
        ("waiting", ("_active", ""),            ("_active", ""),            ("_active", "")),
        ("suspend", ("_paused", ""),            ("_paused", ""),            ("_paused", "")),
    ]

    made = 0
    for tier in TIERS:
        name = "%s_thermal_reactor" % tier

        variants = {}
        for status, fsuf, ssuf, tsuf in statuses:
            front = tex(tc_front, *fsuf)
            side = tex(tc_side, *ssuf)
            top = tex(cr_front, *tsuf)

            textures = {
                "bottom": "gtceu:block/casings/voltage/%s/bottom" % tier,
                "side": "gtceu:block/casings/voltage/%s/side" % tier,
                "top": "gtceu:block/casings/voltage/%s/top" % tier,
                "overlay_front": front,
                "overlay_back": front,
                "overlay_side": side,
                "overlay_top": top,
            }
            # 发光层：也只在存在时写
            fe = tex(tc_front, *(s + "_emissive" for s in fsuf))
            if fe.endswith("_emissive"):
                textures["overlay_front_emissive"] = fe
                textures["overlay_back_emissive"] = fe
            se = tex(tc_side, *(s + "_emissive" for s in ssuf))
            if se.endswith("_emissive"):
                textures["overlay_side_emissive"] = se
            te = tex(cr_front, *(s + "_emissive" for s in tsuf))
            if te.endswith("_emissive"):
                textures["overlay_top_emissive"] = te

            variants["recipe_logic_status=%s" % status] = {"model": {
                "parent": "gtceu:block/casings/voltage/%s" % tier,
                "textures": textures,
            }}

        model = {
            "parent": "minecraft:block/block",
            "loader": "gtceu:machine",
            "machine": "gtnecore:%s" % name,
            "variants": variants,
        }
        for rel, data in (
            (("models", "block", "machine", name + ".json"), model),
            (("models", "item", name + ".json"),
             {"parent": "gtnecore:block/machine/%s" % name}),
        ):
            p = os.path.join(BASE, *rel)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, "w", encoding="utf-8", newline="\n") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
                f.write("\n")
            made += 1

        bs = {"variants": {}}
        for facing, y in (("north", None), ("east", 90), ("south", 180), ("west", 270)):
            entry = {"model": "gtnecore:block/machine/%s" % name}
            if y is not None:
                entry["y"] = y
            bs["variants"]["facing=%s" % facing] = entry
        p = os.path.join(BASE, "blockstates", name + ".json")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            json.dump(bs, f, ensure_ascii=False, indent=2)
            f.write("\n")
        made += 1

    print("生成/更新 %d 个 JSON（%d 档）" % (made, len(TIERS)))
    # 打印 LV 的解析结果，便于确认挑中的贴图
    p = os.path.join(BASE, "models", "block", "machine", "lv_thermal_reactor.json")
    with open(p, encoding="utf-8") as f:
        lv = json.load(f)
    for st in ("idle", "working", "suspend"):
        t = lv["variants"]["recipe_logic_status=%s" % st]["model"]["textures"]
        print("  LV/%-8s front=%-58s side=%-58s top=%s" % (
            st, t["overlay_front"].replace("gtceu:block/machines/", ""),
            t["overlay_side"].replace("gtceu:block/machines/", ""),
            t["overlay_top"].replace("gtceu:block/machines/", "")))


if __name__ == "__main__":
    main()
