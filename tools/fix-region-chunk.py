#!/usr/bin/env python3
"""从 .mca 区域文件中剔除指定区块（用于清除"保存必失败/导致卡死"的坏区块）。

Minecraft 区域文件（Anvil 格式）结构：
  扇区 0..1  头部：8192 字节，前 4096 为 1024 个 4 字节槽位
             （每槽 = 3 字节大端扇区偏移 off + 1 字节扇区数 cnt），后 4096 保留为 0
  扇区 off   数据块：(4 字节大端 length) + (1 字节压缩类型 comp) + (length-1 字节数据)
             数据块总长必须补 0 到 cnt*4096
  ⚠️ 扇区编号从 2 起 —— 头部占 2 个扇区。**这是本工具第一版写错的地方**：
     写成 header(4096)+body 会让所有区块错位一个扇区（表现为 length 变成天文数字）。

修复策略：整文件重建 —— 只写回头部登记且校验通过的非目标区块，扇区从 2 起紧凑重排。
写完后**自校验**：逐区块读回并与原数据逐字节比对，任一不一致即报错。

用法: python fix-region-chunk.py <region.mca> <chunkX> <chunkZ>
"""
import struct
import sys
import zlib

SECTOR = 4096
HEADER_SECTORS = 2


def parse_region(data):
    """返回 {index: (off, cnt)}（只含头部登记项）。"""
    entries = {}
    for i in range(1024):
        base = i * 4
        off = (data[base] << 16) | (data[base + 1] << 8) | data[base + 2]
        cnt = data[base + 3]
        if off != 0 and cnt != 0:
            entries[i] = (off, cnt)
    return entries


def read_chunk(data, off, cnt):
    """读取并解压一个区块，返回 (压缩类型, 未压缩数据)。失败抛异常。"""
    p = off * SECTOR
    if p + 5 > len(data):
        raise ValueError("header beyond EOF (off=%d)" % off)
    length = struct.unpack('>i', data[p:p + 4])[0]
    comp = data[p + 4]
    if length <= 1 or p + 4 + length > len(data):
        raise ValueError("bad length %d" % length)
    body = data[p + 5:p + 4 + length]
    if comp == 2:
        return comp, zlib.decompress(body)
    if comp == 1:
        return comp, zlib.decompress(body, -15)
    if comp == 3:
        return comp, body
    raise ValueError("unknown compression %d" % comp)


def build_region(chunks):
    """chunks: {index: (comp, raw)} → 完整区域文件 bytes（头部占 2 扇区）。"""
    header = bytearray(SECTOR * HEADER_SECTORS)
    body = bytearray()
    sector = HEADER_SECTORS
    for i in sorted(chunks):
        comp, raw = chunks[i]
        payload = zlib.compress(raw, 6) if comp == 2 else raw
        length = len(payload) + 1
        need = (length + 4 + SECTOR - 1) // SECTOR
        header[i * 4] = (sector >> 16) & 0xFF
        header[i * 4 + 1] = (sector >> 8) & 0xFF
        header[i * 4 + 2] = sector & 0xFF
        header[i * 4 + 3] = need
        block = struct.pack('>i', length) + bytes([comp]) + payload
        block += b'\x00' * (need * SECTOR - len(block))
        body += block
        sector += need
    return bytes(header) + bytes(body)


def self_check(out, chunks):
    """写回后逐区块读回比对，返回 (ok, failures)。"""
    failures = []
    for i, (comp, raw) in chunks.items():
        base = i * 4
        off = (out[base] << 16) | (out[base + 1] << 8) | out[base + 2]
        cnt = out[base + 3]
        try:
            comp2, raw2 = read_chunk(out, off, cnt)
            if raw2 != raw:
                failures.append((i, "content mismatch"))
        except Exception as e:
            failures.append((i, str(e)))
    return len(chunks) - len(failures), failures


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    path, cx, cz = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    target = (cx & 31) + (cz & 31) * 32

    src = open(path, 'rb').read()
    # 合法的区域文件允许尾部有不足一扇区的残留，所以不能要求 len % 4096 == 0
    usable = len(src) - (len(src) % SECTOR)
    if usable < SECTOR * 3:
        sys.exit("ERROR: 文件过小，不像是区域文件")

    entries = parse_region(src)
    print("源文件 %d 字节（可用 %d = %d 扇区），登记区块 %d 个"
          % (len(src), usable, usable // SECTOR, len(entries)))
    print("目标: chunk(%d,%d) 索引 %d，登记项 %s" % (cx, cz, target, entries.get(target)))

    chunks, skipped = {}, []
    for i, (off, cnt) in entries.items():
        if i == target:
            continue
        if off * SECTOR + 5 > usable:
            skipped.append((i, "扇区越界"))
            continue
        try:
            chunks[i] = read_chunk(src, off, cnt)
        except Exception as e:
            skipped.append((i, str(e)))
    print("读取成功 %d 个，失败 %d 个" % (len(chunks), len(skipped)))
    for i, e in skipped[:10]:
        print("   跳过索引 %d: %s" % (i, e))
    if skipped:
        sys.exit("ERROR: 有区块读取失败，为安全起见未写出（请先修好源文件）")

    out = build_region(chunks)

    ok, failures = self_check(out, chunks)
    print("自校验: %d/%d 区块读回一致" % (ok, len(chunks)))
    if failures:
        for f in failures[:10]:
            print("   失败 %s" % (f,))
        sys.exit("ERROR: 自校验未通过，未写出文件")

    open(path, 'wb').write(out)
    print("已写出 %d 字节 / %d 扇区，区块 %d 个（剔除 1 个，索引 %d）"
          % (len(out), len(out) // SECTOR, len(chunks), target))


if __name__ == '__main__':
    main()
