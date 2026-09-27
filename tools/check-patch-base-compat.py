#!/usr/bin/env python3
"""检查 patch 层引用的 GTCEu 成员在目标 GTCEu jar 里是否存在。

用途：把 patch 层（94 个类）叠到另一个版本的 GTCEu 官方 jar 上之前，先确认
引用的类/方法/字段都还在——版本不匹配会在运行时炸 NoSuchMethodError。

用法:
  python tools/check-patch-base-compat.py <patch.jar> <base-gtceu.jar>
"""
import struct
import sys
import zipfile

CONST_Utf8 = 1
CONST_Integer = 3
CONST_Float = 4
CONST_Long = 5
CONST_Double = 6
CONST_Class = 7
CONST_String = 8
CONST_Fieldref = 9
CONST_Methodref = 10
CONST_InterfaceMethodref = 11
CONST_NameAndType = 12
CONST_MethodHandle = 15
CONST_MethodType = 16
CONST_Dynamic = 17
CONST_InvokeDynamic = 18
CONST_Module = 19
CONST_Package = 20


def parse_pool(data):
    """返回 (constant_pool, this_class_index)。constant_pool 是 1-based 列表。"""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    count = struct.unpack_from(">H", data, 8)[0]
    pool = [None] * count
    i = 10
    idx = 1
    while idx < count:
        tag = data[i]
        i += 1
        if tag == CONST_Utf8:
            n = struct.unpack_from(">H", data, i)[0]
            i += 2
            pool[idx] = ("Utf8", data[i:i + n].decode("utf-8", "replace"))
            i += n
        elif tag in (CONST_Integer, CONST_Float):
            pool[idx] = (tag, data[i:i + 4])
            i += 4
        elif tag in (CONST_Long, CONST_Double):
            pool[idx] = (tag, data[i:i + 8])
            i += 8
            idx += 1  # 占两个槽
        elif tag in (CONST_Class, CONST_String, CONST_MethodType, CONST_Module, CONST_Package):
            pool[idx] = (tag, struct.unpack_from(">H", data, i)[0])
            i += 2
        elif tag in (CONST_Fieldref, CONST_Methodref, CONST_InterfaceMethodref,
                     CONST_NameAndType, CONST_Dynamic, CONST_InvokeDynamic):
            pool[idx] = (tag, struct.unpack_from(">HH", data, i))
            i += 4
        elif tag == CONST_MethodHandle:
            pool[idx] = (tag, struct.unpack_from(">BH", data, i))
            i += 3
        else:
            raise ValueError("unknown cp tag %d" % tag)
        idx += 1
    this_class = struct.unpack_from(">H", data, i + 2)[0]
    return pool, this_class


def utf8(pool, i):
    e = pool[i]
    return e[1] if e and e[0] == "Utf8" else None


def class_name(pool, i):
    e = pool[i]
    if not e or e[0] != CONST_Class:
        return None
    return utf8(pool, e[1])


def members(data):
    """返回 (methods:set[str name+desc], fields:set[str name+desc], super_name, interfaces)。"""
    pool, this_class = parse_pool(data)
    # 跳过 access_flags, this_class, super_class, interfaces
    p = 10
    count = struct.unpack_from(">H", data, 8)[0]
    i = 10
    idx = 1
    while idx < count:  # 重新走一遍求常量池结束位置
        tag = data[i]
        i += 1
        if tag == CONST_Utf8:
            n = struct.unpack_from(">H", data, i)[0]
            i += 2 + n
        elif tag in (CONST_Integer, CONST_Float):
            i += 4
        elif tag in (CONST_Long, CONST_Double):
            i += 8
            idx += 1
        elif tag in (CONST_Class, CONST_String, CONST_MethodType, CONST_Module, CONST_Package):
            i += 2
        elif tag in (CONST_Fieldref, CONST_Methodref, CONST_InterfaceMethodref,
                     CONST_NameAndType, CONST_Dynamic, CONST_InvokeDynamic):
            i += 4
        elif tag == CONST_MethodHandle:
            i += 3
        idx += 1
    i += 2  # access flags
    this_i = struct.unpack_from(">H", data, i)[0]
    i += 2
    super_name = class_name(pool, struct.unpack_from(">H", data, i)[0])
    i += 2
    n_if = struct.unpack_from(">H", data, i)[0]
    i += 2
    ifaces = [class_name(pool, struct.unpack_from(">H", data, i + 2 * k)[0]) for k in range(n_if)]
    i += 2 * n_if
    n_fields = struct.unpack_from(">H", data, i)[0]
    i += 2
    fields = set()
    for _ in range(n_fields):
        i += 2  # access
        name = utf8(pool, struct.unpack_from(">H", data, i)[0])
        i += 2
        desc = utf8(pool, struct.unpack_from(">H", data, i)[0])
        i += 2
        n_attr = struct.unpack_from(">H", data, i)[0]
        i += 2
        for _a in range(n_attr):
            ln = struct.unpack_from(">I", data, i + 2)[0]
            i += 6 + ln
        fields.add(name + desc)
    n_methods = struct.unpack_from(">H", data, i)[0]
    i += 2
    methods = set()
    for _ in range(n_methods):
        i += 2
        name = utf8(pool, struct.unpack_from(">H", data, i)[0])
        i += 2
        desc = utf8(pool, struct.unpack_from(">H", data, i)[0])
        i += 2
        n_attr = struct.unpack_from(">H", data, i)[0]
        i += 2
        for _a in range(n_attr):
            ln = struct.unpack_from(">I", data, i + 2)[0]
            i += 6 + ln
        methods.add(name + desc)
    return methods, fields, super_name, ifaces


