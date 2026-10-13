# Spec: Lokaler Inhalts-Klassifikator (Praxis/Bildung/Aktuelles/Ton) in ZenPipe

Stand: 2026-10-13. **Status: AP 1–3 umgesetzt (Abschnitt 10), AP 4–7 offen.** Entscheidungen E1–E8: Empfehlungen bestätigt. Maßgeblich bleibt `rules.md`; diese Spec schlägt Regeln vor,
Abschnitt 8 listet die nötigen Änderungen an `rules.md`. Quelle des Klassifikators: Projekt YoutubeManager, übergeben als
`resources/zen-android.zip`, `zen-handoff.zip`, `zen-rules.zip` (**ungetrackt, wird nicht committet**).

Kennzeichnung: **[V]** = in dieser Analyse selbst geprüft (Code, Jar, AAR, Probelauf), **[D]** = Zahl/Aussage aus der
Bundle-Doku (`HANDOFF.md`, `zen-android/README.md`, `docs/zen-rules.md`), nicht nachgemessen, **[A]** = Annahme/Schätzung.

## 0. Kurzfassung

1. **Machbar, aber nicht „einfach einstecken“.** Kotlin-Kern, Regeln und Modell sind fertig und am Defy gemessen
   (320 ms/Video bei 128 Tokens [D]). Die eigentliche Arbeit liegt in Datenbeschaffung (Feed hat nur Titel/Kanal),
   Auslieferung (Modell 37 MB + ONNX-Runtime 34 MB nativ pro ABI [V]) und Produktregeln.
2. **Feed: nur Titel + Kanal.** Feed-Einträge (`StreamEntity`) haben weder Beschreibung noch Tags noch Kategorie [V]. Details
   für alle Kandidaten nachzuladen kostet ~1 YouTube-Anfrage je Video und ist das Rate-Limit-Risiko. Im Probelauf ändern
   sich mit Titel+Kanal statt vollem Text 15 von 40 Kategorie-Mengen, Praxis wird nur 1 von 4-mal gefunden [V, n = 40].
   Empfehlung: Feed mit Titel+Kanal gewichten, Details nur beim Öffnen eines Videos und bei der Plausibilitätsprüfung.
3. **Plausibilitätsprüfung beim Markieren** (Praxis/Bildung): letzte 15 Videos des Kanals mit Details klassifizieren,
   Anteil anzeigen, bei Auffälligkeit warnen, „Trotzdem markieren“ führt in den bestehenden 24-h-Sperr-Ablauf. Die Liste
   bleibt die Entscheidung; der Klassifikator ändert nie selbst eine Zuordnung.
4. **Feed-Gewichtung**: Score × Gewicht (Aktuelles 0,3, positive Nachrichten 0,8, Zerstreuung 0,5, belastend × 0,5 [D]),
   belastend höchstens 1 von 15 je Ausgabe, nie hintereinander, nie auf Platz 1–9. Videos **ohne** Kategorie bleiben im
   Feed (Abweichung vom Bundle, das sie ausblendet).
5. **Auslieferung**: ONNX Runtime als Maven-AAR (MIT [V]) mit **entferntem Telemetrie-Provider** [V: vorhanden], nur
   `arm64-v8a`, Modell **nicht** in Git/APK, sondern als Opt-in-Download eines festen GitHub-Release-Assets mit SHA-256.
6. **Aufwand**: rund 6–8 Arbeitstage in 7 PR-großen Paketen (Abschnitt 7); Bildung-Prüfung erst nach der Kategorie Bildung
   (Spec `spec-bildung-und-hintergrundzeit.md`, Phase 3).

## 1. Was das Bundle liefert (Inventar, [V] aus den entpackten Archiven)

| Teil | Inhalt | Größe |
|---|---|---|
| `encoder_int8.onnx` | `intfloat/multilingual-e5-small`, Wortschatz auf ~36.600 Tokens gekürzt, int8-Gewichte, Pooling + L2 im Graph; Ein: `input_ids`, `attention_mask` [1,S] int64; Aus: `embedding` [1,384] | 36,9 MB (gzip -9: 32,7 MB, komprimiert kaum) |
| `tokenizer.bin` | XLM-R-Unigram + Normalisierungstabelle, Format „ZTK1“ (`reference/make_bin.py`) | 4,3 MB (gzip 2,4 MB) |
| `vocab_map.json` | alte → neue Token-ID (base64 int32) | 1,3 MB (gzip 0,12 MB) |
| `heads.json` | 3 lineare Köpfe + Platt-Kalibrierung: Thema 37, Format 10, Ton 3 | 0,1 MB |
| `yt_categories.json` | YouTube-Kategorie-ID → deutscher Name (für den Eingabetext) | < 1 KB |
| `kotlin-core/` | `UnigramTokenizer`, `TextBuilder`, `Heads`, `ZenRules`, `ZenPipeline`/`OrtEncoder` (Paket `de.zen.core`, 652 Zeilen) | – |
| `golden.json` | 40 Fälle: Text, Token-IDs, Embedding, Wahrscheinlichkeiten, Ton, Kategorien (`min_p` 0,6, 256 Tokens) | 0,4 MB |
| `zen-rules/` | Python-Regeln (Stdlib), `golden_cases.json` (200 Fälle, **nur Wahrscheinlichkeiten, keine Titel**), `test_golden.py` | – |

Ablauf je Video: Text `query: Titel — Kanal | Kategorie: … | Tags: … | Beschreibung ≤ 600` → Tokenizer (≤ 128 Tokens
empfohlen) → ID-Abbildung → Encoder → 3 Köpfe → `tone_from_probs` → `categories(…, min_p = 0,6)` → `weight` / `compose_feed`.
Kategorien: praxis, ruhe, psyche, bildung, koerper, aktuelles, zerstreuung (mehrere oder keine je Video).

Probeläufe dieser Analyse:
- `zen-rules/test_golden.py` mit Python 3, nur Stdlib, in einer Kopie: **2/2 Tests grün** (200 Goldfälle + Feed-Deckel) [V].
- Eigener Probelauf (Tokenizer aus `tokenizer.bin` nach Referenzalgorithmus, ONNX Runtime 1.31 der vorhandenen
  `~/.ytplaylist/onnx-venv`, nichts installiert, kein Netz) gegen `golden.json`: **Token-IDs 40/40, Kategorien 40/40,
  Ton 40/40** bei 256 Tokens [V]. Damit ist das Bundle in sich konsistent. Weitere Ergebnisse in Abschnitt 2.3.

## 2. A – Daten

### 2.1 Felder in Listen vs. Detailseite (NewPipeExtractor `3313901108…`, Jar aus dem Gradle-Cache, `javap`) [V]

