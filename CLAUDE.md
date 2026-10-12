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
- applicationId `io.github.sprobst76.zenpipe` (erledigt, parallel zu NewPipe installierbar). Kein „NewPipe“ im App-Namen; keine Anfragen an NewPipe-Server (Update-Check aus, Crash-Reports nur an ZenPipe-GitHub).

## Operations
- `./dev.sh` / `./stop.sh`: tmux-Dev-Loop (Build → Install → Logcat); CONFIG ist auf `io.github.sprobst76.zenpipe.debug` + `org.schabi.newpipe.MainActivity` gesetzt.
- Paketnamen: Release `io.github.sprobst76.zenpipe`, Debug `io.github.sprobst76.zenpipe.debug` (Namespace/Package im Code bleibt `org.schabi.newpipe`). Alte Debug-App `org.schabi.newpipe.debug.zen` auf dem Defy ist Altlast (enthält Test-Abos). Versionsschema, Icon-Generator, Fastlane: `zen-docs/release.md`.
- Gerät: Motorola Defy per WiFi-ADB; Status mit `~/development/WorkflowAnalyser/scripts/adb-device-check.sh`.
- Release: Skill `/android-release` + `android-release-preflight.sh`.

## Status
Siehe `.planning/STATE.md`.

## Git-Workflow
- Fork von `TeamNewPipe/NewPipe`; Remote `upstream`. Branch `dev` folgt Upstream unverändert, `zen` trägt unsere Patches (ein klarer Commit pro Änderung, Präfix `zen:`).
- Upstream einspielen: `git fetch upstream && git checkout dev && git merge --ff-only upstream/dev`, dann `git checkout zen && git rebase dev`.
