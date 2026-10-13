#!/usr/bin/env python3
"""Build the pause quote resources from zen-docs/sprueche.md (the single source).

Usage: python3 zen-docs/gen_sprueche.py
Writes app/src/main/res/values/zen_quotes.xml (English) and values-de/zen_quotes.xml (German).
Each table row "| Nr | Deutsch | English | Anzeige DE | Anzeige EN | Beleg |" becomes one item;
a non-empty attribution is appended on its own line as "— <attribution>".
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "zen-docs" / "sprueche.md"
RES = ROOT / "app" / "src" / "main" / "res"
ROW = re.compile(r"^\|\s*([QEA]\d+)\s*\|(.*)\|\s*$")


def escape(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if text.startswith(("@", "?")):
        text = "\\" + text
    return text


def build(rows, text_col, attr_col, header):
    items = []
    for row in rows:
        text = row[text_col].strip()
        attr = row[attr_col].strip()
        value = escape(text) + (("\\n— " + escape(attr)) if attr else "")
        items.append(f"        <item>{value}</item>")
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {header} Generated from zen-docs/sprueche.md by zen-docs/gen_sprueche.py, do not edit. -->\n"
        '<resources>\n    <string-array name="zen_quotes">\n'
        + "\n".join(items)
        + "\n    </string-array>\n</resources>\n"
    )


def main():
    rows = []
    for line in SRC.read_text(encoding="utf-8").splitlines():
        m = ROW.match(line)
        if not m:
            continue
        cells = [c for c in m.group(2).split("|")]
        if len(cells) != 5:
            raise SystemExit(f"{m.group(1)}: expected 6 columns, got {len(cells) + 1}")
        rows.append(cells)
    if len(rows) < 10:
        raise SystemExit("too few rows parsed")
    (RES / "values" / "zen_quotes.xml").write_text(build(rows, 1, 3, "Pause quotes (English)."), encoding="utf-8")
    (RES / "values-de").mkdir(exist_ok=True)
    (RES / "values-de" / "zen_quotes.xml").write_text(build(rows, 0, 2, "Pause quotes (German)."), encoding="utf-8")
    print(f"{len(rows)} quotes written")


if __name__ == "__main__":
    main()
