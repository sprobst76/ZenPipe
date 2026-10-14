# Umsetzungsprotokoll: Lokaler Inhalts-Klassifikator

**Worum geht es:** Was vom lokalen Inhalts-Klassifikator gebaut wurde: Commits, Bausteine, Messwerte am Gerät, Abweichungen von
der Spec und offene Punkte, je Etappe (Abschnitte 10–14, 2026-10-13 bis 2026-10-14).
**Wann nachlesen:** Wenn du am Klassifikator-Code arbeitest oder wissen willst, warum eine Regel von der Spec abweicht.
Vorschlag, Begründung und Entscheidungen E1–E8: [spec-klassifikator-integration.md](spec-klassifikator-integration.md)
(Abschnitte 0–9); geltende Werte: [rules.md](rules.md) „Inhalts-Erkennung (lokal)“ (bei Widerspruch gilt rules.md).

Bis 2026-10-14 standen diese Abschnitte in der Spec; die Nummern 10–14 sind unverändert, damit ältere Verweise („Spec 14.3“,
„Abschnitt 12.2“, Code-Kommentare) weiter passen. Verweise auf Abschnitte 0–9 (z. B. „Spec 2.5“, „4.7“, „6.3“) und auf E1–E8
meinen die Spec. Ein Protokoll: frühere Abschnitte werden nicht umgeschrieben, spätere Abschnitte nennen, was sie ersetzen.

## 10. Umsetzung AP 1–3 (2026-10-13)

Commits auf `zen`: `zen: classifier core (tokenizer, text builder, heads, rules)`, `zen: onnx runtime and opt-in model
download`, `zen: ci emulator for API 23 on x86_64`, `zen: classification cache and worker`. Code in
`app/src/main/java/org/schabi/newpipe/zen/classify/`. Noch **keine Wirkung** auf Feed, Markieren oder Bildung (AP 4–7).

### 10.1 Bausteine
- **Kern (AP 1):** `UnigramTokenizer`, `TextBuilder` (Kategorie-ID → deutscher Name als Konstante, englischer
  Extraktor-Name → ID, YouTube-HTML → Text per jsoup), `Heads`, `ZenRules` (Regelstand v5), `ZenPipeline` mit
  `Encoder`-Schnittstelle, `ZenBase64` (eigene Implementierung, API 23 + JVM-Tests), Code-Point-Schleifen statt
  `String.codePoints()`.
- **ORT + Download (AP 2):** `onnxruntime-android` 1.31.0 nur von Maven Central (`exclusiveContent` in
  `settings.gradle.kts`), `tools:overrideLibrary`, Telemetrie-Provider per `tools:node="remove"` entfernt +
  `setTelemetry(false)` vor jeder Session (`OrtEncoder`), `abiFilters` arm64-v8a (Debug + x86_64),
  `useLegacyPackaging`, `-keep class ai.onnxruntime.** { *; }`. Modell = **ein Zip** (`encoder_int8.onnx`, `tokenizer.bin`,
  `vocab_map.json`, `heads.json`, `NOTICE`), gebaut mit `zen-docs/tools/make_classifier_bundle.py` (deterministisch,
  gibt SHA-256/Größe aus). `ZenModelInstaller`: SHA-256 beim Schreiben, nur bekannte flache Dateinamen (kein Zip-Slip),
  Austausch erst nach vollständigem Entpacken, Ablage `noBackupFilesDir/zen_classifier/model/`, Marker
  `installed.properties`. `ZenClassifierModel`: URL/SHA-256 als Platzhalter (**TODO AP 0**, Download bis dahin
  ausgegraut, Status „noch nicht veröffentlicht“); Debug-Builds können eine lokale Testquelle nutzen (`cls_debug_url`,
  `cls_debug_sha` in `zen_state`, Klartext nur zu 127.0.0.1/localhost über `src/debug`-Netzwerkkonfiguration).
  Einstellungen > Zen > „Inhalts-Erkennung (lokal)“: Status, Herunterladen (Bestätigung, Fortschritt, Abbrechen),
  Löschen, Lizenzen (eigene Hinweise + ORT-ThirdPartyNotices vollständig, `res/raw/`).
- **Cache + Worker (AP 3):** `ZenClassCache` (eigene SQLite-Datei `noBackupFilesDir/zen_class_cache.db`, Schlüssel
  `serviceId:url`, Kategorien, Ton, Top-Themen/-Formate, Stufe T/D, Modellversion, 30 Tage nach letztem Zugriff;
  neu berechnet bei Modellwechsel oder wenn statt T jetzt Details vorliegen), `ZenClassifier` (ein Hintergrund-Thread,
  lazy, 60 s Leerlauf → schließen, 4 Threads / 2 während eigener Wiedergabe, Fehler → Session zu, `class_error`, Status
  „defekt“), `ZenClassifyWorker` (WorkManager, unique KEEP, Akku nicht niedrig; nach Feed-Aktualisierung, App-Start mit
  installiertem Modell, nach Installation; nur Titel+Kanal, Batches à 50, jedes Ergebnis sofort gespeichert,
  `class_run`), Stufe D aus `ZenPause.onStreamInfo` und `VideoDetailFragment.handleResult` (5 s verzögert, nie während
  des Player-Starts, keine Zusatzanfrage). Prüfansicht „Eingeordnete Videos“ (letzte 100, Kategorien · Ton · Gewicht ·
  Stufe + „warum“) in allen Builds; Selbsttest nur in Debug-Builds.