| Feld | `StreamInfoItem` (Feed, Kanal-Tab, Playlist, Suche) | in der Feed-DB `StreamEntity` | `StreamInfo` (Detail/Wiedergabe) |
|---|---|---|---|
| Titel (`getName`) | ja | ja (`title`) | ja |
| Kanalname, Kanal-URL | ja | ja (`uploader`, `uploaderUrl`) | ja |
| Dauer, Typ (Live/Short) | ja (`getDuration`, `getStreamType`, `isShortFormContent`) | ja (`duration`, `streamType`) | ja |
| Upload-Datum, Aufrufe | ja | ja | ja |
| Kurzbeschreibung (`getShortDescription`) | **Default `null`** im Interface; YouTube-Listen-Extraktor liest `detailedMetadataSnippets`/`descriptionSnippet` (Snippet, nicht die Beschreibung); RSS-Feed-Extraktor (`YoutubeFeedInfoItemExtractor`) liest nur title/author/published/link/thumbnail/views | **nein** (keine Spalte) | – |
| Beschreibung | nein | nein | `getDescription()` (YouTube: HTML mit Links) |
| Tags | nein | nein | `getTags()` |
| Kategorie | nein | nein | `getCategory()` (englischer Name, z. B. „Music“; so schon genutzt in `ZenContentKind.classify`, `zen/ZenPause.kt:81-93`) |
| Sprache, Lizenz, Altersgrenze | nein | nein | ja |

Belege: `StreamEntity` hat nur `title, streamType, duration, uploader, uploaderUrl, thumbnailUrl, viewCount,
textualUploadDate, uploadDate` (`database/stream/model/StreamEntity.kt:29-65`). Der Feed lädt standardmäßig über die
**Kanal-Tabs** (`feed_use_dedicated_fetch_method` = false, `local/feed/service/FeedLoadManager.kt:173-230`), nicht über RSS.
Die Zen-Kandidaten kommen aus dieser DB (`zen/ZenFeedProvider.kt:53-77`).

**Folge:** Ohne Zusatzanfragen kennt der Feed pro Video nur **Titel + Kanal** (+ Dauer/Datum, die der Klassifikator nicht
nutzt; Dauer/Jahr brachten im Training keinen Gewinn [D, `student-features.md`]). Kategorie-ID für den Text: `StreamInfo`
liefert den englischen Namen, `TextBuilder` erwartet die numerische ID → kleine Tabelle Englisch → ID (15 Einträge) nötig [A:
Extraktor liefert immer Englisch, unabhängig von der Inhaltssprache; am Gerät prüfen]. Beschreibung: YouTube-HTML muss vor
dem Textbau zu Klartext werden (`HtmlCompat.fromHtml(…).toString()`); kleine Abweichungen zum Server-Text (Linkdarstellung)
sind zu erwarten [A].

### 2.2 Minimalbedarf und Qualitätsverlust

- Minimal: Titel (Pflicht) + Kanal. Alles andere ist optional (`TextBuilder.build` lässt leere Teile weg).
- Die Köpfe sind auf den **vollen** Text (`rich`) trainiert [D]. Für Titel+Kanal allein gibt es in der Bundle-Doku **keine
  Messung**. Die alte Opus-Stichprobe „nur Titel/Kanal“ in `docs/zen-rules.md` misst die *Regeln auf Lehrer-Labels*, nicht den
  Schüler auf Kurztext, und ist hier nicht übertragbar.

### 2.3 Probelauf: Kurztext gegen vollen Text [V, n = 40, nur Indiz]

Basis: Kategorien von `golden.json` (voller Text, 256 Tokens). Das ist die Modell-Ausgabe, **nicht** die Wahrheit; gemessen
wird also nur, wie stark sich die Ausgabe verschiebt. 37 der 40 Fälle haben eine Beschreibung.

| Eingabe | gleiche Kategorie-Menge | gleicher Ton | ohne Kategorie |
|---|---|---|---|
| voller Text, 256 Tokens (Referenz) | 40/40 | 40/40 | 13/40 |
| voller Text, **128 Tokens** (Doku-Empfehlung) | 30/40 | 38/40 | – |
| **Titel + Kanal** (128) | 25/40 | 36/40 | 17/40 |
| Titel + Kanal + Kategorie (128) | 27/40 | – | – |

Je Kategorie (Referenz → mit Titel+Kanal, davon übereinstimmend): Bildung 12 → 9 (7), Zerstreuung 8 → 8 (6),
Aktuelles 4 → 3 (3), **Praxis 4 → 1 (1)**, Psyche 1 → 1 (1); neu nur mit Titel+Kanal: Körper 3, Ruhe 1. Insgesamt gingen 11
von 29 Labels verloren, 8 kamen hinzu.

Lesart: Mit Titel+Kanal ändert sich gut ein Drittel der Einordnungen; Praxis-Erkennung hängt stark an der Beschreibung.
Auch 128 statt 256 Tokens verschiebt 10 von 40 Mengen. Die Doku meldet für 128 Tokens trotzdem gleiche oder bessere F1 gegen
die Wahrheit [D]; Verschiebung ist also nicht gleich Verschlechterung. n = 40 ist zu klein für Prozentangaben je Kategorie.
**Nötig vor dem Bau (AP 0):** im YoutubeManager die Ende-zu-Ende-F1 mit Titel+Kanal gegen die Konsens-Wahrheit messen und
optional eigene **Kurztext-Köpfe** trainieren (gleicher Encoder, Text `query: Titel — Kanal`). Das kostet keine App-Größe
(Köpfe 0,1 MB).

### 2.4 Wo im Zen-Feed-Ablauf

Heutiger Ablauf (alle Stellen [V]):
1. Feed-Aktualisierung (NewPipe) schreibt `StreamEntity`-Zeilen (`FeedLoadManager`).
2. Feed öffnen → `ZenFeedProvider.compute` (Hintergrund-Thread, „Blocking“, `zen/ZenFeedProvider.kt:25-49`) →
   `ZenFeedState.view` → bei fälliger Ausgabe `createEdition` (`zen/ZenFeedState.kt:143-166`) → `ZenFeedPlanner.select`
   (`zen/ZenFeedPlanner.kt:97-126`) mit `isEligible` (`:68-75`) und `score` (`:78-94`).
3. Eine Ausgabe wird einmal gebaut und bleibt (Regel „Laufendes bleibt unberührt“).

