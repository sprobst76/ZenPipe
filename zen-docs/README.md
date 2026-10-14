# ZenPipe-Wiki: Einstieg und Index

**Worum geht es:** Startseite der Projektdokumentation für Menschen und Coding-Agenten: Konzept in Kürze, Leitplanken, Karte
aller Dokumente, Lesepfade für typische Aufgaben, Konventionen und offene Dokumentationsschulden.
**Wann nachlesen:** Vor jeder Arbeit am Projekt, wenn unklar ist, wo eine Regel, Entscheidung oder ein Ablauf steht.

## Konzept in fünf Sätzen

ZenPipe ist ein Fork von NewPipe (Android, Kotlin/Java, GPLv3) für bewusstes Schauen statt endlosem Feed. Nichts startet von
selbst: kein Autoplay, keine Empfehlungen, keine Kommentare, keine Shorts, und am Ende jedes Videos hält der Player an. Der Feed
ist eine endliche **Ausgabe** (15 Videos, frühestens 60 min nach der letzten, höchstens 30 verschiedene pro Zen-Tag) mit
lokalem, erklärbarem Scoring, optional gewichtet durch einen lokalen Inhalts-Klassifikator. Wer ein Video nach mindestens 5 min
abbricht, bekommt eine kurze **Pflichtpause**; Lockerungen der Zen-Einstellungen wirken erst nach einer **24-h-Sperre**, strenger
stellen sofort. Ausnahmen gibt es nur für vom Nutzer gepflegte **Praxis**-Kanäle/-Playlists (Yoga, Meditation, Einschlafhilfen).
Ausführlich: [ZenPipe-Konzept_v0.1.md](ZenPipe-Konzept_v0.1.md) (historisch), Begriffe: [glossary.md](glossary.md).

## Leitplanken

| Leitplanke | Bedeutung | Quelle |
|---|---|---|
| Fork als kleiner Patch-Satz | Eigener Code im Paket `org.schabi.newpipe.zen`, in Upstream-Klassen nur kurze Hooks (Kommentar `ZenPipe:`/`zen:`); Upstream regelmäßig einspielen (`dev` folgt Upstream, `zen` trägt die Patches) | [../AGENTS.md](../AGENTS.md), [architecture.md](architecture.md) |
| NewPipeExtractor unverändert | Keine Änderungen am Extractor; Datenlücken (z. B. fehlende Kategorie im Feed) werden in der App gelöst | [../AGENTS.md](../AGENTS.md), Konzept §4 |
| GPLv3 | Fork bleibt Open Source, §5a-Vermerk (README, Über/Lizenz), §6 Quell-Link je Release | [release.md](release.md) „GPL notices“ |
| Keine Anfragen an Dritte | Gegenstellen sind nur YouTube (über den Extractor, wie beim Öffnen eines Videos) und GitHub (Crash-Report-Ziel, Releases, opt-in Modell-Download auf Tipp). Kein Update-Check, keine NewPipe-Server, ONNX-Telemetrie entfernt | [rules.md](rules.md) „Externe Anfrage“, [release.md](release.md) „No NewPipe backend contact“ |
| Verteilung | GitHub-Releases, eigenes F-Droid-Repo, Obtainium; nicht Play Store | [release.md](release.md) |
| Nie Autoplay | Weiterspielen nur als Praxis-Kette; keine Sperr-Umgehung in Release-Code | [rules.md](rules.md) „Grundregeln“, [decisions.md](decisions.md) |

## Was steht wo

Verbindlichkeit: **verbindlich** = gilt, bei Widerspruch gewinnt [rules.md](rules.md); **Spec** = Begründung, Entwurf und
Umsetzungsprotokoll (bei Widerspruch gilt rules.md); **Status** = Fortschritt/Protokoll; **Recherche** = Hintergrund, nicht
bindend; **Archiv** = Momentaufnahme.

