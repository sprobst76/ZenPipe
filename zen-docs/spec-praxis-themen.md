# Spec: Praxis-Unterthemen und einzelne Praxis-Videos

Anforderung (Stefan, 2026-10-19): Praxis nach Themen gliedern (Fitness, Yoga, Schlaf, Meditation, Breathwork/Pranayama).
Jedes Video kann eigene Zuordnungen haben, auch mehrere (ein Yoga-Kanal liefert auch Breathwork oder Meditation).
**Status: Entwurf, nicht gebaut.** Maßgeblich bleibt `rules.md`/`spec-praxis.md`, bis dies freigegeben ist.

## 1. Entscheidungen (Stefan, 2026-10-19)
- Unterthemen hängen **am Video**, bis zu 3 pro Video.
- Ein Kanal/eine Playlist darf ein **Standardthema** haben (Fallback für Videos ohne bessere Angabe).
- Die fünf Themen sind **fest** (Fitness, Yoga, Schlaf, Meditation, Atemarbeit); keine frei erweiterbare Liste.
- Unterthemen sind **reine Gliederung und Anzeige**: Sperren, Limits und Pausen ändern sich nicht (kein Etikett braucht eine 24-h-Sperre).
- Einzelne Videos als Praxis (ohne Deckel, früher zugesagt) kommen im selben Paket.

## 2. Datenmodell
- Themenschlüssel: `fitness`, `yoga`, `sleep`, `meditation`, `breath` (Anzeige DE: Fitness, Yoga, Schlaf, Meditation, Atemarbeit).
- Tabelle `praxis_theme` in der eigenen SQLite (`ZenClassCache`-Umfeld, noBackupFilesDir): `video_key`, `themes` (Bitmaske), `src` (`U` Nutzer, `R` Regeln), `ts`.
- Kanal-/Playlist-Standardthema: zusätzliches Feld im Praxis-Eintrag (`zs_praxis`), Bitmaske. Ändern ist neutral/strenger -> sofort.
- Einzelnes Praxis-Video: eigener Eintrag mit Videoschlüssel in der Praxis-Liste (analog Kanal, Flag `v`); Hinzufügen = Lockerung (24-h-Sperre, 3-s-Wartezeit, 5-min-Rückgängig wie bei Kanälen).

## 3. Reihenfolge der Quellen je Video
1. Nutzer-Korrektur (`src=U`) -> gilt allein.
2. Schlüsselwort-Regeln aus Titel, Beschreibung, Tags (`src=R`), bis 3 Themen.
3. Kanal-/Playlist-Standardthema.
4. „Ohne Thema“ (Abschnitt „Weitere“).
Die Regeln sind ein kleines lokales Wörterbuch (DE/EN), kein ML-Modell. Ob Thema-Kopf des Klassifikators die Feinunterteilung stützt, wird vor dem Bau an den Eval-Daten geprüft (Vorbedingung, nicht Annahme).
Beispiele: Breathwork = Pranayama, Atemübung, Atemtechnik, breathwork, box breathing; Meditation = Meditation, Body Scan, Achtsamkeit; Schlaf = Einschlafen, Schlafmeditation, sleep; Yoga = Yoga, Vinyasa, Yin, Asana; Fitness = Workout, Training, HIIT, Mobility.
Mehrdeutiges (Yoga vs. Fitness) darf mehrfach zugeordnet werden; Schwellen werden an Beispielen kalibriert.

## 4. Anzeige und Bedienung
- Praxis-Tab: Chips „Alle · Fitness · Yoga · Schlaf · Meditation · Atemarbeit · Weitere“, Mehrfachvideos erscheinen unter jedem ihrer Themen. Reihenfolge unverändert (Datum), kein Nachladen-Zwang.
- Korrektur am Video: Der geplante Korrektur-Dialog bekommt eine Zeile mit den Praxis-Themen (Mehrfachauswahl). Jede Korrektur wird lokal gespeichert und im Debug-Export ausgegeben (Regel-Kalibrierung).
- Beim Markieren eines Kanals/einer Playlist: im selben Dialog optional ein Standardthema wählen.

## 5. Regeln (unverändert)
Praxis bleibt eine Einheit: kein Tageslimit, keine Pflichtpause, Start in laufender Pause erlaubt, Kette wie bisher. Ausnahmen aus `spec-praxis.md` gelten auch für einzelne Praxis-Videos.

## 6. Folgen für andere Dokumente (bei Freigabe)
- `spec-praxis.md` „nur pro Kanal, nie pro Video, keine automatische Erkennung“ -> mit „[überholt: …]“ markieren (Einzelvideos, Themen-Erkennung nur als Anzeige).
- `rules.md` Abschnitt Praxis ergänzen; `decisions.md` D61 von „Entwurf“ auf beschlossen.
- Abo-Sichtung: Zuordnung der 30 Praxis-Kanäle zu Standardthemen im Artefakt vorbereiten (optional).

## 7. Tests
Regelwörterbuch-Tests mit Titelbeispielen DE/EN, Quellen-Reihenfolge (U vor R vor Standard), Bitmasken, Chip-Filter, Sperrlogik für Einzelvideos (Lockerung/Verschärfung), Migration ohne Themen-Daten.

## 8. Offene Punkte
- Wörterbuch-Inhalt und Schwellen (am Gerät/Eval kalibrieren).
- Wo „Schlaf“ an den Sleep-Timer anknüpft (derzeit nicht verknüpft).
- Reihenfolge der Chips (fest oder nach Nutzung).
