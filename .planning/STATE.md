# State

- Stand: 2026-10-12
- Phase: MVP umgesetzt (Branch `zen`), auf Motorola Defy verifiziert

## Erledigt (je ein Commit `zen:`)
1. Drawer: nur Abos, Feed, Playlists, Suche (+ Einstellungen, Info); Kiosk/Downloads/Verlauf/Spenden entfernt. Kiosk-/Blank-/History-Tabs werden in `TabsManager.getTabs()` gefiltert, Default-Tabs = Feed, Abos, Playlists, Einstellung "Inhalt der Hauptseite" entfernt. Startseite = Feed.
2. Autoplay (`PlayerHelper.getAutoplayType` = NEVER), Auto-Queue (`isAutoQueueEnabled` = false), Kommentare und Related Items (`VideoDetailFragment` Flags fest false) hart aus; Settings-Schalter entfernt; Shorts-Kanal-Tab und Shorts-Feed-Fetch in `ChannelTabHelper` gesperrt.
3. Absichtsabfrage `util/ZenIntentPrompt` (aufgerufen am Ende von `MainActivity.onCreate`): 5 s Wartezeit, "Weiter"/"Doch nicht" (App schliessen), DE+EN Strings.

## Verifiziert (Defy ZY32CVNR2B, Logcat ohne FATAL)
- Drawer-Eintraege per uiautomator; Feed als Startseite; Video-Detailseite ohne Kommentare/Related, startet nicht automatisch; Dialog-Countdown (Continue (2) disabled), Weiter schliesst Dialog, Doch nicht beendet App.

## Update 2026-10-12 (Verifikation + Absichtsabfrage)
- Unit-Tests: `./gradlew testDebugUnitTest` (gesamte Suite) gruen, keine Anpassungen noetig.
- Einstellungen "Video und Audio" + "Inhalt" auf dem Defy geoeffnet (uiautomator + Screenshot): rendern ohne Crash, keine toten Eintraege, Logcat ohne FATAL.
- Shorts in Suchergebnissen ausgeblendet (`SearchFragment.zenWithoutShorts`, `StreamInfoItem.isShortFormContent()`); Suche auf dem Defy ohne Fehler (Positiv-Fall mit echtem Short nicht gezielt geprueft).
- `ZenIntentPrompt` erweitert: zusaetzlich bei externen/geteilten Links (`MainActivity.handleIntent`, Stream/Kanal/Playlist ohne PlayQueue) und bei Rueckkehr nach >5 min im Hintergrund (`MainActivity.onStart/onStop`). Cooldown 5 min seit letzter Frage; kein Dialog bei laufender Wiedergabe (`AudioManager.isMusicActive`). Auf dem Defy mit temporaer 15 s/20 s Schwellen getestet: Link im Cooldown -> kein Dialog, nach Cooldown -> Dialog; kurzer Hintergrund (8 s) -> kein Dialog, langer (25 s) -> Dialog; bei laufender Wiedergabe -> kein Dialog. Commit enthaelt 5 min.
- Autoplay bleibt dauerhaft AUS (Entscheidung): Link-Oeffnen zeigt Auswahldialog, Video startet erst nach Play-Tipp.

## Offen
- Absicht (Freitext) wird nicht gespeichert (nicht gewuenscht).
- Der RouterActivity-Auswahldialog erscheint vor der Absichtsabfrage (Dialog kommt erst in MainActivity).
- Branding/applicationId/Signierung, Tageslimit etc. spaeter.
