# Spec (Entwurf): Absichtsabfrage, die zum Nicht-Schauen einlädt

> **Stand 2026-10-28:** AP 0 (Ergebniszähler + Log) und AP 1 (Kanal-Blatt „Nur ansehen“ zählt als Bestätigung) sind umgesetzt (D72, rules.md „Absichtsabfrage“); AP 2–8 offen.

**Worum geht es:** Neugestaltung der Absichtsabfrage („Was willst du schauen?“), damit sie klar ist und ruhig, ehrlich und ohne
Schuldgefühl dazu anregt, ZenPipe gerade **nicht** zu nutzen: eigene Tageszahlen, eine Praxis-Alternative, wechselnde Fragen,
gewichtete Knöpfe (Schritt 1); eigene Gegenmittel und Kontextton (Schritt 2).
**Wann nachlesen:** Vor jeder Änderung an `ZenIntentPrompt`, an den Absichts-Strings oder an den Zeilen „Absichtsabfrage“ in
[rules.md](rules.md).

**Status:** Entwurf vom 2026-10-14, nichts davon gebaut. Stefan hat die Elemente (1) Zahlen, (2) Alternative, (4) wechselnde
Fragen und (6) Knopf-Gewichtung als ersten Schritt gebilligt, (3) Gegenmittel und (5) Kontext ausdrücklich als zweiten Schritt
([decisions.md](decisions.md) D59). Verbindlich bleibt [rules.md](rules.md); dieser Entwurf ändert dort nichts, Abschnitt 8 nennt die
Zeilen, die sich bei der Umsetzung ändern würden. Markierung wie in [research-achtsamer-konsum.md](research-achtsamer-konsum.md):
**[Belegt]**, **[Schwach belegt]**, **[Annahme]** (eigene Einschätzung/Startwert, von Stefan zu bestätigen).

---

## 1. Ziel, Nicht-Ziele, Leitprinzipien

### Ziel
- Die Abfrage ist **auf den ersten Blick verständlich**: Sie stellt eine Frage zum Nachdenken, erwartet aber keine Eingabe.
- Sie macht den Weg **weg** von ZenPipe zum leichtesten, freundlichsten Weg („Doch nicht“, Praxis, eigene Gegenmittel), ohne
  den Weg **hinein** („Weiter“) zu verstecken.
- Sie gibt dem Moment eine **ehrliche Grundlage** (was ich heute schon geschaut habe) statt eines Appells.
- Sie bleibt durch **Abwechslung** wach (gegen Gewöhnung, Abschnitt 6).

### Nicht-Ziele
- Kein Freitextfeld, keine gespeicherte Absicht (bleibt bei D14: „Freitext nicht gewünscht“).
- Keine längere Wartezeit, keine neue Sperre, kein Tageslimit (F2 bleibt offen und getrennt).
- Keine Streaks, Punkte, Abzeichen, Belohnungen, Fortschrittsbalken, Vergleiche mit gestern/anderen, Druck-Zähler
  („noch 3 Öffnungen übrig“), keine Rotfärbung, keine Schuldformulierungen („schon wieder“, „wirklich?“).
- Kein Dark Pattern: kein versteckter, winziger oder irreführend beschrifteter „Weiter“-Knopf, kein Confirmshaming
  („Nein, ich verschwende lieber Zeit“), keine Tricks, die der Nutzer nicht durchschaut.
- Keine Daten nach außen, keine Auswertung außerhalb des Geräts.

### Leitprinzipien
| Prinzip | Bedeutet konkret |
|---|---|
| Ehrlich | Zahlen sind echte Messwerte aus `ZenWatchTime`/Zählern, gerundet nach unten, nie geschönt oder dramatisiert. Knöpfe tun genau, was draufsteht. |
| Ruhig | Salbei-Design ([spec-design.md](spec-design.md)), kein Rot, keine Ausrufezeichen, keine Animationen außer dem bestehenden Countdown. Du-Form, Einladung statt Aufforderung (Tonregel aus [sprueche.md](sprueche.md)). |
| Durchschaubar | Was der Dialog zeigt und warum, steht in dieser Spec und später in rules.md; jede Zahl ist in Einstellungen > Zen nachvollziehbar. Gewichtung der Knöpfe ist sichtbar (Form/Reihenfolge), nicht subtil. |
| Abbrechbar | Jede Abfrage hat jederzeit einen Ausweg ohne Wartezeit („Doch nicht“, Alternative). „Weiter“ ist nach der Wartezeit immer erreichbar. |
| Kein Schuldgefühl | Zahlen ohne Wertung, keine Wörter wie „schon“, „bereits“, „wieder“, „zu viel“. Jede Wahl ist in Ordnung; die Texte sagen das auch. |
| Nichts heimlich | Neue Zähler sind wenige, benannt (Abschnitt 7), in `zen_state` (nicht Export/Backup), sichtbar in Einstellungen > Zen. |
| Alles lokal | Keine neue Gegenstelle. Die Alternative lädt Praxis-Inhalte über denselben YouTube-Abruf wie der Praxis-Tab. |

---

## 2. Heutiger Ablauf (Ist-Stand, Code gelesen 2026-10-14)

Code: `app/src/main/java/org/schabi/newpipe/util/ZenIntentPrompt.java` (Java, im Paket `util`, aber ZenPipe-eigen), Strings
`zen_intent_*` in `res/values{,-de}/strings.xml`, Regeln [rules.md](rules.md) „Absichtsabfrage“.

