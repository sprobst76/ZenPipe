# ZenPipe: Regel-Tabelle (Single Source of Truth)

Stand: 2026-10-13, nach Phase 2 Teil 1, Verteilbarkeit Schritt 2, Praxis-Kanälen/-Playlists, Praxis-Kette und Sleep-Timer. Alle Specs verweisen hierher; bei Widerspruch gilt diese Datei. Die Konstanten im Code
tragen dieselben Namen (Spalte „Code“). Entscheidungen F1-F9 stehen am Ende von `consistency-review.md`.
Status: **umgesetzt** = im Code und am Gerät geprüft; **Spec** = nur beschrieben; **offen** = nicht entschieden/nicht gebaut.

## Grundregeln

| Regel | Wert / Default | Gilt für | Ausnahme | Code / Quelle | Status |
|---|---|---|---|---|---|
| Autoplay (geöffnetes Video) | aus, fest (Nutzerentscheidung, auch nicht für gewählte Videos) | alle Player | – | `PlayerHelper.getAutoplayType` | umgesetzt |
| Weiterspielen in Warteschlangen | **nie automatisch** (Nutzerentscheidung 2026-10-13): der Player pausiert am Ende jedes Videos (`setPauseAtEndOfMediaItems`), auch in selbst gebauten Warteschlangen und Playlists („Alle abspielen“); weiter geht es nur per Tipp. Ausnahme: **Praxis-Kette** (Abschnitt „Praxis“). Wiederholen (eins) bleibt Nutzerwahl | alle Player | Praxis-Kette | `Player.zenOnItemEnd`, `ZenPauseEngine.mayChain` | umgesetzt |
| Auto-Queue / Related / Kommentare | aus, fest | Detailseite, Player | – | `PlayerHelper.isAutoQueueEnabled`, `VideoDetailFragment` | umgesetzt |
| Shorts | nirgends | Listen, Kanal-Tab, Feed-Abruf, RSS, `/shorts/`-Links, Einstellungen | Watch-Links auf Short-IDs (Restrisiko) | `InfoListAdapter`, `ChannelTabHelper`, `RouterActivity` | umgesetzt |
| Livestreams | nicht im Feed / Kanal-Tab | Feed, Kanal | Suche, Playlists (bewusst offen) | `ChannelTabHelper`, `FeedViewModel` | umgesetzt |
| Zen-Tag | Wechsel 04:00, Zeitzone beim ersten Start fixiert | Deckel, Später-Zähler, Bilanz | – | `ZenDay.ROLLOVER_HOUR`, `ZenZone` | umgesetzt |
| Vertrauenszeit | Wanduhr gegen `elapsedRealtime`, Toleranz 2 min, Boot-Zähler erkennt Neustarts, große Vorsprünge über Neustart werden als `clock_reboot_gap` protokolliert | Ausgabe-Takt, Zen-Tag | HTTP-`Date` als dritte Quelle fehlt | `ZenClock` | umgesetzt (Restlücke: Vorsprung über Neustart) |
| Zeitkritische Countdowns | sinken nur mit Gerätelaufzeit, unabhängig von der Wanduhr, Ausschaltzeit zählt nicht | Pflichtpause, später Einstellungs-Sperre | – | `ZenCountdown` | umgesetzt |
| Zen-Zustand | Prefs-Datei `zen_state`, nicht in Export/Import/Reset/Auto-Backup; Schlüssel `serviceId+url` statt `uid` | alles Zen | – | `Zen`, `ZenStore` | umgesetzt |

## Feed (Details: `spec-feed-mit-ende.md`)

