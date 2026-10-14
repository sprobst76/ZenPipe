# Recherche: ZenPipe auf F-Droid bringen (erste Entwicklungsversion)

**Projekt:** ZenPipe (NewPipe-Fork)
**Stand:** 12.10.2026
**Bezug:** `ZenPipe-Konzept_v0.1.md`, `AGENTS.md`

Legende: **[Belegt]** = Primärquelle verlinkt · **[Schwach belegt]** = Sekundärquelle oder Forum · **[Einschätzung]** = eigene Schlussfolgerung

---

## Empfehlung auf einen Blick

> **Empfohlen:** Stufenweise vorgehen.
> 1. **Jetzt (Dev-Builds):** Eigenes F-Droid-Repo, automatisch per GitHub Actions gebaut. Die APKs liegen in GitHub Releases, der Index auf GitHub Pages. Stefan trägt das Repo einmalig im F-Droid-Client ein und bekommt Updates wie gewohnt. Mit Obtainium funktioniert dieselbe Release-Quelle ohne weiteren Aufwand.
> 2. **Ab stabiler v1.0:** Antrag bei **IzzyOnDroid**. Das ist schnell und nutzt die eigenen signierten APKs.
> 3. **Optional später:** **Offizielles F-Droid** mit reproduzierbarem Build und Entwickler-Signatur (wie NewPipe und Tubular). Dadurch haben alle Quellen dieselbe Signatur, und man kann zwischen ihnen wechseln.
>
> **Sofort festlegen (nicht mehr änderbar ohne Neuinstallation):** applicationId, Signaturschlüssel, versionCode-Schema.

---

## 0. Ausgangslage im Repo (geprüft am 12.10.2026)

- `buildSrc/src/main/kotlin/ProjectConfig.kt`: `NEWPIPE_APPLICATION_ID_OLD = "org.schabi.newpipe"`, `NEWPIPE_VERSION_CODE = 1015`, `NEWPIPE_VERSION_NAME = "0.29.1"`. Daneben gibt es `NEWPIPE_APPLICATION_ID_NEW = "net.newpipe.app"` für `shared`/`desktopApp` (Upstream-Refactor).
- `app/build.gradle.kts`: `applicationId = NEWPIPE_APPLICATION_ID_OLD`, `app_name = "NewPipe"`. Der Debug-Build hängt `.debug` an, `continuous` hängt `.continuous` an. **Kein Release-SigningConfig.** Upstream baut unsigniert (`.github/workflows/build-release-apk.yml`) und signiert außerhalb.
- JDK-Toolchain 21, compileSdk 37, minSdk 23, targetSdk 35.
- `README.md` von Upstream: *„NewPipe“ ist eine eingetragene Wortmarke, verletzende Apps werden nicht toleriert.* Dazu kommt der Hinweis, dass Forks nicht in den Play Store dürfen.
- `fastlane/metadata/android/*` enthält NewPipe-Beschreibungen in vielen Sprachen. Diese müssen für ZenPipe ersetzt werden.

---

## 1. Grundsatzentscheidungen vor dem ersten Build

### 1.1 applicationId

- **[Belegt]** F-Droid verlangt von Forks eine **neue applicationId**, einen **neuen Namen**, ein **neues Icon** und angepasste Strings. Eine applicationId, die auf eine fremde Domain verweist, wird abgelehnt.
  https://f-droid.org/docs/Inclusion_Policy/
- **[Belegt]** IzzyOnDroid verlangt einen eindeutigen Paketnamen und einen unterscheidbaren Anzeigenamen und Icon, dazu eine Nennung des Originalprojekts.
  https://izzyondroid.org/docs/general/AppInclusionPolicy/
- **[Belegt]** Vorbilder: Tubular = `org.polymorphicshade.tubular`, PipePipe = `InfinityLoop1309.NewPipeEnhanced`. Beide sind im offiziellen F-Droid.
  https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/org.polymorphicshade.tubular.yml · https://f-droid.org/packages/InfinityLoop1309.NewPipeEnhanced/

