# ZenPipe – Feature-Audit (NewPipe-Bestand + neue Zen-Features)

**Stand:** 12.10.2026 · Branch `zen` (HEAD `f120f8cc5`, dazu eine noch nicht committete Änderung an `SearchFragment.java`, an der parallel gearbeitet wird)
**Bezug:** `ZenPipe-Konzept_v0.1.md`, `research-achtsamer-konsum.md`, `.planning/STATE.md`
**Methode:** Code-Durchsicht von `app/src/main` (alle `res/xml/*_settings.xml`, `MainActivity`, `RouterActivity`, `VideoDetailFragment`, `Player`/`PlayerHelper`, `local/feed/*`, `fragments/list/*`, `info_list/dialog/*`, `settings/tabs/*`, `error/*`, `NewVersionWorker`, `AndroidManifest.xml`) plus eine kurze Web-Recherche zu vergleichbaren Tools. Es wurde nichts gebaut, nichts installiert und kein Code geändert.

Legende:
- **[Code]** = im Repo nachgesehen (Datei/Zeile genannt)
- **[Vermutung]** = Schlussfolgerung oder Erinnerung, nicht im Code bzw. nicht am Gerät geprüft
- **Aufwand:** S ≤ 1 Tag · M 2–4 Tage · L ≥ 1 Woche
- **Merge-Risiko:** wie wahrscheinlich die Änderung beim Rebase auf Upstream Konflikte erzeugt. Gering = neue Datei oder 1–3 Zeilen in einer selten geänderten Stelle. Mittel = mehrere Hunks in einer stark gepflegten Klasse. Hoch = Umbau oder Änderungen an vielen Stellen.

---

## 1. Kurzfazit und Top-10-Empfehlungen

**Kurzfazit**

Der MVP greift an den richtigen Stellen: Drawer, Kiosk-Tabs, Auto-Queue, Kommentare, Related, Shorts-Kanal-Tab und Absichtsabfrage. Die Patches sind klein und lokal. Die Code-Durchsicht zeigt aber drei Arten von Lücken:

1. **Seitentüren in den Sog, die noch offen sind:** „Im Browser öffnen“ (19 Aufrufstellen, führt direkt auf youtube.com mit allen Empfehlungen), Live-Streams im Feed und auf Kanalseiten, Remote-Suchvorschläge (YouTube-Autocomplete, also Trend-Begriffe), „Alle abspielen“ auf Kanal-Tabs mit endlosem Nachladen, Hashtags/Tags in der Beschreibung, die eine Suche öffnen.
2. **Die Absichtsabfrage ist umgehbar:** Sie erscheint nur einmal pro Prozess, nicht bei geteilten Links (`RouterActivity` → Hintergrund/Popup) und nicht nach Rückkehr aus dem Hintergrund. Die Absicht wird auch nicht gespeichert.
3. **Fork-Hygiene:** `applicationId` ist noch `org.schabi.newpipe` (Release), und Crash-Reports gehen an NewPipe (E-Mail und GitHub-Issues). Beides sollte vor jeder Verteilung geändert werden.

Für die neuen Zen-Features ist die wichtigste Architekturentscheidung: **ein eigenes Paket `org.schabi.newpipe.zen` mit eigenem SharedPreferences-File**. Upstream-Klassen bekommen nur Einzeiler-Hooks. Das hält den Patch-Satz rebasebar und macht den Time-Lock möglich, weil Zen-Werte dann nicht über „Einstellungen zurücksetzen“ oder „Daten importieren“ ausgehebelt werden können.

**Top 10 (Reihenfolge = Empfehlung)**

| # | Empfehlung | Aufwand | Merge-Risiko |
|---|---|---|---|
| 1 | Fork-Hygiene: eigene `applicationId`/App-Name, Crash-Report-Ziel auf ZenPipe-Repo umstellen | S | gering |
| 2 | „Im Browser öffnen“ für YouTube-Inhalte entfernen (zentral in `ShareUtils.openUrlInBrowser` oder in den Menüs) | S | gering–mittel |
| 3 | Absichtsabfrage v2: auch in `RouterActivity`, erneut nach ≥ 15 min im Hintergrund, Absicht als Text bzw. Auswahl speichern | M | gering |
| 4 | Live-Streams standardmäßig aus Feed-Abruf und Kanal-Tabs nehmen (wie Shorts per Code-Filter); Remote-Suchvorschläge standardmäßig aus | S | gering |
| 5 | Shorts-Lücken schließen: Suche (läuft parallel), Einstellungslisten bereinigen, Shorts über RSS-Schnellmodus prüfen | S | gering |
| 6 | Feed mit Ende („Du bist durch“, nur Neues seit letztem Besuch, Standardfilter „Gesehene ausblenden“) | M | mittel |
| 7 | Zen-Einstellungsbereich + **Time-Lock** (Strenger sofort, Lockern ab nächstem Tag 4:00) + monotone Uhr | M | gering |
| 8 | Sitzungs-Timer mit sanftem Ende (laufendes Video zu Ende, dann Pause-Screen) + Tageslimit mit Escape-Ventil „Nur Audio/Später-Liste“ | M | gering–mittel |
| 9 | Ruhiges Erscheinungsbild: Standard „Liste“ statt Karten, Thumbnails gedämpft oder aus (NewPipe kann „keine Bilder“ schon), optional Graustufen | S–M | gering |
| 10 | Reflexion: am Sitzungsende „Hast du gefunden, was du wolltest?“ + Wochenbilanz ohne Schuldgefühl (inkl. Umgehungs-Protokoll) | M | gering |

---

## 2. Bestandsfeatures von NewPipe – Entscheidungen

Entscheidungen: **ENTFERNEN/VERSTECKEN** · **UMKONFIGURIEREN** · **BEHALTEN** · **ERLEDIGT** (siehe Abschnitt 3 für Lücken).
Alle Fundstellen relativ zu `app/src/main/`.

