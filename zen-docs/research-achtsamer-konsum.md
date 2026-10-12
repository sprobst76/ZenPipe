# Recherche: Achtsamer Konsum, Selbstkontrolle und umgehungsfeste Limits

**Projekt:** ZenPipe (NewPipe-Fork)
**Stand:** 12.10.2026
**Bezug:** `ZenPipe-Konzept_v0.1.md`

Legende in diesem Dokument:
- **[Belegt]** = durch Studie, offizielle Doku oder Primärquelle gestützt (Quelle verlinkt)
- **[Schwach belegt]** = Einzelstudie, kleine Stichprobe, Herstellerangabe oder nur Sekundärquelle
- **[Einschätzung]** = eigene Schlussfolgerung oder Vermutung, nicht direkt belegt

---

## Kurzfazit

1. **Die stärksten Hebel für ZenPipe sind Design-Hebel, nicht Sperren.** Autoplay und Empfehlungen sind laut YouTube selbst der Motor für über 70 % der Sehzeit. Werden sie entfernt, steigt nachweislich das Gefühl, selbst zu entscheiden (Agency). Das ist billig umzusetzen und schwer zu umgehen, weil es im eigenen Code steckt.
2. **Friction wirkt, auch wenn sie klein ist.** Schon eine kurze Pause vor dem Öffnen verhindert einen relevanten Teil der Öffnungen: 13 % bei bloßer Pause, 36 % bei One Sec, 47 % bei einer mühsamen Eingabe. Je stärker die Hürde, desto mehr Frust und desto eher wird das Tool abgeschaltet.
3. **Selbst gesetzte Limits wirken** (großes Feldexperiment mit ca. 2000 Personen), aber **langfristige Wirkung ist kaum belegt**. Habits brechen nur, wenn sich der Kontext ändert.
4. **Umgehung ist das Kernproblem.** Für den Entwickler selbst gilt: Wer die App bauen kann, kann jede Sperre ausbauen. Ziel ist deshalb **Schutz vor dem Impuls im Moment, nicht vor dem entschlossenen Ich**. Dafür reichen In-App-Mechanismen mit Zeitverzögerung beim Lockern völlig.
5. **Systemweite Sperren ohne Root sind möglich, aber teuer.** Am realistischsten sind eine Bedienungshilfe (Accessibility) in einer separaten FOSS-App (z. B. Curbox/DigiPaws, TimeLimit) und die Deinstallation der Schlupflöcher. Device Owner bzw. Kiosk-Modus ist auf einem Alltagshandy mit Google-Konto praktisch nicht machbar.

---

## 1. Herausforderungen: Warum Selbstkontrolle bei Video-Feeds schwer ist

### 1.1 Gewohnheitsschleifen (Habits)

- **[Belegt]** Gewohnheiten entstehen als gelernte Verknüpfung zwischen **Kontext-Reiz** (Ort, Uhrzeit, vorherige Handlung) und **Reaktion**. Sind sie einmal gebildet, löst der Kontext die Handlung **ohne bewusstes Ziel** aus. Ziele können Gewohnheiten nur indirekt steuern.
  Wood & Neal (2007), *A New Look at Habits and the Habit-Goal Interface*, Psychological Review 114(4)
  https://dornsife.usc.edu/wendy-wood/wp-content/uploads/sites/183/2023/10/wood.neal_.2007psychrev_a_new_look_at_habits_and_the_interface_between_habits_and_goals.pdf
- **[Belegt]** In Tagebuchstudien wurden rund 45 % der Alltagshandlungen fast täglich am selben Ort wiederholt.
  Neal, Wood & Quinn (2006) https://dornsife.usc.edu/wendy-wood/wp-content/uploads/sites/183/2023/10/Neal.Wood_.Quinn_.2006_Habits_a_repeat_performance.pdf
- **[Belegt]** Ein großes Feldexperiment (ca. 2000 Smartphone-Nutzer) schätzt, dass **31 % der Social-Media-Nutzung auf Selbstkontrollprobleme zurückgehen**. Vorübergehende Zahlungen für weniger Nutzung wirkten auch **nach** dem Ende der Zahlung weiter, was für Gewohnheitsbildung spricht.
  Allcott, Gentzkow & Song (2022), *Digital Addiction*, American Economic Review 112(7)
  https://www.nber.org/system/files/working_papers/w28936/w28936.pdf