**Empfehlung:** `io.github.sprobst76.zenpipe`
Begründung: Der Namensraum gehört nachweislich dir (GitHub-Account), es ist kein Domain-Kauf nötig, und der Name enthält kein „newpipe“. Alternative bei eigener Domain: `de.<deinedomain>.zenpipe`.
- `namespace` (Kotlin/Java-Package `org.schabi.newpipe`) **nicht ändern**. Die applicationId ist davon unabhängig, und eine Umbenennung des Namespace würde jeden Upstream-Merge sprengen. Die NewPipe-Markenrichtlinie nimmt interne Code-Strukturen ausdrücklich aus.
- Debug und Dev bekommen eigene Suffixe (`.debug`, `.dev`). Dann laufen Release, Dev-Build und das Original-NewPipe parallel.

### 1.2 Name, Branding, Marke

- **[Belegt]** Die NewPipe-Markenrichtlinie: Abgeleitete Apps dürfen „NewPipe“ nicht im Namen führen (Frist für bestehende Derivate war der 30.04.2025). „Downloader for NewPipe“ ist für **Zusatzsoftware** erlaubt, Derivate sind aber keine Zusatzsoftware. Websites müssen klar sagen, dass keine Verbindung zu TeamNewPipe besteht und keine Billigung durch TeamNewPipe vorliegt.
  https://newpipe-ev.de/policy/trademark/
- **[Einschätzung]** „ZenPipe“ verletzt die Wortmarke „NewPipe“ nicht. Eine kurze Suche ergab keine gleichnamige Android-App. Formulierung in Beschreibungen: „ein Fork von NewPipe; nicht mit TeamNewPipe verbunden“. Eine **Markenrechtsprüfung** (DPMA/EUIPO) wurde **nicht** gemacht.
- Zu ändern: `app_name` (in allen `resValue`), Launcher-Icon (inklusive adaptive/monochrome), `fastlane/metadata/android/en-US` und `de` (title, short/full description, Screenshots), About-Screen und Links (Spenden-, Website- und Issue-Links zeigen sonst auf NewPipe).
- Lizenzhinweis GPLv3 und Copyright der NewPipe-Autoren beibehalten.

### 1.3 Signaturschlüssel

- **[Belegt, Android-Grundlage]** Android installiert Updates nur mit **derselben Signatur**. Geht der Schlüssel verloren, sind keine Updates mehr möglich: deinstallieren, und die Daten sind weg. Das betrifft auch den Wechsel zwischen Quellen. NewPipe erklärt das selbst: Das eigene Repo und GitHub-Releases nutzen denselben Schlüssel, das F-Droid-Build einen anderen, und diese sind nicht kompatibel (Upstream-`README.md`, Abschnitt Installation).
- **Empfehlung:**
  - **Zwei getrennte Schlüssel:** (a) **App-Signaturschlüssel** (RSA 4096, Gültigkeit ≥ 25 Jahre) und (b) **F-Droid-Repo-Indexschlüssel** (erzeugt `fdroid init`). Optional (c) einen eigenen Dev-Schlüssel für `.dev`-Builds.
  - Keystore + Passwörter in den Passwortmanager und eine **Offline-Kopie** (USB-Stick oder Papier-Base64). Niemals ins Git-Repo. `*.jks`/`*.keystore` in `.gitignore` prüfen.
  - Signieren in CI: Keystore als Base64 in GitHub Secret, Passwörter als eigene Secrets. Gradle liest sie aus Umgebungsvariablen (nicht als Kommandozeilen-Argumente, siehe globale Regel).
  - Fingerprint (SHA-256) notieren. Er wird später für `AllowedAPKSigningKeys` (F-Droid) und als Verifikationshinweis in der README gebraucht.
  - **apksigner aus build-tools 34** verwenden, falls offizielles F-Droid mit Reproducible Builds geplant ist. Laut F-Droid erzeugt apksigner ab 35.0.0-rc1 APKs, deren Signatur F-Droid nicht kopieren kann. [Belegt] https://f-droid.org/docs/Reproducible_Builds/

### 1.4 versionCode-Schema

- **[Belegt]** NewPipe nutzt fortlaufende Codes (0.29.0 → 1014, 0.29.1 → 1015). Tubular hat für 0.28.4 den Code 1009 übernommen, also NewPipes Schema.
  https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/org.schabi.newpipe.yml