| Aspekt | Heute | Stelle |
|---|---|---|
| Auslöser | App-Start, bis einmal bestätigt (`showIfNeeded`, Ende `MainActivity.onCreate`); externer/geteilter Link auf Video/Kanal/Playlist (`onExternalLink`); Rückkehr nach ≥ `intent_background_min` (5 min) im Hintergrund (`onActivityStarted`); Wiedergabe über geteilte Links ohne `MainActivity` (`gate`, `RouterActivity`) | `MainActivity` Z. 213/234/242/822–834, `RouterActivity` Z. 691 |
| Unterdrückung | nur während eigener Wiedergabe (`ZenPlayback.isPlaying`) | `isSkippable` |
| Cooldown | 5 min nach einer **bestätigten** Abfrage (`MIN_INTERVAL_MS`, fest), gilt nicht für den App-Start | `isSkippable` |
| Dialog | `AlertDialog`, Titel „Was willst du schauen?“, Text „Nimm dir kurz Zeit: Was hast du vor, und wann bist du fertig?“, nicht abbrechbar (`setCancelable(false)`) | `show`, `zen_intent_title/_message` |
| Knöpfe | Positiv „Weiter (5)“ → „Weiter“ nach `intent_wait_s` (5 s, 3–30) gesperrt; Negativ „Doch nicht“ sofort aktiv, schließt die App (`finishAffinity`) bzw. bricht die Router-Wiedergabe ab | `show`, `CountDownTimer` |
| Folgen | „Weiter“ setzt `confirmed` + `lastAskedAt`, startet wartende Router-Wiedergabe und baut die Feed-Ausgabe (`setOnConfirmedListener`, `ZenFeedProvider` prüft `isConfirmed`); „Doch nicht“ kostet nichts (keine Ausgabe, D20) | `FeedFragment`, `ZenFeedProvider` |
| Gespeichert | nichts außer Prozess-Variablen; kein Log-Eintrag | – |

### Was daran unklar ist
1. **Antwort ohne Feld.** Der Text verlangt eine Antwort („Was hast du vor, und wann bist du fertig?“), es gibt aber kein
   Eingabefeld und keinen Hinweis, dass die Antwort im Kopf genügt. Wirkt wie ein fehlendes UI-Element. D14 schließt ein Feld aus,
   also muss der **Text** klarstellen, dass nichts einzugeben ist.
2. **Immer derselbe Satz.** Nach wenigen Tagen wird er nicht mehr gelesen **[Annahme, Abschnitt 6]**.
3. **Keine Richtung.** Beide Knöpfe sind Standard-Textknöpfe gleicher Gewichtung; die gewünschte Richtung ist nicht erkennbar,
   und es gibt außer „App schließen“ kein Angebot.
4. **Kanal-Blatt „Nur ansehen“ → Abfrage direkt danach.** Ablauf: Kanal-Link → `RouterActivity` → Blatt „Kanal übernehmen“
   (fragt selbst nicht) → „Nur ansehen“ (`ZenChannelImport.onView`) → `MainActivity` mit Link-Typ `CHANNEL` →
   `onExternalLink` → Abfrage, sobald die letzte Bestätigung ≥ 5 min her ist. Bei kaltem Prozess fragt zusätzlich
   `showIfNeeded` in `onCreate`. Beim Teilen aus der YouTube-App ist das praktisch immer der Fall. rules.md („Kanal-Link geteilt“)
   nennt das seit 2026-10-14 „gewollt“; aus Nutzersicht ist es aber eine **zweite Frage unmittelbar nach einer bewussten
   Entscheidung** im Blatt (Stefans Rückmeldung).

### Vorschlag zu 4: Blatt-Entscheidung zählt als bewusste Entscheidung
- „Nur ansehen“ im Blatt ruft vor dem Öffnen `ZenIntentPrompt.acknowledge("channel_sheet")` auf: setzt `confirmed = true` und
  `lastAskedAt = now` (also auch den 5-min-Cooldown), Log `intent_prompt result=sheet`. Der Intent nach `MainActivity` bekommt
  ein Extra `zen_intent_decided`, damit auch `showIfNeeded` bei kaltem Prozess nicht fragt (der Prozess-Zustand allein reicht
  nicht, weil `onCreate` vor `handleIntent` läuft – **[Annahme]**, bei der Umsetzung am Code prüfen).
- „Abonnieren“/„Als Praxis markieren“ öffnen nichts und bleiben ohne Wirkung auf die Abfrage.
- **Abwägung:** Der Kanal-Weg verliert damit die 5-s-Wartezeit. Vertretbar, weil das Blatt selbst schon eine Pause mit Inhalt
  ist (Einordnung lädt sichtbar mehrere Sekunden, die Wahl „Nur ansehen“ ist aktiv). Alternative wäre, die Wartezeit ins Blatt zu
  verlegen („Nur ansehen (5)“); das macht das Blatt umständlicher (D54 wollte genau das vermeiden). Empfehlung: Variante ohne
  Wartezeit (Frage 1 in Abschnitt 11).
- Ob eine Blatt-Entscheidung auch die **Feed-Ausgabe** freigibt (wie „Weiter“): Ja, sonst fragt der nächste Feed-Besuch im
  selben Prozess nicht, baut aber auch keine Ausgabe – das wäre ein unsichtbarer Zustand. Eine Entscheidung genügt.

---

## 3. Neuer Dialog, Schritt 1

### 3.1 Layout-Skizze

Eigener Inhalt im bestehenden `AlertDialog` (`setView`, Layout `zen_intent_prompt.xml`), Knöpfe im Layout statt
`setPositiveButton`, damit Reihenfolge und Form frei sind. Hochformat, Defy (≈ 360 dp breit):

```
┌──────────────────────────────────────────┐
│  Kurz innehalten                         │  Titel, fest, 20 sp regular, Primärtext
│                                          │
│  Was brauchst du gerade wirklich?        │  wechselnde Frage, 18 sp, Primärtext
│                                          │
│  Antwort im Kopf genügt, nichts wird     │  fester Hinweis, 14 sp, Sekundärtext
│  gespeichert.                            │
│                                          │
│  Heute: 42 Min. Bild · 15 Min. Ton       │  Zahlenzeile (3.3), 14 sp, Sekundärtext,
│  3. Einstieg in dieser Stunde            │  Tabellenziffern; fehlt, wenn leer
│                                          │
│  ┌────────────────────────────────────┐  │
│  │            Doch nicht              │  │  gefüllt, Akzent, 48 dp hoch
│  └────────────────────────────────────┘  │
│  ┌────────────────────────────────────┐  │
│  │   Lieber Praxis: Yoga mit Adriene  │  │  umrandet (Akzent), nur mit Praxis-Quelle
│  └────────────────────────────────────┘  │
│                                          │
│                  Weiter (5)              │  Textknopf, Akzent, 48 dp Tippfläche,
└──────────────────────────────────────────┘  nach Wartezeit „Weiter“
```

Querformat/große Schrift: Inhalt in `ScrollView`, Knöpfe bleiben untereinander und vollständig erreichbar.

### 3.2 Textbausteine

**Fest** (klärt das Missverständnis „Antwort ohne Feld“):