| Regel | Wert / Default | Gilt für | Ausnahme | Code / Quelle | Status |
|---|---|---|---|---|---|
| Ausgabe-Größe | 15 Videos | Feed | konfigurierbar 10-15 (<= Deckel/2) erst mit Einstellungs-Sperre | `ZenFeedPlanner.BUDGET_PER_HOUR` | umgesetzt (Konstante) |
| Ausgabe-Takt (F5) | frühestens 60 min nach der letzten Ausgabe; Ausgabe erst nach bestätigter Absichtsabfrage und beim Öffnen des Feeds | Feed | während Pause/Nachtruhe soll keine neue Ausgabe entstehen (W14/W8) | `ZenFeedPlanner.EDITION_INTERVAL_MS` | umgesetzt, Pausen-Ausnahme offen |
| Tages-Deckel (F1) | 30 **verschiedene** Videos pro Zen-Tag | Feed | Später-Liste, Playlists, gezielte Suche, Praxis-Kanäle | `ZenFeedPlanner.DAILY_CAP` | umgesetzt |
| Kandidaten | ungesehen, aus Abos, <= 7 Tage, kein Live, keine Musik (F3), kein Praxis-Kanal, Länge >= 5 min (gilt **nur im Feed**) | Feed | Praxis-Videos stehen im Praxis-Tab | `ZenFeedPlanner.isEligible`, `MAX_AGE_DAYS`, `MIN_DURATION_SEC` | umgesetzt |
| Scoring | Feed-Gruppen-Rang (60 - 15 pro Rang, >= 0) + Länge (>= 15 min +12, >= 30 min +6 zusätzlich) + Frische (0-20); Musik ohne Längenbonus | Feed | – | `ZenFeedPlanner.score` | umgesetzt |
| Vielfalt | max. 3 je Kanal und Ausgabe | Feed | – | `MAX_PER_CHANNEL` | umgesetzt |
| Später-Liste | max. 10, lokale Playlist „Watch later“, Ablauf nach 30 Tagen (F4) | Feed-Triage | vom Tages-Deckel ausgenommen, Pause gilt | `ZenWatchLater.MAX_ITEMS`, `EXPIRY_MS` | umgesetzt |
| Kanalseite | max. 15 Einträge, keine Paginierung | Kanal-Tabs | – | `InfoListAdapter.setZenLimit` | umgesetzt |
| Suche | max. 2 Ergebnisseiten | Suche | – | `SearchFragment` | umgesetzt |
| Entdecken | opt-in, max. 3 Kanäle/Woche, >= 2 unabhängige Quellen, Wechsel Mo 04:00 | eigener Bereich | – | `spec-verwandte-kanaele.md` | Spec (Phase 2) |
| Abo-Hygiene | bei neuem Abo und monatlich: Kanäle mit 90 Tagen ohne „zu Ende“ | Abos | – | Entdecken-Spec | Spec (Phase 2/3) |
| Tageslimit Minuten (F2) | Wert offen; Minuten-Limit ja, aber erst nach der Einstellungs-Sperre; Audio/Podcast halb, Musik nicht (Vorschlag) | Konsum | – | Konzept §3 | offen |

## Absichtsabfrage

| Regel | Wert / Default | Gilt für | Ausnahme | Code / Quelle | Status |
|---|---|---|---|---|---|
| Wartezeit | 5 s, „Weiter“ erst danach, „Doch nicht“ schließt die App | alle Abfragen | – | `ZenIntentPrompt.WAIT_SECONDS` | umgesetzt |
| Auslöser | App-Start (bis einmal bestätigt, auch nach „Doch nicht“ und nach neu erzeugter Activity), geteilter/externer Link, Rückkehr nach >= 5 min im Hintergrund, **Video-/Hintergrund-/Popup-Wiedergabe über geteilte Links (RouterActivity)** | MainActivity und RouterActivity | – | `ZenIntentPrompt` | umgesetzt |
| Cooldown | 5 min nach einer **bestätigten** Abfrage | alle Auslöser außer App-Start | – | `MIN_INTERVAL_MS`, `BACKGROUND_THRESHOLD_MS` | umgesetzt |
| Unterdrückung | nur bei laufender **eigener** Wiedergabe (`ZenPlayback`), nie wegen Musik/Audio anderer Apps | Link, Hintergrund | – | `ZenPlayback.isPlaying` | umgesetzt |
| Zweite Abfrage offen | ein Router-Link, der während einer offenen Abfrage eintrifft, wartet auf deren Antwort | Router | – | `ZenIntentPrompt.gate` | umgesetzt (am Gerät nur indirekt geprüft) |