- **Problem:** ZenPipe braucht **mehrere eigene Releases pro Upstream-Version** und muss nach jedem Upstream-Rebase trotzdem monoton steigen.
- **Empfehlung:** `versionCode = upstreamCode * 1000 + zenRelease`. Beispiel: auf Basis von 1015, erstes ZenPipe-Release = **1015001**, zweites = 1015002. Nach Rebase auf 1016 folgt 1016001. Das passt weit unter das Maximum von 2 100 000 000.
  `versionName = "0.29.1-zen.1"` (bzw. eigenes SemVer `0.1.0` mit Upstream im Changelog; wichtig ist nur, dass es zum Git-Tag passt).
- **Dev/Nightly-Builds** (eigene applicationId `.dev`): `versionCode` aus der Commit-Anzahl (`git rev-list --first-parent --count HEAD`) oder einem Zeitstempel. Das ist die Empfehlung der F-Droid-Doku für Nightlies. [Belegt] https://f-droid.org/en/docs/Publishing_Nightly_Builds/
- Den `versionCodeOverride`-Hook im `build.gradle.kts` (System-Property) beibehalten. Er passt gut für CI.
- **Fastlane-Changelogs** heißen `fastlane/metadata/android/<lang>/changelogs/<versionCode>.txt`. Sie werden von F-Droid und IzzyOnDroid gelesen.

### 1.5 Datenmigration von NewPipe

**[Einschätzung, Funktion in NewPipe bekannt]:** NewPipe → Einstellungen → Sichern und Wiederherstellen → „Datenbank exportieren“ (ZIP) → in ZenPipe importieren. Abos, Playlists und Verlauf werden so übernommen. Danach kann NewPipe deinstalliert werden, was zugleich ein Schlupfloch schließt (siehe `research-achtsamer-konsum.md`). Ob Importe zwischen Upstream- und Fork-Version kompatibel sind, hängt von der DB-Version ab. Darum zeitnah zur gleichen Upstream-Basis migrieren.

---

## 2. Option (a): Eigenes F-Droid-Repo

### 2.1 Wie es funktioniert

- **[Belegt]** `fdroid init` erzeugt `config.yml` und den Repo-Signaturschlüssel. APKs kommen nach `repo/`, `fdroid update -c` erzeugt Metadaten-Skelette und den signierten Index. Hochladen ist per `fdroid deploy` möglich (rsync/SSH oder Git-Mirror) oder durch Kopieren auf einen beliebigen HTTPS-Server. Schlüssel und `config.yml` gehören **nicht** auf den Webserver.
  https://f-droid.org/docs/Setup_an_F-Droid_App_Repo/
- **[Belegt]** In `config.yml` gibt es `servergitmirrors`, das das Repo in ein Git-Remote (GitHub/GitLab) pusht und automatisch als Mirror einträgt. Dazu kommen `repo_url` (muss auf `/repo` enden), `repo_name`, `keystore`, `repo_keyalias`, `archive_older` (Anzahl der Versionen im Haupt-Repo) und optional `binary_transparency_remote`.
  https://gitlab.com/fdroid/fdroidserver/-/raw/master/examples/config.yml
- **[Belegt]** Für Debug-/Nightly-Builds gibt es `fdroid nightly`. Es signiert mit dem Debug-Keystore und pusht in ein `<projekt>-nightly`-Git-Repo, das Keystore-Secret heißt `DEBUG_KEYSTORE`. Dazu gibt es Beispiele für GitHub Actions. F-Droid warnt ausdrücklich, dass das **nicht** dieselbe Sicherheit wie ein normales Repo bietet.
  https://f-droid.org/en/docs/Publishing_Nightly_Builds/
- **[Belegt]** Fertige Vorlagen für ein „F-Droid-Repo per GitHub Actions“: xarantolus/fdroid (Repo wird über `raw.githubusercontent.com/.../fdroid/repo?fingerprint=…` eingebunden) und das Template GAF-Droid.
  https://github.com/xarantolus/fdroid · https://git.tdem.in/GAF-Droid/GAF-Droid-Template
- **Auf dem Gerät:** F-Droid → Einstellungen → Paketquellen → „+“ → URL `https://<host>/fdroid/repo?fingerprint=<SHA256>` eintragen bzw. QR-Code scannen. Mit Fingerprint in der URL prüft der Client den Index-Schlüssel beim ersten Kontakt (Trust on First Use). [Schwach belegt, F-Droid-Standardverhalten, Fingerprint-Parameter siehe xarantolus]
- **Updates auf dem Defy (Android 11) [Einschätzung]:** Der F-Droid-Client zeigt neue Versionen an. Die Installation braucht aber **einen Tipp pro Update**. Unbeaufsichtigte Updates ohne Privileged Extension sind erst ab Android 12 (`PackageInstaller.SessionParams.setRequireUserAction`) möglich, und auch dann nur, wenn F-Droid der Installer der App ist. Für Android 14+ hat F-Droid die Update-Ownership integriert. [Belegt] https://f-droid.org/2024/04/18/twif.html
  → Wichtig: Die App **über F-Droid** installieren, nicht per `adb install`, sonst ist F-Droid nicht Installer-of-record.
