# Spec: Feed mit Ende („Ausgabe" statt endlosem Scrollen)

Anforderung (Nutzer, 2026-10-12): Kein endloses Scrollen im Feed; gute Struktur/Regel entwerfen.

## Prinzip
Der Feed ist keine Strömung, sondern eine **Ausgabe** (wie eine Zeitung): begrenzt, abgeschlossen, mit klarem Ende.
Wer die Ausgabe durch hat, ist durch — bis zur nächsten Ausgabe.

## Regeln (Defaults, als Konstanten im `zen`-Paket)
1. **Budget:** Eine Ausgabe enthält höchstens **15 Videos** (Spanne 10–25 konfigurierbar, aber nur strenger stellen sofort, Lockern ab nächstem Tag).
2. **Auswahl:** Neueste ungesehene Videos aus den Abos, **maximal 7 Tage alt**, nach Datum absteigend. Ungesehen/angeschaut-Markierungen zählen; bereits angesehene Videos kommen nicht vor. Optional Phase 2: Feed-Gruppen als Priorität (z. B. „Lernen" vor „Unterhaltung"), je Kanal max. 3 Videos pro Ausgabe, damit ein Vielposter nicht alles füllt.
3. **Ende:** Nach dem letzten Eintrag steht eine Abschlusskarte „Du bist durch. Nächste Ausgabe morgen ab 4:00." — kein „Mehr laden", kein Nachladen beim Scrollen, kein Pull-to-load-more.
4. **Aktualisieren ist kein Nachschub:** Pull-to-refresh holt nur neue Daten aus dem Netz in den Vorrat. Die laufende Ausgabe wächst dadurch nicht.
5. **Nächste Ausgabe:** Wird zum Tageswechsel (4:00 Uhr, wie beim Tageslimit) neu zusammengestellt. Optional (Entscheidung offen): zweite Ausgabe am Abend. Nicht gesehene Videos fallen weg (Zeitfenster 7 Tage) oder wandern per Knopf in „Später ansehen".
6. **Triage statt Scrollen:** Jeder Eintrag hat zwei Aktionen: „Später" (in die budgetierte Später-Liste, max. 10) und „Verwerfen" (aus der Ausgabe nehmen). Ausgabe gilt als erledigt, wenn alles gesehen/verworfen/gemerkt ist oder man am Ende ist.
7. **Rest-Info:** Abschlusskarte zeigt, wie viele Videos nicht in die Ausgabe passten („23 weitere verfallen") — bewusst ohne Schuldgefühl formuliert.

## Gleiche Regel überall („keine endlosen Listen")
- **Kanalseiten:** nur die letzten 10–15 Videos, keine Paginierung; Ende-Karte „Weiter geht's in der Suche".
- **Suche:** höchstens 2 Ergebnisseiten, dann „Suche verfeinern".
- **Playlists:** endlich, ohne Eingriff. „Alle abspielen" bleibt entfernt (siehe Phase 0).
- **Verlauf/Bibliothek:** nicht betroffen (nicht algorithmisch, endlich).

## Umgehungsschutz
- Ausgabe-Zustand (Zusammenstellung, Zeitstempel, gesehen/verworfen) in eigener `zen`-Einstellungsdatei; nicht durch Daten-Import/Reset oder App-Neustart neu würfelbar.
- Neue Ausgabe nur zum Tageswechsel; Uhr-Manipulation erkennen (monotone Zeit + Wanduhr vergleichen).
- Abo-Hygiene als Gegenstück: Viele Abos = große Auswahl, aber das Budget bleibt gleich, daher kein Anreiz zum Aufblähen.

## Technisch (zu prüfen vom Umsetzungs-Agent)
- NewPipe-Feed lädt aus der DB (FeedFragment/FeedViewModel/FeedDatabaseManager), Zeitfenster und „gesehene ausblenden" gibt es als Einstellungen; Budget/Ende lässt sich vermutlich als Filter/Limit in der Abfrage plus Footer-Item lösen. Kleine, lokale Patches bevorzugen.
- Footer-Karte „Du bist durch" als eigenes Item oder Footer-View; kein Nachlade-Trigger (Paging deaktivieren).

## Offene Entscheidungen
- Budget 15 passend? Eine Ausgabe pro Tag oder zusätzlich abends?
- Gilt das Budget pro Tag oder pro Öffnung? (Empfehlung: pro Tag.)
