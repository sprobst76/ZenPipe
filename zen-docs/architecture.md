# Architektur: Zen-Pakete, Datenflüsse, Upstream-Hooks

**Worum geht es:** Karte des ZenPipe-Codes: welche Datei wofür zuständig ist, wie Daten fließen, welche Invarianten gelten und
an welchen Stellen Upstream-Code (NewPipe) gepatcht ist. Werte und Regeln stehen nicht hier, sondern in [rules.md](rules.md).
**Wann nachlesen:** Bevor du Zen-Code änderst, einen neuen Hook setzt oder einen Upstream-Merge-Konflikt auflöst.

Pfade relativ zu `app/src/main/java/org/schabi/newpipe/` (kurz `zen/` = Paket `org.schabi.newpipe.zen`). Stand: Commit `3bdcfd2a6`
(2026-10-14). Unit-Tests: `app/src/test/java/org/schabi/newpipe/zen/` (je Bereich eine `Zen*Test.kt`).

## Grundprinzip

```
 Upstream (NewPipe)                         zen/ (eigene Dateien)
 ───────────────────                        ─────────────────────
 MainActivity, RouterActivity  ── Hook ──►  ZenIntentPrompt (util/), ZenChannelImport
 FeedViewModel/FeedFragment    ── Hook ──►  ZenFeedProvider ─► ZenFeedPlanner / ZenFeedState / ZenEditionPrep
 Player, PlayerService         ── Hook ──►  ZenPause (Engine) ─► ZenWatchTime, ZenSleep, ZenPlayback
 VideoDetailFragment, Listen   ── Hook ──►  ZenPause.blockStart, ZenPraxisUi, ZenSettings.reducedVideoDetails
 viele Einstiege               ── Hook ──►  ZenFeatures (abgeschaltete Upstream-Funktionen)
 App.onCreate                  ── Hook ──►  ZenSettings.init, ZenPause.recover, ZenDesign.migrate, ZenClassifyWorker
                                            ▼
                         ZenStore (`zen_state`) · ZenClock/ZenCountdown · ZenLog · zen_class_cache.db
```

- Hooks in Upstream-Klassen sind kurz und mit `ZenPipe:`/`zen:` kommentiert, meist voll qualifiziert
  (`org.schabi.newpipe.zen.ZenPause.…`), damit keine Import-Konflikte bei Upstream-Merges entstehen.
- Abgeschaltete Upstream-Funktionen werden nicht gelöscht, sondern über `zen/ZenFeatures.kt` an jedem Einstieg geprüft.
- Alles Zeitkritische läuft auf Gerätelaufzeit (`elapsedRealtime`), nie allein auf der Wanduhr.

## Kern: Zustand, Zeit, Protokoll

| Datei | Zweck |
|---|---|
| `zen/ZenStore.kt` | `ZenKeyValueStore` (Interface), `SharedPrefsZenStore`, `MemoryZenStore` (Tests), Objekt `Zen` = Zugriff auf Store/Uhr/Log |
| `zen/ZenClock.kt` | `ZenClock` (Vertrauenszeit: Wanduhr gegen `elapsedRealtime`, Toleranz 2 min, Boot-Zähler) und `ZenCountdown` (Fristen nur auf Gerätelaufzeit) |
| `zen/ZenDay.kt` | Zen-Tag, Wechsel 04:00 (`ROLLOVER_HOUR`); `ZenZone` (feste Zeitzone) liegt in `zen/ZenFeedState.kt` |
| `zen/ZenLog.kt` | Zen-Protokoll (Ereignisse wie `pause_start`, `settings_change`), nicht im Export |
| `zen/ZenPlayback.kt` | „läuft gerade eigene Wiedergabe?“ (für Absichtsabfrage, Thread-Zahl des Klassifikators, Ende der Ersteinrichtung) |

**Invarianten:** Zen-Zustand nur in der Prefs-Datei `zen_state` (ausgeschlossen von Export/Import/Reset/Auto-Backup über
`res/xml/zen_backup_rules.xml` und `zen_data_extraction_rules.xml`); Schlüssel für Videos sind `serviceId+url`, nie DB-IDs;
Countdowns sinken nur mit Gerätelaufzeit (fail-closed über Neustart). Regeln: [rules.md](rules.md) „Grundregeln“.

## Absichtsabfrage