**Konsequenz für ZenPipe [Einschätzung]:** Der Auslöser ist meist nicht „Ich will Video X sehen“, sondern ein Kontext wie Langeweile, Sofa oder Warten. Eine App, die im Moment des Öffnens **das Ziel wieder ins Bewusstsein holt** (Absichtsabfrage), setzt genau an der Lücke zwischen Habit und Ziel an.

### 1.2 Autoplay und Empfehlungen

- **[Belegt, Herstellerangabe]** YouTube gab 2018 an, dass **über 70 % der Sehzeit von den eigenen Empfehlungen getrieben** werden (Neal Mohan, CES 2018).
  https://www.tubefilter.com/2018/01/11/youtube-most-watch-time-driven-by-recommendations/
- **[Belegt]** Umfrage mit 120 US-Nutzern und 13 Co-Design-Sessions: **Autoplay und Empfehlungen untergraben das Gefühl der Selbstbestimmung, Suche und Playlists stärken es.** Wer eine konkrete Absicht hat, bevorzugt Oberflächen mit mehr Kontrolle.
  Lukoff et al. (2021), *How the Design of YouTube Influences User Sense of Agency*, CHI '21
  https://arxiv.org/abs/2101.11778
- **[Schwach belegt]** Folgeprojekt **SwitchTube** (Feldstudie, 46 Personen): Ein suchzentrierter „Focus Mode“ erhöhte die Agency, **reduzierte aber die Nutzungszeit nicht**.
  https://dub.uw.edu/seminars/2022-08-03.html
- **[Schwach belegt]** UChicago-Studie zu Netflix (76 Personen): Wer Autoplay abschaltete, schaute **rund 18 Minuten kürzer pro Session**. Quelle ist eine Uni-Pressemeldung, der Volltext wurde nicht geprüft.
  https://news.uchicago.edu/story/uchicago-scientists-study-hidden-cost-netflixs-autoplay

**Konsequenz [Einschätzung]:** Empfehlungen und Autoplay zu entfernen ist der Hebel mit dem besten Verhältnis von Aufwand zu Wirkung. Allein reicht es laut SwitchTube aber nicht, um die Zeit zu senken. Dafür braucht es zusätzlich Stopp-Punkte und Limits.

### 1.3 Variable Belohnung und Endlos-Feeds

- **[Belegt, nur Laborforschung]** Variable Verstärkung, also Belohnung nach einer unvorhersehbaren Zahl von Handlungen, erzeugt das stabilste und am schwersten zu löschende Verhalten (Skinner). Die Übertragung auf Feeds („Slot Machine“) ist **plausibel, aber empirisch kaum direkt getestet**. In der Recherche fanden sich dazu nur Erklärtexte, keine Feldstudie.
- **[Belegt]** Endloses Scrollen erzeugt eine „Schleife“, die Social-Media-Sessions gegen den eigenen Willen verlängert. Die Studie untersucht, was Nutzer aus dieser Schleife herausholt.
  Rixen et al. (2023), *The Loop and Ways to Break It*, MobileHCI '23 (Honorable Mention)
  https://www.uni-ulm.de/in/mi/institut/news/article/auszeichnung-an-rixen-et-al-mobilehci/
- **[Einschätzung]** Fehlende **Stopp-Signale** (kein Ende des Feeds, kein Ende der Folge) sind der gemeinsame Nenner von Autoplay, Shorts und Endlos-Feed. Ein Feed mit Ende („Du bist durch“) erzeugt genau dieses fehlende Signal.

### 1.4 Umgehungsverhalten

- **[Belegt]** Bei Lockout-Apps sind **restriktive Mechanismen wirksamer als bloße Warnungen**, erzeugen aber **mehr Frust und Druck** (4-Wochen-Feldstudie, 36 Personen).
  Kim et al. (2019), *GoalKeeper*, IMWUT https://ic.kaist.ac.kr/files/papers/kim2019goalkeeper.pdf
- **[Belegt, niedrige Evidenzqualität]** Review zu 28 Interventionen: Reine Bewusstmachung wirkt kaum. **Sanktionen müssen „hart genug“ sein**, sonst werden sie weggeklickt.
  Biedermann, Schneider & Drachsler, *Digital self-control interventions for distracting media multitasking*
  https://publikationen.ub.uni-frankfurt.de/opus4/frontdoor/index/index/year/2022/docId/63952
