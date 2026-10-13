# ZenPipe: Designkonzept „ruhiges Hell/Dunkel“

Stand: 2026-10-13. Status: **Spec** (nichts umgesetzt). Vorschau mit drei Varianten:
`design-preview.html` (lokal erzeugt, eigenständig; Generator mit WCAG-Prüfung siehe Anhang).

## Empfehlung (zuerst)

**Variante A „Salbei“**: das gedämpfte Schiefergrün des Icons (`#2E4A45`) wird zur Leitfarbe und
ersetzt NewPipe-Rot. Hell = warmes Papier mit tiefem Salbei-Balken, Dunkel = fast schwarzes
Grüngrau, dessen Balken kaum vom Hintergrund absticht, Schwarz = AMOLED mit fast schwarzem Balken.
Grund: größter Ruhegewinn bei kleinstem Patch. Die Toolbar bleibt dunkel genug für die in
Upstream-Layouts fest verdrahteten weißen Tab-/Icon-Farben, deshalb reichen **Farbwerte** statt
Layout-Umbauten (rund 35 geänderte Zeilen in 8 Upstream-Dateien plus 1 neue Datei).
Standard bleibt „System folgen“ (ist schon Upstream-Default), Nachtvariante „Dunkel“.
Kein Material You. Medien-Benachrichtigung standardmäßig nicht eingefärbt.

Varianten B „Stein“ (fast Graustufen) und C „Papier“ (Toolbar = Hintergrund, dunkle Icons) sind
in der Vorschau. C ist am ruhigsten, braucht aber Änderungen an ca. 10 Layouts/Styles mehr
(fest verdrahtetes `@color/white` auf Tabs, Drawer-Kopf, Kanal-Tabs, `actionColor`,
`windowLightStatusBar`). Später möglich als Stufe 2 auf Basis von A.

## 1. Leitidee

- **Wenig Reiz**: keine gesättigten Farben, kein Rot, kein Reinweiß auf Schwarz für Flächen.
  Sättigung aller UI-Farben unter ca. 25 %; die einzige Farbigkeit liefern die Thumbnails.
- **Große Ruhe-Flächen**: ein Hintergrund, Karten heben sich nur minimal ab (Helligkeitsabstand
  2-4 %), Trenner statt Schatten.
- **Eine Akzentfarbe** (Salbei) für Interaktives: Links, Schalter, Fortschritt, Knöpfe.
- **Wenig Dekoration**: keine Zähler, keine Badges außer der Dauer, keine Bewegung als Belohnung.
- Pausenbildschirm und Absichtsabfrage sind die „Referenz-Screens“: so still soll die ganze App wirken.

## 2. Palette Variante A (Hex) und Mapping auf NewPipe-Ressourcen

