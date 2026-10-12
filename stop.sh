#!/bin/bash
# <Projekt> — Entwicklungsumgebung sauber beenden
#
# Kopiervorlage: nach <projekt>/stop.sh kopieren, SESSION-Wert an dev.sh anpassen.
#
# Tötet gezielt nur die Prozesse aus den Pidfiles dieses Projekts (nie
# `pkill -f uvicorn` o.ä. — das erwischt bei mehreren parallel laufenden
# Projekten im Zweifel den falschen Prozess). Danach wird die tmux-Session
# als Ganzes entfernt, damit keine Zombie-Panes/Shells zurückbleiben.

set -uo pipefail

SESSION="zenpipe"
PROJECT_DIR="$HOME/development/zenpipe"
PIDDIR="$PROJECT_DIR/.dev/pids"

if [ -d "$PIDDIR" ]; then
    for pidfile in "$PIDDIR"/*.pid; do
        [ -f "$pidfile" ] || continue
        name="$(basename "$pidfile" .pid)"
        pid="$(cat "$pidfile")"
        if kill -0 "$pid" 2>/dev/null; then
            echo "Beende $name (PID $pid)..."
            kill "$pid" 2>/dev/null
            for _ in 1 2 3 4 5; do
                kill -0 "$pid" 2>/dev/null || break
                sleep 1
            done
            kill -0 "$pid" 2>/dev/null && { echo "  ...reagiert nicht, kill -9"; kill -9 "$pid" 2>/dev/null; }
        fi
        rm -f "$pidfile"
    done
fi

if tmux has-session -t "$SESSION" 2>/dev/null; then
    tmux kill-session -t "$SESSION"
    echo "tmux-Session '$SESSION' beendet."
else
    echo "Keine laufende tmux-Session '$SESSION' gefunden."
fi
