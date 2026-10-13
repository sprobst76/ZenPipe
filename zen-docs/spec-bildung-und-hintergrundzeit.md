# Spec: Zeitzählung im Hintergrund und Kategorie „Bildung“

Stand: 2026-10-13. **Status: Spec (nicht gebaut).** Maßgeblich bleibt `rules.md`; diese Spec schlägt Regeln vor. Abschnitt 6
listet, was sich in `rules.md` ändern bzw. ergänzen müsste.

Ausgangslage (Nutzerentscheidungen): Der Pop-up-Player entfällt. Das Bild läuft nie nebenbei: Das Verlassen der App pausiert ein
Video. Ton über „Hintergrund“ läuft weiter, auch bei eingeschaltetem Bildschirm (Gehen, Einschlafen, Podcasts). Den Sleep-Timer
gibt es bereits. Offene Fragen: Wie zählt Zeit im Hintergrund? Braucht es eine Kategorie „Bildung“, die anders zählt?

## 1. Empfehlung (kurz)

1. **Gemessen wird eine einzige Größe: die Wiedergabezeit.** Das ist die echte Abspielzeit im Zustand „spielt“, gemessen auf
   `elapsedRealtime`, wie heute in `ZenPauseEngine`. Neu ist nur, dass die Zeit nach **Wiedergabeart** getrennt gebucht wird:
   **Bild** (Video-Player) und **Ton** (Hintergrund-Player). Bildschirmzustand, Geschwindigkeit und Inhalt (Podcast oder Video)
   ändern an der Messung nichts.
2. **Pflichtpause:** Die Regeln bleiben, Bild und Ton zählen gleich (Podcasts lösen die Pause weiter aus). Neu ist die
   **Ruhezeit-Anrechnung**: Die Zeit seit dem letzten Abspielen der Sitzung wird von der Pause abgezogen. Damit löst sich der
   Fall „Podcast pausiert, Stunden später die Benachrichtigung weggewischt“, ohne eine Hintertür zu öffnen.
3. **Tageslimit (Phase 3):** Es zählt in **Zen-Minuten**: Bild 1:1, Ton 1:2, Musik und Praxis 0. Ist das Limit erreicht, sind nur
   noch Bild-Starts gesperrt, Ton bleibt erlaubt (Escape-Ventil, Konzept §3). Gegen Dauerberieselung gibt es einen eigenen
   **Ton-Deckel**.
4. **Feed-Kontingent:** bleibt eine **Angebotsgrenze**. Gezählt wird, was in Ausgaben **angeboten** wurde, weder Starts noch
   konsumierte Zeit. Hintergrundzeit spielt dafür keine Rolle.
5. **Praxis-Minuten:** Bild und Ton zählen 1:1. Es ist eine Bilanz ohne Limit, daher ohne Gewichtung.
6. **Bildung: ja, aber als schmale Kategorie** mit einem **begrenzten Zusatzbudget** (60 Zen-Minuten pro Tag). Sie ist keine
   Ausnahme vom Feed und hat keinen eigenen Tab. Eine Sitzung ab 15 min Wiedergabezeit darf ohne Pause enden („Lernblock“).
   Bildung gilt nur für Videos ab 15 min Länge. Die Zuordnung läuft wie bei Praxis: Kanal- oder Playlist-Liste, Hinzufügen
   startet die 24-h-Sperre.
7. **Datenmodell:** Die Praxis-Liste bekommt eine **Kategorie-Spalte**. Feste Profile (Praxis, Bildung) stehen als Konstanten im
   Code. Vom Nutzer selbst definierte Profile gibt es **nicht**, sonst ließe sich ein „alles frei“-Profil bauen.

## 2. Zeitzählung

### 2.1 Messgröße

- **Wiedergabezeit** ist die Zeit im Zustand „spielt“ (`Player.changeState` mit `STATE_PLAYING`), gemessen auf `elapsedRealtime`
  (`ZenPauseEngine.accumulate`). Puffern, Pausieren und Spulen zählen nicht. Die Wanduhr spielt keine Rolle.
- **Wiedergabeart** ergibt sich aus dem Player-Typ zum Messzeitpunkt: `MAIN` = **Bild**, `AUDIO` = **Ton**. Ein Wechsel innerhalb
  einer Sitzung (Video-Player → Hintergrund und zurück) teilt die Zeit am Wechselzeitpunkt auf. Die Sitzung bleibt dieselbe
  (wie heute: kein Abbruch).
