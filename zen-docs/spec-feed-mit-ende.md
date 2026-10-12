# Spec: Feed mit Ende („Ausgabe" statt endlosem Scrollen)

Anforderung (Nutzer, 2026-10-12): Kein endloses Scrollen im Feed; gute Struktur/Regel entwerfen.

## Prinzip
Der Feed ist keine Strömung, sondern eine **Ausgabe** (wie eine Zeitung): begrenzt, abgeschlossen, mit klarem Ende.
Wer die Ausgabe durch hat, ist durch — bis zur nächsten Ausgabe.

## Regeln (Defaults, als Konstanten im `zen`-Paket)
1. **Ausgabe-Größe und Takt (Entscheidung Nutzer 2026-10-12):** Jede Ausgabe enthält **15 verschiedene Videos**. Eine neue Ausgabe entsteht frühestens **60 Minuten nach der letzten** (Konstante `EDITION_INTERVAL_MS`, nicht zur vollen Stunde) oder beim ersten Öffnen eines neuen Zen-Tags. Nicht erledigte Videos werden nicht aufgestockt, sondern warten und werden neu gewichtet („warten statt verfallen"; nur was älter als 7 Tage wird, fällt heraus).
   **Tages-Deckel (Entscheidung Nutzer):** max. **30 verschiedene Videos pro Zen-Tag** (Tageswechsel 4:00). Ein Video, das in mehreren Ausgaben auftaucht, zählt nur einmal. Ist der Deckel erreicht, entsteht bis 4:00 keine neue Ausgabe („Nächste Ausgabe ab 04:00").
   **Erst nach der Absichtsabfrage:** Die Ausgabe wird erst gebaut, wenn die Absichtsabfrage bestätigt wurde; Öffnen + „Doch nicht" verbraucht nichts.
2. **Auswahl („passt zum Nutzer"):** Kandidaten sind ungesehene Videos aus den eigenen Abos, **maximal 7 Tage alt**. Die Auswahl der 15 erfolgt über ein **transparentes, lokales Scoring** — kein Empfehlungsalgorithmus, keine YouTube-Empfehlungen, keine Daten verlassen das Gerät:
   - **Explizite Signale (Vorrang):** Kanal-Priorität bzw. Feed-Gruppe, vom Nutzer gesetzt (z. B. „Lernen" > „Unterhaltung" > „Nebenbei"), plus Stichwort-Filter (ausblenden/bevorzugen, vgl. PipePipe).
   - **Implizite lokale Signale (schwach gewichtet):** pro Kanal Anteil zu Ende geschauter Videos und geschaute Minuten (aus dem lokalen Verlauf); Kanäle, deren Videos fast immer abgebrochen werden, rutschen nach unten.
   - **Frische:** neuere Videos leicht bevorzugt.
   - **Vielfalt statt Sog:** je Kanal max. 3 Videos pro Ausgabe; keine „mehr davon"-Schleife, kein Boost durch Klicks auf Thumbnails/Reihenfolge.
   - **Erklärbar:** Jeder Eintrag zeigt auf Wunsch den Grund („Gruppe Lernen · du schaust diesen Kanal meist zu Ende").
   - **Längenfokus (Entscheidung Nutzer):** Die Ausgabe soll längere Videos bevorzugen (deshalb nur wenige pro Tag). **Mindestlänge 5 min** (`MIN_DURATION_SEC`); kürzere Videos, Livestreams und Videos mit unbekannter Länge kommen nicht vor. **Längenbonus:** ab 15 min +12 Punkte, ab 30 min zusätzlich +6; kleiner als ein Gruppenschritt (15), also nie stärker als die Gruppen-/Kanal-Priorität. Alle Gewichte sind benannte Konstanten in `ZenFeedPlanner`.
   - **Keine Musik:** Musik-Kanäle (Topic-/VEVO-Kanäle, Feed-Gruppe „Musik"/„Music") und Titel mit „Official Video/Audio", „Lyrics", „Full Album" kommen nicht vor; Titel mit „Mix", „Playlist", „DJ Set", „Lofi" u. Ä. bekommen keinen Längenbonus. Reine Heuristik auf Kanal-/Titelebene (die Feed-DB hat keine Kategorie).
   - **Anzeige:** Jeder Eintrag zeigt Länge und Gruppe („42 min · Gruppe Lernen").
   Bereits angesehene Videos kommen nicht vor.
3. **Ende:** Nach dem letzten Eintrag steht eine Abschlusskarte „Du bist durch. Nächste Ausgabe um HH:00." — kein „Mehr laden", kein Nachladen beim Scrollen, kein Pull-to-load-more.
4. **Aktualisieren ist kein Nachschub:** Pull-to-refresh holt nur neue Daten aus dem Netz in den Vorrat. Die laufende Ausgabe wächst dadurch nicht.
5. **Nächste Ausgabe:** Siehe Regel 1 (60 min nach der letzten). Nicht gesehene Videos warten (Zeitfenster 7 Tage) oder wandern per Knopf in „Später ansehen". Die Später-Liste fasst max. 10 Einträge; Einträge laufen nach 30 Tagen ab.
6. **Triage statt Scrollen:** Jeder Eintrag hat zwei Aktionen: „Später" (in die budgetierte Später-Liste, max. 10) und „Verwerfen" (aus der Ausgabe nehmen). Ausgabe gilt als erledigt, wenn alles gesehen/verworfen/gemerkt ist oder man am Ende ist.
7. **Rest-Info:** Abschlusskarte zeigt, wie viele Videos nicht in die Ausgabe passten („23 weitere warten"; bei erreichtem Deckel „bleiben heute liegen") — bewusst ohne Schuldgefühl formuliert.

## Gleiche Regel überall („keine endlosen Listen")
- **Kanalseiten:** nur die letzten 10–15 Videos, keine Paginierung; Ende-Karte „Weiter geht's in der Suche".
- **Suche:** höchstens 2 Ergebnisseiten, dann „Suche verfeinern".
- **Playlists:** endlich, ohne Eingriff. „Alle abspielen" bleibt entfernt (siehe Phase 0).
- **Verlauf/Bibliothek:** nicht betroffen (nicht algorithmisch, endlich).

## Umgehungsschutz
- Ausgabe-Zustand (Zusammenstellung, Zeitstempel, verworfen/gemerkt), Videos nach URL geschlüsselt (Daten-Import ändert DB-IDs) in eigener `zen`-Einstellungsdatei; nicht durch Daten-Import/Reset oder App-Neustart neu würfelbar.
- Neue Ausgabe frühestens 60 min nach der letzten, Deckel 30 verschiedene Videos pro Zen-Tag; Zeitzonenwechsel verschiebt den Zen-Tag nicht (die beim ersten Start gespeicherte Zone gilt); Uhr-Manipulation erkennen (monotone Zeit + Wanduhr vergleichen).
- Abo-Hygiene als Gegenstück: Viele Abos = große Auswahl, aber das Budget bleibt gleich, daher kein Anreiz zum Aufblähen.

## Technisch (zu prüfen vom Umsetzungs-Agent)
- NewPipe-Feed lädt aus der DB (FeedFragment/FeedViewModel/FeedDatabaseManager), Zeitfenster und „gesehene ausblenden" gibt es als Einstellungen; Budget/Ende lässt sich vermutlich als Filter/Limit in der Abfrage plus Footer-Item lösen. Kleine, lokale Patches bevorzugen.
- Footer-Karte „Du bist durch" als eigenes Item oder Footer-View; kein Nachlade-Trigger (Paging deaktivieren).

## Entscheidungen
- Tages-Deckel 30 verschiedene Videos; Scoring-Start: Gruppen-Priorität + Längenbonus + Vielfalt + Frische; implizite Zu-Ende-Signale später (Phase 2).
- Offen: Stichwort-Filter; Rest-Info bei erreichtem Deckel gesondert formuliert; Hinweis beim Ablauf der Später-Einträge; Nachtruhe.