- **[Schwach belegt, Anekdoten]** Typische Umgehungen bei System-Tools: zweiter Nutzer oder Gastmodus, Zeitzone oder Uhrzeit verstellen, App zur Ausnahmeliste hinzufügen, Task-Killer, Deinstallation. Der TimeLimit-Entwickler nennt neue Nutzer und Gastmodus ausdrücklich als Lücke.
  https://f-droid.org/packages/io.timelimit.android.aosp.direct/ und https://discuss.grapheneos.org/d/22587-gos-for-childs-pixel
- **[Schwach belegt]** Googles Digital-Wellbeing-App-Timer lassen sich selbst übersteuern („Honor System“). Der „Focus Mode“ hat eine eingebaute Pause von 5, 15 oder 30 Minuten.
  https://www.androidpolice.com/how-to-set-up-digital-wellbeing-android-phone/

**Zentrales Dilemma [Einschätzung]:** Zu weiche Sperren werden weggeklickt, zu harte Sperren erzeugen Reaktanz und werden deinstalliert. In der Verhaltensökonomie heißt das „Hot-Cold Empathy Gap“: Im ruhigen Zustand plant man strenge Regeln, im „heißen“ Moment will man sie loswerden. Die Lösung ist eine **Asymmetrie**: Strenger stellen geht sofort, lockern nur mit Verzögerung.

### 1.5 Der Sonderfall Ein-Personen-Fork

**[Einschätzung, aber zwingend]** Stefan ist Nutzer **und** Entwickler. Jede Sperre lässt sich durch einen Rebuild, durch `adb shell pm clear` oder durch Neuinstallation von NewPipe oder dem Browser umgehen. Umgehungsfestigkeit kann deshalb nur heißen:
- Der Umweg ist **teurer als die Lust im Moment** (Minuten statt Sekunden, Laptop nötig, Datenverlust).
- Der Umweg ist **sichtbar und bewusst**: Er wird protokolliert und taucht in der Wochenbilanz auf.

Mehr ist ohne externe Instanz (Partner, MDM) nicht erreichbar, und mehr ist auch nicht nötig.

---

## 2. Was laut Evidenz wirkt

| Maßnahme | Evidenz | Effekt (soweit bekannt) | Qualität |
|---|---|---|---|
| **Friction / Pause vor dem Öffnen** | LocknType (CHI 2019, 40 Pers., 3 Wo.) | Reine Pause verhinderte **13,1 %** der App-Öffnungen, eine 30-stellige Eingabe **47,5 %** | mittel |
| **Absichtsabfrage + Wartezeit (One Sec)** | Grüning, Riedel & Lorenz-Spreen (2023), PNAS 120(8), 280 Pers., 6 Wo. | **36 %** der Öffnungsversuche wurden danach abgebrochen; Öffnungsversuche nahmen über die Zeit ab. Herstellerangabe: −57 % Nutzung | mittel, **Interessenkonflikt**: Erstautor ist Lead Scientist bei One Sec |
| **Selbst gesetzte Tageslimits (nicht sofort übersteuerbar)** | Allcott, Gentzkow & Song (2022), AER, ca. 2000 Pers. | Limits **senken die Nutzung deutlich**, die Nachfrage nach Limits zeigt Selbstkontrollprobleme | hoch |
| **Ziel setzen + Erinnerung** | Hiniker et al. (2016), MyTime, CHI, 23 Pers., 2 Wo. | **−21 %** Zeit in „Zeitverschwendungs“-Apps, nützliche Apps unverändert | niedrig (klein, kurz) |
| **Graustufen** | Zimmermann & Sobolev (2023), Cyberpsychology, Behavior, and Social Networking 26(1), N=112, präregistriert, randomisiert | **sofortige, signifikante** Reduktion der Bildschirmzeit; Zielsetzung wirkte schwächer und langsamer; **kein** Effekt auf Wohlbefinden oder Studienleistung | mittel–hoch |
| Graustufen (weitere) | Studie 2023, Mobile Media & Communication | **−20 min/Tag**, Zahl der Entsperrungen unverändert | mittel |
| **Implementation Intentions** („Wenn X, dann Y“) | Gollwitzer & Sheeran (2006), Meta-Analyse, 94 Tests | **d ≈ 0,65** auf Zielerreichung allgemein, nicht speziell Smartphone | hoch (allgemein), Zahl aus Sekundärquellen |
| **Commitment Devices** | Bryan, Karlan & Nelson (2010), Annual Review of Economics | Es gibt Nachfrage nach Selbstbindung, sie wirkt in mehreren Feldern; harte (mit Kosten) und weiche Varianten | hoch (Übersicht) |
| Soziale Selbstbindung (stickK-Daten) | KAIST, *Sticky Goals* | Gemischte (finanziell + sozial) Bindung erfolgreicher | niedrig–mittel |
| **Autoplay aus** | Schaffner et al. (UChicago, Netflix) | ca. **−18 min pro Session** | niedrig (Pressemeldung) |
| **Empfehlungen weg / suchzentriert** | Lukoff 2021; SwitchTube | mehr Agency, aber **keine** Zeitreduktion | mittel |
| **Digitale Selbstkontroll-Tools allgemein** | Monge Roffarello & De Russis (2023), TOCHI, Meta-Analyse | **kurzfristig** weniger Zeit; **langfristige Wirkung nicht belegt**, „fördern keine neuen Gewohnheiten“; Studien meist ca. 21 Tage, ohne Kontrollgruppe | hoch (Übersicht), aber dünne Basis |

