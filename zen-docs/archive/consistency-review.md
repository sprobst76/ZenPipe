> **ARCHIV – Stand 2026-10-12, nicht mehr verbindlich; maßgeblich ist [rules.md](../rules.md).** Die Entscheidungen F1–F9
> (Abschnitt am Ende) sind in [decisions.md](../decisions.md) übernommen. Zeilenangaben (`Datei:Zeile`) beziehen sich auf den
> damaligen Stand; Pfade ohne `archive/` meinen `zen-docs/`.

# ZenPipe – Konsistenz-Review der Regeln und Entscheidungen

**Stand:** 12.10.2026 · Branch `zen`, HEAD `64ac2d489` plus **uncommittete Feed-Arbeit eines parallelen Agenten** (`ZenFeedPlanner.kt`, `ZenFeedState.kt`, `ZenFeedProvider.kt`, `ZenWatchLater.kt`, `ZenGroupRank.kt`, Diffs in `FeedViewModel.kt`, `FeedFragment.kt`, `FeedGroupDAO.kt`, `strings.xml`). Diese Arbeit ist ein Zwischenstand. Befunde dazu sind als **[WIP]** markiert und können sich schon erledigt haben.
**Nachtrag:** Neue Nutzerregel „24-h-Sperre für Zen-Einstellungen“ (`spec-einstellungs-sperre.md`, Commit `36266f156`) ist eingearbeitet: 1.8, U-19/U-20, U11–U13, Regel-Tabelle, 4.2 Nr. 4–5, F7–F9.
**Gelesen:** alle Dateien in `zen-docs/`, `.planning/PROJECT.md`, `.planning/STATE.md`, `CLAUDE.md`, `git log` (inkl. Historie von `spec-feed-mit-ende.md`), Code-Stichproben (nur gelesen, nichts gebaut, nichts geändert).

**Legende:** **[Beleg]** = im Dokument oder Code nachgesehen (Datei:Zeile) · **[Vermutung]** = Schlussfolgerung, nicht am Gerät oder im Code bis zum Ende verfolgt · **[Empfehlung]** = Vorschlag dieses Reviews.

**Gesetzte Nutzer-Entscheidungen (Prüfmaßstab):** Autoplay dauerhaft aus · Shorts nirgends · Feed = Ausgabe mit 15 verschiedenen Videos pro Stunde, Tages-Deckel 30 Videos, lokales erklärbares Scoring ohne YouTube-Empfehlungen, Fokus auf längeren Videos · Pflichtpause nach Abbruch bei ≥ 5 min Anschauzeit (anteilig; Musik ausgenommen, Podcasts inklusive, Pausieren ist kein Abbruch) · Entdecken verwandter Kanäle · Absichtsabfrage bei Start, Links und Rückkehr aus dem Hintergrund.

---

## 1. Widersprüche und Lücken zwischen Regeln und Specs

### 1.1 Feed: Stunde, Tag und Deckel

| # | Befund | Beleg | Bewertung / Vorschlag |
|---|---|---|---|
| W1 | **Tages-Deckel zählt *angebotene*, nicht *geschaute* Videos.** Jede neu erzeugte Ausgabe erhöht `feed_day_count` um ihre Größe. Bei 15/h und Deckel 30 ist der Tag nach **zwei** Ausgaben vorbei, egal ob man etwas angesehen hat. Faktisch bedeutet das „2 Ausgaben pro Tag“, nicht „15 pro Stunde“. | [Beleg][WIP] `ZenFeedState.kt:135–152` (`remaining = DAILY_CAP - dayCount()`, `dayCount + e.picks.size`) | **Größter inhaltlicher Widerspruch.** Die Spec (`spec-feed-mit-ende.md:10`) lässt offen, was „30 Videos/Tag“ zählt. Entscheidung nötig, siehe 4.3 (F1). Empfehlung: Der Deckel zählt **verschiedene angebotene Videos pro Tag** (ein Video, das in zwei Ausgaben auftaucht, zählt einmal), und eine Ausgabe wird erst erzeugt, wenn der Nutzer den Feed **nach** der Absichtsabfrage tatsächlich öffnet. |
| W2 | **Doppelzählung:** Nicht erledigte Videos der vorigen Stunde sind wieder Kandidaten („neu gewichtet“) und werden beim erneuten Auftauchen noch einmal auf den Deckel gezählt. | [Beleg][WIP] `ZenFeedState.kt:141–152` (Filter nur auf `handled`) und `spec-feed-mit-ende.md:10` | Folgt aus W1. Fix: bereits gezählte UIDs pro Zen-Tag merken. |
| W3 | **Ausgabe entsteht beim App-Start, vor der Absichtsabfrage.** Der Feed ist Startseite, und `FeedViewModel` ruft `zenFeed.compute()` bei jedem `IdleEvent` auf. „Doch nicht“ in der Absichtsabfrage verbraucht trotzdem bis zu 15 von 30. | [Beleg] Startseite = Feed (`STATE.md:7`). [Beleg][WIP] `FeedViewModel.kt` Diff (`zenFeed.compute(groupId)` im `map`). [Vermutung] Reihenfolge Dialog/Feed-Laden nicht am Gerät geprüft. | Ausgabe lazy erst beim ersten Scrollen oder Tippen bzw. nach „Weiter“ erzeugen. Alternativ zählt erst ein angetipptes Video. |
| W4 | **„Neue Ausgabe nur zum Tageswechsel“** (Umgehungsschutz) widerspricht „zur vollen Stunde“ (Regel 1 und 5). Das ist ein Rest der Vorversion („Ausgabe morgen ab 4:00“). | [Beleg] `spec-feed-mit-ende.md:32` vs. `:10`, `:20`; Historie via `git log -p` | Zeile 32 ändern in: „Neue Ausgabe frühestens zur nächsten vollen Stunde, solange der Tages-Deckel nicht erreicht ist.“ |
| W5 | **„23 weitere verfallen“ ist sachlich falsch.** `leftover` = berechtigte, aber nicht gewählte Videos. Diese verfallen nicht, sie sind nächste Stunde wieder Kandidaten (Regel 1 „neu gewichtet“). Regel 5 sagt zusätzlich „fallen weg (Zeitfenster 7 Tage)“. Drei Formulierungen für denselben Sachverhalt. | [Beleg] `spec-feed-mit-ende.md:10, :20, :22`; [WIP] `strings.xml` `zen_feed_leftover` „…verfallen“, `ZenFeedState.kt:150` | Einheitlich: „N weitere warten. Was älter als 7 Tage wird, fällt heraus.“ Bei erreichtem Deckel: „N weitere bleiben heute liegen.“ |
| W6 | **„Nächste Ausgabe morgen um 04:00“** ist zwischen 0:00 und 3:59 falsch, denn dann ist es „heute 04:00“. | [Beleg][WIP] `strings.xml` `zen_feed_next_tomorrow`, `ZenFeedState.kt:126` (`nextDayStart`) | Text ohne „morgen“: „Nächste Ausgabe ab 04:00.“ |
| W7 | **Konfigurierbare Spanne 10–25/h vs. Deckel 30:** Bei 25/h hätte die zweite Ausgabe nur noch 5 Videos. Wie Spanne und Deckel zusammenwirken, ist nicht beschrieben. Ein Time-Lock („Lockern ab nächstem Tag“) existiert noch nicht. | [Beleg] `spec-feed-mit-ende.md:10`; Time-Lock = Z6, nicht umgesetzt (`feature-audit.md:235`) | Regel-Tabelle (4.1): Stunden-Budget ≤ Deckel/2. Konfigurierbarkeit erst mit dem Time-Lock freischalten, bis dahin Konstanten. |
| W8 | **Nachtstunden:** Ausgaben um 0–3 Uhr zählen auf den Vortag (4:00-Grenze). Eine „Nachtruhe“ (Z8, 22:30–6:00) ist angedacht, aber mit den stündlichen Ausgaben nicht verzahnt. | [Beleg] `ZenDay.kt:8–11`; `feature-audit.md:237` | Festlegen: In der Nachtruhe keine neue Ausgabe (Abschlusskarte „Nachtruhe bis 6:00“). |

### 1.2 Stück vs. Minuten, Längenfokus

| # | Befund | Beleg | Bewertung / Vorschlag |
|---|---|---|---|
| W9 | **Mindestlänge und Längenbonus stehen nur im Code, nicht in der Spec.** Die Spec kennt weder `MIN_DURATION 5 min` noch Bonus ab 15 bzw. 30 min. Die Nutzer-Entscheidung „Fokus auf längeren Videos“ ist nirgends dokumentiert. | [Beleg][WIP] `ZenFeedPlanner.kt:19–27`; `spec-feed-mit-ende.md:11–17` (kein Wort zu Länge) | Spec Regel 2 ergänzen (siehe 4.2). |
| W10 | **Stück-Budget wird durch den Längenfokus zeitlich bedeutungslos.** 15 Videos mit Längenbonus, also typischerweise 15–60 min lang, ergeben pro Stunde ein Angebot von mehreren Stunden Sehzeit. Der Deckel von 30 Videos entspricht bei 20 min Schnitt 10 h. Das alte „Tageslimit“ (Konzept §3, Z7 „45 min Video“) ist minutenbasiert und weder verworfen noch übernommen. | [Beleg] `ZenPipe-Konzept_v0.1.md:26`; `feature-audit.md:236`; `ZenFeedPlanner.kt:21–27` | Klarstellen: **Feed-Budget (Stück) = Angebotsgrenze**, **Tageslimit (Minuten) = Konsumgrenze**, zwei getrennte Regeln. Das Minutenlimit ist offen bzw. Phase 2 und braucht eine Nutzer-Entscheidung (F2). Ohne Minutenlimit begrenzt der Feed nur die Auswahl, nicht die Zeit. |
| W11 | **„Budget“ ist doppelt belegt:** Feed-Budget (Videos/h), Später-Budget (Einträge), Z7-Tagesbudget (Minuten). | [Beleg] `spec-feed-mit-ende.md:10, :21`; `feature-audit.md:236, :240` | Begriffe: „Ausgabe-Größe“, „Tages-Deckel“, „Später-Platz“, „Tageslimit (min)“. |