- **Dieselbe applicationId in mehreren Repos** (eigenes Repo + später IzzyOnDroid/F-Droid): Der Client bietet nur Updates mit passender Signatur an. Sind alle Quellen mit **deinem** Schlüssel signiert, ist das unkritisch. [Einschätzung]

### 2.2 Konkretes Setup (empfohlen)

```
GitHub: sprobst76/ZenPipe
  ├─ Tag v0.1.0-zen.1  ──► Workflow "release":
  │     ./gradlew assembleRelease (Keystore aus Secrets, versionCode 1015001)
  │     → APK an GitHub Release anhängen
  │     → fdroid update (Repo-Key aus Secret) → Index + APK nach Branch gh-pages
  │        (oder separates Repo sprobst76/zenpipe-fdroid)
  └─ GitHub Pages: https://sprobst76.github.io/zenpipe-fdroid/fdroid/repo
```

- Für eine Dev-Version reicht **ein** Kanal (Release-Signatur). Optional ein zweiter Kanal `…/fdroid-dev/repo` mit `.dev`-Builds über `fdroid nightly`.
- **Speicherplatz:** Mit `archive_older: 3` bleiben nur 3 APKs im Repo. NewPipe-APKs sind gut 10–15 MB, das passt zu den GitHub-Pages-Grenzen (1 GB). Alternative: Die APKs nur in GitHub Releases halten und im Index darauf verlinken. Das ist komplizierter, für den Anfang lieber nicht.

### 2.3 Bewertung

| | |
|---|---|
| Zeit bis zur ersten Installation | **1 Tag** (Signing + Workflow + Repo) |
| Kontrolle | voll, kein Review |
| Updates | sofort nach dem Tag |
| Nachteile | eigene Schlüsselverwaltung (2 Keys), Workflow-Pflege, Nutzer müssen das Repo manuell hinzufügen |
| Passt für | Dev-Builds, Eigenbedarf, kleine Testergruppe |

---

## 3. Option (b): Offizielles F-Droid (fdroiddata)

### 3.1 Anforderungen

- **[Belegt]** Nur FLOSS-Code, kompletter FLOSS-Build ohne proprietäre Build-Tools. Binärabhängigkeiten nur aus vertrauenswürdigen Maven-Repos und frei lizenziert. Kein Firebase, GMS oder Crashlytics, keine Tracking- oder Werbe-SDKs. Undeklarierte Anti-Features führen zur Ablehnung. Forks brauchen eine neue applicationId, einen neuen Namen, ein neues Icon und angepasste Strings. Marken- und Urheberrechte müssen geklärt sein.
  https://f-droid.org/docs/Inclusion_Policy/
- **[Belegt]** Anti-Feature **`NonFreeNet`** („hängt von einem proprietären Netzdienst ab“) gilt für jeden YouTube-Client. NewPipe und Tubular führen genau dieses Merkmal mit dem Text „Depends on Youtube for videos.“. Das ist ein Warnhinweis, **kein Ausschlussgrund**.
  https://f-droid.org/docs/Anti-Features/ · https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/org.schabi.newpipe.yml
- **[Belegt]** NewPipe ist ACRA-Crash-Reporting nur per E-Mail und nur durch den Nutzer selbst ausgelöst. Das gilt bei F-Droid **nicht** als Tracking (MaintainerNotes in der NewPipe-Metadatei). Für ZenPipe muss die Crash-Report-Adresse auf eine eigene umgestellt werden, sonst landen Fork-Abstürze bei TeamNewPipe.
- **[Einschätzung]** YouTube-Clients sind bei F-Droid etabliert (NewPipe, Tubular, PipePipe, LibreTube). Die Aufnahme eines weiteren NewPipe-Forks ist grundsätzlich unproblematisch, sofern er sich klar unterscheidet.