Quellen:
- LocknType: https://ic.kaist.ac.kr/files/papers/kim2019lockntype.pdf
- One Sec / PNAS: https://doi.org/10.1073/pnas.2213114120 · Herstellerseite: https://one-sec.app/research/
- Allcott et al.: https://www.nber.org/system/files/working_papers/w28936/w28936.pdf
- MyTime: https://depts.washington.edu/chilllab/?p=176
- Zimmermann & Sobolev: https://repositorio.ie.edu/entities/publication/78b87732-5c0d-494a-9769-bdfd4a501297/full
- Graustufen 2023: https://journals.sagepub.com/doi/10.1177/20501579231212062
- Gollwitzer & Sheeran: https://stafforini.com/works/gollwitzer-2006-implementation-intentions-and/
- Bryan, Karlan & Nelson: https://ideas.repec.org/a/anr/reveco/v2y2010p671-698.html
- Sticky Goals: https://koasas.kaist.ac.kr/handle/10203/288658
- Monge Roffarello & De Russis: https://elite.polito.it/news/2023/05/31/tochi-dscts (DOI 10.1145/3571810)
- Lyngs et al. (2019), Übersicht über 367 Tools: https://arxiv.org/abs/1902.00157

### Ableitungen für das Design [Einschätzung]

1. **Absichtsabfrage plus kurze Wartezeit** ist das am besten belegte „sanfte“ Muster. Es kombiniert Friction, Reflexion und einen leichten Ausweg und passt exakt zum Konzept-MVP.
2. **Implementation Intention statt freier Frage:** Statt „Was willst du schauen?“ besser eine konkrete Antwort verlangen, etwa „Ich schaue [Kanal/Video] und höre auf, wenn [Ende/Zeit]“. Danach gezielt **zur Suche oder zum Abo** führen, nicht zum Feed.
3. **Limits werden im kühlen Zustand gesetzt und sind im heißen Zustand nicht sofort änderbar.** Das ist der Kern des AER-Experiments: Limits, die sich „nicht sofort übersteuern lassen“.
4. **Graustufen in der App** sind gut belegt und billig. In-App wirkt es nur auf ZenPipe, nicht auf das ganze Handy. Ob der Effekt in-app gleich groß ist, ist **nicht belegt**.
5. **Langfristigkeit:** Keine Studie zeigt dauerhafte Habit-Änderung. Darum lieber **Kontext umgestalten** (keine Empfehlungen, Feed mit Ende, kein Autoplay) als nur auf Sperren zu setzen. Die Umgestaltung wirkt dauerhaft, solange die App benutzt wird.
6. **Reaktanz vermeiden:** Ruhige, nicht moralisierende Texte. Immer einen legitimen Ausweg anbieten, etwa „Nur Audio“, „Später-Liste“ oder „5 Minuten mehr, wird protokolliert“. Ein Totalverbot treibt in die YouTube-App.