## Pflichtpause (Details: `spec-pause-nach-abbruch.md`)

| Regel | Wert / Default | Gilt für | Ausnahme | Code / Quelle | Status |
|---|---|---|---|---|---|
| „Zu Ende geschaut“ | Position >= 90 % oder Rest <= 60 s | Pause, Wiedergabe-Ende | – | `ZenPauseRules.FINISHED_FRACTION`, `FINISHED_REMAINING_MS` | umgesetzt |
| Wiedergabezeit | echte Abspielzeit **einer Sitzung** des Videos; Pausieren/Puffern/Spulen zählen nicht | Pause | – | `ZenPauseEngine` | umgesetzt |
| Auslöser | Abbruch einer Sitzung mit Wiedergabezeit >= 5 min | alle Player (Video, Popup, Hintergrund, Router) | Musik; Praxis (Kanal/Playlist); „Später fortsetzen“ (F6); Sleep-Timer | `ZenPauseRules.MIN_WATCH_MS` | umgesetzt |
| Abbruch | Player schließen (auch pausiert), Videowechsel, Prozess-Kill (beim nächsten Start erkannt) | Pause | Pausieren allein, Anruf/Audio-Fokus, zu Ende geschaut, Ablauf des Sleep-Timers | `ZenPause`, `App.onCreate` | umgesetzt (Lücke: Mini-Player nach unten ziehen ohne Rückfrage) |
| Pausenlänge | 20 % der Wiedergabezeit, min 1, max 15 min; nie kürzer als eine laufende | Pause | – | `PAUSE_FRACTION`, `MIN_PAUSE_MS`, `MAX_PAUSE_MS` | umgesetzt |
| Verhalten | Wiedergabestart gesperrt (Pausenbildschirm mit Countdown, kein Überspringen), Browsen/Suchen erlaubt, endet nur durch Zeitablauf auf Gerätelaufzeit (nicht durch Stunden-/Tageswechsel, Uhr, Neustart); ein Nicht-Praxis-Video, das trotzdem den Player erreicht (z. B. nächstes Element einer Warteschlange), wird gestoppt | alle Starts | **Praxis-Videos** (Abschnitt „Praxis“) | `ZenPause.blockStart`, `PlayerService`-Netz | umgesetzt |
| Musik vs. Podcast | Musik = Kategorie „Music“ / Topic- oder VEVO-Kanal / Titelwörter; Podcast = Hinweise oder >= 20 min Audio (Pause gilt wie Video); unsicher = streng | Pause | – | `ZenContentKind.classify` (StreamInfo.category/tags) | umgesetzt |
| Später fortsetzen (F6) | 2x pro Zen-Tag, keine Pause, Rückfrage beim Mini-Player-X, nur wenn das Schließen eine Pause auslösen würde | Pause | – | `ZenPauseRules.LATER_PER_DAY`, `ZenPause.confirmClose` | umgesetzt |
| Pausenspruch | Pausenbildschirm zeigt unter dem Countdown einen Spruch (98 je Sprache, DE/EN nach App-Sprache, Quelle `sprueche.md`, nur gemeinfreie belegte Zitate + eigene Sätze + Atemhinweise); **einer pro Pause** (`pause_seq`), jede neue Pause ein anderer, keine Wiederholung bis alle gezeigt (Runde), nie zweimal derselbe hintereinander. Eigene Sprüche (Einstellungen > Zen, max. 100 × 300 Zeichen) gemischt oder „Nur eigene“ (leer = eingebaute); **keine Lockerung, keine Sperre**. Stört Countdown/Knöpfe nicht (Bildschirm scrollt bei langen Sprüchen). Zustand in `zen_state` (`qt_custom`, `qt_only_custom`, `qt_seen`, `qt_last`, `qt_pause_id`, `qt_pause_text`, `pause_seq`), nicht in Export/Import/Reset/Auto-Backup | Pausenbildschirm | – | `ZenQuotes`, `ZenPauseActivity`, `R.array.zen_quotes` (`zen-docs/gen_sprueche.py`) | umgesetzt |
| Protokoll | `pause_start`, `pause_end`, `abort_short`, `abort_exempt` (Musik/Praxis), `watch_end`, `later`, `pause_praxis_start` (Praxis-Start während Pause), `pause_block_running` | ZenLog | nicht im Export | `ZenLog` | umgesetzt |

