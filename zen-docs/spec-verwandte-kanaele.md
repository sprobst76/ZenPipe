# Spec: Entdecken — Empfehlungen für verwandte Kanäle

Anforderung (Nutzer, 2026-10-12): Empfehlungen gerne für verwandte Kanäle.

## Prinzip
Empfehlungen auf **Kanal-Ebene, nicht auf Video-Ebene**: Entdecken ist eine bewusste, seltene, endliche Handlung
(„Wen will ich abonnieren?"), kein Feed. Kein Autoplay, keine Video-Vorschläge unter Videos (bleibt aus).

## Datenquellen (alles lokal ausgewertet, keine Konto-/Verlaufsdaten an Dritte)
1. Verwandte-Videos-Daten, die der Extractor beim Öffnen eines Videos ohnehin liefert (UI bleibt aus), zählen
   die **Kanäle** der verwandten Videos mit; nur Kanäle, die noch nicht abonniert sind.
2. Auf Kanalseiten verlinkte/„featured" Kanäle (falls der Extractor sie liefert — prüfen).
3. Gewichtung: Häufigkeit über mehrere Quellkanäle, die der Nutzer hoch priorisiert/zu Ende schaut
   (vgl. Feed-Scoring). Mindestens 2 unabhängige Quellen, damit keine Zufallstreffer.

## Darstellung und Regeln
- **Eigener Bereich „Entdecken"** (nicht im Feed, nicht auf der Startseite): max. **3 Kanalvorschläge pro Woche**,
  je mit Begründung („Wird von 3 deiner Lern-Kanäle verlinkt/empfohlen"), Aktionen: **Ansehen**, **Abonnieren**,
  **Nicht interessiert** (dauerhaft ausblenden).
- Beim Abonnieren Pflichtfrage nach der **Feed-Gruppe/Priorität** (Lernen/Unterhaltung/Nebenbei) — so bleibt das
  Feed-Scoring bedeutungsvoll.
- **Gegengewicht Abo-Hygiene:** Zu jedem neuen Abo wird ein selten genutztes vorgeschlagen („Dieses Abo hast du seit
  90 Tagen nie zu Ende geschaut — entfernen?"), damit Abozahl nicht ungebremst wächst.
- **Keine Endlosliste, kein Nachladen**; Rest verfällt nach der Woche. Liste wird nur beim Wochenwechsel neu bestimmt
  (Zustand in `zen`-Einstellungsdatei, Uhr-Manipulation erkennen).
- Opt-in: Standardmäßig aus; bei Aktivierung Absichtsabfrage-Stil-Hinweis („Entdecken ist zum Auswählen, nicht zum Stöbern").

## Priorität / Aufwand
Phase 2 (nach Zen-Paket, Feed mit Ende, Pflichtpause). Aufwand M. Merge-Risiko gering (neuer Bereich im `zen`-Paket,
nur Datensammlung beim Videoöffnen als kleiner Hook).

## Offen
- Was liefert der NewPipeExtractor an verwandten Kanälen/featured channels? Vor Umsetzung klären.
- Wöchentlich 3 Vorschläge ok?
