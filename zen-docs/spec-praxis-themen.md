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

- **Weicher Praxis-Tagesdeckel** (Stefan, 2026-10-20, Empfehlung angenommen): kein harter Stopp, aber nach einer Schwelle Praxis-Zeit pro Tag (Vorschlag 90 min, Wert offen) eine ruhige Rückfrage "Noch Praxis oder schon Schauen?". Das weicht von "kein Deckel" für Einzelvideos ab und ist die Antwort auf die Hintertür-Frage. Schwelle zu erhöhen/abzuschalten = Lockerung (24 h). Keine Streaks, keine Beschämung.
- **Einzelne Praxis-Videos: 24-h-Sperre wie bei Kanälen** (kein Sofort-Markieren, auch nicht bei Klassifikator-Übereinstimmung).
- **Erkennung streng**: nur klare Treffer bekommen ein Thema, Zweifel landen unter "Weitere".
- **Reihenfolge**: erst zen.5-Härtung, dann dieses Paket.

- **Praxis-Tab je Thema mit fester Anzahl** (Stefan, 2026-10-20): je Thema eine feste Zahl Videos (Vorschlag 5, Wert offen), davon höchstens ein "beliebtes" Video. Beliebt = Aufrufe geteilt durch den Median der letzten Videos desselben Kanals (Ausreißer nach oben, kleine Kanäle nicht benachteiligt); fehlen Aufrufzahlen, entfällt der Platz. Mehr als ein beliebtes pro Thema bewusst nicht (Zen statt Algorithmus). Eingefrorene Playlists (D63) speisen dieselbe Auswahl. Praxis-Tab bisher: 15 je Kanal, kein Nachladen; die Quote gilt zusätzlich als Themenansicht.

## 2. Datenmodell
- Themenschlüssel: `fitness`, `yoga`, `sleep`, `meditation`, `breath` (Anzeige DE: Fitness, Yoga, Schlaf, Meditation, Atemarbeit).
- Tabelle `praxis_theme` in der eigenen SQLite (`ZenClassCache`-Umfeld, noBackupFilesDir): `video_key`, `themes` (Bitmaske), `src` (`U` Nutzer, `R` Regeln), `ts`.
- Kanal-/Playlist-Standardthema: zusätzliches Feld im Praxis-Eintrag (`zs_praxis`), Bitmaske. Ändern ist neutral/strenger -> sofort.
- Einzelnes Praxis-Video: eigener Eintrag mit Videoschlüssel in der Praxis-Liste (analog Kanal, Flag `v`); Hinzufügen = Lockerung (24-h-Sperre, 3-s-Wartezeit, 5-min-Rückgängig wie bei Kanälen).

## 3. Erkennung: Titelregeln zuerst, dann der vorhandene Klassifikator (Stefan, 2026-10-20)
Das Modell hat die Praxis-Themen bereits (`ZenRules.PRACTICE_THEMES`, Thema-Kopf ist multi-label): `yoga`, `atmen_breathwork`, `meditation_hypnose`, `mobilitaet_kraft_training`, `schlaf_klang`. Kein neues Training nötig; Yoga lag in der Bundle-Auswertung bei Precision/Recall 90/88 (Support 64, Schwelle 0,5; `student-multilabel.md`). Für die anderen vier liegen hier keine Zahlen vor: vor dem Bau auf den Eval-Daten (`~/.ytplaylist/zen-eval`) und den 30 Praxis-Kanälen messen.
Abbildung: fitness = mobilitaet_kraft_training (deckt Cardio/HIIT evtl. nicht ab: messen, sonst Titelregel), yoga = yoga, sleep = schlaf_klang, meditation = meditation_hypnose, breath = atmen_breathwork.

Reihenfolge je Video (Stufe T = Titel+Kanal, D = mit Beschreibung):
1. Nutzer-Korrektur (`src=U`) gilt allein.
2. **Titelregeln** (kleines DE/EN-Wörterbuch, streng, nur eindeutige Begriffe wie Pranayama, Breathwork, Vinyasa, Body Scan) -> sicher, auch ohne Modell und ohne Beschreibung.
3. **Modell-Themenwerte** (Wahrscheinlichkeit je Thema >= Schwelle, Start 0,6 wie `MIN_P`, bis 3 Themen); die Werte werden beim Klassifizieren ohnehin berechnet und müssen nur mit abgelegt werden (ggf. Spalte in `ZenClassCache`). Stufe T ist unsicherer als D: bei T nur bei hoher Sicherheit.
4. Kanal-/Playlist-Standardthema.
5. Sonst "Weitere".
Ohne installiertes Modell arbeiten nur 1, 2, 4. Titelregel und Modell widersprechen sich selten; die Vereinigung beider Treffer (Titelregel stärker) gilt, begrenzt auf 3.

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
- Anzahl pro Thema (Vorschlag 5) und genaue Auswahl der übrigen Plätze (Datum, Klassifikator-Sicherheit).
- Schwelle des weichen Praxis-Deckels (Vorschlag 90 min) und Wortlaut der Rückfrage (am Gerät abstimmen).
- Wörterbuch-Inhalt und Schwellen (am Gerät/Eval kalibrieren).
- Wo „Schlaf“ an den Sleep-Timer anknüpft (derzeit nicht verknüpft).
- Reihenfolge der Chips (fest oder nach Nutzung).