### 3.2 Ablauf

- **[Belegt]** Release-Commits taggen (`v0.1.0-zen.1`), Fastlane-Metadaten ins App-Repo legen, `fdroiddata` auf GitLab forken, Branch = applicationId, `metadata/io.github.sprobst76.zenpipe.yml` anlegen. Lokal oder in der CI `fdroid readmeta`, `rewritemeta`, `checkupdates`, `lint` und `build` ausführen, dann einen Merge Request „New App: …“ stellen. Nach dem Merge dauert es **ca. 24–48 h**, bis die App erscheint (Build-Zyklus und Signatur mit manuellem Keystore-Zugriff).
  https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- **[Schwach belegt]** Die Review-Dauer **vor** dem Merge ist nicht garantiert. Ehrenamtliche, „permanent unterbesetzte“ Reviewer; Forenberichte nennen Wartezeiten von mehreren Wochen ohne Rückmeldung.
  https://forum.f-droid.org/t/f-droid-submission-status-no-update-since-3-weeks-repostore/34091 · https://android.izzysoft.de/articles/named/fdroid-intro-4
  → **Realistisch: 2–8 Wochen bis zur ersten Veröffentlichung**, danach Updates mit einigen Tagen Verzug. [Einschätzung]

### 3.3 Signatur: F-Droid-Key vs. Reproducible Build

- **Standard:** F-Droid baut und signiert mit **seinem eigenen Schlüssel**. Diese APKs sind **inkompatibel** mit deinem eigenen Repo und deinen GitHub-Releases. Ein Wechsel geht nur per Deinstallation und Datenexport.
- **Besser [Belegt]:** **Reproduzierbarer Build mit Entwickler-Signatur.** In der Metadatei stehen `Binaries:` (URL zu deinen GitHub-Release-APKs) und `AllowedAPKSigningKeys:` (dein SHA-256). F-Droid baut aus dem Quellcode, kopiert deine Signatur auf sein unsigniertes Build und veröffentlicht **deine** APK nur, wenn beide bitgleich sind. Andernfalls wird die Version übersprungen. So machen es **NewPipe** (2 erlaubte Keys) und **Tubular**.
  https://f-droid.org/docs/Reproducible_Builds/
- Anforderungen an die Reproduzierbarkeit: identisches JDK (der Upstream-Build nutzt JDK 21 via Toolchain; die F-Droid-Rezepte für NewPipe installieren das JDK per `sudo`-Block aus Debian), festgelegte `buildToolsVersion`, keine Zeitstempel im Build, gleiche Gradle- und AGP-Versionen. Unbenutzte JDKs entfernen.
- **[Einschätzung, prüfen]** Den „Dependency metadata“-Block von AGP im APK-Signing-Block abschalten (`dependenciesInfo { includeInApk = false; includeInBundle = false }`). Er ist mit Googles Schlüssel verschlüsselt, und F-Droid-Scanner bzw. -Reviewer bemängeln ihn regelmäßig. Im aktuellen Upstream-Code ist dieser Schalter nicht gesetzt. Ob F-Droid ihn bei NewPipe trotzdem akzeptiert, wurde nicht verifiziert.

### 3.4 Bewertung

| | |
|---|---|
| Zeit bis zur Veröffentlichung | Wochen (Review) |
| Reichweite | maximal (Standard-Repo) |
| Aufwand | mittel: Metadaten, Reproduzierbarkeit, Pflege bei jedem Upstream-Bump |
| Risiko | Review verlangt Änderungen; Updates hinken hinterher (bei YouTube-Brüchen kritisch, darum betreibt NewPipe ein eigenes Repo) |
| Passt für | stabile Version für andere Nutzer, **nicht** für die erste Dev-Version |

---

## 4. Option (c): GitHub Releases + Obtainium / IzzyOnDroid

### 4.1 GitHub Releases + Obtainium

- **[Schwach belegt]** Obtainium installiert und aktualisiert Apps direkt aus Release-Seiten (GitHub, GitLab, Codeberg …) und benachrichtigt über neue Releases. Es prüft selbst **keine** Entwickler-Signaturen. Die Signaturprüfung beim **Update** übernimmt aber Android selbst: Eine APK mit fremdem Schlüssel wird abgelehnt. Das Risiko liegt also nur bei der Erstinstallation.
  https://github.com/ImranR98/Obtainium · Diskussion: https://lemmy.dbzer0.com/post/498741/717612
