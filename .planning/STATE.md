# State

- Stand: 2026-10-12
- Phase: MVP umgesetzt (Branch `zen`), auf Motorola Defy verifiziert

## Erledigt (je ein Commit `zen:`)
1. Drawer: nur Abos, Feed, Playlists, Suche (+ Einstellungen, Info); Kiosk/Downloads/Verlauf/Spenden entfernt. Kiosk-/Blank-/History-Tabs werden in `TabsManager.getTabs()` gefiltert, Default-Tabs = Feed, Abos, Playlists, Einstellung "Inhalt der Hauptseite" entfernt. Startseite = Feed.
2. Autoplay (`PlayerHelper.getAutoplayType` = NEVER), Auto-Queue (`isAutoQueueEnabled` = false), Kommentare und Related Items (`VideoDetailFragment` Flags fest false) hart aus; Settings-Schalter entfernt; Shorts-Kanal-Tab und Shorts-Feed-Fetch in `ChannelTabHelper` gesperrt.
3. Absichtsabfrage `util/ZenIntentPrompt` (aufgerufen am Ende von `MainActivity.onCreate`): 5 s Wartezeit, "Weiter"/"Doch nicht" (App schliessen), DE+EN Strings.

## Verifiziert (Defy ZY32CVNR2B, Logcat ohne FATAL)
- Drawer-Eintraege per uiautomator; Feed als Startseite; Video-Detailseite ohne Kommentare/Related, startet nicht automatisch; Dialog-Countdown (Continue (2) disabled), Weiter schliesst Dialog, Doch nicht beendet App.

## Offen
- Absichtsabfrage nicht bei geteilten Links (RouterActivity) und nicht bei Rueckkehr aus dem Hintergrund (nur Prozessstart); Eingabe der Absicht wird nicht gespeichert.
- Autoplay=NEVER: Link-Oeffnen zeigt Auswahldialog, Video startet erst auf Play-Tipp.
- Einstellungs-Screens (Video/Audio, Inhalt) nach Entfernen der Schalter nicht manuell durchgeklickt; Unit-Tests (`testDebugUnitTest`) nicht gelaufen.
- Branding/applicationId/Signierung, Shorts in Suchergebnissen, Tageslimit etc. spaeter.
