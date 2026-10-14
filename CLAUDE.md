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
- Gerätetests mit 24-h-Sperre: nur im Debug-Build per `run-as io.github.sprobst76.zenpipe.debug sed -i …` den Wert `cd_settings_lock_rem` in `shared_prefs/zen_state.xml` verkürzen (App vorher `am force-stop`, ohne offene Wiedergabe – sonst Kill-Pause); danach `zs_*`, `zp_*`, `cd_settings_*`, `px_time` wieder entfernen. Keine Sperr-Umgehung in Release-Code.
- Kotlin-Stil: `runKtlint` ist Teil von `runCheckstyle`-Läufen; Fehler mit `./gradlew formatKtlint` beheben. Signierung nur über Env `ZENPIPE_KEYSTORE`, `ZENPIPE_KEYSTORE_PASSWORD`, `ZENPIPE_KEY_ALIAS`, `ZENPIPE_KEY_PASSWORD` (ohne = unsigniert); CI-Release `.github/workflows/release.yml` bei Tag `v<versionName>` (Details `zen-docs/release.md`).
- Sleep-Timer am Gerät testen: Debug-Builds bieten im Mond-Menü „1 min (debug)“ (Fade ab 30 s). Defy hat **PIN-Sperre**: nie per `input keyevent 26` ausschalten, sonst sind UI-Tests bis zum Entsperren durch Stefan blockiert (Bildschirm-aus-Test nur, wenn er dabei ist). Zen-Zustand zurücksetzen zusätzlich: `px_pl_items`, `sleep_default_min`.
- Gerätetest-Tricks: Pflichtpause schnell auslösen = in der Ersteinrichtung „Pause ab 1 min“ + „kürzeste Pause 5 min“ (strenger, sofort), Pause verlängern per `run-as` (`cd_pause_rem`, `cd_pause_mono` = `nowELAPSED` aus `dumpsys alarm`; App vorher `am force-stop`). Deutsche Texte über Einstellungen > Inhalt > Sprache der App (danach „Systemstandard“). Während Wiedergabe liefert `uiautomator dump` oft „could not get idle state“ -> Screenshots nutzen. Popup per Long-Press braucht Overlay-Recht (`appops set … SYSTEM_ALERT_WINDOW allow`), sonst Upstream-Crash.
- Weiterspielen: der Player pausiert am Ende jedes Videos (`setPauseAtEndOfMediaItems` in `Player.initPlayer`), nur Praxis-Ketten laufen weiter (`Player.zenOnItemEnd`); nie Autoplay/Weiterspielen für andere Inhalte einbauen.
- Abgeschaltete Upstream-Funktionen (Downloads, Pop-up-Player, Minimieren beim App-Wechsel) laufen über `zen/ZenFeatures.kt`; neue Einstiege immer dort prüfen lassen (Tabelle in `zen-docs/rules.md`). Bild läuft nie nebenbei: App verlassen pausiert den Video-Player, Ton nur über „Hintergrund“.
- Farben: neue Namen nur in `values/zen_colors.xml`, Upstream-Farbnamen behalten ihre Schlüssel (Werte in `colors.xml`/`colors_services.xml`); Kontrast >= 4.5:1 nachrechnen (Formel in `zen-docs/spec-design.md`). Home-Taste am Defy zeigt die Launcher-Auswahl: „Nur einmal“ tippen, nie „Immer“.

## Status
Siehe `.planning/STATE.md`.

## Doku
Einstieg und Index aller Doku: `zen-docs/README.md` (Regeln: `zen-docs/rules.md`, Entscheidungen: `zen-docs/decisions.md`, Code-Karte: `zen-docs/architecture.md`, Begriffe: `zen-docs/glossary.md`).
Neue Nutzerentscheidungen als Eintrag in `zen-docs/decisions.md`; überholte Spec-Stellen mit „[überholt: …]“ markieren, Momentaufnahmen nach `zen-docs/archive/`.

## Git-Workflow
- Fork von `TeamNewPipe/NewPipe`; Remote `upstream`. Branch `dev` folgt Upstream unverändert, `zen` trägt unsere Patches (ein klarer Commit pro Änderung, Präfix `zen:`).
- Upstream einspielen: `git fetch upstream && git checkout dev && git merge --ff-only upstream/dev`, dann `git checkout zen && git rebase dev`.