| Rolle | Hell | Dunkel | Schwarz | Ressource (NewPipe) |
|---|---|---|---|---|
| Toolbar/Tab-Leiste/Statusbar (`colorPrimary`, `colorPrimaryDark`) | `#3D5A53` | `#24302D` | `#0B0E0D` | `light_/dark_youtube_primary_color`; Schwarz neu `zen_black_primary_color` |
| Akzent (`colorAccent`, Links, Schalter) | `#3E6A5E` | `#9DBFB2` | `#93B5A8` | `light_/dark_youtube_accent_color`, `*_settings_accent_color` |
| Hintergrund (`windowBackground`, Navbar transparent) | `#F3F1EC` | `#161B1A` | `#000000` | `light_/dark_/black_background_color` |
| Karte/Liste (`card_item_background_color`) | `#FAF9F6` | `#1D2321` | `#0B0E0D` | `*_card_item_background_color` |
| Karten-Kontrast (Chips, „Abonniert“) | `#E7E4DD` | `#29302E` | `#181D1C` | `*_card_item_contrast_color` |
| Dialog | `#FAF9F6` | `#222927` | (Dunkel) | `light_/dark_dialog_background_color`, `dark_license_background_color` |
| Text primär | `#1F2725` | `#E4E1D8` | `#D9D6CD` | Stufe 2: Color-State-Lists `zen_text_*` (sonst AppCompat-Default) |
| Text sekundär | `#57615E` | `#A7AEAA` | `#9DA4A0` | Stufe 2 (wie oben) |
| Hinweis/Meta | `#626C69` | `#8E9592` | `#848B88` | Stufe 2 |
| Toolbar-Text/Icons | `#F2EFE6` | `#E9E6DD` | `#D9D6CD` | fest `@color/white` in Layouts (bleibt), `drawer_header_font_color` → `#F2EFE6` |
| Trenner (`separator_color`) | `#261F2725` | `#14FFFFFF` | `#1AFFFFFF` | `*_separator_color` |
| Rahmen (`border_color`) | `#261F2725` | `#26E4E1D8` | `#1FE4E1D8` | `*_border_color` |
| Ripple / Auswahl | `#333D5A53` / `#1F3D5A53` | `#33E4E1D8` / `#1FE4E1D8` | (Dunkel) | `*_ripple_color`, `*_selected_color` |
| Fortschritt (gesehen) | `#3D5A53` | `#7FA698` | `#7FA698` | `progress_youtube_horizontal_*`; Dunkel neu `zen_dark_progress_color` |
| Dauer-Badge Hintergrund / Text | `#B81F2725` / `#F2EFE6` | gleich | gleich | `duration_background_color`, `duration_text_color` |
| Live-Badge | `#C88A5A4E` (gedämpftes Rost) | gleich | gleich | `live_duration_background_color` |
| Platzhalter-Thumbnail | `#8A918E` / `#6A716E` | gleich | gleich | `placeholder_background/foreground` |
| Abo-Knopf-Text | `#F2EFE6` | gleich | gleich | `subscribe_text_color` |
| Splash | Ring `#F2EFE6` auf `#2E4A45` | gleich | gleich | `splash_background.xml` (NewPipe-Dreieck → Ring) |

Variante B/C: Werte in der Vorschau (Generator-Dict `P`).

### WCAG-Kontrast (Variante A, Python-Skript, relative Luminanz nach WCAG 2.x)

| Paar | Hell | Dunkel | Schwarz | Soll |
|---|---|---|---|---|
| Text / Hintergrund | 13.5 | 13.3 | 14.5 | 4.5:1 |
| Text / Karte | 14.5 | 12.2 | 13.3 | 4.5:1 |
| Text / Dialog | 14.5 | 11.4 | 12.2 | 4.5:1 |
| Sekundärtext / Hintergrund | 5.7 | 7.7 | 8.3 | 4.5:1 |
| Sekundärtext / Karte | 6.1 | 7.1 | 7.6 | 4.5:1 |
| Hinweis / Hintergrund | 4.8 | 5.7 | 6.0 | 4.5:1 |
| Akzent (Link/Knopftext) / Hintergrund | 5.4 | 8.7 | 9.4 | 4.5:1 |
| Akzent / Dialog | 5.8 | 7.4 | 8.0 | 4.5:1 |
| Toolbar-Text / Toolbar | 6.6 | 11.0 | 13.3 | 4.5:1 |
| Weiß (fest in Layouts) / Toolbar | 7.5 | 13.7 | 19.4 | 4.5:1 |
| Fortschritt / Hintergrund (Nicht-Text) | 3.4 | 6.5 | 6.9 | 3:1 |
| Dauer-Badge über weißem Thumbnail | 5.3 | 6.0 | 6.4 | 4.5:1 |
| Dauer-Badge über grauem Thumbnail | 9.2 | 10.3 | 10.7 | 4.5:1 |

B und C bestehen ebenfalls alle Paare (Minimum jeweils der Hinweistext: B 4.9/5.5, C 4.8/5.7).

**Fund dabei**: `colorPrimary` wird an zwei Stellen als **Textfarbe/Fortschritt** auf dem Hintergrund
benutzt (`playlist_header.xml` „Mehr anzeigen“, `progress_youtube_horizontal_dark.xml`). Mit einem
ruhigen Dunkel-Balken wäre das unsichtbar (1.3:1; Upstream heute schon nur 2.0:1). Beide Stellen
werden mitgeändert (Akzent bzw. `zen_dark_progress_color`).

## 3. Theme-Strategie in NewPipes Struktur

