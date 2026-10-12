# ZenPipe

Fork von NewPipe (GPLv3) mit Zen-Fokus: bewusstes Schauen statt endlosem Feed. Android, Kotlin/Java.
Konzept: `zen-docs/ZenPipe-Konzept_v0.1.md`.

## Sprache
Kommunikation Deutsch; Code, Commits, technische Doku Englisch.

## Leitplanken
- Fork als **kleiner Patch-Satz** auf Upstream NewPipe halten (regelmäßig mergen). Änderungen minimal und lokal, keine Umbauten ohne Not.
- NewPipeExtractor unverändert einbinden.
- GPLv3 beachten: Fork bleibt Open Source.
- Verteilung: GitHub-Releases / eigenes F-Droid-Repo, nicht Play Store.
- Nicht denselben Package-Namen wie NewPipe verwenden (Parallelinstallation); applicationId klären.

## Operations
- `./dev.sh` / `./stop.sh`: tmux-Dev-Loop (Build → Install → Logcat). Vorher CONFIG-Block (PACKAGE, MAIN_ACTIVITY) anpassen, sobald der Fork existiert.
- Gerät: Motorola Defy per WiFi-ADB; Status mit `~/development/WorkflowAnalyser/scripts/adb-device-check.sh`.
- Release: Skill `/android-release` + `android-release-preflight.sh`.

## Status
Siehe `.planning/STATE.md`.

## Git-Workflow
- Fork von `TeamNewPipe/NewPipe`; Remote `upstream`. Branch `dev` folgt Upstream unverändert, `zen` trägt unsere Patches (ein klarer Commit pro Änderung, Präfix `zen:`).
- Upstream einspielen: `git fetch upstream && git checkout dev && git merge --ff-only upstream/dev`, dann `git checkout zen && git rebase dev`.
