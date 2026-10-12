# ZenPipe: Regel-Tabelle (Single Source of Truth)

Stand: 2026-10-12, nach Phase 1 Teil 2. Alle Specs verweisen hierher; bei Widerspruch gilt diese Datei. Die Konstanten im Code
tragen dieselben Namen (Spalte „Code“). Entscheidungen F1-F9 stehen am Ende von `consistency-review.md`.
Status: **umgesetzt** = im Code und am Gerät geprüft; **Spec** = nur beschrieben; **offen** = nicht entschieden/nicht gebaut.

## Grundregeln

| Regel | Wert / Default | Gilt für | Ausnahme | Code / Quelle | Status |
|---|---|---|---|---|---|
| Autoplay (geöffnetes Video) | aus, fest (Nutzerentscheidung, auch nicht für gewählte Videos) | alle Player | – | `PlayerHelper.getAutoplayType` | umgesetzt |
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
| Tages-Deckel (F1) | 30 **verschiedene** Videos pro Zen-Tag | Feed | Später-Liste, Playlists, gezielte Suche | `ZenFeedPlanner.DAILY_CAP` | umgesetzt |
| Kandidaten | ungesehen, aus Abos, <= 7 Tage, kein Live, keine Musik (F3), Länge >= 5 min (gilt **nur im Feed**) | Feed | – | `ZenFeedPlanner.isEligible`, `MAX_AGE_DAYS`, `MIN_DURATION_SEC` | umgesetzt |
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
| Auslöser | Abbruch einer Sitzung mit Wiedergabezeit >= 5 min | alle Player (Video, Popup, Hintergrund, Router) | Musik; „Später fortsetzen“ (F6) | `ZenPauseRules.MIN_WATCH_MS` | umgesetzt |
| Abbruch | Player schließen (auch pausiert), Videowechsel, Prozess-Kill (beim nächsten Start erkannt) | Pause | Pausieren allein, Anruf/Audio-Fokus, zu Ende geschaut | `ZenPause`, `App.onCreate` | umgesetzt (Lücke: Mini-Player nach unten ziehen ohne Rückfrage) |
| Pausenlänge | 20 % der Wiedergabezeit, min 1, max 15 min; nie kürzer als eine laufende | Pause | – | `PAUSE_FRACTION`, `MIN_PAUSE_MS`, `MAX_PAUSE_MS` | umgesetzt |
| Verhalten | Wiedergabestart gesperrt (Pausenbildschirm mit Countdown, kein Überspringen), Browsen/Suchen erlaubt, endet nur durch Zeitablauf auf Gerätelaufzeit (nicht durch Stunden-/Tageswechsel, Uhr, Neustart) | alle Starts | – | `ZenPause.blockStart`, `PlayerService`-Netz | umgesetzt |
| Musik vs. Podcast | Musik = Kategorie „Music“ / Topic- oder VEVO-Kanal / Titelwörter; Podcast = Hinweise oder >= 20 min Audio (Pause gilt wie Video); unsicher = streng | Pause | – | `ZenContentKind.classify` (StreamInfo.category/tags) | umgesetzt |
| Später fortsetzen (F6) | 2x pro Zen-Tag, keine Pause, Rückfrage beim Mini-Player-X, nur wenn das Schließen eine Pause auslösen würde | Pause | – | `ZenPauseRules.LATER_PER_DAY`, `ZenPause.confirmClose` | umgesetzt |
| Protokoll | `pause_start`, `pause_end`, `abort_short`, `abort_exempt`, `watch_end`, `later` | ZenLog | nicht im Export | `ZenLog` | umgesetzt |

## Einstellungs-Sperre (Details: `spec-einstellungs-sperre.md`)

| Regel | Wert / Default | Gilt für | Ausnahme | Code / Quelle | Status |
|---|---|---|---|---|---|
| Sperre (F7) | nach jeder bestätigten Änderung 24 h (Gerätelaufzeit-Dauer) keine weitere Änderung, global | Ausgabe-Größe, Deckel, Mindestlänge, Pause-/Abfrage-Parameter, Nachtruhe, Entdecken an, Graustufen aus | Ersteinrichtung (bis „Fertig“, erstes Video oder 24 h) | Spec | Spec (Phase 2); bis dahin nur Konstanten |
| Asymmetrie (F7) | strenger wirkt sofort und bleibt während der Sperre erlaubt (ohne Neustart der Sperre); lockern wirkt erst nach 24 h und ist abbrechbar | s. o. | Kosmetik ohne Lockerungswirkung startet keine Sperre | Spec | Spec |
| Nicht gesperrt (F8) | Feed-Gruppen (Zuordnung/Reihenfolge), Abos, Playlists, Suche, normale Player-Einstellungen | – | – | Spec | Spec |
| Rückgängig (F9) | 5 min nach Bestätigung, exakt zurück, startet keine Sperre | Zen-Einstellungen | – | Spec | Spec |
| Bestätigung | 3 s Wartezeit mit Zusammenfassung | Speichern | – | Spec | Spec |
| Laufendes | laufende Pause und laufende Ausgabe werden durch Änderungen nie verkürzt bzw. neu gewürfelt | – | – | Spec | Spec |
