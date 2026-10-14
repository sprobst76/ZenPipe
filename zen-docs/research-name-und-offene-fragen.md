# Recherche: Name „ZenPipe“, Lizenz/Recht, Signing, offene Punkte vor dem ersten Release

**Stand:** 13.10.2026 · **Bezug:** `research-fdroid-deployment.md`, `release.md`, `AGENTS.md`
Legende: **[Belegt]** = Primärquelle bzw. selbst abgefragte Datenbank · **[Schwach belegt]** = Sekundärquelle · **[Einschätzung]** = eigene Schlussfolgerung, **keine Rechtsberatung**

---

## Empfehlungen auf einen Blick

> 1. **Name: „ZenPipe“ behalten.** Weder in der EU, in Deutschland noch in den USA gibt es eine eingetragene oder angemeldete Marke „ZENPIPE“. Die einzige Anmeldung kommt aus Mexiko (Klasse 9, eingereicht 10/2026). Auf F-Droid und IzzyOnDroid gibt es keine gleichnamige App. Andere NewPipe-Forks heißen ebenfalls „…Pipe“, und TeamNewPipe duldet das. Optional `zenpipe.de` oder `zenpipe.app` sichern (laut RDAP frei).
> 2. **GPLv3:** Copyright- und Lizenzhinweise beibehalten (schon erledigt). **Ergänzen:** einen datierten Änderungsvermerk (§5a) und einen Quelltext-Link pro Release (Tag) (§6).
> 3. **YouTube-AGB:** Ein Verstoß ist sicher, praktisch gibt es aber kein Vorgehen gegen Clients wie NewPipe. Das reale Risiko in DE ist **§95a UrhG bei der Download-Funktion** (OLG Hamburg 2024). Ob ZenPipe Downloads anbietet, sollte bewusst entschieden werden (Empfehlung: lassen, so wie NewPipe e.V. es in DE selbst verteilt, und im Blick behalten).
> 4. **Signing:** **ein** eigener App-Schlüssel (RSA 4096, PKCS12, ~27 Jahre gültig), zwei Offline-Backups, Signatur mit `apksigner` aus build-tools **34**. Getrennt davon der Repo-Indexschlüssel. Keine Play-App-Signatur und keine Abhängigkeit von Google, ab 2027 nur den SHA-256 im Android Developer Console registrieren.
> 5. **Vor dem ersten öffentlichen Release (Top 3):** Keystore und Release-Workflow, NewPipe-Reste in `.github/` (FUNDING, Issue-Templates, Workflows), Entscheidung zu Download-Funktion und Intent-Filtern.

---

## 1. Name „ZenPipe“

### 1.1 Markenrecherche (TMview, abgefragt am 13.10.2026)

TMview (EUIPO) umfasst EUIPO, DPMA, USPTO, WIPO und weitere Ämter. Die Suche lief headless, Ergebnisse siehe unten. Link zum Nachprüfen: https://www.tmdn.org/tmview/#/tmview/results?page=1&pageSize=30&criteria=F&basicSearch=zenpipe

| Treffer | Klassen | Amt / Status | Bewertung |
|---|---|---|---|
| **ZENPIPE** (Martha Martínez Fuentes), angem. 02.10.2026 | 9 | Mexiko IMPI, *Filed* | einziger exakter Treffer. Gilt nur in MX, nicht in EU/DE. **[Belegt]** |
| Zerpipe (IBS Technology GmbH), 2016 | 9, 42 | DPMA, *Registered* | ein Buchstabe Abstand. Inhaber und Produkt nicht auffindbar (vermutlich Industrie). Restrisiko gering **[Einschätzung]** |
| VENPIPE (TB-Safety AG) | 7, 9, 11, 22 | CH/WIPO/UK | Industriesicherheit, anderes Feld |
| ZENPIRE, ZENPINE, ZENPIGE, CENPIPE | div. | US/MX/KR/CH | klanglich entfernt bzw. andere Branche |
| **NewPipe** (NewPipe e.V.), EUTM **019104121**, angem. 11.11.2024 | **9** | EUIPO, *Registered* bis 2034 | die relevante Nachbarmarke, siehe 1.3 **[Belegt]** |

- USPTO und DPMA wurden nur über TMview geprüft, nicht direkt in deren Datenbanken (DPMAregister, USPTO tmsearch). TMview ist laut Disclaimer „kein amtliches Register“.
- „Zen“ ist schwach unterscheidungskräftig und weit verbreitet. Das senkt das Kollisionsrisiko, würde aber auch eine eigene Markenanmeldung schwierig machen. Eine eigene Anmeldung ist **nicht nötig** **[Einschätzung]**.