| Schlüssel | Deutsch | English |
|---|---|---|
| Titel | Kurz innehalten | A moment first |
| Hinweis | Antwort im Kopf genügt, nichts wird gespeichert. | Just answer in your head. Nothing is saved. |
| „Doch nicht“ | Doch nicht | Never mind |
| „Weiter“ / mit Countdown | Weiter / Weiter (%d) | Continue / Continue (%d) |
| Alternative (3.4) | Lieber Praxis: %s | Practice instead: %s |
| Alternative, ohne Namen | Lieber zur Praxis | Practice instead |
| Laden der Alternative | Praxis wird geladen … | Loading practice … |

**Wechselnde Fragen/Sätze** (eine pro Abfrage). Regeln: höchstens ~60 Zeichen, Du-Form, keine Ausrufezeichen, keine Wörter
„schon/bereits/wieder/immer noch/wirklich?/zu viel“, keine Unterstellung, jede Antwort ist erlaubt. Gruppen nur zur Pflege, die
Auswahl mischt alle.

| Nr | Gruppe | Deutsch | English |
|---|---|---|---|
| F01 | Bedürfnis | Was brauchst du gerade wirklich? | What do you really need right now? |
| F02 | Bedürfnis | Was wäre jetzt gut für dich? | What would be good for you right now? |
| F03 | Bedürfnis | Bist du gerade müde, gelangweilt oder neugierig? | Are you tired, bored or curious right now? |
| F04 | Bedürfnis | Würde dir Bewegung, Ruhe oder ein Gespräch guttun? | Would movement, rest or a chat do you good? |
| F05 | Absicht | Weißt du, was du schauen willst? | Do you know what you want to watch? |
| F06 | Absicht | Was hast du vor, und wann ist Schluss? | What's the plan, and when will you stop? |
| F07 | Absicht | Suchst du etwas Bestimmtes oder einfach Ablenkung? Beides ist in Ordnung. | Something specific, or just a distraction? Both are fine. |
| F08 | Absicht | Ein Video, und dann? | One video, and then what? |
| F09 | Zeit | Wofür nutzt du die Zeit sonst? | What else could this time be for? |
| F10 | Zeit | Gibt es etwas, das gerade auf dich wartet? | Is something waiting for you right now? |
| F11 | Zeit | Ist das ein guter Moment dafür? | Is this a good moment for it? |
| F12 | Gefühl | Wie willst du dich nach dem Schauen fühlen? | How do you want to feel when you're done? |
| F13 | Gefühl | Wie geht es dir in diesem Moment? | How are you right now? |
| F14 | Ruhe | Ein Atemzug, bevor es weitergeht. | One breath before you go on. |
| F15 | Ruhe | Du darfst auch einfach nichts tun. | You're allowed to do nothing at all. |
| F16 | Ruhe | Ein Blick aus dem Fenster geht auch. | A look out of the window works too. |
| F17 | Menschen | Gibt es jemanden, von dem du gern hören würdest? | Is there someone you'd like to hear from? |
| F18 | Menschen | Wem könntest du gerade eine Nachricht schreiben? | Who could you send a message to right now? |

18 Sätze je Sprache. **[Annahme]** Das reicht für etwa drei Wochen ohne Wiederholung bei 5–6 Abfragen am Tag; Erweiterung später
über dieselbe Quelle (Frage 4).

### 3.3 Zahlenzeile

Zeigt eigene Zahlen des aktuellen Zen-Tags (`ZenDay.dayOf`, Wechsel 04:00, vertrauenswürdige Zeit wie bei `ZenWatchTime`),
sachlich, ohne Wertung. Höchstens zwei kurze Zeilen.

| Teil | Quelle | Format DE / EN | Erscheint, wenn |
|---|---|---|---|
| Bild | `ZenWatchTime.get(heute).pictureMs` | „42 Min. Bild“ / „42 min picture“; ab 60 min „1 Std. 5 Min.“ / „1 h 5 min“ | ≥ 1 min (abgerundet) |
| Ton | `…soundMs` | „15 Min. Ton“ / „15 min sound“ | ≥ 1 min |
| Videos | **neu** `ia_vid_<Tag>`: verschiedene Video-URLs (Bild oder Ton, ohne Musik/Praxis) mit ≥ 60 s Wiedergabezeit an diesem Tag | „6 Videos“ / „6 videos“ | ≥ 1 |
| Einstieg | **neu** `ia_in_<Tag>` (Anzahl „Weiter“ heute) und `ia_recent` (Zeitpunkte der „Weiter“ der letzten 60 min, `elapsedRealtime` + Boot-Zähler) | „3. Einstieg in dieser Stunde“, sonst „4. Einstieg heute“ / „3rd time this hour“, „4th time today“ | Stunde: ≥ 2 vorherige in 60 min; sonst Tag: ≥ 1 vorheriger heute |

Zeile 1 = „Heute: “ + vorhandene Teile aus Bild · Ton · Videos (mit „ · “ getrennt); Zeile 2 = Einstieg. Regeln:
- **Nicht bei null:** Fehlen alle Teile einer Zeile, entfällt die Zeile; fehlen beide, entfällt der Block ganz (erste Abfrage des
  Tages zeigt keine Zahlen). Nie „0 Min.“.
- **Musik und Praxis** erscheinen nicht (Musik ist kein Schauen im Sinne der Abfrage; Praxis-Minuten wären eine Belohnung,
  Frage 3).
- **Keine Bewertung:** gleiche Farbe und Größe bei 5 und bei 500 Minuten, kein Vergleich, kein Ziel, keine Schwelle, ab der sich
  etwas verfärbt. Ordinalzahl statt „schon N-mal“.
- **Neustart:** `ia_recent` gilt nur im selben Boot (wie die Ruhezeit-Anrechnung); nach Neustart fehlt die Stundenzeile, die
  Tageszahl bleibt. Ungenauigkeit nur zugunsten weniger Anzeige.
- Abgerundet: 59 s zählen nicht als 1 Min. (ehrlich, nie aufgebläht).
- TalkBack liest ausgeschriebene Einheiten („42 Minuten Bild“), eigener `contentDescription`.

### 3.4 Alternativ-Schaltfläche (Praxis)