`util/ZenIntentPrompt.java` (nicht im `zen/`-Paket): Dialog mit Wartezeit; aufgerufen aus `MainActivity.onCreate`
(`showIfNeeded`), `onStart/onStop` (Rückkehr aus dem Hintergrund), `handleIntent` (externe Links) und
`RouterActivity` (`gate(...)` vor Wiedergabe). Die Feed-Ausgabe wartet auf die Bestätigung (`FeedFragment` →
`setOnConfirmedListener`). Regeln: rules.md „Absichtsabfrage“.

## Feed als Ausgabe

```
FeedLoadManager (DB füllen, Pull-to-refresh)  ──►  ZenClassifyWorker (T-Einordnung im Hintergrund)
FeedViewModel ──► ZenFeedProvider.compute()
                    ├─ Kandidaten aus der Feed-DB (+ FeedGroupDAO.getZenGroupRanks, ZenGroupRank)
                    ├─ ZenFeedLanguages (Sprachfilter) · ZenPraxis (Praxis raus) · ZenClassExempt
                    ├─ Ausgabe fällig? (ZenFeedState.isDue) ─► ZenEditionPrep (bis 20 s einordnen, Karte „wird vorbereitet“)
                    ├─ ZenFeedPlanner.select (isEligible, score × contentWeight, Vielfalt, Belastend-Regel) ─► Pick + why
                    ├─ ZenFeedState (Ausgabe, Tageszählung, verworfen/gemerkt) in zen_state
                    └─ nach dem Bau: ZenFeedDetails (T → D per StreamInfo, nur WLAN) ─► ZenFeedState.updateWhy
FeedFragment ──► StreamItem (Zeile „N min · Gruppe“ + Warum-Zeile) · ZenEndCardItem (Abschlusskarte) · Triage (ZenWatchLater)
```

| Datei | Zweck |
|---|---|
| `zen/ZenFeedProvider.kt` | Orchestriert Kandidaten, Fälligkeit, Vorbereitung, Bau, Details nachladen; liefert `Result`/`End` an das ViewModel |
| `zen/ZenFeedPlanner.kt` | Reine Logik: Eignung, Scoring, Inhaltsgewicht, Belastend-Regel, Warum-Kodierung (`whyOf/parseWhy`), Musik-Heuristik |
| `zen/ZenFeedState.kt` | Persistenz der laufenden Ausgabe, Tages-Deckel (verschiedene angebotene Videos), Takt; `ZenZone` |
| `zen/ZenEditionPrep.kt` | Vorbereitung einer fälligen Ausgabe (Einordnung bis `LIMIT_MS` 20 s, Startzeit = Erstellzeit) |
| `zen/ZenWatchLater.kt` | Später-Liste (lokale Playlist, max. 10, Ablauf 30 Tage) |
| `zen/ZenGroupRank.kt` | Datensatz Feed-Gruppen-Rang (Priorität = Sortierreihenfolge der Feed-Gruppen) |
| `zen/ZenFeedLanguages.kt`, `zen/ZenLanguage.kt` | Feed-Sprachfilter (Einstellung `feed_langs`): lokale Spracherkennung `ZenLangDetect`, Kanal-Mehrheit `ZenLangFilter` (neu, `3bdcfd2a6`; noch keine Zeile in rules.md) |
| `local/feed/item/ZenEndCardItem.kt` | Abschlusskarte „Du bist durch …“ |

**Invarianten:** Pull-to-refresh füllt nur den Vorrat, die laufende Ausgabe wächst nie; eine laufende Ausgabe wird durch
Einstellungen oder Details nie neu gewürfelt (nur `why` wird ersetzt); Kanalseiten/Suche enden über
`InfoListAdapter.setZenLimit/setZenEnded`. Regeln: rules.md „Feed“, „Inhalts-Erkennung“. Spec: [spec-feed-mit-ende.md](spec-feed-mit-ende.md).

## Pflichtpause und Wiedergabe-Sitzung

| Datei | Zweck |
|---|---|
| `zen/ZenPause.kt` | `ZenKind` (Video/Podcast/Musik/Praxis), `ZenPauseRules` (feste Schwellen wie „zu Ende“ 90 %/60 s, `LATER_PER_DAY`), `ZenContentKind` (Musik/Podcast-Heuristik), `ZenPauseEngine` (reine Logik: Sitzung, Wiedergabezeit, Ruhezeit, Abbruch, Kette, Sleep-Ende), Fassade `ZenPause` (Android-Hooks, `blockStart`, `onStreamInfo`, `recover`, `confirmClose`) |
| `zen/ZenPauseActivity.kt` | Pausenbildschirm mit Countdown und Spruch |
| `zen/ZenQuotes.kt` | Spruchauswahl (einer pro Pause, Runden), eigene Sprüche |
| `zen/ZenWatchTime.kt` | Wiedergabezeit pro Zen-Tag (`wt_<Tag>` = Bild, Ton, Musik, Praxis), Zen-Minuten-Hook |

