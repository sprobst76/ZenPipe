# Spec: 24-Stunden-Sperre für Zen-Einstellungen (Time-Lock)

Anforderung (Nutzer, 2026-10-12): Nach einer Änderung an den Zen-Einstellungen können diese 24 h lang nicht erneut geändert
werden. Entscheidungen F7-F9 (2026-10-12) sind eingearbeitet. **Status: umgesetzt (Phase 2 Teil 1, 2026-10-12).** Maßgeblich ist `rules.md`
(Abschnitte „Zen-Einstellungen“ und „Einstellungs-Sperre“); Abweichungen dieser Spec von dort sind unten markiert.

## Auflösungen bei der Umsetzung (2026-10-12, gelten vor dem Rest dieser Spec)
- **Nur Lockern startet die Sperre.** Der Satz „jede bestätigte Änderung startet eine 24-h-Sperre“ widerspricht F7 („strenger startet/verlängert
  die Sperre nicht“). Umgesetzt: eine Gruppe mit mindestens einer Lockerung startet die Sperre (die Lockerung wird ausstehend und wirkt
  mit dem Sperrende); rein strengere Gruppen starten nichts. Während der Sperre sind Lockerungen abgelehnt, Strenger geht immer.
- **Richtung pro Einstellung, Gruppen werden aufgeteilt** (strenger sofort, lockerer Teil ausstehend). „Gemischt = Lockern“ entfällt, bis es die
  Nachtruhe (Zeitfenster) gibt.
- **Ersteinrichtung:** Ende durch „Fertig“, ersten Wiedergabestart oder 24 h Gerätelaufzeit seit dem ersten Zugriff ([archive/consistency-review.md](archive/consistency-review.md) S8/S9);
  danach beginnt die Sperre. Keine Rückgängig-Funktion in der Einrichtung.
- **Rückgängig** wirkt auch auf die Sperre: hat die Änderung sie gestartet, wird sie wieder gelöscht. Abbrechen einer ausstehenden Lockerung
  lässt die Sperre dagegen weiterlaufen.
- Notfallschlüssel: bewusst nicht gebaut (Default aus).

## Regel
- **Asymmetrie (F7):** **Strenger stellen wirkt sofort. Lockern wirkt erst nach 24 h** und ist bis dahin abbrechbar
  („ausstehende Änderung“, im UI sichtbar). Das schützt den heißen Moment, ohne dass man den Tag über festhängt.
- [überholt: nur eine **Lockerung** startet die Sperre, siehe „Auflösungen“ oben und decisions.md D31] Jede **bestätigte Änderung** startet eine **24-h-Sperre** für **alle** Zen-Einstellungen (ein globaler Zeitpunkt `lockedUntil`,
  nicht pro Einstellung, sonst Umgehung durch Umschalten anderer Werte). 24 h = Dauer in Vertrauenszeit, unabhängig von Zen-Tag
  und Zeitzone. Während der Sperre sind Einstellungen sichtbar, aber ausgegraut: „Änderbar ab <Datum/Uhrzeit>“, **nur Datum und
  Uhrzeit, kein laufender Countdown und keine Benachrichtigung** bei Ablauf.
- **Strenger während der Sperre:** erlaubt, ohne die Sperre zu verlängern oder neu zu starten (strenger ist nie ein Schlupfloch).
  Kosmetik ohne Lockerungswirkung (z. B. Graustufen **an**) startet keine Sperre.
- **Richtung pro Einstellung** fest im Code (`stricterIf(alt, neu)`): z. B. Mindestlänge senken = lockern, Entdecken **an** =
  lockern, aus = strenger, Graustufen an = strenger, Ausgabe-Größe senken bei gleichem Deckel = strenger. Gemischte Änderungen
  (Zeitfenster verschoben) zählen als Lockern. [überholt: Gruppen werden pro Einstellung aufgeteilt, siehe „Auflösungen“ oben]
- **Geltungsbereich:** Ausgabe-Größe/Deckel/Mindestlänge, Pflichtpause-Parameter, Absichtsabfrage-Parameter (Wartezeit, Schwellen),
  Nachtruhe, Entdecken an/aus, Graustufen aus u. Ä. [Nachtruhe, Entdecken, Graustufen sind nicht gebaut; tatsächliche Liste plus Praxis,
  Ausnahmen, Inhalts-Erkennung, Feed-Sprachen: rules.md „Zen-Einstellungen“] (alles, was Zen lockern oder abschalten könnte).