- **Zen-Tag:** Die Zeit wird **laufend** dem Zen-Tag des Messzeitpunkts gutgeschrieben (`ZenClock.trustedMillis`, Wechsel um
  04:00), und zwar bei jedem Persistier-Tick (10 s) sowie bei Zustandswechseln. Eine Sitzung über 04:00 wird damit korrekt
  aufgeteilt. Praxis bucht heute auf den Tag des Sitzungsendes; das wird angeglichen.
- **Speicher:** `zen_state`, neuer Schlüssel `wt_<tag>` mit den Feldern Bild, Ton, Musik, Praxis und Bildung in ms, 30 Tage
  aufbewahrt. Er ist nicht Teil von Export, Import, Reset oder Auto-Backup.

### 2.2 Situationen

| Situation | Wiedergabezeit | Art | Abbruch? | Begründung |
|---|---|---|---|---|
| Video im Vordergrund | ja | Bild | – | Normalfall |
| App verlassen / Bildschirm aus im Video-Player | nein (Video pausiert) | – | nein (Pausieren) | Nutzerentscheidung „Bild nie nebenbei“. **Voraussetzung:** Bildschirm aus pausiert das Video ebenfalls (am Gerät prüfen) |
| Ton im Hintergrund, Bildschirm an | ja | Ton | – | Bildschirmzustand wird **nicht** unterschieden: Er sagt nichts über Aufmerksamkeit aus und ist trivial umschaltbar |
| Ton im Hintergrund, Bildschirm aus (Schlafen) | ja | Ton | – | wie oben; das Einschlafen fängt der Sleep-Timer ab |
| Ablauf des Sleep-Timers | zählt bis zur Pause (inkl. 30 s Fade) | wie gespielt | **nein** (`sleep_end`, unverändert) | Einschlafen ist kein Abbruch |
| Pausieren / Fortsetzen | Pausenzeit zählt nicht | – | nein | unverändert. Neu: Ruhezeit-Anrechnung (2.3) |
| Anruf, Audio-Fokus-Verlust, Kopfhörer abgezogen (`AUDIO_BECOMING_NOISY`) | Player pausiert, zählt nicht | – | nein | unverändert (Player pausiert, Sitzung bleibt offen) |
| Wiedergabegeschwindigkeit 0,5–2× | **Echtzeit**, nicht Inhaltszeit | – | – | Die Grenze betrifft Lebenszeit am Gerät. Wer schneller schaut, hat schneller „durch“ (zu Ende über Position, wie heute) |
| Lautstärke 0 / stumm | zählt | – | – | nicht zuverlässig erkennbar; streng |
| Prozess-Kill | bis zum letzten Tick (max. 10 s Verlust) | wie gebucht | **ja** (unverändert, `KILLED`) | streng; Ruhezeit-Anrechnung gilt auch hier (letzter Tick → Erkennung) |
| Neustart des Geräts | Ausschaltzeit zählt nicht | – | Kill-Regel | `elapsedRealtime` beginnt neu, gebucht ist gebucht |

### 2.3 Pflichtpause (Auslöser >= 5 min)

- **Schwelle und Abbruch-Definition bleiben**: Bild und Ton zählen gleich und voll zur Schwelle (5 min) und zur Pausenlänge (20 %).
  Ton nur halb zu zählen hieße, dass ein abgebrochener Podcast nach 8 min nie eine Pause auslöst. Das widerspricht der
  Entscheidung „Podcasts inklusive“ (W22).
- **Neu: Ruhezeit-Anrechnung.** Bei einem Abbruch ist die Pause `max(0, Länge − Ruhezeit)`. Die Ruhezeit ist die
  `elapsedRealtime`-Spanne seit dem letzten „spielt → nicht spielt“ der Sitzung. Bei Abbruch während des Spielens ist sie 0.
  - Warum das keine Hintertür ist: In der Ruhezeit lief in ZenPipe nachweislich nichts. Ein anderes Video zu starten wäre ein
    Videowechsel und damit ein sofortiger Abbruch (`abandonForNewStart`). Wer eine Pause vorab abwartet, hat sie abgesessen.
  - Damit gelöst: Podcast beim Ankommen pausiert und abends weggewischt (U6/W20), Video wegen der Haustür pausiert und erst nach
    20 min geschlossen. Der Vorrat „Später fortsetzen“ (2 pro Tag) bleibt für den Fall „jetzt sofort schließen“.
  - Über einen Neustart hinweg gibt es keine Anrechnung (Ruhezeit = 0, fail-closed), da `elapsedRealtime` nicht vergleichbar ist.
  - `previewClose` (Rückfrage beim Mini-Player-X) rechnet mit der verkürzten Pause; ist sie 0, kommt kein Dialog.