| Dokument | Inhalt | Art |
|---|---|---|
| [rules.md](rules.md) | Alle Regeln, Werte, Code-Stellen, Status (Single Source of Truth) | verbindlich |
| [decisions.md](decisions.md) | Entscheidungsprotokoll (warum, Alternativen, Belege) | verbindlich (Entscheidungen), Werte in rules.md |
| [architecture.md](architecture.md) | Karte der Zen-Pakete, Datenflüsse, Upstream-Hooks | Orientierung (aus dem Code) |
| [glossary.md](glossary.md) | Begriffe (Ausgabe, Zen-Tag, Stufe T/D …) | Orientierung |
| [release.md](release.md) | Paketnamen, Versionsschema, Signierung, CI, F-Droid-Repo, Obtainium (Englisch) | verbindlich für den Release-Ablauf |
| [spec-feed-mit-ende.md](spec-feed-mit-ende.md) | Feed als Ausgabe | Spec (teils überholt, markiert) |
| [spec-pause-nach-abbruch.md](spec-pause-nach-abbruch.md) | Pflichtpause, Musik/Podcast-Heuristik, Pausenspruch | Spec |
| [spec-einstellungs-sperre.md](spec-einstellungs-sperre.md) | 24-h-Sperre, Ersteinrichtung, Rückgängig | Spec |
| [spec-praxis.md](spec-praxis.md) | Praxis-Kanäle/-Playlists, Kette, Sleep-Timer | Spec |
| [spec-design.md](spec-design.md) | Palette „Salbei“, WCAG, Umsetzung | Spec |
| [spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md) | Wiedergabezeit Bild/Ton, Ruhezeit-Anrechnung (gebaut); Bildung, Tageslimit (nur Spec) | Spec |
| [spec-klassifikator-integration.md](spec-klassifikator-integration.md) | Lokaler Inhalts-Klassifikator: Analyse, Vorschlag, Entscheidungen E1–E8 (Abschnitte 0–9) | Spec |
| [klassifikator-umsetzungsprotokoll.md](klassifikator-umsetzungsprotokoll.md) | Klassifikator-Umsetzung je Etappe: Bausteine, Messwerte, Abweichungen, Offenes (Abschnitte 10–14) | Status (Protokoll) |
| [spec-verwandte-kanaele.md](spec-verwandte-kanaele.md) | Entdecken verwandter Kanäle | Spec, nicht gebaut |
| [sprueche.md](sprueche.md) | Pausensprüche DE/EN (Quelle für `gen_sprueche.py`) | verbindlich (Inhalt) |
| [gen_sprueche.py](gen_sprueche.py) | erzeugt `res/values{,-de}/zen_quotes.xml` aus sprueche.md | Werkzeug |
| [tools/](tools/) | `gen_icons.py` (Icons/Splash), `make_classifier_bundle.py` (Modell-Zip), F-Droid-Repo-Schlüssel/Secrets-Skripte | Werkzeug |
| [research-achtsamer-konsum.md](research-achtsamer-konsum.md) | Evidenz zu Selbstkontrolle, Umgehungsschutz | Recherche |
| [research-fdroid-deployment.md](research-fdroid-deployment.md) | Verteilungswege, Signatur | Recherche |
| [research-name-und-offene-fragen.md](research-name-und-offene-fragen.md) | Name/Marke, GPL/ToS, Signing | Recherche |
| [research-einschlaferkennung.md](research-einschlaferkennung.md) | Sleep-Timer + Einschlaferkennung | Recherche |
| [ZenPipe-Konzept_v0.1.md](ZenPipe-Konzept_v0.1.md) | Ursprungsidee | historisch |
| [archive/consistency-review.md](archive/consistency-review.md) | Konsistenz-Review 2026-10-12 (W/U/S-Befunde, F1–F9) | Archiv |
| [archive/feature-audit.md](archive/feature-audit.md) | NewPipe-Bestand + Ideen-Backlog Z1–Z22 | Archiv |
| [../.planning/STATE.md](../.planning/STATE.md) | Umsetzungsprotokoll je Etappe, Gerätebefunde, Gerätetest-Tricks, Offenes | Status |
| [../.planning/PROJECT.md](../.planning/PROJECT.md) | Kurzbild des Projekts (ohne Zahlen, verweist auf rules.md) | Status |
| `../.planning/HANDOFF.json` | Maschinenlesbarer Wiedereinstieg (`next_action`, offene Human-Actions; gelesen von `resume-radar`/`pause-project`) | Status |
| [../AGENTS.md](../AGENTS.md) | Arbeitsregeln für Agenten, Operations, Gerätetest-Regeln, Git-Workflow (`CLAUDE.md` importiert nur `@AGENTS.md`) | verbindlich (Arbeitsweise) |
| [../README.md](../README.md) | Öffentliche Projektseite (Englisch), Installation, Fingerprint | öffentlich |

