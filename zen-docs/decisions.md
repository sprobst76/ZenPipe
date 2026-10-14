# Entscheidungsprotokoll (ADR-light)

**Worum geht es:** Warum ZenPipe so ist, wie es ist: getroffene Entscheidungen mit Datum, Grund, verworfenen Alternativen und
Beleg (Regel, Code, Spec, Commit). Rückwirkend aus Specs, [rules.md](rules.md), dem Konsistenz-Review und der Commit-Historie.
**Wann nachlesen:** Bevor du eine Regel änderst oder ein Feature baust, das eine dieser Entscheidungen berühren könnte.

Werte stehen in [rules.md](rules.md) (bei Abweichung gilt rules.md). Datum = Commit- bzw. Dokumentdatum. „Stefan“ =
Nutzerentscheidung. F1–F9 stammen aus [archive/consistency-review.md](archive/consistency-review.md) (alle Empfehlungen am
2026-10-12 übernommen), E1–E8 aus [spec-klassifikator-integration.md](spec-klassifikator-integration.md) Abschnitt 9; „Protokoll 14.x“ =
[klassifikator-umsetzungsprotokoll.md](klassifikator-umsetzungsprotokoll.md).
Neue Einträge unten anhängen, fortlaufend nummeriert.

## Grundlagen und Identität

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D1 | 2026-10-12 | Fork von NewPipe als kleiner Patch-Satz; eigener Code im Paket `zen`, Upstream nur Hooks; NewPipeExtractor unverändert | Wartung gegen YouTube-Änderungen bleibt beim Upstream; Rebase bleibt billig | Eigene App; Fork von PipePipe/Tubular; Extractor-Patches | Konzept §4, [../AGENTS.md](../AGENTS.md), [archive/feature-audit.md](archive/feature-audit.md) „Architektur-Leitlinie“ |
| D2 | 2026-10-12 | Zen-Zustand in eigener Prefs-Datei `zen_state`, ausgeschlossen von Export/Import/Reset/Auto-Backup; Videos nach `serviceId+url` geschlüsselt | Import/Reset darf kein Bypass sein; DB-IDs ändern sich beim Import | Prefs-Datei `zen` in den Default-Prefs; Room-UIDs | `64ac2d489`, F-Zusatz, rules.md „Zen-Zustand“ |
| D3 | 2026-10-12 | Zen-Tag wechselt um 04:00, Zeitzone beim ersten Start fixiert; Fristen nur auf Gerätelaufzeit (`ZenCountdown`), fail-closed über Neustart | Nachtstunden gehören zum Vortag; Uhr/Zeitzone/Neustart dürfen nichts verkürzen | Mitternacht; Wanduhr; HTTP-`Date` als dritte Quelle (nicht gebaut) | `64ac2d489`, `f66a12bae`, rules.md „Zen-Tag“, „Zeitkritische Countdowns“ |
| D4 | 2026-10-12 | Name „ZenPipe“, applicationId `io.github.sprobst76.zenpipe` (Debug `.debug`), Namespace bleibt `org.schabi.newpipe`; nur en+de ausgeliefert | Parallel zu NewPipe installierbar, kein „NewPipe“ im Namen; Namespace hält Merges sauber; andere Sprachen nennen „NewPipe“ | Reserve-Name StillPipe; eigene Markenanmeldung (derzeit nicht) | `fa0fe169c`, `2678ead2c`, [research-name-und-offene-fragen.md](research-name-und-offene-fragen.md) §1.4/§5.3, [release.md](release.md) |
| D5 | 2026-10-12 | Keine Anfragen an NewPipe-Server: Update-Check hart aus, Crash-Reports nur als GitHub-Issue bei ZenPipe (keine E-Mail); Upstream-Ankündigungsdialoge aus (2026-10-14) | Datenschutz, Markenrichtlinie, keine fremden Gegenstellen | Update-Check eigener Server | `b771fbf30`, `2430cd282`, rules.md „Funktionen“ |
| D6 | 2026-10-12 | Verteilung über GitHub-Releases + eigenes F-Droid-Repo (GitHub Pages, eigener Repo-Schlüssel) + Obtainium; IzzyOnDroid ab 1.0, offizielles F-Droid optional (Reproducible Build); nicht Play Store | YouTube-ToS; eine Signatur über alle Kanäle | Play Store; nur offizielles F-Droid (fremde Signatur) | Konzept §4, `14cca332e`, `b6db2cbb9`, [release.md](release.md) |
| D7 | 2026-10-12 | `versionCode = upstream × 1000 + n`, versionName `<x.y.z>-zen.<n>`, Tag `v<versionName>` | Monoton steigend auch nach Upstream-Merges | – | `b771fbf30`, [release.md](release.md) „Version scheme“ |
| D8 | 2026-10-13 | Signierung nur aus Umgebungsvariablen `ZENPIPE_*` (ohne = unsigniert); Keystore-Passwörter nur ASCII | Keine Schlüssel im Repo; Nicht-ASCII-Passwörter scheiterten in CI (Passwort wurde rotiert) | Schlüssel-Datei im Repo; UTF-8-Locale (`b14341686`; laut Projekt-Memory half `LC_ALL` nicht, die Locale-Zeilen in `release.yml` sind seitdem wirkungslos, Entfernen vorgeschlagen) | `b556424fe`, `b14341686`, [release.md](release.md) „Signing“, „Passwords ASCII only“ |