Einbau:
- **Klassifikation getrennt von der Auswahl.** Ein Hintergrund-Job klassifiziert neue Kandidaten nach jeder
  Feed-Aktualisierung und legt das Ergebnis in einen **Cache** (`ZenClassCache`, eigene kleine SQLite-Datei bzw. Room-Tabelle
  in einer **eigenen** Datenbank unter `noBackupFilesDir`, nicht in der NewPipe-DB, damit keine Upstream-Migration nötig ist).
  Schlüssel `serviceId + url`, Werte: Kategorien mit Sicherheit, Ton, Top-Themen/-Formate (für „warum“), Eingabestufe
  (`T` = Titel+Kanal, `D` = mit Details), Modellversion, Zeitpunkt. Aufbewahrung 30 Tage nach letztem Zugriff.
- **`Candidate` bekommt Felder** `zenCats: Map<String, Double>?`, `tone: String?` (aus dem Cache in
  `ZenFeedProvider.candidates`). `select` liest nur, rechnet nie selbst. Fehlt ein Eintrag (Job noch nicht fertig), gilt
  „nicht eingeordnet“ (Abschnitt 4.3).
- **Notfall im Auswahlmoment:** Sind beim Bau einer Ausgabe noch Kandidaten ohne Eintrag, dürfen in `compute` höchstens
  **3 s** nachklassifiziert werden (≈ 10 Videos), der Rest bleibt „nicht eingeordnet“. Die Ausgabe wartet nicht länger.
- **Details beim Öffnen:** Wenn ein Video geöffnet/abgespielt wird, liegt `StreamInfo` ohnehin vor (`ZenPause.onStreamInfo`,
  `zen/ZenPause.kt:501-528`, und die Detailseite). Dann einmal mit vollem Text klassifizieren und den Cache auf Stufe `D`
  heben. Kostet keine zusätzliche Netzanfrage.

### 2.5 Mengen und Netzlast

- **Kandidaten** = ungesehene Abo-Videos ≤ 7 Tage, ≥ Mindestlänge, ohne Live/Musik/Praxis. Neue Videos pro Stunde ≈
  (Abos × Uploads pro Woche) / 168. Beispiel [A]: 150 Abos × 3/Woche ≈ 2,7 neue Videos pro Stunde, ≈ 450 Kandidaten im
  7-Tage-Fenster beim ersten Lauf.
- **Rechenzeit** am Defy (320 ms je Video bei 128 Tokens, 4 Threads [D]; Titel+Kanal hat ~20–40 Tokens, also eher
  schneller [A]): laufend ≈ 1 s pro Stunde; Erstlauf 450 Videos ≈ 2,5 min, 1.000 Videos ≈ 5–6 min.
- **Netz für Details**: Jede `StreamInfo` ist eine eigene YouTube-Anfrage (Player/Next-Endpunkt, ExtractorHelper-Cache nur im
  Speicher) [V: `StreamInfo.getInfo` ist der einzige Weg zu Beschreibung/Tags]. 450 Detail-Abrufe im Erstlauf und danach
  ~65 pro Tag würden das Verbrauchsprofil gegenüber YouTube deutlich verändern (Bot-Erkennung, 429, „Sign in to confirm“)
  [A, typisches NewPipe-Fehlerbild]. **Empfehlung: kein Massen-Nachladen.** Details nur
  (a) beim Öffnen (kostenlos, s. o.) und (b) für die Plausibilitätsprüfung (15 Abrufe, selten, gedrosselt auf 1 Abruf/s,
  abbrechbar).
- Ein späteres optionales „Details nachladen bei WLAN + Laden, max. 30 pro Tag, nur Grenzfälle“ ist möglich, aber nicht Teil
  dieser Spec.

## 3. B – Integration

### 3.1 Abhängigkeiten und Build

| Punkt | Befund | Empfehlung |
|---|---|---|
| ONNX Runtime | `com.microsoft.onnxruntime:onnxruntime-android:1.31.0` (in der Gerätemessung verwendet [D]); AAR geprüft [V]: Java-Klassen 149 KB, nativ `libonnxruntime.so` arm64 **34,2 MB**, armv7 24,2 MB, x86/x86_64 je ~40,8 MB (unkomprimiert; gzip arm64 12,9 MB) | eine Version fest pinnen; `-keep class ai.onnxruntime.** { *; }` in `proguard-rules.pro` (JNI ruft Java-Klassen per Name) [A: konkrete Keep-Liste im Release-Build prüfen] |
| **Telemetrie** | AAR-Manifest registriert `ai.onnxruntime.TelemetryInitializer` (ContentProvider, lädt die native Bibliothek **bei jedem App-Start**) und fordert `INTERNET`/`ACCESS_NETWORK_STATE`; Paket `ai.onnxruntime.telemetry` mit `HttpClient` (u. a. `isDeviceIdCollectionEnabled`), native JNI-Funktionen `HttpClient_setDeviceInfo/…`; `OrtEnvironment.setTelemetry(boolean)` existiert [V]. Ob und wohin tatsächlich gesendet wird: **nicht verifiziert** (kein Klartext-Endpunkt gefunden) | Provider im App-Manifest entfernen (`tools:node="remove"`), `setTelemetry(false)` vor jeder Session, am Gerät Netzverkehr prüfen (Abschnitt 6.2). Verstößt sonst gegen „nichts in die Cloud“ und wäre in F-Droid ein Anti-Feature „Tracking“ |
| minSdk | AAR: `minSdkVersion 24` [V]; ZenPipe: 23 (`buildSrc/.../ProjectConfig.kt:8`) [V] | Manifest `tools:overrideLibrary="ai.onnxruntime"` + Laufzeit-Schutz (Klassifikator unter API 24 aus); kein Upstream-Diff in `ProjectConfig` |
| API < 26 | `kotlin-core` nutzt `java.util.Base64` (erst API 26) und `String.codePoints()` (API 24) [V] | beim Port `android.util.Base64` bzw. eigene Code-Point-Schleife |
| ABI | Release-APK enthält heute schon `lib/` für 4 ABIs (androidx.graphics.path) [V]; Defy: `arm64-v8a,armeabi-v7a,armeabi` [V, `adb`] | `ndk { abiFilters += "arm64-v8a" }` für Release (Entscheidung E2); Debug zusätzlich `x86_64` für Emulator |
| Native Libs packen | AGP speichert `.so` bei minSdk ≥ 23 standardmäßig unkomprimiert (Download = 34 MB) [A: AGP-Default] | `packaging.jniLibs.useLegacyPackaging = true` → Download ~13 MB, dafür Entpacken bei Installation (+34 MB Speicher) |
| Modell-Assets | int8-ONNX komprimiert kaum (36,9 → 32,7 MB) [V] | falls doch im APK: `androidResources.noCompress += "onnx"` und Session aus Datei/`ByteBuffer`; empfohlen ist aber der Download (3.3) |
| Kotlin-Port | `de.zen.core`, Stefans eigener Code, im YoutubeManager ohne LICENSE-Datei [V] | nach `org.schabi.newpipe.zen.classify` übernehmen, GPLv3-Header (Stefan ist Urheber, darf relizenzieren); `formatKtlint` (lange Zeilen, Einzeiler-`if`) |
| JSON | `kotlinx-serialization-json` ist schon Abhängigkeit (`app/build.gradle.kts:287`) [V] | keine neue Abhängigkeit; `vocab_map.json` besser als Binärdatei (1 MB statt 1,3 MB, kein base64-Parsen) |
| WorkManager | schon im Projekt (`androidx.work.runtime`, `NotificationWorker`) [V] | für den Klassifikations-Job nutzen |