- **Hintergrund-Audio und Abbruch:** Benachrichtigung wegwischen oder schließen ist ein Abbruch (unverändert). Mit der
  Ruhezeit-Anrechnung kostet das nur, wenn direkt aus dem Spielen heraus abgebrochen wird.

### 2.4 Tageslimit (Phase 3, F2)

- **Zen-Minuten = Bild + Ton/2.** Musik und Praxis zählen 0 (Musik nach `ZenContentKind`, Praxis nach Zuordnung). Bildung
  zählt zuerst gegen das Bildungsbudget (Abschnitt 3), der Überlauf gegen das Tageslimit.
- Die Gewichtung richtet sich nach der **Wiedergabeart**, nicht nach der Inhaltsart: Ein Podcast im Video-Player zählt voll. So
  reicht eine Podcast-Heuristik nicht, um die Gewichtung zu umgehen.
- **Bei Erreichen des Limits:** Neue **Bild**-Starts sind gesperrt (eigener Bildschirm, wie der Pausenbildschirm). Eine laufende
  Bild-Sitzung wird 5 min vorher angekündigt und dann wie beim Sleep-Timer sanft **pausiert**. Das ist **kein Abbruch**: keine
  Pflichtpause, kein Verbrauch von „Später fortsetzen“. Angeboten wird „Als Ton weiterhören“. **Ton** bleibt erlaubt und zählt
  weiter.
- **Ton-Deckel:** Reine Tonzeit pro Zen-Tag, vor und nach dem Limit gezählt, Musik und Praxis ausgenommen. Danach sind auch
  Ton-Starts gesperrt, bis 04:00. Das schützt vor der „Podcast als Dauerberieselung“.
- **Laufendes:** Ein gesenktes Limit wirkt sofort, ein erhöhtes erst nach der Sperre (Regel der Einstellungs-Sperre).

### 2.5 Feed-Kontingent (15 pro Ausgabe, Deckel 30)

Bleibt **unverändert**: Gezählt werden **angebotene** verschiedene Videos (`ZenFeedState.counted`), nicht gestartete und nicht
konsumierte. Begründung (W10): Das Kontingent begrenzt die **Auswahl**, das Tageslimit die **Zeit**. Würde man „gestartet“
zählen, würde Nicht-Anklicken belohnt und das Ausgabe-Prinzip (Zeitung) unterlaufen. Hintergrundzeit hat keinen Einfluss.

### 2.6 Praxis-Minuten

Wiedergabezeit der Praxis-Sitzungen, Bild und Ton 1:1, laufend auf den Zen-Tag gebucht (2.1). Es gibt kein Limit (Entscheidung),
daher keine Gewichtung. Die Minuten einer laufenden Sitzung sind damit sofort sichtbar (bisher eine offene Lücke).

### 2.7 Missbrauch und Manipulation

| Szenario | Gegenmittel |
|---|---|
| Video im Hintergrund laufen lassen, um Bild-Minuten zu sparen | Im Hintergrund gibt es kein Bild (`AUDIO`). Den Pop-up-Player gibt es nicht mehr. Ton zählt halb, das ist gewollt, aber durch den Ton-Deckel begrenzt |
| Podcast als Dauerberieselung | Ton-Deckel. Abbruch-Pausen gelten auch für Ton |
| Bildschirm aus, damit nichts zählt | Der Bildschirmzustand wird nicht ausgewertet; gezählt wird die Abspielzeit |
| Uhr zurückstellen, Zeitzone ändern | Tag aus `ZenClock.trustedMillis` und `ZenZone.fixed`; Messung auf `elapsedRealtime` |
| Kill vor dem Tick | max. 10 s Verlust; die Kill-Erkennung löst wie bisher die Pause aus |
| Daten-Import/Reset | `zen_state` ist ausgenommen (bestehende Regel) |
| Praxis/Bildung als Hintertür markieren | Hinzufügen = Lockerung mit 24-h-Sperre. Bildung ist budgetiert und gilt erst ab 15 min Länge |
| 2× Geschwindigkeit, um „mehr“ zu schauen | Es zählt Echtzeit. Mehr Inhalt pro Minute ist kein Zen-Problem; das Limit schützt die Zeit |