---

## 3. Umgehungsfeste Sperren bauen

### 3.1 Innerhalb der App (ZenPipe selbst)

| Muster | Wie | Schützt gegen | Lücke | Aufwand |
|---|---|---|---|---|
| **Asymmetrische Änderung (Time-Lock)** | Strenger stellen wirkt **sofort**. Lockern (Limit hoch, Feature an) wird als „ausstehende Änderung“ gespeichert und wird erst **nach X Stunden oder am nächsten Tag** wirksam. Abbrechen ist jederzeit möglich. | Impulsives Hochsetzen im Moment | Daten löschen bzw. Neuinstallation | S–M |
| **Cooldown beim Öffnen der Zen-Einstellungen** | Countdown (z. B. 60 s, eskalierend) und Pflichttext („Warum willst du das ändern?“), bevor der Bildschirm editierbar ist | Spontanes Abschalten | Geduld | S |
| **Sperrzeit / Lock-Modus** | Zen-Einstellungen für einen gewählten Zeitraum (z. B. 7 oder 30 Tage) **komplett gesperrt**; nur „strenger“ bleibt erlaubt | Wiederholtes Herumschrauben | Daten löschen | S |
| **Partner-Code (Accountability)** | Beim Einrichten gibt ein Partner einen PIN ein, den Stefan nicht kennt. Lockern nur mit PIN **oder** mit Time-Lock. Variante: Partner-Freigabe per Challenge/Response (HMAC-Code, offline, Partner hat das Geheimnis) | Starke Impulse, auch mit Geduld | Partner gibt nach; Daten löschen | M (PIN) / L (Challenge) |
| **Manipulation der Uhr erkennen** | Zeit monoton führen (`SystemClock.elapsedRealtime()` + zuletzt gesehene Wanduhr). Rückwärtssprünge erkennen, im Zweifel die **strengere** Auslegung wählen. Bonus: Die App spricht ohnehin mit YouTube, der HTTP-`Date`-Header liefert eine unabhängige Uhrzeit | Uhr zurückdrehen, um Tageslimit zurückzusetzen | – | S |
| **„Daten löschen“ teuer machen** | Abos, Playlists und Verlauf liegen in derselben App. Löschen = Abos weg (sofern nicht exportiert). Limit-Zustand **nicht** in Auto-Backup aufnehmen bzw. bewusst entscheiden | Reset-Umgehung | Export/Import-Workflow | S (Entscheidung) |
| **Umgehungs-Protokoll** | Jede Lockerung, jedes „5 Minuten mehr“, Uhr-Anomalien und App-Neuinstallationen (Erkennung über `firstInstallTime`) landen in einer **Wochenbilanz** | Unbewusstes Umgehen | – | S–M |
| **Escape-Ventil statt Härte** | Nach dem Limit: nur Audio, nur „Später-Liste“, oder ein Notfall-Video pro Tag mit langer Wartezeit | Reaktanz bzw. Abwandern in die YouTube-App | – | M |

**Empfehlung für die Lock-Mechanik [Einschätzung]:** Ein einziges, durchgängiges Prinzip: **„Lockern dauert bis morgen 4:00 Uhr“**, mit Tagesgrenze 4:00 statt Mitternacht, damit Nachtsessions nicht den nächsten Tag freischalten. Zusätzlich optional ein Partner-PIN als Sofort-Freigabe. Das ist leicht verständlich, leicht zu implementieren (gespeicherter `pendingConfig` + `effectiveAt`) und deckt den „heißen Moment“ vollständig ab.

### 3.2 Auf Android-Systemebene (ohne Root)