## Lesepfade

| Ich will … | Lesen in dieser Reihenfolge |
|---|---|
| eine Regel/einen Wert ändern | [rules.md](rules.md) (Zeile + Code-Spalte) → [decisions.md](decisions.md) (warum so) → passende Spec → Code laut [architecture.md](architecture.md); Lockerung oder Verschärfung? → Abschnitt „Zen-Einstellungen“ in rules.md |
| ein neues Feature bauen | [decisions.md](decisions.md) (nichts Entschiedenes umwerfen) → [architecture.md](architecture.md) (Hook-Stellen, `ZenFeatures`) → Spec schreiben/ergänzen → rules.md-Zeilen im selben Commit |
| eine Upstream-Funktion abschalten | rules.md „Funktionen“ + `zen/ZenFeatures.kt` (alle Einstiege dort prüfen lassen) |
| einen Release bauen | [release.md](release.md) „Release checklist“ → Skill `/android-release` + `android-release-preflight.sh` → [../AGENTS.md](../AGENTS.md) „Operations“ |
| auf dem Gerät testen | [../AGENTS.md](../AGENTS.md) „Operations“ (Defy, PIN-Sperre, `run-as`-Tricks) → Abschnitte „Gerätetest-Tricks“ in [../.planning/STATE.md](../.planning/STATE.md) → Zen-Zustand danach zurücksetzen |
| den Klassifikator verstehen | [glossary.md](glossary.md) (Stufe T/D, Warum-Zeile) → rules.md „Inhalts-Erkennung“ → [architecture.md](architecture.md) „Klassifikator“ → [spec-klassifikator-integration.md](spec-klassifikator-integration.md) Abschnitte 0, 4, 9 → [klassifikator-umsetzungsprotokoll.md](klassifikator-umsetzungsprotokoll.md) 10–14 |
| Pause/Zeitmessung verstehen | rules.md „Pflichtpause“ + „Wiedergabezeit“ → [spec-pause-nach-abbruch.md](spec-pause-nach-abbruch.md) → [spec-bildung-und-hintergrundzeit.md](spec-bildung-und-hintergrundzeit.md) Abschnitte 2 und 10 |
| den aktuellen Stand wissen | [../.planning/STATE.md](../.planning/STATE.md) (unterster Abschnitt zuerst) + `git log --oneline zen` |
| Upstream einspielen | [../AGENTS.md](../AGENTS.md) „Git-Workflow“, [release.md](release.md) (modify/delete-Konflikte bei fastlane/.github) |

## Konventionen

- **rules.md ist Single Source of Truth.** Specs begründen und protokollieren, rules.md entscheidet. Wer eine Regel ändert, ändert
  rules.md im selben Commit wie den Code; Specs bekommen höchstens einen Verweis oder Umsetzungsabschnitt.