```
Player.changeState/handleIntent ─► ZenPause.onPlaying/setAudio ─► ZenPauseEngine (alle 10 s ps_* persistieren, wt_* buchen)
Player (StreamInfo geladen)      ─► ZenPause.onStreamInfo ─► Art bestimmen; Nicht-Praxis während Pause/Kette ⇒ STREAM_BLOCK_PAUSE / STREAM_CHAIN_STOP
Player schließen / Videowechsel  ─► ZenPause.onClosed/onItem ─► Abbruch? ⇒ Pause = max(0, Länge − Ruhezeit) als ZenCountdown
App.onCreate                     ─► ZenPause.recover (offene Sitzung nach Kill = Abbruch)
Startstellen (Detailseite, NavigationHelper, Router) ─► ZenPause.blockStart ─► Pausenbildschirm; Netz: PlayerService
```

**Invarianten:** Pause endet nur durch Zeitablauf auf Gerätelaufzeit; laufende Pause wird nie verkürzt; Pausieren ist kein
Abbruch; Praxis darf während der Pause starten (Zuordnung, nie Titel); unbekannte Art = streng (Video). Regeln: rules.md
„Pflichtpause“, „Wiedergabezeit“. Specs: [spec-pause-nach-abbruch.md](spec-pause-nach-abbruch.md),
[spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md).

## Zen-Einstellungen und 24-h-Sperre

| Datei | Zweck |
|---|---|
| `zen/ZenSettings.kt` | `ZenSettingDef` (Spanne, Richtung `strictHigher`, Bool/Bitmaske), `ZenSettingKeys`, `ZenSettingDefs.all` (Defaults), Lesezugriff `ZenSettings.*()` (ohne `init` gelten Defaults) |
| `zen/ZenSettingsLock.kt` | `ZenSettingsRules` (`LOCK_MS` 24 h, `UNDO_MS` 5 min, `SETUP_MAX_MS`, `CONFIRM_WAIT_S` 3) und `ZenSettingsLock` (apply: strenger sofort / lockern ausstehend + Sperre; cancelPending; undo; Ersteinrichtung; Praxis- und Ausnahmelisten `addPraxis/removePraxis`, `addExempt/removeExempt`) |
| `zen/ZenSettingsFragment.kt` | Einstellungen > Zen (Eintrag in `res/xml/main_settings.xml`): Entwurf, Bestätigung, Status, Praxis-/Ausnahmen-/Sprüche-Verwaltung, „Wiedergabe heute“, Klassifikator-Abschnitt |

**Invarianten:** Richtung wird pro Einstellung gegen den wirksamen Wert bestimmt; nur Lockern startet die Sperre; Kreuzregeln
gegen neuen und ausstehenden Zielzustand; alle Fristen sind `ZenCountdown`. Jede neue lockerbare Funktion muss über
`ZenSettingsLock` laufen. Regeln: rules.md „Zen-Einstellungen“, „Einstellungs-Sperre“. Spec: [spec-einstellungs-sperre.md](spec-einstellungs-sperre.md).

## Praxis, Kette, Sleep-Timer

| Datei | Zweck |
|---|---|
| `zen/ZenPraxis.kt` | `ZenPraxisChannel` (Kanal oder Online-Playlist, normalisierte URL), `ZenPraxisTime` (Praxis-Minuten), `ZenPraxisPlaylistItems` (`px_pl_items`), `ZenPraxis.isPraxisStream` |
| `zen/ZenPraxisFragment.kt` | Tab „Praxis“ (`settings/tabs/Tab.PraxisTab`, ID 10; `TabsManager` ergänzt ihn immer nach dem Feed) und Drawer-Eintrag |
| `zen/ZenPraxisUi.kt` | Markieren/Abbrechen/Entfernen (Menü, Long-Press, Bestätigung, Plausibilitätsprüfung davor) |
| `zen/ZenSleep.kt`, `zen/ZenSleepUi.kt` | `ZenSleepTimer` (Gerätelaufzeit, Fade 30 s), `ZenSleepSignal` (Hook Einschlaferkennung), Mond-Knopf/Menü |