**APK-Größe** (Basis: heutiges `app-release-unsigned.apk` 6,8 MB, gebaut heute 09:09 [V]; Rest [A], aus gzip-Größen geschätzt):

| Variante | geschätzte APK |
|---|---|
| heute | 6,8 MB |
| + ORT arm64, unkomprimiert, Modell als Download | ≈ 41 MB |
| + ORT arm64, `useLegacyPackaging`, Modell als Download | **≈ 20 MB** |
| + ORT arm64 + Modell/Tokenizer im APK | ≈ 55 MB (komprimiert) bis 83 MB (unkomprimiert) |
| universal (4 ABIs) + Modell im APK | ≈ 110–180 MB |

Ein Gradle-Lauf zur Messung war nicht nötig: die ORT-Bibliothek ist in der Release-Variante fast vollständig enthalten, die
Schätzung oben reicht für die Entscheidung. Nach AP 2 wird die echte Größe gemessen.

### 3.2 Laufzeit: Speicher, Threads, Hintergrund, Akku

- Speicher [D]: ORT nativ ≈ 120 MB Spitze, Java-Heap ≈ 40 MB, Laden 1,2 s (Kotlin-Messung). Der Defy hat 3,8 GB [D].
  Session **lazy** laden und nach 60 s Leerlauf schließen [A: Wert]; nie während der Player-Initialisierung laden.
- Threads: ein eigener Single-Thread-Executor (`ZenClassifier`), ORT `intraOp` = 4 im Leerlauf, **2 während Wiedergabe**
  (`ZenPlayback.isPlaying`), damit Video/Audio nicht ruckeln [A: Ruckeln ungetestet]. 2 Threads ≈ 0,72 s/Video bei 256
  Tokens [D].
- Hintergrund: `ZenClassifyWorker` (WorkManager, `OneTimeWorkRequest`, unique `KEEP`), eingereiht nach einer
  Feed-Aktualisierung und beim App-Start; Constraint `requiresBatteryNotLow`; Batches à 50, Fortschritt im Cache, damit ein
  Abbruch nichts verliert. Kein Foreground-Service nötig (Laufzeit < 10 min) [A].
- Akku [A]: 450 Videos × 0,3 s × 4 Kerne ≈ 9 CPU-Minuten einmalig, danach ≈ 1 s pro Stunde. Vernachlässigbar gegenüber
  Video-Dekodierung; trotzdem am Gerät messen (6.2).

### 3.3 Modell-Auslieferung

| Option | Repo | APK / Updates | Release-CI | F-Droid-Repo (`fdroid/`) | Obtainium | Bewertung |
|---|---|---|---|---|---|---|
| **A: Opt-in-Download** eines festen GitHub-Release-Assets (eigener Tag, z. B. `model-e5s-1`), SHA-256 im Code, Ablage `noBackupFilesDir` | nichts | APK ≈ 20 MB, Modell nur einmal (40 MB) und nur bei Modellwechsel neu | unverändert | unverändert (lädt nur APKs) | unverändert | **empfohlen** |
| B: Modell im APK, beim Build per Gradle-Task aus Release-Asset geholt (Prüfsumme) | nichts | jedes App-Update ≈ 55–80 MB | Download-Schritt im Workflow | größere APKs, sonst gleich | gleich | gut für „alles offline ab Installation“, teuer bei häufigen Releases |
| C: Modell direkt in Git | +37 MB je Modellversion, für immer | wie B | – | – | – | abgeraten |
| D: Git LFS | LFS-Kontingent (Speicher/Bandbreite begrenzt) [A: aktuelle GitHub-Grenzen prüfen]; jeder CI-Checkout zählt | wie B | `lfs: true` | – | – | abgeraten |

Begründung A: ZenPipe veröffentlicht oft (`0.1.0-zen.2`), F-Droid und Obtainium kennen keine Delta-Updates [A], jedes Update
würde sonst 40–70 MB extra kosten. Das Modell ist Daten, kein Code (keine Code-Nachladung). Die Anfrage geht an GitHub, nicht
an YouTube oder NewPipe; sie passiert nur nach ausdrücklichem Tipp („Inhalts-Erkennung herunterladen, 40 MB“) und ist mit
SHA-256 gegen Austausch gesichert. Ohne Modell läuft ZenPipe exakt wie heute.

Release-Workflow: `.github/workflows/release.yml:79` nimmt **ein** APK an [V]. Mit `abiFilters arm64-v8a` bleibt es bei einem
APK; ABI-Splits (Entscheidung E2 b) bräuchten Anpassungen an `release.yml`, `android-release-preflight.sh`, versionCode-Schema
je ABI und eine Obtainium-Regex.

### 3.4 Lizenzen und F-Droid

| Bestandteil | Lizenz | Quelle der Prüfung | GPLv3 |
|---|---|---|---|
| ONNX Runtime (Java + nativ) | MIT | POM `onnxruntime-1.22.0.pom` und `LICENSE` der Python-Wheel 1.31 [V]; das Android-AAR selbst enthält **keine** Lizenzdatei [V]; `ThirdPartyNotices.txt` (6.369 Zeilen) [V] | kompatibel; Copyright-Hinweis + ThirdPartyNotices mitliefern (About-Bildschirm/AboutLibraries) |
| `multilingual-e5-small` (Gewichte) | MIT | Model Card im HF-Cache (`license: mit`) [V] | kompatibel; Hinweis mitliefern |
| XLM-R-Tokenizer (Wortschatz, Normalisierung) | MIT (fairseq/XLM-R) [A: nicht im Bundle belegt] | e5 baut laut Model Card auf xlm-roberta auf [V] | kompatibel |
| sentencepiece-Bibliothek | wird **nicht** genutzt (eigener Kotlin-Tokenizer) [V] | – | – |
| Kotlin-Kern, Regeln, Köpfe | Stefans eigene Arbeit, keine Lizenzdatei [V] | – | als GPLv3 in ZenPipe übernehmen |
| Trainingsdaten der Köpfe | Labels von Claude-Agenten (Lehrer) auf Titeln/Beschreibungen aus Stefans Watch Later [D] | – | Gewichte enthalten keine Texte. Hinweis in der Doku: „Köpfe auf KI-erzeugten Labels trainiert“. Nutzungsbedingungen des Label-Anbieters für das Trainieren eines eigenen Klassifikators prüfen [A, kein Rechtsrat] |

