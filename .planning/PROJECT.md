# ZenPipe

**Stack:** kotlin-android (Fork von NewPipe, GPLv3)

## Zweck
Achtsamerer YouTube-Konsum: NewPipe-Fork ohne Feed-Sog. Ein Werkzeug zum bewussten Schauen.

## Kernwert
Man schaut, was man vorhatte — und ist dann fertig. Kein Autoplay, keine Empfehlungen, kein endloses Scrollen.

## Kernregeln (Details und Werte: `zen-docs/rules.md`, Single Source of Truth)
- Autoplay dauerhaft aus; Auto-Queue, Related und Kommentare aus; Shorts nirgends; keine Livestreams im Feed/Kanal-Tab; YouTube-Links bleiben in der App (Link kopieren statt Browser).
- Drawer nur Abos, Feed, Playlists, Suche. Startseite = Feed.
- Feed mit Ende: Ausgabe mit 15 Videos (frühestens 60 min nach der letzten), Tages-Deckel 30 verschiedene Videos pro Zen-Tag (Wechsel 04:00), lokales erklärbares Scoring (Feed-Gruppen-Reihenfolge = Priorität, Längenfokus), keine Musik im Feed, Später-Liste max. 10 (30 Tage). Kanalseiten 15 Einträge, Suche 2 Seiten.
- Absichtsabfrage (5 s Wartezeit) bei App-Start (auch nach „Doch nicht“), geteilten Links inkl. Hintergrund-/Popup-Wiedergabe über den Router und Rückkehr nach >= 5 min; nur die eigene laufende Wiedergabe unterdrückt sie.
- Pflichtpause: Abbruch eines Videos (Schließen, Videowechsel, Prozess-Kill; Pausieren ist kein Abbruch) nach >= 5 min Wiedergabezeit -> Pause 20 % der Wiedergabezeit (min 1, max 15 min); Musik ausgenommen, Podcasts inklusive; „Später fortsetzen“ ohne Pause 2x pro Zen-Tag. Pause zählt nur Gerätelaufzeit (Uhr-/Neustart-fest).
- Einstellungs-Sperre (Phase 2, Spec): nach jeder Änderung 24 h keine weitere; strenger sofort, lockern erst nach 24 h und abbrechbar; 5 min Rückgängig; Feed-Gruppen nicht gesperrt.
- Entdecken verwandter Kanäle (Phase 2, opt-in, Spec). Minuten-Tageslimit erst nach der Einstellungs-Sperre.

## Stand
MVP, Phase 0 (Lücken schließen), Phase 1 (Zen-Paket, Feed mit Ende, Absichtsabfrage-Lücken, Pflichtpause) umgesetzt. Siehe `.planning/STATE.md`.

## Später
Zen-Einstellungen mit 24-h-Sperre, Entdecken, Minuten-Tageslimit, Nachtruhe, Graustufen/ruhiges Design, Branding/applicationId/Signierung.

## Risiken
Wartung gegen Upstream/Extractor bei YouTube-Änderungen; ToS-Hinweis (siehe Konzept §4).

## Offene Punkte
Name/Branding, applicationId, Upstream-Strategie.