Ist-Zustand: Themes `LightTheme`/`DarkTheme`/`BlackTheme` (+ `Base.V21/V27/V29`) in `values/styles.xml`,
Dienst-Overlays `*.YouTube` in `styles_services.xml` (setzen nur `colorPrimaryDark`), Farben in
`colors.xml` + `colors_services.xml`. `ThemeHelper` wählt, Default `auto_device_theme` mit
Nachtvariante `dark_theme`. Kein Material You/Dynamic Color im Projekt (AppCompat; Chips über
`MaterialComponents.Bridge`). Navbar ab API 27 transparent, also = Hintergrund.

Änderungen (Variante A):
1. `values/colors_services.xml`: 4 YouTube-Werte (primary/accent hell+dunkel). Andere Dienste
   (SoundCloud usw.) bleiben, sind in ZenPipe praktisch unbenutzt.
2. `values/colors.xml`: Werte der Hell/Dunkel/Schwarz-Blöcke und „Miscellaneous“ laut Tabelle
   (nur Hex-Werte in bestehenden Zeilen, keine neuen Namen).
3. **Neu** `values/zen_colors.xml`: `zen_black_primary_color`, `zen_dark_progress_color`
   (später `zen_text_*`). Neue Namen nur hier.
4. `values/styles.xml` (`Base.V21.BlackTheme`, +2 Zeilen `colorPrimary`/`colorPrimaryDark`) und
   `styles_services.xml` (`BlackTheme.YouTube` → `zen_black_primary_color`).
5. `drawable/progress_youtube_horizontal_dark.xml` (1 Zeile), `layout/playlist_header.xml`
   (1 Zeile `?attr/colorAccent`).
6. `xml/player_notification_settings.xml`: `notification_colorize` Default `false` (sonst färbt
   Android 11 die Medien-Benachrichtigung grell nach dem Thumbnail). `NotificationUtil` nutzt
   `dark_background_color` → automatisch neu. Feed-Benachrichtigungen nutzen schon das Icon-Grün.
7. `drawable/splash_background.xml` (schon Zen-Datei): Ring statt NewPipe-Dreieck,
   `gen_icons.py` erzeugt zusätzlich `drawable-nodpi/zen_splash_ring.png`.

Nicht ändern: `ThemeHelper` (Default „System folgen“ passt), Theme-Auswahl in Einstellungen >
Darstellung (Hell/Dunkel/Schwarz bleibt), Player-Oberfläche (dunkel, Icons weiß, Akzent ok).
**Material You nicht einführen**: Defy (Android 11) hat es nicht, es würde die ruhige feste Palette
durch Wallpaper-Farben ersetzen und den Patch vergrößern.

**Merge-Risiko**: gering. Konflikte entstehen nur, wenn Upstream genau diese Wertezeilen ändert
(selten; beim Konflikt unsere Werte nehmen). Größtes Risiko ist NewPipes Compose-Umbau
(`refactor`-Branch): dort wäre das Theming ohnehin neu zu machen. Ein Overlay ohne Änderung von
Upstream-Dateien geht in einem Modul nicht sauber (doppelte Ressourcennamen in `main`; ein
`values-v23`-Trick wäre möglich, ist aber undurchsichtig) → bewusst kleine Wertänderungen.
Aufwand A: ca. 2-3 h inkl. Gerätprüfung. C zusätzlich ca. 3-4 h und mehr Merge-Fläche.

## 4. Typografie, Abstände, Icons (kleine Maßnahmen)

- **Gewichte**: keine fetten Titel; Listen-Titel regular 14sp (wie Upstream), Toolbar-Titel medium.
  Countdown/Zahlen `sans-serif-light`, Ziffern tabellarisch.
- **Meta-Zeile ruhig**: Kanal/Datum in Sekundär-/Hinweisfarbe (kommt mit Stufe 2 Textfarben).
- **Zähler weg** (Entscheidung 4): Aufrufzahlen in Listen (`StreamInfoItemHolder.getStreamInfoDetailLine`)
  und Aufrufe/Likes auf der Detailseite ausblenden; „N min · Gruppe“ bleibt.
- **Dauer-Badge dezent**: 72 % Grüngrau statt Schwarz, Text Off-White (Farbwerte genügen).
- **Thumbnails** ohne zusätzliche Overlays; Entsättigung **nicht** jetzt (Codeänderung im
  Bildladen, eigener Graustufen-Modus wäre ein späteres Feature).