## Zen-Einstellungen (Modell, Phase 2 Teil 1)

Diese Werte sind veränderbar (Einstellungen > Zen) und stehen im Code in `ZenSettingDefs`; gelesen werden sie über `ZenSettings`
(die frühere Konstante ist der Default). Alle anderen Werte dieser Datei (Zen-Tag 04:00, Takt 60 min, 7 Tage, max. 3 je Kanal,
„zu Ende“ 90 %/60 s, Cooldown 5 min, Später-Zähler, Später-Liste, Kanalseite/Suche) sind **feste Regeln**, keine Einstellungen.

| Schlüssel | Einstellung | Default | Spanne (Schritt) | strenger ist | Wirkung |
|---|---|---|---|---|---|
| `feed_budget` | Videos pro Ausgabe | 15 | 5-15 (1) | kleiner | nächste Ausgabe |
| `daily_cap` | Tages-Deckel (verschiedene Videos) | 30 | 10-30 (1) | kleiner | sofort bei der nächsten Prüfung |
| `min_length_min` | Mindestlänge im Feed | 5 min | 1-30 (1) | größer | nächste Ausgabe |
| `pause_enabled` | Pflichtpause an/aus | an | an/aus | an | nächster Abbruch |
| `pause_percent` | Pausenlänge, Anteil der Schauzeit | 20 % | 10-50 (5) | größer | nächster Abbruch |
| `pause_min_min` | kürzeste Pause | 1 min | 1-5 (1) | größer | nächster Abbruch |
| `pause_max_min` | längste Pause | 15 min | 5-30 (1) | größer | nächster Abbruch |
| `pause_trigger_min` | Schauzeit, ab der ein Abbruch pausiert | 5 min | 1-15 (1) | kleiner | nächster Abbruch |
| `intent_wait_s` | Wartezeit der Absichtsabfrage | 5 s | 3-30 (1) | größer | nächste Abfrage |
| `intent_background_min` | Abwesenheit bis zur erneuten Abfrage | 5 min | 1-60 (1) | kleiner | nächste Rückkehr |
| `reduced_details` | Reduzierte Videodetails | an | an/aus | an | nächstes geöffnetes Video (Beschreibungs-Tab sofort beim Zurückkehren) |

- Budget und Deckel haben als Obergrenze den bisherigen Default: mehr als vorher wird nie angeboten, Lockern heißt „zurück Richtung
  Default“. Die Absichtsabfrage hat keinen Aus-Schalter (gibt es nicht); „Pflichtpause aus“ zählt als Lockern.
- Pflichtpause aus: Abbrüche werden als `abort_pause_off` protokolliert, es entsteht keine Pause; eine bereits laufende Pause
  wird nicht verkürzt (Countdown läuft weiter), eine laufende Sitzung beim Umschalten zählt nach der neuen Einstellung.
- Reduzierte Videodetails an: die Detailseite zeigt nur Thumbnail (Play, Dauer), Titel (antippbar: Teilen/Link kopieren),
  Kanalname + Avatar (antippbar) und Hinzufügen/Hintergrund/Pop-up. Ausgeblendet: Beschreibungs-Tab (Beschreibung inkl. Links,
  „Veröffentlicht am“, Tags/Kategorie/Lizenz/Altersfreigabe), Aufrufe, Likes/Dislikes, Abonnentenzahl, Meta-Info-Kasten,
  „Nichts hier“-Hinweis. Kein „Details anzeigen“ (keine Hintertür per Tipp). Kapitel stehen nicht auf der Detailseite, nur im
  Player (bleiben). „Aus“ zählt als Lockern (`VideoDetailFragment.zenReduced`).
