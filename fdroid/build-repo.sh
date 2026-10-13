#!/usr/bin/env bash
# Builds the signed ZenPipe F-Droid repository from already signed release APKs.
# The APKs are NOT rebuilt or re-signed; only the repo index is signed with the separate
# repo key. Used by .github/workflows/fdroid-repo.yml and for local dry runs.
#
# Usage: fdroid/build-repo.sh <apk-dir> <out-dir>
#   <apk-dir>  directory with ZenPipe_v*.apk (+ optional .sha256 files from the releases)
#   <out-dir>  receives the web root: repo/, archive/, index.html, .nojekyll
# Environment:
#   FDROID_KEYSTORE           path to the repo key (PKCS12)
#   FDROID_KEYSTORE_PASSWORD  store + key password (ASCII only)
#   FDROID_KEY_ALIAS          key alias in the keystore
#   FDROID_CMD                optional, fdroid executable (default: fdroid)
#   ANDROID_HOME              Android SDK with build-tools (apksigner)
set -euo pipefail

APP_ID="io.github.sprobst76.zenpipe"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK_DIR="$(cd "${1:?apk-dir missing}" && pwd)"
OUT_DIR="${2:?out-dir missing}"
FDROID="${FDROID_CMD:-fdroid}"

for v in FDROID_KEYSTORE FDROID_KEYSTORE_PASSWORD FDROID_KEY_ALIAS; do
  if [ -z "${!v:-}" ]; then
    echo "error: $v is not set" >&2
    exit 1
  fi
done

if [ -e "$OUT_DIR" ] && [ -n "$(ls -A "$OUT_DIR")" ]; then
  echo "error: $OUT_DIR exists and is not empty" >&2
  exit 1
fi

WORK="$(mktemp -d)"
if [ -z "${KEEP_WORK:-}" ]; then trap 'rm -rf "${WORK:?}"' EXIT; else echo "work dir: $WORK"; fi

# 1. fdroidserver working tree: config, metadata (+ fastlane texts/icon/changelogs), key
cp "$ROOT/fdroid/config.yml" "$WORK/config.yml"
cp -r "$ROOT/fdroid/config" "$WORK/config"
mkdir -p "$WORK/metadata/$APP_ID" "$WORK/repo"
cp "$ROOT/fdroid/metadata/$APP_ID.yml" "$WORK/metadata/"
for locale_dir in "$ROOT"/fastlane/metadata/android/*/; do
  cp -r "$locale_dir" "$WORK/metadata/$APP_ID/"
done
# repo_icon/archive_icon in config.yml: copied by fdroid into repo/icons/ and archive/icons/
cp "$ROOT/fastlane/metadata/android/en-US/images/icon.png" "$WORK/zenpipe-icon.png"
cp "$FDROID_KEYSTORE" "$WORK/repo-keystore.p12"
chmod 600 "$WORK/config.yml" "$WORK/repo-keystore.p12"

# 2. APKs: check the release checksums, then copy into repo/. The file mtime becomes the
#    "added" date in the index (--use-date-from-apk), so callers set it to the release date.
shopt -s nullglob
apks=("$APK_DIR"/ZenPipe_v*.apk)
if [ ${#apks[@]} -eq 0 ]; then
  echo "error: no ZenPipe_v*.apk in $APK_DIR" >&2
  exit 1
fi
for apk in "${apks[@]}"; do
  if [ -f "$apk.sha256" ]; then
    (cd "$APK_DIR" && sha256sum --quiet -c "$(basename "$apk").sha256")
  fi
  cp -p "$apk" "$WORK/repo/"
done

# 3. lint metadata, build + sign the index. AllowedAPKSigningKeys in the metadata makes
#    'fdroid update' reject APKs that are not signed with the ZenPipe release key.
(
  cd "$WORK"
  "$FDROID" lint "$APP_ID"
  "$FDROID" update --use-date-from-apk --pretty
  # every APK must have ended up in the index (nothing silently skipped)
  for apk in "${apks[@]}"; do
    name="$(basename "$apk")"
    if ! grep -q "\"/$name\"" repo/index-v2.json archive/index-v2.json 2>/dev/null; then
      echo "error: $name is missing from the index" >&2
      exit 1
    fi
  done
)

# 4. web root: never publish config.yml, the key or the metadata sources
mkdir -p "$OUT_DIR"
cp -r "$WORK/repo" "$OUT_DIR/repo"
cp -r "$WORK/archive" "$OUT_DIR/archive"
# fdroid's run logs (tool paths, command line) are not needed by clients
rm -rf "${OUT_DIR:?}/repo/status" "${OUT_DIR:?}/archive/status"
touch "$OUT_DIR/.nojekyll"
cat > "$OUT_DIR/index.html" <<'HTML'
<!doctype html>
<meta charset="utf-8">
<title>ZenPipe F-Droid repository</title>
<meta http-equiv="refresh" content="0; url=repo/">
<p>ZenPipe F-Droid repository: <a href="repo/">repo/</a>
(source: <a href="https://github.com/sprobst76/ZenPipe">github.com/sprobst76/ZenPipe</a>)</p>
HTML
echo "repo written to $OUT_DIR"