- **Sichtbar** nur, wenn mindestens eine **aktive** Praxis-Quelle existiert (`ZenSettingsLock.praxisChannels()`, ausstehende
  zählen nicht).
- **Auswahl der Quelle:** die zuletzt genutzte aktive Quelle (**neu** `ia_px_last` = Schlüssel `serviceId:url` der Quelle, gesetzt
  beim Start einer Praxis-Sitzung, wo heute `ZenKind.PRAXIS` bestimmt wird). Fehlt sie oder ist sie nicht mehr aktiv: die erste
  Quelle der Liste in Nutzerreihenfolge. „Kürzeste“ scheidet in Schritt 1 aus: Längen sind ohne Netzabruf nicht bekannt
  **[Annahme: kein Längen-Cache für Praxis vorhanden]**. Bei mehreren Quellen keine Auswahlliste im Dialog (ein Tipp, keine
  weitere Entscheidung).
- **Beschriftung:** „Lieber Praxis: <Name>“ (Name auf 28 Zeichen gekürzt mit „…“). Eine Dauer („10 Min. atmen“) nur, wenn sie
  bekannt ist; Schritt 1 nennt keine, damit nichts versprochen wird, was nicht stimmt.
- **Öffnen direkt in die Wiedergabe, ohne weiteren Dialog:**
  - Playlist: erste Seite laden (`ExtractorHelper.getPlaylistInfo`, wie Praxis-Tab), als Warteschlange im Video-Player ab dem
    ersten Element starten; die Praxis-Kette läuft wie bei „Alle abspielen“.
  - Kanal: neueste Videos laden (`ExtractorHelper.getChannelInfo`, Videos-Tab, ohne Shorts/Live), das **neueste** starten
    (einzeln, keine Warteschlange).
  - Während des Ladens: Knopftext „Praxis wird geladen …“, alle Knöpfe bleiben bedienbar („Doch nicht“ bricht ab).
    Fehler oder > 8 s **[Annahme]**: Praxis-Tab öffnen statt Wiedergabe, kurzer Hinweis (Toast).
  - Läuft eine Pflichtpause, startet die Praxis trotzdem (rules.md „Praxis während Pause“, D39); keine Sonderregel nötig.
- **Wirkung auf die Abfrage:** setzt den Cooldown (`lastAskedAt`), aber **nicht** die Feed-Freigabe (`confirmed` bleibt false):
  wie „Doch nicht“ kostet die Alternative keine Ausgabe und keinen Deckel (D20). Öffnet der Nutzer danach im selben Prozess den
  Feed, zeigt er die laufende Ausgabe; eine neue entsteht erst nach dem nächsten „Weiter“ (Frage 2).
- **Router-Weg (`gate`):** Die Alternative verwirft die geteilte Wiedergabe (wie `onDeclined`) und startet stattdessen die Praxis.
- **Keine Praxis-Quelle:** kein Knopf, kein Platzhalter, kein Werbehinweis im Dialog. Der Dialog hat dann zwei Knöpfe. Wer Praxis
  nicht kennt, findet sie über den Praxis-Tab (Leertext erklärt sie bereits). Schritt 2 kann den Platz mit einem eigenen
  Gegenmittel füllen.

### 3.5 Knöpfe: Reihenfolge, Hervorhebung, Wartezeit

| Knopf | Form | Aktiv | Wirkung |
|---|---|---|---|
| Doch nicht | gefüllt, Akzent (`#3E6A5E` hell / `#9DBFB2` dunkel, Text in Kontrastfarbe ≥ 4.5:1), oben | sofort | wie heute: App schließen bzw. Router-Wiedergabe verwerfen |
| Lieber Praxis | umrandet, Akzent, Mitte | sofort | 3.4 |
| Weiter | Textknopf, Akzent, unten mittig | nach `intent_wait_s` (unverändert 5 s), Countdown im Text | wie heute |

Ehrlichkeitsregeln (kein Dark Pattern):
- „Weiter“ hat dieselbe Schriftgröße, Akzentfarbe (Kontrast ≥ 4.5:1 wie alle Textknöpfe, [spec-design.md](spec-design.md)) und
  48 dp Tippfläche wie die anderen; nur die **Form** ist schlichter. Die Beschriftung ist neutral.
- Die Gewichtung trägt Form und Reihenfolge, nicht Farbe allein (Barrierefreiheit).
- Nach Ablauf der Wartezeit verschiebt sich nichts, nichts springt, kein Knopf verschwindet.
- Zurück-Taste: wie heute nicht abbrechbar (sonst wäre „Zurück“ ein dritter, unklarer Weg). **[Annahme]** Alternative:
  Zurück = „Doch nicht“ (Frage 6).

### 3.6 Rotation der Texte

- Neue Klasse nach dem Muster von `ZenQuotes`: zufällig unter den in dieser Runde noch nicht gezeigten, Runde endet, wenn alle
  gezeigt sind, **nie derselbe Satz zweimal hintereinander** (auch über Rundengrenzen). Identität über den Text (Sprachwechsel =
  neuer Vorrat).
- **Ein Satz pro Abfrage:** eine Abfrage-Nummer (`ia_seq`, bei jedem `show` hochgezählt) bindet den Satz; eine neu erzeugte
  Activity (Drehen) mit derselben offenen Abfrage zeigt denselben Satz, jede neue Abfrage einen neuen.
- Zustand in `zen_state`: `ia_seen`, `ia_last`, `ia_seq`, `ia_text_id`.
- Quelle der Sätze: Abschnitt in einer Markdown-Tabelle, daraus `R.array.zen_intent_questions` (Frage 4).

### 3.7 Barrierefreiheit, Hell/Dunkel

- Lesereihenfolge TalkBack: Titel, Frage, Hinweis, Zahlen, Knöpfe in Bildschirmreihenfolge. Countdown nicht sekündlich ansagen;
  einmal „Weiter ist jetzt verfügbar“ (`announceForAccessibility`) nach Ablauf.
- Schrift bis 200 %: Inhalt scrollt, Knöpfe brechen um (keine abgeschnittenen Texte), Mindesthöhe 48 dp.
- Hell/Dunkel/Schwarz über Theme-Attribute (`colorAccent`, Dialog-Hintergrund, `textColorPrimary/Secondary`), keine festen Farben;
  neue Farbnamen nur in `values/zen_colors.xml`, Kontraste nachrechnen (Formel spec-design.md Anhang). Kein Rot.