## Kein Sog

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D9 | 2026-10-12 | Drawer nur Abos, Feed, (Praxis,) Playlists, Suche; Startseite = Feed; Kiosk/Trends/Verlauf/Downloads raus | Keine Einstiege in algorithmische Listen | Einstellbarer Drawer | `d5d11a18a`, rules.md (Grundregeln), STATE „Erledigt 1“ |
| D10 | 2026-10-12 | Autoplay dauerhaft aus (auch für gewählte Videos); Auto-Queue, Related, Kommentare fest aus | Kernwert „nichts startet von selbst“ | Autoplay nur im WLAN (Audit-Vorschlag) | `a4f7fdf69`, STATE „Autoplay bleibt dauerhaft AUS (Entscheidung)“, rules.md „Autoplay“ |
| D11 | 2026-10-13 | Kein Weiterspielen in Warteschlangen/Playlists: Player pausiert am Ende jedes Videos; einzige Ausnahme Praxis-Kette | Auch selbst gebaute Warteschlangen wurden zum Sog | NewPipe-Verhalten (nahtlos weiter) | `c504d1733`, rules.md „Weiterspielen“, [spec-praxis.md](spec-praxis.md) „Praxis-Kette“ |
| D12 | 2026-10-12 | Shorts nirgends (Listen, Kanal-Tab, Feed, RSS, `/shorts/`-Links); Livestreams nicht im Feed/Kanal-Tab; Remote-Suchvorschläge aus | Kurzformate und Live sind Sog-Formate | Shorts nur ausblenden; Watch-Links auf Short-IDs blockieren (Restrisiko akzeptiert) | `6e651dee4`, `f23fb74f6`, `59ed0cdd2` |
| D13 | 2026-10-12 | „Link kopieren“ statt „Im Browser öffnen“; YouTube-Links bleiben in der App | Browser führt zurück zu youtube.com mit Empfehlungen | Browser-Link behalten | `75a7b78ea` |
| D14 | 2026-10-12 | Absichtsabfrage mit Wartezeit (5 s) bei Start, Links, Rückkehr nach ≥ 5 min; nur eigene Wiedergabe unterdrückt sie; die Absicht wird **nicht** gespeichert | Atempause vor dem Impuls (One-Sec-Muster); Freitext nicht gewünscht | Absicht als Text/Chips speichern (Audit Z1) | `5ab0108ba`, `ce88458d2`, `f66a12bae`, STATE „Offen: Absicht wird nicht gespeichert (nicht gewünscht)“ |
| D15 | 2026-10-13 | Pop-up-Player entfernt; „Bild nie nebenbei“: App verlassen/Bildschirm aus pausiert das Video, Ton nur über „Hintergrund“ | Bild nebenbei ist Berieselung; entfernt auch einen Upstream-Absturz | Pop-up behalten; Minimieren → Hintergrund | `76011669c`, Stefan 2026-10-13, rules.md „Bild nie nebenbei“, „Funktionen“ |
| D16 | 2026-10-13 | Downloads aus über `ZenFeatures`, Code nicht gelöscht (später per Einstellung freischaltbar) | Downloads als Vorrat sind Sog; Löschen würde Merges erschweren | Code entfernen | `356a0da21`, rules.md „Funktionen“ |
| D17 | 2026-10-13 | Reduzierte Videodetails standardmäßig an (keine Beschreibung, Zähler, Datum), ohne „Details anzeigen“-Hintertür; Aus = Lockerung; Zähler auch in Listen | Zahlen und Beschreibungslinks ziehen weiter | Per Tipp aufklappbar | `162d6dffc`, `9edc1647b`, rules.md „Zen-Einstellungen“ |

