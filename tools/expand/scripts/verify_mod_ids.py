#!/usr/bin/env python3
"""Verify soft-compat item/block ids from ModFlavorMatchers + spec against profile jars.

Usage:
  python3 verify_mod_ids.py [--mods-dir PATH]

Default mods dir: CreateAERO profiles/my/mods
Exit 0 always unless --strict (then missing exact icon ids → exit 1).
"""
from __future__ import annotations

import argparse
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MATCHERS = ROOT / "neoforge/src/main/java/com/vnap/dialogue/ModFlavorMatchers.java"
SPEC_ID_MAP = Path(__file__).resolve().parents[1] / "out" / "id_map.json"
DEFAULT_MODS = Path(
    "/Users/s00d/Downloads/FireShot/CreateAERO 2.2/mods"
)
OUT = Path(__file__).resolve().parents[1] / "out" / "verify_mod_ids_report.md"


def jar_registry_ids(mods_dir: Path) -> tuple[set[str], set[str]]:
    """Returns (item/block-ish ids, modIds/namespaces present)."""
    ids: set[str] = set()
    namespaces: set[str] = set()
    if not mods_dir.is_dir():
        return ids, namespaces
    jars = list(mods_dir.glob("*.jar")) + list(mods_dir.glob("*.jar.disabled"))
    for jar in jars:
        try:
            with zipfile.ZipFile(jar) as zf:
                names = zf.namelist()
                for meta in (
                    "META-INF/neoforge.mods.toml",
                    "META-INF/mods.toml",
                    "fabric.mod.json",
                ):
                    if meta not in names:
                        continue
                    try:
                        raw = zf.read(meta).decode("utf-8", errors="ignore")
                    except Exception:
                        continue
                    for mid in re.findall(r'(?:modId|id)\s*=\s*"([^"]+)"', raw):
                        namespaces.add(mid)
                    for mid in re.findall(r'"id"\s*:\s*"([^"]+)"', raw):
                        namespaces.add(mid)
                for name in names:
                    m = re.match(r"assets/([^/]+)/models/item/(.+)\.json$", name)
                    if m:
                        namespaces.add(m.group(1))
                        ids.add(f"{m.group(1)}:{m.group(2).split('/')[-1]}")
                    m = re.match(r"assets/([^/]+)/blockstates/(.+)\.json$", name)
                    if m:
                        namespaces.add(m.group(1))
                        ids.add(f"{m.group(1)}:{m.group(2)}")
        except zipfile.BadZipFile:
            continue
    return ids, namespaces


def extract_icon_ids(java: str) -> list[str]:
    return sorted(set(re.findall(r'Map\.entry\(\s*"([a-z0-9_./]+:[a-z0-9_./]+)"', java)))


def extract_namespaces(java: str) -> list[str]:
    # Set.of("create", ...)
    found: set[str] = set()
    for block in re.findall(r"Set\.of\(\s*([^)]+)\)", java, flags=re.S):
        for ns in re.findall(r'"([a-z0-9_]+)"', block):
            found.add(ns)
    return sorted(found)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mods-dir", type=Path, default=DEFAULT_MODS)
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()

    java = MATCHERS.read_text(encoding="utf-8")
    icons = extract_icon_ids(java)
    namespaces = extract_namespaces(java)
    registry, present_ns = jar_registry_ids(args.mods_dir)
    jar_count = 0
    if args.mods_dir.is_dir():
        jar_count = len(list(args.mods_dir.glob("*.jar"))) + len(list(args.mods_dir.glob("*.jar.disabled")))

    lines = [
        "# verify_mod_ids",
        "",
        f"- mods_dir: `{args.mods_dir}`",
        f"- jars scanned: **{jar_count}**",
        f"- registry-ish ids found: **{len(registry)}**",
        f"- namespaces/modIds found: **{len(present_ns)}**",
        "",
        "## Namespaces (soft)",
        "",
    ]
    missing_ns = []
    for ns in namespaces:
        ok = ns in present_ns or any(p == ns or p.startswith(ns + "_") or ns.startswith(p) for p in present_ns)
        mark = "ok" if ok else "MISSING (soft — branch never fires)"
        if not ok:
            missing_ns.append(ns)
        lines.append(f"- `{ns}` — {mark}")

    lines += ["", "## Exact icon item ids", ""]
    missing_icons = []
    for item in icons:
        soft = item.split(":")[0] in present_ns
        if item in registry:
            lines.append(f"- `{item}` — exact")
        elif soft:
            lines.append(f"- `{item}` — namespace present (path may differ; runtime soft no-op if missing)")
            missing_icons.append(item)
        else:
            lines.append(f"- `{item}` — STALE (mod absent; soft no-op)")
            missing_icons.append(item)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT}")
    print(f"missing_ns={len(missing_ns)} uncertain_icons={len(missing_icons)}")
    if args.strict and (missing_ns or missing_icons):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
