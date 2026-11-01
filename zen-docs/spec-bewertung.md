# Spec: Lokale Bewertung „Mehr davon / Weniger davon“ und Like-Liste

**Worum geht es:** Eine eigene, rein lokale Bewertung einzelner Videos, die die nächsten Feed-Ausgaben leicht lenkt, und eine
ruhige Liste der Videos, die ein echtes YouTube-Like wert sind (ZenPipe hat keinen Login).
**Status:** umgesetzt 2026-11-01 (Unit-Tests grün, Gerätetest offen). Entscheidungen D79–D82 in [decisions.md](decisions.md),
Werte in [rules.md](rules.md) „Feed“ (Zeile „Bewertung“). Bei Widerspruch gilt rules.md.

## 1. Verhalten

- **Einstieg:** Long-Press-Menü jedes Videos (`InfoItemDialog`, alle Listen inkl. Feed) und langer Druck auf den Titel der
  Videoseite, beide mit demselben Wortlaut **„Mehr oder weniger davon…“** (wie „Als Praxis-Video markieren“, D68). Die Videoseite
  bleibt nach dem Ende stehen, damit ist „nach dem Schauen“ abgedeckt; der Pausenbildschirm bleibt unverändert (Frage 2).
- **Dialog:** Titel = Videotitel, zwei Zeilen mit Symbol: ⊕ **Mehr davon**, ⊖ **Weniger davon**; die gewählte Zeile trägt ein
  „✓“. Ist das Video bewertet, kommt ◯ **Bewertung entfernen** dazu. Gleiche Wahl noch einmal = entfernen (Umschalten),
  die andere Wahl ersetzt. Toast nennt die Wirkung („wirkt ab der nächsten Ausgabe“, „ausgeblendet wird nichts“).
- **Symbole:** keine Daumen (Stefan). Plus im Kreis / Minus im Kreis / leerer Kreis (`zen_ic_more`, `zen_ic_less`,
  `zen_ic_rating_clear`, Material-Pfade Apache 2.0), Farbe `?attr/colorAccent`: hell `#3E6A5E` auf Dialog `#F3F1EC` = 5,4:1,
  dunkel `#9DBFB2` auf `#222927` = 7,4:1, auf Schwarz höher (Formel [spec-design.md](spec-design.md)).
- **Wirkung:** nur die Reihenfolge der **nächsten** Ausgaben auf diesem Gerät (Abschnitt 3). Die laufende Ausgabe wird nie neu
  gewürfelt. Feed-Zeile des Eintrags nennt es: „… · mehr davon“ / „… · weniger davon“ (ohne Zahlen, D65), auch ohne
  Inhalts-Erkennung.
- **Like-Liste:** „Mehr davon“ legt das Video zusätzlich auf die Like-Liste. Einstellungen > Zen > Abschnitt „Mehr oder weniger
  davon“: Knöpfe **„Like-Liste (n)“** und **„Bewertungen (n)“**. Like-Liste: neueste zuerst, Tipp auf einen Eintrag →
  Dialog mit **„Bei YouTube liken“** (öffnet das Video außerhalb von ZenPipe: YouTube-App, sonst Standardbrowser) und
  **„Erledigt“** (Eintrag weg, Bewertung bleibt). „Bewertungen“: alle Bewertungen mit Symbol, Tipp → „Bewertung entfernen“.
- **Sperre:** Bewerten, Ändern, Entfernen und „Erledigt“ sind **neutral** (sofort, auch während der Sperre, keine Sperre,
  kein Rückgängig-Eintrag), wie die Reihenfolge der Feed-Gruppen (F8).

## 2. Daten

`zen_state` (wie alle Zen-Zustände; nicht in NewPipe-Export/Import/Reset, nicht im Auto-Backup/Gerätetransfer, `zen_backup_rules.xml`
und `zen_data_extraction_rules.xml` bleiben unverändert, D81):

| Schlüssel | Inhalt | Grenze |
|---|---|---|
| `rt_items` | je Zeile `serviceId⇥url⇥wert(+1/−1)⇥kanal-key⇥kanalname⇥thema⇥zeit⇥titel`, älteste zuerst | 200, älteste fällt weg |
| `rt_likes` | je Zeile `serviceId⇥url⇥zeit⇥kanalname⇥titel` | 100, älteste fällt weg |

Schlüssel des Videos = `ZenPraxisChannel.keyOf(serviceId, url)` (normalisierte URL, nie DB-IDs). Kanal-Key = dasselbe für die
Kanal-URL (leer, wenn unbekannt: dann keine Kanalwirkung). Thema = stärkstes Thema aus `zen_class_cache.db` beim Bewerten, nur
mit funktionierendem Modell und nur ab der Modellschwelle 0,6 (`ZenClassWhy.belowThreshold`), sonst leer. Die Ausgabe speichert
je Eintrag das Vorzeichen (`Pick.rated`, 5. Tab-Feld in `feed_edition_picks`, alte Zeilen bleiben lesbar).
Protokoll (`ZenLog`, nicht exportiert): `rating action=more|less|remove change=set|changed|removed theme=…`, `like_open`,
`like_done`; `edition … more=n less=n`, wenn eine Ausgabe bewertete Einträge hat. Der Debug-Export der Einordnungen enthält
keine Bewertungen.