### 2.1 Navigation, Startseite, Drawer

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Drawer: Kiosks (Trends, Live, Gaming, Musik, Podcasts …) | `MainActivity.addDrawerMenuForCurrentService` | **ERLEDIGT** [Code] | – | gering | Schleife über `getAvailableKiosks()` entfernt. `kioskSelected()` existiert noch als toter Code (harmlos, bleibt für kleinen Diff besser drin). |
| Drawer: Downloads | `MainActivity` (`ITEM_ID_DOWNLOADS` entfernt) | **UMKONFIGURIEREN**: wieder aufnehmen | S | gering | [Code] Downloads lassen sich weiter anstoßen (Long-Press, Detailseite, Router), die Liste ist aber nur noch über die Download-Benachrichtigung bzw. den Snackbar-Button in `VideoDetailFragment:568` erreichbar. Offline-Podcasts und -Vorträge sind bewusstes Schauen, deshalb Eintrag zurück (oder unter „Playlists/Bibliothek“). |
| Drawer: Verlauf | `MainActivity` (`ITEM_ID_HISTORY` entfernt), `Tab.HistoryTab` gefiltert | **ERLEDIGT**, später **UMKONFIGURIEREN** | S | gering | [Code] `StatisticsPlaylistFragment` ist aus der UI nicht mehr erreichbar, der Verlauf wird aber weiter aufgezeichnet (nötig für „gesehen“-Markierung und Fortsetzen). Später als Basis der Wochenbilanz nutzen (Abschnitt 4), nicht als „Meistgesehen“-Liste. |
| Drawer: Spenden | `MainActivity` | **ERLEDIGT** | – | gering | Unkritisch. NewPipe-Spendenlink bleibt im Info-Screen, das ist fair. |
| Drawer: Suche | `MainActivity` (`ITEM_ID_SEARCH`) | **ERLEDIGT** | – | gering | Suche ist laut Lukoff (2021) agency-stärkend. |
| Dienstauswahl im Drawer-Header (YouTube, SoundCloud, PeerTube, media.ccc, Bandcamp) | `MainActivity` (Service-Switcher), `ServiceHelper` | **BEHALTEN** | – | – | Kiosks sind für alle Dienste aus dem Drawer und aus den Tabs gefiltert [Code: `TabsManager.getTabs`]. Andere Dienste sind kaum Sog-Quellen. |
| Hauptseiten-Tabs (Kiosk, Default-Kiosk, Blank, Verlauf) | `settings/tabs/TabsManager.getTabs`, `TabsJsonHelper.FALLBACK_INITIAL_TABS_LIST` | **ERLEDIGT** [Code] | – | gering | Filter auf Leseseite, also auch alte gespeicherte JSONs sicher. |
| „Inhalt der Hauptseite“ (`ChooseTabsFragment`) | `res/xml/content_settings.xml` (entfernt) | **UMKONFIGURIEREN**: zurückholen, aber gefiltert | S–M | gering–mittel | [Code] Damit sind auch die sinnvollen Tab-Typen weg: `FEEDGROUP` (Feed einer Abo-Gruppe, z. B. „Lernen“), `CHANNEL`, `PLAYLIST` (z. B. „Später ansehen“ als Tab). Empfehlung: Screen wieder einhängen und im Hinzufügen-Dialog Kiosk/Blank/History ausblenden (der Lesefilter in `TabsManager` schützt ohnehin). |
| Tab-Position (oben/unten) | `appearance_settings.xml` `main_tabs_position_key` | **BEHALTEN** | – | – | Neutral. |
| Startseite = Feed | `TabsJsonHelper` | **ERLEDIGT**, prüfen | S | gering | Für Zen wäre **Abos oder eine Absicht-Startseite** konsequenter als der Feed (Feed = chronologischer Sog in Miniatur). Mit „Feed mit Ende“ (4.x) ist der Feed als Start vertretbar. Entscheidung bei Stefan, Empfehlung: Feed behalten, aber mit Ende. |

