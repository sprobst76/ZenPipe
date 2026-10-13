#!/usr/bin/env bash
# Stores the F-Droid repo key as secrets of the GitHub environment "fdroid-repo"
# (read by .github/workflows/fdroid-repo.yml) and the public fingerprint as repository
# variable FDROID_REPO_FINGERPRINT. Values go to 'gh' via stdin, never as arguments.
# Run it yourself in a terminal (gh must be logged in with admin rights on the repo):
#   bash zen-docs/tools/set-fdroid-repo-secrets.sh [keystore] [alias]
set -euo pipefail

REPO="sprobst76/ZenPipe"
ENV_NAME="fdroid-repo"
KS="${1:-$HOME/development/keystores/zenpipe-fdroid-repo.p12}"
ALIAS="${2:-zenpipe-repo}"

JAVA_HOME="${JAVA_HOME:-$( (find "$HOME/.jdks" -maxdepth 1 -name 'jdk-21*' 2>/dev/null || true) | sort | head -n1)}"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

if [ ! -f "$KS" ]; then
  echo "Abbruch: $KS fehlt (erst zen-docs/tools/create-fdroid-repo-keystore.sh)." >&2
  exit 1
fi
if [[ "$(basename "$KS")" == *release* ]]; then
  echo "Abbruch: $KS sieht nach dem App-Release-Key aus. Der Repo-Key muss ein eigener sein." >&2
  exit 1
fi

read -r -s -p "Passwort des Repo-Keystores: " PW; echo
if printf '%s' "$PW" | LC_ALL=C grep -q '[^ -~]'; then
  echo "Abbruch: Passwort enthaelt Nicht-ASCII-Zeichen." >&2
  exit 1
fi
export FDROID_REPO_KS_PW="$PW"

# check password + alias before anything is uploaded
if ! LISTING="$("$KEYTOOL" -list -v -keystore "$KS" -alias "$ALIAS" -storepass:env FDROID_REPO_KS_PW 2>&1)"; then
  echo "Abbruch: Alias $ALIAS nicht gefunden oder Passwort falsch." >&2
  exit 1
fi
FP="$(printf '%s\n' "$LISTING" | grep -i 'SHA256:' | head -n1 | sed 's/.*SHA256: *//' | tr -d ':')"
if [ -z "$FP" ]; then
  echo "Abbruch: Alias $ALIAS nicht gefunden oder Passwort falsch." >&2
  exit 1
fi
echo "Repo-Fingerprint: $FP"

# create the environment if needed (no protection rules; secrets are only for this job)
gh api -X PUT "repos/$REPO/environments/$ENV_NAME" --silent

base64 -w0 "$KS" | gh secret set FDROID_KEYSTORE_BASE64 --env "$ENV_NAME" -R "$REPO"
printf '%s' "$PW" | gh secret set FDROID_KEYSTORE_PASSWORD --env "$ENV_NAME" -R "$REPO"
printf '%s' "$ALIAS" | gh secret set FDROID_KEY_ALIAS --env "$ENV_NAME" -R "$REPO"
unset PW FDROID_REPO_KS_PW
printf '%s' "$FP" | gh variable set FDROID_REPO_FINGERPRINT -R "$REPO"

echo
gh secret list --env "$ENV_NAME" -R "$REPO"
echo
echo "Fertig. Repo-URL fuer F-Droid-Clients:"
echo "  https://sprobst76.github.io/ZenPipe/repo?fingerprint=$FP"