**Achtung `golden.json`:** enthält echte Titel und Beschreibungen (inkl. Sponsor-Links) aus Stefans Watch Later [V]. Nicht in
das öffentliche Repo committen (verrät Sehgewohnheiten). Für Tests: `golden_cases.json` (keine Titel) ist unbedenklich; für
Tokenizer/Encoder neue Goldfälle aus neutralen, selbst geschriebenen Texten im YoutubeManager erzeugen lassen.

**F-Droid:** Das eigene Repo verteilt fertig signierte APKs (`fdroid/build-repo.sh`, `AllowedAPKSigningKeys`) [V]; dafür gelten
die Build-Regeln des offiziellen F-Droid **nicht**. Für das offizielle F-Droid wäre ORT als vorgebautes Maven-Binary ein
Hindernis (dort wird alles aus Quellen gebaut) und ein Telemetrie-Pfad ein Anti-Feature [A: F-Droid-Policy]. Ein
heruntergeladenes MIT-Modell ist als freie Daten vertretbar [A]. Bestehendes Anti-Feature `NonFreeNet` (YouTube) bleibt; ein
weiteres ist bei entferntem Provider nicht nötig [A]. Die Metadaten (`fdroid/metadata/…yml`) brauchen nur einen Satz in der
Beschreibung („optionale lokale Inhalts-Erkennung, Modell 40 MB, Download von GitHub“).

Langfristige Alternativen (nicht jetzt): eigener ORT-Minimal-Build nur mit den Operatoren dieses Modells (kleiner,
reproduzierbar, ohne Telemetrie) oder ein reiner Kotlin/Java-Encoder (keine native Bibliothek, langsamer) [A: beides
unerprobt, je 2–4 Tage].

## 4. C – Produktlogik

### 4.1 Grundsatz

- **Der Klassifikator entscheidet nie über Zugehörigkeit.** Praxis- und Bildungslisten bleiben Nutzerlisten mit 24-h-Sperre
  (`rules.md` Praxis/Zuordnung: „keine automatische Erkennung, nie Titel/Stichworte“). Er liefert (a) eine **Warnung beim
  Markieren**, (b) eine **Reihenfolge im Feed** und (c) später eine **strengere** Einschränkung (Bildung ohne Politik).
- **Asymmetrie wie bei der Einstellungs-Sperre:** Der Klassifikator darf nur in die strengere Richtung automatisch wirken.
  Alles, was ihn abschaltet oder umgeht (Schalter aus, Ausnahmeliste), ist eine **Lockerung** mit 24-h-Sperre.
- **Erklärbar und lokal:** jede Wirkung hat eine „Warum“-Zeile mit Kategorie, Sicherheit, Ton, wichtigsten Themen/Formaten
  und der Eingabestufe („nur Titel und Kanal“ / „mit Beschreibung“). Keine Daten verlassen das Gerät.

### 4.2 Abbildung Klassifikator → Zen-Regeln

| Klassifikator | Feed-Ausgabe | Praxis-Markieren | Bildung (nach Spec Phase 3) |
|---|---|---|---|
| praxis | Gewicht 1,0 (Praxis-Kanäle sind ohnehin nicht im Feed) | zählt als Treffer | – |
| ruhe | Gewicht 1,0 | zählt als Treffer (Einschlafhilfen gehören zu Praxis, `spec-praxis.md`) | – |
| psyche | 1,0 | – | „Psychologie erklärt“ ist Psyche **und** Bildung [D] |
| bildung | 1,0 | – | Treffer beim Markieren |
| koerper | 1,0 | – (Training ohne Mitmach-Format) | – |
| aktuelles | **0,3**, positiver Ton **0,8** [D] | Warnsignal | Politik ⇒ **keine** Bildungsrechte |
| zerstreuung | **0,5** [D] | Warnsignal | Warnsignal |
| Ton belastend | Gewicht × 0,5, höchstens 10 % der Ausgabe, nie hintereinander, nie Platz 1–9 [D] | – | KI-Ethik + belastend blockiert Bildung (Regel im Bundle) |
| keine Kategorie | Gewicht **1,0**, Kennzeichnung „nicht eingeordnet“ | zählt als „unklar“ (weder Treffer noch Warnsignal) | „unklar“ |

### 4.3 Feed-Gewichtung (Änderung in `ZenFeedPlanner`)

- `score` (`zen/ZenFeedPlanner.kt:78-94`) bleibt; neu: `zenScore = score × weight(cats, tone)` mit den Bundle-Gewichten.
  Mehrere Kategorien: das **höchste** Gewicht zählt (Bundle-Regel `weight`), z. B. Tech-News = Bildung + Aktuelles → 1,0
  nur dann, wenn keine Politik (Politik blockiert Bildung schon in den Regeln).
- Belastend-Regel in `select` (`:97-126`) eingebaut, nicht über `composeFeed`: beim Füllen der Ausgabe wird ein belastendes
  Video übersprungen, wenn (Anteil + 1) > 10 % der Plätze, wenn das vorige belastend war oder Platz < 10. Bei 15 Plätzen ist
  das **höchstens 1** belastendes Video, frühestens auf Platz 10. Übersprungene warten (zählen als `leftover`) und altern nach
  7 Tagen heraus. `MAX_PER_CHANNEL`, Deckel und `counted` gelten unverändert.
- Abweichung vom Bundle: **Videos ohne Kategorie werden nicht ausgeblendet.** Das Ausblenden wäre eine Autopilot-Entscheidung
  über ~21–33 % der Videos [D: 21–26 % ohne Kategorie; V: 13/40 im Probelauf], und „gesehen ≠ irrelevant“ gilt laut Handoff.
- Feed-Gruppen-Rang ist die ausdrückliche Wahl des Nutzers. Ein Gruppenrang 0 (60 Punkte) × 0,3 = 18 Punkte: Nachrichten
  landen also auch in der Lieblingsgruppe hinten. Gewollt laut Ziel (3); wer das nicht will, schaltet die Gewichtung aus
  (Lockerung).
- Optional (Entscheidung E6): Deckel „höchstens 3 Aktuelles je Ausgabe“ [Vorschlag, keine Doku-Zahl].

### 4.4 Plausibilitätsprüfung beim Markieren (Praxis jetzt, Bildung mit Phase 3)

Ablauf (Einstieg `ZenPraxisUi.onMenu` → Zweig `NONE`, vor `confirmAdd`, `zen/ZenPraxisUi.kt:52-66` und `:85`):
1. Kanal: Videos-Tab laden wie `ZenPraxisFragment.newestOf` (`zen/ZenPraxisFragment.kt:120-140`, eine Anfrage, ohne
   Shorts/Live), die **neuesten 15** nehmen. Playlist: die ersten 15 Einträge.
