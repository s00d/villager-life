#!/usr/bin/env python3
"""Checks the Fabric shim's mixins of a version against that version's Minecraft before a run finds them one by one.

  python3 tools/dev/mixin_targets.py 1.21.4      (the node neoforge/versions/1.21.4-fabric, built: its generated sources)

For each mixin of villager_life.fabric.mixins.json (and the mod's own villager_life.mixins.json): the target class is
in the jar Loom made, every method a handler names is in it, every INVOKE/FIELD target an @At names is called in that
method. Prints what is missing; exit 1 if anything is. Reads the classes with javap (named, Mojang's names).
"""
import glob
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def jar_of(mc):
    jars = glob.glob(f"{ROOT}/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/{mc}*/minecraft-merged-*.jar")
    jars = [j for j in jars if re.search(rf"/{re.escape(mc)}(-loom[^/]*)?/", j)]
    if not jars:
        sys.exit(f"no Minecraft jar of {mc}: build the node first")
    return max(jars, key=os.path.getmtime)


CACHE = {}


def javap(jar, cls):
    if cls not in CACHE:
        out = subprocess.run(["javap", "-p", "-c", "-s", "-cp", jar, cls], capture_output=True, text=True)
        CACHE[cls] = out.stdout if out.returncode == 0 else None
    return CACHE[cls]


def methods(text):
    """name -> list of (descriptor, body) of every method in javap -c -s output."""
    found = {}
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^  (?:[\w$<>\[\],.? ]+ )?([\w$<>]+)\(.*\)(?: throws .*)?;$", line)
        if m and i + 1 < len(lines) and "descriptor:" in lines[i + 1]:
            name = m.group(1)
            if name == text.split("class ")[-1].split()[0].split(".")[-1]:
                name = "<init>"
            desc = lines[i + 1].split("descriptor:")[1].strip()
            body = []
            j = i + 2
            while j < len(lines) and lines[j].strip() != "":
                body.append(lines[j])
                j += 1
            found.setdefault(name, []).append((desc, "\n".join(body)))
            i = j
            continue
        if re.match(r"^  (?:[\w$<>\[\],.? ]+ )?[\w$]+\(.*\);$", line) and "static {}" not in line:
            pass
        if line.strip() == "static {};":
            j = i + 2
            body = []
            while j < len(lines) and lines[j].strip() != "":
                body.append(lines[j])
                j += 1
            found.setdefault("<clinit>", []).append(("()V", "\n".join(body)))
            i = j
            continue
        i += 1
    return found


def strip_comments(src):
    # active code only: Stonecutter leaves inactive branches inside /* ... */
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"(?m)^\s*//.*$", "", src)


def check(mc):
    node = f"{ROOT}/neoforge/versions/{mc}-fabric"
    gen = f"{node}/build/generated/stonecutter/main"
    if not os.path.isdir(gen):
        sys.exit(f"no generated sources in {gen}: run :neoforge:{mc}-fabric:compileJava first")
    jar = jar_of(mc)
    problems = []
    configs = [("com.vnap.fab.mixin", f"{node}/build/resources/main/villager_life.fabric.mixins.json"), ("com.vnap.mixin", f"{node}/build/resources/main/villager_life.mixins.json"), ("com.vnap.mixin", f"{node}/build/resources/main/villager_life.client.mixins.json")]
    for package, path in configs:
        if not os.path.exists(path):
            path = path.replace(f"{gen}/resources", f"{node}/build/resources/main")
        config = json.load(open(path))
        package = config.get("package", package)
        for name in config.get("mixins", []) + config.get("client", []) + config.get("server", []):
            src_path = f"{gen}/java/{package.replace('.', '/')}/{name.replace('.', '/')}.java"
            if not os.path.exists(src_path):
                problems.append(f"{name}: no source")
                continue
            src = strip_comments(open(src_path).read())
            problems += check_mixin(jar, name, src)
    # every mixin the shim has is in its config (a new one left out is never applied); the build drops some by version
    src_dir = f"{ROOT}/neoforge/src/main/java/com/vnap/fab/mixin"
    listed = json.load(open(f"{ROOT}/neoforge/src/main/resources/villager_life.fabric.mixins.json"))
    names = set(listed.get("mixins", []) + listed.get("client", []))
    for dirpath, _, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".java"):
                rel = os.path.relpath(os.path.join(dirpath, f[:-5]), src_dir).replace(os.sep, ".")
                if rel not in names:
                    problems.append(f"{rel}: not in villager_life.fabric.mixins.json")
    for p in problems:
        print(p)
    print(f"mixin_targets {mc}: {len(problems)} problems")
    return 1 if problems else 0