**Fail-closed:** Ist die Art unbekannt, gilt Video, also Bild bzw. Ton nach Player-Typ, nie Musik. Ist die Zuordnung unbekannt,
gilt die Sitzung nicht als Praxis oder Bildung. Bei einer Uhr-Anomalie entscheidet `ZenClock` (strengere Auslegung). Gebuchte
Zeit wird nie zurückgenommen.

## 3. Kategorie „Bildung“

### 3.1 Braucht es sie?

Was „Bildung“ heute schon hat: Der Feed bevorzugt lange Videos (Längenbonus ab 15/30 min), und Feed-Gruppen wie „Lernen“ an
erster Stelle geben Priorität. Die Später-Liste und „Später fortsetzen“ decken einzelne Unterbrechungen ab. Was fehlt:
1. **Aufteilen in Blöcke.** Eine 90-min-Vorlesung in drei Sitzungen kostet heute bis zu zwei Pausen oder beide „Später“ des Tages.
2. **Ein Tageslimit, das Lernen nicht bestraft.** Mit 90 Zen-Minuten frisst eine Vorlesung den Tag.

Ohne Minutenlimit wäre Bildung fast überflüssig. Mit Limit (Phase 3) ist sie sinnvoll, aber nur als **begrenzter Zusatz**. Eine
Ausnahme wäre die gefährlichste Hintertür von allen: „Lernvideo“ ist die bequemste Ausrede, und Edutainment-Kanäle sind
genau die, die am stärksten binden.

### 3.2 Regeln (Vorschlag)

- **Zuordnung:** wie Praxis, nur durch den Nutzer, pro **Kanal oder Online-Playlist**. Keine Titelheuristik, keine
  YouTube-Kategorie „Education“ (die setzt der Uploader selbst). Hinzufügen = Lockerung (Bestätigung 3 s, ausstehend,
  24-h-Sperre, abbrechbar, 5-min-Rückgängig). Entfernen = strenger, wirkt sofort.
- **Mindestlänge:** Bildung gilt nur für Videos **>= 15 min** (bekannte Länge). Kürzere Videos eines Bildungskanals sind
  normale Videos. Das entspricht der Nutzerbeobachtung und verhindert, dass kurze Edutainment-Clips Bildungsrechte bekommen.
- **Feed:** **keine Ausnahme.** Bildungsvideos bleiben in der Ausgabe, konkurrieren um die 15 Plätze und zählen zum Deckel 30.
  Es gibt keinen eigenen Tab und keine eigene Liste, sonst entsteht ein zweiter Feed ohne Ende.
- **Pflichtpause:** Endet eine Bildungssitzung mit **>= 15 min Wiedergabezeit**, gibt es **keine Pause** und keinen
  „Später“-Verbrauch (Protokoll `abort_exempt kind=BILDUNG`). Die Position bleibt wie immer im Verlauf. Zwischen 5 und 15 min
  gilt die normale Pause, denn das ist Zappen und kein Lernblock. Während einer laufenden Pflichtpause darf Bildung **nicht**
  starten (anders als Praxis).
- **Bildungsbudget:** **60 Zen-Minuten pro Zen-Tag** (Bild 1:1, Ton 1:2) als **Zusatz** vor dem Tageslimit. Ist es aufgebraucht,
  zählt Bildung normal gegen das Tageslimit. Höchstens 60 min mehr pro Tag, also ein Deckel und kein Freibrief.
- **Podcasts/Ton/Hintergrund:** wie überall (Ton halb, Ton-Deckel gilt, Sleep-Timer-Ende ist kein Abbruch).
- **Mehrere Sitzungen:** Jede Sitzung zählt ihre eigene Zeit, es gibt keine Summierung über Sitzungen (wie bei der Pause). Das
  Fortsetzen an der gespeicherten Position ist eine neue Sitzung. Die Regel „>= 15 min = kein Abbruch“ macht Blöcke von 15 min und
  mehr pausenfrei.