## 3. Wirkungsformel (`ZenRatingWeights`)

```
n_k = clamp(Summe der Bewertungen (+1/−1) mit diesem Kanal, −3, +2)
n_t = clamp(Summe der Bewertungen mit diesem Thema,         −3, +2)
f   = clamp( (1 + 0,10·max(n_k,0)) · 0,80^max(−n_k,0) · (1 + 0,05·max(n_t,0)) · 0,90^max(−n_t,0), 0,40, 1,30 )
Reihenfolge-Score = Grund-Score × Inhaltsgewicht (falls an) × Kurzvideo-Faktor × f
```

- Kanal: +10 % je Schritt (max. +20 %), −20 % je Schritt (bis 0,51); Thema halb so stark (+5 % / −10 %). Gesamt 0,40–1,30.
  +20 % ≈ eine Feed-Gruppen-Stufe (15 Punkte) bei typischen Scores um 70: „klein“.
- Thema des Kandidaten nur, wenn die Inhalts-Erkennung für die Ausgabe aktiv ist (sonst nur der Kanal).
- **Nie Ausblenden:** f > 0, Eignung, Ausgabe-Größe, Tages-Deckel, Takt, Sprachfilter, Praxis, Belastend-Regel und
  Kurzvideo-Regel (D62: nie vor einem längeren) bleiben unverändert. Ein herabgestuftes Video füllt weiter freie Plätze.
- **Kein Aufweichen der Inhalts-Erkennung (D42):** f multipliziert nur, das Inhaltsgewicht bleibt; Aktuelles (0,3) × 1,3 = 0,39
  bleibt weit unter einem Video ohne Kategorie (1,0).

## 4. Randfälle

- Video ohne Kanal-URL (manche Listen): nur Themenwirkung, falls ein Thema bekannt ist.
- Praxis-Video bewertet: kommt ohnehin nicht in die Ausgabe; „Mehr davon“ legt es trotzdem auf die Like-Liste.
- Mehr und Weniger am selben Kanal heben sich auf (Summe).
- Bewertung eines Videos in der laufenden Ausgabe: Zeile und Reihenfolge ändern sich erst mit der nächsten Ausgabe.
- Reset/Neuinstallation: Bewertungen und Like-Liste sind weg (wie alle Zen-Zustände, D81); kein Sperr-Risiko, weil neutral.
- „Bei YouTube liken“ ist die einzige Stelle, die einen YouTube-Link bewusst an eine andere App gibt (sonst kopiert ZenPipe
  YouTube-Links statt sie zu öffnen, `ShareUtils`); ZenPipe selbst stellt keine Netzverbindung her (D80).

## 5. Offene Fragen (für Stefan)

1. **Wöchentlicher Hinweis** auf die Like-Liste (z. B. einmal pro Woche eine Zeile auf der Abschlusskarte des Feeds, nur wenn
   die Liste nicht leer ist)? Nicht gebaut. Empfehlung: **nicht bauen**, die Liste in den Einstellungen reicht; wenn doch, dann nur
   die Zeile auf der Abschlusskarte (kein Dialog, keine Benachrichtigung).
2. **Pausenbildschirm:** „Weniger davon“ für das gerade abgebrochene Video dort anbieten? Nicht gebaut, weil der Bildschirm
   auch bei gesperrten Starts erscheint und das letzte Video samt Kanal dort nicht bekannt ist. Empfehlung: **nicht bauen**.
3. **Neutral statt Lockerung:** Bewertungen laufen ohne 24-h-Sperre (D82, wie Feed-Gruppen F8). Empfehlung: **so lassen**; eine
   Sperre für „Mehr davon“ würde die Funktion praktisch nutzlos machen, die Wirkung ist auf ±30 % der Reihenfolge begrenzt.
4. Stärke der Schritte (0,10/0,80, Thema halb) nach einigen Wochen Nutzung am Gerät prüfen (Log `edition … more= less=`).

## 6. Tests

Unit-Tests `ZenRatingsTest`: Umschalten/Ersetzen, Like-Liste (Mehr, Weniger, Entfernen, Erledigt), Obergrenzen, Kodierung mit
Tabs/Zeilenumbrüchen und kaputten Zeilen, Formel (Schritte, Klammern, nie 0, Aufheben, ohne Kanal), nur Reihenfolge (gleiche
Menge, Kurzvideo bleibt hinten, Livestream bleibt draußen, Budget gleich), Aktuelles bleibt unter normal, Speichern/Laden von
`Pick.rated` inkl. alter 4-Feld-Zeilen.
**Am Gerät (offen):** Long-Press im Feed und Titel-Long-Press zeigen „Mehr oder weniger davon…“; Dialog mit Symbolen in hell und
dunkel lesbar, ✓ und „Bewertung entfernen“; Toast; nächste Ausgabe zeigt „· mehr davon“ bzw. „· weniger davon“ und die
Reihenfolge; Einstellungen > Zen: Zähler, Like-Liste, „Bei YouTube liken“ öffnet die YouTube-App (bzw. Browser) und nicht
ZenPipe selbst, „Erledigt“ entfernt; Bewerten während einer laufenden Sperre geht.
