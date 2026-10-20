# Spec: Praxis-Kanäle und -Playlists (Yoga, Atemübungen, Meditation, Einschlafhilfen)

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
- **Playlists** (umgesetzt 2026-10-13, nur Online-Playlists, keine lokalen): gleiche Liste und gleiche Sperre wie Kanäle
  (`ZenPraxisChannel.playlist`, gespeichert mit Flag `p`). Praxis sind die Videos, die **aus der Playlist selbst geladen**
  wurden (`ZenPraxisPlaylistItems`, `px_pl_items`): der Praxis-Tab lädt die erste Seite (bei YouTube bis 100) und registriert
  sie, die Playlist-Seite registriert ihre geladenen Seiten; max. 200 je Playlist. [überholt 2026-10-20: „jedes Laden ersetzt die
  Menge“ – seit D63 wird nur noch ergänzt, siehe „Eingefroren“] Gezählt wird
  nur, solange die Playlist aktiv ist (Entfernen wirkt sofort). Bewusst **nicht** über den Warteschlangen-Kontext: in eine
  Praxis-Warteschlange eingereihte fremde Videos würden sonst Praxis. Grenze: Videos jenseits der geladenen Seiten einer sehr
  langen Playlist zählen erst, wenn die Seite geladen wurde.
- **Eingefroren (D63, umgesetzt 2026-10-20):** Der Inhalt einer Praxis-Playlist wird beim Aktivieren eingefroren, damit eine
  eigene YouTube-Playlist kein Freifahrschein wird (Analyse 2026-10, A1.2). **Basis** (zählt sofort ab Aktivierung) sind die
  Videos, die geladen wurden, **bevor** die Playlist aktiv war: die Playlist-Seite, auf der sie markiert wurde, jedes Öffnen der
  Seite während der 24 h „ausstehend“ und der Praxis-Tab, der ausstehende Playlists dafür im Hintergrund lädt (nicht anzeigt);
  in der Ersteinrichtung zählt jedes Laden als Basis. Ein Video, das erst in einer **aktiven** Playlist auftaucht, ist **neu**:
  es wird mit der beobachteten Gerätelaufzeit vermerkt (`ZenUptime` „praxis“, `ut_praxis_*`) und zählt erst nach **24 h
  Gerätelaufzeit** (Wanduhr egal, Ausschaltzeit zählt nicht, Neustart fail-closed; startet und braucht keine Einstellungs-Sperre).
  Bis dahin ist es ein normales Video (Pflichtpause, während einer Pause gesperrt, kein Teil einer Praxis-Kette), der Praxis-Tab
  zeigt es nicht und nennt in der Kopfzeile „N neue Playlist-Videos zählen … (das nächste ab …)“. Laden ergänzt nur, ersetzt
  nie: ein Video, das auf YouTube aus der Playlist entfernt wurde, bleibt Praxis (harmlos), die Grenze 200 je Playlist bleibt
  (darüber hinaus wird nichts mehr aufgenommen). Wird eine Playlist nie vor der Aktivierung geladen (z. B. per langem Druck
  markiert und der Praxis-Tab nie geöffnet), gilt ihr ganzer Inhalt als neu (streng). Migration: Zeilen älterer Versionen
  (`playlist<TAB>video`) bleiben unverändert und sind Basis; neue Videos stehen als `playlist<TAB>video<TAB>gesehenBeiLaufzeitMs`
  in derselben Liste, eine unlesbare Zeit wartet von vorn.

## Markieren / Entfernen (24-h-Sperre)
- Kanalseite > Überlaufmenü: „Als Praxis-Kanal markieren“ / „Ausstehenden Praxis-Kanal abbrechen“ / „Aus Praxis-Kanälen
  entfernen“ (`ZenPraxisUi`), Playlist-Seite entsprechend („Als Praxis-Playlist markieren“ …). **Langer Druck** auf einen
  Kanal- oder Playlist-Eintrag in Listen (Suche, Kanal-Tabs, alle `BaseListFragment`-Listen) öffnet einen kleinen Dialog mit
  derselben Aktion; in den Abos ist sie ein zusätzlicher Eintrag im Long-Press-Dialog. Verwaltung unter Einstellungen > Zen
  (Liste mit Status und Kennzeichnung „Playlist“, „Entfernen“/„Abbrechen“).
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
| Laufende Pflichtpause | Praxis-Videos **dürfen starten** (Pause läuft weiter); alles andere bleibt gesperrt (Vorprüfung beim Start, verbindlich im Player nach `StreamInfo`) |
| Weiterspielen | nur als Praxis-Kette (s. u.); jedes andere Video hält am Ende an |
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
- [überholt 2026-10-13: Praxis-Minuten werden laufend über `ZenWatchTime` (`wt_<Tag>`) gebucht, `px_time` wird nur noch gelesen;
  rules.md „Praxis-Minuten“] Wiedergabezeit jeder Praxis-Sitzung wird beim Sitzungsende (Schließen, Wechsel, zu Ende, Prozess-Kill beim nächsten Start)
  dem **Zen-Tag** (04:00) des Endzeitpunkts gutgeschrieben (`ZenPraxisTime`, Schlüssel `px_time`, 30 Tage).