def imports(src):
    found = {}
    for m in re.finditer(r"^import ([\w.]+)\.(\w+);", src, re.M):
        found[m.group(2)] = m.group(1) + "." + m.group(2)
    return found


def resolve(simple, imp, package_hint=""):
    head = simple.split(".")[0]
    if head in imp:
        return imp[head] + simple[len(head):].replace(".", "$")
    if re.match(r"^[a-z]", simple):  # fully qualified
        parts = simple.split(".")
        for k, part in enumerate(parts):
            if part[:1].isupper():
                return ".".join(parts[:k + 1]) + "".join("$" + p for p in parts[k + 1:])
        return simple
    return None


def check_mixin(jar, name, src):
    problems = []
    imp = imports(src)
    m = re.search(r"@Mixin\((.*?)\)\s*(?:public )?(?:abstract )?(?:class|interface)", src, re.S)
    if not m:
        return [f"{name}: no @Mixin"]
    head = m.group(1)
    targets = [resolve(t, imp) for t in re.findall(r"([\w.]+)\.class", head)]
    targets += [t.replace("/", ".") for t in re.findall(r'targets\s*=\s*"([^"]+)"', head)]
    targets = [t for t in targets if t]
    for target in targets:
        if not target.startswith(("net.minecraft.", "com.mojang.")):
            continue  # another mod's class (Fabric API's): not in Minecraft's jar
        text = javap(jar, target)
        if text is None:
            problems.append(f"{name}: no class {target}")
            continue
        found = methods(text)
        for ann in re.finditer(r"@(Inject|WrapOperation|WrapMethod|ModifyReturnValue|ModifyExpressionValue|Redirect|ModifyVariable|ModifyArg|ModifyConstant)\((.*?)\)\s*(?:private|public|protected)", src, re.S):
            body = ann.group(2)
            meth = re.search(r"method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", body)
            if not meth or re.search(r"require\s*=\s*0", body):
                continue  # an optional injection may find nothing on a version
            for sel in re.findall(r'"([^"]+)"', meth.group(1)):
                mname, _, mdesc = sel.partition("(")
                mdesc = "(" + mdesc if mdesc else ""
                cands = [(d, b) for d, b in found.get(mname, []) if not mdesc or d == mdesc]
                if not cands:
                    problems.append(f"{name}: {target.split('.')[-1]}.{sel} missing")
                    continue
                for at in re.findall(r'target\s*=\s*"(L[^"]+)"', body):
                    owner, _, member = at[1:].partition(";")
                    mem_name = member.split("(")[0].split(":")[0]
                    mem_desc = member[len(mem_name):]
                    owner = owner.replace("$", "$")
                    hit = False
                    for d, b in cands:
                        for line in b.splitlines():
                            if (f"Method {owner}.{mem_name}:{mem_desc}" in line or f"InterfaceMethod {owner}.{mem_name}:{mem_desc}" in line
                                    or (owner.replace("/", ".").replace("$", ".") == target.replace("$", ".") and f"Method {mem_name}:{mem_desc}" in line)
                                    or f"Field {owner}.{mem_name}" in line
                                    or (owner.replace("/", ".").replace("$", ".") == target.replace("$", ".") and f"Field {mem_name}:" in line)):
                                hit = True
                    if not hit:
                        problems.append(f"{name}: {target.split('.')[-1]}.{sel} calls no {owner.split('/')[-1]}.{mem_name}{mem_desc}")
        problems += check_handlers(name, target, found, src)
        for sh in re.finditer(r"@Shadow\s*(?:@Final\s*)?(?:@Mutable\s*)?(?:private|protected|public)?\s*(static\s+)?(?:final\s+)?(?:abstract\s+)?([\w.<>?, \[\]@]+?)\s+(\w+)\s*[;(]", src):
            fname = sh.group(3)
            is_method = src[sh.end() - 1] == "("
            declared = (re.search(rf"[ .$]{re.escape(fname)}\(", text) if is_method else re.search(rf"(?m)^  [^(]*[ .]{re.escape(fname)};$", text))
            if not declared:
                problems.append(f"{name}: {target.split('.')[-1]} has no {fname}")
            elif not is_method:
                want = re.sub(r"<.*>", "", re.sub(r"@[\w.]+\s*", "", sh.group(2))).strip().split(".")[-1]
                after = text[declared.end():].lstrip("\n").splitlines()[0]
                desc = after.split("descriptor:")[-1].strip()
                have = descriptor_params("(" + desc + ")V")[0] if "descriptor:" in after else want
                if want != have:
                    problems.append(f"{name}: {target.split('.')[-1]}.{fname} is {have}, the shadow says {want}")
    return problems


