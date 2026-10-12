#!/bin/bash
# <Projekt> — Android-Dev-Loop starten (Kotlin/Compose, tmux)
#
# Anders als bei Web-Stacks gibt es hier keinen HTTP-Service zum Health-Pollen —
# der Loop ist Build → Install → Logcat beobachten → Code ändern → wiederholen.
# Nutzt adb-device-check.sh wieder (nicht duplizieren!), um das historisch größte
# Android-Problem zu vermeiden: Claude/Terminal "blind" gegenüber Gerätezustand
# (8h-Stall bei gesperrtem Gerät, siehe reflection-notes.md Kandidat 5).
#
# Für den RELEASE-Vorgang (Tag, versionCode, Play-Store-Upload) NICHT dieses
# Skript verwenden — siehe stattdessen android-release-preflight.sh / Skill
# /android-release. Dieses Skript ist nur für den täglichen Dev-Loop.
#
# Nutzung: ./dev.sh starten, ./stop.sh beendet nur die tmux-Session
#          (kein Hintergrundprozess auf dem Rechner zu killen — die App läuft
#          auf dem Gerät, nicht lokal).

set -uo pipefail

# ============================== CONFIG ======================================
SESSION="zenpipe"
PROJECT_DIR="$HOME/development/ZenPipe"

PACKAGE="io.github.sprobst76.zenpipe.debug"   # applicationId (debug build)
MAIN_ACTIVITY="org.schabi.newpipe.MainActivity"   # namespace stays org.schabi.newpipe
WIFI_ADDR=""                           # z.B. "192.168.0.74:38949" — leer = nur USB/bereits verbundenes Gerät

FOCUS_WINDOW="claude"
ADB_CHECK="$HOME/development/WorkflowAnalyser/scripts/adb-device-check.sh"
# =============================================================================

if [ -n "${TMUX:-}" ]; then
    echo "⚠  Du bist bereits in einer tmux-Session."
    read -p "Trotzdem neue Session erstellen? (j/n) " -n 1 -r; echo ""
    [[ ! $REPLY =~ ^[Jj]$ ]] && exit 0
    unset TMUX
fi

if tmux has-session -t "$SESSION" 2>/dev/null; then
    echo "Session '$SESSION' existiert bereits. Attaching..."
    tmux attach -t "$SESSION"
    exit 0
fi

echo "== Gerätestatus =="
if [ -n "$WIFI_ADDR" ]; then
    "$ADB_CHECK" "$WIFI_ADDR"
else
    "$ADB_CHECK"
fi
DEVICE_STATUS=$?
if [ "$DEVICE_STATUS" -ne 0 ]; then
    echo ""
    echo "Kein Gerät verfügbar — Session startet trotzdem (Fenster 'device' wartet selbst)."
fi
echo ""

# --- Fenster 0: device (Logcat, sobald der Prozess läuft) ---
tmux new-session -d -s "$SESSION" -n 'device' -c "$PROJECT_DIR"
tmux send-keys -t "$SESSION:device" "bash -c '
echo \"Warte auf Prozess $PACKAGE (installiere/starte es im Fenster gradle)...\"
pid=\"\"
for i in \$(seq 1 120); do
  pid=\$(adb shell pidof -s $PACKAGE 2>/dev/null | tr -d \"\\r\")
  [ -n \"\$pid\" ] && break
  sleep 1
done
if [ -z \"\$pid\" ]; then
  echo \"Prozess lief nach 120s nicht an — allgemeiner Log-Filter statt PID-Filter:\"
  exec adb logcat | grep --line-buffered \"$PACKAGE\"
else
  echo \"Streame Logcat für PID \$pid\"
  exec adb logcat --pid=\"\$pid\"
fi
'" C-m

# --- Fenster 1: gradle (Build + Install, Auto-Run beim Start) ---
tmux new-window -t "$SESSION" -n 'gradle' -c "$PROJECT_DIR"
tmux send-keys -t "$SESSION:gradle" "./gradlew installDebug && adb shell am start -n $PACKAGE/$MAIN_ACTIVITY" C-m
tmux send-keys -t "$SESSION:gradle" "" # Enter-Zeile bewusst frei lassen für den nächsten manuellen Rerun

# --- Fenster 2: claude ---
tmux new-window -t "$SESSION" -n 'claude' -c "$PROJECT_DIR"

# --- Fenster 3: shell (Tests, Git, adb shell) ---
tmux new-window -t "$SESSION" -n 'shell' -c "$PROJECT_DIR"

tmux select-window -t "$SESSION:$FOCUS_WINDOW" 2>/dev/null || true

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║  $SESSION Android-Dev-Loop gestartet"
echo "╠══════════════════════════════════════════════╣"
echo "║  Window 0: device  (adb logcat, sobald App läuft)"
echo "║  Window 1: gradle  (installDebug lief bereits einmal;"
echo "║             erneut:  ./gradlew installDebug && \\"
echo "║             adb shell am start -n $PACKAGE/$MAIN_ACTIVITY)"
echo "║  Window 2: claude  (Entwicklung)"
echo "║  Window 3: shell   (Tests, Git, adb shell)"
echo "╠══════════════════════════════════════════════╣"
echo "║  Ctrl+B, 0-3  → Window wechseln"
echo "║  Ctrl+B, d    → Detach (läuft weiter)"
echo "║  tmux a -t $SESSION → Wieder verbinden"
echo "╚══════════════════════════════════════════════╝"
echo ""

tmux attach -t "$SESSION"