### 10.2 Messwerte
| Messung | Wert |
|---|---|
| Unit-Tests | gesamt 307 grün; neu: `ZenRulesTest` (200 Regel-Goldfälle + Feed-Deckel), `ZenTokenizerTest` (synthetischer Tokenizer, erfundene Texte mit IDs aus der Python-Referenz, Textbau, Kategorie-Tabelle, HTML, Base64), `ZenModelInstallerTest` (8), `ZenClassCacheTest` (6), `ZenGoldenTest` |
| `golden.json` lokal (`ZEN_GOLDEN_DIR`, Kopf + Regeln auf Referenz-Embedding, 256 Tokens) | Token-IDs 40/40, Kategorien 40/40, Ton 40/40 |
| Rundung | Bundle-Kotlin rundete anders als Python `round` (Goldfall 13: 0,889 statt 0,888) → `BigDecimal` HALF_EVEN; betrifft nur den gespeicherten Wert |
| APK Release (unsigniert, arm64) | **19,9 MB** (19.886.876 B; vorher 6,8 MB); `libonnxruntime.so` 34,2 MB → 12,9 MB komprimiert |
| APK Debug (arm64 + x86_64) | 60,2 MB |
| Modell-Zip | 35,4 MB (35.432.218 B), installiert 42,6 MB |
| Download am Defy (lokale Testquelle, `adb reverse`) | ~2 s; falsche SHA-256 → abgelehnt, nichts installiert (`class_model_failed`); richtige → `class_model_installed` |
| Laden (Defy, Debug) | Tokenizer 391–460 ms, Karte + Köpfe 138–166 ms, Encoder 653–705 ms (≈ 1,2–1,3 s) |
| Zeit pro Video (4 Threads, ≤ 128 Tokens) | Titel + Kanal (17–26 Tokens) **110–125 ms**; mit Beschreibung (59–79 Tokens) 187–213 ms; Median 123 ms |
| Selbsttest gegen Python-Referenz (ORT 1.31 PC, 10 erfundene Videos) | Texte 10/10, Tokenzahl 10/10, Kategorien 10/10, Ton 10/10, Kosinus ≥ 0,99990 |
| Speicher (`dumpsys meminfo`) | PSS 176 → 285 MB mit Session (+109 MB; Native Heap +55 MB, Java Heap +26 MB); nach 60 s Leerlauf geschlossen: Native Heap wieder 40 MB |
| Wiedergabe | Klassifikation während Hintergrund-Wiedergabe lädt mit 2 Threads (Log `threads 2`) |
| Telemetrie/Netz | Provider fehlt im gemergten Manifest (Debug + Release) und in `dumpsys package`; kein `ORTTelemetry`-Log; `/proc/net/tcp*` der App-UID: keine neue Verbindung während Laden + Rechnen; `libonnxruntime` nach Kaltstart nicht geladen (`/proc/<pid>/maps`) |
| Kaltstart Debug (`am start -W`) | 3,9–4,2 s, ORT wird beim Start nicht geladen |
| Defekt-Fall | Encoder abgeschnitten → `ORT_INVALID_PROTOBUF`, `class_error`, Status „Defekt – bitte neu herunterladen“, App läuft weiter; „Neu herunterladen“ repariert |

### 10.3 Abweichungen von der Spec
1. Modell als **ein** Zip-Asset statt Einzeldateien; `vocab_map.json` bleibt JSON (eigenes Base64), `yt_categories.json`
   ist Code-Konstante.
2. ORT nur aus Maven Central (Gradle bekam von `dl.google.com` per HTTP/1.1 Timeouts; zugleich Lieferketten-Schutz).
3. CI-Emulator API 23 auf x86_64 (Debug-APK hat kein 32-bit-x86 mehr).
4. Cache mit `SQLiteOpenHelper` statt Room (keine Migrationen, eine Tabelle).
5. Prüfansicht in **allen** Builds (Stichprobe 6.3 läuft mit Stefans echten Abos), Selbsttest nur Debug.
6. Löschen des Modells war bis AP 3 frei; seit AP 4 nur, wenn beide Inhalts-Schalter aus sind (Abschnitt 11).
7. Thread-Wechsel 4 ↔ 2 lädt den Encoder neu (~0,7 s); geöffnete Videos werden 5 s verzögert eingeordnet.
8. AboutLibraries-Export nicht neu erzeugt (bringt große fremde Upstream-Änderungen); Lizenzen stattdessen unter
   Einstellungen > Zen > Lizenzen. Fastlane/F-Droid-Beschreibung erst, wenn das Modell veröffentlicht ist.

### 10.4 Offen
- **AP 0:** Release-Asset `model-e5s-1` hochladen, `DOWNLOAD_SHA256`/`DOWNLOAD_BYTES` in `ZenClassifierModel` setzen
  (Skript gibt beides aus), ggf. Kurztext-Köpfe; Satz in Fastlane-Beschreibung.
- Worker-Erstlauf mit echtem Feed am Gerät (Dauer, `batterystats`, Temperatur): in der Debug-App gab es 0 Kandidaten
  (nur Test-Abo, dessen neues Video angeschaut ist). Zeit pro Video ist aus dem Selbsttest bekannt.
- Release-Build am Gerät (Keep-Regeln nur statisch geprüft: ORT-Klassen im Dex vorhanden), Ruckeln bei Wiedergabe mit
  2 Threads (nur Log), Kategorie-Namen des Extraktors in anderer Inhaltssprache, HTML-Beschreibung gegen Server-Text.

## 11. Umsetzung AP 4–6 (2026-10-13)

Commits auf `zen`: `zen: synthetic golden test for the classifier`, `zen: feed weighting by content`, `zen: plausibility check
when marking practice`, `zen: classifier exceptions`, `zen: classifier exception from the subscriptions long press`.
Regeln: `rules.md` „Inhalts-Erkennung“.

### 11.1 Bausteine
- **AP 4 Feed:** `Candidate` + `zenCats`, `tone`, `classLevel`, `classExempt`; `ZenFeedPlanner.ContentRules` (beide Schalter,
  nur mit funktionierendem Modell, sonst `OFF` = altes Verhalten); `contentWeight`, `weightedScore`, Belastend-Regel als
  Platz-für-Platz-Füllung in `select` (ohne Regel identisch zur alten Schleife, Regressions-Test); `whyOf` → `Pick.why`
  (in `feed_edition_picks` als 4. Feld gespeichert); `ZenFeedProvider.classified` liest den Cache und klassifiziert Fehlendes
  höchstens 3 s (`ZenClassifier.classifyWithin`); Zeile in `StreamItem` über `ZenClassWhy.feedLine`. Zen-Einstellungen
  `class_feed`/`class_heavy` (bool, Default an, aus = Lockerung), nur wo der Klassifikator unterstützt ist sichtbar; Modell
  löschen nur mit beiden aus.