- **Kette/Autoplay:** keine. Am Ende hält der Player an (die Praxis-Kette bleibt die einzige Ausnahme).
- **Vorrang:** Praxis > Bildung > Musik > Podcast/Video. Ein Kanal ist entweder Praxis oder Bildung. Ein Wechsel Bildung → Praxis
  ist eine Lockerung (24 h), Praxis → Bildung ist strenger und wirkt sofort.

### 3.3 Verallgemeinerung „Kategorie mit eigenen Regeln“

**Empfehlung: das Datenmodell verallgemeinern, die Regeln nicht.**

- **Datenmodell:** `ZenPraxisChannel` bekommt das Feld `category` (`PRAXIS` | `BILDUNG`), kodiert als 5. Spalte in `zs_praxis`.
  Der Wert fehlt bei alten Einträgen, dann gilt `PRAXIS`, das Format bleibt also rückwärtskompatibel. `ZenSettingsLock.addPraxis`
  und `removePraxis` sowie Undo und Pending bleiben, sie bekommen nur die Kategorie mit. Einen Umbau der Klassennamen (Upstream-
  und Diff-Kosten) braucht es nicht, ein Alias `ZenCategoryEntry` ist optional. `ZenPraxisPlaylistItems` funktioniert
  unverändert, der Schlüssel ist die Playlist und die Kategorie kommt aus der Liste.
- **Regeln:** Ein festes Profil je Kategorie als Konstante, zum Beispiel
  `ZenCategoryProfile(feedExempt, pauseExemptAfterMs, startsDuringPause, chains, dailyBudgetMin, countsToLimit, minLengthSec)`:
  - Praxis = `(true, 0, true, true, null, false, 0)`
  - Bildung = `(false, 15 min, false, false, 60, overflow, 15 min)`
  - Musik bleibt eine Heuristik (`ZenContentKind`), ohne Liste.
  Vom Nutzer konfigurierbare Profile gibt es **nicht**: Jeder Parameter wäre ein Lockerungsschalter. Neue Kategorien entstehen
  nur per Code-Änderung mit Spec.
- **`ZenKind`:** Neuer Wert `BILDUNG`. `pauseExempt` wird zu einer Funktion von Art und Wiedergabezeit
  (`exemptAt(playedMs)`). `classify(…, category, durationSec)` liefert `BILDUNG` nur bei einer Länge von mindestens 15 min.
- **Aufwand:** Datenmodell und Klassifikation S (rund ½ Tag inkl. Tests). UI (Markieren-Dialog mit Kategorie-Auswahl,
  Einstellungen-Liste mit Kennzeichnung) S–M (½–1 Tag). Das Budget hängt am Tageslimit (Phase 3).

## 4. Regelwerk: Kategorie × Regel (Standardwerte)

| Regel | Video (Bild) | Video/Podcast als Ton | Musik | Praxis | Bildung (>= 15 min) |
|---|---|---|---|---|---|
| Feed-Ausgabe 15 / Deckel 30 | zählt (angeboten) | zählt (angeboten) | nicht im Feed | nicht im Feed | **zählt** (keine Ausnahme) |
| Pflichtpause nach Abbruch >= 5 min | ja | ja (voll gezählt) | nein | nein | nur bei 5–15 min; ab 15 min nein |
| Ruhezeit-Anrechnung | ja | ja | – | – | ja |
| Start während Pflichtpause | nein | nein | nein | **ja** | nein |
| Zen-Minuten (Tageslimit 90) | 1:1 | 1:2 | 0 | 0 | zuerst Bildungsbudget 60, Überlauf ins Limit |
| Ton-Deckel 180 min | – | zählt | nein | nein | zählt |
| Nach Erreichen des Tageslimits | gesperrt | erlaubt | erlaubt | erlaubt | gesperrt (nach Budget), als Ton erlaubt |
| Kette (Weiterspielen) | nein | nein | nein | ja | nein |
| Sleep-Timer-Ende | kein Abbruch | kein Abbruch | kein Abbruch | kein Abbruch | kein Abbruch |
| Zuordnung | – | Player-Typ `AUDIO` | Heuristik | Liste, 24-h-Sperre | Liste, 24-h-Sperre, Länge >= 15 min |

Neue Zen-Einstellungen (hinter der Sperre, `ZenSettingDefs`):