def refs_of(data):
    """返回该 class 引用的 (owner, name+desc, kind)。"""
    pool, _ = parse_pool(data)
    out = []
    for e in pool:
        if not e:
            continue
        tag = e[0]
        if tag not in (CONST_Fieldref, CONST_Methodref, CONST_InterfaceMethodref):
            continue
        ci, nti = e[1]
        owner = class_name(pool, ci)
        nt = pool[nti]
        if not owner or not nt or nt[0] != CONST_NameAndType:
            continue
        name = utf8(pool, nt[1][0])
        desc = utf8(pool, nt[1][1])
        kind = "field" if tag == CONST_Fieldref else "method"
        out.append((owner, name + desc, kind))
    return out


def main():
    patch_jar, base_jar = sys.argv[1], sys.argv[2]
    pz = zipfile.ZipFile(patch_jar)
    bz = zipfile.ZipFile(base_jar)
    base_names = set(bz.namelist())

    # patch 层自己重写的类：这些类由 patch 覆盖，不依赖底座，跳过对它们自身成员的检查
    patched_classes = {n[:-len(".class")] for n in pz.namelist() if n.endswith(".class")}
    cache = {}

    def load_base(internal):
        if internal in cache:
            return cache[internal]
        p = internal + ".class"
        if p not in base_names:
            cache[internal] = None
            return None
        try:
            cache[internal] = members(bz.read(p))
        except Exception:
            cache[internal] = None
        return cache[internal]

    def has_member(owner, member, kind, seen=None):
        """沿父类/接口链查找成员；返回 (found, class_exists)。"""
        if seen is None:
            seen = set()
        if owner in seen:
            return False, True
        seen.add(owner)
        info = load_base(owner)
        if info is None:
            return False, False
        methods, fields, sup, ifaces = info
        if member in (fields if kind == "field" else methods):
            return True, True
        for parent in ([sup] if sup else []) + list(ifaces or []):
            if parent in ("java/lang/Object", "java/lang/Record", None):
                continue
            found, exists = has_member(parent, member, kind, seen)
            if found:
                return True, True
        return False, True

    missing_classes = {}
    missing_members = {}
    checked = 0
    for name in pz.namelist():
        if not name.endswith(".class") or "$" in name:
            continue
        data = pz.read(name)
        try:
            refs = refs_of(data)
        except Exception as ex:
            print("解析失败 %s: %s" % (name, ex))
            continue
        for owner, member, kind in refs:
            if not owner.startswith("com/gregtechceu/gtceu/"):
                continue
            if owner in patched_classes:
                # 该类由 patch 层重写，其成员由 patch 层提供，与底座版本无关
                continue
            checked += 1
            found, exists = has_member(owner, member, kind)
            if not exists:
                missing_classes.setdefault(owner, set()).add(name)
                continue
            if not found:
                missing_members.setdefault((owner, member, kind), set()).add(name)

    print("检查引用数: %d" % checked)
    print()
    if missing_classes:
        print("!! 目标 jar 缺少这些类 (%d):" % len(missing_classes))
        for c, users in sorted(missing_classes.items()):
            print("   %s   <- %s" % (c, ", ".join(sorted(users)[:3])))
    else:
        print("OK: 没有缺失的类")
    print()
    if missing_members:
        print("!! 目标 jar 的类里找不到这些成员 (%d):" % len(missing_members))
        for (owner, member, kind), users in sorted(missing_members.items()):
            print("   [%s] %s.%s   <- %s" % (kind, owner, member, ", ".join(sorted(users)[:2])))
    else:
        print("OK: 所有方法/字段引用都能在目标 jar 里找到")


if __name__ == "__main__":
    main()