Kette: `Player.initPlayer` setzt `setPauseAtEndOfMediaItems(true)`; am Ende ruft `Player.zenOnItemEnd` →
`ZenPause.mayChain` (nur Praxis → Praxis). Regeln: rules.md „Praxis“. Spec: [spec-praxis.md](spec-praxis.md).

## Klassifikator (`zen/classify/`)

```
Modell-Download (Tipp) ─► ZenModelInstaller (Zip, SHA-256, noBackupFilesDir) ─► ZenClassifierModel (Status, URL e5s-1)
VideoText (TextBuilder) ─► UnigramTokenizer ─► OrtEncoder (ONNX, Embedding) ─► Heads (Thema/Format/Ton) ─► ZenRules (Kategorien, Gewicht)
                                   └──────────── ZenPipeline ──────────────┘          ▼
ZenClassifier (Session, Threads 4/2, Leerlauf 60 s) ─► ZenClassCache (zen_class_cache.db: Einträge T/D, Labels, Sprache)
Auslöser: ZenClassifyWorker (nach Feed-Aktualisierung/App-Start, T) · VideoDetailFragment/Player (D) · ZenEditionPrep · ZenFeedDetails
```

| Datei | Zweck |
|---|---|
| `TextBuilder.kt`, `UnigramTokenizer.kt`, `OrtEncoder.kt`, `Heads.kt`, `ZenPipeline.kt`, `ZenRules.kt`, `ZenBase64.kt` | Kern (Port des Bundles aus dem Projekt YoutubeManager, Regelsatz v5) |
| `ZenClassifier.kt`, `ZenClassifyWorker.kt`, `ZenClassCache.kt` | Laufzeit, Hintergrund-Einordnung, Cache (30 Tage, neu bei Modellwechsel oder T → D) |
| `ZenClassifierModel.kt`, `ZenModelInstaller.kt`, `ZenNoticeText.kt` | Modellstatus, Download/Prüfung, Lizenzhinweise |
| `ZenDetailRequests.kt`, `ZenFeedDetails.kt` | gedrosselte `StreamInfo`-Abrufe (1/s, Fehlerarten), Details nachladen für die Ausgabe (WLAN, 30/Zen-Tag) |
| `ZenPlausibility.kt`, `ZenPlausibilityCheck.kt` | Prüfung beim Markieren als Praxis (Stichprobe 15, Schwellen 30/60 %) |
| `ZenChannelProfile.kt` + `zen/ZenChannelImport.kt` | Blatt „Kanal übernehmen“ für Kanal-Links (Hook `RouterActivity.onSuccess`) |
| `zen/ZenClassExempt.kt`, `zen/ZenClassExemptUi.kt` | Ausnahmeliste (Kanäle, Schlagwörter) |
| `ZenClassWhy.kt` | Text der Warum-Zeile (Kurznamen DE/EN) |
| `ZenClassifierSection.kt`, `ZenClassReview.kt`, `ZenClassExport.kt`, `ZenClassifierSelfTest.kt` | Einstellungsabschnitt, Prüfansicht mit Korrekturen, Debug-Export (JSON/CSV), Selbsttest (Debug) |

**Invarianten:** Ohne Modell läuft alles wie vorher (Gewicht 1,0); der Klassifikator wirkt automatisch nur strenger, entscheidet
nie über Praxis-Zugehörigkeit; `select` liest nur den Cache; einzige neue Gegenstelle ist der GitHub-Download auf Tipp; Labels
der Prüfansicht ändern nichts an Feed/Gewicht/Sperre. Regeln: rules.md „Inhalts-Erkennung (lokal)“. Spec:
[spec-klassifikator-integration.md](spec-klassifikator-integration.md), Umsetzung: [klassifikator-umsetzungsprotokoll.md](klassifikator-umsetzungsprotokoll.md).

## Abgeschaltete Upstream-Funktionen und Design

| Datei | Zweck |
|---|---|
| `zen/ZenFeatures.kt` | Schalter `downloadsEnabled`, `popupPlayerEnabled`, `minimizeOnExitEnabled`, `upstreamAnnouncementsEnabled` (alle `false`), `effectiveOpenAction`. Tabelle aller Einstiege: rules.md „Funktionen“ |
| `zen/ZenDesign.kt` | Einmalige Migration ruhiger Defaults (Benachrichtigung nicht eingefärbt, `design_prefs_version`) |
| `res/values/zen_colors.xml`, `res/color/zen_text_*` | Palette „Salbei“ (Werte: rules.md „Darstellung“, [spec-design.md](spec-design.md)) |

