#!/usr/bin/env bash
# Creates the signing key of the ZenPipe F-Droid REPOSITORY (signs only the repo index,
# NOT the app; the app keeps its release key zenpipe-release.p12).
# PKCS12, RSA 4096, ~27 years, store password == key password, ASCII only.
# The password is read interactively and handed to keytool via the environment, so it never
# appears in shell history, process arguments or logs.
# Run it yourself in a terminal: bash zen-docs/tools/create-fdroid-repo-keystore.sh
set -euo pipefail

DIR="$HOME/development/keystores"
KS="$DIR/zenpipe-fdroid-repo.p12"
ALIAS="zenpipe-repo"

JAVA_HOME="${JAVA_HOME:-$( (find "$HOME/.jdks" -maxdepth 1 -name 'jdk-21*' 2>/dev/null || true) | sort | head -n1)}"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

if [ -e "$KS" ]; then
  echo "Abbruch: $KS existiert bereits. Nichts wird ueberschrieben." >&2
  exit 1
fi

echo "Passwort fuer den F-Droid-Repo-Schluessel (Passwortmanager!)."
echo "Nur ASCII (keine Umlaute/Sonderzeichen ausserhalb ASCII), mindestens 16 Zeichen."
read -r -s -p "Passwort: " PW1; echo
read -r -s -p "Wiederholen: " PW2; echo
if [ "$PW1" != "$PW2" ]; then
  echo "Abbruch: Passwoerter verschieden." >&2
  exit 1
fi
if [ "${#PW1}" -lt 16 ]; then
  echo "Abbruch: Passwort kuerzer als 16 Zeichen." >&2
  exit 1
fi
if printf '%s' "$PW1" | LC_ALL=C grep -q '[^ -~]'; then
  echo "Abbruch: Passwort enthaelt Nicht-ASCII-Zeichen." >&2
  exit 1
fi
export FDROID_REPO_KS_PW="$PW1"
unset PW1 PW2

mkdir -p "$DIR"
chmod 700 "$DIR"

"$KEYTOOL" -genkeypair -v -storetype PKCS12 \
  -keystore "$KS" -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 \
  -dname "CN=ZenPipe F-Droid repo, O=sprobst76" \
  -storepass:env FDROID_REPO_KS_PW -keypass:env FDROID_REPO_KS_PW
chmod 600 "$KS"

FP="$("$KEYTOOL" -list -v -keystore "$KS" -alias "$ALIAS" -storepass:env FDROID_REPO_KS_PW \
  | grep -i 'SHA256:' | head -n1 | sed 's/.*SHA256: *//')"
unset FDROID_REPO_KS_PW

echo
echo "Fertig: $KS (Alias: $ALIAS)"
echo "Repo-Fingerprint (SHA-256, oeffentlich, kommt in README und Repo-URL):"
echo "  $FP"
echo "  ohne Doppelpunkte: $(echo "$FP" | tr -d ':')"
echo
echo "NAECHSTE SCHRITTE:"
echo " 1. Datei + Passwort im Passwortmanager ablegen, Kopie offline (USB-Stick)."
echo " 2. Secrets setzen: bash zen-docs/tools/set-fdroid-repo-secrets.sh"