- Aufwand: **praktisch null**, wenn ohnehin signierte APKs an GitHub Releases hängen.
- Nachteil: zusätzliche App; kein F-Droid-Katalog.

### 4.2 IzzyOnDroid

- **[Belegt]** F-Droid-kompatibles Drittrepo (in F-Droid-Clients wie Neo Store oder Droid-ify vorinstalliert, im offiziellen Client hinzufügbar). Bedingungen: OSI/FSF-Lizenz, Quellcode öffentlich (GitHub ok), **keine Tracker**, eindeutiger Paketname, Fork mit Nennung des Originals und unterscheidbarem Namen und Icon. Antrag über den Issue-Tracker (Codeberg). Es gibt eine Größengrenze, die Quelle nennt Apps unter 10 MB als unkritisch; den genauen Wert vor dem Antrag prüfen.
  https://izzyondroid.org/docs/general/AppInclusionPolicy/
- IzzyOnDroid nimmt **deine signierten APKs aus GitHub Releases** und verteilt sie mit deiner Signatur weiter. Dadurch ist die App kompatibel mit dem eigenen Repo und mit Obtainium. [Einschätzung, Standardpraxis]
- Typischerweise deutlich schneller als das offizielle F-Droid (Tage statt Wochen). [Einschätzung]

---

## 5. Externer Faktor: Googles Entwickler-Verifizierung (2026/2027)

- **[Belegt]** Seit dem 30.09.2026 gilt in **Brasilien, Indonesien, Singapur und Thailand** auf zertifizierten Geräten (Android 7+): Apps von nicht verifizierten Entwicklern lassen sich nur noch über den „Advanced Flow“ installieren. **Global ab 2027.** Es gibt kostenlose **„Limited distribution accounts“** für Studenten und Hobbyisten (bis zu 20 Geräte, ohne Ausweis und ohne Gebühr) sowie einen Registrierungsweg für Open-Source-Apps.
  https://developer.android.com/developer-verification
- **[Belegt]** Advanced Flow: In den Entwickleroptionen „Apps von nicht verifizierten Entwicklern zulassen“ aktivieren, **einmalig 24 h warten**, danach 7 Tage oder dauerhaft. **ADB-Installationen sind nicht betroffen.**
  https://9to5google.com/2026/08/18/google-gradually-rolling-out-androids-advanced-sideloading-ahead-of-developer-verification/ · F-Droid-Position: https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- **Konsequenz [Einschätzung]:** Für Deutschland **noch keine Auswirkung**. Spätestens 2027 sollte Stefan auf dem eigenen Gerät einmalig den Advanced Flow aktivieren **oder** ZenPipe über einen kostenlosen Limited-Distribution-Account bzw. die Open-Source-Registrierung mit dem eigenen Signaturschlüssel registrieren. Der Dev-Loop per `adb install` (`dev.sh`) bleibt unberührt. Den Stand Anfang 2027 neu prüfen.

---

## 6. Vergleich

| Kriterium | (a) Eigenes F-Droid-Repo | (b) Offizielles F-Droid | (c1) GitHub + Obtainium | (c2) IzzyOnDroid |
|---|---|---|---|---|
| Zeit bis Gerät | ~1 Tag | Wochen | Stunden | Tage–Wochen |
| Signatur | eigene | F-Droid-eigene oder (repro.) eigene | eigene | eigene |
| Update-Latenz | sofort | Tage | sofort | ~1 Tag |
| Aufwand laufend | gering (CI) | mittel | ~0 | ~0 |
| „Läuft in F-Droid-App“ | ja | ja | nein | ja (Repo hinzufügen) |
| Für Dev-Version geeignet | **ja** | nein | ja | eher nein |

---

## 7. Checkliste: erste Version (v0.1.0-zen.1)

