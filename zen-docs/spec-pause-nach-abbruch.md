# Spec: Pflichtpause nach abgebrochenem Video

Anforderung (Nutzer, 2026-10-12): Wird ein Video nach mindestens 5 Minuten Anschauzeit abgebrochen,
folgt eine anteilige, verpflichtende Pause. Umgesetzt in `org.schabi.newpipe.zen` (`ZenPause.kt`,
`ZenPauseActivity.kt`), Werte siehe `rules.md` (Single Source of Truth).

## Begriffe
- **Sitzung:** eine Wiedergabe eines Videos (URL) im Player. Beenden oder Wechsel des Videos beendet die Sitzung,
  erneutes Öffnen desselben Videos beginnt eine neue Sitzung (Fortsetzen bei Minute 40 zählt wieder ab 0).
- **Wiedergabezeit:** echte Abspielzeit der Sitzung (Zeit im Zustand „spielt“). Geschwindigkeit egal; Pausieren, Puffern,
  Spulen und Springen zählen nicht. Mehrere Sitzungen desselben Videos werden **nicht** addiert.
- **Zu Ende geschaut:** Position >= 90 % der Länge **oder** Rest <= 60 s (bei Videos unter 10 min greift die Restzeit-Regel,
  sonst die 90-%-Regel). Ein 5:20-Video, das bei 5:05 endet, ist zu Ende geschaut. Ohne bekannte Länge gilt es nie als zu Ende.

## Abbruch
Eine Sitzung ist **abgebrochen**, wenn sie endet, ohne dass sie zu Ende geschaut ist, durch:
- Schließen des Players (Mini-Player-X, Schließen in der Benachrichtigung, Wegwischen der Benachrichtigung, Task wegwischen bei
  Video-Player), **auch wenn das Video dabei pausiert war** (pausiert schließen = Abbruch);
- **Videowechsel:** ein anderes Video wird gestartet (auch Öffnen einer anderen Detailseite, die den Player stoppt, oder
  Überspringen in einer Playlist);
- **Prozess-Kill:** der Fortschritt wird alle 10 s in `zen_state` geschrieben (`ps_*`). Findet der nächste App-Start eine offene
  Sitzung, gilt sie als abgebrochen (Protokoll `reason=KILLED`). Die Pause beginnt dann beim Erkennen, nicht rückwirkend.
  Folge: Auch ein Absturz oder `adb install -r` während der Wiedergabe löst die Pause aus (bewusst streng).

**Kein Abbruch:** Pausieren im Player (auch lange und im Hintergrund), Anruf/Audio-Fokus-Verlust (der Player pausiert, die
Sitzung bleibt offen), Display aus, Wechsel zwischen Video-, Popup- und Hintergrundplayer desselben Videos, „zu Ende geschaut“,
„Später fortsetzen“, **Ablauf des Sleep-Timers** (Sitzung endet ohne Pause, `sleep_end`; Weiterspielen beginnt eine neue Sitzung). Nicht erfasst (Lücke): Ziehen des Mini-Players nach unten (Abbruch ohne Rückfrage), Android-Auto-Wiedergabe.

## Regeln
- **Auslöser:** Abbruch einer Sitzung mit Wiedergabezeit >= 5 min, die nicht Musik ist. Kürzere Abbrüche werden nur protokolliert
  (`abort_short`).
- **Musik:** keine Pause (`abort_exempt`). **Podcasts:** Pause gilt, auch im Hintergrund-/Audio-Player.
- **Pausenlänge:** 20 % der Wiedergabezeit (10 min -> 2 min, 60 min -> 12 min), Minimum 1 min, Maximum 15 min.
  Läuft schon eine Pause, wird sie nie verkürzt (es gilt das Maximum aus Rest und neuer Länge).
- **Während der Pause:** Der Start jeder Wiedergabe (Detailseite, Popup, Hintergrund, Playlist, geteilter Link, Einreihen bei
  geschlossenem Player) zeigt den Pausenbildschirm mit Countdown statt zu starten, ohne Überspringen. **Ausnahme Praxis**
  (Entscheidung 2026-10-13): Videos eines aktiven Praxis-Kanals bzw. einer Praxis-Playlist (`spec-praxis.md`) dürfen starten;
  die Pause läuft dabei unverändert weiter, ein Praxis-Abbruch erzeugt keine neue Pause. Zählt nur die Zuordnung, nie der Titel.
  Die Startstellen prüfen mit Listendaten (`uploaderUrl` von Warteschlange/Detailseite; fehlt sie = gesperrt), der Player prüft
  verbindlich, sobald die `StreamInfo` vorliegt, und stoppt alles, was kein Praxis-Video ist (auch das nächste Element einer
  Warteschlange, ein in eine Praxis-Warteschlange eingereihtes Video oder die Lücke „Videowechsel in der Playlist“). **Browsen, Suchen und
  Ansehen von Listen bleiben erlaubt.** Reihenfolge bei geteilten Links: erst Absichtsabfrage, dann (falls die Pause läuft) Pausenbildschirm. Die Abfrage beim
  App-Start bleibt davon unberührt.