### 2.2 Video-Detailseite und Player

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Kommentare-Tab | `VideoDetailFragment.onCreate` (`showComments = false`) | **ERLEDIGT** [Code] | – | gering | `shouldShowComments()` prüft `showComments`. `CommentRepliesFragment` ist nur über Kommentare erreichbar, also mit tot. |
| Related/„Nächstes Video“ (Phone-Tab und Tablet-Seitenleiste) | `VideoDetailFragment` (`showRelatedItems = false`, `relatedItemsLayout` → GONE, Z. 1508/1925) | **ERLEDIGT** [Code] | – | gering | Beide Layouts abgedeckt. |
| Auto-Queue (Related ans Ende der Queue) | `PlayerHelper.isAutoQueueEnabled` → `false`; Aufruf in `Player.maybeAutoQueueNextStream` | **ERLEDIGT** [Code] | – | gering | Das ist der eigentliche „nächstes Video“-Mechanismus. `RelatedItemsFragment` hat noch einen Schalter auf `auto_queue_key`, der ist aber nicht mehr erreichbar und wird ignoriert. |
| „Autoplay“ (Autostart des **geöffneten** Videos) | `PlayerHelper.getAutoplayType` → `NEVER` | **ERLEDIGT**, **Neubewertung** empfohlen | S | gering | [Code] In NewPipe bedeutet `autoplay_key` nur „Video beim Öffnen der Detailseite sofort starten“. Kein Folgevideo, das macht Auto-Queue. `NEVER` ist ein zusätzlicher Bremspunkt (gut), führt laut STATE aber zum Auswahldialog bei Links. Option: belassen (Empfehlung, kostet nur einen Tipp) oder für explizit gewählte Videos wieder `WIFI`. |
| Beschreibung, Kapitel, Metadaten | `show_description_key`, `show_meta_info_key`, Segmente | **BEHALTEN** | – | – | Kapitel helfen gezielt zu schauen. |
| Hashtags und Tags in der Beschreibung öffnen eine Suche | `util/text/HashtagLongPressClickableSpan`, `BaseDescriptionFragment:266` | **UMKONFIGURIEREN** (später): Tags nicht klickbar | S | gering | [Code] Kleiner Kaninchenbau („#xyz“ → Suchergebnisse). Niedrige Priorität. |
| Links in der Beschreibung auf andere Videos | `TextLinkifier` | **BEHALTEN** | – | – | Explizite, vom Creator gesetzte Verweise. Die Absicht entscheidet. |
| View-, Like- und Abo-Zahlen | Detail-Header, `InfoItemBuilder`/Holder | **später UMKONFIGURIEREN** (optional ausblenden) | M | mittel | [Vermutung] Social Proof erhöht Klickanreiz. Viele Holder betroffen, darum später. |
| „Im Browser öffnen“ (Detailmenü, Long-Press, Kanal, Playlist, Fehler) | `ShareUtils.openUrlInBrowser` (19 Aufrufe), `StreamDialogDefaultEntry.OPEN_IN_BROWSER:137`, `VideoDetailFragment:511`, `menu_channel.xml`, `menu_playlist.xml` | **ENTFERNEN** für YouTube-Inhalte | S | gering–mittel | [Code] Ein Tipp führt auf youtube.com mit Startseite, Related und Shorts. Das ist das größte offene Schlupfloch innerhalb der App. Minimaler Patch: Menüeinträge ausblenden; „Teilen“ bleibt. |
| „Teilen“ | `StreamDialogDefaultEntry.SHARE`, `ShareUtils` | **BEHALTEN** | – | – | Nach außen gerichtet, kein Sog. |
| Hauptplayer (Vollbild, Gesten, Geschwindigkeit, Pitch, Skip Silence) | `player/Player.java`, `player/ui/*`, `video_audio_settings.xml` | **BEHALTEN** | – | – | Werkzeuge. Hohe Geschwindigkeit kann „Durchsuchten“ fördern, ist aber primär nützlich. |
| Hintergrundwiedergabe | `PlayerType.AUDIO`, `PlayerService` | **BEHALTEN** | – | – | Kern des Escape-Ventils „Nur Audio“ (Podcasts, Musik, Vorträge). |
| Popup-Player | `PlayerType.POPUP`, `START_HERE_ON_POPUP`, Router-Auswahl | **BEHALTEN**, später ggf. abschaltbar | M | mittel | [Einschätzung] Fördert Nebenbei-Schauen. Entfernen berührt viele Stellen (Dialoge, Router, Detail-Buttons, Settings). Deshalb erst als Zen-Option, wenn Bedarf. |
| „Beim Verlassen minimieren“ (Default: Hintergrund) | `minimize_on_exit_key`, Default `minimize_on_exit_background_key` [Code `settings_keys.xml:75`] | **BEHALTEN** | – | – | Audio läuft weiter, kein visueller Sog. Mit Sitzungs-Timer kombinieren (Hintergrund-Zeit zählt mit). |
| Wiedergabe-Queue (Einreihen, Als nächstes, Start hier) | `StreamDialogDefaultEntry.ENQUEUE*`, `START_HERE_ON_*`, `PlayQueueActivity` | **BEHALTEN** | – | – | Explizite, vom Nutzer gebaute Queue = bewusst. |
| „Alle abspielen“ auf Kanal-Tab | `ChannelTabFragment` (`PlaylistControlViewHolder`), `ChannelTabPlayQueue`, `AbstractInfoPlayQueue` (lädt Seiten nach) | **UMKONFIGURIEREN** (später): Queue auf N Videos begrenzen | S–M | mittel | [Code] Lädt fortlaufend weitere Seiten, also praktisch ein Endlos-Autoplay der Kanalhistorie. Für Playlists ist „Alle abspielen“ ok (endlich, vom Menschen kuratiert). |
| Repeat/Shuffle | `Player` | **BEHALTEN** | – | – | Musik. |
| Seekbar-Vorschaubilder | `seekbar_preview_thumbnail_key` | **BEHALTEN** | – | – | Neutral. |
| Kodi | `show_play_with_kodi_key` (Default aus) | **BEHALTEN** | – | – | Neutral. |
| Externer Player | `use_external_*_player_key` | **BEHALTEN** (Hinweis) | – | – | [Einschätzung] Umgeht spätere Sitzungs-Timer, weil die Wiedergabe außerhalb läuft. Bei Tageslimit ebenfalls zählen bzw. sperren. |
| Player-Benachrichtigung, MediaSession, Hardware-Tasten | `player/notification`, `player/mediasession`, `player_notification_settings.xml` | **BEHALTEN** | – | – | Steuerung, kein Sog. |
| Android Auto / MediaBrowser (Playlists, Verlauf, Suche) | `player/mediabrowser/MediaBrowserImpl` (`ID_BOOKMARKS`, `ID_HISTORY`, `onSearch`) | **BEHALTEN** | – | – | Audio-only im Auto. [Code] Verlauf wird dort weiter angeboten, das ist unkritisch. |
| ExoPlayer-Feinoptionen | `exoplayer_settings.xml` | **BEHALTEN** | – | – | Technik. |

### 2.3 Feed und Abos

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Abo-Feed (chronologisch) | `local/feed/FeedFragment.kt`, `FeedViewModel.kt`, `FeedDAO` (`LIMIT 500`), `FeedDatabaseManager` (13 Wochen) | **UMKONFIGURIEREN** → „Feed mit Ende“ | M | mittel | [Code] Zeigt bis zu 500 Einträge der letzten 13 Wochen, also faktisch endlos. Zen: Standardansicht „neu seit letztem Besuch“, am Ende eine „Du bist durch“-Karte, ältere nur auf Tippen. |
| Feed-Filter gesehen/teilweise gesehen/kommend | `FeedViewModel` (Defaults `true`) | **UMKONFIGURIEREN**: Default „gesehene ausblenden“ | S | gering | Liste schrumpft beim Abarbeiten, das macht das Ende erreichbar. Nur Default ändern (Zeile 158). |
| Feed-Gruppen (Abo-Kategorien) | `local/subscription/dialog/FeedGroupDialog`, `Tab.FeedGroupTab` | **BEHALTEN** und ausbauen | – | – | Fertige Basis für „Lernen/Unterhaltung pro Kanal“ (Abschnitt 4). |
| Feed-Aktualisierungsschwelle | `feed_update_threshold_key` | **BEHALTEN** | – | – | Ein höherer Wert (z. B. 6 h) ist zen-freundlich, weil es weniger Neues pro Öffnung gibt. Optional Default anheben. |
| Schnellmodus (RSS) | `feed_use_dedicated_fetch_method_key` (Default aus) | **BEHALTEN**, Hinweis | – | – | [Vermutung, gestützt durch Privacy-Guides-Hinweis] YouTube-RSS enthält Shorts. Bei aktiviertem Schnellmodus greift der Shorts-Tab-Filter nicht. Am Gerät prüfen; ggf. Schalter verstecken. |
| Feed-Abruf: Kanal-Tabs (Videos, Shorts, Live …) | `feed_fetch_channel_tabs_key`, `ChannelTabHelper.fetchFeedChannelTab` | Shorts **ERLEDIGT**; Live **UMKONFIGURIEREN** | S | gering | [Code] Shorts per Code gesperrt, steht aber noch in der Auswahlliste (`settings_keys.xml:410`). Livestreams sind Default-an (Default = ganze Liste). Live = FOMO und endlose Dauer, deshalb wie Shorts sperren oder Default aus. |
| Benachrichtigungen über neue Videos | `local/feed/notifications/NotificationWorker`, `NotificationHelper:169` (Default `false`), `notifications_settings.xml` | **ENTFERNEN/VERSTECKEN** (Option ausblenden) | S | gering | [Code] Default ist schon aus. Push-Reize von außen sind aber das Gegenteil von „bewusst öffnen“. Schalter ausblenden, Glocke im Kanalmenü (`ChannelFragment:156`, `menu_item_notify`) ebenso. Optional später: **eine** tägliche Zusammenfassung zu fester Uhrzeit. |
| Abos importieren/exportieren (YouTube-Takeout, NewPipe) | `local/subscription/*`, `backup_restore_settings.xml` | **BEHALTEN** | – | – | Migration von NewPipe nötig. |
| Abo-Liste | `SubscriptionFragment` | **BEHALTEN** | – | – | Basis für „Abo-Hygiene“ (Abschnitt 4). |

### 2.4 Kanalseiten

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Tab Shorts | `ChannelTabHelper.showChannelTab` | **ERLEDIGT** [Code] | – | gering | Steht noch in der Einstellungsliste `show_channel_tabs_value_list` (wirkungslos, verwirrend). |
| Tab Livestreams | `show_channel_tabs_livestreams` | **UMKONFIGURIEREN**: wie Shorts sperren | S | gering | Gleiches Muster, eine Zeile. |
| Tabs Videos, Playlists, Alben, Tracks, Podcasts, Kurse, Info | `show_channel_tabs_key` | **BEHALTEN** | – | – | Kuratiertes Material des Kanals. |
| Tab „Kanäle“ (empfohlene Kanäle) | `show_channel_tabs_channels` | **UMKONFIGURIEREN** (optional sperren) | S | gering | [Einschätzung] Leichter Empfehlungscharakter. Niedrige Priorität. |
| Tab „Likes“ | `show_channel_tabs_likes` | **BEHALTEN** | – | – | Selten (nur bestimmte Dienste). |
| Abo-Button, Glocke, RSS-Link | `ChannelFragment`, `menu_channel.xml` | Abo **BEHALTEN**, Glocke **VERSTECKEN** | S | gering | Siehe Benachrichtigungen. |

### 2.5 Suche

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Suche | `fragments/list/search/SearchFragment` | **BEHALTEN** | – | – | Zentrales Werkzeug. |
| Shorts in Suchergebnissen | `SearchFragment.handleResult/handleNextItems` | **in Arbeit** (uncommittet, `zenWithoutShorts`) | S | gering | [Code] Filtert `StreamInfoItem.isShortFormContent()`. Prüfen, ob das Flag in YouTube-Suchergebnissen zuverlässig gesetzt ist [Vermutung: meist ja, wenn das Item als Short-Lockup kommt]. Leere erste Seite nach dem Filtern sollte nicht als „keine Ergebnisse“ erscheinen. |
| Remote-Suchvorschläge (YouTube-Autocomplete) | `show_search_suggestions_key`, Default lokal **und** remote [Code `content_settings.xml:83`], `SearchFragment:793` | **UMKONFIGURIEREN**: remote Default aus | S | gering | Autocomplete spiegelt populäre bzw. Trend-Suchen, ein kleiner Empfehlungskanal. Lokale Suchhistorie reicht für gezielte Suche. |
| Suchverlauf (lokal) | `enable_search_history_key` | **BEHALTEN** | – | – | Hilft, Absichten wiederzufinden. |
| „Meintest du“-Korrektur | `SearchFragment.handleSearchSuggestion` | **BEHALTEN** | – | – | Neutral. |
| Inhaltsfilter (Videos, Kanäle, Playlists, Musik) | `SearchFragment` Menü | **BEHALTEN** | – | – | Präzisiert die Suche. |

### 2.6 Bibliothek, Playlists, Verlauf, Statistik

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Lokale Playlists, gemerkte Remote-Playlists | `local/bookmark/BookmarkFragment`, `local/playlist/*` | **BEHALTEN** | – | – | Kern des bewussten Schauens. Basis für „Später ansehen mit Budget“. |
| „Gesehene entfernen“ in Playlists | `LocalPlaylistFragment` | **BEHALTEN** | – | – | Abarbeiten statt Sammeln. |
| Wiedergabe-Verlauf + Statistik (zuletzt, meistgesehen) | `local/history/StatisticsPlaylistFragment`, `HistoryRecordManager`, `history_settings.xml` | **VERSTECKT** (UI), Daten **BEHALTEN** | – | – | Wird für Fortsetzen und gesehen-Markierungen gebraucht. „Meistgesehen“ nicht als Liste zeigen (Sog in Wiederholung), sondern als Datenquelle für die Wochenbilanz. |
| Fortsetzen an letzter Position, Wiedergabestatus in Listen | `enable_playback_resume_key`, `enable_playback_state_lists_key` | **BEHALTEN** | – | – | Hilft beim Abarbeiten. |
| Verlauf/Cache/Cookies löschen | `history_settings.xml` | **BEHALTEN** | – | – | Datenschutz. Hinweis: Zen-Statistik nicht im NewPipe-Verlauf speichern, sonst löscht man die Bilanz mit. |

### 2.7 Downloads

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Download (Video, Audio, Untertitel) | `download/DownloadDialog`, `us.shandian.giga.*`, `download_settings.xml` | **BEHALTEN** | – | – | Bewusstes Offline-Schauen, kein Feed. |
| Download-Liste | `DownloadActivity` | **UMKONFIGURIEREN**: wieder erreichbar machen | S | gering | Siehe 2.1. |

### 2.8 Erscheinungsbild

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Theme hell/dunkel/schwarz, Nacht-Theme | `appearance_settings.xml` `theme_key`, `night_theme_key` | **BEHALTEN**, Graustufen ergänzen | S–M | gering | Graustufen siehe 4. |
| Listenansicht (Auto/Liste/Raster/Karte) | `list_view_mode_key`, Default `auto` [Code `settings_keys.xml:1424`] | **UMKONFIGURIEREN**: Default „Liste“ | S | gering | Kleine Thumbnails = weniger Klickanreiz [Einschätzung]. Nur Default-Wert ändern. |
| Bildqualität inkl. „Keine Bilder“ | `image_quality_key` (Werte none/low/medium/high, Default medium) | **BEHALTEN**, als Zen-Option bewerben | S | gering | [Code] „Thumbnails aus“ gibt es schon. Zen-Variante „gedämpft“ (Graustufen, Unschärfe, Klick zum Zeigen) wäre neu. |
| Tablet-Modus, „Halten zum Einreihen“-Hinweis, Untertitel-Einstellungen | `appearance_settings.xml` | **BEHALTEN** | – | – | Neutral. |

### 2.9 Inhalt und Altersfreigabe

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| App-/Inhaltssprache, Land | `content_settings.xml` | **BEHALTEN** | – | – | Land beeinflusste v. a. Trends, die jetzt weg sind. |
| Altersbeschränkte Inhalte zeigen (Default aus) | `show_age_restricted_content` | **BEHALTEN** | – | – | Neutral. |
| YouTube-Restricted-Mode (Default aus) | `youtube_restricted_mode_enabled` | **BEHALTEN** | – | – | Optional als „ruhiger Modus“. Wirkung auf Sog unklar [Vermutung]. |
| PeerTube-Instanzen | `PeertubeInstanceListFragment` | **BEHALTEN** | – | – | Neutral. |

### 2.10 System, Einstiege, Wartung

| Feature | Fundstelle | Entscheidung | Aufwand | Merge-Risiko | Begründung |
|---|---|---|---|---|---|
| Teilen-/Link-Einstieg (Auswahl Detail/Video/Hintergrund/Popup/Download/Playlist) | `RouterActivity` (Choices Z. 302–330), Intent-Filter für youtube.com, youtu.be, Invidious/Piped, `vnd.youtube`, `ACTION_SEND`, `MEDIA_PLAY_FROM_SEARCH` | **UMKONFIGURIEREN**: Absichtsabfrage auch hier | M | gering | [Code] Hintergrund und Popup starten ohne `MainActivity`, also ohne Abfrage. Der Detailpfad zeigt sie nur, wenn der Prozess frisch ist. |
| Bevorzugte Öffnen-Aktion (Default „immer fragen“) | `preferred_open_action_key` | **BEHALTEN** | – | – | Die Rückfrage ist schon eine kleine Hürde. |
| Update-Prüfung (newpipe.net) | `NewVersionWorker`, `UpdateSettingsFragment`, `MainSettingsFragment:26` | **faktisch ERLEDIGT** [Code] | – | – | `ReleaseVersionUtil.isReleaseApk` prüft das NewPipe-Signaturzertifikat. Ein ZenPipe-APK mit eigenem Schlüssel blendet den Update-Screen automatisch aus und setzt `update_app_key=false`. Updates kommen über das eigene F-Droid-Repo. Später optional eigener Checker (GitHub-Releases), Aufwand M. |
| Crash-Reporter (ACRA + ErrorActivity) | `error/ErrorActivity.kt:277–280` (`crashreport@newpipe.schabi.org`, `github.com/TeamNewPipe/NewPipe/issues`) | **UMKONFIGURIEREN**: Ziel ZenPipe-Repo | S | gering | [Code] Fork-Bugs dürfen nicht bei NewPipe landen (Fairness gegenüber Upstream). Zwei Konstanten. |
| `applicationId` / App-Name | `app/build.gradle.kts:42` (`NEWPIPE_APPLICATION_ID_OLD`), `app_name` | **UMKONFIGURIEREN** | S | gering–mittel | [Code] Release-Build kollidiert mit installiertem NewPipe. Ist in STATE als offen notiert. Debug nutzt `.debug`-Suffix. |
| Backup/Restore (Daten-ZIP inkl. Einstellungen), Einstellungen zurücksetzen | `backup_restore_settings.xml`, `settings/export/*` | **BEHALTEN**, Zen-Werte ausnehmen | S | gering | [Einschätzung] Wenn Zen-Einstellungen in den Default-Prefs liegen, sind Import und Reset ein Time-Lock-Bypass. Deshalb eigenes Prefs-File, das Import/Reset nicht anfassen. |
| Android-Auto-Backup | `AndroidManifest.xml:33` `allowBackup="true"` | **BEHALTEN**, Zen-Zustand ausschließen | S | gering | Siehe Research 3.1 „Daten löschen teuer machen“. |
| Panic-Responder | `PanicResponderActivity` | **BEHALTEN** | – | – | Neutral. |
| Debug-Einstellungen | `debug_settings.xml` (nur DEBUG) | **BEHALTEN** | – | – | Nur im Debug-Build. |
| Info/Lizenzen | `about/*` | **BEHALTEN**, Fork-Hinweis ergänzen | S | gering | GPL: Herkunft NewPipe nennen. |
| Widgets / App-Shortcuts | – | **nicht vorhanden** [Code: keine `appwidget`/`shortcuts`-Ressourcen im Manifest bzw. `res/xml`] | – | – | Gut so. Bewusst keine Shortcuts direkt in Feed/Suche (umgehen die Abfrage). |
| SponsorBlock/DeArrow/Return-YouTube-Dislike | – | **nicht vorhanden** (gibt es in Tubular/PipePipe) | – | – | Siehe 4: SponsorBlock ist zen-neutral, DeArrow (Clickbait-Titel/-Thumbnails entschärfen) wäre zen-relevant, beides später. |

---

## 3. Lücken im bereits Umgesetzten

Alle Punkte **[Code]**, sofern nicht anders markiert.

| # | Bereich | Lücke | Fundstelle | Fix-Vorschlag | Aufwand | Merge-Risiko |
|---|---|---|---|---|---|---|
| L1 | Absichtsabfrage | Nur einmal pro **Prozess** (`static shownInThisProcess`). „Doch nicht“ ruft `finishAffinity()`, der Prozess lebt weiter, und der nächste Start aus dem Launcher zeigt **keine** Abfrage. | `util/ZenIntentPrompt.java` | Zeitstempel statt Prozess-Flag: zeigen, wenn letzte Antwort > 15 min her ist **oder** nach „Doch nicht“. Prüfung in `onResume`. | S | gering |
| L2 | Absichtsabfrage | Geteilte Links und `vnd.youtube`-Intents öffnen Hintergrund/Popup ohne Abfrage. | `RouterActivity` | Gleiche Abfrage (Dialog) vor Ausführung der Choice. | S–M | gering |
| L3 | Absichtsabfrage | Keine Eingabe, nichts gespeichert, kein Weiterleiten in Suche/Abo/Später-Liste. | `ZenIntentPrompt` | Implementation-Intention-Dialog (Abschnitt 4, Z1). | M | gering |
| L4 | Shorts | Suche läuft parallel (uncommittet). Einstellungslisten zeigen weiter „Shorts“ (Kanal-Tabs, Feed-Abruf), Häkchen wirkungslos. | `settings_keys.xml:306, 410` | Einträge aus beiden Arrays entfernen (Ressourcen-Diff) **oder** in den Settings-Fragments per Code filtern (konfliktärmer). | S | gering |
| L5 | Shorts | Schnellmodus/RSS enthält vermutlich Shorts. Der Tab-Filter greift dort nicht. | `FeedLoadManager:67/170` | Am Gerät prüfen. Falls ja: Schnellmodus-Schalter verstecken bzw. erzwingen aus. `StreamEntity` hat kein Shorts-Feld, ein DB-Filter wäre L. | S | gering |
| L6 | Shorts | Shorts-URLs (`youtube.com/shorts/…`) über Teilen/Links öffnen den normalen Player. Das ist ok (einzelnes Video, kein Swipe-Feed). | `RouterActivity` | Behalten, kein Fix nötig [Einschätzung]. | – | – |
| L7 | Live | Livestreams im Feed-Abruf und als Kanal-Tab aktiv. Kein Live-Kiosk mehr, aber Live-Kacheln erscheinen im Feed (inkl. „kommend“). | `ChannelTabHelper`, `FeedViewModel` (`feed_show_future_items` Default `true`) | `LIVESTREAMS` analog zu Shorts sperren (2 Zeilen), „kommende“ Default aus. | S | gering |
| L8 | Vorschläge | Remote-Suchvorschläge standardmäßig an. | `content_settings.xml:83` | Default auf nur lokal (Ressource) oder in `SearchFragment:793` remote ignorieren. | S | gering |
| L9 | Browser | „Im Browser öffnen“ an 19 Stellen. | `ShareUtils.openUrlInBrowser` u. a. | Menüeinträge ausblenden. Nicht `ShareUtils` selbst ändern, das nutzen auch About/Lizenzen. | S | gering–mittel |
| L10 | Player/Queue | „Alle abspielen“ auf Kanal-Tabs lädt endlos nach. | `ChannelTabPlayQueue`/`AbstractInfoPlayQueue.fetch` | Maximal z. B. 10 Videos oder Button auf Kanal-Tabs ausblenden. | S | mittel |
| L11 | Kommentare/Related im Popup oder Overlay | **Keine Lücke gefunden.** Popup/Vollbild-UI (`player/ui/*`) zeigt nur Queue und Kapitel, keine Related/Kommentare. | `player/ui/MainPlayerUi`, `PopupPlayerUi` | – | – | – |
| L12 | Startseite/Tabs | Mit `ChooseTabsFragment` sind auch Feed-Gruppen-/Playlist-Tabs nicht mehr einstellbar. | `content_settings.xml` | Siehe 2.1. | S–M | gering–mittel |
| L13 | Downloads | Download-Liste nur noch über Benachrichtigung bzw. Snackbar erreichbar. | `MainActivity` | Drawer-Eintrag zurück. | S | gering |
| L14 | Benachrichtigungen | Neue-Video-Benachrichtigungen + Kanal-Glocke sind weiter einschaltbar (Default aus). | `notifications_settings.xml`, `ChannelFragment:156` | Ausblenden. | S | gering |
| L15 | Verlauf-basierte Vorschläge | **Keine Lücke.** NewPipe hat keine verlaufsbasierten Empfehlungen. Lokale Suchvorschläge stammen aus der eigenen Suchhistorie, das ist ok. | – | – | – | – |
| L16 | Settings-Reste | `RelatedItemsFragment` liest/schreibt noch `auto_queue_key` (unerreichbar). Migrations-Code setzt `autoplay_key` evtl. (`SettingMigrations`), ist aber wirkungslos, weil `getAutoplayType` hart verdrahtet ist. | `RelatedItemsFragment:83`, `settings/migration` | Lassen (kleiner Diff schlägt Sauberkeit). | – | – |
| L17 | Verifikation | Laut STATE wurden die Settings-Screens nach dem Entfernen nicht durchgeklickt und `testDebugUnitTest` nicht ausgeführt. [Code] Im Paket `settings/` referenziert kein Fragment die entfernten Keys (`show_comments_key`, `show_next_video_key`, `autoplay_key`, `auto_queue_key`, `main_page_content_key`), ein `requirePreference`-Crash ist also unwahrscheinlich. Ein Gerätetest steht trotzdem aus (u. a. Einstellungssuche). | `settings/*` | Gerätetest + Unit-Tests. | S | – |
| L18 | Fork-Hygiene | `applicationId` = NewPipe, Crash-Reports an NewPipe. | `build.gradle.kts:42`, `ErrorActivity.kt:277` | Siehe 2.10. | S | gering |

---

## 4. Neue Zen-Features (über NewPipe hinaus), priorisiert

Grundlage: `research-achtsamer-konsum.md` (dort Evidenz und Quellen). Neue Ideen sind als [Einschätzung] markiert.
**Umgehungsrisiko** = wie leicht man das Feature im Impuls-Moment aushebelt (bei Entwickler-Nutzer ist ein Rebuild immer möglich).

### Architektur-Leitlinie (Voraussetzung für alles Weitere)

- Neues Paket `org.schabi.newpipe.zen` (eigene Dateien = keine Merge-Konflikte). Upstream-Klassen bekommen nur Hooks wie `ZenGate.onAppForeground(activity)`, `ZenSession.onPlaybackTick(...)`, `ZenFeed.decorate(...)`.
- Eigenes `SharedPreferences`-File `zen` plus eigene kleine Room-Tabelle oder JSON-Datei für Sitzungen, Absichten und Protokoll. **Nicht** in den NewPipe-Daten-Export, nicht in Auto-Backup, nicht von „Einstellungen zurücksetzen“ betroffen.
- Eigener Einstellungsbereich „Zen“ als neuer Eintrag in `main_settings.xml` (1 Hunk) mit eigenem Fragment.
- Zeitbasis: `elapsedRealtime` + letzte gesehene Wanduhr + HTTP-`Date` von YouTube-Antworten (`DownloaderImpl`) gegen Uhr-Manipulation. Tagesgrenze 4:00.

### MVP-Erweiterung (als Nächstes)

| # | Feature | Beschreibung | Evidenz | Aufwand | Umgehungsrisiko | Merge-Risiko |
|---|---|---|---|---|---|---|
| Z1 | **Absicht v2 (Implementation Intention)** | Dialog: „Ich schaue [Kanal/Thema/Video] für [10/20/30/45 min]“. Chips aus Abo-Gruppen und Später-Liste, Freitext optional. Danach direkt zur passenden Stelle (Suche mit Begriff, Kanal, Playlist). Wartezeit bleibt 5 s, eskaliert bei mehreren Öffnungen pro Stunde (wie ScreenZen). Absicht + gewählte Dauer werden gespeichert und speisen Z3/Z4/Z10. Auch in `RouterActivity` und nach Rückkehr aus dem Hintergrund (> 15 min). | stark (One Sec PNAS, Gollwitzer, LocknType) | M | gering–mittel | gering |
| Z2 | **Feed mit Ende** | Default: nur „neu seit letztem abgeschlossenen Besuch“, gesehene ausgeblendet. Am Listenende eine Karte „Du bist durch – 7 neue Videos von 5 Kanälen“ + Buttons „Später-Liste“, „App schließen“. Ältere Videos nur über „Ältere zeigen“. Kein Pull-to-Refresh-Nachladen innerhalb von X h (nutzt `feed_update_threshold`). | mittel (Rixen 2023, Stopp-Signale) | M | gering | mittel (`FeedFragment.kt`) |
| Z3 | **Sitzungs-Timer mit sanftem Ende** | Startet mit der Dauer aus Z1. Bei Ablauf: laufendes Video zu Ende, dann **keine** neue Wiedergabe aus Feed/Suche, sondern ein ruhiger Pause-Screen („Deine 20 Minuten sind um. Hast du gefunden, was du wolltest? Ja / Teilweise / Nein“). „5 Minuten mehr“ ist möglich, wird protokolliert. Zählt alle Player inkl. Hintergrund, mit Option „Audio zählt halb“. | mittel (MyTime) | S–M | mittel (ohne Time-Lock) | gering (Hook in `Player`) |
| Z4 | **Reflexion am Ende** | Die Ja/Teilweise/Nein-Frage aus Z3, auch beim Verlassen der App nach > 5 min. Speichert, ob die Absicht erfüllt wurde. Keine Bewertung, nur Spiegel. | schwach–mittel (Selbstreflexion; „Agency“ bei Lukoff) [Einschätzung] | S | – | gering |
| Z5 | **Ruhige Darstellung** | Zen-Schalter „Ruhig“: Liste statt Karten, Thumbnails gedämpft (Graustufen + 50 % Deckkraft, Tippen zeigt Farbe) oder aus, View-Zahlen ausgeblendet. Optional **Graustufen für die ganze App** per `ColorMatrix` auf der Decor-View, z. B. automatisch ab 21 Uhr oder nach dem Limit. | gut für systemweite Graustufen (Zimmermann & Sobolev 2023), in-app unbelegt | S–M | gering | gering (Decor-View) bis mittel (Holder) |

### Bald (nach Time-Lock)

| # | Feature | Beschreibung | Evidenz | Aufwand | Umgehungsrisiko | Merge-Risiko |
|---|---|---|---|---|---|---|
| Z6 | **Time-Lock für Zen-Einstellungen** | Strenger sofort, Lockern wird zur „ausstehenden Änderung“ mit Wirkung ab nächstem Tag 4:00 (abbrechbar). Optional Sperrzeitraum 7/30 Tage. Zen-Einstellungen öffnen erst nach 30-s-Countdown + Begründungsfeld. | stark (Allcott 2022, Commitment Devices) | M | gering | gering |
| Z7 | **Tageslimit mit Escape-Ventil** | Tagesbudget (z. B. 45 min Video). Danach: nur Audio (Hintergrundplayer), nur Später-Liste oder ein „Notfall-Video“ pro Tag mit 60 s Wartezeit. Feed und Suche gesperrt. Tagesgrenze 4:00. | stark (Allcott) | M | gering mit Z6 | gering–mittel |
| Z8 | **Nachtruhe** | Zeitfenster (z. B. 22:30–6:00): nur Audio, Graustufen, Feed aus, Absichtsabfrage mit längerer Wartezeit. Kein Video-Start. | [Einschätzung]; Schlaf-Bezug plausibel | S | gering mit Z6 | gering |
| Z9 | **Umgehungsschutz und Protokoll** | Erkennt Uhr-Rücksprünge, Neuinstallation (`firstInstallTime`), Daten-Reset (fehlender Zen-Marker bei vorhandener DB), „5 min mehr“, Lockerungen. Alles landet in der Wochenbilanz. Keine Strafe, Sichtbarkeit. | Research 1.5/3.1 | S–M | – | gering |
| Z10 | **Wochen-/Tagesbilanz ohne Schuldgefühl** | Ein Screen: Zeit pro Tag (Video/Audio getrennt), Absichten erfüllt ja/teilweise/nein, häufigste Kanäle, Lern- vs. Unterhaltungsanteil (Z12), Umgehungen. Neutrale Sprache („Diese Woche: 3 h 10 min, davon 1 h Audio“), kein Ranking, keine roten Zahlen. Optional sonntags als einmalige Benachrichtigung. | schwach (Self-Tracking allein wirkt kaum), sinnvoll mit Z6/Z7 | M | – | gering |
| Z11 | **„Später ansehen“ mit Budget** | Fest angelegte lokale Playlist „Später“, schnell per Long-Press befüllbar. Obergrenze z. B. 15 Einträge (der älteste muss raus oder wird automatisch entfernt nach 30 Tagen). Wird in Z1 als erste Option angeboten. Macht aus Impuls („will ich sehen“) eine aufgeschobene, bewusste Entscheidung. | [Einschätzung]; passt zu „Ausweg statt Verbot“ | S–M | – | gering (`StreamDialogDefaultEntry` + neue Datei) |
| Z12 | **Kategorien pro Kanal (Lernen/Musik/Unterhaltung)** | Auf Basis der vorhandenen **Feed-Gruppen**: drei vorgeschlagene Gruppen beim Einrichten. Unterhaltung zählt voll aufs Limit, Lernen nur halb, Musik/Podcast im Audio-Modus gar nicht (konfigurierbar). Feed-Gruppen-Tabs wieder erlauben (L12). | [Einschätzung] | M | mittel (Selbsteinstufung) | gering |
| Z13 | **Abo-Hygiene** | Monatlicher Vorschlag: „Diese 8 Kanäle hast du seit 90 Tagen nicht angesehen – abbestellen oder in ‚Ruhend‘ verschieben?“ Daten aus Verlauf + Abos. Ruhend = nicht im Feed, aber im Kanal-Verzeichnis. Weniger Abos = kürzerer Feed. | [Einschätzung] | M | – | gering |

### Später / optional

| # | Feature | Beschreibung | Aufwand | Umgehungsrisiko | Merge-Risiko |
|---|---|---|---|---|---|
| Z14 | Partner-PIN | Sofortfreigabe beim Lockern durch eine Person mit PIN (sonst Time-Lock). | S–M | gering | gering |
| Z15 | Öffnungslimit pro Tag | „Höchstens 3 Sitzungen täglich“ (ScreenZen-Muster), ergänzt das Zeitlimit. | S | gering mit Z6 | gering |
| Z16 | Keyword-/Kanal-Filter | Titel mit Stichwörtern ausblenden (z. B. „REACTION“, „gone wrong“), Kanäle stummschalten (PipePipe hat das). Gegen Clickbait im Feed und in der Suche. | M | gering | mittel |
| Z17 | DeArrow-Integration | Crowdsourced neutrale Titel/Thumbnails. Externe API, Datenschutz prüfen. | M–L | – | mittel |
| Z18 | SponsorBlock | Spart Zeit, ist aber zen-neutral. Nur falls ohnehin gewünscht (Tubular/PipePipe-Code als Vorlage). | L | – | hoch |
| Z19 | Streak „Absicht erfüllt“ | Positive Serie für erfüllte Absichten oder Tage unter dem Limit. **Vorsicht:** Streaks erzeugen selbst Druck und Sog [Einschätzung]; wenn überhaupt, dann ohne Verlust-Mechanik („5 von 7 Tagen“ statt „Serie gerissen“). | S | – | gering |
| Z20 | Audio-First-Modus | Standardaktion beim Tippen auf ein Video = Hintergrund-Audio, Video nur bewusst per Button. Ein Profil für Podcasts/Vorträge. | S–M | gering | mittel (`NavigationHelper`/Router) |
| Z21 | Wartezeit vor Wiedergabe | 3–5 s „Atemzug“-Overlay vor jedem Videostart aus dem Feed (nicht aus Später-Liste/Playlist). Kann nerven, darum optional und nach Uhrzeit. | S | mittel | gering |
| Z22 | Usage-Stats der YouTube-App/des Browsers in der Bilanz | Nur Anzeige, Spezialberechtigung. Research rät eher ab. | M | – | gering |

### Was vergleichbare Tools zeigen (Web-Recherche, kurz)

- **Unhook / Unhook NG** (Browser): Startseiten-Feed, Seitenleiste, Shorts, Endscreen, Kommentare, Explore/Trends ausblenden, Autoplay aus. Deckt sich mit dem ZenPipe-MVP. Einziger Mehrwert dort: Endscreen-Karten, die es in NewPipe ohnehin nicht gibt. [Quellen: Edge-/Firefox-Listings]
- **PipePipe** (NewPipe-Fork): Filter nach Keywords und Kanälen, Shorts- und Paid-Video-Blocker, SponsorBlock → Vorlage für Z16. **Tubular**: SponsorBlock + Return-YouTube-Dislike. Kein Fork mit Limits oder Absichtsabfrage gefunden, die Nische ist frei.
- **ScreenZen**: Wartezeit, die **mit jeder Öffnung wächst**, Öffnungslimit pro Tag, Sperre der eigenen Einstellungen → Vorlage für Z1 (Eskalation), Z15, Z6.
- **Opal Deep Focus**: Sitzung nicht vorzeitig beendbar, ein Notfallschlüssel pro Woche. Nutzer berichten Umgehungen über System-Einstellungen → bestätigt: In-App-Mechanismen schützen vor dem Impuls, nicht vor dem Entschlossenen. Ein „Notfallschlüssel pro Woche“ ist eine gute, reaktanzarme Escape-Variante für Z7.
- **Thumbnails/Clickbait**: Studie mit 16.215 YouTube-Covern: emotional starke Thumbnails bringen mehr Views. Stützt Z5/Z17 indirekt (Klickanreiz, nicht Sehzeit).

---

## 5. Vorgeschlagene Roadmap

**Phase 0 – Hygiene und Lücken schließen (≈ 2–3 Tage, alles S, geringes Merge-Risiko)**
1. `applicationId`, App-Name, Crash-Report-Ziel (L18)
2. Settings-Screens verifizieren, Unit-Tests laufen lassen (L17)
3. Shorts: Suche committen, Listen bereinigen, RSS-Schnellmodus prüfen (L4, L5)
4. Live sperren, „kommende“ Default aus, Remote-Vorschläge Default aus (L7, L8)
5. „Im Browser öffnen“ ausblenden, Neue-Video-Benachrichtigungen und Glocke verstecken (L9, L14)
6. Downloads-Eintrag zurück, „Inhalt der Hauptseite“ gefiltert zurück (L12, L13)
7. Feed-Default „gesehene ausblenden“, Listenansicht Default „Liste“

**Phase 1 – Absicht und Stopp-Signale (≈ 1–1,5 Wochen)**
1. Zen-Grundgerüst: Paket `zen`, Prefs-File, Zen-Einstellungsbereich, Zeitbasis
2. Absicht v2 inkl. Router und Rückkehr aus dem Hintergrund (Z1, L1–L3)
3. Feed mit Ende (Z2)
4. Sitzungs-Timer + Reflexion (Z3, Z4)
5. „Später“-Liste mit Budget (Z11), weil Z1 und Z2 dorthin verweisen

**Phase 2 – Verbindliche Grenzen (≈ 1–1,5 Wochen)**
1. Time-Lock (Z6) **vor** dem Limit
2. Tageslimit mit Escape-Ventil + Notfallschlüssel (Z7)
3. Nachtruhe (Z8), ruhige Darstellung/Graustufen (Z5)
4. Umgehungsprotokoll (Z9)

**Phase 3 – Spiegel und Pflege (≈ 1 Woche)**
1. Wochenbilanz (Z10)
2. Kategorien pro Kanal (Z12), Abo-Hygiene (Z13)

**Phase 4 – Optional nach 4–6 Wochen Eigennutzung**
Partner-PIN, Öffnungslimit, Keyword-Filter, Audio-First, ggf. DeArrow. Entscheidung anhand der eigenen Wochenbilanzen: Wo geht die Zeit trotzdem hin?

Begründung der Reihenfolge: Phase 0 schließt Seitentüren, die alle späteren Features entwerten würden (Browser-Link, Live). Phase 1 setzt die best belegten, reaktanzarmen Hebel um (Absicht, Stopp-Signal). Limits kommen erst nach dem Time-Lock, weil ein Limit ohne Time-Lock im heißen Moment hochgesetzt wird (Research 4, „7 vor 6“).

---

## 6. Quellen

**Code (Repo, Branch `zen`)**
- `app/src/main/java/org/schabi/newpipe/MainActivity.java`, `RouterActivity.java`, `NewVersionWorker.kt`, `util/ZenIntentPrompt.java`, `util/ChannelTabHelper.java`, `util/text/HashtagLongPressClickableSpan.java`
- `fragments/detail/VideoDetailFragment.java`, `fragments/list/search/SearchFragment.java`, `fragments/list/channel/ChannelFragment.java`, `ChannelTabFragment.java`, `fragments/list/videos/RelatedItemsFragment.java`
- `player/Player.java`, `player/helper/PlayerHelper.java`, `player/playqueue/AbstractInfoPlayQueue.java`, `player/mediabrowser/*`
- `local/feed/FeedFragment.kt`, `FeedViewModel.kt`, `FeedDatabaseManager.kt`, `database/feed/dao/FeedDAO.kt`, `local/feed/notifications/*`, `local/history/StatisticsPlaylistFragment.java`
- `info_list/dialog/StreamDialogDefaultEntry.java`, `InfoItemDialog.java`, `error/ErrorActivity.kt`, `settings/MainSettingsFragment.java`, `settings/tabs/TabsManager.java`, `Tab.java`
- `res/xml/*_settings.xml`, `res/values/settings_keys.xml`, `AndroidManifest.xml`, `app/build.gradle.kts`

**Projekt-Dokumente**
- `zen-docs/ZenPipe-Konzept_v0.1.md`, `zen-docs/research-achtsamer-konsum.md` (dort alle Studienquellen: Lukoff 2021, Grüning et al. 2023/PNAS, Allcott et al. 2022, Zimmermann & Sobolev 2023, Rixen et al. 2023, Kim et al. 2019, Monge Roffarello & De Russis 2023 u. a.), `.planning/STATE.md`

**Web (Recherche 12.10.2026)**
- Unhook: https://microsoftedge.microsoft.com/addons/detail/hebpjnnclppdnfghdnmhgdljmjpfhggk · Unhook NG: https://addons.mozilla.org/firefox/addon/unhook-ng/
- PipePipe: https://korben.info/en/pipepipe-newpipe-fork-skips-ads.html · https://codeberg.org/NullPointerException/PipePipe
- LibreTube 31.0: https://www.apkmirror.com/apk/libretube/libretube-github-version/libretube-github-version-31-0-release/
- ScreenZen: https://apps.apple.com/us/app/-/id1541027222 · https://wordpress.aber.ac.uk/digital-capabilities/2024/02/29/take-control-of-your-phone-with-screenzen-before-it-controls-you-%f0%9f%93%b4/
- Opal Deep Focus: https://opal.so/help/what-is-timer · https://www.opal.so/help/how-can-i-make-opal-foolproof · https://community.opal.so/t/app-uninstall-protection-no-longer-works/1549
- NewPipe-Schnellmodus (0.19.0): https://newpipe.net/blog/release/pinned/newpipe-0.19.0-released/ · Shorts im Feed/Suche: https://git.tdem.in/TeamNewPipe/NewPipe/discussions/10681 · YouTube-RSS enthält Shorts: https://discuss.privacyguides.net/t/how-to-create-a-rss-feed-for-any-youtube-channel/35169
- Thumbnail-Sentiment und Views: https://scholars.ln.edu.hk/en/publications/clicks-for-money-predicting-video-views-through-a-sentiment-analy/ · DeArrow: https://www.popsci.com/?p=558369

**Nicht verifiziert / offen**
- Ob die Settings-Screens nach dem Entfernen der Schalter am Gerät sauber öffnen (L17; laut Code-Grep keine Referenzen mehr).
- Ob Shorts im RSS-Schnellmodus wirklich im Feed landen (L5).
- Ob `isShortFormContent` in YouTube-Suchergebnissen zuverlässig gesetzt ist (Suche-Patch).
- Ob Kiosk-Links (z. B. `youtube.com/feed/trending`) über `RouterActivity` geöffnet werden können [Vermutung: nein, der Router behandelt nur Stream/Kanal/Playlist].