| Mechanismus | Was es kann | Voraussetzungen / Nachteile | Bewertung für ZenPipe |
|---|---|---|---|
| **Digital Wellbeing App-Timer / Focus Mode** (Google, Bordmittel) | Tageslimit pro App (z. B. YouTube, Chrome, NewPipe). Danach wird das Icon grau, Reset um Mitternacht | Selbst übersteuerbar, Focus Mode hat eine Pausen-Taste; ein PIN gibt es nur über Family Link | **Sofort nutzbar** für die Schlupflöcher (YouTube-App, Browser) |
| **Device Admin API** | Historisch: Deinstallationsschutz | Seit Android 7 **lässt sich das Entfernen des Admins nicht mehr verhindern**; seit Android 9/10 sind viele Policies veraltet oder werfen eine `SecurityException`. Zum Deinstallieren muss man den Admin nur vorher deaktivieren. [Belegt] https://developers.google.com/android/work/device-admin-deprecation | Nur ein kleiner Zusatzschritt, **nicht lohnend** |
| **Accessibility Service** (Bedienungshilfe) | Erkennt Vordergrund-App und URLs, kann blockieren, Shorts ausblenden, „Home“ drücken. So arbeiten Curbox/DigiPaws und TimeLimit | **Sehr mächtige Berechtigung**. Ab Android 13 „eingeschränkte Einstellungen“ für sideloaded Apps (Freigabe pro App nötig) [Belegt] https://esper.io/blog/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis; ab Android 15/16 zusätzlich „Enhanced Confirmation Mode“ https://androidauthority.com/android-15-enhanced-confirmation-mode-3436697. Abschaltbar in den Einstellungen, oft von Herstellern (auch Motorola) im Hintergrund beendet | **Nicht in ZenPipe einbauen**, stattdessen eine separate FOSS-App nutzen (siehe unten) |
| **Usage Stats** (`PACKAGE_USAGE_STATS`) | Nutzungszeit anderer Apps auslesen, z. B. für eine Wochenbilanz „YouTube-App: 40 min“ | Spezialberechtigung, nur Anzeige, kein Blocken | Optional später, gering |
| **Overlay** (`SYSTEM_ALERT_WINDOW`) | Sperrbildschirm über anderen Apps | Nur zusammen mit Usage Stats oder Accessibility sinnvoll | – |
| **Lock Task / Kiosk** | App lässt sich nicht verlassen | Ohne Device Owner nur **Bildschirmfixierung**, die der Nutzer jederzeit beenden kann. [Belegt] https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode | Unpassend: ZenPipe soll nicht der einzige Bildschirm sein |
| **Device Owner** (per `adb shell dpm set-device-owner`) | Echter Deinstallationsschutz, Apps sperren oder verstecken, Nutzer-Anlage verbieten | Nur auf einem Gerät **ohne Konten und ohne Zweitnutzer** einrichtbar, also Google-Konto entfernen. Rückgängig oft nur per Werksreset; als Dev-/Testweg gedacht. [Belegt] https://bayton.org/android/android-enterprise-faq/can-i-set-device-owner-without-factory-reset/ · OwnDroid: https://github.com/BinTianqi/OwnDroid | **Auf dem Alltagshandy unrealistisch**; nur für ein dediziertes Zweitgerät |
| **App-Deinstallation per adb** | `adb shell pm uninstall --user 0 com.google.android.youtube` entfernt auch vorinstallierte Apps für den Nutzer (kein Root) | Lässt sich über Play Store oder `cmd package install-existing` zurückholen | **Gute, billige Friction** gegen die YouTube-App |
| **Private DNS / DNS-Sperre** | youtube.com systemweit sperren | **Sperrt ZenPipe mit**, weil ZenPipe selbst youtube.com und googlevideo.com abfragt | **Ungeeignet** [Einschätzung] |

**Separate FOSS-Blocker (statt Eigenbau):**
- **Curbox (ehemals DigiPaws)**, GPL-3.0, auf F-Droid: App- und URL-Blocking, Shorts/Reels-Blocker, Graustufen, Cooldowns, Anti-Uninstall, ohne INTERNET-Berechtigung, nutzt Accessibility.
  https://f-droid.org/packages/neth.iecal.curbox/ · https://f-droid.org/packages/nethical.digipaws/
- **TimeLimit**, auf F-Droid: Kategorien, Zeitfenster, Tages- und Mehrtageslimits, Device-Admin- oder Device-Owner-Unterstützung, protokolliert das Abschalten des Admins.
  https://f-droid.org/packages/io.timelimit.android.aosp.direct/ · https://timelimit.io/en

### 3.3 Die Schlupflöcher realistisch bewertet