Weitere Zen-Anpassungen ohne eigene Datei: Shorts/Live-Filter (`InfoListAdapter.addInfoItemList`, `ChannelTabHelper`,
`SearchFragment.zenWithoutShorts`, RSS-Schnellmodus aus in `FeedLoadManager`), Autoplay/Auto-Queue (`PlayerHelper`), Related/Kommentare
und reduzierte Details (`VideoDetailFragment`), Drawer (`MainActivity`), Kiosk-Tabs (`TabsManager`), kein Update-Check
(`ReleaseVersionUtil`, `NewVersionWorker`), Crash-Report nach GitHub (`ErrorActivity`).

## Upstream-Einstiegspunkte (per grep verifiziert)

| Upstream-Datei | Zen-Hook |
|---|---|
| `App.kt` (`onCreate`) | `ZenSettings.init`, `ZenPause.recover`, `ZenDesign.migrate`, `ZenClassifyWorker.enqueueIfInstalled` |
| `MainActivity.java` | `ZenIntentPrompt.showIfNeeded/onActivityStarted/onActivityStopped/onExternalLink`, Drawer (Praxis, Suche), `ZenFeatures.upstreamAnnouncementsEnabled` |
| `RouterActivity.java` | `ZenChannelImport.handles/show` (Kanal-Links), `ZenFeatures` (Auswahl ohne Pop-up/Download, `effectiveOpenAction`), `ZenIntentPrompt.gate`, Shorts-Links blockiert |
| `player/Player.java` | `initPlayer` (`setPauseAtEndOfMediaItems`), `zenOnItemEnd`, `ZenPause.onPlaying/setAudio/onProgress/onStreamInfo/onClosed/onItem/onCompleted`, `ZenPlayback.setPlaying`, `ZenSleep.bind` |
| `player/PlayerService.java` | Sicherheitsnetz `ZenPause.blockForService` (`ZenPauseRules.intentStartsPlayback`), verwirft POPUP-Intents |
| `player/PlayQueueActivity.java`, `player/ui/VideoPlayerUi.java` | Sleep-Timer-Menü (Hintergrund) bzw. Mond-Knopf (`zenSleepButton`), kein Pop-up; Suchleiste Salbei (`zenSeekColor`) |
| `fragments/detail/VideoDetailFragment.java` | Related/Kommentare aus, `zenReduced`, `ZenPause.blockStart/confirmClose`, `ZenClassifier.onStreamInfo`, kein Download/Pop-up |
| `local/feed/FeedViewModel.kt`, `FeedFragment.kt`, `item/StreamItem.kt` | `ZenFeedProvider`, Vorbereitungs-/Detail-Listener, Triage, Warum-Zeile |
| `local/subscription/SubscriptionFragment.kt` | Long-Press: Praxis markieren, Ausnahme |
| `fragments/list/BaseListFragment.java` | Long-Press auf Kanal/Playlist: `ZenPraxisUi.showLongPress` |
| `fragments/list/channel/ChannelFragment.java`, `fragments/list/playlist/PlaylistFragment.java` | Menü „Als Praxis markieren“, `ZenPraxis.registerPlaylistItems` |
| `util/NavigationHelper.java` | `ZenPause.blockStart` vor jedem Start, Pop-up/Downloads no-op, Praxis-Seite |
| `info_list/InfoListAdapter.java`, `fragments/list/search/SearchFragment.java` | Listenende (15 Einträge / 2 Seiten), Shorts-Filter |
| `info_list/dialog/InfoItemDialog.java`, `QueueItemMenuUtil.java`, `util/PlayButtonHelper.kt`, `settings/*`, `download/*` | `ZenFeatures`-Prüfungen |
| `database/feed/dao/FeedGroupDAO.kt` | `getZenGroupRanks` |

## Release, CI, F-Droid

`buildSrc/src/main/kotlin/ProjectConfig.kt` (applicationId, Version), `app/build.gradle.kts` (Signierung aus `ZENPIPE_*`, nur
en+de, `abiFilters` arm64), `.github/workflows/{ci,release,fdroid-repo}.yml`, `fdroid/` (Repo-Konfiguration, `build-repo.sh`),
`fastlane/metadata/android/{en-US,de-DE}`. Ablauf: Tag `v<versionName>` → `release.yml` (signiertes APK + SHA-256 + GitHub-Release)
→ `fdroid-repo.yml` (Index auf GitHub Pages). Details: [release.md](release.md).