## Feed als Ausgabe

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D18 | 2026-10-12 | Feed = Ausgabe mit 15 Videos, lokales erklärbares Scoring (Gruppen-Reihenfolge = Priorität, Längenbonus, Frische, max. 3 je Kanal), Mindestlänge 5 min nur im Feed, Abschlusskarte | „Zeitung statt Strömung“; Fokus auf längere Videos | Endloser Feed mit „gesehene ausblenden“; „nur neu seit letztem Besuch“ (Audit Z2) | `114108440`, [spec-feed-mit-ende.md](spec-feed-mit-ende.md), rules.md „Feed“ |
| D19 | 2026-10-12 | **F5:** neue Ausgabe frühestens 60 min nach der letzten (nicht zur vollen Stunde) | Weniger Termin-Anreiz | Volle Stunde | F5, `EDITION_INTERVAL_MS` |
| D20 | 2026-10-12 | **F1:** Tages-Deckel 30 zählt **verschiedene angebotene** Videos; Ausgabe erst nach bestätigter Absichtsabfrage | „Doch nicht“ kostet nichts; Deckel begrenzt die Auswahl, nicht die Zeit | Angebotene inkl. Doppelte; nur angetippte | F1, rules.md „Tages-Deckel“ |
| D21 | 2026-10-12 | **F3:** keine Musik(-Kanäle) im Feed, kein Längenbonus für Musik | Lange Mixes würden sonst oben landen | – | F3, `ZenFeedPlanner` (Musik-Heuristik) |
| D22 | 2026-10-12 | **F4:** Später-Liste max. 10, Ablauf nach 30 Tagen, vom Deckel ausgenommen; Umweg über eigene Playlists bewusst akzeptiert | Aufschieben statt Impuls, ohne zweiten Feed | 15 Einträge; Umweg sperren | F4, `ZenWatchLater`, Review W25 |
| D23 | 2026-10-12 | Kanalseiten max. 15 Einträge, Suche max. 2 Seiten | Sonst trivialer Feed-Ersatz | Unbegrenzt | `ba0aa56da`, rules.md „Kanalseite“, „Suche“ |
| D24 | 2026-10-12 | **F2:** Minuten-Tageslimit ja, aber erst nach der Einstellungs-Sperre; Wert offen; gewichtet nach Wiedergabeart (Bild + Ton/2) | Stück-Deckel begrenzt nur das Angebot | Kein Limit; Gewichtung nach Inhaltsart | F2, rules.md „Tageslimit“, [spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md) 2.4 (Gewichtung: Annahme, nur Hook) |
| D25 | 2026-10-14 | Feed-Sprachfilter `feed_langs` (Default Deutsch + Englisch): Sprache entfernen = strenger, hinzufügen = Lockerung; unsichere Erkennung bleibt im Feed | Fremdsprachige Videos aus Abos nicht anbieten; lokal ohne Bibliothek | – | `3bdcfd2a6`, rules.md „Sprachen im Feed“, ausführlich D53 |