| Schlüssel | Default | Spanne | strenger ist |
|---|---|---|---|
| `daily_limit_min` (Zen-Minuten) | 90 | 30–180 (15) | kleiner |
| `audio_cap_min` (Ton-Deckel) | 180 | 60–240 (30) | kleiner |
| `edu_budget_min` | 60 | 0–90 (15) | kleiner |
| `audio_weight_percent` | 50 | fest (keine Einstellung) | – |
| Bildung-Mindestlänge / pausenfrei ab | 15 min / 15 min | fest | – |

## 5. Randfälle

- **Videowechsel im Hintergrund** (nächstes Element der Warteschlange per Tipp in der Benachrichtigung): Das ist ein Abbruch des
  alten Videos wie heute. Die Ruhezeit-Anrechnung greift, wenn es vorher pausiert war.
- **Sitzung über 04:00:** wird laufend aufgeteilt (2.1). Das Limit des neuen Tags beginnt bei 0. Eine wegen des Limits pausierte
  Bild-Sitzung darf nach 04:00 fortgesetzt werden.
- **Ton nach dem Limit zum Einschlafen:** Praxis zählt nicht gegen den Ton-Deckel. Einschlafhilfen gehören deshalb in Praxis,
  nicht in Bildung.
- **Bildungsvideo mit unbekannter Länge oder Livestream:** gilt nicht als Bildung (streng).
- **Bildung in einer Praxis-Warteschlange:** wird nicht verkettet (Kette nur Praxis → Praxis).
- **Wechsel Bild → Ton genau beim Limit:** Ton-Starts und der Wechsel bleiben erlaubt. Die Sitzung läuft weiter, ab dann zählt Ton.
- **Kill während einer Ruhezeit:** Beim Recover zählt die Ruhezeit nur, wenn sie im selben Boot liegt (Boot-Zähler wie bei
  `ZenCountdown`), sonst 0.
- **Rückfrage beim Mini-Player-X:** zeigt die verkürzte Pause. Bei Bildung ab 15 min oder einer Rest-Pause von 0 kommt kein Dialog.
- **Ersteinrichtung:** Bildung markieren wirkt sofort (wie Praxis).

## 6. Abgleich mit `rules.md` (Widersprüche und neue Regeln; `rules.md` hier **nicht** geändert)

Widersprüche bzw. Stellen, die nachgezogen werden müssen:
1. **Pop-up** steht noch in Pflichtpause/Auslöser („alle Player (Video, Popup, …)“), Absichtsabfrage/Auslöser
   („Video-/Hintergrund-/Popup-Wiedergabe“), Sleep-Timer („Video- und Popup-Player“), reduzierte Videodetails („Hinzufügen/
   Hintergrund/Pop-up“) und Funktionen/Downloads („Bleibt: … Hintergrund-Audio und Popup“). Nach der Nutzerentscheidung
   entfällt es. Die Pop-up-Einstiege (Long-Press, Detailseite, Router, Einstellung „Minimieren → Pop-up“) gehören in
   `ZenFeatures` (wie Downloads).
2. **„Bild läuft nie nebenbei“** steht nirgends in `rules.md`. Neue Grundregel: App verlassen oder Bildschirm aus pausiert den
   Video-Player (Minimieren-Modus fest „keiner“, Bildschirm-aus-Verhalten am Gerät prüfen). Ton nur über „Hintergrund“.
3. **Tageslimit (F2)** sagt „Audio/Podcast halb“. Die Spec gewichtet nach **Wiedergabeart** (Ton halb), nicht nach Inhaltsart
   (Podcast im Video-Player = voll). Die Zeile muss präzisiert werden.
4. **Pflichtpause/Pausenlänge**: „20 % der Wiedergabezeit, …“. Neu kommt die Ruhezeit-Anrechnung hinzu
   (`max(0, Länge − Ruhezeit)`, nur im selben Boot). Dazu gehört die Zeile „Später fortsetzen … nur wenn das Schließen eine
   Pause auslösen würde“: Die Vorschau rechnet mit der verkürzten Pause.
5. **Pflichtpause/Auslöser, Ausnahmen**: „Musik; Praxis; Später; Sleep-Timer“ bekommt den Zusatz „**Bildung ab 15 min
   Wiedergabezeit**“.
