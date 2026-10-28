# Spec: Praxis-Unterthemen und einzelne Praxis-Videos

Anforderung (Stefan, 2026-10-19): Praxis nach Themen gliedern (Fitness, Yoga, Schlaf, Meditation, Breathwork/Pranayama).
Jedes Video kann eigene Zuordnungen haben, auch mehrere (ein Yoga-Kanal liefert auch Breathwork oder Meditation).
**Status: umgesetzt (2026-10-20)**, Unit-Tests grün; Gerätestatus in `.planning/STATE.md`. Maßgeblich sind die Werte in `rules.md` (Abschnitt „Praxis“) und D61, D66–D70 in `decisions.md`. Commits: Messung `2f87b35c9`, Erkennung `81edae341`, Chips `2dd7b2b39`, Standardthema/Korrektur `84e082605`, Einzelvideos `e6c6365b5`, weicher Deckel `585d767be`.

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

### 3.1 Messung vor dem Bau (2026-10-20, ausgeliefertes `model-e5s-1`, kein Training)
Details, Skripte und Grenzen: `resources/analyse-2026-10/praxis-themen-messung.md` (privat). Mengen: **H** = `zen-eval/holdout`+`final`
(400, nicht im Training; dort nur Kategorie-Konsens, Themen-Wahrheit = Lehrer-Labels für 197 davon), **O** = 1 102 Lehrer-Labels nach dem
Training (Restmenge, schwer), **P** = 30 Praxis-Kanäle × 15 neueste Videos = 450, blind gelabelt (Opus 5.5, streng). Precision/Recall in %:

| Thema | H Stufe D @0,6 / @0,8 | P Stufe T @0,6 / @0,8 | P Stufe D @0,6 / @0,8 | Stütze H+O / P |
|---|---|---|---|---|
| yoga | 86/79 · 100/75 | 76/83 · 81/70 | 75/82 · 81/67 | 38 / 216 |
| meditation | 100/82 · 100/36 | 86/82 · 92/68 | 88/92 · 99/80 (@0,7: 97/85) | 16 / 87 |
| breath | 100/100 · 100/80 | 85/76 · 87/69 | 87/69 · 89/59 | 7 / 29 |
| sleep | 33/33 · 33/33 | 95/78 · 100/48 | 94/70 · 100/39 | 6 / 23 |
| fitness | 92/73 · 100/20 | 58/47 · 68/31 | 57/47 · 65/32 (@0,9: 77/25) | 47 / 68 |

- **Cardio/HIIT:** das Modell erkennt es unzuverlässig (echtes „HIIT Workout“ 0,26, Tabata/Zumba/Aerobic-Sonden 0,30–0,47) -> **Titelregel
  nötig**. fitness verwechselt außerdem ruhige Yoga-Dehnflows mit Training.
- **Schwellen (umgesetzt):** yoga T/D 0,8; meditation T 0,8, D 0,7; breath T/D 0,8; sleep T/D 0,8 (**Daten reichen nicht**: H nur 3
  Videos, daher die strengere Schwelle); fitness **nur Stufe D ab 0,9**, bei T nie (Titelregeln tragen fitness). Die Start-Schwelle 0,6
  ergab auf den Praxis-Kanälen nur 57–88 % Precision.
- Titelregeln allein auf P: fitness 79/16, yoga 85/76, sleep 91/87, meditation 86/85, breath 96/79; Regeln + Modell (Stufe D):
  fitness 71/29, yoga 80/85, sleep 91/91, meditation 87/100, breath 93/93; 117 von 450 Videos „Weitere“. Die meisten Fehltreffer sind
  Vorträge/Ankündigungen eines Yoga-Kanals, die das strenge Label „ohne Thema“ bekamen.

## 4. Anzeige und Bedienung
[Umsetzung: rules.md „Praxis-Tab“, D67; Korrektur als minimale Mehrfachauswahl in der Prüfansicht, D70]
- Praxis-Tab: Chips „Alle · Fitness · Yoga · Schlaf · Meditation · Atemarbeit · Weitere“, Mehrfachvideos erscheinen unter jedem ihrer Themen. Reihenfolge unverändert (Datum), kein Nachladen-Zwang.
- Korrektur am Video: Der geplante Korrektur-Dialog bekommt eine Zeile mit den Praxis-Themen (Mehrfachauswahl). Jede Korrektur wird lokal gespeichert und im Debug-Export ausgegeben (Regel-Kalibrierung).
- Beim Markieren eines Kanals/einer Playlist: im selben Dialog optional ein Standardthema wählen.

## 5. Regeln (unverändert)
Praxis bleibt eine Einheit: kein Tageslimit, keine Pflichtpause, Start in laufender Pause erlaubt, Kette wie bisher. Ausnahmen aus `spec-praxis.md` gelten auch für einzelne Praxis-Videos.

## 6. Folgen für andere Dokumente (bei Freigabe)
- [erledigt 2026-10-20] `spec-praxis.md` „nur pro Kanal, nie pro Video, keine automatische Erkennung“ mit „[überholt: …]“ markiert.
- [erledigt 2026-10-20] `rules.md` Abschnitt Praxis ergänzt; `decisions.md` D61 beschlossen/umgesetzt, D66–D70 neu.
- Abo-Sichtung: Zuordnung der 30 Praxis-Kanäle zu Standardthemen im Artefakt vorbereiten (optional).

## 7. Tests
Regelwörterbuch-Tests mit Titelbeispielen DE/EN, Quellen-Reihenfolge (U vor R vor Standard), Bitmasken, Chip-Filter, Sperrlogik für Einzelvideos (Lockerung/Verschärfung), Migration ohne Themen-Daten.

## 8. Offene Punkte
- [entschieden 2026-10-20, D67] Anzahl pro Thema 5: die ersten 4 in Tab-Reihenfolge + höchstens ein beliebtes (Verhältnis > 1, sonst leer).
- [Standard 90 min umgesetzt, D69] Schwelle des weichen Praxis-Deckels; **Wortlaut der Rückfrage am Gerät mit Stefan abstimmen**.
- [erste Fassung 2026-10-20, D66] Wörterbuch-Inhalt und Schwellen; nachkalibrieren mit Stefans Korrekturen aus dem Debug-Export.
- Einzelvideos im Praxis-Tab nur mit Titel (kein Vorschaubild, kein Abruf); Standardthema im Kanal-Blatt als Kästchen (dieselbe Komponente wie im Bestätigungsdialog, zen.6; Sperrlogik unverändert), in der Ersteinrichtung sofort, sonst nachträglich.
- Wo „Schlaf“ an den Sleep-Timer anknüpft (derzeit nicht verknüpft).
- Reihenfolge der Chips (fest oder nach Nutzung).
