#!/usr/bin/env python3
"""
Revisión rápida del proyecto SIN compilar (no hace falta Android Studio ni Gradle).

Detecta los fallos más comunes antes de subir a GitHub:
  - XML mal formado
  - llaves o paréntesis desequilibrados en los .java
  - imports que sobran
  - referencias R.id / R.string / R.layout / R.drawable que no existen
  - métodos de Store usados pero no definidos
  - Activities del manifest sin clase, o que no heredan de BaseActivity
  - tabla de colores de países (assets/country_colors.js) inválida

Uso (desde la raíz del repositorio):   python3 tools/check_project.py
No sustituye a compilar: el compilador real es GitHub Actions.
"""
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "app/src/main/java/com/CraftPlay777/jps")
RES = os.path.join(ROOT, "app/src/main/res")
MANIFEST = os.path.join(ROOT, "app/src/main/AndroidManifest.xml")

errors = []
warnings = []


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def strip_java(src):
    """Quita literales, comentarios y caracteres para contar llaves sin falsos positivos."""
    s = re.sub(r"'(\\.|[^'\\])'", "''", src)
    s = re.sub(r'"(\\.|[^"\\])*"', '""', s)
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return re.sub(r"//.*", "", s)


# 1) XML bien formado
xml_files = glob.glob(os.path.join(RES, "**/*.xml"), recursive=True) + [MANIFEST]
for f in xml_files:
    try:
        ET.parse(f)
    except ET.ParseError as e:
        errors.append(f"XML mal formado: {os.path.relpath(f, ROOT)}: {e}")

# 2) Java: llaves, paréntesis e imports
java = {os.path.basename(f): read(f) for f in glob.glob(os.path.join(JAVA, "*.java"))}
for name, src in sorted(java.items()):
    s = strip_java(src)
    if s.count("{") != s.count("}"):
        errors.append(f"{name}: llaves desequilibradas ({s.count('{')} abren, {s.count('}')} cierran)")
    if s.count("(") != s.count(")"):
        errors.append(f"{name}: paréntesis desequilibrados")
    imports = re.findall(r"^import ([\w.]+);", src, flags=re.M)
    body = re.sub(r"^import .*$", "", s, flags=re.M)
    for imp in imports:
        if imp.split(".")[-1] not in body:
            warnings.append(f"{name}: import sin usar: {imp}")

# 3) Recursos referenciados
ids, layouts, drawables, strings = set(), set(), set(), set()
for f in glob.glob(os.path.join(RES, "layout*/*.xml")):
    ids |= set(re.findall(r"@\+id/(\w+)", read(f)))
    layouts.add(os.path.basename(f)[:-4])
for f in glob.glob(os.path.join(RES, "drawable*/*.xml")):
    drawables.add(os.path.basename(f)[:-4])
for f in glob.glob(os.path.join(RES, "values*/strings.xml")):
    strings |= set(re.findall(r'<string name="(\w+)"', read(f)))

used = {"id": set(), "string": set(), "layout": set(), "drawable": set()}
for src in java.values():
    for kind, name in re.findall(r"(?<![\w.])R\.(id|string|layout|drawable)\.(\w+)", src):
        used[kind].add(name)
for f in xml_files:
    t = read(f)
    used["string"] |= set(re.findall(r"@string/(\w+)", t))
    used["layout"] |= set(re.findall(r"@layout/(\w+)", t))
    used["drawable"] |= set(re.findall(r"@drawable/(\w+)", t))
for kind, known in (("id", ids), ("string", strings), ("layout", layouts), ("drawable", drawables)):
    for name in sorted(used[kind] - known):
        errors.append(f"Falta el recurso {kind}/{name}")

# 4) Métodos de Store
store_methods = set(re.findall(r"static\s+[\w<>\[\]]+\s+(\w+)\(", java.get("Store.java", "")))
for name, src in java.items():
    if name == "Store.java":
        continue
    for m in sorted(set(re.findall(r"Store\.(\w+)\(", src)) - store_methods):
        errors.append(f"{name}: Store.{m}() no existe")

# 5) Manifest
manifest = read(MANIFEST)
for act in re.findall(r'<activity[^>]*android:name="\.(\w+)"', manifest):
    cls = java.get(act + ".java")
    if cls is None:
        errors.append(f"Activity {act} está en el manifest pero no tiene clase")
    elif "extends BaseActivity" not in cls:
        warnings.append(f"{act} no hereda de BaseActivity (no seguirá el tema elegido)")

# 6) Tabla de colores de países (la leen Regions.java y globe.html)
try:
    text = read(os.path.join(ROOT, "app/src/main/assets/country_colors.js"))
    table = json.loads(text[text.index("{"): text.rindex("}") + 1])
    bad = [k for k, v in table.items() if not re.fullmatch(r"#[0-9A-Fa-f]{6}", v)]
    if bad:
        errors.append(f"country_colors.js: colores inválidos en {bad}")
except (OSError, ValueError) as e:
    errors.append(f"country_colors.js no se puede leer: {e}")

gradle = read(os.path.join(ROOT, "app/build.gradle"))
ver = re.search(r'versionName "([^"]+)"', gradle)
code = re.search(r"versionCode (\d+)", gradle)
print(f"Jps {ver.group(1) if ver else '?'} (versionCode {code.group(1) if code else '?'})")
print(f"Revisados: {len(java)} archivos Java, {len(xml_files)} XML")
for w in warnings:
    print("  aviso:", w)
for e in errors:
    print("  ERROR:", e)
print("RESULTADO:", "con errores" if errors else "sin errores")
sys.exit(1 if errors else 0)