- **Videowechsel-Vorprüfung:** Wer ein neues Video starten will, während ein anderes mit >= 5 min offen ist, löst den Abbruch des
  alten sofort aus; der alte Player wird gestoppt, das neue Video startet nicht (Pausenbildschirm).
- **„Später fortsetzen“ (F6):** Beim Schließen über das Mini-Player-X erscheint eine Rückfrage, **wenn** das Schließen eine Pause
  auslösen würde: „Später fortsetzen“ (Position bleibt wie immer im Verlauf, **keine Pause**, 2x pro Zen-Tag, Protokoll `later`),
  „Trotzdem schließen“ (Pause) oder „Weiterschauen“. Ohne Pausenfolge (kurz, Musik, fast zu Ende) kein Dialog und kein
  Verbrauch. Sind die 2 aufgebraucht, entfällt der Knopf. Nicht angeboten bei Benachrichtigung/Wegwischen/Kill.
- **Umgehungsschutz:** Die Pause ist ein persistierter Countdown (`ZenCountdown`), der **nur mit Gerätelaufzeit** (elapsedRealtime)
  sinkt: Uhr vor- oder zurückstellen ändert nichts; nach Neustart zählt nur die Laufzeit des neuen Starts (Ausschaltzeit zählt
  nicht, fail-closed). Zustand in der eigenen Zen-Datei (`zen_state`, nicht Teil von Export/Import/Reset/Auto-Backup).
  Keine Abkürzung in den Einstellungen: Parameter lockern erst nach 24 h und abbrechbar, siehe `spec-einstellungs-sperre.md`;
  **eine laufende Pause wird durch keine Einstellungsänderung verkürzt.**
- **Protokoll (ZenLog):** `pause_start` (url, Grund, Kind, Wiedergabezeit, Länge), `pause_end`, `abort_short`, `abort_exempt`,
  `watch_end`, `later`.

## Musik/Podcast-Heuristik (Laufzeit, StreamInfo)
Der Extractor liefert `StreamInfo.getCategory()` (YouTube-Kategorie aus dem Player-Response, z. B. „Music“, „Entertainment“;
auf der Detailseite als „CATEGORY“ sichtbar, am Gerät bestätigt) und `getTags()` (Uploader-Keywords). In dieser Reihenfolge:
1. Kategorie „Music“ -> **Musik**.
2. Kanalname endet auf „- Topic“ oder „VEVO“ -> Musik.
3. Titel enthält „official (music) video“, „official audio“, „lyrics“, „lyric video“, „music video“, „full album“ -> Musik.
4. Nur wenn **keine** Kategorie vorliegt: Tag „music“ -> Musik. (Bei bekannter anderer Kategorie ist ein „music“-Tag kein Beleg.)
5. Titel/Kanal „podcast“, „episode“, „folge“, „ep. N“, „#N“ oder Audio-Player und Länge >= 20 min -> **Podcast**.
6. sonst **Video**. Unsicher = streng (Video/Podcast lösen die Pause aus). Für die Pause ist Podcast = Video; die Unterscheidung
   dient nur dem Protokoll.
Die Klassifikation passiert während der Wiedergabe, sobald die StreamInfo vorliegt, und wird mit der Sitzung gespeichert. Im Feed
(ohne Kategorie, nur Kanal-/Titelebene) gilt die getrennte Feed-Heuristik (`ZenFeedPlanner.isMusic`).
Am Gerät geprüft: Rick-Astley-Video -> Kategorie Music -> `abort_exempt`; Blender-Film (Film & Animation) und LastWeekTonight
(Entertainment) -> Video.

## Offen
- Prozentsatz/Min/Max sind Konstanten (`ZenPauseRules`), bis die Zen-Einstellungen (Phase 2) sie hinter der Sperre konfigurierbar machen.
- Während der Pause entsteht weiter eine Feed-Ausgabe, wenn man den Feed öffnet (W14 aus dem Review ist noch nicht umgesetzt).