2. Für diese 15 die `StreamInfo` laden (gedrosselt 1/s, Fortschritt „Prüfe 7 von 15 …“, „Überspringen“ jederzeit) und mit
   vollem Text klassifizieren; Ergebnisse in den Cache (Stufe `D`). Ohne Netz/bei Fehlern: Titel+Kanal, so gekennzeichnet.
3. Auswertung (Schwellen sind **Vorschläge**, mit der Stichprobe in 6.3 zu kalibrieren):

| Ziel | Treffer | Ergebnis |
|---|---|---|
| Praxis | Anteil praxis ∪ ruhe ≥ 60 % und Aktuelles + Zerstreuung < 20 % | „passt“ (grüner Hinweis im Bestätigungsdialog) |
| Praxis | Anteil 30–60 % oder Aktuelles + Zerstreuung ≥ 20 % | Warnung |
| Praxis | Anteil < 30 % | deutliche Warnung |
| Bildung | Anteil bildung ≥ 60 % und Aktuelles < 10 % | „passt“ |
| Bildung | Aktuelles ≥ 20 % (Politik) | deutliche Warnung „häufig Politik“ |
| Bildung | Zerstreuung ≥ 30 % | Warnung „viel Unterhaltung“ |
| beide | ≥ 8 von 15 „nicht eingeordnet“ | Hinweis „kein klares Bild“ (keine Warnung) |

Wegen Recall ≈ 71 % [D] und schwacher Praxis-Erkennung bei Kurztext [V] darf die Formulierung nie „ist kein Praxis-Kanal“
lauten, sondern „erkennt nur …“.

4. Dialogtexte (Deutsch; Englisch analog in `values/strings.xml`):
   - Titel: „Passt das zu Praxis?“
   - Warnung: „Die Inhalts-Erkennung ordnet nur **%1$d von %2$d** neueren Videos von „%3$s“ als Praxis (Mitmachen,
     Atmen, Meditation, Einschlafen) ein. **%4$d** wirken wie Unterhaltung oder Aktuelles, z. B. „%5$s“.
     Die Erkennung irrt sich bei etwa jedem vierten Video. Praxis-Videos sind von Feed-Grenzen und Pflichtpause ausgenommen.
     Bitte nur Kanäle markieren, die du wirklich zum Üben nutzt.“
   - Bildung/Politik: „%1$d von %2$d neueren Videos von „%3$s“ wirken wie Politik oder Nachrichten. Bildung gilt nur für
     Inhalte ohne Politik; diese Videos würden ohnehin normal zählen.“
   - Knöpfe: „Videos ansehen“ (Liste mit Einordnung je Video, „warum“), „Abbrechen“, „Trotzdem markieren“. Letzteres führt
     in den **unveränderten** Ablauf `confirmAdd` (3 s Wartezeit, ausstehend, 24-h-Sperre, 5-min-Rückgängig).
5. Kein Modell: Prüfung entfällt mit einem Satz im Bestätigungsdialog („Inhalts-Erkennung nicht installiert“). Ersteinrichtung:
   Prüfung läuft auch dort (Warnung ist billig, Markieren bleibt sofort wirksam).
6. ZenLog: `class_check kind=PRAXIS n=15 hit=… news=… ent=… none=… level=D result=warn`, `class_check_override` bei „Trotzdem“.

### 4.5 Abspielprüfung „Bildung nur ohne Aktuelles/Politik“ (erst mit Kategorie Bildung)

- Ort: `ZenPause.onStreamInfo` (`zen/ZenPause.kt:501-528`), wo heute `ZenContentKind.classify` die Art bestimmt. Die
  Klassifikation läuft **asynchron** (Modell-Laden bis 1,2 s [D]); der Player startet normal.
- Regel: Ein Video eines Bildungskanals mit `aktuelles` (bzw. Politik-Thema ≥ 0,5, Bundle-Regel) bekommt **keine**
  Bildungsrechte (zählt als normales Video: Pflichtpause ab 5 min, Tageslimit). Strenger, daher automatisch erlaubt;
  Protokoll `edu_denied reason=politik p=0.72`.
- Bis das Ergebnis da ist, gilt das Video als normal (fail-closed wie „Art unbekannt = Video“).
- Erklärung im Player: dezenter Hinweis „Zählt nicht als Bildung: erkannt als Aktuelles/Politik (0,72). Warum?“.
- Praxis bekommt **keine** Abspielprüfung (Entscheidung E5): heute gilt „alle Videos eines Praxis-Kanals sind Praxis“.

### 4.6 Ausnahmeliste

- Pro Kanal/Playlist: „Inhalts-Erkennung für diesen Kanal nicht anwenden“ (z. B. Geschichtskanal, den das Modell als Politik
  liest). Wirkung: Bildungsprüfung aus, Feed-Gewicht 1,0.
- Hinzufügen = **Lockerung** über `ZenSettingsLock` (gleiche Mechanik wie `addPraxis`, `zen/ZenSettingsLock.kt:282`):
  Bestätigung 3 s, ausstehend, 24-h-Sperre, Rückgängig 5 min. Entfernen = strenger, sofort. Speicherung `zs_class_exempt` in
  `zen_state` (nicht in Export/Import/Reset).

### 4.7 Einstellungen > Zen, Abschnitt „Inhalts-Erkennung (lokal)“

| Eintrag | Default | Richtung |
|---|---|---|
| Status: „nicht installiert – Herunterladen (40 MB)“ / „aktiv, Modell e5-small v1“ / „defekt – neu laden“ | – | Herunterladen ist nicht gesperrt; Löschen ist nur ohne aktive Schalter erlaubt |
| Feed nach Inhalt gewichten | an (sobald installiert) | aus = Lockerung |
| Belastende Videos begrenzen (max. 1 von 10, nie hintereinander) | an | aus = Lockerung |
| Bildung nur ohne Politik (mit Phase 3) | an | aus = Lockerung |
| Ausnahmen (Liste) | leer | Hinzufügen = Lockerung |
| Plausibilitätsprüfung beim Markieren | immer an, **kein Schalter** (nur Warnung) | – |

### 4.8 Fehlerfälle