| Schlupfloch | Gegenmaßnahme ohne Root | Restrisiko |
|---|---|---|
| **Offizielle YouTube-App** | `pm uninstall --user 0` per adb, sonst deaktivieren + Digital-Wellbeing-Timer (z. B. 0–5 min) | Neuinstallation über Play Store in 30 s, aber bewusst |
| **NewPipe parallel** (andere applicationId) | Nach Datenmigration (NewPipe-Export → ZenPipe-Import) deinstallieren. F-Droid zeigt NewPipe weiter an, ein Neu-Install dauert aber Minuten | mittel |
| **Browser (m.youtube.com)** | Curbox-URL-Blocking (Accessibility) **oder** Firefox + uBlock-Origin-Filter `||youtube.com^` (in Firefox Android leicht wieder abschaltbar) **oder** Digital-Wellbeing-Timer auf den Browser (zu grob) | mittel–hoch; ehrlichste Lösung ist die Accessibility-App |
| **ZenPipe-Daten löschen / Neuinstallation** | Daten löschen kostet Abos, Neuinstallation wird erkannt (`firstInstallTime`) und protokolliert | gering–mittel |
| **Uhrzeit verstellen** | monotone Uhr + Server-`Date`-Header | gering |
| **Rebuild ohne Sperren (Entwickler!)** | Nicht verhinderbar. Mildern: Lock-Logik im Release-Build per Build-Flag nicht abschaltbar machen; Debug-Build hat eigenes Paket ohne Abo-Daten | unvermeidbar; Laptop-Hürde ist aber hoch genug |
| **Zweitnutzer / Gastmodus** | Ohne Device Owner nicht verhinderbar | gering (Aufwand groß, kaum ein Impuls-Weg) |

### 3.4 Was für einen Ein-Personen-Fork sinnvoll ist [Einschätzung, Empfehlung]

1. **ZenPipe bleibt eine normale App ohne Spezialberechtigungen.** Keine Accessibility, kein Device Admin, kein Usage Stats im MVP. Gründe: F-Droid-Review, Wartbarkeit (kleiner Patch-Satz auf Upstream), Vertrauen und Akkuverbrauch. Die Umgehungsfestigkeit kommt aus dem **Time-Lock-Prinzip** in der App.
2. **Schlupflöcher einmalig schließen:** YouTube-App per adb für den Nutzer entfernen, NewPipe nach der Migration deinstallieren. Für den Browser bei Bedarf **Curbox** einsetzen, statt Systemfunktionen nachzubauen.
3. **Device Owner nur bei echtem Bedarf** und dann auf einem Zweitgerät, nicht auf dem Defy mit Google-Konto.
4. **Akzeptieren, dass der Entwickler gewinnt.** Ziel ist Impuls-Schutz und Sichtbarkeit (Protokoll, Wochenbilanz), nicht Unumgehbarkeit.

Hinweis zum Testgerät **[Belegt bzw. Einschätzung]:** Das Motorola Defy läuft unter Android 11. Die „eingeschränkten Einstellungen“ für Accessibility (ab Android 13) betreffen es also noch nicht. Bei einem späteren Geräte-Upgrade sollte man das neu prüfen.

---

## 4. Feature-Empfehlungen (priorisiert)

Aufwand: **S** ≤ 1 Tag, **M** 2–4 Tage, **L** ≥ 1 Woche (Einzelentwickler mit KI-Unterstützung, inklusive Test; grobe Schätzung).
Umgehungsrisiko: wie leicht man das Feature **im Moment des Impulses** aushebelt.

### MVP (v0.1 – v0.3)

| # | Feature | Evidenz | Aufwand | Umgehungsrisiko | Bemerkung |
|---|---|---|---|---|---|
| 1 | **Empfehlungen, „Nächstes Video“, Kommentare und Autoplay hart entfernen** (nicht nur Default aus, sondern Option entfernen) | stark (70 %-Angabe, Lukoff, Netflix) | S–M | **sehr gering** (nur per Rebuild) | NewPipe hat dafür bereits Einstellungen (`autoplay_key`, `auto_queue_key`, Anzeige von Kommentaren und nächstem Video). Patch: Default setzen und Option ausblenden |
| 2 | **Seitenleiste/Hauptseite reduzieren** (nur Abos, Feed, Playlists, Suche; Trends/Kiosk/Live entfernen) | indirekt (Agency) | S | sehr gering | Kleiner, gut rebasebarer Patch |
| 3 | **Absichtsabfrage beim Start** mit kurzer Wartezeit (z. B. 5–10 s), Antwort als Implementation Intention („Ich schaue … bis …“), danach direkt in Suche, Abo oder Später-Liste | stark (One Sec, LocknType, Gollwitzer) | M | gering–mittel (Wegklicken möglich, kostet aber Sekunden) | Kernfeature. Gilt auch beim Öffnen über geteilte Links (Intent-Filter!), sonst Schlupfloch |
| 4 | **Shorts konsequent ausblenden** (Kanal-Tab Shorts, Shorts-Links) | plausibel | S | gering | NewPipe hat `show_channel_tabs_shorts` |
| 5 | **Feed mit Ende** („Du bist durch, X neue Videos seit gestern“) statt endlosem Nachladen | mittel (Rixen, Stopp-Signale) | M | gering | |