- **AP 5 Prüfung:** `ZenPlausibility` (reine Logik, Schwellen 30/60/20 %, „kein klares Bild“ ab 8/15), `ZenPlausibilityCheck`
  (Fortschrittsdialog „prüfe 7 von 15 …“ mit Überspringen/Abbrechen, Liste, gedrosselte `StreamInfo`-Abrufe 1/s,
  Cache-Wiederverwendung, Warn-Dialog mit Spec-Texten, Videoliste mit Kategorien/Ton/Stufe/Top-Themen), Einstieg in
  `ZenPraxisUi.onMenu` (auch Long-Press), danach unverändert `confirmAdd` mit einer Zeile zum Ergebnis.
- **AP 6 Ausnahmen:** `ZenClassExempt` (Kanal/Schlagwort), `ZenClassExemptions` (Abgleich Kanal-Schlüssel wie Praxis,
  Schlagwort als Teilstring im Titel), `ZenSettingsLock.addExempt/removeExempt` (Mechanik wie Praxis inkl. Undo, Abbrechen,
  Ausstehendes in der Statusbox), UI in Einstellungen > Zen > Inhalts-Erkennung und Long-Press auf Kanal-Einträge/Abos.

### 11.2 Abweichungen von der Spec
1. **Aktuelles auf Stufe T: Gewicht 0,6 statt 0,3** (AP-0-Messung, Aktuelles-F1 nur 58 % ohne Beschreibung); Stufe D 0,3.
   „Warum“-Zeile zeigt „unsicher (nur Titel)“.
2. Ohne Kategorie gilt Gewicht 1,0 **auch bei belastendem Ton** (E4); die Belastend-Platzregel gilt aber für jeden
   eingeordneten belastenden Ton, auch ohne Kategorie.
3. Unter 10 Plätzen pro Ausgabe gibt es keinen Platz für Belastendes (10 %-Regel); Ausgaben können durch die Regel kürzer
   als das Budget werden (übersprungene warten).
4. Prüfung: Ein Praxis-Video, das zugleich wie Unterhaltung aussieht, zählt nicht als Warnsignal; „kein klares Bild“ hat
   Vorrang vor der Warnung (Spec-Tabelle). Bildung-Zeilen der Tabelle sind nicht gebaut (AP 7).
5. Ausnahmen: zusätzlich zu Kanälen auch **Schlagwörter im Titel** (Auftrag); Playlists nicht (Feed kennt nur Kanäle).
   Bildungsprüfung-Ausnahme folgt mit AP 7. Gesperrt: Hinzufügen-Knöpfe ausgegraut.
6. `golden_synthetic.json` (52 erfundene Fälle) liegt als Test-Ressource im Repo (`ZenSyntheticGoldenTest`: Textbau,
   Clickbait, Regeln, Gewicht, Ton immer; Tokenizer/Köpfe mit `ZEN_MODEL_DIR`).

