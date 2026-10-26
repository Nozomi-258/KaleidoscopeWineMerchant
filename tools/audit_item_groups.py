#!/usr/bin/env python3
"""分类一致性审计 —— 核对 WineGroups 的分类/定价参数与前置模组的真实配方。

## 它检查什么

`WineGroups.java` 里每种物品都带两个参数:

  * `hasQuality` —— 该物品是否有酿造品质(`brew_level` 组件)
  * `ingredients` —— 原料份数,用于「酒类分档」拓展功能的默认分档

这两个值如果和前置模组的实际配方不符,就会出现:
  * 有品质却标成无品质 → 按固定价收,品质高的酒卖亏了
  * 无品质却标成有品质 → 价格表里出现六个无效档位
  * 原料数错 → 「酒类分档」把酒分到错误的档

## 判定依据(从配方文件读,不猜)

  kaleidoscope_tavern:barrel   → 酒桶酿造,原料份数 = 流体(1) + ingredients 之和
  kaleidoscope_tavern:shaker   → 雪克杯调酒,原料份数 = ingredients 之和 + 酒(1)
  minecraft:crafting_*         → 普通合成,**无品质**

三种类型都会扫。早先版本漏扫 crafting,导致可乐/汤力水这类
「确实是普通合成、标 flat 正确」的物品被判成「无配方、无法判定」。

## 用法

  python audit_item_groups.py                         # 全部用默认路径
  python audit_item_groups.py --mods "E:\\mc\\mods"    # 指定模组目录
  python audit_item_groups.py --strict                # 有差异时以非 0 退出(可接进 CI)
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import sys
import zipfile

DEFAULT_MODS = os.environ.get("KWM_MODS_DIR", "")

# 已确认的合理差异:物品 ID -> 原因。报告里会显示但**不计入问题数**。
KNOWN_DIFFERENCES = {
    "kaleidoscope_tavern:molotov":
        "岩浆装瓶,零配料;它是武器不是酒,标 flat 正确",
}
# 鸡尾酒统一按 3 记录(雪克杯真实为 3 原料 + 1 份酒 = 4),这是有意的简化
COCKTAIL_SIMPLIFIED_ING = 3

ENTRY_RE = re.compile(
    r'\b(flat|i)\(\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"(?:\s*,\s*(\d+))?\s*\)')


def find_wine_groups(explicit: str | None) -> pathlib.Path:
    if explicit:
        p = pathlib.Path(explicit)
        if not p.is_file():
            sys.exit(f"找不到文件:{p}")
        return p
    here = pathlib.Path(__file__).resolve()
    for base in (here.parent.parent, here.parent.parent.parent):
        hits = list(base.glob("src/main/java/**/config/WineGroups.java"))
        if hits:
            return hits[0]
    sys.exit("自动定位 WineGroups.java 失败,请用 --wine-groups 指定")


def parse_ours(path: pathlib.Path) -> dict:
    src = path.read_text(encoding="utf-8")
    ours = {}
    for group in ("WINES", "COCKTAILS", "DRINKS"):
        # 贪婪匹配到最后一个 ");" —— 块尾形如 "3));"(双括号)
        m = re.search(r"List<Item> " + group + r" = List\.of\((.*)\);", src, re.S)
        if not m:
            print(f"警告:未匹配到 {group} 列表", file=sys.stderr)
            continue
        for kind, ns, p_, cn, ing in ENTRY_RE.findall(m.group(1)):
            ours[f"{ns}:{p_}"] = {
                "group": group,
                "cn": cn,
                "ourIng": int(ing) if ing else 1,
                "hasQuality": kind == "i",
            }
    return ours


def scan_recipes(mods_dir: pathlib.Path) -> dict:
    """扫描所有模组 jar,返回 id -> 配方信息。

    kind 取值:barrel / shaker / crafting
    """
    found: dict[str, dict] = {}
    for jar in sorted(mods_dir.glob("*.jar")):
        try:
            z = zipfile.ZipFile(jar)
        except Exception:
            continue
        for name in z.namelist():
            if "/recipe" not in name or not name.endswith(".json"):
                continue
            try:
                d = json.loads(z.read(name).decode("utf-8"))
            except Exception:
                continue
            rtype = str(d.get("type", ""))
            result = d.get("result")
            rid = result.get("id") if isinstance(result, dict) else None
            if not rid:
                continue
            ings = d.get("ingredients") or []
            if rtype == "kaleidoscope_tavern:barrel":
                n_ing = sum(int(i.get("count", 1)) for i in ings)
                info = {"kind": "barrel",
                        "fluid": str(d.get("fluid", "")).split(":")[-1],
                        "nIng": len(ings),
                        "total": (1 if d.get("fluid") else 0) + n_ing}
            elif "shaker" in rtype or "mixology" in rtype:
                info = {"kind": "shaker", "nIng": len(ings), "total": len(ings) + 1}
            elif "crafting" in rtype:
                info = {"kind": "crafting", "total": 0}
            else:
                continue
            # 同一物品可能有多条配方(不同机器);酿造类优先,信息量更大
            if rid not in found or found[rid]["kind"] == "crafting":
                found[rid] = info
    return found


def describe(r: dict) -> str:
    if r["kind"] == "barrel":
        return f"酒桶 流体={r['fluid']} +{r['nIng']}配料"
    if r["kind"] == "shaker":
        return f"雪克杯 {r['nIng']}原料+酒"
    return "普通合成(无品质)"


def main() -> int:
    ap = argparse.ArgumentParser(description="分类一致性审计")
    ap.add_argument("--mods", default=DEFAULT_MODS, help="模组目录(含前置模组 jar)")
    ap.add_argument("--wine-groups", default=None, help="WineGroups.java 的路径")
    ap.add_argument("--strict", action="store_true", help="有差异时返回非 0")
    args = ap.parse_args()

    wg = find_wine_groups(args.wine_groups)
    mods = pathlib.Path(args.mods)
    print(f"WineGroups : {wg}")
    print(f"模组目录   : {mods}")
    if not mods.is_dir():
        sys.exit(f"模组目录不存在:{mods}")

    ours = parse_ours(wg)
    found = scan_recipes(mods)
    kinds = {}
    for r in found.values():
        kinds[r["kind"]] = kinds.get(r["kind"], 0) + 1
    print(f"解析到 {len(ours)} 项  /  扫到 {len(found)} 个配方 "
          f"({'、'.join(f'{k} {v}' for k, v in sorted(kinds.items()))})")
    print()

    missing, wrong_flag, wrong_ing, known = [], [], [], []
    for iid, info in sorted(ours.items(), key=lambda kv: (kv[1]["group"], kv[0])):
        r = found.get(iid)
        if r is None:
            missing.append((info["group"], info["cn"], iid))
            continue

        expect_quality = r["kind"] in ("barrel", "shaker")
        flag_ok = expect_quality == info["hasQuality"]
        ing_ok = (r["total"] == info["ourIng"]) or (iid in KNOWN_DIFFERENCES)
        if info["group"] == "COCKTAILS" and info["ourIng"] == COCKTAIL_SIMPLIFIED_ING:
            ing_ok = True  # 有意的简化
        if not info["hasQuality"]:
            # 无品质物品的 ingredients 只是占位符(不参与定价、也不参与分档),
            # 拿它和配方比对没有意义(如可乐/汤力水是普通合成,配方里根本没有原料数组)。
            ing_ok = True

        if iid in KNOWN_DIFFERENCES:
            known.append((info["group"], info["cn"], iid, KNOWN_DIFFERENCES[iid]))
            continue
        if not flag_ok:
            wrong_flag.append((info["group"], info["cn"], iid, info["hasQuality"], describe(r)))
        if not ing_ok:
            wrong_ing.append((info["group"], info["cn"], iid, info["ourIng"], r["total"], describe(r)))

    def report(title: str, rows: list, fmt) -> None:
        print("=" * 74)
        print(f"{title}:{len(rows)} 项")
        print("=" * 74)
        for row in rows:
            print("  · " + fmt(row))
        if not rows:
            print("  无 ✓")
        print()

    report("品质标记与配方不符", wrong_flag,
           lambda r: f"[{r[0]}] {r[1]} ({r[2]})  我们 hasQuality={r[3]}  [{r[4]}]")
    report("原料份数与配方不符", wrong_ing,
           lambda r: f"[{r[0]}] {r[1]} ({r[2]})  我们={r[3]} 真实={r[4]}  [{r[5]}]")
    report("已确认的合理差异(不计入问题)", known,
           lambda r: f"[{r[0]}] {r[1]} ({r[2]}) —— {r[3]}")

    if missing:
        print("=" * 74)
        print(f"未找到 JSON 配方:{len(missing)} 项")
        print("=" * 74)
        print("  以下物品由前置模组用代码/流体逻辑产出,**本来就没有 JSON 配方文件**,")
        print("  无法用静态扫描判定,需在游戏内实测(或人工核对):")
        for g, cn, iid in missing:
            print(f"  ? [{g}] {cn} ({iid})")
        print()

    problems = len(wrong_flag) + len(wrong_ing)
    print(f"结论:{'全部一致 ✓' if problems == 0 else f'发现 {problems} 处差异'}")
    return 1 if (args.strict and problems) else 0


if __name__ == "__main__":
    raise SystemExit(main())