### 1.3 Mindestlänge 5 min vs. Pflichtpause-Schwelle 5 min

| # | Befund | Beleg | Bewertung / Vorschlag |
|---|---|---|---|
| W12 | **Jedes Feed-Video ist pausenfähig.** Weil alle Feed-Videos ≥ 5 min dauern, kann jedes nach 5 min Anschauzeit eine Pause auslösen. Bei einem 5:20-min-Video, das bei 5:05 beendet wird (Abspann, Endkarte), greift die Pflichtpause, obwohl das Video inhaltlich zu Ende ist. „Zu Ende geschaut“ ist nicht definiert. | [Beleg] `spec-pause-nach-abbruch.md:7–9`; `ZenFeedPlanner.kt:20` | Definition ergänzen: **„zu Ende“ = ≥ 90 % oder Rest ≤ 60 s** (gilt auch für die „gesehen“-Logik). Der Zufall gleicher Zahlen ist unkritisch, solange beide Werte getrennte Konstanten bleiben. |
| W13 | **„Wiedergabezeit“ ist nicht genau definiert:** Zählt Vorspulen mit, oder Wiedergabe mit 2×? Auch Fortsetzen über mehrere Sitzungen ist offen (gestern 4 min, heute 2 min, dann Abbruch). | [Beleg] `spec-pause-nach-abbruch.md:8` („reine Wiedergabezeit dieses Videos“) | Festlegen: echte Abspielzeit (Wanduhr während „playing“) **pro Wiedergabe-Sitzung** des Videos. Geschwindigkeit egal, Vorspulen zählt nicht. |

### 1.4 Pause vs. Feed-Ausgabe vs. Absichtsabfrage vs. Zen-Tag

| # | Befund | Beleg | Bewertung / Vorschlag |
|---|---|---|---|
| W14 | **Pause und Ausgabe:** Während der Pause ist Browsen erlaubt. Wird in dieser Zeit eine neue Stunde erreicht, entsteht eine Ausgabe, die auf den Deckel zählt, ohne dass man etwas ansehen kann. | [Beleg] `spec-pause-nach-abbruch.md:15–16`; [WIP] Ausgabe entsteht beim Öffnen (`FeedFragment` `zenRefresh()` in `onResume`) | Während der Pause keine neue Ausgabe erzeugen, sondern die alte anzeigen. Oder Zählung nach W1-Empfehlung. |
| W15 | **Pause und Absichtsabfrage:** Kehrt man nach > 5 min Pause aus dem Hintergrund zurück, kommen der Pausenbildschirm und die Absichtsabfrage. Die Reihenfolge ist offen. | [Beleg] `ZenIntentPrompt.java:22, :43–50`; Pause-Spec nennt die Abfrage nicht | Regel: Bei laufender Pause **nur** der Pausenbildschirm (er enthält die Absichtsfrage als Text). Nach Ablauf genau eine Abfrage. |
| W16 | **Pause über die Tagesgrenze:** Eine Pause um 3:58 läuft über 4:00 hinaus. Nirgends steht, dass der Tageswechsel sie **nicht** beendet. | [Beleg] fehlt in `spec-pause-nach-abbruch.md` | Ergänzen: „Pause endet nur durch Zeitablauf, nie durch Tages- oder Stundenwechsel.“ |
| W17 | **Pause und „neues Video starten“:** Startet man Video B, während A nach ≥ 5 min läuft (Queue, Link, Feed), ist das ein Abbruch von A. Dann müsste B sofort gesperrt werden. Die Reihenfolge ist nicht definiert. | [Beleg] fehlt in Spec | Regel: Ein Wechsel zu einem anderen Video zählt als Abbruch. B startet nicht, stattdessen erscheint der Pausenbildschirm. Ausnahme: B steht in einer vom Nutzer gebauten Queue bzw. Playlist und A ist „zu Ende“ (W12). |
| W18 | **Hintergrund-Wechsel:** Die Spec nennt „App-Wechsel länger als X Sek.“ als Abbruch. Der NewPipe-Default „Beim Verlassen minimieren → Hintergrund“ lässt die Wiedergabe beim App-Wechsel aber als Audio weiterlaufen. Das ist **kein** Abbruch. X ist nicht festgelegt. | [Beleg] `spec-pause-nach-abbruch.md:7–8`; `feature-audit.md:81` | Abbruch nur bei Ende der Wiedergabe (Player geschlossen, Benachrichtigung weggewischt, Stopp, Wechsel des Videos). Der App-Wechsel selbst ist kein Kriterium. |

### 1.5 Podcast/Musik-Heuristik vs. Mindestlänge/Längenbonus

| # | Befund | Beleg | Bewertung / Vorschlag |
|---|---|---|---|
| W19 | **Musik fällt durch die Mindestlänge ohnehin aus dem Feed** (meist 3–4 min). Lange Mixes (≥ 30 min) bekommen dagegen den **höchsten** Längenbonus und landen oben in der Ausgabe. Das ist nicht beabsichtigt. | [Beleg][WIP] `ZenFeedPlanner.kt:55–58` | Musik (Heuristik Stufe 1) bekommt keinen Längenbonus. Oder Musik-Kanäle (Topic-Kanäle) gar nicht im Feed (F3). |
| W20 | **Podcast-Heuristik „≥ 20 min im Audio-Modus“ überschneidet sich mit dem Längenbonus ab 15/30 min.** Das ist kein Widerspruch (Podcasts sind erwünscht lang). Folge: Fast jede Ausgabe ist podcastlastig, und jeder Podcast-Abbruch nach 5 min kostet eine Pause, im Hintergrund-Player besonders oft (Ausstieg beim Autofahren, Ankunft). | [Beleg] `spec-pause-nach-abbruch.md:11, :26`; `ZenFeedPlanner.kt:21–22` | UX-Falle, siehe 3 (U6). Vorschlag: Bei Podcasts zählt „Pausieren und später fortsetzen“ ausdrücklich als nicht abgebrochen, solange die Position gespeichert ist und innerhalb von 24 h fortgesetzt wird. |
| W21 | **Kategorie-Daten fehlen in der Feed-DB.** `StreamEntity` speichert keine YouTube-Kategorie. Die Musik-Erkennung geht also nur zur Wiedergabezeit (StreamInfo), nicht beim Feed-Scoring. | [Vermutung] gestützt durch `feature-audit.md:192` („`StreamEntity` hat kein Shorts-Feld“); Kandidaten-Mapping `ZenFeedProvider.kt:256–272` nutzt nur Dauer/Typ | Heuristik für Pause (Wiedergabezeit) und Feed (DB) getrennt spezifizieren. Für den Feed reicht die Kanal-Ebene (Topic-Kanal/Feed-Gruppe „Musik“). |
| W22 | **Feature-Audit Z12** sagt: „Musik/Podcast im Audio-Modus zählt gar nicht aufs Limit“. Die Pause-Spec sagt: Podcasts lösen Pausen aus. Die Haltung zu Podcasts ist also zwischen den Dokumenten uneinheitlich. | [Beleg] `feature-audit.md:241` vs. `spec-pause-nach-abbruch.md:11` | Z12 an die Nutzer-Entscheidung anpassen: Podcasts zählen wie Video. Für das Minutenlimit offen (F2). |

### 1.6 Später-Liste vs. Tages-Deckel

| # | Befund | Beleg | Bewertung / Vorschlag |
|---|---|---|---|
| W23 | **Max-Wert uneinheitlich:** Spec 10, Audit Z11 „z. B. 15“. Der Code nutzt 10. | [Beleg] `spec-feed-mit-ende.md:21`; `feature-audit.md:240`; [WIP] `ZenWatchLater.kt:11` | Auf 10 vereinheitlichen, Z11 anpassen. Ablauf nach 30 Tagen (Z11) ist in der Spec nicht übernommen. Entscheiden (F4). |
| W24 | **Später ist ein Deckel-Ventil.** Gemerkte Videos sind aus der Ausgabe „handled“ und jederzeit über Playlists abspielbar, auch nach erreichtem Deckel. Gewollt ist das als Escape-Ventil (Research §3.1), in der Spec steht es aber nicht. | [Beleg] `research-achtsamer-konsum.md:139`; [WIP] `FeedFragment` `saveForLater` → `zenHandled(..., "later")` | Ausdrücklich festhalten: „Später-Liste und eigene Playlists sind vom Deckel ausgenommen, Pflichtpause gilt dort trotzdem.“ |
| W25 | **Das Später-Limit ist umgehbar:** „Weitere Optionen…“ öffnet das normale NewPipe-Menü mit „Zur Playlist hinzufügen“. Dort geht es in **beliebige** eigene Playlists ohne Limit, auch in die Später-Playlist selbst (die `MAX_ITEMS`-Prüfung steckt nur in `ZenWatchLater.add`). | [Beleg][WIP] `FeedFragment.kt` Diff (`zen_more_options` → `showInfoItemDialog`), `ZenWatchLater.kt:9–27` | Realistisch akzeptieren (eigene Playlists = bewusste Kuratierung). Höchstens „Zur Playlist hinzufügen“ im Feed-Menü ausblenden. Siehe 2. |