## Pflichtpause

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D26 | 2026-10-12 | Pflichtpause nach Abbruch einer Sitzung ab 5 min Wiedergabezeit: 20 %, min 1, max 15 min; Musik ausgenommen, Podcasts inklusive; Pausieren ist kein Abbruch, pausiert schließen und Prozess-Kill schon | Abbruch = Zeichen für Zappen; streng, aber kurz | App-Wechsel als Abbruch; Podcasts ausnehmen | Stefan 2026-10-12, `7ae341b11`, `6e59f0c43`, rules.md „Pflichtpause“ |
| D27 | 2026-10-12 | „Zu Ende geschaut“ = Position ≥ 90 % oder Rest ≤ 60 s; Wiedergabezeit = echte Abspielzeit **einer** Sitzung | Abspann soll keine Pause auslösen; Sitzungen werden nicht addiert | Wanduhr; Summe über Sitzungen | Review W12/W13, rules.md |
| D28 | 2026-10-12 | **F6:** „Später fortsetzen“ ohne Pause, 2× pro Zen-Tag, als Rückfrage beim Mini-Player-X | Podcast-/Anruf-Fall ohne Hintertür | Aktion in der Benachrichtigung (Android 11: nur 3 Aktionen) | F6, rules.md „Später fortsetzen“ |
| D29 | 2026-10-13 | Ruhezeit-Gutschrift: Pause = max(0, Länge − Ruhezeit seit letztem Spielen), nur im selben Boot | In der Ruhezeit lief nachweislich nichts; löst „pausiert, später weggewischt“ | Nur „Später fortsetzen“ | `b61bb1998`, rules.md „Ruhezeit-Anrechnung“; **Annahme, von Stefan zu bestätigen** (STATE, [spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md) 10.3) |
| D30 | 2026-10-13 | Pausenspruch: einer pro Pause, nur gemeinfreie belegte Zitate + eigene Sätze; eigene Sprüche ohne Sperre (keine Lockerung); nicht belegbare Zuordnung gestrichen (Q27) | Ein Spruch macht die Pause weder kürzer noch umgehbar | – | `e8507cd4a`, `8e1767afd`, rules.md „Pausenspruch“ |

## Einstellungs-Sperre

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D31 | 2026-10-12 | **F7:** Strenger stellen wirkt sofort; Lockern wird „ausstehend“ und wirkt erst nach 24 h (abbrechbar); **nur Lockern startet die Sperre** | Schützt den heißen Moment; strenger ist nie ein Schlupfloch | Lockern sofort + danach 24 h Sperre (erste Spec); Lockern erst 04:00 | F7, rules.md „Auflösung Spec-Widerspruch“, `4b184ef9d` |
| D32 | 2026-10-12 | **F8:** Feed-Gruppen (Zuordnung/Reihenfolge), Abos, Playlists, Suche nicht gesperrt | Gruppen gewichten nur, vergrößern nichts | Gruppen-Reihenfolge sperren | F8 |
| D33 | 2026-10-12 | **F9:** 5-min-Rückgängig nach Bestätigung (stellt auch die Sperre wieder her); Bestätigung mit 3 s Wartezeit; gruppierte Änderungen = eine Sperre | Tippfehler beim Strengerstellen | 30-s-Countdown + Begründungsfeld (Audit Z6) | F9, `e1e19fe65` |
| D34 | 2026-10-12 | Ersteinrichtung ohne Sperre, endet mit „Fertig“, erstem Wiedergabestart oder nach 24 h Gerätelaufzeit | Nie abgeschlossene Einrichtung darf keine Dauer-Freiheit geben | Bis „Fertig“ frei | Review S8/S9, rules.md „Ersteinrichtung“ |
| D35 | 2026-10-12 | Kein Notfallschlüssel | Jede Abkürzung wäre eine Hintertür | Opal-Stil 1×/Woche | rules.md „Notfallschlüssel“, [spec-einstellungs-sperre.md](spec-einstellungs-sperre.md) |
| D36 | 2026-10 | Sperr-Umgehung nur im Debug-Build per `run-as` (Gerätetests), nie im Release-Code | Entwicklerumweg akzeptiert, Release nicht `debuggable` | – | [../AGENTS.md](../AGENTS.md) „Operations“, Spec „Umgehungsschutz“ |