### 11.3 Messwerte (Defy, Debug, mit vorübergehend abonnierten öffentlichen Kanälen)
| Messung | Wert |
|---|---|
| Unit-Tests | 326 (1 übersprungen ohne `ZEN_GOLDEN_DIR`); neu u. a. 7 Feed-, 4 Sperr-, 6 Prüf-Tests, synthetische Goldfälle 52/52 |
| Worker-Erstlauf | 92 Kandidaten (Titel+Kanal) in 15,2 s, Ø 134 ms/Video, Laden 1,7 s; Akku: Gerät am USB bei 100 %, 25 °C → Verbrauch nicht messbar |
| Erste Ausgabe direkt nach Feed-Aktualisierung | Worker lief parallel: 3-s-Budget schaffte 4 von 92 (Modell-Laden 1,7 s), alle 14 Picks „noch nicht eingeordnet“ |
| Ausgabe mit vollem Cache | 12 Videos, 0 nicht eingeordnet, 1 belastendes auf Platz 12; Nachrichten (tagesschau/phoenix/ZDFheute, ~70 % als belastend eingeordnet) fast alle hinten/übrig |
| Prüfung Yoga With Adriene | 15/15 Praxis → passt (beim ersten Lauf 8 von 14 Detailabrufen mit `RuntimeException`, danach Titel+Kanal; spätere Läufe ohne Fehler) |
| Prüfung tagesschau | 0/15, 15 Aktuelles (alle belastend) → deutliche Warnung; „Trotzdem markieren“ → `praxis_add_pending` |
| Prüfung Kurzgesagt (DE) | 0/15 → deutliche Warnung (12 Bildung) |
| Prüfung Gronkh (Let's Plays) | 11/15 ohne Kategorie → nur „kein klares Bild“ |
| Prüfung Playlist One-Minute Meditations | 8 Videos (ohne Shorts), 5 Praxis → passt |
| Dauer der Prüfung | 15 Videos 21–30 s (14–15 Detailabrufe, Drosselung 1/s), Playlist 8 Videos 15 s |

Auffällige Fehlurteile: Gronkh „PALWORLD … Forsche Forschungen …“ = Bildung 0,84; tagesschau „Die Toten Hosen: Letztes Konzert“
und „Wirtschaftsnobelpreis …“ = Aktuelles + **belastend**; fast alle Nachrichten belastend (Ton-Precision laut AP 0 nur 50–73 %);
MrWissen2go „KI außer Kontrolle?“ ohne Kategorie; Talk-Formate (phoenix persönlich, 11KM-Podcast „Pferde klonen“) = Bildung.

### 11.4 Offen
- Schwellen (E8) und „kein klares Bild“-Vorrang mit Stefans Stichprobe (6.3) kalibrieren: Gronkh bekäme heute nur einen Hinweis.
- Belastend-Ton bei Nachrichten wirkt zu häufig: mit der Regel landen Nachrichtensendungen praktisch nie in der Ausgabe.
- Erste Ausgabe nach einer Feed-Aktualisierung ist meist unklassifiziert (Worker und Ausgabe starten gleichzeitig).
- Ursache der `RuntimeException` beim ersten Prüflauf (8 von 14 Detailabrufen) unklar; Ursachen-Logging ist jetzt drin.
- AP 7 (Bildung ohne Politik), AP 0-Asset/SHA in `ZenClassifierModel`, Akku-Messung ohne USB.

## 12. Umsetzung „Kanal übernehmen“ und vorbereitete Ausgabe (2026-10-13)

Commits auf `zen`: `zen: prepare a due feed edition before building it`, `zen: retry and report failed detail requests in the
practice check`, `zen: take over a shared channel link`. Regeln: `rules.md` „Inhalts-Erkennung“ (Zeilen „Auswahlmoment“,
„Prüfung beim Markieren“, „Kanal übernehmen“) und „Externe Links“.

### 12.1 Kanal übernehmen (Wunsch Stefan)
- **Einstieg:** `RouterActivity.onSuccess` – ist der Link-Typ `CHANNEL` (`ZenChannelImport.handles`), erscheint statt der
  Öffnen-Auswahl der Dialog `ZenChannelImportDialog` (DialogFragment; der FragmentManager stellt ihn nach dem Drehen wieder
  her, `RouterActivity` beendet sich wie bei Playlist-/Download-Dialogen, wenn er geschlossen wird). Erkennung = die des
  Extraktors (`@name`, `@name/videos`, `/channel/`, `/c/`, `/user/`, `m.`, Link in Text); Video-, Playlist-, Shorts-Links
  gehen den alten Weg. Links innerhalb der App, Suche, Abos und Kanallisten sind unverändert.
- **Laden:** `ZenChannelImportModel` (ViewModel, übersteht Drehen): `ChannelInfo` (Name, Avatar, kanonische URL), Abo-Status
  (`SubscriptionDAO.getSubscription`), dann `ZenPlausibilityCheck.sample` (neue öffentliche Stichproben-Funktion der
  Prüfung aus AP 5: neueste 15 ohne Shorts/Live, Details 1/s, Cache Stufe D, Wiederholungen, Stopp-Flag). Zurück/Schließen
  setzt das Stopp-Flag; die laufende Anfrage endet noch, weitere folgen nicht.
- **Auswertung:** `ZenChannelProfile` (reine Logik): Gruppen Praxis (praxis ∪ ruhe), Bildung, Aktuelles, Unterhaltung,
  Körper, Psyche; „Überwiegend X (k von n)“ ab 50 %, sonst „Gemischt: …“ (drei größte), „kein klares Bild“ ab 8/15 ohne
  Kategorie (wie die Prüfung); Aktuelles- und Belastend-Anteil; „wird im Feed niedrig gewichtet“, wenn ≥ 50 % ein
  Feed-Gewicht < 0,75 hätten (`ZenFeedPlanner.contentWeight`, nur mit `class_feed` an, nicht bei Ausnahme-Kanälen); bis zu
  4 Beispiele (je Gruppe eins, dann ohne Kategorie, Reihenfolge des Kanals). Hinweis auf Unsicherheit immer, dazu nicht
  ladbare Videos.
- **Aktionen:** Abonnieren sofort (`SubscriptionManager.insertSubscription`, wie die Kanalseite, ohne Sperre); Als Praxis
  markieren = `ZenPraxisUi.onMenu` mit der fertigen Stichprobe (`ZenPlausibilityCheck.run(…, presampled)`, keine zweite
  Prüfung) bzw. stoppt eine laufende Einordnung, wenn der Ablauf selbst prüfen wird (gesperrt: nur Ablehnung, Einordnung
  läuft weiter); Nur ansehen = `NavigationHelper.getIntentByLink` → Kanalseite in `MainActivity` (Absichtsabfrage wie bisher).
- **Protokoll:** `channel_import action=open|subscribe|praxis|view|close|load_failed url=…` (close mit `last=`),
  `channel_import_check url=… n= kind= main= news= heavy= none= lowFeed= requests= retries= failed= errors=` bzw.
  `result=no_model|failed`.

### 12.2 Vorbereitete Ausgabe (Fehler „erste Ausgabe unklassifiziert“)
- Ursache (Abschnitt 11.3): Worker und Ausgabe starteten gleichzeitig, das 3-s-Budget schaffte 4 von 92.
- Neu: `ZenFeedProvider.compute` fragt bei fälliger Ausgabe (`ZenFeedState.isDue`, ohne Seiteneffekt) und aktiver
  Inhalts-Erkennung `ZenEditionPrep.decide`: fehlen Einordnungen geeigneter Kandidaten, startet ein Hintergrund-Thread, der sie
  nach Grund-Score absteigend über `ZenClassifier.classifyWithin` einordnet, bis alle fertig sind, ein Fehler auftritt oder
  `LIMIT_MS` = 20 s vergangen sind. Bis dahin wird `view(…, allowCreate = false)` gerechnet (laufende Ausgabe unverändert)
  und die Endkarte zeigt „Die nächste Ausgabe wird vorbereitet … (n von m)“ (Fortschritt alle 10 Videos, `FeedViewModel`
  hört auf `ZenEditionPrep`). Danach wird die Ausgabe einmal gebaut; Erstellzeit = Start der Vorbereitung
  (`view(createdAt = …)`, nur am selben Zen-Tag), damit der 60-min-Takt nicht wandert. Eine fertige Vorbereitung, die über
  60 s niemand abholt, verfällt (neue Daten → neu vorbereiten). Ohne fehlende Einträge: sofort bauen (wie bisher).
- Der Worker läuft weiter parallel; doppelte Arbeit entfällt, weil `classifyNow` vorhandene Einträge überspringt.

### 12.3 Gescheiterte Detailabrufe in der Prüfung
- Ursache am Gerät gefunden: die 8 von 14 `RuntimeException` beim Yoga-Kanal sind **`PaidContentException`** („This video is
  only available for members of the channel“) – Mitglieder-Videos in den neuesten 15, kein Netzfehler. RxJava verpackt
  geprüfte Ausnahmen in `RuntimeException`, daher sah das alte Log nur diese.
- Neu: `ZenPlausibilityCheck.failKind` (Ursachenkette): `ContentNotAvailableException`-Familie (Mitglieder, privat, Alter,
  Region …) = dauerhaft, keine Wiederholung; `ReCaptchaException`, `SignInConfirmNotBotException`, HTTP 429 = blockiert,
  keine weiteren Detailabrufe in dieser Prüfung; sonst vorübergehend, 2 Wiederholungen nach 2 s / 5 s (gedrosselt wie
  alle Abrufe). Ergebnis-Hinweis in **jedem** Ergebnis (auch „passt“ / „kein klares Bild“): „8 von 15 Videos nur für
  Kanalmitglieder – nur nach Titel und Kanal beurteilt“ bzw. „n von 15 Videos nicht ladbar …“; Videoliste markiert sie.
  Log je Fehler mit Art, Versuch und Ursachenkette; `class_check … requests= retries= failed= errors=PaidContentException:8`.

### 12.4 Messwerte (Defy, Debug)
| Messung | Wert |
|---|---|
| Unit-Tests | 344 (3 übersprungen ohne lokale Modelldaten); neu `ZenChannelImportTest` (3: 8 Kanal-Formen, 9 Nicht-Kanal-Links), `ZenChannelProfileTest` (6), `ZenEditionPrepTest` (8, inkl. `isDue`/Erstellzeit), `ZenPlausibilityTest` +1 (Fehlerarten) |
| Blatt Yoga With Adriene (geteilt `@yogawithadriene?si=…`) | „Überwiegend Praxis (15 von 15)“, 15 Abrufe in 22,3 s, 8 × `PaidContentException` → Hinweis Kanalmitglieder; zweites Öffnen 8 Abrufe (nur die T-Einträge), 7,3 s |
| Blatt tagesschau | „Überwiegend Aktuelles (15 von 15)“, belastend 15/15, Hinweis „niedrig gewichtet“, 15 Abrufe 26,2 s; erneut geöffnet (bereits abonniert): 0 Abrufe, 21 ms, kein Abo-Knopf |
| Blatt Kurzgesagt DE | „Überwiegend Bildung (12 von 15)“, belastend 2/15, 29,2 s |
| Drehen während der Prüfung | Stand bleibt (9 von 15 → weiter), eine einzige Prüfung im Log |
| Zurück während der Prüfung | Abbruch nach der laufenden Anfrage (4 Videos / 5 Anfragen) |
| Vorbereitete Ausgabe nach Feed-Aktualisierung | `class_feed_prep missing=56 done=56 ms=9719 reason=complete`, Ausgabe `unclassified=0`, Erstellzeit 10,0 s vor dem Bau (= Start der Vorbereitung) |
| Vorbereitete Ausgabe beim App-Start (Worker parallel) | Karte „Preparing … (20 of 48)“, „(41 of 48)“, dann `missing=48 done=48 ms=7386`, `unclassified=0` |

### 12.5 Abweichungen
1. Dialog statt BottomSheet (Material-BottomSheet braucht ein MaterialComponents-Theme, ZenPipe nutzt AppCompat).
2. Für Kanal-Links entfällt die Öffnen-Auswahl samt gespeicherter Öffnen-Aktion (z. B. „Video-Player“ spielte bisher den
   ganzen Kanal); „Nur ansehen“ ersetzt „Info anzeigen“.
3. Mitglieder-Videos bleiben in der Stichprobe (mit Titel+Kanal), statt sie zu überspringen: sie zeigen, was der Kanal macht.
4. Abonnieren aus dem Blatt lädt den Feed nicht sofort nach (wie der Abo-Knopf der Kanalseite).
5. Die 3-s-Nachklassifikation beim Ausgabe-Bau ist entfallen (ersetzt durch die Vorbereitung); `class_feed_late` gibt es nicht mehr.

### 12.6 Offen
- Ton „belastend“ zu häufig (tagesschau 15/15; mit der Belastend-Regel hatte die vorbereitete Ausgabe nur 7 Videos,
  65 warten) – nur dokumentiert, Schwellen/Ton unverändert (nicht Teil dieses Auftrags).
- Bei kaltem Prozess erscheint der Router (und damit das Blatt) in der Systemsprache, nicht in der gewählten App-Sprache
  (Upstream: die Sprache wird erst in `MainActivity` gesetzt).
- Dialoge des Praxis-Ablaufs (Prüfung/Bestätigung) überstehen das Drehen nicht (wie auf der Kanalseite, bestehendes Verhalten).

## 13. Umsetzung „stärkstes Thema“ und Details nachladen (2026-10-13)

Anlass: Stefans Feed (Tech/KI-Abos) zeigte viele „ohne Kategorie · unsicher (nur Titel)“, Two Minute Papers einmal
„Bildung 0,89“, einmal „ohne Kategorie · belastend“ („Cracked The Code Of Life“, Fehlurteil am Ton). Commits auf `zen`:
`zen: name the strongest topic for videos without a category`, `zen: load details for the picks of a new feed edition`,
`zen: let the feed "why" line wrap`. Regeln: `rules.md` „Inhalts-Erkennung“ (Zeilen „Warum“-Zeile, „Details nachladen“,
Cache, Prüfansicht).

### 13.1 Stärkstes Thema in der „Warum“-Zeile
- Der Cache speicherte schon „Top-Themen/-Formate“ (`ClassEntry.top`, 3 stärkste gemischt). `Analysis.top` hängt jetzt das
  stärkste Thema an, wenn nur Formate unter den ersten 3 sind; **kein Schemawechsel**, alte Einträge bleiben gültig (ohne
  Thema nur „ohne Kategorie“). Thema vs. Format: `ZenClassWhy.FORMAT_KEYS` (10 Formatschlüssel der Taxonomie v5).
- Feed: `Candidate.classTheme` (nur Anzeige) → `whyOf` hängt bei leerer Kategorie `|thema=p` als 5. Feld an,
  `ZenFeedPlanner.parseWhy` liest 4 und 5 Felder. Text: „ohne Kategorie (stärkstes Thema: KI 0,42, unter Schwelle)“,
  „unter Schwelle“ nur bei p < 0,6 (`MIN_P_SMALL`): Kategorien entstehen aus Thema **und** Format, ein starkes Thema ohne
  Kategorie (z. B. „KI 0,93“ als News-Format) ist also kein Schwellenfall. Gewicht/Auswahl unverändert (Unit-Test).
- Namen: 18 Hauptthemen mit DE/EN-Kurznamen (KI, Technik, Programmieren, Wissenschaft, Politik, …), sonst Rohschlüssel
  (z. B. `office_pkm_software`). Die Zeile darf bis 3 Zeilen umbrechen (vorher fest 2, am Defy abgeschnitten).
- Prüfansicht „Eingeordnete Videos“ und die Videoliste der Prüfung nutzen dieselbe Zusammenfassung.

### 13.2 Details nachladen für die Ausgabe
- `ZenFeedProvider.compute` → `ZenFeedState.view` meldet `created`; dann `refineDetails`: Picks mit Stufe T (aus dem
  gespeicherten „Warum“) werden zu Jobs; `ZenFeedDetails.shared.start` (ein Lauf zur Zeit, eigener Thread) prüft
  Inhalts-Erkennung an + Modell, Zen-Tages-Budget (`cfd_day`/`cfd_requests`, 30), „heute blockiert“ (`cfd_block_day`),
  ungemessenes Netz; dann `process`: Cache-D wiederverwenden, sonst `ZenDetailRequests.fetch` (gemeinsam mit der Prüfung
  beim Markieren herausgelöst: Drosselung 1/s, Fehlerarten, Wiederholungen) und `ZenClassifier.classifyBlocking` (Stufe D).
  Neue „Warum“-Werte → `ZenFeedState.updateWhy(createdAt, …)` (nur die gleiche Ausgabe, nur das `why`-Feld), Listener →
  `FeedViewModel.zenRefresh`; `StreamItem.hasSameContentAs` vergleicht `zenPick`, damit die Liste neu zeichnet.
- Dauerhaft fehlgeschlagene Videos (Mitglieder, privat …) werden in diesem Prozess nicht erneut angefragt.
- Log: `class_feed_detail run=7 ok=7 cached=0 failed=0 requests=7 retries=0 ms=13555 reason=complete`.

### 13.3 Messwerte (Defy, Debug, vorübergehend abonniert: Two Minute Papers, AI Explained, Matt Wolfe, c't 3003, NetworkChuck)
| Messung | Wert |
|---|---|
| Unit-Tests | 363 (3 übersprungen ohne lokale Modelldaten); neu `ZenFeedDetailsTest` (5: Drosselung, Cache, Mitglieder/Netzfehler, Bot-Prüfung, Budget/Aus), `ZenFeedTest` +2 (Thema nur ohne Kategorie, Reihenfolge gleich, alte 4-Feld-Werte; `updateWhy` nur gleiche Ausgabe), `ZenClassCacheTest` +2 (stärkstes Thema, `top`) |
| Ausgabe mit 7 Videos (Cache leer, Worker 9 Videos in 2,7 s) | vorher alle „unsicher (nur Titel)“: Bildung 0,84 / ohne Kategorie (Wissenschaft 0,13, unter Schwelle) / Bildung 0,60 / ohne Kategorie (office_pkm_software 0,16) / Bildung 0,88 / ohne Kategorie (Linux & Maker 0,40) / Bildung 0,70 |
| Nachladen | `run=7 ok=7 requests=7 ms=12087` bzw. `13555`, ≈ 14 s nach dem Bau neu gezeichnet: Bildung 0,60 / ohne Kategorie (KI 0,42, unter Schwelle) / Bildung 0,86 / ohne Kategorie (Politik 0,09, unter Schwelle) / Bildung 0,90 / ohne Kategorie (Linux & Maker 0,58, unter Schwelle) / ohne Kategorie (KI 0,65), alle „mit Beschreibung“; **Reihenfolge und Auswahl identisch** (UI-Dump vorher/nachher) |
| Stefans Fall | „DeepMind's New AI Just Cracked The Code Of Life“ (Two Minute Papers, nicht in der Ausgabe, daher Stufe T) in der Prüfansicht: „ohne Kategorie (stärkstes Thema: KI 0,93) · belastend · unsicher (nur Titel)“, Rohwerte `ki 0.93, news_aktuell 0.74, ki_gesellschaft_ethik 0.69`: das Thema ist klar, die Kategorie scheitert am News-Format und der Ton am Titel |

### 13.4 Abweichungen
1. Netzbedingung „WLAN“ = Android „ungemessen“; „Laden“ aus 2.5 ist keine Bedingung (höchstens 15 Anfragen je Ausgabe,
   der Nutzer hat den Feed ohnehin offen). Budget 30/Tag = zwei volle Ausgaben.
2. Nur Videos der **neuen** Ausgabe mit Stufe T; „noch nicht eingeordnet“ und Ausnahmen werden nicht nachgeladen; spätere
   Ausgaben erhalten Picks, die schon D sind, ohne Anfrage.
3. Die „Warum“-Zeile zeigt nach dem Nachladen auch das neue Gewicht, obwohl die Reihenfolge mit dem alten berechnet wurde
   (die Zeile beschreibt die Einordnung, nicht den Platz).

### 13.5 Offen
- Ton „belastend“ am Titel (Stefans Fall) bleibt ein Modellthema; Stufe D hilft nur für Videos der Ausgabe.
- Kurznamen nur für 18 Themen; Format-Namen werden in der Prüfansicht roh gezeigt. [erledigt: alle 37 Themen, 10 Formate,
  3 Töne, `1a6a30a43`, Abschnitt 14]
- Nachladen im Mobilfunknetz bewusst nie; ob Stefan es dort will, ist offen.

## 14. Umsetzung Release-Test zen.3: Korrekturen, Export, Sprachfilter, Praxis im Kanal-Blatt (2026-10-14)

Anlass: Release-Test von 0.1.0-zen.3 auf dem Defy und Wünsche Stefans (Trainingsdaten für YoutubeManager, Sprachen außerhalb
Deutsch/Englisch ausblenden, ein Dialog statt zwei beim Teilen eines Kanals). Commits auf `zen`: `zen: turn off the upstream
announcement dialogs`, `zen: margins and text size of the empty practice page`, `zen: short names for all classifier topics,
formats and tones`, `zen: reflow the license notices for the phone screen`, `zen: show what the content switches do around the
model download`, `zen: corrections and debug export in the classification review`, `zen: feed language filter`, `zen: choose
subscribe or practice in one channel import sheet`. Regeln: `rules.md` „Funktionen“, „Feed“ (Sprachen im Feed),
„Zen-Einstellungen“ (`feed_langs`), „Inhalts-Erkennung“ (Schalter, Prüfansicht, Korrektur, Export, Cache, Kanal übernehmen),
„Absichtsabfrage“ (Kanal-Link).

### 14.1 Fehler aus dem Release-Test
- **„Keep Android Open“** (Upstream, alle 30 Tage in Release-Builds, nennt NewPipe, Links keepandroidopen.org/woheller69) und
  der Android-5-Hinweis (newpipe.net) laufen über `ZenFeatures.upstreamAnnouncementsEnabled()` = aus. Der leere Tab hieß fest
  „NewPipe“, jetzt App-Name. Weitere Fundstellen mit „NewPipe“/externen Links, bewusst **nicht** geändert: Über/Lizenz und
  „Support NewPipe“/„NewPipe website“ (Herkunft), `import_settings_vulnerable_format` (nennt NewPipe 0.27.0 als Formatversion),
  Fehlerhinweis „IP-Sperre“ mit Link auf newpipe.net/FAQ (`ErrorInfo.YOUTUBE_IP_BAN_FAQ_URL`, hilfreicher Inhalt), Dateiname des
  Daten-Exports `NewPipeData-*.zip`, Update-Prüfung (ohnehin aus).
- Praxis-Leerseite: Text 16 sp mit 24 dp Seitenrand statt 24 sp ohne Rand (Kiosk-Layout, nur im Praxis-Fragment angepasst).
- Kurznamen für alle 37 Themen, 10 Formate und Töne (eigene Ressourcen `zen_taxonomy_strings.xml`, DE/EN), Rohschlüssel nur Rückfall.
- Lizenztexte: Anzeige umbrochen (`ZenNoticeText.reflow`: harte Umbrüche in Absätzen verbunden, Trennlinien kurz), Dateien unverändert.
- **Schalter beim Modell-Download (Frage a):** Der Code schaltet nichts ein. `class_feed`/`class_heavy` haben ab Werk den Wert
  „an“ (4.7) und wirken erst, sobald ein funktionierendes Modell da ist – für den Nutzer sah das wie ein stilles Einschalten aus.
  Entscheidung (konservativ im Sinne von 4.1/4.7, kein neuer Zustand): Standard bleibt an, wird aber sichtbar: Download-Dialog
  nennt den Stand beider Schalter und dass Ausschalten eine Lockerung ist, Hinweis nach der Installation, ohne Modell steht unter
  beiden Zeilen „Noch ohne Wirkung“. Nicht gewählt: Schalter ab Werk aus (wäre eine Lockerung des Standards, und das Einschalten
  danach wäre zwar strenger, aber ein zusätzlicher Schritt, den die Spec nicht vorsieht).
- **Absichtsabfrage nach ~6 min beim Kanal-Link (Frage b):** gewollt. Das Blatt fragt nicht; „Nur ansehen“ ist ein externer Link
  in `MainActivity` (Abfrage nach Ablauf des 5-min-Cooldowns), und die Rückkehr aus der YouTube-App nach ≥ 5 min Hintergrund fragt
  ebenfalls (`rules.md` „Absichtsabfrage“).

### 14.2 Korrekturen und Export (Teil B)
- `UserLabel` in Tabelle `lbl`, T-Ergebnis vor einer D-Neuberechnung in Spalte `tprev`; beides wird in `SqliteClassStore.onOpen`
  angelegt (kein Versionssprung, alte Einträge gültig, ältere App-Versionen lesen weiter; Downgrade-Absturz vermieden).
- Prüfansicht als Liste (`ZenClassReview`): Tipp → Stimmt / Stimmt nicht (7 Kategorien oder keine) / entfernen.
- `ZenClassExport` (rein, JSON über nanojson, CSV): Feldlisten `TOP_FIELDS`, `VIDEO_FIELDS`, `LANGUAGE_FIELDS`; Unit-Test prüft
  Struktur und verbietet Schlüssel mit history/subscri/setting/lock/pause/log/watch/zs_/zp_/cd_/state.
- Ausgabe-Export: Picks der aktuellen Ausgabe mit `title_only` (T, ggf. aus `tprev`) und `with_details` (D) nebeneinander.

### 14.3 Sprachfilter (Teil C)
- Extraktor (Jar `3313901108…`, `javap`): `StreamInfo.getLanguageInfo()` gibt es, **YouTube liefert immer null**
  (Bytecode `aconst_null`); `AudioStream.getAudioLocale()/getAudioTrackType()` (ORIGINAL) nur bei mehrsprachigen Videos;
  `SubtitlesStream.isAutoGenerated()/getLanguageTag()` = Sprache der automatischen Untertitel (YouTube erzeugt sie in der
  vom Kanal angegebenen Videosprache). `StreamInfoItem` (Feed/Listen) hat keine Sprachangabe. Reihenfolge der Stufe D:
  Sprachfeld → Originaltonspur → einzige ASR-Spur; sie ersetzt die Titel-Heuristik. Am Feed wirkt Stufe D nur für Videos,
  deren Details schon geladen wurden (Kanal-Blatt, Prüfung, geöffnet, „Details nachladen“) – die Auswahl selbst lädt nichts.
- Heuristik `ZenLangDetect` (Wortlisten ~100–200 häufige Wörter je Sprache für de, en, es, fr, it, pt, ca, nl, tr, pl,
  seltene Buchstaben, Endungen, Buchstabengruppen; Schrift für ru/uk, ar/fa/ur, hi, ja, ko, zh, el, he, th).
  Entscheidung `ZenLangFilter` siehe `rules.md`. Kanalebene über die letzten 20 Einträge des Kanals im Cache.
- **Fehlerraten (Unit-Test `ZenLanguageTest`, erfundene Titel, ohne Kanal-Kontext):**

| Satz | Deutsch/Englisch/Gemischt fälschlich ausgeblendet | fremd ausgeblendet | Sprache richtig benannt |
|---|---|---|---|
| A (Wortlisten daran abgestimmt, 30 de, 30 en, 15 gemischt, 82 fremd) | 0 / 75 | 70 / 82 (85 %) | 81 / 82; de 29/30, en 29/30 |
| B (erst nach der Abstimmung geschrieben, nicht abgestimmt; 20 de, 20 en, 10 gemischt, 62 fremd) | **0 / 50** | **38 / 62 (61 %)**; lateinische Schrift 24 / 48 (50 %), andere Schriften 14 / 14 | 47 / 62; de 18/20, en 16/20; Ukrainisch 0/2 als „uk“ (als Russisch erkannt, trotzdem ausgeblendet) |
| B mit Kanal-Kontext (die übrigen Titel derselben Sprache als Kanalverlauf) | – | 41 / 62 (66 %) | – |

  Ehrlich: Satz A ist überangepasst; Satz B ist die realistischere Schätzung. Kurze lateinische Titel („Cinco libros que debes
  leer“, „Hem anat al desert“) bleiben oft unerkannt und damit im Feed – gewollt („unsicher = bleibt“), die Kanal-Mehrheit
  fängt einen Teil davon. Beide katalanischen TEDx-Titel werden erkannt und ohne Kanalverlauf ausgeblendet.
- Am Gerät (TEDx Talks vorübergehend abonniert, Feed aktualisiert): Ausgabe `feed_lang hidden=9 langs=ca:2,es:2,hi:4,tr:1`;
  beide Katalanisch-Titel per Titel ausgeblendet (1,00), „Sobre assassins silenciosos i àngels de la guarda“ nur 0,60
  (unsicher, blieb in der Ausgabe auf Platz 1). Vier Videos von TEDxBocconiUMumbai mit **englischem Titel** wurden über die
  automatische Untertitelspur als **Hindi** ausgeblendet (ASR-Spur `hi` per Abruf der Watch-Seite bestätigt; ob wirklich
  durchgehend Hindi gesprochen wird, ist nicht geprüft). Spanisch/Türkisch ebenso über Stufe D.

#### Korrektur nach Gerätetest (2026-10-14, Commit `866a87d43`)
- **Befund:** Die einzige automatische Untertitelspur (ASR) trägt die Sprache, die der Kanal angibt, nicht die gesprochene. Die vier
  englischen TEDxBocconiUMumbai-Videos wurden dadurch als Hindi ausgeblendet (siehe oben). Neue Hierarchie (`ZenLangDetect.combine`,
  Regeln in `rules.md` „Sprachen im Feed“, Entscheidung D60): Sprachfeld/Originaltonspur hart (außer ein klarer Titel ab 0,45 nennt
  eine andere Sprache), Titel-Heuristik unverändert, ASR bestätigt nur den Titel (`TS`), verliert gegen eine andere Titelsprache und
  blendet allein (`S`) nie aus; `S` zählt nicht für die Kanal-Mehrheit.
- **Speicher:** Rohsignale in `lang.sig`, Neubewertung ohne Anfrage; beim Anlegen der Spalte werden alte Detail-Einträge (`src='D'`)
  einmalig gelöscht.
- **Ergebnis am Gerät (TEDx-Test, Debug):** vorher `feed_lang hidden=9` inkl. `hi:4`, nachher `hidden=4` mit `ca:2,tr:1,es:1`; die vier
  englischen Bocconi-Videos bleiben im Feed.
- **Fehlerraten der Erkennung** (unverändert, Titel-Heuristik, siehe Tabelle in 14.3): Satz A 0/75 fälschlich ausgeblendet, 70/82
  Fremdsprachen ausgeblendet; Satz B 0/50, 38/62 (61 %), lateinische Schrift 24/48, andere Schriften 14/14, mit Kanal-Kontext 41/62.
- **Nicht am Gerät geprüft:** der Fall „Titel ca + ASR ca“ (= `TS`, hart) nur im Unit-Test (`ZenLanguageTest`); ebenso die Regel, dass
  ein klarer Titel gegen Sprachfeld/Originaltonspur gewinnt.

### 14.4 Praxis im Kanal-Blatt (Teil D, Wunsch Stefan)
- Statt Blatt → Prüf-/Bestätigungsdialog nun ein Blatt: Abschnitt „Praxis-Kanal?“ mit Prüfergebnis bzw. Warnung, Wirkungstext
  und Wartezeit auf den Knöpfen; Knöpfe „Als Praxis-Kanal markieren“ / „Trotzdem …“ und „Abonnieren und als Praxis markieren“.
  Dialogfreie Teile ausgelagert: `ZenPraxisUi.addPlan/addText/commitAdd`, `ZenPlausibilityCheck.assess/logOverride`; Kanalseite
  und Long-Press behalten ihre Dialoge. Texte der Prüfung werden mit dem Kontext des Blatts erzeugt (der ViewModel-Kontext
  war in Systemsprache: englische Prüfzeile im deutschen Blatt, beim Test gefunden und behoben).
- Abweichung: `class_check` wird jetzt beim Ende der Einordnung im Blatt protokolliert (vorher erst beim Tipp auf Praxis).

### 14.5 Messwerte (Defy, Debug)
| Messung | Wert |
|---|---|
| Unit-Tests | 381 grün, 3 übersprungen (lokale Modelldaten); neu `ZenClassWhyTest` (2), `ZenNoticeTextTest` (2), `ZenClassExportTest` (5), `ZenLanguageTest` (7), `ZenSettingsLockTest` +1 (Sprachmaske), `ZenPraxisTest` +1 (`addPlan`), `ZenFeaturesTest` erweitert |
| Migration Cache v1 → neue Spalte/Tabellen | alte Yoga-Einträge nach Update sichtbar, Labels gespeichert |
| Export | 43 Videos, 30 Spracheinträge, 3 Korrekturen, Ausgabe mit T/D nebeneinander; Kanal-URL/Dauer bei 29 von 43 (die übrigen stammen aus Kanal-Stichproben und stehen nicht in der NewPipe-DB) |
| Sprachfilter | s. 14.3; Sprache entfernen während Sperre sofort, Zulassen während Sperre abgelehnt (Hinweis „gesperrt bis“) |
| Kanal-Blatt | gesperrt: Knopf aus + „gesperrt bis“; frei: „(3)“-Wartezeit, „Abonnieren und als Praxis markieren“ → abonniert + ausstehend in einem Schritt; tagesschau: deutliche Warnung inline, „Trotzdem“ → ausstehend + `class_check_override`; Ersteinrichtung: ohne Wartezeit sofort aktiv; Entfernen sofort |

### 14.6 Offen
- „Keep Android Open“ ist am Gerät nicht sichtbar prüfbar (Debug-Builds zeigen ihn upstream nie; das Release-Paket wurde bewusst
  nicht angefasst) – nur Code + Unit-Test.
- Download-Dialog mit Schalter-Stand nicht am Gerät gesehen (Modell war installiert, Löschen braucht beide Schalter aus = Lockerung).
- Spracherkennung: kurze lateinische Titel (Recall ≈ 50 % im nicht abgestimmten Satz); ASR-Sprache kann von der Titelsprache
  abweichen (Hindi-Fall); Persisch/Urdu werden erkannt, sind aber nicht in der Auswahl der Einstellung (bleiben damit ausgeblendet).
- Export: Kanal-URL/Dauer fehlen für Videos, die nur aus Kanal-Stichproben stammen.