**A. Identität festlegen (einmalig, nicht mehr änderbar)**
- [x] applicationId `io.github.sprobst76.zenpipe` in `ProjectConfig.kt` als neue Konstante. `namespace` bleibt `org.schabi.newpipe`.
- [x] `app_name` → „ZenPipe“ (Release), „ZenPipe Debug“, „ZenPipe Dev“ in allen `resValue`-Stellen von `app/build.gradle.kts`.
- [x] versionCode-Schema `upstream*1000 + n` → `1015001`, versionName `0.1.0-zen.1` (bzw. `0.29.1-zen.1`). (erledigt: 0.1.0-zen.1 / 1015001, siehe `zen-docs/release.md`)
- [x] Neues Launcher-Icon (inkl. adaptive/monochrome), About-Screen und Links, Crash-Report-E-Mail auf eigene Adresse. (erledigt 12.10.2026: Icon, About, Crash-Report; README-Fork-Hinweis)
- [x] Provider-Authorities: Im Manifest steht bereits `${applicationId}.provider` (geprüft), die Parallelinstallation mit NewPipe ist also ok. Weitere Provider bzw. Account-Typen beim Umbenennen trotzdem kurz gegenprüfen.
- [x] Crash-Report-Adresse: `ERROR_EMAIL_ADDRESS = "crashreport@newpipe.schabi.org"` in `app/src/main/java/org/schabi/newpipe/error/ErrorActivity.kt` → eigene Adresse.
- [ ] Hinweis: ZenPipe übernimmt die YouTube-Intent-Filter. Geteilte YouTube-Links öffnen dann ZenPipe direkt. Das ist ein Einstiegspunkt, der ebenfalls durch die Absichtsabfrage laufen sollte.

**B. Signatur**
- [ ] Release-Keystore erzeugen (`keytool -genkeypair -keyalg RSA -keysize 4096 -validity 10000 -alias zenpipe`), Backup in Passwortmanager + offline.
- [ ] SHA-256-Fingerprint notieren (`apksigner verify --print-certs`).
- [ ] `signingConfigs.release` in Gradle aus Umgebungsvariablen. `.gitignore` für `*.jks` prüfen.
- [ ] GitHub Secrets: `ZEN_KEYSTORE_B64`, `ZEN_KEYSTORE_PASS`, `ZEN_KEY_ALIAS`, `ZEN_KEY_PASS`.

**C. Metadaten**
- [x] `fastlane/metadata/android/en-US` + `de`: `title.txt`, `short_description.txt`, `full_description.txt` (mit „Fork of NewPipe, not affiliated with TeamNewPipe“), `changelogs/1015001.txt`, Icon und 2–3 Screenshots. Fremde Sprachordner mit NewPipe-Texten entfernen oder neutralisieren (sonst Markenproblem). (erledigt: en-US + de-DE; Screenshots fehlen noch, fremde Sprachordner entfernt)
- [ ] README: Hinweis auf Fork, GPLv3, nicht-Affiliation, Installationsweg, Signatur-Fingerprint.

**D. Build & Verteilung**
- [x] Release-Build lokal: `./gradlew assembleRelease`. Installation per adb auf dem Defy, Smoke-Test (Feed, Abo, Wiedergabe, Hintergrund). (erledigt: `assembleRelease` baut unsigniert, Smoke-Test nur mit Debug-Build am Defy)
- [ ] Workflow `release.yml`: bei Tag `v*` bauen, signieren und an GitHub Release anhängen.
- [ ] F-Droid-Repo: `fdroid init` lokal (Repo-Key), `config.yml` mit `repo_url`, `repo_name: "ZenPipe"`, `archive_older: 3`. Keystore + `config.yml` als Secrets. Workflow-Schritt `fdroid update` → Push nach gh-pages bzw. `zenpipe-fdroid`.
- [ ] Repo-URL mit `?fingerprint=` und QR-Code in die README.

**E. Auf dem Gerät verifizieren**
- [ ] NewPipe → Datenbank exportieren.
- [ ] F-Droid → Repo hinzufügen → ZenPipe **über F-Droid** installieren (nicht per adb, damit F-Droid Installer-of-record wird).
- [ ] ZenPipe → Datenbank importieren → Abos und Playlists prüfen.
- [ ] Testupdate: `v0.1.0-zen.2` taggen → erscheint in F-Droid → Update installiert ohne Signaturfehler, Daten bleiben erhalten.
- [ ] Danach NewPipe deinstallieren (Schlupfloch).

**F. Später**
- [ ] IzzyOnDroid-Antrag (ab stabiler Version).
- [ ] Reproduzierbarkeit testen (zwei Builds bitgleich? `apksigcopier`/`diffoscope`) → fdroiddata-MR mit `Binaries` + `AllowedAPKSigningKeys`.
- [ ] Anfang 2027: Status der Entwickler-Verifizierung in DE prüfen.