- Keine Animationen; Vibration nein.

### 3.8 Sehr kurze und sehr lange Nutzung

| Situation | Verhalten |
|---|---|
| erste Abfrage des Zen-Tags / unter 1 min geschaut | keine Zahlenzeile, nur Frage |
| viele Einstiege kurz hintereinander | Zahlenzeile „N. Einstieg in dieser Stunde“, sonst nichts; keine längere Wartezeit, kein Zusatztext (Kontexttöne erst Schritt 2) |
| sehr viel Zeit heute (z. B. 4 Std.) | Zahl wird schlicht gezeigt; keine Färbung, keine Steigerung, keine Warnung. Ein Limit ist F2, nicht diese Spec |
| eigene Wiedergabe läuft | keine Abfrage (unverändert) |
| Prozess neu / nach „Doch nicht“ | fragt wieder (unverändert); Zahlen kommen aus `zen_state`, überleben also den Prozess |

---

## 4. Schritt 2: Gegenmittel und Kontextton

### 4.1 Eigene Gegenmittel-Liste
- Einstellungen > Zen > Absichtsabfrage > „Meine Alternativen“: kurze Einträge des Nutzers („Spazieren gehen“, „Wasser
  trinken“, „Jemanden anrufen“). Max. 20 Einträge × 60 Zeichen, keine Dubletten. Speicherung wie `qt_custom` (`ia_alt_custom`).
- **Im Dialog:** eine Zeile unter der Frage „Oder: Spazieren gehen“, Eintrag rotiert nach demselben Verfahren wie die Fragen
  (eigene Runde, `ia_alt_seen/_last`). Zusätzlich wird die Zeile als **zweiter umrandeter Knopf** angeboten, wenn keine
  Praxis-Quelle existiert („Lieber: Spazieren gehen“): Tipp schließt die App wie „Doch nicht“, protokolliert aber `result=own_alt`.
  Mit Praxis-Quelle bleibt es bei der Textzeile (höchstens zwei Ausweg-Knöpfe).
- Vorgaben ab Werk: leer; beim ersten Öffnen der Einstellung drei Vorschläge zum Antippen (nicht vorbelegt, nichts geschieht ohne
  Tipp).
- **Sperrlogik:** Hinzufügen sofort, ohne Sperre (bremst nur). Entfernen ebenfalls sofort **[Empfehlung, Frage 5]**: wie eigene
  Pausensprüche (D30) macht eine Zeile die Abfrage weder kürzer noch überspringbar; die Wartezeit bleibt. Streng ausgelegt wäre
  Entfernen eine Lockerung; das wäre für eine Komfortliste unverhältnismäßig.

### 4.2 Kontextton
Ein Kontext ersetzt die normale Frage durch einen Satz aus seinem eigenen kleinen Vorrat (gleiche Rotation, je Kontext eigene
Runde). Höchstens ein Kontext; Priorität: Pause > Spät > Lange Sitzung. Der Ton wird **behutsamer**, nie strenger; keine längere
Wartezeit (Frage 7).

| Kontext | Startwert | Begründung | Beispielsätze DE / EN |
|---|---|---|---|
| Kurz nach einer Pause | Pflichtpause vor ≤ 15 min zu Ende (neu `ia_pause_end` = `elapsedRealtime` + Boot bei `pause_end`) | Nach einer Pause ist der Impuls „jetzt endlich weiter“ am größten; 15 min ≈ maximale Pausenlänge, danach ist der Bezug weg **[Annahme]** | „Die Pause ist vorbei. Was möchtest du jetzt mit deiner Zeit?“ / „The break is over. What would you like to do with your time now?“; „Wie war die Pause?“ / „How was the break?“; „Magst du die Ruhe noch ein wenig behalten?“ / „Would you like to keep the calm a little longer?“ |
| Spät abends | 22:00–04:00 Ortszeit (Ende = Zen-Tag-Wechsel) | Schlaf ist der häufigste Verlierer später Sitzungen; 04:00 passt zum Zen-Tag (D3). 22:00 ist ein Startwert, später an eine Nachtruhe koppeln, sobald es sie gibt **[Annahme]** | „Es ist spät. Was hilft dir beim Runterkommen?“ / „It's late. What helps you wind down?“; „Morgen gibt es auch noch Videos.“ / „The videos will still be there tomorrow.“; „Vielleicht lieber etwas Ruhiges?“ / „Maybe something calm instead?“ |
| Nach langer Sitzung | Zen-Minuten heute (`Day.zenMinutesMs`, Bild + Ton/2) ≥ 90 min **oder** letzte Sitzung mit ≥ 30 min Wiedergabe endete vor ≤ 10 min | 90 min = Spec-Empfehlung für das (noch offene) Tageslimit ([spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md) 2.4, D57, nicht bestätigt); 30/10 min fängt „direkt im Anschluss weiter“ **[Annahme]** | „Du hast heute einiges gesehen. Was braucht dein Kopf jetzt?“ / „You've seen quite a bit today. What does your mind need now?“; „Augen und Rücken freuen sich über eine Pause.“ / „Your eyes and back would enjoy a break.“; „Wie wäre es mit etwas ohne Bildschirm?“ / „How about something without a screen?“ |

Bei „Spät“ bevorzugt die Alternative eine Praxis-Quelle, die zuletzt mit Sleep-Timer genutzt wurde, falls bekannt (neu
`ia_px_sleep`), sonst wie 3.4.

### 4.3 Einordnung in die Sperrlogik und Einstellungen (Entwurf)

Grundsatz (Stefan): Alles, was den Dialog **schwächt oder abschaltet**, ist eine Lockerung (ausstehend, 24-h-Sperre, Rückgängig,
Ersteinrichtung frei); Stärken wirkt sofort. Neue Einträge in `ZenSettingDefs` (gleiche Mechanik wie `pause_enabled`):

| Schlüssel | Einstellung | Default | strenger ist | Lockerung |
|---|---|---|---|---|
| `intent_numbers` | Tageszahlen in der Abfrage | an | an | aus |
| `intent_alternative` | Praxis als Alternative anbieten | an | an | aus |
| `intent_context` | Behutsamer Ton je nach Situation (Schritt 2) | an | an | aus |
| `intent_wait_s` | Wartezeit (besteht) | 5 s | größer | kleiner |
| – (Liste) | Meine Alternativen | leer | – | keine Sperre (4.1) |