- Kreuzregeln (immer gegen den neuen **und** den ausstehenden Zielzustand geprüft): Deckel >= Ausgabe-Größe, Ausgabe >= 5 (Spanne).
  Kürzeste <= längste Pause ist durch die getrennten Spannen (1-5 / 5-30) immer erfüllt.
- Richtung wird pro Einstellung bestimmt (gegen den **wirksamen** Wert). Eine Gruppe mit gemischten Richtungen wird aufgeteilt:
  die strengeren Werte gelten sofort, die lockeren werden ausstehend (die Spec-Zeile „gemischte Änderungen zählen als Lockern“
  betraf nur das Nachtruhe-Zeitfenster, das es noch nicht gibt).

## Einstellungs-Sperre (Details: `spec-einstellungs-sperre.md`)

| Regel | Wert / Default | Gilt für | Ausnahme | Code / Quelle | Status |
|---|---|---|---|---|---|
| Sperre (F7) | Eine **Lockerung** startet die 24-h-Sperre (globaler Countdown `settings_lock`, Gerätelaufzeit). Währenddessen ist jedes weitere Lockern abgelehnt (ausgegraut), Strenger bleibt möglich | alle Zen-Einstellungen oben | Ersteinrichtung | `ZenSettingsLock`, `ZenSettingsRules.LOCK_MS` | umgesetzt |
| Asymmetrie (F7) | strenger: sofort, startet/verlängert die Sperre **nicht**, nie ein Schlupfloch; lockern: „ausstehend“, wirkt erst, wenn die Sperre endet (= 24 h nach Bestätigung), bis dahin abbrechbar (Sperre läuft weiter) | s. o. | – | `apply`, `cancelPending` | umgesetzt |
| Auflösung Spec-Widerspruch | Die Spec sagt „jede Änderung startet die Sperre“, F7 „strenger startet sie nicht“. Gilt: **nur Lockern startet die Sperre**, denn nur Lockern braucht Impulsschutz; strenger Stellen jederzeit zu erlauben kostet nichts | – | – | `ZenSettingsLock` | entschieden |
| Ausstehend + strenger | Strenger auf einer Einstellung mit ausstehender Lockerung (oder Zurücksetzen auf den wirksamen Wert) verwirft die ausstehende Lockerung dieser Einstellung | – | – | `apply` | umgesetzt |
| Nicht gesperrt (F8) | Feed-Gruppen (Zuordnung/Reihenfolge), Abos, Playlists, Suche, normale Player-Einstellungen | – | – | – | umgesetzt (nicht angefasst) |
| Rückgängig (F9) | 5 min nach Bestätigung (Countdown `settings_undo`), stellt Werte, ausstehende Änderungen und – falls diese Änderung sie gestartet hat – die Sperre exakt wieder her; startet keine Sperre; nur eine Änderung zurück | alle Zen-Einstellungen | Ersteinrichtung (dort frei) | `ZenSettingsLock.undo` | umgesetzt |
| Bestätigung | Dialog mit Zusammenfassung („gilt sofort“ / „gilt ab <Zeit>“), 3 s Wartezeit (Knopf „Bestätigen (3)“), Hinweis auf Sperre und Rückgängig. Gruppierte Änderungen = ein Entwurf = **eine** Bestätigung = eine Sperre | UI | – | `ZenSettingsFragment` | umgesetzt |
| Ersteinrichtung | Solange `zs_setup_done` fehlt (auch auf Bestandsinstallationen): Änderungen sofort wirksam, keine Sperre, kein Rückgängig. Ende durch „Fertig“, **ersten Wiedergabestart** (`ZenPlayback.setPlaying(true)`) oder 24 h Gerätelaufzeit seit dem ersten Zugriff (`settings_setup`-Countdown, entscheidet consistency-review S8/S9); mit dem Ende beginnt die 24-h-Sperre. Eine nie abgeschlossene Einrichtung gibt so keine Dauer-Freiheit | – | – | `isSetupMode`, `completeSetup`, `onPlaybackStarted` | umgesetzt |
| Zeit | alle Fristen sind `ZenCountdown` (nur Gerätelaufzeit; Uhr vor-/zurückstellen und Neustart verkürzen nichts, Ausschaltzeit zählt nicht; Fortschritt wird beim Hinsehen gespeichert, UI und Zugriffe tun das) | Sperre, Ausstehend, Rückgängig, Setup | – | `ZenCountdown` | umgesetzt |
| Laufendes | laufende Pause und laufende Ausgabe werden durch Änderungen nie verkürzt bzw. neu gewürfelt (Parameter wirken ab dem nächsten Abbruch bzw. der nächsten Ausgabe; Deckel zählt gegen die schon gezählten Videos) | – | – | Konstanten nur zum Entscheidungszeitpunkt gelesen | umgesetzt |
| Zen-Protokoll | `settings_change`, `settings_pending_applied`, `settings_pending_cancelled`, `settings_undo`, `settings_setup_done` | ZenLog | nicht im Export | `ZenSettingsLock` | umgesetzt |
| Notfallschlüssel | standardmäßig aus, nicht gebaut | – | – | – | bewusst nicht umgesetzt |