## Praxis

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D37 | 2026-10-13 | Praxis ist eine vom Nutzer gepflegte Liste von Kanälen und Online-Playlists, nie Titel-/Stichwort-Heuristik; alle Videos eines Praxis-Kanals sind Praxis; lokale Playlists nicht | Heuristiken wären Hintertüren; lokale Playlists beliebig befüllbar | Automatische Erkennung; Praxis pro Video | `7cc724da4`, `c504d1733`, rules.md „Zuordnung“ |
| D38 | 2026-10-13 | Praxis hinzufügen = Lockerung (24-h-Sperre), entfernen sofort; Praxis-Videos außerhalb von Ausgabe/Deckel und ohne Pflichtpause; Praxis-Minuten ohne Limit | Praxis soll verfügbar sein, der Zugang aber geschützt | Weiches Limit (nur Hook) | rules.md „Praxis“ |
| D39 | 2026-10-13 | Praxis-Videos dürfen während einer laufenden Pflichtpause starten (Pause läuft weiter) | Atemübung/Einschlafhilfe gerade dann sinnvoll | Pause sperrt alles | Stefan 2026-10-13, rules.md „Pflichtpause“ (Praxis) |
| D40 | 2026-10-13 | Sleep-Timer an den Player gebunden (über Praxis-Kette hinweg), kein „Ende des Videos“, Ablauf = Pause ohne Abbruch | Einschlaf-Kette soll nicht ohne Timer weiterlaufen; am Videoende hält der Player ohnehin | An das Video gebunden | `527c8fbf1`, [spec-praxis.md](spec-praxis.md) „Sleep-Timer“ |

## Design

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D41 | 2026-10-13 | Palette „Salbei“ (A), Nachtvariante Dunkel statt AMOLED-Schwarz, nirgends Rot, kein Material You, Medien-Benachrichtigung nicht eingefärbt | Größter Ruhegewinn bei kleinstem Patch (nur Farbwerte) | B „Stein“, C „Papier“ (mehr Layout-Patches); Schwarz als Nacht | `fb19931b7`, `a953e242f`, [spec-design.md](spec-design.md), rules.md „Darstellung“ |