6. **Praxis-Minuten**: „Tag des Sitzungsendes“ wird zu „laufend auf den Zen-Tag gebucht“ (gemeinsamer Zähler `wt_<tag>`). Die
   Lücke „Minuten einer laufenden Sitzung erst nach Ende“ entfällt.
7. **Feed/Tages-Deckel, Ausnahmen** („Später-Liste, Playlists, gezielte Suche, Praxis-Kanäle“): klarstellen, dass Bildung
   **keine** Ausnahme ist und der Deckel *angebotene* Videos zählt.
8. **Praxis/Zuordnung** (Liste `zs_praxis`): bekommt die Kategorie-Spalte. „Ein Kanal kann zugleich abonniert/in Gruppen sein“
   gilt für Bildung ebenso, aber ohne Feed-Ausschluss.
9. **Musik vs. Podcast** (`ZenContentKind`): Die Art wird einmal pro Sitzung bestimmt (`audioOnly` beim ersten `StreamInfo`).
   Für die Zählung ist sie nur noch für Musik relevant, Ton/Bild kommt aus dem Player-Typ. Kein Widerspruch, aber in der Zeile
   vermerken.

Neue Regeln (Zeilen ergänzen):
- Grundregeln: **Wiedergabezeit/Wiedergabeart** (Bild/Ton), Messung auf `elapsedRealtime`, laufende Buchung auf den Zen-Tag, Echtzeit
  statt Inhaltszeit, Bildschirmzustand egal.
- Abschnitt **Tageslimit**: Zen-Minuten, Ton 1:2, Musik/Praxis 0, Verhalten am Limit (Bild gesperrt, laufendes sanft pausiert,
  kein Abbruch), Ton-Deckel.
- Abschnitt **Bildung**: Zuordnung, Mindestlänge 15 min, Pause-Regel, Budget 60, keine Feed-Ausnahme, kein Start während
  der Pause, keine Kette.
- Zen-Einstellungen: `daily_limit_min`, `audio_cap_min`, `edu_budget_min` (Richtung „kleiner = strenger“). Markieren als Bildung
  = Lockerung, Bildung → Praxis = Lockerung, Praxis → Bildung = strenger.
- Protokoll: `limit_reached`, `limit_pause`, `audio_cap_reached`, `abort_exempt kind=BILDUNG`, `pause_rest_credit`
  (Ruhezeit, verkürzte Länge).

## 7. Tests und Verifikation

Unit-Tests (Engine mit Fake-`monotonic`, Stil `ZenPauseTest`/`ZenPraxisTest`):
- Bild/Ton-Aufteilung bei Wechsel mitten in der Sitzung. Puffern und Pause zählen nicht. Geschwindigkeit ist irrelevant
  (Echtzeit).
- Laufende Buchung: Tick über 04:00 wird aufgeteilt. Kill-Recover bucht bis zum letzten Tick und löst weiter die Pause aus.
- Ruhezeit-Anrechnung: Abbruch beim Spielen = volle Pause. Nach 3 min Ruhe = Länge − 3 min. Ruhe >= Länge = keine Pause,
  `pause_rest_credit`. Über einen Neustart = keine Anrechnung. `previewClose` liefert null, wenn die Rest-Pause 0 ist.
- Bildung: Länge 14:59 = Video. Abbruch nach 14 min = Pause, nach 15 min = `abort_exempt`. Während der Pause blockiert. Keine
  Kette. Kategorie-Spalte: altes Format decodiert als Praxis. Richtungen beim Wechsel Praxis ↔ Bildung.
- Limit: Bild 60 + Ton 60 = 90 Zen-Minuten. Bildung 80 Bild = 60 Budget + 20 Limit. Am Limit: Bild-Start blockiert, Ton erlaubt,
  laufendes Bild endet ohne Pflichtpause. Ton-Deckel blockiert Ton, aber nicht Praxis oder Musik.
- Manipulation: Wanduhr ±1 Tag ändert den gebuchten Tag nicht (Trusted Time). Zone fest.

Am Gerät (Defy, Debug-Build mit verkürzten Konstanten, danach zurücksetzen):
- Hintergrund-Player, Bildschirm aus (`adb shell input keyevent 26`): `wt_*` wächst als Ton. Video-Player + Bildschirm aus:
  pausiert, `wt_*` steht.
