# Spec: Feed mit Ende („Ausgabe" statt endlosem Scrollen)

Anforderung (Nutzer, 2026-10-12): Kein endloses Scrollen im Feed; gute Struktur/Regel entwerfen.

## Prinzip
Der Feed ist keine Strömung, sondern eine **Ausgabe** (wie eine Zeitung): begrenzt, abgeschlossen, mit klarem Ende.
Wer die Ausgabe durch hat, ist durch — bis zur nächsten Ausgabe.

## Regeln (Defaults, als Konstanten im `zen`-Paket)
1. **Budget (Entscheidung Nutzer 2026-10-12):** Pro **Stunde** stehen **15 verschiedene Videos** zur Verfügung (Spanne 10–25 konfigurierbar, nur strenger stellen wirkt sofort, Lockern ab nächstem Tag). Eine neue Stunden-Ausgabe wird zur vollen Stunde freigeschaltet; Unerledigtes der vorigen Stunde wird nicht aufgestockt, sondern neu gewichtet. **Tages-Deckel (Empfehlung, offen):** max. 45 Videos/Tag, sonst ergäbe 15/h theoretisch >300 am Tag und das Ende wäre wieder Fiktion.
2. **Auswahl („passt zum Nutzer"):** Kandidaten sind ungesehene Videos aus den eigenen Abos, **maximal 7 Tage alt**. Die Auswahl der 15 erfolgt über ein **transparentes, lokales Scoring** — kein Empfehlungsalgorithmus, keine YouTube-Empfehlungen, keine Daten verlassen das Gerät:
   - **Explizite Signale (Vorrang):** Kanal-Priorität bzw. Feed-Gruppe, vom Nutzer gesetzt (z. B. „Lernen" > „Unterhaltung" > „Nebenbei"), plus Stichwort-Filter (ausblenden/bevorzugen, vgl. PipePipe).
   - **Implizite lokale Signale (schwach gewichtet):** pro Kanal Anteil zu Ende geschauter Videos und geschaute Minuten (aus dem lokalen Verlauf); Kanäle, deren Videos fast immer abgebrochen werden, rutschen nach unten.
   - **Frische:** neuere Videos leicht bevorzugt.
   - **Vielfalt statt Sog:** je Kanal max. 3 Videos pro Ausgabe; keine „mehr davon"-Schleife, kein Boost durch Klicks auf Thumbnails/Reihenfolge.
   - **Erklärbar:** Jeder Eintrag zeigt auf Wunsch den Grund („Gruppe Lernen · du schaust diesen Kanal meist zu Ende").
   Bereits angesehene Videos kommen nicht vor.
3. **Ende:** Nach dem letzten Eintrag steht eine Abschlusskarte „Du bist durch. Nächste Ausgabe um HH:00." — kein „Mehr laden", kein Nachladen beim Scrollen, kein Pull-to-load-more.
4. **Aktualisieren ist kein Nachschub:** Pull-to-refresh holt nur neue Daten aus dem Netz in den Vorrat. Die laufende Ausgabe wächst dadurch nicht.
5. **Nächste Ausgabe:** Wird zur vollen Stunde neu zusammengestellt (Tageszähler läuft bis 4:00 Uhr, wie beim Tageslimit). Nicht gesehene Videos fallen weg (Zeitfenster 7 Tage) oder wandern per Knopf in „Später ansehen".
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
- Tages-Deckel 45 ja/nein bzw. andere Zahl?
- Reicht das Scoring (Gruppen-Priorität + lokale Zu-Ende-Quote + Stichwortfilter) oder sollen Kanal-Prioritäten zunächst nur manuell sein? (Empfehlung: Start mit Gruppen-Priorität + Vielfalt, implizite Signale in Phase 2.)