## Inhalts-Erkennung (Klassifikator)

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D42 | 2026-10-13 | Lokaler Klassifikator wirkt nur strenger (Gewichtung, Warnung, Belastend-Regel); entscheidet nie über Praxis; Abschwächendes ist eine Lockerung | Kein Algorithmus, der Inhalte freischaltet | Klassifikator als Praxis-Erkennung (E5 b) | rules.md „Inhalts-Erkennung“, E5 |
| D43 | 2026-10-13 | **E1:** Modell nicht im APK, Opt-in-Download eines festen GitHub-Release-Assets (Zip, SHA-256) nur nach Tipp; Asset `model-e5s-1` | Repo/Updates klein; ohne Modell läuft alles wie vorher | Im APK; Git LFS | E1, `016212132`, `4b7d96677` |
| D44 | 2026-10-13 | **E2:** APK nur `arm64-v8a` (Debug zusätzlich x86_64) | ONNX-Runtime je ABI groß | ABI-Splits; universal (+140 MB) | E2, `abiFilters` |
| D45 | 2026-10-13 | **E3:** Feed nur mit Titel + Kanal (Stufe T), Details (Stufe D) nur beim Öffnen, in der Prüfung und später „Details nachladen“ (nur WLAN, 1/s, 30/Zen-Tag) | Rate-Limit-Risiko bei YouTube | Details für alle Kandidaten | E3, `c99c54387` |
| D46 | 2026-10-13 | **E4:** Videos ohne Kategorie bleiben, Gewicht 1,0 | Unsicherheit darf nicht ausblenden | Ausblenden (Bundle-Verhalten) | E4 |
| D47 | 2026-10-13 | **E6:** Kein Aktuelles-Deckel je Ausgabe, nur Gewicht (0,3 mit Details, 0,6 nur Titel+Kanal nach AP-0-Messung) | Erst Erfahrung sammeln; Aktuelles-F1 auf Stufe T nur 58 % | Max. 3 von 15 | E6, rules.md „Inhaltsgewicht“ |
| D48 | 2026-10-13 | **E7:** ONNX Runtime als Maven-AAR, Telemetrie-Provider entfernt, `setTelemetry(false)` | Geringster Aufwand, am Gerät netzgeprüft | Eigener Minimal-Build; reiner Kotlin-Encoder | E7, rules.md „Externe Anfrage“ |
| D49 | 2026-10-13 | **E8:** Plausibilitätsschwellen 30/60 % als Start, später kalibrieren | Stichprobe fehlt noch | – | E8, rules.md „Prüfung beim Markieren“ |
| D50 | 2026-10-13 | Fällige Ausgabe wird bis 20 s vorbereitet (Einordnung), Erstellzeit = Start der Vorbereitung; ersetzt 3-s-Nachklassifikation | Erste Ausgabe nach Aktualisierung war unklassifiziert | 3-s-Nachklassifikation | `aab05feeb`, rules.md „Auswahlmoment“ |
| D51 | 2026-10-13 | „Kanal übernehmen“: Kanal-Links zeigen ein Blatt mit Einordnung; **Abonnieren sofort, ohne Sperre**; Praxis markieren über den normalen Sperr-Ablauf | Abonnieren ist nicht gesperrt (F8); Stefans Wunsch | Abonnieren als Lockerung | `f90881fdd`, Stefan, rules.md „Kanal übernehmen“ |
| D52 | 2026-10-14 | Korrekturen in der Prüfansicht (Labels) ändern nie Feed, Gewicht oder Sperre; Debug-Export nur lokal (Teilen-Dialog) | Daten für Kalibrierung ohne Nebenwirkung | – | `8a37a5ff5`, Stefan (Trainingsdaten für YoutubeManager), rules.md „Korrektur“/„Export“, Protokoll 14.2 |
| D53 | 2026-10-14 | **Sprachen im Feed** (Stefan): Standard Deutsch + Englisch, andere Sprachen nur im Feed nicht angeboten (nie gelöscht, Abos/Suche/Kanäle unverändert); weniger Sprachen = strenger, Sprache zulassen = Lockerung (24 h); Erkennung lokal ohne Bibliothek, unsicher = bleibt, Kanal-Mehrheit für mittlere Fälle, Sprache aus Tonspur/automatischen Untertiteln hat Vorrang | Fremdsprachige Videos (TEDx-Kanal) füllten die Ausgabe; keine proprietären Abhängigkeiten (kein ML Kit) | ML Kit/CLD3; Ausblenden auch bei unklarer Erkennung; eigene Sprache pro Kanal einstellen | `3bdcfd2a6`, rules.md „Sprachen im Feed“, Protokoll 14.3 |
| D54 | 2026-10-14 | **Kanal-Blatt in einem Dialog** (Stefan): Abonnieren / Als Praxis markieren / Abonnieren und als Praxis markieren direkt im Blatt, Prüfergebnis, Sperrtext und 3-s-Wartezeit inline; Sperrlogik unverändert, Kanalseite/Long-Press behalten den Dialog | Zwei Fenster beim Teilen waren umständlich | Blatt schließt und öffnet den Praxis-Dialog (alter Weg) | `9e8b85059`, rules.md „Kanal übernehmen“, Protokoll 14.4 |
| D55 | 2026-10-14 | Inhalts-Schalter bleiben ab Werk „an“, werden beim Modell-Download aber sichtbar gemacht (Dialog, Hinweis nach Installation, „Noch ohne Wirkung“ ohne Modell) | Kein stilles Einschalten (Release-Test zen.3), ohne den strengeren Standard aufzugeben | Schalter ab Werk aus; automatisches Einschalten ohne Hinweis (bisher) | `3fd090565`, rules.md „Schalter“, Protokoll 14.1 |
| D56 | 2026-10-13 | **Details nachladen** nur für die Stufe-T-Videos einer gerade gebauten Ausgabe, im Hintergrund, nur in ungemessenem Netz (WLAN), 1 Anfrage/s, höchstens 30 Anfragen pro Zen-Tag (inkl. Wiederholungen), Bot-Prüfung/429 beendet den Tag; die Ausgabe selbst ändert sich nie (nur ihre „Warum“-Zeile); kein eigener Schalter | Stufe T ist unsicher (Aktuelles-F1 58 %), Massen-Abrufe wären das Rate-Limit-Risiko (Spec 2.5); 30/Tag = zwei volle Ausgaben | Details für alle Kandidaten (E3 b); „WLAN + Laden“ (Spec-Vorschlag 2.5, „Laden“ entfiel); Ausgabe nach dem Nachladen neu bauen; Mobilfunk (bewusst nie, ob Stefan es will: offen) | `c99c54387`, rules.md „Details nachladen“, Protokoll 13.2/13.4 |
| D57 | 2026-10-13 | **Ton zählt halb, nur als Hook:** Zen-Minuten = Bild + Ton/2 (Musik, Praxis 0) werden gemessen (`ZenWatchTime.Day.zenMinutesMs`), wirken aber nirgends (kein Limit, kein Ton-Deckel) | Messung zuerst, Limit erst mit F2; Ton ist das Escape-Ventil (Konzept §3), ein Ton-Deckel (Empfehlung 180 min) soll Dauerberieselung verhindern; für die Pflichtpause zählt Ton voll | Ton voll; Ton gar nicht (Spec 9.1 b/c) | `b61bb1998`, rules.md „Tageslimit Minuten (F2)“, [spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md) 10.1/10.3; **Annahme, von Stefan zu bestätigen** |
| D58 | 2026-10-14 | **Projektanweisungen in `AGENTS.md`**, `CLAUDE.md` enthält nur `@AGENTS.md` (Import) | Eine Quelle für alle Coding-Agenten, keine doppelt gepflegten Regeln | Inhalt in `CLAUDE.md` und Kopie in `AGENTS.md` | `4e90936a7`, [../AGENTS.md](../AGENTS.md), [README.md](README.md) |

