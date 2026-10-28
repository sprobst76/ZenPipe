#!/usr/bin/env bash
# Checks that every Zen hook in an upstream (NewPipe) file still exists, e.g. after
# `git rebase dev`. Hook list: zen-docs/tools/upstream-hooks.tsv
# (file <TAB> fixed grep string <TAB> description). Run from the repository root.
#
# Usage: zen-docs/tools/check-upstream-hooks.sh [--list]
#   --list   print the hook table (file, description) and exit.
# Exit code 1 if a hook is missing, a file is gone or the table is unreadable.
# Extra lines in the TSV: "#own<TAB>path" marks a Zen-owned file outside zen/ (no warning).
set -euo pipefail

TSV="zen-docs/tools/upstream-hooks.tsv"
SRC="app/src/main"

[ -f "$TSV" ] || { echo "ERROR: $TSV not found (run from the repository root)" >&2; exit 1; }

if [ "${1:-}" = "--list" ]; then
  grep -v '^#' "$TSV" | awk -F'\t' 'NF >= 3 { printf "%-90s %s\n", $1, $3 }'
  exit 0
fi

total=0
missing=0
while IFS=$'\t' read -r file pattern desc; do
  case "$file" in ''|'#'*) continue ;; esac
  total=$((total + 1))
  if [ ! -f "$file" ]; then
    echo "MISSING FILE: $file ($desc)"
    missing=$((missing + 1))
  elif ! grep -qF -e "$pattern" "$file"; then
    echo "MISSING HOOK: $file: $pattern ($desc)"
    missing=$((missing + 1))
  fi
done < "$TSV"

# Warn about upstream files with a "ZenPipe:" comment that are not listed in the table.
# Zen-owned files (zen/ package, basename Zen*/zen_*, "#own" lines) are skipped.
listed="$(grep -v '^#' "$TSV" | cut -f1 | sort -u)"
owned="$(grep '^#own' "$TSV" | cut -f2 | sort -u || true)"
unlisted=0
while IFS= read -r f; do
  case "$f" in "$SRC"/java/org/schabi/newpipe/zen/*|*/Zen*|*/zen_*) continue ;; esac
  grep -qxF -e "$f" <<<"$listed" && continue
  grep -qxF -e "$f" <<<"$owned" && continue
  echo "WARNING: not in $TSV: $f"
  unlisted=$((unlisted + 1))
done < <(grep -rlF "ZenPipe:" "$SRC" --include='*.java' --include='*.kt' --include='*.xml' | sort)

if [ "$missing" -gt 0 ]; then
  echo "FAILED: $((total - missing))/$total hooks found, $missing missing"
  exit 1
fi
echo "OK $total/$total"
[ "$unlisted" -eq 0 ] || echo "($unlisted unlisted file(s) with a ZenPipe: comment, see warnings)"