### Danach (v0.4 – v1.0)

| # | Feature | Evidenz | Aufwand | Umgehungsrisiko | Bemerkung |
|---|---|---|---|---|---|
| 6 | **Tageslimit** mit Escape-Ventil (danach nur Audio oder Später-Liste) | stark (Allcott) | M | mittel ohne Time-Lock, gering mit | Tagesgrenze 4:00 |
| 7 | **Time-Lock für Zen-Einstellungen** (Lockern erst ab nächstem Tag, Strengerstellen sofort, Uhr-Manipulation erkennen) | stark (Commitment, „nicht sofort übersteuerbar“) | M | gering (nur Daten löschen oder Rebuild) | **Voraussetzung** dafür, dass 6 und 8 ernst genommen werden |
| 8 | **Sitzungs-Timer mit sanftem Ende** (Ende des laufenden Videos abwarten, dann Pause-Screen) | mittel (MyTime) | S–M | mittel | Video nicht hart abbrechen (Reaktanz) |
| 9 | **Graustufen in der App** (optional, z. B. ab 21 Uhr oder nach dem Limit) | gut (für systemweit) | S | gering | In-App per `ColorMatrix` auf der Root-View bzw. den Thumbnails; Wirkung in-app nicht belegt |
| 10 | **Wochenbilanz + Umgehungs-Protokoll** (Zeit, Absichten erfüllt ja/nein, Lockerungen) | schwach (Self-Tracking allein wirkt kaum) | M | – | Nur zusammen mit 6/7 sinnvoll |
| 11 | **Thumbnails ausblenden oder abschwächen** (nur Titel-Liste) | [Einschätzung] | S | gering | Klickanreiz reduzieren |

### Später / optional

| # | Feature | Aufwand | Umgehungsrisiko | Bemerkung |
|---|---|---|---|---|
| 12 | **Partner-PIN** (Accountability) als Sofort-Freigabe beim Lockern | S–M | gering | Einfache PIN-Variante reicht |
| 13 | Partner-Freigabe per Challenge/Response (HMAC, offline) | L | sehr gering | Nur falls 12 zu weich ist |
| 14 | Zeitfenster („nur 19–21 Uhr“) | M | mittel | |
| 15 | Usage-Stats-Anzeige der YouTube-App und des Browsers in der Wochenbilanz | M | – | Spezialberechtigung, eher nicht |
| 16 | Eigener Accessibility-Blocker gegen Schlupflöcher | L | mittel | **Nicht empfohlen**, besser Curbox nutzen |

### Empfohlene Reihenfolge

**1 → 2 → 3 → 4** (entspricht dem Konzept-MVP plus Shorts), dann **7 vor 6**: Erst das Time-Lock bauen, dann das Limit. Ein Limit ohne Time-Lock wird im heißen Moment einfach hochgesetzt.

---

## 5. Offene Punkte / Unsicherheiten

- Die exakten PNAS-Zahlen zur Nutzungsreduktion über 6 Wochen konnten wegen einer Zugriffssperre nicht im Volltext geprüft werden. Belegt ist der Abbruch von 36 % der Öffnungsversuche, die 57 % sind eine Herstellerangabe.
- Ob Graustufen **nur innerhalb einer App** ähnlich wirken wie systemweit, ist offen.
- Zur Langzeitwirkung aller genannten Maßnahmen gibt es keine Daten (TOCHI-Meta-Analyse).
- Umgehungsraten von Self-Control-Tools: Dazu wurde keine quantitative Studie gefunden, nur Anekdoten und Foren.
