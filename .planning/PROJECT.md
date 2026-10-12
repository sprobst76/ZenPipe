# ZenPipe

**Stack:** kotlin-android (Fork von NewPipe, GPLv3)

## Zweck
Achtsamerer YouTube-Konsum: NewPipe-Fork ohne Feed-Sog. Ein Werkzeug zum bewussten Schauen.

## Kernwert
Man schaut, was man vorhatte — und ist dann fertig. Kein Autoplay, keine Empfehlungen, kein endloses Scrollen.

## MVP (ca. ein Wochenende)
1. Seitenleiste (Drawer) reduzieren: nur Abos, Feed, Playlists, Suche
2. Autoplay und Empfehlungen hart abschalten
3. Absichtsabfrage beim Start („Was willst du schauen?")

## Später (nach MVP-Entscheidung)
Sitzungs-Timer, Tageslimit, Feed mit Ende, Graustufen/ruhiges Design.

## Risiken
Wartung gegen Upstream/Extractor bei YouTube-Änderungen; ToS-Hinweis (siehe Konzept §4).

## Offene Punkte
Drawer-Schalter in NewPipe 0.29.1 prüfen; Code-Stellen für Drawer/Autoplay/Empfehlungen; Name/Branding; Upstream-Strategie.