- Pausieren 2 min, Benachrichtigung wegwischen, dann `pause_rest_credit` im ZenLog prüfen. Verkürzte Pause auf dem
  Pausenbildschirm.
- Sleep-Timer (1 min debug) im Hintergrund: `sleep_end`, Minuten gebucht, keine Pause.
- Bildung markieren (Ersteinrichtung), langes Video 16 min (verkürzt: Schwelle 1 min) schließen: keine Pause.
- Nicht selbst prüfbar, als TODO für Stefan: Kopfhörer abziehen und echter Anruf (Player pausiert, keine Pause), Gefühl
  beim Limit-Fade, ob 90/180/60 im Alltag passen.

## 8. Umsetzung in Phasen

| Phase | Inhalt | Aufwand | Risiko |
|---|---|---|---|
| 0 (Voraussetzung) | Pop-up abschalten über `ZenFeatures` (alle Einstiege). Minimieren fest auf „keiner“, Bildschirm aus pausiert das Video (prüfen, ggf. Hook) | S–M (½–1 Tag) | mittel: viele Einstiege, Upstream-Diff |
| 1 | Zeitmessung `ZenWatchTime` (`wt_<tag>`, Bild/Ton/Musik/Praxis/Bildung, laufende Buchung, Player-Typ-Hook), Praxis-Minuten darauf umstellen, Anzeige „Heute: N min (Ton M)“ in Einstellungen > Zen | S–M (1 Tag) | gering: nur Zählen, keine Sperre |
| 2 | Ruhezeit-Anrechnung in `ZenPauseEngine.finish`/`previewClose` (+ Boot-Prüfung) | S (½ Tag) | gering–mittel: ändert eine bestehende Regel |
| 3 | Kategorie-Spalte, `ZenKind.BILDUNG`, feste Profile, Markieren-UI mit Kategorie, Pausenregel ab 15 min | M (1–1½ Tage) | mittel: Hintertür-Risiko, UI an mehreren Stellen |
| 4 | Tageslimit + Ton-Deckel + Bildungsbudget (Einstellungen hinter der Sperre, Start-Gate über die `blockStart`-Infrastruktur, sanfte Pause am Limit, Limit-Bildschirm) | M–L (2–3 Tage) | mittel–hoch: Gate an allen Startstellen, Reaktanz-Risiko |

Phasen 1 und 2 lohnen sich auch ohne Bildung und ohne Limit. Phase 3 hat ohne Phase 4 nur den Pausen-Vorteil. Wer Bildung
vor dem Limit will, kann Phase 3 allein bauen, das Budget ist dann ein Hook wie bei `ZenPraxisTime.msOn`.

## 9. Entscheidungen für den Nutzer

1. **Wie zählt Ton im Hintergrund für das Tageslimit?** (a) halb, plus Ton-Deckel 180 min. (b) voll. (c) gar nicht.
   **Empfehlung: (a).** Ton ist das Escape-Ventil (Konzept §3), der Deckel verhindert Dauerberieselung. Für die Pflichtpause zählt
   Ton in jedem Fall voll.
2. **Ruhezeit-Anrechnung für die Pflichtpause?** (a) ja, die Zeit seit dem letzten Abspielen wird abgezogen (nur im selben
   Boot). (b) nein, „Später fortsetzen“ 2× pro Tag reicht. **Empfehlung: (a).** Sie löst den Podcast- und Haustür-Fall ohne
   Hintertür, da nachweislich nichts lief.
3. **Kategorie Bildung?** (a) ja, schmal: Liste mit 24-h-Sperre, nur Videos ab 15 min, ab 15 min Wiedergabezeit pausenfrei,
   Zusatzbudget 60 Zen-Minuten, keine Feed-Ausnahme, kein Tab. (b) nein: Feed-Gruppe „Lernen“ plus „Später fortsetzen“ reichen.
   (c) breit, wie Praxis (ohne Limit, eigener Tab). **Empfehlung: (a)**, und zwar erst zusammen mit dem Tageslimit. Vorher
   reicht (b), weil ohne Limit nur der Pausenvorteil bliebe.
4. **Standardwerte des Tageslimits:** (a) 90 Zen-Minuten, Ton-Deckel 180, Bildung 60. (b) 60 / 120 / 45 (strenger).
   **Empfehlung: (a)** als Start. Strenger stellen geht jederzeit sofort, Lockern nur über die 24-h-Sperre.