| Fall | Verhalten |
|---|---|
| Modell nicht installiert | alles wie heute; Feed ohne Gewichtung; Markieren ohne Prüfung |
| Modell defekt (SHA-256 falsch, ORT-Fehler, OOM) | Session aus, `class_error` im ZenLog, Status „defekt“; Feed ohne Gewichtung (Ranking ist kein Privileg); **Bildungsrechte fail-closed** (gilt als normales Video), solange „Bildung nur ohne Politik“ an ist |
| API < 24 oder fremde ABI | Klassifikator nicht verfügbar, Abschnitt ausgegraut |
| Video ohne Kategorie | Feed: Gewicht 1,0, „nicht eingeordnet“; Prüfungen: „unklar“ |
| Kandidat noch nicht klassifiziert | wie „nicht eingeordnet“ (max. 3 s Nachklassifikation beim Ausgabe-Bau) |
| Sprache | e5 ist mehrsprachig; Köpfe vor allem auf Deutsch/Englisch trainiert [A]. Andere Sprachen: Ergebnisse ungeprüft, Warnungen entsprechend vorsichtig |
| Modellwechsel | Cache-Einträge mit alter Version werden neu berechnet; laufende Ausgabe bleibt unberührt |
| Clickbait | im Titel erkannt (`isClickbait`, ohne Regex wegen ICU-Fehler [D]); senkt Psyche um 15 % |

## 5. D – Risiken und Fehlerquoten (ehrlich)

| Zahl | Wert | Herkunft |
|---|---|---|
| Kategorien Ende zu Ende, Android-Modell | F1 ≈ 72 %, Precision ≈ 85 % (`MIN_P` 0,6) | [D] README |
| mit 128 Tokens | F1 73,7 / 75,1 (zwei Sets) | [D] |
| Server-Modell e5-base | F1 ≈ 77 %; je Kategorie Praxis 90, Bildung 78, Zerstreuung 75, Körper 70, Aktuelles 69, Psyche 67 | [D] `zen-rules.md`, **Server**, nicht App |
| Ton | Genauigkeit 88,6 %; belastend P 57 % / R 67 % bei n ≈ 8 | [D], sehr unsicher |
| Videos ohne Kategorie | 21–26 % (Schüler-Labels) | [D]; 13/40 im Probelauf [V] |
| Obergrenze (die zwei Opus-Beurteiler untereinander) | F1 92–94 % | [D] |
| Kurztext (Titel+Kanal) | keine Messung gegen Wahrheit; 15/40 Kategorie-Mengen ändern sich, Praxis 1/4 | [V] Probelauf, n = 40 |

Was das praktisch heißt: Etwa jede 7. Einordnung ist falsch (Precision 85 %), fast jede 3.–4. richtige Kategorie fehlt
(Recall ~71 %). „Belastend“ beruht auf sehr wenigen Testfällen. Wahrheit sind Urteile zweier KI-Beurteiler, nicht Stefans
Urteil. Für Warnungen und Ranking reicht das, für harte Entscheidungen nicht – daher keine.

Weitere Risiken: Telemetriepfad im AAR (3.1); Text-Drift durch HTML-Beschreibungen und Kategorie-Namen (2.1); Trainingsdaten
aus einem einzigen Watch-Later-Profil (Themenlücken, z. B. Sport/Familie selten [A]); Upstream-Merge-Kosten bleiben klein,
wenn alles im Paket `zen/` lebt und nur `ZenFeedPlanner`/`ZenFeedProvider`/`ZenPraxisUi` und das Manifest berührt werden.

## 6. Testplan

### 6.1 Unit-Tests (JVM, `app/src/test`)
- `ZenRulesTest`: alle 200 Fälle aus `golden_cases.json` (keine Titel, als Test-Ressource committbar) + Feed-Deckel-Test aus
  `test_golden.py`.
- `TextBuilderTest`/`TokenizerTest`: neutrale, selbst formulierte Texte mit erwarteten IDs (im YoutubeManager erzeugt);
  Sonderfälle aus `make_bin.py` (Emoji, Vollbreite, leerer Text, 400 Wörter). **Nicht** `golden.json` (Privatsphäre, 2.3).
- Encoder-Test nur, wenn das Modell lokal liegt (`assumeTrue`), sonst übersprungen; CI ohne Modell bleibt grün.
- `ZenFeedPlannerTest` erweitern: Gewichtung (Aktuelles hinter Bildung trotz besserem Gruppenrang), belastend höchstens 1 von 15,
  nicht vor Platz 10, nie zweimal hintereinander, „nicht eingeordnet“ = Gewicht 1,0, Verhalten ohne Cache = heutiges Ergebnis
  (Regressionsschutz).
- Plausibilität: Schwellenlogik mit künstlichen Ergebnissen (15 Videos, Grenzen 30/60 %, „unklar“).

### 6.2 Gerät (Defy, Debug-Build)
- Laden, Zeit pro Video (Titel+Kanal und voll), Spitzen-Speicher (`dumpsys meminfo`), während Wiedergabe (Ruckeln? 2 Threads).
- Erstlauf mit echtem Feed: Dauer, `dumpsys batterystats` vor/nach, Temperatur.
- Netz: nach App-Start und nach Klassifikation **keine** Verbindungen außer YouTube/GitHub-Download (Logcat-Tag `ORTTelemetry`,
  `dumpsys netstats detail` je UID); Provider wirklich entfernt (`dumpsys package … | grep -i provider`).
- APK-Größe des Release-Builds; Kaltstartzeit vorher/nachher (kein ORT-Laden beim Start).
- Plausibilitätsprüfung mit einem echten Yoga-Kanal und einem Nachrichtenkanal; Drosselung 1/s im Log.

### 6.3 Stichprobe mit echten Abos (Stefan, als TODO-Liste statt Gate)
- Debug-Ansicht „Einordnung“ (Liste der letzten 100 Kandidaten mit Kategorie/Ton/Gewicht/Stufe) → Stefan markiert falsche
  Einordnungen; Ziel: Precision je Kategorie im eigenen Feed ≥ 80 %, sonst Schwellen anheben.
- 5 Praxis-, 5 Bildungs-, 5 Nachrichten-/Unterhaltungskanäle durch die Plausibilitätsprüfung schicken; Schwellen 30/60 %
  danach festlegen.
- Zwei Wochen Ausgaben: Fühlt sich die Gewichtung richtig an? Wie oft taucht Belastendes auf?

## 7. Arbeitspakete (PR-große Commits, Reihenfolge)