---

## 8. Stolpersteine

1. **applicationId nachträglich ändern** = neue App. Nutzer müssen neu installieren und Daten migrieren. Darum vor dem ersten verteilten Build festlegen.
2. **Signaturschlüssel verloren** = keine Updates mehr möglich. Schlüsselrotation (APK Signature Scheme v3) setzt den alten Schlüssel voraus.
3. **Mehrere Signaturen im Umlauf** (F-Droid-eigene vs. eigene) = Nutzer können nicht zwischen den Quellen wechseln. Entweder offizielles F-Droid nur mit reproduzierbarem Build und Entwickler-Signatur, oder gar nicht.
4. **versionCode nach Upstream-Rebase kleiner** als das letzte Release = Android lehnt das Update ab (`INSTALL_FAILED_VERSION_DOWNGRADE`). Das Schema `upstream*1000+n` verhindert das. Vor jedem Tag `android-release-preflight.sh` laufen lassen.
5. **Upstream-Bump ändert NewPipes versionCode in `ProjectConfig.kt`**. Die ZenPipe-Formel muss darauf aufbauen und darf ihn nicht hart überschreiben, sonst gibt es Merge-Konflikte bei jedem Rebase.
6. **Provider-Authorities** mit fester `org.schabi.newpipe`-Zeichenkette würden die Parallelinstallation verhindern. Aktuell sind sie über `${applicationId}` gelöst, das bei Upstream-Merges im Blick behalten.
7. **Crash-Reports und Links** zeigen weiter auf TeamNewPipe. Das ist für sie lästig und markenrechtlich heikel.
8. **Fastlane-Beschreibungen in ~80 Sprachen** sagen weiter „NewPipe“. Entfernen oder ersetzen.
9. **App per adb statt über F-Droid installiert**: F-Droid ist dann nicht Installer-of-record. Updates gehen trotzdem (gleiche Signatur), unbeaufsichtigte Updates (Android 12+) aber nicht.
10. **YouTube-Brüche:** Ein Extractor-Update muss schnell ausgerollt werden. Ein eigenes Repo bzw. GitHub Releases ist hier deutlich schneller als offizielles F-Droid. Das ist ein Grund, warum NewPipe ein eigenes Repo betreibt.
11. **Kein Play Store:** Das sagen Upstream-README und YouTube-AGB ausdrücklich.

---

## Quellen (Auswahl)

- F-Droid Inclusion Policy: https://f-droid.org/docs/Inclusion_Policy/
- F-Droid Anti-Features: https://f-droid.org/docs/Anti-Features/
- F-Droid Reproducible Builds: https://f-droid.org/docs/Reproducible_Builds/
- F-Droid Submitting Quick Start: https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- F-Droid Setup App Repo: https://f-droid.org/docs/Setup_an_F-Droid_App_Repo/
- F-Droid Nightly Builds: https://f-droid.org/en/docs/Publishing_Nightly_Builds/
- fdroidserver config-Beispiel: https://gitlab.com/fdroid/fdroidserver/-/raw/master/examples/config.yml
- fdroiddata NewPipe: https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/org.schabi.newpipe.yml
- fdroiddata Tubular: https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/org.polymorphicshade.tubular.yml
- PipePipe auf F-Droid: https://f-droid.org/packages/InfinityLoop1309.NewPipeEnhanced/
- NewPipe Markenrichtlinie: https://newpipe-ev.de/policy/trademark/
- IzzyOnDroid Inclusion Policy: https://izzyondroid.org/docs/general/AppInclusionPolicy/
- F-Droid Update-Ownership (TWIF 04/2024): https://f-droid.org/2024/04/18/twif.html
- Repo-Vorlagen: https://github.com/xarantolus/fdroid · https://git.tdem.in/GAF-Droid/GAF-Droid-Template
- Obtainium: https://github.com/ImranR98/Obtainium
- Android Developer Verification: https://developer.android.com/developer-verification
- Advanced Flow (9to5Google): https://9to5google.com/2026/08/18/google-gradually-rolling-out-androids-advanced-sideloading-ahead-of-developer-verification/
- F-Droid offener Brief: https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- Review-Wartezeiten (Forum): https://forum.f-droid.org/t/f-droid-submission-status-no-update-since-3-weeks-repostore/34091