## Praxis (Details: `spec-praxis.md`)

| Regel | Wert / Default | Gilt für | Ausnahme | Code / Quelle | Status |
|---|---|---|---|---|---|
| Zuordnung | vom Nutzer gepflegte Liste von **Kanälen und (Online-)Playlists** (`serviceId` + normalisierte URL, Playlist mit Flag `p`); keine automatische Erkennung, nie Titel/Stichworte; ein Kanal kann zugleich abonniert/in Gruppen sein, dann sind **alle** seine Videos Praxis. Playlist: Praxis sind die **geladenen** Videos der Playlist (Praxis-Tab lädt die erste Seite, max. 200 je Playlist; die Playlist-Seite ergänzt nachgeladene Seiten; `px_pl_items`), nur solange die Playlist aktiv ist; ein in eine Praxis-Warteschlange eingereihtes fremdes Video ist kein Praxis | Praxis | lokale Playlists nicht (beliebig befüllbar) | `ZenPraxisChannel`, `ZenPraxisPlaylistItems`, `ZenPraxis.isPraxisStream` | umgesetzt |
| Hinzufügen | Kanal **oder Playlist** = **Lockerung**: Bestätigung (3 s), ausstehend, wirksam mit Ende der dadurch gestarteten 24-h-Sperre, abbrechbar, 5-min-Rückgängig; während einer laufenden Sperre abgelehnt (globale Sperre); in der Ersteinrichtung sofort | Praxis | – | `ZenSettingsLock.addPraxis`, `ZenPraxisUi` | umgesetzt |
| Entfernen | **strenger**: sofort, auch während der Sperre, startet keine Sperre, 5-min-Rückgängig; Entfernen eines ausstehenden Kanals = Abbrechen | Praxis | – | `ZenSettingsLock.removePraxis` | umgesetzt |
| Feed | Praxis-Videos (Kanal oder bekannte Playlist-Videos) nicht in der Ausgabe: zählen nicht zu 15/Ausgabe und Deckel 30, Mindestlänge/Längenbonus/Scoring gelten nicht | Feed | – | `ZenFeedPlanner.isEligible` (`Candidate.praxis`) | umgesetzt |
| Pflichtpause | Abbruch eines Praxis-Videos löst **keine** Pause aus (wie Musik, `abort_exempt kind=PRAXIS`); Pausieren bleibt kein Abbruch. **Während einer laufenden Pause dürfen Praxis-Videos starten** (Entscheidung 2026-10-13), alles andere bleibt gesperrt; die Pause läuft unverändert weiter. Maßgeblich ist nur die Zuordnung (Kanal/Playlist), nie Titel/Stichworte; Vorprüfung beim Start mit Listendaten (`uploaderUrl`, ohne = streng gesperrt), verbindliche Prüfung im Player, sobald die `StreamInfo` vorliegt (kein Praxis -> Player stoppt, Pausenbildschirm, `pause_block_running`) | Pause | – | `ZenKind.PRAXIS`, `ZenPauseEngine.allowsStart/blocksRunning`, `ZenPause.blockStart(…, praxis)`, `ZenPause.onStreamInfo` | umgesetzt |
| Unverändert | kein Autoplay, keine Related/Kommentare, keine Shorts/Livestreams, Absichtsabfrage | Praxis | – | – | umgesetzt |
| Praxis-Tab | Tab „Praxis“ (immer direkt nach dem Feed, auch bei alten Tab-Listen) + Drawer-Eintrag: neueste Videos der aktiven Praxis-Kanäle, max. 15 pro Kanal (`ZenPraxis.MAX_PER_CHANNEL`), nach Datum; danach je aktiver Praxis-Playlist ein Playlist-Eintrag (öffnen -> „Alle abspielen“ = Kette) und ihre ersten 15 Videos in Playlist-Reihenfolge; kein Nachladen, kein Kontingent; Kopfzeile „Praxis heute: N min“ | Praxis | – | `ZenPraxisFragment`, `Tab.PraxisTab` (ID 10) | umgesetzt |
| Praxis-Kette | Ein Video, das zu Ende gelaufen ist, darf **nur** dann automatisch ins nächste Element der Warteschlange übergehen, wenn die laufende Sitzung Praxis ist **und** das nächste Element nach Listendaten Praxis ist; sonst bleibt der Player am Ende stehen (`chain_stop`). Verbindlich: Stellt sich das verkettete Element nach `StreamInfo` als Nicht-Praxis heraus, pausiert der Player sofort. Gilt auch während der Pflichtpause, bei Bildschirm aus und im Hintergrund | Praxis | – | `ZenPauseEngine.mayChain/chainBreaks`, `Player.zenOnItemEnd`, `ZenPause.STREAM_CHAIN_STOP` | umgesetzt |
| Markieren | Kanalseite bzw. Playlist-Seite > Menü „Als Praxis-Kanal/-Playlist markieren / Ausstehende(n) abbrechen / Entfernen“; **langer Druck** auf Kanal- und Playlist-Einträgen in Listen (Suche, Kanal-Tabs, alle `BaseListFragment`-Listen) und auf Abos (zusätzlicher Eintrag); Verwaltung in Einstellungen > Zen (Playlists mit Kennzeichnung) | UI | lokale Lesezeichen-Playlists (Tab „Lesezeichen“) haben keinen Eintrag | `ChannelFragment`, `PlaylistFragment`, `BaseListFragment`, `SubscriptionFragment`, `ZenPraxisUi.showLongPress`, `ZenSettingsFragment` | umgesetzt |
| Praxis-Minuten | Wiedergabezeit der Praxis-Sitzungen pro Zen-Tag (04:00, Tag des Sitzungsendes), 30 Tage; Anzeige im Praxis-Tab und in Einstellungen > Zen. **Kein Limit** (Entscheidung); weiches Limit nur als Hook (`ZenPraxisTime.msOn`) | Praxis | – | `ZenPraxisTime` (`px_time`) | umgesetzt |
| Sleep-Timer | Mond-Knopf in der Player-Kopfzeile (Video- und Popup-Player, zeigt den Countdown m:ss) und Menüeintrag in der Warteschlange (Hintergrund-Player); Dauer 15/30/45/60/90 min (Debug-Builds zusätzlich 1 min), **kein „Ende des Videos“** (es startet ohnehin nichts von selbst). Läuft auf Gerätelaufzeit (`elapsedRealtime`), über Videogrenzen (Praxis-Kette) hinweg, endet mit dem Schließen des Players; Neustart ersetzt die Dauer. Letzte 30 s Lautstärke linear auf 5 %, dann **Pause** und Lautstärke zurück; das Sitzungsende zählt **nicht als Abbruch** (keine Pflichtpause, kein „Später“-Verbrauch, `sleep_end`), Praxis-Minuten zählen; Weiterspielen startet eine neue Sitzung. Standard-Dauer (zuerst angeboten) in Einstellungen > Zen, ohne Sperre. Hook für Einschlaferkennung: `ZenSleepSignal` (`research-einschlaferkennung.md`) | Player | – | `ZenSleepTimer`, `ZenSleep`, `ZenSleepUi`, `ZenPauseEngine.endBySleepTimer` | umgesetzt |
| Zustand/Protokoll | `zen_state` (`zs_praxis`, `zp_praxis`, `px_time`, `px_pl_items`, `sleep_default_min`), nicht in Export/Import/Reset/Auto-Backup; ZenLog `praxis_add`, `praxis_add_pending`, `praxis_activated`, `praxis_pending_cancelled`, `praxis_remove`, `chain_stop`, `sleep_timer_start/_cancel/_end`, `sleep_end` | – | – | `ZenSettingsLock`, `ZenPause`, `ZenSleep` | umgesetzt |

