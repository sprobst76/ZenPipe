# Spec: Praxis-Kanäle (Yoga, Atemübungen, Meditation, Einschlafhilfen)

Anforderung (Nutzer, 2026-10-13): Eine Kategorie „Praxis“ für Inhalte, die meist verfügbar sein sollen. Maßgeblich ist
`rules.md` (Abschnitt „Praxis“); diese Spec beschreibt Begründung und Umsetzung. **Status: umgesetzt (2026-10-13).**

## Zuordnung
- **Nur durch den Nutzer**, pro **Kanal** (nie pro Video, keine automatische Erkennung über Titel/Stichworte/Kategorie).
- Identität: `serviceId` + normalisierte Kanal-URL (`ZenPraxisChannel.keyOf`: Schema, `www.`/`m.` und abschließender `/` egal,
  Host klein, Pfad/Kanal-ID unverändert). Auf der Kanalseite wird `ChannelInfo.getUrl()` (kanonisch `/channel/UC…`) gespeichert,
  dieselbe Form liefern `StreamInfo.getUploaderUrl()` (Wiedergabe) und `StreamEntity.uploaderUrl` (Feed-DB).
- Ein Kanal kann zugleich abonniert/in Feed-Gruppen und Praxis sein. **Alle** seiner Videos gelten dann als Praxis-Videos: sie
  erscheinen im Praxis-Tab und **nicht** in der Feed-Ausgabe (sie konkurrieren nicht um die 15 Plätze und zählen nicht zum
  Tages-Deckel 30). Bereits gebaute Ausgaben werden nicht neu gewürfelt (Regel „Laufendes bleibt unberührt“).
- **Playlists:** nicht umgesetzt (Folgeaufgabe, s. u.).

## Markieren / Entfernen (24-h-Sperre)
- Kanalseite > Überlaufmenü: „Als Praxis-Kanal markieren“ / „Ausstehenden Praxis-Kanal abbrechen“ / „Aus Praxis-Kanälen
  entfernen“ (`ZenPraxisUi`). Verwaltung unter Einstellungen > Zen (Liste mit Status, „Entfernen“/„Abbrechen“).
- **Hinzufügen = Lockerung** über `ZenSettingsLock.addPraxis`, mit denselben Regeln wie die übrigen Zen-Einstellungen:
  Bestätigungsdialog mit 3 s Wartezeit; **ausstehend**, wirksam mit dem Ende der dadurch gestarteten **24-h-Sperre**
  (Gerätelaufzeit, `ZenCountdown`, fail-closed); bis dahin abbrechbar (Abbrechen lässt die Sperre weiterlaufen);
  **5-min-Rückgängig** (stellt Liste und Sperre exakt wieder her). Läuft bereits eine Sperre, ist Hinzufügen **abgelehnt**
  („gesperrt bis …“), genau wie jede andere Lockerung (globale Sperre, sonst Umgehung durch Umschalten).
- **Entfernen = strenger**, wirkt sofort (auch während der Sperre), startet/verlängert keine Sperre, 5-min-Rückgängig.
  Entfernen eines nur ausstehenden Kanals = dessen Hinzufügen abbrechen.
- **Ersteinrichtung** (noch nicht „Fertig“): Hinzufügen sofort wirksam, ohne Sperre (wie alle Einstellungen dort).
- Persistenz in `zen_state` (`zs_praxis` wirksam, `zp_praxis` ausstehend, Rückgängig-Kopie `zs_undo_praxis*`), also nicht in
  Export/Import/Reset/Auto-Backup.
- ZenLog: `praxis_add` (Ersteinrichtung), `praxis_add_pending`, `praxis_activated`, `praxis_pending_cancelled`, `praxis_remove`;
  Rückgängig als `settings_undo`.

