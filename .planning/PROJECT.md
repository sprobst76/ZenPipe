# ZenPipe

**Stack:** kotlin-android (Fork von NewPipe, GPLv3)

Kurzbild des Projekts (Stand 2026-10-14). Alle Werte und Regeln stehen in `zen-docs/rules.md` (Single Source of Truth),
Begründungen in `zen-docs/decisions.md`, der Einstieg in die Doku in `zen-docs/README.md`, der Fortschritt in `.planning/STATE.md`.
Diese Datei wiederholt bewusst keine Zahlen.

## Zweck
Achtsamerer YouTube-Konsum: NewPipe-Fork ohne Feed-Sog. Ein Werkzeug zum bewussten Schauen.

## Kernwert
Man schaut, was man vorhatte — und ist dann fertig. Kein Autoplay, keine Empfehlungen, kein endloses Scrollen.

## Bausteine (umgesetzt)
- Kein Sog: Autoplay/Weiterspielen, Related, Kommentare, Shorts aus; Pop-up-Player, Downloads, Minimieren beim App-Wechsel und
  Upstream-Ankündigungen über `ZenFeatures` aus; Bild läuft nie nebenbei.
- Feed als endliche Ausgabe mit Tages-Deckel, lokalem erklärbarem Scoring und Sprachfilter; Absichtsabfrage.
- Pflichtpause nach Abbruch (mit Ruhezeit-Anrechnung), Wiedergabezeit pro Zen-Tag (Bild/Ton/Musik/Praxis, ohne Limit).
- Zen-Einstellungen mit Asymmetrie: strenger sofort, Lockern erst nach 24-h-Sperre, 5-min-Rückgängig, Ersteinrichtung frei.
- Praxis-Kanäle/-Playlists (Ausnahmen von Ausgabe und Pause, Praxis-Kette, Sleep-Timer).
- Lokale Inhalts-Erkennung (Opt-in-Modell): Feed-Gewichtung, Prüfung beim Markieren, Ausnahmen, „Kanal übernehmen“,
  Prüfansicht mit Korrekturen und Debug-Export.
- Eigene Identität und Verteilung: applicationId `io.github.sprobst76.zenpipe`, eigener Signierschlüssel, GitHub-Releases,
  eigenes F-Droid-Repo, Obtainium.

## Offen
Bildung ohne Politik (AP 7), Minuten-Tageslimit (F2, Wert offen), Entdecken verwandter Kanäle, Nachtruhe, Abo-Hygiene.

## Risiken
Wartung gegen Upstream/Extractor bei YouTube-Änderungen; ToS-Hinweis (Konzept §4); Rate-Limits bei Detailabrufen
(gedrosselt, siehe rules.md „Details nachladen“).