- **Ein Commit pro Änderung**, Präfix `zen:`, Englisch (Code, Commits, technische Doku). Diese Wiki-Dokumente sind Deutsch.
- **Spec vs. Regel:** Eine Spec beschreibt Vorschlag, Gründe, Alternativen und (danach) „Umsetzung“ mit Messwerten und
  Abweichungen; eine Regel ist eine Zeile in rules.md mit Wert, Code-Stelle und Status (umgesetzt / Spec / offen).
- **Entscheidungen** (Nutzerentscheidungen Stefans, Auflösungen von Widersprüchen) kommen als Eintrag in [decisions.md](decisions.md).
- **Überholte Stellen** in Specs werden nicht umgeschrieben, sondern mit `[überholt: …]` markiert; Momentaufnahmen wandern nach
  [archive/](archive/).
- Zen-Zustand liegt in `zen_state` (nicht Export/Backup); neue Schlüssel in rules.md nennen.

## Offene Dokumentationsschulden

Stand 2026-10-14 (nach dem Nachzug von rules.md, Klassifikator-Spec, STATE/PROJECT/HANDOFF, decisions.md und Projekt-README).
Erledigt und daher entfernt: Code-Spalte und Kopf von rules.md, Teilung der Klassifikator-Spec, STATE-Kopf, PROJECT/HANDOFF,
F-Droid-Repo im Projekt-README (Repo live, Fingerprint in README und [release.md](release.md)).

1. **release.yml / UTF-8-Locale** (Bewertung, nicht geändert, weil Release-Workflow): Die Zeilen `LC_ALL: C.UTF-8`/`LANG: C.UTF-8`
   im Signierschritt (`b14341686`) sollten Nicht-ASCII-Keystore-Passwörter ermöglichen; laut Projekt-Memory half das nicht, das
   Passwort wurde auf ASCII rotiert, [release.md](release.md) verlangt „Passwords ASCII only“. Die Zeilen sind damit wirkungslos
   und ihr Kommentar irreführend. **Vorschlag:** beim nächsten ohnehin nötigen Workflow-Commit entfernen (oder Kommentar auf
   „harmlos, Passwörter sind ASCII“ ändern); nicht während eines Releases. Entscheidung Stefan.
2. **Code-Kommentare** verweisen noch auf die alte Gesamtdatei: `ZenLanguageTest.kt` („spec-klassifikator-integration.md, section
   14“, jetzt [klassifikator-umsetzungsprotokoll.md](klassifikator-umsetzungsprotokoll.md) 14). Die Spec behält einen Wegweiser „10–14“, der Verweis bleibt also auflösbar; beim
   nächsten Code-Commit in dieser Datei mitziehen. (`UnigramTokenizer.kt` meint Format „ZTK1“, Abschnitt 1, bleibt richtig.)
3. **spec-bildung-und-hintergrundzeit.md**: Tageslimit heißt in 1.3/2.4 „Phase 3“, in Abschnitt 8 „Phase 4“ (markiert). Die
   Annahmen 10.3 (Ruhezeit wirkt sofort, Ton halb, 90/180/60) sind von Stefan noch zu bestätigen (decisions.md D29, D57).
4. **spec-design.md** verweist auf `design-preview.html`, die nicht im Repo liegt (nur lokal erzeugt).
5. **rules.md, Status „am Gerät nicht geprüft“**: Bildschirm aus im Video-Player, Download-Dialog mit Schalter-Stand, „Keep Android
   Open“ im Release-Paket – keine Doku-Fehler, sondern offene Gerätetests (STATE, Protokoll 14.6).
6. Offene Sachfragen, die in mehreren Dokumenten auftauchen (keine Doku-Fehler, aber noch nicht entschieden): W14 (keine neue
   Ausgabe während Pause/Nachtruhe), Tageslimit F2 (Wert), Bildung/AP 7, Entdecken, Nachtruhe, Nachladen im Mobilfunk,
   Notfallschlüssel (bewusst nicht).