## Ausnahmen
| Regel | Praxis-Video |
|---|---|
| Ausgabe 15/Stunde, Tages-Deckel 30, Mindestlänge 5 min, Längenbonus/Scoring, max. 3 je Kanal | gelten **nicht** (nicht Teil der Ausgabe) |
| Pflichtpause nach Abbruch | **keine** (wie Musik: `ZenKind.PRAXIS`, `abort_exempt kind=PRAXIS`); kein „Später fortsetzen“-Dialog nötig |
| Pausieren | bleibt kein Abbruch (unverändert) |
| Laufende Pflichtpause | blockiert auch Praxis-Starts (unverändert streng, siehe Offen) |
| Autoplay, Related, Kommentare, Shorts, Livestreams, Absichtsabfrage | **unverändert** |

Die Einordnung als Praxis passiert zur Wiedergabe, sobald die `StreamInfo` vorliegt (`ZenPause.onStreamInfo` ->
`ZenContentKind.classify(praxis = …)`; Praxis hat Vorrang vor Musik/Podcast). Ist die Art beim Abbruch noch unbekannt, gilt
streng „Video“.

## Praxis-Tab
- Eigener Tab **„Praxis“** (`Tab.PraxisTab`, ID 10, immer direkt nach dem Feed, wird auch bei bestehenden Tab-Listen ergänzt)
  und Drawer-Eintrag „Praxis“ (`ZenPraxisFragment`).
- Inhalt: die neuesten Videos (Videos-Tab des Kanals, ohne Shorts/Livestreams) jedes **aktiven** Praxis-Kanals, **max. 15 pro
  Kanal**, zusammen nach Datum sortiert, **kein Nachladen** (endliche Liste), kein Auswahl-Kontingent. Geladen wie eine
  Kanalseite übers Netz (der Kanal muss nicht abonniert sein; `ExtractorHelper`-Cache). Kopfzeile: „Praxis heute: N min“ und
  Anzahl ausstehender Kanäle. Nach Änderungen an der Liste lädt der Tab beim nächsten Anzeigen neu.
- Kanalseiten (15 Einträge) und Suche (2 Seiten) bleiben wie gehabt nutzbar.

## Praxis-Minuten
- Wiedergabezeit jeder Praxis-Sitzung wird beim Sitzungsende (Schließen, Wechsel, zu Ende, Prozess-Kill beim nächsten Start)
  dem **Zen-Tag** (04:00) des Endzeitpunkts gutgeschrieben (`ZenPraxisTime`, Schlüssel `px_time`, 30 Tage).
- Anzeige: Praxis-Tab (Kopfzeile) und Einstellungen > Zen (Info-Zeile). Eine eigene Tagesübersicht gibt es (noch) nicht.
- **Kein Limit** (Entscheidung). Hook für ein späteres optionales weiches Limit: `ZenPraxisTime.msOn(day)`.

## Sleep-Timer
NewPipe hat **keinen** Sleep-Timer (Code und Strings durchsucht, 2026-10-13). Nicht gebaut (Offenpunkt).

## Offen / Folgeaufgaben
- **Playlists als Praxis** (z. B. eine kuratierte Yoga-Playlist eines sonst „normalen“ Kanals): Zuordnung über Playlist-URL;
  bräuchte die Playlist-ID in der Wiedergabe (PlayQueue), daher zurückgestellt.
- **Praxis während einer laufenden Pflichtpause starten?** Derzeit blockiert (streng, unverändert). Empfehlung: erlauben, weil
  Praxis die gewünschte Alternative ist; dafür müsste die Pausen-Sperre den Kanal vor dem Start kennen (Start-URL reicht nicht).
- Markieren über das Long-Press-Menü von Kanal-Einträgen (Abo-Liste) fehlt; nur Kanalseite + Verwaltung.
- Minuten einer noch laufenden Praxis-Sitzung erscheinen erst nach ihrem Ende.
- Weiches Praxis-Limit (Hinweis ab N min/Tag): nur Hook, nicht gebaut.
- Sleep-Timer (s. o.).