- Anzeige: Praxis-Tab (Kopfzeile) und Einstellungen > Zen (Info-Zeile). Eine eigene Tagesübersicht gibt es (noch) nicht.
- **Kein Limit** (Entscheidung). Hook für ein späteres optionales weiches Limit: `ZenPraxisTime.msOn(day)`.

## Praxis während der Pflichtpause (2026-10-13)
Praxis-Videos dürfen auch während einer laufenden Pflichtpause starten; alles andere bleibt gesperrt, die Pause läuft weiter.
Nur die Zuordnung zählt (Kanal-URL des Uploaders bzw. bekanntes Playlist-Video), nie Titel/Stichworte. Startstellen prüfen mit
Listendaten (`ZenPause.blockStart(…, praxis)`, Warteschlange: aktuelles Element; ohne `uploaderUrl` = gesperrt), der Player
prüft verbindlich nach `StreamInfo` und schließt bei Nicht-Praxis (`pause_block_running`). Praxis-Abbruch erzeugt weiter keine Pause.

## Praxis-Kette (Nutzerentscheidung 2026-10-13)
In ZenPipe startet nie ein Video von selbst: der Player pausiert am Ende **jedes** Videos, auch in selbst gebauten Warteschlangen
und bei „Alle abspielen“ (vorher spielte NewPipe eine vom Nutzer gebaute Warteschlange nahtlos weiter, ExoPlayer-Übergang
`AUTO_TRANSITION`; Autoplay/Related-Einreihen war schon aus). **Einzige Ausnahme:** aneinandergehängte Praxis-Videos. Ist die
laufende Sitzung Praxis und das nächste Element nach Listendaten Praxis, läuft es nahtlos weiter; sonst bleibt der Player am
Ende stehen (`chain_stop`, Sitzung „zu Ende“). Stellt sich ein verkettetes Element nach `StreamInfo` als Nicht-Praxis heraus,
pausiert der Player sofort. Wiederholen „eins“ bleibt Nutzerwahl; bei „alle“ gilt die Regel auch für den Sprung zum Anfang.
Am Defy geprüft: Praxis-Playlist läuft 1 -> 2 -> 3 -> 4 durch, auch bei gesperrtem Bildschirm.

## Sleep-Timer (2026-10-13)
Mond-Knopf in der Player-Kopfzeile (Video- und Popup-Player [überholt: Pop-up entfernt, decisions.md D15], Text = Countdown) und Menüeintrag „Sleep-Timer“ in der
Warteschlange (Hintergrund-Player). 15/30/45/60/90 min (Debug-Builds zusätzlich „1 min (debug)“ für Gerätetests), „Ausschalten“.
Kein „Ende des Videos“: am Videoende hält der Player ohnehin an. Läuft auf `elapsedRealtime` über Videogrenzen der
Praxis-Kette hinweg und endet mit dem Player (Entscheidung: an den Player gebunden, nicht ans Video, sonst würde eine
Einschlaf-Kette nach dem ersten Video ohne Timer weiterlaufen). Letzte 30 s Fade auf 5 %, dann Pause, Lautstärke zurück.
Ablauf = **kein Abbruch** (`ZenPauseEngine.endBySleepTimer`, `sleep_end`): keine Pflichtpause, kein „Später“-Verbrauch,
Praxis-Minuten zählen; Weiterspielen am Morgen beginnt eine neue Sitzung (die Zeit zählt also wieder). Standard-Dauer unter
Einstellungen > Zen (wird im Menü zuerst angeboten, keine Sperre, kein Auto-Start). Kopplung an Einschlaferkennung: Hook
`ZenSleepSignal`, Analyse in `research-einschlaferkennung.md`.

## Offen / Folgeaufgaben
- Lokale Playlists als Praxis (bewusst nicht: beliebig befüllbar).
- Lesezeichen-Playlists (Tab „Lesezeichen“) ohne Long-Press-Eintrag; Markieren dort über die Playlist-Seite.
- [erledigt 2026-10-13 durch laufende Buchung in `ZenWatchTime`] Minuten einer noch laufenden Praxis-Sitzung erscheinen erst nach ihrem Ende.
- Weiches Praxis-Limit (Hinweis ab N min/Tag): nur Hook, nicht gebaut.
- Sleep-Timer: keine Anzeige in der Benachrichtigung; Einschlaferkennung nur als Hook.
