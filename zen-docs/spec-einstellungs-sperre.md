# Spec: 24-Stunden-Sperre für Zen-Einstellungen (Time-Lock)

Anforderung (Nutzer, 2026-10-12): Nach einer Änderung an den Zen-Einstellungen können diese 24 h lang nicht
erneut geändert werden.

## Regel
- Jede **bestätigte Änderung** an einer Zen-Einstellung startet eine **24-h-Sperre** für **alle** Zen-Einstellungen
  (ein globaler Zeitstempel `lockedUntil`, nicht pro Einstellung — sonst Umgehung durch Umschalten anderer Werte).
- Während der Sperre sind Zen-Einstellungen sichtbar, aber ausgegraut; Anzeige „Änderbar ab <Datum/Uhrzeit>"
  (Restzeit). Keine Hintertür in der UI.
- Geltungsbereich „Zen-Einstellungen": Feed-Budget/Deckel/Mindestlänge, Gruppen-/Kanal-Prioritäten, Pflichtpause-Parameter,
  Absichtsabfrage-Parameter (Wartezeit, Schwellen), Nachtruhe, Entdecken an/aus, Graustufen u. Ä. — alles, was Zen lockern
  oder abschalten könnte. Nicht betroffen: Abonnieren/Deabonnieren, Playlists, Suche, normale Player-Einstellungen.

## Schutz vor Frust (wichtig, sonst Vermeidungsverhalten)
- **Bestätigungsdialog vor dem Speichern:** „Diese Änderung gilt sofort. Danach sind Zen-Einstellungen 24 h gesperrt."
  mit kurzer Wartezeit (3 s) und Zusammenfassung der Änderung.
- **Ersteinrichtung:** Beim ersten Start gibt es einen Einrichtungsablauf; die Sperre beginnt erst mit „Fertig".
  Bis dahin frei änderbar. (Verhindert, dass ein Tippfehler am ersten Tag einen Tag lang festhängt.)
- **Gruppierte Änderungen:** Mehrere Änderungen in einem Einstellungsdialog = eine Bestätigung = eine Sperre.

## Offene Entscheidungen (Empfehlung zuerst)
1. **Strenger stellen während der Sperre?** Empfehlung: erlaubt, ohne die Sperre zu verlängern oder neu zu starten
   (verschärft ist nie ein Schlupfloch). Alternative: strikt keine Änderung, auch nicht strenger.
2. **Notfallschlüssel** (Opal-Stil, 1x pro Woche, mit Wartezeit und Protokolleintrag)? Empfehlung: standardmäßig aus;
   in Phase 2 optional.
3. **Lockern/Abschalten einzelner Funktionen** (z. B. Pflichtpause aus): zusätzlich zu den 24 h erst zum nächsten
   Zen-Tag (4:00)? Empfehlung: nein, 24 h reichen als einzige Regel (einfacher zu verstehen).

## Umgehungsschutz
- `lockedUntil` in der eigenen Zen-Einstellungsdatei (nicht über Daten-Import/Reset/Auto-Backup überschreibbar);
  Restzeit über monotone Zeit (SystemClock.elapsedRealtime) + Wanduhr; Uhr-Manipulation wird erkannt und die
  Sperre bleibt bestehen (Fail-closed).
- Realistische Grenzen: Wer App-Daten löscht oder neu installiert, setzt den Zustand zurück (Ein-Personen-Fork; vgl.
  research-achtsamer-konsum.md). Gegenmaßnahme: Löschen/Neuinstallation nicht verhindern, aber Protokoll im Export
  und Hinweis beim Neustart („Zen wurde zurückgesetzt").

## Priorität
Phase 2 (vor Tageslimit/weiteren lockerbaren Features), nutzt das Zen-Paket aus Phase 1.
Konsistenz mit „Lockern erst am nächsten Tag" aus research-achtsamer-konsum.md: ersetzt dieses Prinzip durch die
einfachere 24-h-Regel.
