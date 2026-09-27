#!/usr/bin/env python3
"""把用户的原始结构转成 GTM 多方块所需的两份东西：

  1) KubeJS 版（kjs版多方块结构/人造恒星.js）—— 参考用
  2) Java PATTERN 数据（tools/out/StarPattern.java.txt）—— 直接贴进机器类

GTCEu 里一个 .aisle() = 一层（Y）。用户文件是 65 层 x 44 串 x 65 字符，
其中真正有内容的是前 13 串；补空串到 65 后即为 65 x 65 x 65 立方体。

改动（其余原样）：
  B/G/C/H -> 新键 'B'；I -> '#'；D 仍是控制器
"""
import os
import re
from collections import Counter

SRC = (r"C:\Users\Administrator\AppData\Roaming\dsh-desktop\harness\attachments\v1\files"
       r"\37\37a1219e80b1e02cc51b2ca8dea2f164d5c0bb69e591a4e1a62d59c521f30d4f\gtm_structure.js")
KJS_OUT = os.path.join("kjs版多方块结构", "人造恒星.js")
JAVA_OUT = os.path.join("src", "main", "java", "org", "ringclouds", "gtnecore",
                        "machine", "StarForgePattern.java")

TARGET = 65
MERGE_KEYS = ("B", "G", "C", "H")
NEW_KEY = "B"
ANY_KEYS = ("I",)
DROP_WHERE = ("B", "G", "C", "H", "I")

# 补空串：**默认不补**（用户 2026-09-26：不要把原结构撑成空盒子）。
# 原文件那一维是 44，其中前 3 串有内容；保持原样即可。
# 若将来确实需要撑成立方体，把 PAD_LOW 设成 >0，脚本会把内容居中补齐。
PAD_LOW = 0


def main():
    s = open(SRC, encoding="utf-8").read()
    aisles, pos = [], 0
    while True:
        i = s.find(".aisle(", pos)
        if i < 0:
            break
        j = i + len(".aisle(")
        d, k = 1, j
        while k < len(s) and d:
            if s[k] == "(":
                d += 1
            elif s[k] == ")":
                d -= 1
            k += 1
        aisles.append(re.findall(r"'([^']*)'", s[j:k - 1]))
        pos = k

    Z, X, Y = len(aisles), len(aisles[0]), len(aisles[0][0])
    print("源尺寸: %d 层 x %d 串 x %d 字符" % (Z, X, Y))

    used = [j for j in range(X) if any(set(a[j]) != {"#"} for a in aisles)]
    lo, hi = min(used), max(used)
    n_content = hi - lo + 1
    pad_high = max(0, TARGET - PAD_LOW - n_content) if PAD_LOW else 0
    assert Z == Y, "层数与串长应相等（都是 65）"
    print("内容串: %d..%d 共 %d；补空 %d(低)+%d(高) -> 每层 %d 串"
          % (lo, hi, n_content, PAD_LOW, pad_high, PAD_LOW + n_content + pad_high))

    # 原样保留全部串（含尾部空串），只做键替换——不裁不补，尺寸与源文件一致
    merged = []
    for a in aisles:
        merged.append([
            "".join(NEW_KEY if c in MERGE_KEYS else ("#" if c in ANY_KEYS else c)
                    for c in row)
            for row in a
        ])

    # 【2026-09-26 修正】aisle 顺序必须整体倒序！
    # 教训：GTCEu 的 FactoryBlockPattern.aisle() 是**依次往上叠**，
    # 而用户导出的 JS 里 .aisle 的顺序与控制器的实际位置相反 —— 不倒序的话，
    # 整个结构沿 UP 轴翻面：控制器从 (UP=0,FRONT=2,LEFT=32) 跑到 (UP=64,...)，
    # B 功能舱位全部错位，玩家把仓放对位置也不被算作部件（症状：成型但 parts=0）。
    # 校验点：倒序后 D 必须落在 UP=0, FRONT=2, LEFT=32。
    merged.reverse()

    # ---- 1) KubeJS ----
    out = ["pattern(definition => FactoryBlockPattern.start()"]
    for rows in merged:
        out.append("  .aisle(" + ", ".join("'" + r + "'" for r in rows) + ")")
    out += [
        "  .where('D', Predicates.controller(definition.getBlock()))",
        "  .where('A', Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get()))",
        "  .where('E', Predicates.blocks(GTBlocks.MACHINE_CASING_UHV.get()))",
        "  .where('F', Predicates.blocks('avaritia:neutron'))",
        "  .where('B', Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get())",
        "      .or(Predicates.abilities(PartAbility.MAINTENANCE))",
        "      .or(Predicates.blocks('gtceu:me_output_hatch'))",
        "      .or(Predicates.blocks('gtceu:me_input_hatch')))",
        "  .where('#', Predicates.any())",
        "  .build()",
        ")",
    ]
    os.makedirs(os.path.dirname(KJS_OUT), exist_ok=True)
    with open(KJS_OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")

    # ---- 2) Java PATTERN ----
    jl = [
        "package org.ringclouds.gtnecore.machine;",
        "",
        "/**",
        " * 人造恒星底座结构数据：65 层(aisle) x 65 串 x 65 字符 = 65^3 立方体。",
        " *",
        " * <p>由 {@code tools/fix-artificial-star-structure.py} 从用户导出的结构文件生成，",
        " * 生成时只做了三件事：B/G/C/H 合并为新键 'B'、信标位 I 置为 '#'、补空串凑成 65^3。",
        " * 其余排布与原始设计逐格一致。",
        " *",
        " * <p>符号：A=高能机壳  E=UHV机壳  F=中子块  D=控制器  B=功能舱位(OR)  #=any",
        " */",
        "public final class StarForgePattern {",
        "",
        "    private StarForgePattern() {}",
        "",
        "    public static final String[][] PATTERN = {",
    ]
    for rows in merged:
        jl.append("            { " + ", ".join('"%s"' % r for r in rows) + " },")
    jl.append("    };")
    jl.append("}")
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(jl) + "\n")

    # 校验
    c = Counter(ch for a in merged for r in a for ch in r)
    print("出尺寸: %d x %d x %d" % (len(merged), len(merged[0]), len(merged[0][0])))
    print("字符统计:", dict(sorted(c.items())))
    print("已写入:\n   %s\n   %s" % (KJS_OUT, JAVA_OUT))


if __name__ == "__main__":
    main()