## Absichtsabfrage

| # | Datum | Entscheidung | Grund | Alternativen (verworfen) | Beleg |
|---|---|---|---|---|---|
| D59 | 2026-10-14 | **Entwurf** (Stefan billigt die Richtung, Details offen): Absichtsabfrage lädt ruhig zum Nicht-Schauen ein. Schritt 1: eigene Tageszahlen (Bild/Ton-Minuten, Einstiege), Praxis-Alternative mit einem Tipp, wechselnde Fragen statt festem Satz, „Doch nicht“/Alternative visuell stärker, „Weiter“ dezent, aber ehrlich. Schritt 2: eigene Gegenmittel-Liste (Hinzufügen ohne Sperre), behutsamerer Ton je nach Kontext. Abschwächen/Abschalten = Lockerung (24 h). Keine Streaks, Punkte, Belohnungen, Druck-Zähler, Beschämung | Dialog verlangte eine Antwort ohne Eingabefeld und wirkte unklar; fester Satz nutzt sich ab; Alternative statt Verbot vermeidet Reaktanz | Freitextfeld (D14); längere Wartezeit; Streaks/Belohnungen | [spec-absichtsabfrage-motivation.md](spec-absichtsabfrage-motivation.md) (offene Fragen Abschnitt 11); rules.md unverändert bis zur Umsetzung |

## Bewusst nicht (noch) gebaut

Entdecken verwandter Kanäle (Spec, opt-in, 3/Woche, [spec-verwandte-kanaele.md](spec-verwandte-kanaele.md)), Kategorie Bildung und
Tageslimit/Ton-Deckel (Empfehlungen in [spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md) §9, nicht
entschieden), Nachtruhe, Abo-Hygiene, Graustufen – siehe rules.md (Status „Spec“/„offen“) und das Ideen-Backlog in
[archive/feature-audit.md](archive/feature-audit.md).