Die Abfrage selbst bleibt ohne Aus-Schalter (rules.md „Zen-Einstellungen“). Die wechselnden Fragen haben keinen Schalter
(abschalten hieße: zurück zum unklaren festen Satz).

**Einstellungsseite (Entwurf),** Einstellungen > Zen, neuer Abschnitt „Absichtsabfrage“:
```
Absichtsabfrage
  Wartezeit                                   5 s
  Tageszahlen zeigen                          [an]
  Praxis als Alternative anbieten             [an]
  Ton je nach Situation                       [an]   (Schritt 2)
  Meine Alternativen                          3 Einträge  >   (Schritt 2)
  Rückblick (letzte 7 Tage)                   >   (Abschnitt 6)
```

---

## 5. Was sich für den Nutzer ändert (Kurzfassung)
- Klarer Dialog: feste Überschrift „Kurz innehalten“, eine wechselnde Frage, Hinweis „Antwort im Kopf genügt“.
- Ruhige Zahlen zum eigenen Tag, nur wenn es etwas zu zeigen gibt.
- „Doch nicht“ ist der hervorgehobene Knopf, Praxis ist eine echte Alternative mit einem Tipp, „Weiter“ bleibt nach 5 s da.
- Kein doppeltes Fragen nach „Nur ansehen“ im Kanal-Blatt.

---

## 6. Wirkung und Grenzen (ehrlich)

**Was die Quellen tragen** ([research-achtsamer-konsum.md](research-achtsamer-konsum.md) §2):
- **[Belegt, mittel, Interessenkonflikt]** Absichtsabfrage mit Wartezeit (One Sec, PNAS 2023, 280 Pers., 6 Wochen): 36 % der
  Öffnungsversuche abgebrochen, Öffnungsversuche nahmen über die Zeit ab. Das ist der Kern, den ZenPipe schon hat.
- **[Belegt, mittel]** Reine Pause vor dem Öffnen verhindert 13 % der Öffnungen, eine mühsame Eingabe 47 % (LocknType). Mehr Hürde
  wirkt stärker, erzeugt aber mehr Frust (GoalKeeper) – deshalb **keine** längere Wartezeit in dieser Spec.
- **[Belegt, niedrige Qualität]** Reine Bewusstmachung wirkt kaum (Review Biedermann et al.); Self-Tracking allein ist schwach
  (Feature 10 der Recherche). **Folge:** Die Zahlenzeile ist für sich allein vermutlich wirkungsschwach; sie ist als ehrlicher
  Kontext zur Frage gedacht, nicht als Hebel.
- **[Belegt, allgemein]** Implementation Intentions (d ≈ 0,65, nicht smartphone-spezifisch) und Ziel + Erinnerung (MyTime, −21 %,
  kleine Studie): stützen Fragen nach Plan und Ende (F05/F06/F08).
- **[Einschätzung der Recherche]** Ruhige, nicht moralisierende Texte und ein legitimer Ausweg vermeiden Reaktanz; ein Totalverbot
  treibt in die YouTube-App. Das ist die Begründung für „Alternative statt Verbot“.
- **[Belegt, hoch]** Langfristige Wirkung digitaler Selbstkontroll-Werkzeuge ist nicht belegt (TOCHI-Meta-Analyse, Studien ~21 Tage).

**Was Annahme ist:**
- Dass wechselnde Fragen der Gewöhnung entgegenwirken (Habituation an gleichbleibende Reize ist ein allgemeines Prinzip; für
  Absichtsabfragen fand die Recherche keine Studie). **[Annahme]**
- Dass eine Ein-Tipp-Alternative mehr Abbrüche bewirkt als „App schließen“ allein. **[Annahme]**
- Dass ein gefüllter „Doch nicht“-Knopf die Wahl verschiebt, ohne als Manipulation empfunden zu werden: Er ist sichtbar und
  erklärt (Voreinstellung/Hervorhebung als offener „Nudge“). **[Annahme]**
- Alle Schwellen in 4.2. **[Annahme]**

**Merken, ob es hilft (lokal, nicht aufdringlich):**
- Zähler je Zen-Tag `ia_day_<Tag>` = gezeigt, Weiter, Doch nicht, Praxis, eigene Alternative, Blatt; 30 Tage wie `wt_*`.
- **Rückkehr nach „Doch nicht“:** öffnet der Nutzer binnen 2 min erneut, zählt das als `decline_back` (ehrlicher Gegenwert zu
  „Doch nicht“: ein Abbruch, dem sofort ein neuer Versuch folgt, war keiner).
- ZenLog-Eintrag je Abfrage: `intent_prompt result=continue|decline|praxis|own_alt|sheet trigger=start|link|return|router
  ctx=none|pause|late|long q=F07 numbers=1 waitMs=`. (ZenLog ist auf 500 Einträge begrenzt, deshalb die Tageszähler.)
- Anzeige nur auf Abruf: Einstellungen > Zen > Absichtsabfrage > „Rückblick“: Tabelle der letzten 7 Tage (gezeigt / Doch nicht /
  Praxis / Weiter / zurück binnen 2 min), ohne Trendpfeil, ohne Ziel, ohne Farbe, ohne Benachrichtigung. Stefan schaut nach
  einigen Tagen selbst nach; ein Anteil „Doch nicht + Alternative“ dauerhaft über dem Ausgangswert und wenige `decline_back` wären
  ein Hinweis, dass es hilft. Ausgangswert: die ersten Tage nach Einführung der Zähler, **bevor** der neue Dialog aktiv ist
  (AP 0 in Abschnitt 9), sonst fehlt der Vergleich.
- Welche Frage wie oft zu „Doch nicht“ führte (`q=` im Log) ist auswertbar, wird aber **nicht** zur automatischen Gewichtung der
  Fragen genutzt (keine Optimierung, die der Nutzer nicht durchschaut).

**Datenschutz:** nur Zähler und Kurzcodes, keine Inhalte, keine Antworten (es gibt keine), alles in `zen_state` (nicht in
Export/Import/Reset/Auto-Backup, D2), keine Gegenstelle.

---

## 7. Neuer Zustand (`zen_state`)