| AP | Inhalt | Aufwand | hängt ab von |
|---|---|---|---|
| 0 | **YoutubeManager:** Kurztext-Messung (Titel+Kanal gegen Wahrheit), optional Kurztext-Köpfe; neutrale Goldfälle für Tokenizer/Encoder; Modell-Release-Asset mit SHA-256 und `NOTICE` (MIT e5, XLM-R, ORT) | 0,5–1 Tag | – |
| 1 | `zen: classifier core` – Port nach `zen/classify/` (Tokenizer, TextBuilder inkl. Kategorie-Name→ID und HTML→Text, Heads, Rules), API-23-taugliche Base64/Code-Points, Unit-Tests (6.1), ktlint | 1 Tag | 0 (Goldfälle) |
| 2 | `zen: onnx runtime and model download` – ORT-Abhängigkeit, `overrideLibrary`, Provider entfernen + `setTelemetry(false)`, `abiFilters`, `useLegacyPackaging`, Proguard-Keeps, Download/Prüfsumme/Status in Einstellungen > Zen, Lizenzhinweise; APK-Größe messen | 1–1,5 Tage | 1, E1, E2 |
| 3 | `zen: classification cache and worker` – `ZenClassCache`, `ZenClassifier` (lazy Session, Executor, Thread-Wahl, Leerlauf-Schließen), `ZenClassifyWorker`, Stufe `D` aus `onStreamInfo`, ZenLog, Debug-Ansicht „Einordnung“ | 1 Tag | 2 |
| 4 | `zen: feed weighting by content` – `Candidate`-Felder, Gewicht im Score, Belastend-Regel in `select`, 3-s-Nachklassifikation, „Warum“-Zeile im Feed-Eintrag, Schalter mit Sperre, Tests | 1 Tag | 3 |
| 5 | `zen: plausibility check when marking practice` – Kanal/Playlist, gedrosselte Details, Dialog, Videoliste mit Einordnung, ZenLog | 1 Tag | 3 |
| 6 | `zen: classifier exceptions` – Ausnahmeliste über `ZenSettingsLock` (Lockerung) | 0,5 Tag | 4 |
| 7 | `zen: education without politics` – Bildung-Markieren mit Prüfung, Abspielprüfung in `onStreamInfo` | 0,5–1 Tag | 5, Bildung Phase 3 |
| – | Gerätetest 6.2, `rules.md`/`spec-*`-Nachzug, STATE | 0,5 Tag | je AP |

Summe ≈ 6–8 Tage. AP 1 ist auch ohne die anderen nützlich (prüfbar ohne Gerät). AP 4 und 5 sind unabhängig voneinander.

## 8. Abgleich mit `rules.md` (hier nicht geändert)

1. **Praxis/Zuordnung** („keine automatische Erkennung, nie Titel/Stichworte“): bleibt wahr. Ergänzen: „Die Inhalts-Erkennung
   warnt beim Markieren, entscheidet aber nicht.“
2. **Feed/Scoring**: Faktor „Inhaltsgewicht“ (Aktuelles 0,3/0,8, Zerstreuung 0,5, belastend × 0,5) und Belastend-Regel
   (≤ 10 %, nie hintereinander, nicht Platz 1–9) ergänzen; „ohne Kategorie = 1,0“.
3. **Feed/Kandidaten**: unverändert (der Klassifikator filtert nichts heraus).
4. **Zen-Einstellungen**: drei Schalter + Ausnahmeliste mit Richtung „aus/hinzufügen = Lockerung“.
5. **Bildung** (Spec, noch nicht in `rules.md`): „keine Bildungsrechte bei Aktuelles/Politik laut Inhalts-Erkennung; Ausnahme
   per Liste (Lockerung)“. Widerspricht der Bildung-Spec 3.2 („keine Titelheuristik“) nicht, weil nur eingeschränkt, nie
   gewährt wird.
6. **Grundregeln/Zen-Zustand**: neue Schlüssel `zs_class_exempt`, `class_*`-Schalter; Cache-Datenbank außerhalb von
   Export/Import/Auto-Backup.
7. **Protokoll**: `class_check`, `class_check_override`, `class_error`, `edu_denied`, `class_model_installed`.
8. **Externe Anfragen**: GitHub-Modell-Download als einzige neue Anfrage, nur auf Tipp.

## 9. Entscheidungen für Stefan (Empfehlung zuerst)

- **E1 Modell-Auslieferung:** (a) Opt-in-Download eines festen GitHub-Release-Assets mit SHA-256 **[Empfehlung]**; (b) im APK;
  (c) Git LFS. – (a) hält Updates klein und das Repo sauber; ohne Modell läuft alles wie heute.
- **E2 ABI:** (a) nur `arm64-v8a`, ein APK **[Empfehlung]**; (b) ABI-Splits arm64 + armv7 (mehr CI-Arbeit); (c) universal
  (+140 MB). – 32-bit-Geräte könnten ZenPipe mit (a) nicht mehr installieren.
- **E3 Feed-Daten:** (a) Feed nur Titel+Kanal, Details nur beim Öffnen und bei der Prüfung, plus Kurztext-Köpfe im
  YoutubeManager **[Empfehlung]**; (b) Details für alle Kandidaten nachladen (Rate-Limit-Risiko).
- **E4 Videos ohne Kategorie im Feed:** (a) bleiben, Gewicht 1,0 **[Empfehlung]**; (b) ausblenden (Bundle-Verhalten).
- **E5 Praxis beim Abspielen prüfen?** (a) nein, nur Warnung beim Markieren **[Empfehlung]**; (b) ja, Praxis-Kanal-Videos, die
  nach Unterhaltung/Aktuelles aussehen, verlieren die Ausnahme (ändert eine umgesetzte Regel).
- **E6 Aktuelles-Deckel je Ausgabe:** (a) nur Gewicht, kein Deckel **[Empfehlung]**, erst nach zwei Wochen Erfahrung
  entscheiden; (b) höchstens 3 von 15.
- **E7 ONNX Runtime:** (a) Maven-AAR mit entferntem Telemetrie-Provider und Netzprüfung am Gerät **[Empfehlung]**; (b) eigener
  Minimal-Build aus Quellen (kleiner, F-Droid-fähig, 2–4 Tage); (c) reiner Kotlin-Encoder.
- **E8 Schwellen der Plausibilitätsprüfung:** (a) 30/60 % als Start, nach Stichprobe 6.3 nachziehen **[Empfehlung]**.

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

### 10.3 Abweichungen von dieser Spec
1. Modell als **ein** Zip-Asset statt Einzeldateien; `vocab_map.json` bleibt JSON (eigenes Base64), `yt_categories.json`
   ist Code-Konstante.
2. ORT nur aus Maven Central (Gradle bekam von `dl.google.com` per HTTP/1.1 Timeouts; zugleich Lieferketten-Schutz).
3. CI-Emulator API 23 auf x86_64 (Debug-APK hat kein 32-bit-x86 mehr).
4. Cache mit `SQLiteOpenHelper` statt Room (keine Migrationen, eine Tabelle).
5. Prüfansicht in **allen** Builds (Stichprobe 6.3 läuft mit Stefans echten Abos), Selbsttest nur Debug.
6. Löschen des Modells ist derzeit frei (es gibt noch keine Schalter, 4.7); mit AP 4 wird es zur Lockerung, solange
   ein Schalter aktiv ist.
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