## Funktionen (zentrale Schalter, `zen/ZenFeatures.kt`)

| Funktion | Wert | Einstiegspunkte (alle prüfen `ZenFeatures`) | Bleibt | Status |
|---|---|---|---|---|
| Downloads (`downloadsEnabled()`) | **aus** (Konstante `DOWNLOADS_ENABLED = false`; später per Einstellung freischaltbar, dann nur die Funktion ändern) | Download-Knopf der Detailseite (ausgeblendet, `openDownloadDialog` no-op), Long-Press-Menü (`InfoItemDialog.Builder.addEntry` lässt `DOWNLOAD` weg), Warteschlangen-Menü (`QueueItemMenuUtil`), Teilen/Link-Auswahl „Download“ (`RouterActivity`; gespeicherte Bevorzugung „Download“ wird zu „Info anzeigen“), Einstellung „Bevorzugte Öffnen-Aktion“ ohne „Download“, Einstellungen > Download (entfernt, nicht in der Einstellungs-Suche), `NavigationHelper.openDownloads` (no-op), `DownloadActivity` (beendet sich sofort), `DownloadDialog` (schließt sich), `DownloadManagerService.onStartCommand` (stoppt sich, keine Missionen/Benachrichtigung). Drawer hatte schon keinen Downloads-Eintrag. | Streaming inkl. Hintergrund-Audio und Popup. Manifest-Komponenten bleiben unverändert (beide `exported=false`), Code nicht gelöscht (Upstream-Merges) | umgesetzt |