| Schlüssel | Inhalt | Schritt |
|---|---|---|
| `ia_seq`, `ia_text_id`, `ia_seen`, `ia_last` | Rotation der Fragen | 1 |
| `ia_in_<Tag>`, `ia_recent` | Einstiege („Weiter“) heute / letzte 60 min (Boot-gebunden) | 1 |
| `ia_vid_<Tag>` | verschiedene Videos heute (URL-Hashes, max. 200) | 1 |
| `ia_px_last` | zuletzt genutzte Praxis-Quelle | 1 |
| `ia_day_<Tag>`, `ia_days` | Ergebniszähler, 30 Tage | 1 |
| `ia_alt_custom`, `ia_alt_seen`, `ia_alt_last` | eigene Gegenmittel | 2 |
| `ia_pause_end`, `ia_px_sleep`, `ia_ctx_*` (Rotation je Kontext) | Kontextton | 2 |
| `zs_intent_numbers`, `zs_intent_alternative`, `zs_intent_context` (+ `zp_*` ausstehend) | Einstellungen | 1/2 |

Für Gerätetests zusätzlich zurückzusetzen: `ia_*` (AGENTS.md „Zen-Zustand zurücksetzen“ bei Umsetzung ergänzen).

---

## 8. Spätere Änderungen in rules.md (nur Verweis, jetzt nicht geändert)

Abschnitt „Absichtsabfrage“:
- **Wartezeit:** „Weiter“ nach `intent_wait_s`; „Doch nicht“ und Alternative sofort; Knopfgewichtung (3.5).
- **Neue Zeile „Inhalt“:** fester Titel/Hinweis, eine wechselnde Frage pro Abfrage (Rotation wie Pausenspruch), Quelle.
- **Neue Zeile „Tageszahlen“:** Teile, Erscheinen/Ausblenden, Zähler (3.3).
- **Neue Zeile „Alternative“:** Auswahl, Öffnen, Wirkung auf Cooldown/Feed, Router (3.4).
- **„Kanal-Link geteilt“:** „Nur ansehen“ zählt als Bestätigung (setzt Cooldown, `zen_intent_decided`), „gewollt“-Satz entfällt.
- **Cooldown:** gesetzt durch „Weiter“, Blatt-Entscheidung, Alternative.
- **Neue Zeilen Schritt 2:** „Gegenmittel“, „Kontextton“.
- **Neue Zeile „Protokoll/Zustand“:** `intent_prompt …`, `ia_*`.

Abschnitt „Zen-Einstellungen“: Zeilen `intent_numbers`, `intent_alternative`, `intent_context`. decisions.md: D59 von „Entwurf“
auf die getroffenen Entscheidungen fortschreiben.

---

## 9. Technische Skizze

### Betroffene Dateien
| Datei | Änderung |
|---|---|
| `util/ZenIntentPrompt.java` | bleibt Einstieg; `show` baut die Ansicht aus `ZenIntentContent` (eigenes Layout, eigene Knöpfe), neue Methode `acknowledge(reason)`, Ergebnis-Log/-Zähler; Alternative als drittes Ergebnis. `CountDownTimer` beim Schließen abbrechen (heute läuft er nach dem Schließen weiter, harmlos) |
| **neu** `zen/ZenIntentContent.kt` | reine Logik, testbar über `ZenKeyValueStore`: Satzwahl (Muster `ZenQuotes.next/forPause`), Zahlenzeile (aus `ZenWatchTime`, `ia_*`), Kontextwahl (Schritt 2), Ergebniszähler |
| **neu** `zen/ZenIntentAlternative.kt` | Wahl der Praxis-Quelle (`ZenSettingsLock.praxisChannels`, `ia_px_last`), Laden + Start (Code-Weg wie `ZenPraxisFragment.newestOf/playlistOf` und `NavigationHelper.playOnMainPlayer`) |
| `res/layout/zen_intent_prompt.xml` (neu) | Layout 3.1 |
| `res/values{,-de}/zen_intent_strings.xml` (neu) | Titel, Hinweis, Knöpfe, Zahlenformate (Plurals); vorhandene `zen_intent_*` umziehen oder weiterverwenden |
| `res/values{,-de}/zen_intent_lines.xml` (neu, erzeugt) | `zen_intent_questions` (+ Kontext-Arrays in Schritt 2) |
| `zen-docs/gen_sprueche.py` | erzeugt zusätzlich `zen_intent_lines.xml` aus einer Fragen-Tabelle (Frage 4) |
| `zen/ZenChannelImport.kt` (`onView`) | `ZenIntentPrompt.acknowledge` + Intent-Extra |
| `MainActivity.java` | `showIfNeeded`/`onExternalLink` respektieren `zen_intent_decided` (eine Zeile je Stelle) |
| `zen/ZenPause*.kt` | Hook: Praxis-Sitzung startet → `ia_px_last`; Sitzung ≥ 60 s → `ia_vid_<Tag>`; (Schritt 2) `pause_end` → `ia_pause_end` |
| `zen/ZenSettings*.kt`, `ZenSettingsFragment.kt` | neue Schalter, Abschnitt „Absichtsabfrage“, Rückblick, (Schritt 2) Gegenmittel-Liste |

Keine Upstream-Datei außer den bestehenden Hook-Stellen (`MainActivity`, `RouterActivity`) wird berührt.

### Tests
- **Unit** (`ZenIntentContentTest`, `ZenIntentAlternativeTest`, Erweiterung `ZenSettingsLockTest`, `ZenChannelImportTest`):
  Rotation (keine Wiederholung in einer Runde, nie zweimal hintereinander, auch über Rundengrenze, Pool der Größe 1, gleiche
  Abfrage-Nummer = gleicher Satz, Sprachwechsel); Zahlenzeile (alles 0 → keine Zeile, 59 s → keine Minute, 60 min → „1 Std.“,
  nur Ton, Stunden- vs. Tageszeile, Grenzen 1/2 Einstiege, Neustart verwirft `ia_recent`, Zen-Tag-Wechsel 03:59/04:00);
  Alternative (zuletzt genutzt aktiv, zuletzt genutzt entfernt → erste, nur ausstehende → kein Knopf, keine Quelle → null);
  Kontext (Priorität, Schwellen 15 min/22:00/04:00/90 min/30+10 min); Einstellungen (aus = Lockerung, an = sofort, Ersteinrichtung
  frei); Cooldown nach Blatt/Alternative, Alternative gibt Feed nicht frei.