### 1.2 Apps, Repos, Domains

| Ort | Befund |
|---|---|
| F-Droid | keine App „zenpipe“ ([Suche](https://search.f-droid.org/?q=zenpipe&lang=en)) **[Belegt]** |
| Google Play | Die Websuche zeigte „ZenPipe – Calm Connect“ (VPN, `com.zenpipe.vpn`) und das Puzzlespiel „Zen Pipe“ (`com.ovogame.zenpipe`). **Beide Detailseiten liefern am 13.10.2026 HTTP 404** (entfernt bzw. regional nicht verfügbar) **[Belegt]** |
| Apple App Store | „Zen Pipe“, Rätselspiel von Jean-Claude Cottier, letztes Update 2018 ([Lookup](https://itunes.apple.com/lookup?id=560104506)). Andere Kategorie |
| GitHub | `imazen/zenpipe` (Rust-Bildpipeline, AGPL, 3★), `dynamik-dev/zenpipe-php` (PHP-Lib). Andere Domäne, keine Verwechslung **[Belegt]** |
| Verwandtes Konzept | **ZenTube**: proprietärer „achtsamer“ YouTube-Client für iOS/Mac plus Browser-Erweiterung ([Chrome Web Store](https://chromewebstore.google.com/detail/ljnjkcelmlkcdcghffobikpjlefjaldh)). Gleiches Feld, aber anderer Name. **„ZenTube“ deshalb nicht als Alternative verwenden** |
| Domains (RDAP) | `zenpipe.com` vergeben (seit 2005, Hosting-Firma in Malaysia), `zenpipe.org` vergeben (2024). `zenpipe.de` und `zenpipe.app` laut RDAP **nicht registriert**. Die applicationId `io.github.sprobst76.zenpipe` braucht keine Domain |

### 1.3 Verhältnis zu „NewPipe“ und Namenspraxis anderer Forks

- **[Belegt]** Die Markenrichtlinie von NewPipe e.V. verbietet abgeleiteten Apps, **„NewPipe“** im Namen zu führen. Ausnahme ist Zusatzsoftware nach dem Muster „Downloader for NewPipe“. Websites müssen klar sagen, dass sie *weder mit TeamNewPipe verbunden noch von TeamNewPipe gebilligt* sind. Der Bestandteil „Pipe“ oder ein Logo wird **nicht** geregelt. Interner Code bleibt ausgenommen. https://newpipe-ev.de/policy/trademark/
- **[Belegt]** Namenspraxis der Forks:
  - **PipePipe** (F-Droid, Paket `InfinityLoop1309.NewPipeEnhanced`): https://github.com/InfinityLoop1308/PipePipe
  - **BravePipe**, ehemals „BraveNewPipe“ (Repo `BraveNewPipeLegacy` existiert noch): https://github.com/bravenewpipe/NewPipe
  - **Tubular**, ehemals „NewPipe x SponsorBlock“. Das Repo ist inzwischen **eingestellt** und verweist auf PipePipe: https://github.com/polymorphicshade/Tubular
  - **LibreTube** ist kein NewPipe-Fork (Piped-Frontend), nur zum Vergleich.
- **[Einschätzung]** „ZenPipe“ und „NewPipe“ teilen nur den beschreibenden Bestandteil „Pipe“. Die prägenden Präfixe „Zen“ und „New“ unterscheiden sich klar. BravePipe und PipePipe zeigen, dass TeamNewPipe Namen nach dem Muster „…Pipe“ akzeptiert. Die geforderten Disclaimer stehen schon in README, Fastlane und About-Screen.

### 1.4 Empfehlung: **behalten**

Ein Namenswechsel lohnt sich nur, falls TeamNewPipe widerspricht oder später eine Marke „ZENPIPE“ mit EU-Wirkung auftaucht. Reservenamen mit Kurzcheck vom 13.10.2026 (TMview Fuzzy, GitHub-Namenssuche, F-Droid, RDAP):

| Name | TMview (Kl. 9/42) | GitHub | F-Droid | Domains | Bewertung |
|---|---|---|---|---|---|
| **StillPipe** | nur „skillpipe“ (UK/WIPO, E-Learning) | 0 Repos | – | .com/.de/.app frei | **beste Reserve**: ruhig, deutsch/englisch lesbar |
| **SlowPipe** | nichts Ähnliches in 9/42 (Snowpipe/Snowflake Kl. 42 klanglich nah) | 5 Mini-Repos | – | .com/.de/.app frei | gut, „Slow Media“-Anklang |
| **EnsoPipe** | SENSOpipe (DPMA, Kl. 9/42, eingetragen) | 0 | – | .com frei | passt zum Ring-Icon (Ensō), aber Nähe zu SENSOpipe |
| **CalmPipe** | keine Kollision in 9/42 | 0 | – | .com vergeben, .de/.app frei | Risiko: Nähe zur App-Marke „Calm“ (Meditations-App) **[Einschätzung]** |

Alle Varianten mit „Pipe“ stehen gegenüber NewPipe gleich da wie ZenPipe. Ein Wechsel brächte dort also keinen Vorteil.

---

## 2. Lizenz und Recht

### 2.1 GPLv3-Pflichten im Fork

NewPipe ist `GPL-3.0-or-later` ([fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/org.schabi.newpipe.yml)), NewPipeExtractor ebenfalls GPLv3. Text der Lizenz: https://www.gnu.org/licenses/gpl-3.0.html **[Belegt]**

| Pflicht | GPLv3 | Stand ZenPipe |
|---|---|---|
| Copyright- und Lizenzhinweise unverändert lassen, Lizenztext beilegen | §4 | `LICENSE` vorhanden, Dateiköpfe unverändert ✔ |
| **Datierter Hinweis „geändert von … am …“** | §5a | nur implizit über `zen:`-Commits. **TODO:** Abschnitt „Modifications“ mit Datum ins README oder eine `NOTICE`-Datei |
| Hinweis, dass die Lizenz GPLv3 ist | §5b | README und About ✔ |
| „Appropriate Legal Notices“ in der UI (Copyright, keine Garantie, Lizenzlink) | §5d | About-Screen mit Lizenz und Fork-Hinweis ✔ (vor Release einmal ansehen) |
| **Quellcode zu jedem verteilten Binary** (Corresponding Source inkl. Build-Skripten) | §6 | GitHub-Repo öffentlich. **TODO:** in jedem Release und im F-Droid-Repo auf den **Tag** verlinken. GitHub hängt Quell-Tarballs automatisch an, das reicht für §6d. Abhängigkeiten über JitPack sind öffentlich, der Extractor-Commit ist in `gradle/libs.versions.toml` gepinnt ✔ |
| Keine zusätzlichen Einschränkungen | §10 | – |

### 2.2 YouTube-Nutzungsbedingungen

- **[Belegt]** Die YouTube-AGB (gültig seit 05.01.2022) verbieten Zugriff „using any automated means“ sowie Download und Vervielfältigung, soweit der Dienst das nicht erlaubt oder das Gesetz es zulässt. https://www.youtube.com/static?template=terms&gl=DE&hl=en
- **[Einschätzung]** Die AGB binden nur Nutzer mit Vertrag zu YouTube, nicht den Entwickler als solchen. Gegen NewPipe ist seit Jahren kein Vorgehen bekannt. Darum gibt es keinen Play-Store-Vertrieb (Play-Richtlinien und Upstream-README).
- **[Belegt] Relevanter für DE:** Das **OLG Hamburg (21.11.2024, 5 U 54/23)** hat die „Rolling Cipher“ von YouTube als wirksame technische Schutzmaßnahme (§95a UrhG) eingestuft und den Hoster Uberspace für youtube-dl haftbar gemacht. https://netzpolitik.org/2024/entscheidung-des-olg-hamburg-youtube-dl-org-bleibt-gesperrt/ · https://heise.de/-10179284 · Vorinstanz: https://www.lto.de/recht/hintergruende/h/urheberrecht-youtube-musik-videos-download-haftung-uberspace-youtube-dl
- **[Einschätzung]** Der Extractor entschlüsselt dieselbe Signatur, für das Abspielen wie für den Download. Rechtlich angreifbar ist vor allem die **Download-Funktion** (dauerhafte Kopie). NewPipe e.V. sitzt selbst in Deutschland und verteilt NewPipe mit Downloader, bisher unbehelligt. Für ein kleines, nicht kommerzielles Projekt ist das Risiko gering, aber nicht null. Optionen:
  - (a) Download lassen wie Upstream (**Empfehlung**, minimaler Patch-Satz).
  - (b) Download im Release ausblenden. Das passt auch zum Zen-Konzept, ist aber ein zusätzlicher Patch.

  Vor einer breiten Bewerbung erneut prüfen, ob es neue Rechtsprechung (BGH) gibt.

### 2.3 F-Droid- und IzzyOnDroid-Aufnahme

- **[Belegt]** Anti-Features sind **Warnhinweise und kein Ausschluss**. NewPipe trägt nur `NonFreeNet` („Depends on Youtube for videos.“). ZenPipe würde dasselbe tragen. `Tracking` fällt weg: Es gibt keinen Update-Check, und Crash-Reports werden nur manuell gesendet. https://f-droid.org/docs/Anti-Features/
- **[Belegt]** IzzyOnDroid verlangt für Forks: das Original nennen, einen unterscheidbaren Namen, ein eigenes Icon und eigene Screenshots und Texte. Außerdem eine Release-Signatur, keine `debuggable`/`testOnly`-Flags, APKs aus getaggten GitHub-Releases und höchstens ~30 MB. Das Release-APK hat aktuell ~6,9 MB ✔. https://izzyondroid.org/docs/general/AppInclusionPolicy/
- **[Belegt]** Das offizielle F-Droid verlangt für Forks eine neue applicationId, einen neuen Namen und ein neues Icon (erfüllt). https://f-droid.org/docs/Inclusion_Policy/

---

## 3. Signing und Keystore

**Empfehlung (umsetzen, wenn der Release-Workflow gebaut wird; hier wurde nichts erzeugt):**

1. **Ein App-Signaturschlüssel für alle Kanäle**: eigenes Repo, GitHub-Releases, IzzyOnDroid und später offizielles F-Droid mit Reproducible Build. Nur so können Nutzer zwischen den Quellen wechseln.
   - `keytool -genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 -alias zenpipe` (≈ 27 Jahre).
   - Kein DSA und kein SHA1, sonst greift das F-Droid-Anti-Feature *Disabled Algorithm*.
   - Signaturschemata: wegen minSdk 23 **v1 + v2** (v1 ist für API < 24 nötig), dazu **v3**, um später eine Schlüsselrotation (Lineage) zu ermöglichen.
2. **apksigner aus build-tools 34** verwenden. Mit neueren Versionen kann F-Droid die Signatur nicht auf sein Build kopieren (`apksigcopier`). Weitere Stolpersteine für Reproduzierbarkeit: `baseline.prof`, Zeitstempel, ZIP-Attribute, Build aus sauberem Tag. **[Belegt]** https://f-droid.org/docs/Reproducible_Builds/
3. **Aufbewahrung:**
   - Keystore und beide Passwörter in den Passwortmanager.
   - **Zwei** Offline-Kopien, verschlüsselt, an zwei Orten (z. B. USB-Stick und Papier mit Base64 oder QR).
   - **Nie ins Repo.** `.gitignore` deckt heute nur `local.properties` ab. **TODO:** `*.jks`, `*.keystore`, `*.p12` ergänzen.
   - SHA-256-Fingerprint im README veröffentlichen.
4. **CI-Signatur** nur mit GitHub-Environment-Secrets (Keystore als Base64, Passwörter getrennt). Der Workflow läuft nur bei Tags, Gradle liest die Werte aus Umgebungsvariablen. Alternative mit weniger Angriffsfläche: lokal signieren und hochladen. Für den Start ist beides ok **[Einschätzung]**.
5. **Repo-Indexschlüssel** (`fdroid init`) ist ein eigener Schlüssel. Ein Verlust ist weniger schlimm, weil Nutzer das Repo dann einmal neu hinzufügen. Trotzdem sichern.
6. **Unabhängigkeit von Google Play:**
   - Keine Play-App-Signatur. Der private Schlüssel bleibt bei Stefan.
   - **Ab 2027** (Google Developer Verification, global) im *Android Developer Console* die applicationId und den SHA-256 des eigenen Schlüssels registrieren. Für Open-Source-Plattformen ist das optional, verhindert aber den „Advanced Flow“ beim Nutzer. Wer den Schlüssel verliert, kann das Paket nicht mehr registrieren. https://developer.android.com/developer-verification/guides/open-source-app-registration · https://developer.android.com/developer-verification/guides/faq
   - Würde F-Droid mit **eigenem** Schlüssel signieren (kein Repro-Build), bräuchte es einen zweiten registrierten Fingerprint. Ein weiterer Grund für Reproducible Builds.
7. Debug-Builds nutzen weiter den lokalen Debug-Key (eigene applicationId `.debug`). Kein Konflikt.

---

## 4. Weitere Lücken vor dem ersten öffentlichen Release (priorisiert)

1. **[Blocker]** Keystore, `signingConfigs.release` aus Umgebungsvariablen, Tag-Workflow `release.yml`, F-Droid-Repo (siehe `research-fdroid-deployment.md` §7 B/D). Den geerbten `build-release-apk.yml` (benennt das APK `NewPipe_v….apk`) ersetzen oder löschen.
2. **[Hoch] NewPipe-Reste in `.github/`:**
   - `FUNDING.yml` zeigt auf das Liberapay und die Spendenseite von TeamNewPipe. Der „Sponsor“-Button im ZenPipe-Repo suggeriert sonst eine Verbindung.
   - Die Issue-Templates (`bug_report.yml`, `config.yml`) verlinken auf NewPipe-Issues, NewPipe-Discussions und die NewPipe-Matrix.
   - Upstream-Workflows `pr-labeler` (`pull_request_target`), `no-response` (Cron), `image-minimizer` und `backport-pr` prüfen bzw. deaktivieren.
3. **[Hoch] Entscheidung Download-Funktion** (§2.2) und **YouTube-Intent-Filter**. Sollen geteilte Links ZenPipe öffnen? Falls ja, dann durch die Absichtsabfrage leiten.
4. **[Mittel] GPL-§5a-Änderungsvermerk** und Quellen-Link pro Release (§2.1).
5. **[Mittel] Fastlane-Screenshots** (`en-US/images/phoneScreenshots/`). IzzyOnDroid und F-Droid wollen fork-eigene Bilder.
6. **[Mittel] Datenschutz:** `privacy_policy_url` zeigt auf `README#privacy`, der Abschnitt existiert. Inhalt einmal gegen den DSGVO-Hinweis beim Start prüfen. Der weiter unten übernommene Upstream-Abschnitt „Privacy Policy“ beschreibt NewPipe. Klarstellen oder anpassen.
7. **[Mittel] Update-Strategie bei YouTube-Brüchen:** Wer merged wie schnell neue Extractor-Versionen? Ziel: ein ZenPipe-Release wenige Tage nach einem NewPipe-Hotfix (Preflight-Skript, `versionCode`-Schema steht).
8. **[Niedrig] README-Rest:** Der übernommene NewPipe-Teil enthält Download-Badges und Links zu NewPipe. Das ist ok als Attribution, aber eine deutliche Überschrift „Original NewPipe README“ verhindert Verwechslungen. Fingerprint und Repo-QR ergänzen.
9. **[Niedrig] Impressum** für eine eventuelle GitHub-Pages-Seite: Ein rein privates, nicht kommerzielles Projekt ist nach §5 DDG in der Regel ausgenommen. Mit Spendenlink neu bewerten **[Einschätzung]**.
10. **[Niedrig]** Migration NewPipe → ZenPipe (DB-Export/-Import) einmal real testen, bevor sie in der README beworben wird.
11. **[Später]** `dependenciesInfo { includeInApk = false }` für F-Droid und Reproducible Build prüfen (offen aus der F-Droid-Recherche §3.3).

---

## Quellen

- NewPipe-Markenrichtlinie: https://newpipe-ev.de/policy/trademark/
- TMview (EUIPO): https://www.tmdn.org/tmview/ (Abfragen „zenpipe“ Fuzzy/Contains, „zen pipe“, „newpipe“, Alternativnamen; 13.10.2026)
- F-Droid: [Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/) · [Anti-Features](https://f-droid.org/docs/Anti-Features/) · [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/) · [NewPipe-Metadaten](https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/org.schabi.newpipe.yml)
- IzzyOnDroid Inclusion Policy: https://izzyondroid.org/docs/general/AppInclusionPolicy/
- Forks: [PipePipe](https://github.com/InfinityLoop1308/PipePipe) · [BravePipe](https://github.com/bravenewpipe/NewPipe) · [Tubular](https://github.com/polymorphicshade/Tubular)
- GPLv3: https://www.gnu.org/licenses/gpl-3.0.html
- YouTube-AGB: https://www.youtube.com/static?template=terms&gl=DE&hl=en
- youtube-dl/Uberspace: [netzpolitik.org](https://netzpolitik.org/2024/entscheidung-des-olg-hamburg-youtube-dl-org-bleibt-gesperrt/) · [heise](https://heise.de/-10179284) · [LTO](https://www.lto.de/recht/hintergruende/h/urheberrecht-youtube-musik-videos-download-haftung-uberspace-youtube-dl)
- Android Developer Verification: [Open-Source-Registrierung](https://developer.android.com/developer-verification/guides/open-source-app-registration) · [FAQ](https://developer.android.com/developer-verification/guides/faq) · [Übersicht](https://developer.android.com/developer-verification)
- Namens-Treffer: [imazen/zenpipe](https://github.com/imazen/zenpipe) · [Zen Pipe (iOS)](https://apps.apple.com/us/app/zen-pipe/id560104506) · [ZenTube](https://chromewebstore.google.com/detail/ljnjkcelmlkcdcghffobikpjlefjaldh)

---

## 5. Vertiefte Markenpruefung (2026-10-13)

**Kein Rechtsrat.** Das ist eine Recherche eines KI-Assistenten, keine Markenrecherche durch Anwalt oder Patentanwalt. Legende: **[Geprueft]** = am 13.10.2026 selbst abgerufen, mit Quelle · **[Nicht pruefbar]** = Register ohne JavaScript/Captcha nicht abfragbar, Stefan muss selbst suchen · **[Einschaetzung]** = eigene Schlussfolgerung.

### 5.1 Register und Fundstellen

| Quelle | Suchbegriff | Status / Ergebnis | Quelle / Link |
|---|---|---|---|
| TMview (EUIPO/DPMA/USPTO/WIPO) | zenpipe | **Nicht erneut abfragbar:** der abgerufene Link liefert nur eine JavaScript-Huelle bzw. `ECONNRESET`. Gilt weiter der Stand aus 1.1 (frueherer headless-Lauf, selbe Datumsangabe): kein EU-/DE-/US-Treffer fuer ZENPIPE, nur MX (Filed 02.10.2026) | https://www.tmdn.org/tmview/#/tmview/results?page=1&pageSize=30&criteria=F&basicSearch=zenpipe |
| DPMAregister | Marke: `zenpipe`, dann `zen pipe`, `zen-pipe`; Klassen 9, 38, 41, 42 | **Nicht pruefbar:** Formular (Basisrecherche) per WebFetch nur als Maske lesbar, Suche braucht Browser/Session. Felder: Marke, Klasse(n), Datenbestand (national/EU/IR) | https://register.dpma.de/DPMAregister/marke/einsteiger |
| EUIPO eSearch plus | `zenpipe` (Wortmarke, enthaelt) | **Nicht pruefbar** (JavaScript-App). Ersatz: TMview (deckt EUTM mit ab) | https://euipo.europa.eu/eSearch/ |
| WIPO Global Brand Database | Brand name `zenpipe` | **Nicht pruefbar:** Seite liefert nur Captcha-Widget (altcha) und Weiterleitung, Ergebnisse werden clientseitig nachgeladen | https://branddb.wipo.int/ |
| USPTO Trademark Search | `zenpipe` (Wordmark, Live+Dead) | **Nicht pruefbar** (JavaScript). Nur indirekter Hinweis, siehe Zeile ZenTek | https://tmsearch.uspto.gov/ |
| Websuche "ZenPipe"/"Zen Pipe" trademark/Marke | siehe links | Keine Registereintraege in den Treffern. Funde siehe unten **[Geprueft]** | WebSearch 13.10.2026 |
| **ZenPipe for Civil 3D** (ZenTek Consultants, Hackensack NJ / Vancouver BC) | zentekconsultants.net | **Neu gegenueber 1.2.** Kommerzielles Software-Tool (Zusatzkatalog fuer AutoCAD Civil 3D Pressure Networks), Ankuendigung 22.03.2023, kein ™/® auf der Seite. Klasse 9/42 (Software), aber voellig anderes Feld (Tiefbau-CAD). Registrierung bei USPTO/CIPO **nicht geprueft** | https://zentekconsultants.net/introducing-zenpipe-for-autocad-civil-3d/ |
| Zenpipe (Webhosting, Malaysia) | zenpipe.com | Firmenname/Domain seit 2005 (siehe 1.2), Dienstleistung Hosting (Kl. 38/42). Marke nicht geprueft | https://www.zenpipe.com/ |
| Zen-Pfeifenzubehoer (Rolling Paper Depot, zensmoke.com) | "Zen Pipe" | Tabak-/Raucherzubehoer, Klasse 34, "Zen" ist dort Marke von BBK Tobacco & Foods (laut Webseite). Andere Klasse, keine Verwechslung bei Software | https://zensmoke.com/pipe-cleaners/ |
| Kick-Kanal `zenpipe88` | – | Streaming-Account, irrelevant | https://kick.com/zenpipe88 |
| F-Droid | zenpipe | "does not have any apps matching" **[Geprueft]** | https://search.f-droid.org/?q=zenpipe&lang=en |
| GitHub-Repos (API) | zenpipe | `dynamik-dev/zenpipe-php` (7 Sterne), `imazen/zenpipe` (3), `Zenika/zenpipe`, `svenaoki/zenpipe`, `dynamik-dev/zenpipe`, `nkumar212/ZenPipeMini-*` (2017), zwei `zenpipeline-*`. Alles Bibliotheken/Spielwiese, kein Android-/YouTube-Bezug **[Geprueft]** | https://api.github.com/search/repositories?q=zenpipe |
| **GitHub-Organisation `zenpipe`** | github.com/zenpipe | **Existiert**, angelegt 11.05.2025, 0 oeffentliche Repos, ohne Beschreibung (Namensplatzhalter fremder Person). Fuer uns nur relevant, falls wir eine Org `zenpipe` wollten: **nicht verfuegbar** (aktuell `sprobst76/ZenPipe` ok) **[Geprueft]** | https://api.github.com/orgs/zenpipe |
| Android-/NewPipe-Fork-Suche | "ZenPipe" app android / NewPipe fork | Kein Treffer fuer eine gleichnamige Android-App oder einen Fork **[Geprueft]** (die frueher gefundenen Play-Eintraege waren 404, siehe 1.2) | WebSearch 13.10.2026 |
| NewPipe e.V. Markenrichtlinie | – | Wortmarke "NewPipe" ist EU-Marke. Abgeleitete Projekte sollen **eigene Marke/Identitaet** entwickeln. Webseiten Dritter muessen klar sagen, dass sie *weder mit TeamNewPipe verbunden noch von ihm gebilligt* sind. Unoffizielle Builds: Commit/Version nennen, "unofficial". Logo und der Bestandteil "Pipe" sind **nicht** geregelt. Hinweis: Frist fuer bestehende Forks mit "NewPipe" im Namen war 30.04.2025 **[Geprueft]** | https://newpipe-ev.de/policy/trademark/ |
| YouTube API Services Branding Guidelines | – | Verboten: den Namen YouTube oder Abkuerzung/Variante ("YT", "You-Tube") im Namen der App/des Produkts; beschreibend "app for YouTube" ist erlaubt. Gilt formal fuer API-Clients; ZenPipe nutzt die API nicht, die Regel ist trotzdem eine gute Leitlinie. "ZenPipe" enthaelt kein YouTube-Element **[Geprueft]** | https://developers.google.com/youtube/branding |

### 5.2 Bewertung des Kollisionsrisikos (Einschaetzung, kein Rechtsrat)

- **EU/DE/US, gleiche Branche (Video-/Streaming-Apps, Kl. 9/38/41):** kein bekannter Konflikt. Das schliesst aber nicht aus, dass TMview/DPMA/USPTO etwas zeigen, was ich nicht abfragen konnte. Die Direktabfragen (DPMA, EUIPO, WIPO, USPTO) bleiben offen (Checkliste unten).
- **ZenTek "ZenPipe for Civil 3D" (Kl. 9/42, USA/Kanada):** identischer Name, Softwareklasse, aber anderer Markt (Tiefbau-CAD, B2B, Windows/AutoCAD). Verwechslungsgefahr zu einer Android-App fuer YouTube-Streaming gering. Relevant erst, falls ZenTek die Marke in EU/DE anmeldet oder ZenPipe sehr bekannt wird. Praktisch: Unterlassungsforderung unwahrscheinlich, aber nicht ausgeschlossen. **Niedrig bis mittel.**
- **Zenpipe Web Hosting (MY):** Domain/Firma, anderes Feld, Sitz ausserhalb EU. **Niedrig.**
- **Mexiko-Anmeldung ZENPIPE (Kl. 9, 02.10.2026):** gilt nur in MX. Verteilung ueber GitHub/F-Droid ist weltweit abrufbar, aber MX-Markenrecht wirkt nur gegen Nutzung dort. **Niedrig**, beobachten (Inhaberin unbekannt, Zweck unbekannt).
- **"Zen" schwach, "Pipe" beschreibend:** Das Gesamtzeichen ist schutzfaehig genug fuer Kollisionen, aber der Spielraum anderer Inhaber ist klein. Zusammen mit den "…Pipe"-Forks (PipePipe, BravePipe) ist ein Einspruch von TeamNewPipe unwahrscheinlich, da deren Marke nur "NewPipe" betrifft.
- **Gesamt: niedrig bis mittel**, solange ZenPipe privat, nicht kommerziell und nur ueber GitHub/eigenes F-Droid-Repo verteilt wird. Das Risiko steigt, wenn ZenPipe in grossen Stores erscheint, monetarisiert wird oder die Marke eines Dritten in EU/DE eingetragen wird.

### 5.3 Empfehlung

1. **Name "ZenPipe" behalten: ja.** Keine gefundene Kollision rechtfertigt einen Wechsel jetzt. Reserve bleibt StillPipe (siehe 1.4).
2. **Eigene Markenanmeldung: nicht noetig, derzeit nicht ratsam.** Kosten (DPMA ca. 290 EUR fuer 3 Klassen, EUIPO hoeher; Gebuehren **nicht geprueft**), schwache Unterscheidungskraft ("Zen"), und eine Anmeldung kann Aufmerksamkeit (z. B. von ZenTek) wecken. Neu bewerten, wenn ZenPipe wirtschaftlich genutzt wird.
3. **Absicherungen:**
   - Domain `zenpipe.de` (und ggf. `.app`) registrieren, falls eine Projektseite gewuenscht ist (RDAP-Stand aus 1.2: frei, vor Kauf neu pruefen). `.com`/`.org` sind vergeben.
   - GitHub: Org `zenpipe` ist vergeben. Bei `sprobst76/ZenPipe` bleiben (kein Problem), keine Org mit aehnlichem Namen anlegen, die verwechselt werden kann.
   - **Disclaimer** in README, Fastlane-Beschreibung (full_description) und About-Screen beibehalten bzw. ergaenzen: "ZenPipe is an independent fork of NewPipe (GPLv3) and is not affiliated with or endorsed by TeamNewPipe/NewPipe e.V. or YouTube/Google." (Satz fuer YouTube/Google fehlt vermutlich noch, bitte pruefen.) Auf Release-Seiten Version/Commit nennen und "unofficial build" schreiben, wie es die Richtlinie fuer Builds verlangt.
   - Kein "YouTube"/"YT" im Namen, Titel, Icon; "for YouTube" nur beschreibend. Kein YouTube-Logo.
   - Eigene Logo/Icon (Ring) beibehalten, nicht an das NewPipe-Logo anlehnen.
4. **Beobachten:** Mexiko-Anmeldung ZENPIPE und ZenTek ("ZenPipe for Civil 3D") gelegentlich neu pruefen (siehe Checkliste), vor dem ersten oeffentlichen Release mindestens einmal.

### 5.4 Checkliste fuer Stefan (nur im Browser pruefbar)

- [ ] **DPMAregister:** https://register.dpma.de/DPMAregister/marke/einsteiger, Feld "Marke": `zenpipe`, danach `zen pipe`; Datenbestand: national + Unionsmarken + IR ankreuzen. Zweiter Durchgang mit Klassen 9, 38, 41, 42 (und `*zenpipe*`). Treffer mit Inhaber/Status notieren.
- [ ] **EUIPO eSearch plus:** https://euipo.europa.eu/eSearch/ , Wortmarke `zenpipe` (Bedingung "contains"), dazu `zen pipe`.
- [ ] **WIPO Global Brand Database:** https://branddb.wipo.int/ , Captcha loesen, Brand name `zenpipe` (nur Marken, auch "Dead" ansehen), Land MX/US/EM/DE.
- [ ] **USPTO:** https://tmsearch.uspto.gov/ , `zenpipe` (Live und Dead), Klassen 9/42. Besonders: Inhaber **ZenTek Consultants / ZENTEK LLC**.
- [ ] **TMview** im Browser: https://www.tmdn.org/tmview/ , `zenpipe` plus `zen pipe`, pruefen, ob Treffer aus 1.1 noch stimmen (MX-Anmeldung, ZERPIPE).
- [ ] **Mexiko (optional):** IMPI-Marcia-Eintrag ZENPIPE (Martha Martinez Fuentes, 02.10.2026) kurz ansehen: Klasse/Waren.
- [ ] **Domain:** `zenpipe.de` und `zenpipe.app` bei einem Registrar nochmals pruefen und ggf. registrieren.
- [ ] **Disclaimer:** In README/About/Fastlane pruefen, dass "nicht verbunden mit NewPipe/YouTube" ausdruecklich drinsteht.
- [ ] Ergebnis (Treffer ja/nein, Datum) hier unter 5.1 nachtragen.