- **Nicht gesperrt (F8):** Feed-Gruppen-Zuordnung und -Reihenfolge (die Reihenfolge ist die Priorität im Scoring, sie gewichtet nur
  und vergrößert nichts), Abonnieren/Deabonnieren, Playlists, Suche, normale Player-Einstellungen. Die Pflichtfrage nach der
  Gruppe beim Abonnieren (Entdecken) bleibt damit möglich.
- **Laufendes bleibt unberührt:** Eine laufende Pause wird durch keine Änderung verkürzt; Parameteränderungen wirken ab der
  **nächsten** Feed-Ausgabe (kein Neuwürfeln der laufenden); ein gesenkter Deckel gilt sofort, ein erhöhter erst nach Wirksamwerden
  des Lockerns.

## Schutz vor Frust
- **Bestätigungsdialog vor dem Speichern** mit kurzer Wartezeit (3 s) und Zusammenfassung: „Strenger: gilt sofort. Lockern: gilt
  in 24 h, bis dahin abbrechbar. Danach sind Zen-Einstellungen 24 h gesperrt.“ (Ersetzt den 30-s-Countdown + Begründungsfeld aus
  dem Audit.)
- **Rückgängig-Fenster (F9):** 5 min nach der Bestätigung zurück auf **exakt den vorherigen Stand**, protokolliert, startet keine
  neue Sperre (Tippfehler beim Strengerstellen, z. B. Deckel 3 statt 30).
- **Plausibilitätsgrenzen:** Deckel >= Ausgabe-Größe, Ausgabe >= 5.
- **Gruppierte Änderungen:** Mehrere Änderungen in einem Einstellungsdialog = eine Bestätigung = eine Sperre. Dazu Entwurf
  (`pendingConfig`) mit explizitem „Übernehmen“ statt Autosave des NewPipe-Preference-Musters.
- **Ersteinrichtung:** Beim ersten Start **der Funktion** (fehlender Schlüssel `setup_done` in `zen_state`, gilt auch für
  Bestandsinstallationen nach einem Update) gibt es einen Einrichtungsablauf; die Sperre beginnt mit „Fertig“, **spätestens** beim
  ersten Videostart oder nach 24 h (sonst bliebe sie bei nie abgeschlossener Einrichtung dauerhaft frei). Die Zen-Regeln gelten von
  Anfang an mit Defaults.

## Umgehungsschutz
- `lockedUntil` und ausstehende Änderungen liegen in der eigenen Zen-Datei (`zen_state`, nicht über Daten-Import/Reset/Auto-Backup
  überschreibbar, `lockedUntil` wird nie importiert).
- Die Restdauer läuft über `ZenCountdown` (nur mit Gerätelaufzeit, wanduhrunabhängig). Uhr vor-/zurückstellen und Neustart ändern
  sie nicht (fail-closed; Ausschaltzeit zählt nicht).
- Realistische Grenzen: Wer App-Daten löscht oder neu installiert, setzt den Zustand zurück (Ein-Personen-Fork, vgl.
  `research-achtsamer-konsum.md`); das wird nicht verhindert. **Das Zen-Protokoll ist nicht Teil des Exports** (der Export-Ausschluss
  von `zen_state` ist Absicht, sonst wäre der Import ein Bypass). Einen „Zen wurde zurückgesetzt“-Hinweis gibt es nur, wenn eine
  Datenbank mit Zen-Markern **importiert** wird, nicht nach dem Löschen aller App-Daten (dann ist auch der Marker weg).
- Debug-Builds sind per `run-as` editierbar (Entwicklerumweg, akzeptiert).

## Offene Entscheidungen (aus der ursprünglichen Spec, entschieden)
1. Strenger während der Sperre: **ja**, ohne Neustart der Sperre (durch F7 entschieden).
2. **Notfallschlüssel** (Opal-Stil, 1x pro Woche, mit Wartezeit und Protokoll): standardmäßig aus, in Phase 2 optional. [überholt: bewusst nicht gebaut, decisions.md D35]
3. Lockern zusätzlich erst zum nächsten Zen-Tag (4:00)? **Nein**, 24 h sind die einzige Regel.

## Priorität
Phase 2 (vor Tageslimit/weiteren lockerbaren Features), nutzt das Zen-Paket aus Phase 1 (`ZenCountdown`, `ZenStore`, `ZenLog`).
Ersetzt das frühere Prinzip „Lockern erst am nächsten Tag 4:00“ aus `research-achtsamer-konsum.md`.
