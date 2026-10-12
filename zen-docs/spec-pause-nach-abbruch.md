# Spec: Pflichtpause nach abgebrochenem Video

Anforderung (Nutzer, 2026-10-12): Wird ein Video nach mindestens 5 Minuten Anschauzeit abgebrochen,
folgt eine anteilige, verpflichtende Pause.

## Vorschlag (Defaults, als Konstanten in `zen`-Paket, später konfigurierbar)
- **Auslöser:** Wiedergabe eines Videos wird beendet, *bevor* es zu Ende ist (Stop, Wegwischen, Zurück,
  App-Wechsel länger als X Sek.) und die reine Wiedergabezeit dieses Videos beträgt >= 5 min.
  Zu Ende geschaute Videos lösen keine Pause aus.
- **Musik:** keine Pause (Entscheidung Nutzer 2026-10-12).
- **Podcasts:** Pause gilt (Entscheidung Nutzer), auch im Hintergrund-/Audio-Player.
- **Pausieren im Player ist KEIN Abbruch** (Entscheidung Nutzer). Abbruch = Beenden/Verlassen/Wegwischen.
- **Pausenlänge:** anteilig an der Wiedergabezeit, Default 20 % (z. B. 10 min geschaut -> 2 min Pause,
  60 min -> 12 min), Minimum 1 min, Maximum 15 min.
- **Während der Pause:** Wiedergabe neuer Videos gesperrt (Zen-Pausenbildschirm mit Countdown, kein Überspringen).
  Browsen/Suchen bleibt erlaubt; Playlists/Downloads nur ansehen.
- **Umgehungsschutz:** Pausenende als monotone Zeit (SystemClock.elapsedRealtime) + Uhrzeit speichern;
  Uhr-Manipulation erkennen; Zustand in eigener `zen`-Einstellungsdatei (nicht über Daten-Import/Reset
  löschbar). Keine Abkürzung in den Einstellungen (Lockern erst am nächsten Tag, siehe Time-Lock-Konzept).
- **Protokoll:** Jede Pause und jeder Abbruch wird im Zen-Protokoll vermerkt (Basis für Wochenbilanz).

## Offene Punkte
- Prozentsatz/Min/Max passend?
- **Technik: Musik vs. Podcast unterscheiden.** NewPipe hat dafür kein Flag. Vorschlag (Heuristik, in dieser
  Reihenfolge): (1) YouTube-Kategorie „Music" bzw. Topic-Kanäle/Musik-Metadaten aus dem Extractor -> Musik;
  (2) Streams mit Länge >= 20 min im Audio-Modus oder Titel/Kanal-Hinweis „Podcast"/„Folge"/„Episode" -> Podcast;
  (3) sonst wie Video (Pause gilt). Unsichere Fälle zählen als Video/Podcast (strenger). Prüfen, was der
  Extractor an Kategorie-/Tag-Daten liefert, bevor die Heuristik festgelegt wird.