## Externe Links

| Regel | Wert | Code / Quelle | Status |
|---|---|---|---|
| YouTube-Links öffnen ZenPipe | Intent-Filter (geerbt) für `youtube.com`, `www.`/`m.`/`music.youtube.com` (`/watch`, `/v/`, `/embed/`, `/live/`, `/channel/`, `/user/`, `/c/`, `/@`, `/playlist`, `/shorts/`), `youtu.be`, `youtube-nocookie.com/embed/`, `vnd.youtube:`; ohne `autoVerify` | `AndroidManifest.xml` (`RouterActivity`) | umgesetzt |
| Weg | Link -> `RouterActivity` (Auswahl) -> Info/Player; Info öffnet `MainActivity` mit Absichtsabfrage, Wiedergabe über `ZenIntentPrompt.gate` | `RouterActivity`, `ZenIntentPrompt` | umgesetzt |
| Shorts-Links | blockiert (Toast), keine Wiedergabe | `RouterActivity` | umgesetzt |
| Standard-Handler | Nutzer stellt selbst ein (Android 11: Einstellungen > Apps > ZenPipe > Erweitert > Standardmäßig öffnen > Unterstützte Links öffnen > „In dieser App öffnen“; Android 12+: „Link hinzufügen“). Am Defy geprüft: mit diesem Wert öffnet ein `youtu.be`-Link ZenPipe direkt, obwohl die YouTube-App verifiziert ist; sonst ggf. bei YouTube/YouTube Music „Jedes Mal fragen“ | README | Doku, am Gerät geprüft |