- **Gerät (Defy, DE und EN, Hell/Dunkel/Schwarz):** Kaltstart ohne Zahlen; nach Wiedergabe mit Zahlen; Countdown, Knopfreihenfolge,
  Kontrast (Screenshot); Schrift 200 % + Querformat (scrollt, nichts abgeschnitten); TalkBack kurz (Reihenfolge, eine Ansage);
  Alternative mit Playlist und mit Kanal, während Pflichtpause, ohne Netz (Praxis-Tab + Toast); Kanal-Link → „Nur ansehen“ →
  keine zweite Abfrage, kalter und warmer Prozess; Router-Link → Alternative verwirft Wiedergabe; Drehen bei offener Abfrage
  (gleicher Satz); Log-Einträge und Zähler per `run-as … cat shared_prefs/zen_state.xml`.

### Arbeitspakete (klein, je ein Commit)
| AP | Inhalt | Aufwand |
|---|---|---|
| 0 | Ergebniszähler + Log (`ia_day_*`, `intent_prompt`) am **heutigen** Dialog, damit ein Ausgangswert entsteht | S – **umgesetzt 2026-10-28** (`f6f4ab375`, D72) |
| 1 | Kanal-Blatt: `acknowledge` + `zen_intent_decided` | S – **umgesetzt 2026-10-28** (`8b328afe0`, D72; Gerätetest offen) |
| 2 | Neues Layout, feste Texte, Knopfgewichtung, Barrierefreiheit | S–M |
| 3 | Fragen-Tabelle, Generator, Rotation (`ZenIntentContent`) | S |
| 4 | Zahlenzeile + Zähler `ia_in_*`, `ia_recent`, `ia_vid_*` | S–M |
| 5 | Praxis-Alternative (Wahl, Laden, Start, Fehlerweg, Router) | M |
| 6 | Schalter `intent_numbers`/`intent_alternative`, Rückblick in Einstellungen, rules.md/decisions.md nachziehen | S–M |
| 7 | (Schritt 2) Gegenmittel-Liste + Anzeige | M |
| 8 | (Schritt 2) Kontextton + `intent_context` | S–M |

---

## 10. Abgrenzung zu bestehenden Entscheidungen
- D14 (Absicht nicht gespeichert, kein Freitext): eingehalten; der Hinweis „Antwort im Kopf genügt“ ersetzt das Feld.
- D20 (Ausgabe nur nach „Weiter“, „Doch nicht“ kostet nichts): eingehalten; Alternative gibt den Feed nicht frei, Blatt-Entscheidung
  zählt als Bestätigung (Frage 1).
- D30 (eigene Sprüche ohne Sperre): Vorbild für die Gegenmittel-Liste.
- D31 (Asymmetrie): neue Schalter folgen ihr.
- D39 (Praxis während Pause): Alternative nutzt das, ändert es nicht.

---

## 11. Offene Fragen an Stefan (Empfehlung zuerst)

1. **Kanal-Blatt „Nur ansehen“ zählt als Bestätigung** (Cooldown + Feed-Freigabe, keine zweite Abfrage). Alternative: Wartezeit
   ins Blatt verlegen. *Empfehlung: zählt als Bestätigung, ohne zusätzliche Wartezeit.*
2. **Alternative gibt den Feed nicht frei** (setzt nur den Cooldown; neue Ausgabe erst nach dem nächsten „Weiter“). Alternative:
   wie „Weiter“ behandeln. *Empfehlung: nicht freigeben – wer zur Praxis geht, soll danach nicht in eine frische Ausgabe fallen.*
3. **Praxis-Minuten nicht in der Zahlenzeile.** *Empfehlung: nicht zeigen* (wäre eine Belohnung/Punktzahl); sie stehen schon im
   Praxis-Tab.
4. **Quelle der Fragen:** Tabelle in [sprueche.md](sprueche.md) (neuer Abschnitt) + `gen_sprueche.py` erzeugt
   `zen_intent_lines.xml`. Alternative: Strings direkt im XML. *Empfehlung: über sprueche.md/Generator*, eine Quelle für alle
   Texte dieser Art.
5. **Gegenmittel entfernen ohne Sperre** (wie eigene Sprüche). *Empfehlung: ja, ohne Sperre.*
6. **Zurück-Taste:** weiterhin wirkungslos. Alternative: Zurück = „Doch nicht“. *Empfehlung: wie heute wirkungslos* – ein
   verborgener dritter Weg wäre weniger durchschaubar.
7. **Kontext verlängert die Wartezeit nicht.** *Empfehlung: nein, nur Ton.* Längere Wartezeit spät abends wäre eine eigene
   (strengere) Einstellung, falls gewünscht.
8. **Spät-Fenster 22:00–04:00 fest** in Schritt 2, später an eine Nachtruhe gekoppelt. *Empfehlung: ja, fest, ohne eigene
   Einstellung.*

## 12. Entscheidung von Stefan (2026-10-14)

Stefan übernimmt **alle acht Empfehlungen aus Abschnitt 11** („Merke Dir alle“) und ergänzt: Der Dialog soll **nicht stur würfeln**, sondern aus allen Bausteinen (wechselnde Frage, Zahlenzeile, Praxis-Alternative, später Gegenmittel und Kontextton) **eines oder eine Kombination weise auswählen** (Wortlaut: „wähle eines oder eine Kombination weise aus“).

Umsetzungsvorgabe (Entwurf, vor dem Bau mit Stefan am Gerät abzustimmen):
- Pro Abfrage wird **eine Hauptaussage** gewählt (nie alles gleichzeitig): Frage, Zahlenzeile oder Gegenmittel; die Praxis-Alternative erscheint zusätzlich, wenn eine Quelle markiert ist.
- Die Auswahl richtet sich nach Kontext (Tageszeit, Dauer der letzten Sitzung, Anteil „Doch nicht“ in letzter Zeit, Zahlen vorhanden/nicht null) und nach **Wiederholungsschutz** über alle Bausteine, damit sich nichts abnutzt.
- Sie bleibt **durchschaubar**: kein verstecktes Anreizsystem, die Auswahlregeln stehen in der Spec und in den Einstellungen (Entwurf), nichts wird nach außen gemeldet.