PRIMS = {"I": "int", "J": "long", "Z": "boolean", "F": "float", "D": "double", "B": "byte", "S": "short", "C": "char", "V": "void"}


def descriptor_params(desc):
    """The simple type names of a method descriptor's parameters."""
    out = []
    inner = desc[1:desc.index(")")]
    i = 0
    while i < len(inner):
        dims = 0
        while inner[i] == "[":
            dims += 1
            i += 1
        if inner[i] == "L":
            j = inner.index(";", i)
            simple = re.split(r"[/$]", inner[i + 1:j])[-1]
            i = j + 1
        else:
            simple = PRIMS[inner[i]]
            i += 1
        out.append(simple + "[]" * dims)
    return out


def split_params(text):
    parts, depth, cur = [], 0, ""
    for ch in text:
        if ch in "<(":
            depth += 1
        elif ch in ">)":
            depth -= 1
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def java_type(param):
    """(simple type name, captured by @Local) of a handler's parameter."""
    local = "@Local" in param or "@Share" in param
    p = re.sub(r"@[\w.]+(\([^()]*\))?", "", param)
    while re.search(r"<[^<>]*>", p):
        p = re.sub(r"<[^<>]*>", "", p)
    p = p.replace("final ", "").strip()
    tokens = p.split()
    if len(tokens) < 2:
        return None, local
    t = tokens[-2]
    return t.split(".")[-1], local


def same(java, simple):
    return java == simple or (len(java.rstrip("[]")) == 1 and java[:1].isupper())


def check_handlers(name, target, found, src):
    problems = []
    for ann in re.finditer(r"@(Inject|WrapMethod|ModifyReturnValue)\((.*?)\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:private|public|protected)\s+(?:static\s+)?(?:<[^>]*>\s+)?[\w.<>?\[\], @]+?\s+(vnap\$\w+)\((.*?)\)\s*\{", src, re.S):
        kind, body, handler, params = ann.group(1), ann.group(2), ann.group(3), ann.group(4)
        meth = re.search(r"method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", body)
        if not meth:
            continue
        types = [java_type(p) for p in split_params(params)]
        types = [t for t, local in types if t is not None and not local]
        if kind == "Inject":
            cut = next((i for i, t in enumerate(types) if t in ("CallbackInfo", "CallbackInfoReturnable")), len(types))
            args = types[:cut]
        elif kind == "WrapMethod":
            args = [t for t in types if t != "Operation"]
        else:
            args = types[1:]
        for sel in re.findall(r'"([^"]+)"', meth.group(1)):
            mname, _, mdesc = sel.partition("(")
            mdesc = "(" + mdesc if mdesc else ""
            cands = [d for d, b in found.get(mname, []) if not mdesc or d == mdesc]
            if not cands:
                continue
            ok = False
            for d in cands:
                want = descriptor_params(d)
                if kind == "WrapMethod" and len(args) != len(want):
                    continue
                if len(args) > len(want):
                    continue
                if kind == "Inject" and args and len(args) != len(want):
                    continue
                if all(same(a, w) for a, w in zip(args, want)):
                    ok = True
            if not ok:
                problems.append(f"{name}: {handler}({', '.join(args)}) does not fit {target.split('.')[-1]}.{sel} {cands}")
    return problems


if __name__ == "__main__":
    sys.exit(check(sys.argv[1]))