### 1.7 Kanalseiten-/Suchlimits vs. Entdecken

| # | Befund | Beleg | Bewertung / Vorschlag |
|---|---|---|---|
| W26 | **Kanalseite 10–15 Videos, Suche 2 Seiten** sind spezifiziert, aber **nicht umgesetzt**. Kanalseiten und Suche sind weiter paginiert. Damit ist der Feed-Deckel trivial umgehbar: Kanal öffnen, scrollen. | [Beleg] `spec-feed-mit-ende.md:25–26`; nicht in `STATE.md` als erledigt; kein Zen-Hook in `ChannelTabFragment`/`SearchFragment` außer Shorts-Filter | Priorität nach dem Feed. Außerdem schickt die Ende-Karte der Kanalseite in die Suche („Weiter geht's in der Suche“), die selbst begrenzt sein soll. Besser: „Ältere Videos: über die Suche mit Kanalnamen“ oder keine Weiterleitung. |
| W27 | **Entdecken → „Ansehen“** öffnet die Kanalseite. Mit Kanalseiten-Limit sind das 10–15 Videos ohne Deckel-Bezug. Unkritisch, weil höchstens 3 Vorschläge pro Woche. | [Beleg] `spec-verwandte-kanaele.md:17–19` | OK, nur dokumentieren. |
| W28 | **Datenquelle Entdecken nutzt Related-Daten,** die beim Öffnen eines Videos geladen werden. Die UI ist aus (`showRelatedItems=false`), die Daten kommen aber im `StreamInfo` mit. Das ist kein Widerspruch zu „keine YouTube-Empfehlungen“, solange nur Kanäle gezählt und nie Videos angezeigt werden. Grenzfall: Die Kanalvorschläge sind indirekt YouTube-Empfehlungen. | [Beleg] `spec-verwandte-kanaele.md:10–11`; `VideoDetailFragment.java:316–317` | Dem Nutzer transparent machen („Quelle: YouTube-Verweise bei Videos, die du geschaut hast“). |
| W29 | **Gruppen-Semantik uneinheitlich:** Spec und Entdecken nennen feste Gruppen „Lernen > Unterhaltung > Nebenbei“. Der Code nimmt die **Sortierreihenfolge** beliebiger Feed-Gruppen als Priorität (Gruppe 1 = 60 Punkte, ab Gruppe 5 = 0 wie „keine Gruppe“). Z12 will Gruppen zusätzlich als Limit-Gewicht („Lernen zählt halb“). Das wären zwei Bedeutungen einer Gruppe. | [Beleg] `spec-feed-mit-ende.md:12`; `spec-verwandte-kanaele.md:20–21`; [WIP] `ZenFeedPlanner.kt:8–10, :54`; `feature-audit.md:241` | Festlegen: Gruppen-**Reihenfolge** = Priorität (wie Code). Die Namen sind frei. Z12-Gewichtung erst bei Bedarf als eigenes Gruppen-Attribut. In der Spec erwähnen, dass ab der 5. Gruppe keine Punkte mehr vergeben werden. |
| W30 | **Abo-Hygiene doppelt:** Entdecken nennt „90 Tage nie zu Ende geschaut“ bei jedem neuen Abo, Z13 nennt „monatlich, 90 Tage nicht angesehen“. | [Beleg] `spec-verwandte-kanaele.md:22–23`; `feature-audit.md:242` | Ein Mechanismus: Auslöser = neues Abo **und** monatlich, Kriterium „90 Tage kein Video zu Ende“. |

### 1.8 Neue Regel: 24-h-Sperre für Zen-Einstellungen (`spec-einstellungs-sperre.md`, Commit `36266f156`)

| # | Befund | Beleg | Bewertung / Vorschlag |
|---|---|---|---|
| S1 | **Die neue Regel lässt Lockern sofort wirken.** „Diese Änderung gilt sofort. Danach … 24 h gesperrt.“ Im heißen Moment kann man also den Deckel hochsetzen oder die Pause abschalten, und es wirkt **jetzt**. Die Sperre verhindert nur die *zweite* Änderung. Das frühere Prinzip schützte genau den heißen Moment: Lockern wird zur „ausstehenden Änderung“, die erst am nächsten Tag um 4:00 wirkt und jederzeit abbrechbar ist. | [Beleg] `spec-einstellungs-sperre.md:16, :40–41` vs. `research-achtsamer-konsum.md:132, :141`, `feature-audit.md:235` | **Wichtigster Widerspruch der neuen Spec.** Die Spec erklärt in Z. 40–41, dass sie das alte Prinzip „ersetzt“. Damit fällt dessen Hauptnutzen weg (Allcott: Limits wirken, wenn sie *nicht sofort übersteuerbar* sind). Vorschlag F7 (4.3): **Strenger wirkt sofort, Lockern wirkt nach 24 h** (abbrechbar). Ab der Bestätigung sind 24 h lang keine weiteren Änderungen möglich. Das erfüllt die Nutzerregel „24 h nicht erneut änderbar“ wörtlich und erhält den Impulsschutz mit **einer** Zahl (24 h). |
| S2 | **Andere Specs sagen weiterhin „Lockern ab nächstem Tag“.** | [Beleg] `spec-feed-mit-ende.md:10`, `spec-pause-nach-abbruch.md:19`, `feature-audit.md:37, :235`, `research-achtsamer-konsum.md:141, :206` | Nach F7 überall „siehe `spec-einstellungs-sperre.md`“ eintragen. Research als historisch belassen, mit Verweis. |
| S3 | **Feed-Gruppen-Reihenfolge = Priorität, liegt aber im NewPipe-UI.** Die Spec sperrt „Gruppen-/Kanal-Prioritäten“, Abonnieren bleibt frei. Die Priorität ist im (WIP-)Code aber die Sortierung der NewPipe-Feed-Gruppen (`FeedGroupDialog`, Reorder-Dialog), die man beim normalen Aufräumen bedient. Die Entdecken-Spec verlangt beim Abonnieren eine **Pflichtfrage nach der Gruppe**. Gilt das als gesperrte Zen-Änderung, blockiert es das Abonnieren während der Sperre. Gilt es als Änderung, die eine Sperre startet, sperrt jedes neue Abo 24 h lang die Zen-Einstellungen. | [Beleg] `spec-einstellungs-sperre.md:11–13`; [WIP] `ZenFeedPlanner.kt:8–10`, `FeedGroupDAO.kt` (Diff); `spec-verwandte-kanaele.md:20–21` | Vorschlag: Gruppen-Zuordnung und -Reihenfolge **nicht** sperren. Sie ändern nur die Gewichtung, nicht die Menge (Ausgabe-Größe und Deckel bleiben), sind also kein Lockern. In der Spec aus der Liste streichen. |
| S4 | **Parameteränderung darf die laufende Ausgabe nicht neu würfeln.** Sonst wird „Einstellung ändern“ zum Hebel für eine Extra-Ausgabe (vor allem wenn strenger stellen ohne Sperre erlaubt ist, z. B. Mindestlänge 5 → 6 min: neue Zusammenstellung jetzt). | [Beleg] fehlt in beiden Specs; [WIP] Ausgabe wird nur bei neuer Stunde gebaut (`ZenFeedState.kt:120–122`), das ist korrekt, aber undokumentiert | Regel: Änderungen an Feed-Parametern wirken ab der **nächsten** Ausgabe. Ein gesenkter Deckel gilt sofort für den laufenden Zen-Tag (strenger), ein erhöhter erst ab dem nächsten Zen-Tag 4:00 (nach F7 ohnehin ≥ 24 h später). |
| S5 | **Laufende Pflichtpause und Einstellungsänderung:** Nicht geregelt, ob „Pause aus“ oder „Prozent senken“ eine **laufende** Pause verkürzt. | [Beleg] fehlt in `spec-einstellungs-sperre.md` und `spec-pause-nach-abbruch.md` | Regel: Eine laufende Pause wird nie durch eine Einstellungsänderung verkürzt. |
| S6 | **Was ist „strenger“?** Offene Entscheidung 1 erlaubt Strenger-Stellen während der Sperre, aber für viele Schalter ist die Richtung unklar. Entdecken **an** fügt Inhalte hinzu (lockerer), **aus** ist strenger. Graustufen an = strenger. Nachtruhe-Fenster verschieben ist gemischt. Mindestlänge **senken** ist lockerer. Ausgabe-Größe senken bei gleichem Deckel ist strenger. | [Beleg] `spec-einstellungs-sperre.md:11–13, :23–24` | Pro Einstellung eine feste Richtung im Code (`stricterIf: (old,new)->Boolean`). Gemischte Änderungen (z. B. Zeitfenster verschoben) zählen als Lockern. Liste in der Spec ergänzen. |
| S7 | **Entdecken an/aus + Sperre = Frust.** Opt-in-Feature ausprobieren (an = lockern) startet die 24-h-Sperre für **alles**. Wieder ausschalten geht nur, wenn Strenger-während-Sperre erlaubt ist (offene Entscheidung 1). | [Beleg] `spec-verwandte-kanaele.md:26`, `spec-einstellungs-sperre.md:12, :23` | Offene Entscheidung 1 mit „ja“ beschließen (Empfehlung der Spec teilen). Dann ist Aus jederzeit möglich. |
| S8 | **Ersteinrichtung ohne Ende = nie gesperrt.** „Bis ‚Fertig‘ frei änderbar“. Wer die Einrichtung nie abschließt, hat dauerhaft freie Einstellungen. Unklar ist auch, ob Zen-Regeln (Deckel, Pause) während der Einrichtung schon gelten. | [Beleg] `spec-einstellungs-sperre.md:18–19` | Die Einrichtung endet mit „Fertig“ **oder** automatisch beim ersten Videostart bzw. spätestens nach 24 h. Zen-Regeln gelten von Anfang an mit Defaults. |
| S9 | **Bestandsinstallation:** Stefans Gerät hat ZenPipe schon (ohne Einrichtungsablauf). Wann die Einrichtung bei einem Update auf die Version mit Sperre läuft, ist offen. | [Beleg] `STATE.md:11` (Defy im Einsatz); Spec nennt nur „ersten Start“ | „Erster Start **dieser Funktion**“ = fehlender Schlüssel `setup_done` in `zen_state`. Gilt auch nach Update. |
| S10 | **„Protokoll im Export“** widerspricht der Architektur: `zen_state` ist absichtlich **nicht** im NewPipe-Export (sonst wäre der Import ein Bypass). „Hinweis beim Neustart ‚Zen wurde zurückgesetzt‘“ ist nach *App-Daten löschen* nicht erkennbar, weil kein Zustand überlebt (siehe U-8). | [Beleg] `spec-einstellungs-sperre.md:35–36` vs. `ZenStore.kt:126–131`, `feature-audit.md:217` | Präzisieren: Zen-Log **nur lesend** in den Export (Import schreibt ihn nie zurück, `lockedUntil` wird nie importiert). Der Reset-Hinweis ist nur bei **Import** einer DB mit Zen-Markern erkennbar, nicht nach Datenlöschung. Der Satz sollte ehrlich formuliert werden. |
| S11 | **24 h vs. Zen-Tag 4:00:** Die Sperre ist eine Dauer, der Tag eine Uhrzeitgrenze. Das passt zusammen, solange `lockedUntil` als Dauer (Vertrauenszeit + 24 h) gerechnet wird. Vorteil: Zeitzonen-Tricks (U-7) wirken nicht. Bei der Zeitumstellung zeigt „Änderbar ab“ 1 h verschoben an, das ist korrekt, kann aber irritieren. Offene Entscheidung 3 (kein Extra-Warten bis 4:00) ist konsistent. | [Beleg] `spec-einstellungs-sperre.md:27–28, :31–33`; `ZenDay.kt` | OK. Festhalten: „24 h = Vertrauenszeit-Dauer, unabhängig von Zen-Tag und Zeitzone.“ |
| S12 | **Uhr vorstellen + Neustart beendet die Sperre.** „Fail-closed“ gilt nur für erkannte Manipulation. `ZenClock` erkennt einen Vorsprung über einen Neustart **nicht**. | [Beleg] `spec-einstellungs-sperre.md:32–33`; `ZenClock.kt:8–13` | Wie U-6: Restdauer zusätzlich als „verbleibende Sekunden“ speichern und nur bei Gerätelaufzeit herunterzählen, HTTP-`Date` als dritte Quelle. |
| S13 | **Absichtsabfrage-Parameter und Zugang zu den Einstellungen:** Das Audit (Z6) sah für das Öffnen der Zen-Einstellungen 30 s Countdown + Begründungsfeld vor, die neue Spec 3 s Bestätigung beim Speichern. Absichtsabfrage-Parameter sind gesperrt (gut), sie sind aber im Code heute Konstanten (`ZenIntentPrompt.java:20–22`). | [Beleg] `feature-audit.md:235`; `spec-einstellungs-sperre.md:16–17` | Einheitlich 3 s beim Speichern (neue Spec). Z6 im Audit als überholt markieren. Bis eine Zen-Einstellungsseite existiert, sind alle Werte Konstanten und die Sperre ist gegenstandslos. Keine Einstellung vor der Sperre freigeben. |
| S14 | **Speichermodell:** „Mehrere Änderungen in einem Dialog = eine Sperre“ verträgt sich nicht mit dem NewPipe-Preference-Muster (jede Änderung speichert sofort). | [Beleg] `spec-einstellungs-sperre.md:20` | Zen-Einstellungen als Entwurf (`pendingConfig`) mit explizitem „Übernehmen“. Eigenes Fragment im `zen`-Paket, kein `PreferenceFragment`-Autosave. |

### 1.9 Veraltete Zahlen und Aussagen

| Stelle | Veraltet | Aktuell |
|---|---|---|
| `spec-feed-mit-ende.md:32` | „Neue Ausgabe nur zum Tageswechsel“ | stündlich (W4) |
| `spec-feed-mit-ende.md:22` | „verfallen“ | „warten“ (W5) |
| `feature-audit.md:33, :188, :225` | Absichtsabfrage nach ≥ 15 min Hintergrund | Code: 5 min (`ZenIntentPrompt.java:22`), `STATE.md:18` |
| `feature-audit.md:226` (Z2) | „nur neu seit letztem Besuch“, „Ältere zeigen“-Button, Abschlusskarte „App schließen“ | Ausgabe-Modell mit Scoring, kein „Ältere zeigen“ |
| `feature-audit.md:96–97, :279` | Feed-Default „gesehene ausblenden“ als To-do | durch Ausgabe ersetzt (WIP blendet den Schalter aus) |
| `feature-audit.md:71` | Autoplay „Neubewertung empfohlen … WIFI“ | Entscheidung: dauerhaft aus |
| `feature-audit.md:193` (L6) | Shorts-URLs „ok, kein Fix nötig“ | Shorts-Links blockiert (`RouterActivity.java:225–231`) |
| `feature-audit.md:236` (Z7) | Tagesbudget „z. B. 45 min“ | offen (F2). Nicht mit „30 Videos“ verwechseln |
| `feature-audit.md:240` (Z11) | Später „z. B. 15 Einträge“ | 10 |
| `feature-audit.md:241` (Z12) | Podcast zählt nicht | Podcast zählt für die Pause (W22) |
| `feature-audit.md:3, :25` | HEAD `f120f8cc5`, Prefs-File „`zen`“ | Prefs-File heißt `zen_state` (`ZenStore.kt:152`) |
| `.planning/PROJECT.md:11–17` | Nur MVP und „Später: Tageslimit, Feed mit Ende“ | Feed 15/h + 30/Tag, Pflichtpause, Entdecken, Shorts nirgends fehlen |
| `ZenPipe-Konzept_v0.1.md:22–28` | Funktionsliste ohne Shorts-Sperre, Pause, Ausgabe | als v0.1 historisch lassen, aber auf diese Datei bzw. Specs verweisen |
| Vorversion Feed-Spec (git) | „15 pro Tag“, „45/Tag“ | 15/h, 30/Tag. Im Arbeitsbaum korrekt, nur in der Historie |

---

## 2. Umgehungs- und Missbrauchslücken

Maßstab: Ein Ein-Personen-Fork ohne Spezialberechtigungen schützt vor dem **Impuls**, nicht vor dem Entschlossenen (`research-achtsamer-konsum.md:177`). Ziel ist, dass jeder Umweg **teurer als die Lust im Moment** ist und **sichtbar** wird.

| # | Umgehung | Wirkung heute | Beleg | Realistische Gegenmaßnahme | Aufwand |
|---|---|---|---|---|---|
| U-1 | **„Doch nicht“ und sofort neu öffnen** | Keine Abfrage mehr: `lastAskedAt` ist gesetzt, der Prozess lebt nach `finishAffinity()` weiter, `showIfNeeded` fragt nur bei `lastAskedAt < 0`. L1 aus dem Audit ist damit **weiter offen**. | [Beleg] `ZenIntentPrompt.java:31–35, :82–83` | „Doch nicht“ setzt ein Flag „beim nächsten Start sicher fragen“. Den Zeitstempel in `zen_state` statt `static` halten. | S |
| U-2 | **Musik in einer anderen App laufen lassen** | `AudioManager.isMusicActive()` gilt **systemweit**. Läuft Spotify oder Radio, entfallen Abfragen bei Links und bei Rückkehr. | [Beleg] `ZenIntentPrompt.java:65–69` | Prüfen, ob der **eigene** `PlayerService` spielt (z. B. `PlayerHolder`), statt das System zu fragen. | S |
| U-3 | **Geteilte Links → Hintergrund/Popup** | `RouterActivity` startet Hintergrund- bzw. Popup-Player direkt, ohne `MainActivity`, also ohne Abfrage. Auch Pause und Tages-Deckel greifen dort nur, wenn sie im Player sitzen. | [Beleg] `RouterActivity.java:1038–1040`; `STATE.md:23` | Abfrage und Pausen-Prüfung zentral **im Player-Start** (`NavigationHelper.play*` oder `PlayerService`), nicht in Activities. Pause muss ohnehin im Player-Hook sitzen. | S–M |
| U-4 | **Abbruch per Wegwischen aus „Zuletzt verwendet“ oder Force-Stop** | Ein Abbruch-Hook in `onDestroy`/Stop läuft beim Prozess-Kill evtl. nicht. Die Pause wird dann nie angelegt. | [Vermutung] Pause ist noch nicht implementiert | Wiedergabe-Fortschritt alle ~30 s in `zen_state` schreiben („aktives Video, Sekunden“). Findet der nächste Start einen offenen Eintrag ohne „zu Ende“, gilt er als Abbruch und die Pause beginnt sofort. **Das ist Pflicht für die Pause-Umsetzung.** | S |
| U-5 | **Pausieren, dann App beenden** | „Pausieren ist kein Abbruch“. Ohne Zusatzregel gilt ein pausiert geschlossener Player nie als Abbruch. | [Beleg] `spec-pause-nach-abbruch.md:12` | Player bzw. Benachrichtigung schließen **während pausiert** = Abbruch. Pausiert im Hintergrund > 30 min ohne Fortsetzen: kein Abbruch (Podcast-Fall, U6), aber der Startzeitpunkt bleibt maßgeblich. | S |
| U-6 | **Pause per Neustart oder Uhr verkürzen** | Gleiche Laufzeit: Uhrsprung wird über `elapsedRealtime` erkannt (Toleranz 2 min). **Nach Neustart** wird nur ein Rücksprung erkannt, ein **Vorsprung** nicht. Uhr vorstellen und neu starten beendet Pause, Stunde und Tag. | [Beleg] `ZenClock.kt:8–13, :44–51` (selbst dokumentiert) | HTTP-`Date` der YouTube-Antworten als dritte Zeitquelle (`feature-audit.md:219`, noch nicht umgebaut). Eine Pause speichert zusätzlich „Rest-Sekunden“, die nur **bei laufendem Gerät** sinken (Restlaufzeit über Boot hinweg, im Zweifel strenger). | S–M |
| U-7 | **Zeitzone ändern** | `ZenDay` nutzt `ZoneId.systemDefault()`. Ein Wechsel auf UTC+14 verschiebt Stunden- und Tagesgrenze, ohne dass `ZenClock` etwas merkt (die Epoch-Millis bleiben gleich). Ein neuer Zen-Tag lässt sich so „vorziehen“. | [Beleg] `ZenDay.kt:15, :20, :30` | Zeitzone beim ersten Tagesbeginn in `zen_state` fixieren. Bei Wechsel die alte bis zum nächsten echten 4:00 weiterverwenden und protokollieren. Seltener Reisefall, deshalb Sichtbarkeit statt Sperre. | S |
| U-8 | **App-Daten löschen** | Löscht `zen_state` **und** die NewPipe-DB. Erkennung „DB vorhanden, Zen-Marker fehlt“ (Z9) greift nicht, weil beides weg ist. Abos lassen sich aber per Import in 1 min zurückholen. | [Beleg] `ZenStore.kt:126–131`; `feature-audit.md:238` | Nicht verhinderbar. Die Kosten steigen, wenn Später-Liste, Verlauf und Fortschritt verloren gehen. Optional: `firstInstallTime` und ein „Zen-Seit“-Datum anzeigen, Reset erscheint in der Bilanz als Lücke. Akzeptieren. | – |
| U-9 | **Daten-Import (NewPipe-ZIP)** | `zen_state` bleibt (gut). Aber Ausgabe, `feed_handled` und Später-ID referenzieren **Stream-UIDs aus Room** (`autoGenerate`). Ein Import tauscht die DB, die UIDs passen nicht mehr. Folge: Picks verschwinden, bereits verworfene Videos tauchen wieder auf, die Ausgabe wirkt leer bis zur nächsten Stunde. Kein Bypass, eher Fehler. | [Beleg] `StreamEntity.kt:27`; [WIP] `ZenFeedState.kt:157–167`, `ZenFeedProvider.kt:238–251`, `ZenWatchLater.kt:12` | Schlüssel = `serviceId + url` statt `uid`. Kleiner Umbau im WIP, jetzt billig. | S |
| U-10 | **Verlauf löschen** (in den Einstellungen weiterhin möglich) | Geschaute Videos gelten wieder als ungesehen und können erneut in Ausgaben auftauchen. Später fällt auch die „Zu-Ende-Quote“ (implizites Signal) weg. | [Vermutung] `history_settings.xml` unverändert laut Audit 2.6 | Akzeptabel (Datenschutzfunktion). Ausgabe-relevante Merker in `zen_state` halten. |
| U-11 | **Zweite App-Instanz** | Debug-Build (`.debug`) und Original-NewPipe laufen parallel mit eigenem Zustand. Release hat noch `org.schabi.newpipe` und kollidiert mit NewPipe. | [Beleg] `research-fdroid-deployment.md:24–25, :46`; `STATE.md:33` (L18 offen) | Nicht technisch lösbar. NewPipe nach Migration deinstallieren (`research-achtsamer-konsum.md:168`). Debug-Build auf dem Alltagsgerät nur zum Testen und danach entfernen (Hinweis für Stefan, der Entwickler-Nutzer hat immer einen Rebuild als Umweg). | – |
| U-12 | **Browser, YouTube-App** | „Link kopieren“ statt Browser (gut). Kopierten Link in den Browser einfügen geht weiterhin. | [Beleg] `STATE.md:27` | Außerhalb von ZenPipe: YouTube-App deinstallieren, Digital-Wellbeing-Timer für den Browser (`research-achtsamer-konsum.md:147, :167`). | – |
| U-13 | **Kanalseiten und Suche als Feed-Ersatz** | Unbegrenzt paginiert (W26). Der Deckel gilt nur für den Feed. | [Beleg] W26 | Limits aus der Spec umsetzen. Danach ist der Restweg „gezielt suchen“, und das ist erwünscht (Lukoff). | S–M |
| U-14 | **Eigene Playlists als unbegrenzte Später-Liste** | siehe W25 | [Beleg][WIP] | Akzeptieren. Kuratieren ist bewusst. | – |
| U-15 | **Shorts über `youtu.be/<id>` oder `watch?v=<id>`** | Der Router blockiert nur `/shorts/`-Pfade. Ein als Watch-Link geteiltes Short spielt im normalen Player. Kein Swipe-Feed, aber „Shorts nirgends“ ist nicht wörtlich erfüllt. | [Beleg] Regex `RouterActivity.java:226–227` | Optional nach dem Laden: `StreamInfo` mit Dauer ≤ 60 s und Hochformat bzw. `isShortFormContent` blockieren. Sonst bewusst als Restrisiko dokumentieren. | S |
| U-16 | **Feed-Gruppen-Tab als „anderer Feed“** | Ein Gruppen-Tab zeigt nur die Schnittmenge der globalen Ausgabe, also keinen eigenen Vorrat. Kein Bypass. | [Beleg][WIP] `ZenFeedProvider.kt:241–245` | OK. UX-Hinweis siehe U4 in Abschnitt 3. | – |
| U-17 | **Externer Player / Kodi** | Wiedergabe außerhalb: Pausen und Zeitmessung greifen nicht. | [Beleg] `feature-audit.md:86–87` | Option verstecken oder externen Start als „Abbruch nach 0 min“ werten (keine Pause, nur Protokoll). | S |
| U-19 | **Lockern im heißen Moment** (neue Sperr-Regel) | Lockern wirkt sofort, Sperre greift erst danach (S1). Einmal pro 24 h ist so eine sofortige Lockerung möglich. | [Beleg] `spec-einstellungs-sperre.md:16` | F7: Lockern erst nach 24 h wirksam. | S |
| U-20 | **Ersteinrichtung nie abschließen** | Einstellungen bleiben dauerhaft frei (S8). | [Beleg] `spec-einstellungs-sperre.md:18–19` | Auto-Ende beim ersten Video bzw. nach 24 h. | S |
| U-18 | **Zen-Log manipulieren** | Nur mit Root bzw. `run-as` (Debug-Build!). Beim Debug-Build kann Stefan `zen_state.xml` per `adb shell run-as` editieren. | [Vermutung] | Entwickler-Umweg, akzeptieren. Release-Build ist nicht `debuggable`. | – |

**Gesamturteil:** Gegen den Impuls ist das System nach U-1 bis U-4 (alle S) plausibel robust. Die Uhr ist der einzige systematische Schwachpunkt (U-6/U-7). Mit HTTP-`Date` und fixierter Zeitzone ist er für einen Ein-Personen-Fork ausreichend geschlossen. Gegen Daten löschen, Zweit-App und Rebuild gibt es keinen sinnvollen Schutz, nur Sichtbarkeit.

---

## 3. Nutzer-Erlebnis: Frust-Fallen

| # | Falle | Ursache | Vorschlag |
|---|---|---|---|
| U1 | **Tag nach 2 Öffnungen „vorbei“** (7:58 kurz reinschauen, 8:01 nochmal → Deckel erreicht, Rest des Tages „morgen 04:00“) | W1/W3 | Deckel auf verschiedene Videos zählen. Eine Ausgabe nur erzeugen, wenn der Feed nach der Abfrage wirklich angesehen wird. Ein Video zählt erst als „verbraucht“, wenn es angetippt, verworfen oder gemerkt ist (F1, Empfehlung). |
| U2 | **Leere oder magere Ausgaben** bei Abos mit vielen kurzen Videos (Musik, News-Clips, < 5 min) oder wenigen Uploads/Woche. Die Ausgabe ist dann `provisional` und es gibt keine Abschlusskarte. Was der Leer-Zustand anzeigt, ist offen. | [WIP] `ZenFeedState.kt:147–149`, `ZenFeedProvider.kt:246–247` | Eigene Karte: „Keine neuen Videos ≥ 5 min aus deinen Abos (7 Tage). Später-Liste: N Einträge.“ Diagnose-Zeile: „12 kürzere Videos ausgeblendet“. |
| U3 | **Wenig passende Videos:** Ohne Feed-Gruppen bekommen alle 0 Gruppenpunkte, dann entscheiden nur Länge und Frische. Der Nutzer merkt nicht, dass Gruppen-Reihenfolge = Priorität ist. | [WIP] `ZenFeedPlanner.kt:8–10` | Einmaliger Hinweis in der ersten Ausgabe: „Sortiere deine Feed-Gruppen, die erste wird bevorzugt.“ Begründung pro Eintrag zeigen (Strings vorhanden: `zen_reason_group`). |
| U4 | **Feed-Gruppen-Tab fast leer**, weil er nur die Schnittmenge der globalen Ausgabe zeigt. | [WIP] `ZenFeedProvider.kt:241–245` | Im Gruppen-Tab „N von 15 dieser Ausgabe“ anzeigen, oder Gruppen-Tabs vorerst nicht anbieten (L12 ist ohnehin offen). |
| U5 | **Deckel erreicht, nichts zu tun:** Keine Brücke zu den erlaubten Wegen. | Spec nennt nur „Später“ und „Verwerfen“ | Abschlusskarte mit Ausgängen: „Später-Liste (N)“, „Playlists“, „Suche“, „App schließen“. Ruhiger Ton, kein Countdown-Druck. |
| U6 | **Pause mitten im Kontext:** Podcast im Auto bei Ankunft beendet, Video wegen Anruf oder Tür geschlossen. Das führt zu Pflichtpause und gefühlter Bestrafung (Reaktanz-Risiko, `research-achtsamer-konsum.md:122`). | `spec-pause-nach-abbruch.md` | (a) Anruf bzw. Audio-Fokus-Verlust ist kein Abbruch. (b) Die Pause beginnt erst beim **nächsten Startversuch** eines Videos (nicht schon beim Abbruch), mit klarer Erklärung. (c) „Später fortsetzen“-Knopf im Player: Position merken, keine Pause, max. N pro Tag (protokolliert). |
| U7 | **Zähler-Verwirrung:** Drei Zähler (15/h, 30/Tag, Später 10) plus Pause und eventuell Minutenlimit. „Nächste Ausgabe um 15:00“ wirkt wie eine Einladung, um 15:00 wiederzukommen (stündlicher Anreiz, Sog-Mechanik). | Spec Regel 3 | Abschlusskarte zeigt **eine** Zahl: „Heute noch N Videos in künftigen Ausgaben“ und die Uhrzeit nur klein. Stundentakt eventuell auf „frühestens in 60 min“ statt fester voller Stunde (weniger Termin-Charakter, F5). |
| U8 | **Ausgabe ändert sich nicht nach Pull-to-refresh**, obwohl neue Videos geladen wurden. Das wirkt wie ein Fehler. | Spec Regel 4 | Nach dem Refresh einen Hinweis zeigen: „3 neue Videos im Vorrat, ab 15:00 in der Ausgabe.“ |
| U9 | **Abfrage nach jeder 5-min-Abwesenheit** bei kurzen Unterbrechungen (Nachricht beantworten). Die 5 s Wartezeit summiert sich. | `ZenIntentPrompt.java:21–22` | 5 min Cooldown ist ok. Eventuell Hintergrund-Schwelle 10–15 min (Audit-Wert) und bei Wiederholung innerhalb 1 h eskalieren statt immer gleich (Z1). |
| U11 | **Tippfehler beim Strenger-Stellen** (Deckel 3 statt 30): Das ist nicht „lockern“. Wird er erst nach Ablauf der Sperre korrigierbar, hängt man einen Tag mit einer viel zu strengen Regel fest. Die Ersteinrichtungs-Ausnahme deckt nur den ersten Tag. | `spec-einstellungs-sperre.md:16–19` | **Rückgängig-Fenster 5 min** nach jeder Bestätigung: zurück auf exakt den vorherigen Stand, protokolliert, startet keine neue Sperre. Zusätzlich Plausibilitätsgrenzen (Deckel ≥ Ausgabe-Größe, Ausgabe ≥ 5). |
| U12 | **Globale Sperre durch Kleinkram:** Graustufen umschalten (kosmetisch) sperrt 24 h lang auch wichtige Feed-Korrekturen. | `spec-einstellungs-sperre.md:7–8, :12` | Mit F7 entschärft: Strenger bleibt immer möglich. Kosmetik ohne Lockerungswirkung (Graustufen **an**) startet keine Sperre. |
| U13 | **„Änderbar ab“ als Countdown** lädt zum Wiederkommen ein (wie die stündliche Ausgabe). | `spec-einstellungs-sperre.md:9–10` | Nur Datum und Uhrzeit, kein laufender Countdown, keine Benachrichtigung bei Ablauf. |
| U10 | **Kurze, gezielt gesuchte Videos** (Anleitung 3 min) sind über die Suche erreichbar, nicht über den Feed. Das ist korrekt, aber unerklärt. | W9 | Regel dokumentieren: Mindestlänge gilt **nur für den Feed**. |

---

## 4. Bereinigte Regel-Tabelle („Single Source of Truth“)

### 4.1 Vorschlag

Vorschlag: Diese Tabelle nach Freigabe als `zen-docs/rules.md` führen. Alle Specs verweisen darauf, und die Konstanten im Code tragen denselben Namen.

| Regel | Wert / Default | Gilt für | Ausnahme | Quelle | Status |
|---|---|---|---|---|---|
| Autoplay (geöffnetes Video) | aus, fest | alle Player | – | Nutzer; `PlayerHelper.java:255–258` | umgesetzt |
| Auto-Queue / Related / Kommentare | aus, fest | Detailseite, Player | – | `PlayerHelper.java:231–234`, `VideoDetailFragment.java:316–317` | umgesetzt |
| Shorts | nirgends | Listen, Kanal-Tab, Feed-Abruf, RSS, `/shorts/`-Links, Einstellungen | Watch-Links auf Short-IDs (Restrisiko U-15) | Nutzer; `InfoListAdapter.java:130`, `ChannelTabHelper.java:145`, `RouterActivity.java:225` | umgesetzt |
| Livestreams | nicht im Feed / Kanal-Tab | Feed, Kanal | Suche, Playlists (bewusst offen) | `STATE.md:28, :33` | umgesetzt |
| Zen-Tag | Wechsel 04:00 lokal, **Zeitzone fixiert** | Deckel, Time-Lock, Bilanz | – | `ZenDay.kt` | Zeitzone fehlt (U-7) |
| Vertrauenszeit | Wanduhr vs. `elapsedRealtime`, Toleranz 2 min, + HTTP-`Date` | alle Zen-Zeiten | – | `ZenClock.kt` | HTTP-Date fehlt |
| Ausgabe-Größe | 15 Videos | Feed | konfigurierbar 10–15 erst mit Time-Lock, ≤ Deckel/2 | Nutzer; `ZenFeedPlanner.BUDGET_PER_HOUR` | WIP |
| Ausgabe-Takt | frühestens zur nächsten vollen Stunde, erst bei tatsächlichem Öffnen nach der Abfrage | Feed | keine neue Ausgabe während Pause / Nachtruhe | Nutzer + W3/W14 | WIP, Anpassung nötig |
| Tages-Deckel | 30 **verschiedene** Videos pro Zen-Tag, gezählt beim Erscheinen in einer Ausgabe (oder: beim Antippen/Verwerfen/Merken, F1) | Feed | Später-Liste, Playlists, Suche gezielt | Nutzer; `ZenFeedPlanner.DAILY_CAP` | WIP zählt anders (W1/W2) |
| Kandidaten | ungesehen, aus Abos, ≤ 7 Tage, kein Live, **≥ 5 min** | Feed | – | Spec + `ZenFeedPlanner.kt:46–50` | Spec fehlt Mindestlänge |
| Scoring | Gruppen-Rang (60 − 15·Rang, ≥ 0) + Länge (≥ 15 min +12, ≥ 30 min +18) + Frische (0–20) | Feed | Musik: kein Längenbonus (W19) | `ZenFeedPlanner.kt` | Spec fehlt |
| Vielfalt | max. 3 je Kanal und Ausgabe | Feed | – | Spec | WIP |
| Erklärung | Grund pro Eintrag sichtbar | Feed | – | Spec | WIP (Strings) |
| „Zu Ende geschaut“ | ≥ 90 % oder Rest ≤ 60 s | Pause, gesehen-Logik, Abo-Hygiene | – | **neu** (W12) | offen |
| Pflichtpause: Auslöser | Wiedergabe endet vor „zu Ende“, echte Abspielzeit ≥ 5 min in dieser Sitzung | alle Player inkl. Hintergrund/Popup/Router | Musik; Anruf/Audio-Fokus; „Später fortsetzen“ (F6) | Nutzer; Spec | nicht umgesetzt |
| Abbruch-Definition | Player schließen, Benachrichtigung wegwischen, Stopp, anderes Video starten, Prozess-Kill (beim nächsten Start erkannt) | Pause | Pausieren allein (auch im Hintergrund) | Nutzer + U-4/U-5 | offen |
| Pflichtpause: Länge | 20 % der Abspielzeit, min 1, max 15 min | – | – | Spec | offen |
| Pflichtpause: Verhalten | Videostart gesperrt, Browsen erlaubt, endet nur durch Zeitablauf (nicht durch Stunden-/Tageswechsel), Restzeit läuft nur bei laufendem Gerät | – | – | Spec + W16/U-6 | offen |
| Musik vs. Podcast | Musik = YouTube-Kategorie Music / Topic-Kanal; Podcast = ≥ 20 min Audio-Modus oder Titel-Hinweis; unsicher = streng | Pause | – | Spec | Extractor-Prüfung offen |
| Später-Liste | max. 10, normale lokale Playlist | Feed-Triage | vom Tages-Deckel ausgenommen, Pause gilt | Spec; `ZenWatchLater.MAX_ITEMS` | WIP; 30-Tage-Ablauf offen (F4) |
| Absichtsabfrage | 5 s Wartezeit; bei Start, externem Link, Rückkehr nach ≥ 5 min; Cooldown 5 min; nicht bei **eigener** laufender Wiedergabe; nach „Doch nicht“ beim nächsten Start immer | MainActivity **und** Router-Hintergrund/Popup | – | Nutzer; `ZenIntentPrompt.java` | teilweise (U-1–U-3) |
| Kanalseite | letzte 10–15 Videos, keine Paginierung | Kanal-Tabs | – | Spec | nicht umgesetzt |
| Suche | max. 2 Ergebnisseiten | Suche | – | Spec | nicht umgesetzt |
| Entdecken | opt-in, max. 3 Kanäle/Woche, ≥ 2 unabhängige Quellen, Wochenwechsel Mo 04:00 | eigener Bereich | – | Spec | Phase 2 |
| Abo-Hygiene | bei neuem Abo + monatlich: Kanäle mit 90 Tagen ohne „zu Ende“ | Abos | – | Entdecken-Spec + Z13 | Phase 2/3 |
| Tageslimit Minuten | **offen** (F2) | Konsum | Audio? Podcasts? | Konzept §3, Z7 | Entscheidung nötig |
| Einstellungs-Sperre (Time-Lock) | nach jeder bestätigten Änderung 24 h (Vertrauenszeit-Dauer) keine weitere Änderung, global; **Vorschlag F7:** strenger wirkt sofort und bleibt während der Sperre erlaubt (ohne Neustart der Sperre), lockern wirkt erst nach 24 h (abbrechbar); 3 s Bestätigung mit Zusammenfassung; Rückgängig-Fenster 5 min | Ausgabe-Größe, Deckel, Mindestlänge, Pause-Parameter, Abfrage-Parameter, Nachtruhe, Entdecken an, Graustufen aus | Ersteinrichtung (bis „Fertig“, erstes Video oder 24 h); Feed-Gruppen-Zuordnung/-Reihenfolge, Abos, Playlists **nicht** gesperrt; laufende Pause und laufende Ausgabe werden durch Änderungen nie verkürzt bzw. neu gewürfelt | Nutzer; `spec-einstellungs-sperre.md` (ersetzt Research §3.1 „bis 4:00“) | Spec neu, nicht umgesetzt; bis dahin nur Konstanten |
| Zen-Zustand | `zen_state`-Prefs, nicht in Export/Import/Reset/Auto-Backup; Schlüssel `serviceId+url` statt `uid` | alles | – | `ZenStore.kt`, Backup-Regeln | Schlüssel-Frage offen (U-9) |

### 4.2 Konkrete Anpassungen in Dokumenten

1. **`spec-feed-mit-ende.md`**
   - Z. 10: Zählweise des Deckels festlegen (W1/W2, F1). Die Spanne „10–25“ auf „10–15, ≤ Deckel/2, erst mit Time-Lock“ ändern (W7).
   - Z. 11–17: Kandidaten um „≥ 5 min, kein Live“ ergänzen, außerdem den Längenbonus (15/30 min, Werte), die Gruppen-**Reihenfolge** als Priorität (statt fester Namen) und „ab der 5. Gruppe 0 Punkte“ (W9, W29). Musik ohne Längenbonus (W19).
   - Z. 18 / Z. 20: „Nächste Ausgabe ab HH:00“ bzw. bei Deckel „ab 04:00“ (ohne „morgen“, W6). Keine Ausgabe während Pause bzw. Nachtruhe (W14, W8). Ausgabe erst nach der Absichtsabfrage (W3).
   - Z. 22: „verfallen“ → „warten“ (W5).
   - Z. 25: Ende-Karte der Kanalseite nicht in die Suche schicken (W26).
   - Z. 32: „nur zum Tageswechsel“ → „frühestens zur nächsten vollen Stunde“ (W4). Zeitzone fixieren (U-7).
   - Neu: Abschnitt „Ausnahmen vom Deckel“ (Später, Playlists, gezielte Suche) (W24), Leer-Zustand (U2), „Mindestlänge gilt nur im Feed“ (U10).
2. **`spec-pause-nach-abbruch.md`**
   - Definition „zu Ende“ (W12), „Wiedergabezeit“ (W13), Abbruch-Liste inkl. Videowechsel, Prozess-Kill und „pausiert geschlossen“ (W17, U-4, U-5). „App-Wechsel länger als X Sek.“ streichen (W18).
   - Pause endet nicht durch den Tageswechsel, Restzeit über Neustart (W16, U-6).
   - Reihenfolge Pausenbildschirm vor Absichtsabfrage (W15). Ausnahmen Anruf/Audio-Fokus (U6). Option „Später fortsetzen“ (F6).
   - Hook-Ort: im Player bzw. `PlayerService`, damit Router-Hintergrund/Popup abgedeckt ist (U-3).
   - Heuristik getrennt nach Laufzeit (StreamInfo) und Feed (Kanal-Ebene) (W21).
3. **`spec-verwandte-kanaele.md`**: Gruppen-Frage beim Abonnieren auf „Feed-Gruppe wählen (Reihenfolge = Priorität)“ umformulieren (W29). Abo-Hygiene mit Z13 zusammenführen (W30). Quelle transparent machen (W28). Wochenwechsel-Zeitpunkt nennen (Mo 04:00).
4. **`spec-einstellungs-sperre.md`**: F7 einarbeiten (Lockern verzögert, S1). Feed-Gruppen aus dem Geltungsbereich nehmen (S3). Regeln „laufende Pause/Ausgabe unberührt“ (S4, S5) und „Richtung pro Einstellung“ (S6) ergänzen. Ersteinrichtung mit Auto-Ende und Bestandsinstallationen klären (S8, S9). Den Satz zu Export und Reset-Hinweis ehrlich formulieren (S10). „24 h = Dauer in Vertrauenszeit“ (S11) und Restzeit über Neustart (S12) festhalten. Rückgängig-Fenster (U11), Speichermodell `pendingConfig` (S14). Offene Entscheidung 1 mit „ja“ schließen (S7).
5. **Alle Stellen mit „Lockern ab nächstem Tag/4:00“** auf die Sperr-Spec verweisen: `spec-feed-mit-ende.md:10`, `spec-pause-nach-abbruch.md:19`, `feature-audit.md:37, :235`. `research-achtsamer-konsum.md:141, :206` als „ersetzt durch spec-einstellungs-sperre.md“ markieren (S2).
6. **`feature-audit.md`**: die Zeilen aus 1.9 aktualisieren oder mit „überholt, siehe rules.md“ markieren. Das betrifft vor allem Z. 33/188/225 (15 → 5 min), 71 (Autoplay), 96–97/226/279 (Feed-Modell), 193 (L6), 236 (Z7 offen), 240 (Z11 = 10), 241 (Z12 Podcast), 3/25 (HEAD, Prefs-Name). L1 als **weiter offen** markieren (U-1).
7. **`.planning/PROJECT.md`**: Kernregeln (Shorts nirgends, Feed 15/h + 30/Tag, Pflichtpause, Entdecken, 24-h-Einstellungs-Sperre) und Verweis auf `rules.md` ergänzen. „Später“-Liste aktualisieren.
8. **`.planning/STATE.md`**: Bekannte Lücken U-1, U-2, U-3, U-15 unter „Offen“ aufnehmen.
9. **`ZenPipe-Konzept_v0.1.md`**: als historisch kennzeichnen, mit einem Verweis auf `rules.md` und die Specs.

### 4.3 Offene Nutzer-Entscheidungen (gebündelt, mit Empfehlung)

- **F1 – Was zählt der Tages-Deckel 30?** (a) angebotene Videos, wie heute, also faktisch 2 Ausgaben/Tag · (b) **verschiedene angebotene Videos, Ausgabe erst nach Abfrage und echtem Öffnen** · (c) nur angetippte, verworfene oder gemerkte Videos. **Empfehlung: (b)**. Das ist einfach, erklärbar und nah am Wortlaut „30 Videos am Tag“. (c) ist am fairsten, macht die Ausgabe aber wieder „scroll-billig“.
- **F2 – Kommt zusätzlich ein Minuten-Tageslimit?** **Empfehlung: ja, aber erst nach dem Time-Lock** (Phase 2). Default z. B. 90 min Video. Audio und Podcasts zählen halb, Musik nicht. Ohne Minutenlimit ist der Längenfokus zeitlich offen (W10).
- **F3 – Musik-Kanäle im Feed?** **Empfehlung:** Topic- bzw. Musik-Kanäle nicht im Feed, kein Längenbonus für Musik.
- **F4 – Später-Liste: 30-Tage-Ablauf?** **Empfehlung: ja**, älteste Einträge verfallen nach 30 Tagen (mit Hinweis).
- **F5 – Ausgabe-Takt „volle Stunde“ oder „60 min nach letzter Ausgabe“?** **Empfehlung: 60 min nach letzter Ausgabe.** Das ist weniger Termin-Anreiz und verhindert das 7:58/8:01-Problem auch ohne F1.
- **F6 – „Später fortsetzen“ ohne Pause (Podcast/Anruf-Fall)?** **Empfehlung: ja, 2× pro Tag**, protokolliert.
- **F7 – Wann wirkt eine Lockerung?** (a) sofort, danach 24 h Sperre (aktuelle Spec) · (b) **strenger sofort, lockern nach 24 h (abbrechbar), in dieser Zeit keine weitere Änderung** · (c) lockern erst am nächsten Zen-Tag 4:00 (altes Prinzip). **Empfehlung: (b)**. Es bleibt eine Zahl (24 h), die Nutzerregel ist erfüllt, und der Impulsschutz bleibt erhalten. (a) schützt den heißen Moment nicht.
- **F8 – Feed-Gruppen-Reihenfolge sperren?** **Empfehlung: nein** (S3). Gruppen gewichten nur, sie vergrößern nichts.
- **F9 – Rückgängig-Fenster nach Bestätigung?** **Empfehlung: ja, 5 min**, nur exakt zurück auf den vorherigen Stand (U11).

---

## 5. Abgleich: umgesetzter Code vs. Regeln (Stichproben)

| Bereich | Regel | Befund | Beleg | Urteil |
|---|---|---|---|---|
| Drawer | nur Abos, Feed, Playlists, Suche (+ Einstellungen, Info) | genau diese Einträge | `MainActivity.java:280–302` | ✔ regelkonform |
| Autoplay | dauerhaft aus | `getAutoplayType` → `NEVER`, `isAutoQueueEnabled` → `false` | `PlayerHelper.java:231–234, :255–258` | ✔ |
| Related / Kommentare | aus | Flags fest `false` | `VideoDetailFragment.java:316–317` | ✔ (Related-**Daten** werden weiter geladen, für Entdecken nutzbar, W28) |
| Shorts | nirgends | Listen-Filter zentral, Kanal-Tab und Feed-Abruf gesperrt, `/shorts/`-Links blockiert | `InfoListAdapter.java:130–134`, `ChannelTabHelper.java:145–147, :161–163`, `RouterActivity.java:225–231` | ✔ mit Restrisiko Watch-Link (U-15); Feed-Ausgabe schließt < 5 min ohnehin aus |
| Absichtsabfrage Start | bei Start | nur bei `lastAskedAt < 0`, also einmal pro Prozess. Nach „Doch nicht“ fragt der nächste Start **nicht** | `ZenIntentPrompt.java:31–35, :82–83` | ✘ **widerspricht** „beim Start“ (U-1, Audit-L1 nicht behoben) |
| Absichtsabfrage Links | bei Links | nur in `MainActivity.handleIntent` (Detail, Kanal, Playlist). Router-Hintergrund/Popup ohne Abfrage | `MainActivity.java:810–825`; `RouterActivity.java:1038–1040` | ✘ teilweise (U-3), in `STATE.md:23` nur halb erwähnt |
| Absichtsabfrage Hintergrund | Rückkehr nach langer Zeit | ≥ 5 min, aber unterdrückt bei **systemweiter** Musik | `ZenIntentPrompt.java:22, :43–50, :65–69` | ⚠ Abweichung (U-2) |
| Zen-Zustand | eigene Prefs, nicht in Backup | `zen_state` + Backup-Regeln (Android ≤ 11 und 12+) | `ZenStore.kt:152`, `res/xml/zen_backup_rules.xml`, `zen_data_extraction_rules.xml`, Manifest | ✔ |
| Zen-Tag | 04:00 | `ROLLOVER_HOUR = 4` | `ZenDay.kt:9` | ✔, Zeitzonen-Lücke (U-7) |
| Uhr-Manipulation | erkennen | gleiche Laufzeit ja; Vorsprung über Neustart nein; HTTP-Date fehlt | `ZenClock.kt` | ⚠ (U-6), dokumentiert |
| Feed 15/h, 30/Tag [WIP] | 15 pro Stunde, 30 pro Tag | Konstanten stimmen; Zählung führt zu „2 Ausgaben/Tag“ | `ZenFeedPlanner.kt:14–15`, `ZenFeedState.kt:135–152` | ⚠ Semantik (W1–W3) |
| Längenfokus [WIP] | längere Videos bevorzugt | Mindestlänge 5 min + Bonus 15/30 min | `ZenFeedPlanner.kt:19–27` | ✔ inhaltlich, aber nicht in der Spec (W9); Musik-Mixes profitieren (W19) |
| Später [WIP] | max. 10 | `MAX_ITEMS = 10`, Limit über „Zur Playlist hinzufügen“ umgehbar | `ZenWatchLater.kt:11`, `FeedFragment` Diff | ⚠ (W25), akzeptabel |
| Ende-Karte [WIP] | „Du bist durch“, kein Nachladen | `ZenEndCardItem`, Gesehen-Schalter im Menü ausgeblendet | `FeedFragment` Diff | ✔; Texte „verfallen“/„morgen“ (W5/W6) |
| Kanalseite / Suche | 10–15 / 2 Seiten | nicht umgesetzt | – | offen (W26) |
| Pflichtpause | – | nicht umgesetzt | – | offen |

---

## 6. Zusammenfassung

1. **Kernwiderspruch:** Der Tages-Deckel (30) zählt im WIP-Code angebotene Videos pro Ausgabe. Mit 15/h ist der Tag nach zwei Ausgaben vorbei, und das schon durch bloßes App-Öffnen inkl. „Doch nicht“, weil der Feed Startseite ist. Entscheidung F1 (Empfehlung: verschiedene Videos, Ausgabe erst nach Abfrage) bzw. F5 (60 min nach letzter Ausgabe).
2. **Stück vs. Minuten:** Mit Längenfokus begrenzt das Stück-Budget nur das Angebot, nicht die Sehzeit. Ein Minutenlimit (F2) ist offen und gehört hinter den Time-Lock.
3. **Spec-Lücken:** Mindestlänge und Längenbonus fehlen in der Feed-Spec. „Zu Ende geschaut“, „Wiedergabezeit“ und die Abbruch-Fälle (Videowechsel, Kill, pausiert geschlossen) fehlen in der Pause-Spec. Die Feed-Spec enthält den Rest „nur zum Tageswechsel“ und „verfallen“.
4. **Umgehungen, schnell zu schließen (je S):** „Doch nicht“ + Neustart ohne Abfrage, systemweite Musik unterdrückt die Abfrage, Router-Hintergrund/Popup ohne Abfrage (Pause gehört in den Player-Hook), Abbruch per Prozess-Kill (Fortschritt persistieren), Zeitzonen-Wechsel, UID-Schlüssel bei Import.
5. **Nicht sinnvoll schließbar:** App-Daten löschen, Zweit-App/NewPipe, Browser, Rebuild. Hier hilft nur Sichtbarkeit und Deinstallation der Schlupflöcher.
6. **UX:** Leere Ausgaben bei kurzen Videos, Pause mitten im Kontext (Podcast/Anruf), drei Zähler, stündlicher „Termin“-Anreiz. Vorschläge in Abschnitt 3.
7. **Code vs. Regeln:** Drawer, Autoplay, Related/Kommentare, Shorts und Backup-Ausschluss sind regelkonform. Die Absichtsabfrage widerspricht der Regel in drei Punkten (U-1 bis U-3).
8. **24-h-Sperre (neu):** Sie lässt Lockern sofort wirken und ersetzt damit den Impulsschutz von „Lockern erst morgen“, statt ihn zu ergänzen (S1). Empfehlung F7: strenger sofort, lockern erst nach 24 h. Weitere Punkte: Feed-Gruppen nicht sperren (S3, sonst kollidiert es mit Abonnieren/Entdecken), laufende Pause und Ausgabe nie durch Änderungen verkürzen bzw. neu würfeln (S4/S5), Ersteinrichtung braucht ein Auto-Ende (S8), „Protokoll im Export“ widerspricht dem Export-Ausschluss (S10), Rückgängig-Fenster gegen Tippfehler (U11).
9. **Nächster Schritt:** Regel-Tabelle 4.1 als `zen-docs/rules.md` übernehmen und die Anpassungsliste 4.2 abarbeiten, sobald F1–F9 entschieden sind.

---

## Entscheidungen des Nutzers (2026-10-12): alle neun Empfehlungen aus 4.3 übernommen

- F1: Deckel zählt **verschiedene** Videos; Ausgabe wird erst **nach der Absichtsabfrage** gebaut („Doch nicht" kostet nichts).
- F2: Minuten-Tageslimit ja, aber erst nach der Einstellungs-Sperre.
- F3: Keine Musik-Kanäle im Feed, kein Längenbonus für Musik.
- F4: Später-Liste läuft nach 30 Tagen ab.
- F5: Ausgabe-Takt: 60 min nach der letzten Ausgabe (nicht zur vollen Stunde).
- F6: „Später fortsetzen" ohne Pause (Podcast, Anruf): 2x pro Tag.
- F7: Strenger stellen wirkt sofort; **Lockern wirkt erst nach 24 h** und ist abbrechbar. (Ersetzt „Strenger darf während der Sperre geändert werden, Lockern sofort" aus spec-einstellungs-sperre.md.)
- F8: Feed-Gruppen werden nicht gesperrt.
- F9: Rückgängig-Fenster 5 min nach Bestätigung.
- Zusätzlich übernommen: Zustand (Ausgabe, verworfen, Später) nach Video-URL schlüsseln statt nach DB-ID; Lücken der Absichtsabfrage schließen (Neuöffnen nach „Doch nicht", systemweite Musik-Prüfung, Hintergrund/Popup-Links, Zeitzonen-/Uhr-Härtung).