- **Flach**: Toolbar-Schatten im Dunkel/Schwarz praktisch unsichtbar lassen; Trenner statt Schatten.
- **Animationen**: keine neuen; Pausen-Screen höchstens 300 ms Einblenden. Systemeinstellung
  „Animationen entfernen“ wird von Android ohnehin respektiert.

## 5. Referenz-Screens

**Pausenbildschirm** (`ZenPauseActivity`, eigene Datei, kein Upstream-Risiko):
Hintergrund = `windowBackground`, Statusbar ebenfalls Hintergrundfarbe (helle Statusbar-Icons im
Hellen); oben kleiner Enso-Ring 40dp in Akzent, 70 % Deckkraft; Titel 24sp regular; Nachricht
15sp Sekundärfarbe, Zeilenabstand 1.2; Countdown 52sp light, tabellarische Ziffern; Zitat 16sp
kursiv Primärtext; Quelle 12sp Hinweisfarbe (statt `alpha 0.6`); „Zurück“ als randloser
Textknopf in Akzent statt grauem Raised-Button.

**Absichtsabfrage** (`ZenIntentPrompt`, AlertDialog): bekommt Dialoghintergrund und Akzent-Knöpfe
automatisch über das Theme; Titel regular, Nachricht Sekundärfarbe; „Weiter (n)“ ausgegraut
während der Wartezeit (wie heute). Hintergrund-Scrim Systemstandard.

Beide sind in der Vorschau für Hell/Dunkel/Schwarz gezeigt.

## 6. Umsetzungsplan (für einen Implementierungs-Agenten)

Je Schritt ein Commit `zen: …`; nach jedem Schritt `./gradlew assembleDebug runCheckstyle testDebugUnitTest`.

1. **Palette** (Abschnitt 3, Punkte 1-5): Farbwerte, `zen_colors.xml`, Schwarz-Primary,
   Fortschritt dunkel, „Mehr anzeigen“.
2. **Benachrichtigung** nicht eingefärbt (Punkt 6) + Zeile in `rules.md`.
3. **Splash-Ring** (Punkt 7, `gen_icons.py` erweitern).
4. **Pausenbildschirm** im neuen Look (Abschnitt 5).
5. **Textfarben** (Stufe 2): `color/zen_text_primary_{light,dark}.xml` usw. als Color-State-List
   mit Disabled-Zustand, in `Base.V21.*Theme` als `android:textColorPrimary/Secondary/Hint`.
6. **Zähler ausblenden** (nur nach Nutzer-OK, Entscheidung 4).

**Verifikation auf dem Defy** (`adb-device-check.sh`, dann je Theme Hell/Dunkel/Schwarz und
„System folgen“ mit `adb shell cmd uimode night yes|no`): Screenshots (`adb exec-out screencap -p`)
von Feed mit Ende-Karte, Praxis-Tab, Abos, Kanalseite (Tabs, Abo-Knopf), Detailseite mit
Fortschrittsbalken und „Mehr anzeigen“ in einer Playlist, Drawer, Einstellungen (Schalter-Farbe),
Absichtsabfrage (App neu starten), Pausenbildschirm (Konstanten temporär verkürzen wie in
`STATE.md` beschrieben, danach zurück), Mini-Player, Benachrichtigung
(`adb shell cmd statusbar expand-notifications`), Splash. Mit der Vorschau vergleichen; nirgends
mehr Rot (Ausnahme: Thumbnails); Logcat ohne FATAL. Danach `STATE.md` und diese Spec auf
„umgesetzt“ setzen.

## Offene Entscheidungen (Nutzer)

1. Variante: **A Salbei (Empfehlung)**, B Stein oder C Papier.
2. Nachtvariante bei „System folgen“: **Dunkel (Empfehlung, Upstream-Default)** oder Schwarz.
3. Medien-Benachrichtigung standardmäßig nicht eingefärbt: **ja (Empfehlung)**.
4. Aufruf-/Like-Zähler ausblenden: **ja (Empfehlung)**.

## Anhang: Kontrastformel

```python
def lum(h):
    f = lambda v: v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = (f(int(h[i:i + 2], 16) / 255) for i in (1, 3, 5))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def contrast(a, b):
    hi, lo = sorted((lum(a), lum(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)
# halbtransparente Farben vorher über den Untergrund mischen: a*fg + (1-a)*bg je Kanal
```
