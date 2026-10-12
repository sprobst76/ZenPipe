# ZenPipe – Konzeptskizze

**Version:** 0.1
**Datum:** 12.10.2026
**Status:** Idee / Entwurf (historisch; gültige Regeln stehen in `rules.md` und den Specs)

## 1. Ausgangslage

Ziel ist ein achtsamerer Umgang mit dem Handy, vor allem bei YouTube. Auch mit dem werbefreien Premium-Abo bleiben Empfehlungen, Autoplay und Shorts bestehen, und ohne Werbepausen fehlt sogar ein natürlicher Bremspunkt.

NewPipe (Version 0.29.1, über F-Droid installiert) ist ein Open-Source-YouTube-Client ohne Konto, ohne Tracking, ohne Werbung und ohne Shorts-Feed. Beim Einrichten zeigte sich aber:

- Die Seitenleiste (Abonnements, Neuigkeiten, Wiedergabelisten, Downloads, Verlauf, dazu Gaming-Trends, Beliebte Musik, Filme und Shows, Podcasts, Trends, Live) lässt sich nach bisherigem Kenntnisstand nicht anpassen.
- Die F-Droid-Warnung „Unerwünschte Merkmale" ist nur die Standardkennzeichnung dafür, dass die App von YouTube als nicht quelloffenem Dienst abhängt. Es ist kein Sicherheitsproblem.

## 2. Idee

Ein Fork von NewPipe mit Zen-Fokus: ein Werkzeug zum bewussten Schauen statt eines Feeds, der festhält. Es gibt Forks wie PipePipe oder Tubular, aber nach aktuellem Wissen keinen mit Zen-Fokus.

## 3. Mögliche Funktionen

- Seitenleiste radikal reduziert: nur Abos, Feed, Playlists, Suche. Trends, Gaming, Live usw. komplett entfernt
- Keine Vorschläge unter dem Video, keine Kommentare, kein Autoplay
- Absichtsabfrage beim Start („Was willst du schauen?")
- Sitzungs-Timer mit sanftem Ende
- Tageslimit, danach nur noch Hören oder Playlists
- Feed mit Ende („Du bist durch") statt endlosem Scrollen
- Ruhiges, reduziertes Design, optional Graustufen

## 4. Technische Eckpunkte

- NewPipe steht unter GPLv3: Forken und Verändern ist erlaubt, der Fork muss ebenfalls Open Source bleiben.
- Aufbau: App (Java/Kotlin) plus separate Bibliothek **NewPipeExtractor**, die die YouTube-Daten holt. Die UI-Änderungen liegen fast nur in der App, der Extractor kann unverändert eingebunden werden.
- Größtes Risiko ist die Wartung: Ändert YouTube etwas, braucht es ein Extractor-Update. Empfehlung: den Fork als kleinen Patch-Satz auf dem Upstream halten und regelmäßig mergen.
- Verteilung über ein eigenes F-Droid-Repo oder GitHub-Releases. Im Play Store wäre es wegen der YouTube-Nutzungsbedingungen kaum haltbar.
- Rechtlicher Hinweis: NewPipe-artige Clients verstoßen gegen die YouTube-Nutzungsbedingungen, und Creator erhalten über sie keine Werbe- bzw. Premium-Einnahmen.

## 5. Pragmatischer Einstieg (MVP)

Fork mit nur drei Änderungen, ungefähr ein Wochenende Aufwand:

1. Seitenleiste (Drawer) reduzieren
2. Autoplay und Empfehlungen hart abschalten
3. Absichtsabfrage beim Start

Danach entscheiden, ob sich weitere Funktionen lohnen.

## 6. Bis dahin: NewPipe manuell optimal einstellen

- Einstellungen → Inhalt → „Inhalt der Hauptseite": nur **Feed** und **Abos** (bzw. Bibliothek/Playlists) behalten, Trends und Kiosk entfernen
- Autoplay des nächsten Videos aus
- Abos importieren (Google Takeout oder NewPipe-Import) und in Gruppen sortieren
- Videos über eine Playlist „Später ansehen" bewusst abarbeiten
- Hintergrundwiedergabe für Podcasts und Musik nutzen
- ScreenZen oder One Sec für eine Atempause vor dem App-Start
- Offizielle YouTube-App vom Homescreen nehmen

## 7. Offene Punkte

- Ob Version 0.29.1 doch einen Schalter für die Seitenleiste hat (unter Einstellungen → Aussehen prüfen)
- Konkrete Stellen im NewPipe-Code für Drawer, Autoplay und Empfehlungen identifizieren
- Name und Branding (Arbeitstitel „ZenPipe")
- Wartungsstrategie für den Upstream
