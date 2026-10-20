# Glossar

**Worum geht es:** Die Fachbegriffe von ZenPipe in je ein bis zwei Sätzen, mit Verweis auf Regel, Code oder Spec.
**Wann nachlesen:** Wenn ein Begriff in rules.md, einer Spec, im Code oder im ZenLog unklar ist.

Verweise: „R:“ = Abschnitt in [rules.md](rules.md), Code relativ zu `app/src/main/java/org/schabi/newpipe/`.

| Begriff | Bedeutung | Verweis |
|---|---|---|
| **Abbruch** | Ende einer Sitzung, ohne dass das Video zu Ende geschaut ist: Player schließen (auch pausiert), Videowechsel, Prozess-Kill. Kein Abbruch: Pausieren, Anruf, Sleep-Timer-Ablauf, „Später fortsetzen“. | R: Pflichtpause; `zen/ZenPause.kt` |
| **Absichtsabfrage** | Dialog mit Wartezeit („Weiter“ erst nach n s, „Doch nicht“ schließt die App) beim Start, bei Links und nach längerer Abwesenheit. | R: Absichtsabfrage; `util/ZenIntentPrompt.java` |
| **Ausgabe** (Edition) | Endliche Feed-Zusammenstellung (Standard 15 Videos), frühestens 60 min nach der letzten, erst nach bestätigter Absichtsabfrage; endet mit der Abschlusskarte. | R: Feed; `zen/ZenFeedState.kt`, [spec-feed-mit-ende.md](spec-feed-mit-ende.md) |
| **Ausnahme** (Inhalts-Erkennung) | Kanal oder Titel-Schlagwort, für das die Inhalts-Erkennung nicht gewichtet (Gewicht 1,0, keine Belastend-Regel, Warum = „Ausnahme“). Hinzufügen ist eine Lockerung. | R: Inhalts-Erkennung „Ausnahmen“; `zen/ZenClassExempt.kt` |
| **Ausstehend** (pending) | Eine Lockerung, die bestätigt ist, aber erst mit dem Ende der 24-h-Sperre wirkt; bis dahin abbrechbar. Schlüssel `zp_*`. | R: Einstellungs-Sperre; `zen/ZenSettingsLock.kt` |
| **Belastend** (Ton) | Ton-Kopf des Klassifikators; belastende Videos bekommen Gewicht × 0,5 und dürfen nicht auf Platz 1–9, nie zwei hintereinander, höchstens 10 % der Plätze. | R: Inhalts-Erkennung „Belastend-Regel“; `ZenFeedPlanner` |
| **Bild / Ton** (Wiedergabeart) | Art der Wiedergabezeit nach Player-Typ: Video-Player = Bild, Hintergrund-Player = Ton; Basis für ein späteres Tageslimit (Zen-Minuten). | R: Wiedergabezeit; `zen/ZenWatchTime.kt` |
| **Bild nie nebenbei** | Verlassen der App oder Bildschirm aus pausiert den Video-Player; Ton läuft nur über „Hintergrund“. Kein Pop-up-Player. | R: Grundregeln, Funktionen |
| **Bildung** | Geplante schmale Kategorie (Liste, ≥ 15 min, Zusatzbudget) – **nicht gebaut**; auch Klassifikator-AP 7 „Bildung ohne Politik“ offen. | [spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md) §3 |
| **Deckel** (Tages-Deckel) | Höchstzahl verschiedener Videos, die pro Zen-Tag in Ausgaben angeboten werden (Standard 30). Nicht mit dem Minuten-Tageslimit verwechseln. | R: Feed „Tages-Deckel (F1)“ |
| **Details nachladen** | Nach dem Bau einer Ausgabe werden ihre Stufe-T-Videos per `StreamInfo` auf Stufe D gehoben (nur WLAN, 1/s, ≤ 30/Zen-Tag); nur die Warum-Zeile ändert sich. | R: Inhalts-Erkennung; `zen/classify/ZenFeedDetails.kt` |
| **e5s-1** | Version des On-Device-Modells (gekürztes `multilingual-e5-small`, int8, mit Köpfen für Thema/Format/Ton), Release-Asset `model-e5s-1`. | `zen/classify/ZenClassifierModel.kt` (`VERSION`), [spec-klassifikator-integration.md](spec-klassifikator-integration.md) §1 |
| **E1–E8** | Entscheidungen zur Klassifikator-Integration (Auslieferung, ABI, Feed-Daten, …), alle Empfehlungen übernommen. | [decisions.md](decisions.md) D43–D49 |
| **Ersteinrichtung** (Setup-Modus) | Zeit nach der Installation bzw. Einführung der Sperre, in der Zen-Einstellungen, Praxis und Ausnahmen sofort und ohne Sperre wirken; endet mit „Fertig“, erstem Wiedergabestart oder nach 24 h (`zs_setup_done`). Im Code `isSetupMode`. | R: Einstellungs-Sperre „Ersteinrichtung“ |
| **F1–F9** | Nutzerentscheidungen vom 2026-10-12 aus dem Konsistenz-Review (Deckel-Zählweise, Tageslimit, Musik, Später-Ablauf, Takt, Später fortsetzen, Sperr-Asymmetrie, Gruppen, Rückgängig). | [decisions.md](decisions.md), [archive/consistency-review.md](archive/consistency-review.md) |
| **Gewicht** (Inhaltsgewicht) | Faktor der Inhalts-Erkennung auf den Score (z. B. Aktuelles 0,3/0,6, Unterhaltung 0,5, sonst 1,0; belastend × 0,5); ohne Kategorie/Modell 1,0. | R: Inhalts-Erkennung „Inhaltsgewicht“; `ZenFeedPlanner.contentWeight` |
| **Importblatt** („Kanal übernehmen“) | Blatt, das ein geteilter/geöffneter Kanal-Link zeigt: Status, Einordnung der neuesten 15 Videos, Aktionen Abonnieren / Als Praxis markieren / Nur ansehen. | R: Inhalts-Erkennung „Kanal übernehmen“; `zen/ZenChannelImport.kt` |
| **Kandidat** | Video, das für eine Ausgabe in Frage kommt: ungesehen, aus Abos, ≤ 7 Tage, kein Live, keine Musik, kein Praxis-Video, ≥ Mindestlänge, Sprache erlaubt. | R: Feed „Kandidaten“; `ZenFeedPlanner.isEligible` |
| **Kurzvideo-Pause** | Feste 60-s-Pause nach jeder gespielten Sitzung eines Videos unter 5 min (D62); Musik/Praxis ausgenommen. Im Feed sind Kurzvideos erlaubt, kommen aber zuletzt. | R: Pflichtpause „Kurzvideo-Pause“; `ZenPauseRules.SHORT_PAUSE_MS` |
| **Lockerung / Verschärfung** | Änderung in Richtung „mehr erlaubt“ (z. B. Deckel hoch, Pause aus, Praxis/Ausnahme hinzufügen) bzw. „weniger erlaubt“. Verschärfung wirkt sofort; Lockerung wird ausstehend und startet die 24-h-Sperre. | R: Zen-Einstellungen, Einstellungs-Sperre; [decisions.md](decisions.md) D31 |
| **Musik / Podcast** (`ZenKind`) | Art einer Sitzung aus `StreamInfo` (Kategorie, Kanalname, Titel); Musik löst keine Pause aus, Podcast zählt wie Video. | R: Pflichtpause „Musik vs. Podcast“; `ZenContentKind` |
| **Pausenspruch** | Spruch auf dem Pausenbildschirm, einer pro Pause; Quelle [sprueche.md](sprueche.md). | R: Pflichtpause „Pausenspruch“; `zen/ZenQuotes.kt` |
| **Pflichtpause** | Sperre neuer Wiedergaben (außer Praxis) nach einem Abbruch ab 5 min Wiedergabezeit; 20 % davon, 1–15 min, Countdown auf Gerätelaufzeit. | R: Pflichtpause; [spec-pause-nach-abbruch.md](spec-pause-nach-abbruch.md) |
| **Praxis** | Vom Nutzer markierte Kanäle/Online-Playlists (Yoga, Atmen, Meditation, Einschlafhilfen): eigener Tab, außerhalb der Ausgabe, keine Pflichtpause, dürfen während einer Pause starten. | R: Praxis; [spec-praxis.md](spec-praxis.md) |
| **Praxis-Kette** | Einzige Form des Weiterspielens: am Ende eines Praxis-Videos startet das nächste Element nur, wenn es nach Listendaten ebenfalls Praxis ist (`chain_stop` sonst). | R: Praxis „Praxis-Kette“; `Player.zenOnItemEnd` |
| **Prüfansicht** | Einstellungen > Zen > „Eingeordnete Videos“: letzte 100 Einordnungen mit Warum-Zusammenfassung und Rohwerten, seit `8a37a5ff5` mit Korrektur-Labels und Debug-Export. | R: Inhalts-Erkennung „Prüfansicht“; `zen/classify/ZenClassReview.kt` |
| **Prüfung beim Markieren** (Plausibilitätsprüfung) | Vor dem Markieren als Praxis werden 15 Videos des Kanals/der Playlist eingeordnet; „passt“ / Warnung / deutliche Warnung / „kein klares Bild“. Blockiert nie. | R: Inhalts-Erkennung; `zen/classify/ZenPlausibilityCheck.kt` |
| **Rückgängig** (Undo) | 5 min nach einer Bestätigung zurück auf exakt den vorherigen Stand, inklusive Sperre; startet keine Sperre. | R: Einstellungs-Sperre „Rückgängig (F9)“ |
| **Ruhezeit-Gutschrift** (Ruhezeit-Anrechnung) | Die Zeit seit dem letzten „spielt → spielt nicht“ einer Sitzung wird von der Pflichtpause abgezogen (nur im selben Boot); ergibt sie 0, gibt es keine Pause. | R: Pflichtpause „Ruhezeit-Anrechnung“; `ZenPauseEngine.restMs` |
| **Score** | Grundwert eines Kandidaten: Gruppen-Rang (60 − 15 je Rang) + Längenbonus + Frische (0–20); mit Inhalts-Erkennung × Gewicht. | R: Feed „Scoring“; `ZenFeedPlanner.score` |
| **Sitzung** | Eine Wiedergabe eines Videos im Player; Fortsetzen später ist eine neue Sitzung. Basis für Pause und Wiedergabezeit (`ps_*`). | [spec-pause-nach-abbruch.md](spec-pause-nach-abbruch.md) „Begriffe“ |
| **Sleep-Timer** | Mond-Knopf: nach 15–90 min (Debug: 1 min) 30 s Fade, dann Pause; Ablauf ist kein Abbruch. | R: Praxis „Sleep-Timer“; `zen/ZenSleep.kt` |
| **Später fortsetzen** | Rückfrage beim Mini-Player-X: Schließen ohne Pflichtpause, 2× pro Zen-Tag. Nicht verwechseln mit der Später-Liste. | R: Pflichtpause „Später fortsetzen (F6)“ |
| **Später-Liste** | Lokale Playlist „Watch later“ für Feed-Einträge, max. 10, Ablauf 30 Tage, vom Deckel ausgenommen. | R: Feed „Später-Liste“; `zen/ZenWatchLater.kt` |
| **Sperre** (24-h-Sperre, Einstellungs-Sperre) | Globaler Countdown (`settings_lock`) nach einer Lockerung: 24 h Gerätelaufzeit lang ist jede weitere Lockerung abgelehnt, strenger bleibt möglich. | R: Einstellungs-Sperre; [spec-einstellungs-sperre.md](spec-einstellungs-sperre.md) |
| **Stufe T / D** | Einordnungsstufe im Klassifikations-Cache: T = nur Titel + Kanal (Feed, Worker), D = mit Beschreibung/Tags/Kategorie (geöffnete Videos, Prüfung, Details nachladen). T → D löst eine Neuberechnung aus. | R: Inhalts-Erkennung „Cache“; `zen/classify/ZenClassCache.kt` |
| **Vertrauenszeit** | Uhrzeit aus `ZenClock`: Wanduhr, gegen `elapsedRealtime` und Boot-Zähler geprüft; Grundlage für Zen-Tag und Ausgabe-Takt. | R: Grundregeln „Vertrauenszeit“; `zen/ZenClock.kt` |
| **Vorbereitete Ausgabe** | Eine fällige Ausgabe wartet bis 20 s, bis ihre Kandidaten eingeordnet sind (Karte „wird vorbereitet … (n von m)“). | R: Inhalts-Erkennung „Auswahlmoment“; `zen/ZenEditionPrep.kt` |
| **Warum-Zeile** | Kurze Wendung unter einem Feed-Eintrag ohne Zahlen („eher Aktuelles · belastend“, „noch nicht eingeordnet“, „Ausnahme“); Sicherheiten/Gewicht/Stufe nur in der Prüfansicht; nur Anzeige. | R: Inhalts-Erkennung „Warum-Zeile“; `ZenClassWhy`, `Pick.why` |
| **Wiedergabezeit** | Echte Abspielzeit im Zustand „spielt“ auf `elapsedRealtime` (Echtzeit, nicht Inhaltszeit); pro Sitzung für die Pause, pro Zen-Tag in `wt_<Tag>`. | R: Grundregeln „Wiedergabezeit“ |
| **Zen-Minuten** | Hook für ein späteres Tageslimit: Bild + Ton/2, Musik und Praxis 0. Kein Limit gebaut. | R: Feed „Tageslimit Minuten (F2)“; `ZenWatchTime.Day.zenMinutesMs` |
| **Zen-Tag** | Kalendertag mit Wechsel um 04:00 in der beim ersten Start fixierten Zeitzone; Basis für Deckel, Später-Zähler, Bilanzen. | R: Grundregeln „Zen-Tag“; `zen/ZenDay.kt` |
| **ZenFeatures** | Zentrale Schalter für abgeschaltete Upstream-Funktionen (Downloads, Pop-up, Minimieren, Ankündigungen); jeder Einstieg fragt sie ab. | R: Funktionen; `zen/ZenFeatures.kt` |
| **ZenLog** | Lokales Zen-Protokoll der Ereignisse (z. B. `pause_start`, `class_feed_prep`), nicht im Export. | `zen/ZenLog.kt`; Ereignislisten je Abschnitt in rules.md |
| **`zen_state`** | Eigene SharedPreferences-Datei für allen Zen-Zustand, ausgenommen von Export/Import/Reset/Auto-Backup. | R: Grundregeln „Zen-Zustand“; `zen/ZenStore.kt` |
