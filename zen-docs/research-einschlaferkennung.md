# Research: Sleep-Timer mit Einschlaferkennung koppeln

Stand: 2026-10-13. Analyse, keine Implementierung. Kontext: Sleep-Timer im Player
(15/30/45/60/90 min, 30 s Fade-out, dann Pause; Logik `ZenSleepTimer`/`ZenSleep` in `org.schabi.newpipe.zen`,
`SystemClock.elapsedRealtime`; das Interface `ZenSleepSignal` und `ZenSleepTimer.shortenTo/extend` sind als Hook
schon im Code, ohne Quelle). App: `minSdk = 23` (`buildSrc/.../ProjectConfig.kt`),
Zielgerät Android 11+ (Motorola Defy), F-Droid, keine Play Services.

## Empfehlung (kurz)

1. **Keine echte Schlaferkennung als Basis.** Ein Handy ohne Wearable erkennt Einschlafen
   in Echtzeit nur heuristisch. Für einen Sleep-Timer reicht eine einfache Umkehrung:
   *Wer reagiert, ist wach.*
2. **Stufe 0 zuerst:** fester Timer und ein „Noch da?“-Check-in kurz vor Ablauf
   (Benachrichtigungsaktion „+15 min“, optional Schütteln zum Verlängern). Kostet nichts,
   braucht keine Berechtigung und funktioniert überall.
3. **Stufe 1 optional:** Bewegungsruhe über den Beschleunigungssensor, nur solange der Timer
   läuft. Der Player hat schon einen Foreground-Service, also kommen Sensor-Events auch bei
   ausgeschaltetem Bildschirm an. Die Heuristik darf den Timer nur **verkürzen** (früher ausblenden)
   und nie eigenständig verlängern.
4. **Stufe 2 optional:** Die Broadcasts von Sleep as Android (Tracking gestartet,
   Leicht-/Tiefschlaf) mit einem Runtime-Receiver empfangen. Das ist die einzige
   Echtzeitquelle mit Wearable-Anbindung (auch über Gadgetbridge). Gadgetbridge selbst
   sendet keinen Schlafstatus.
5. **Health Connect und die Google Sleep API nicht für die Echtzeit-Kopplung nutzen.**
   Health Connect kommt höchstens später für Rückblick-Statistiken infrage, die Sleep API
   ist wegen Play Services ausgeschlossen.

## Optionen im Vergleich

Bewertung: ++ sehr gut, + gut, o mittel, − schlecht, −− ungeeignet.

| Option | Zuverlässigkeit (Echtzeit) | Akku | Privatsphäre | Aufwand | F-Droid |
|---|---|---|---|---|---|
| „Noch da?“-Check-in (Notification/Dialog, Ablauf = schläft) | + (Fehlerrichtung harmlos: im Zweifel Pause) | ++ | ++ | + (klein) | ++ |
| Interaktions-Inaktivität (Touch, Lautstärketasten, Bildschirm aus) | o (Bildschirm aus heißt nicht „schläft“) | ++ | ++ | + | ++ |
| Beschleunigungssensor (Ruhe-Heuristik) | o (Handy auf Matratze/in der Hand: ja; auf dem Nachttisch: nein) | o (gering bei SENSOR_DELAY_NORMAL/Batching, *Größenordnung unsicher*) | + (bleibt lokal) | o | ++ |
| Significant Motion (One-shot, Wake-up) | − (meldet nur Ortswechsel, nicht Einschlafen) | ++ | ++ | + | ++ |
| Licht-/Näherungssensor | − (dunkler Raum ≠ Schlaf, Näherung = Hosentasche) | + | + | o | ++ |
| Sleep as Android Broadcasts | + wenn Tracking läuft (Phasen per Aktigraphie/Wearable) | ++ (Arbeit macht die andere App) | o (proprietäre Dritt-App) | + (Runtime-Receiver) | + (optional, keine Lib nötig) |
| Gadgetbridge Intent-API | −− (kein Schlafstatus-Broadcast) | – | ++ | – | ++ |
| Health Connect `SleepSessionRecord` | −− (Sitzungen meist erst nach dem Aufwachen geschrieben, *nicht offiziell dokumentiert*) | + | o (Gesundheitsdaten, READ_SLEEP) | − | o–− (s. u.) |
| Google Sleep API / Activity Recognition | o | + | − | o | −− (Play Services) |

### Details

**Health Connect.** Ab Android 14 ist es ein Framework-Modul. Auf Android 9–13 braucht es die
Health-Connect-App aus dem Play Store (`com.google.android.apps.healthdata`). Auf dem Defy
(Android 11) wäre es also nur mit Play Store nutzbar, was für F-Droid-Nutzer typischerweise
wegfällt. Das SDK `androidx.health.connect:connect-client` verlangt minSdk 26 (ZenPipe: 23),
man bräuchte also Laufzeit-Gating. Die Lib wird laut Maven-Metadaten unter Apache-2.0
veröffentlicht. Ob F-Droid sie ohne Anti-Feature akzeptiert, ist **unsicher**: Sie kommt aus
Google Maven, hängt zur Laufzeit an einer proprietären Google-App und ist eventuell
NonFreeDep. Dazu kommt `READ_SLEEP`, eine sensible Gesundheitsberechtigung. Für Echtzeit ist
das Ganze nutzlos, weil ein Intervall-Record ein Ende braucht. Fazit: höchstens Stufe 3
(Rückblick: „Mit Timer eingeschlafen nach X min“), bei ZenPipe eher gar nicht.

**Gadgetbridge.** Die dokumentierte Intent-API nimmt Befehle an (Sync, Export, Settings,
Alarme) und sendet nur Status (`BLUETOOTH_CONNECTED`, `ACTIVITY_SYNC_FINISH`, BLE-Rohdaten).
Ein Schlafstatus wird nicht gesendet. Gadgetbridge leitet aber Sensordaten von Bangle.js,
Mi Band/Amazfit, Zepp OS, Garmin und Pebble an **Sleep as Android** weiter. Mit einem Wearable
läuft der Weg also über Gadgetbridge zu Sleep as Android und von dort per Broadcast zu ZenPipe.

**Sleep as Android** (proprietär) dokumentiert öffentliche Event-Broadcasts, die nur
Runtime-Receiver empfangen können (`registerReceiver` im Player-Service, keine Manifest-Receiver):
`com.urbandroid.sleep.alarmclock.SLEEP_TRACKING_STARTED_AUTO` / `..._STOPPED_AUTO`,
`com.urbandroid.sleep.TRACKING_LIGHT_SLEEP_AUTO`, `com.urbandroid.sleep.TRACKING_DEEP_SLEEP_AUTO`,
außerdem `ACTION_LULLABY_START/STOPPED_PLAYBACK_AUTO`. Eine Berechtigung ist laut Doku nicht nötig.
Nach Android 14 müssen Receiver als `RECEIVER_EXPORTED` registriert werden. Wie schnell die
Phase nach dem Einschlafen gemeldet wird, ist **unsicher**, wahrscheinlich mit Minuten Verzug.
Der Nutzer muss das Tracking selbst starten.

**Sensoren auf dem Gerät.** Seit Android 9 bekommen Hintergrund-Apps keine Sensor-Events,
mit Foreground-Service schon. Der Player-Service erfüllt das bereits. Der Beschleunigungssensor
verbraucht laut Android-Doku etwa ein Zehntel der Energie anderer Bewegungssensoren. Mit
`registerListener(..., samplingPeriodUs = 200_000, maxReportLatencyUs = 60_000_000)` (Batching)
kann der SoC schlafen. Ob Batching auf dem Defy wirklich in Hardware passiert, ist **unsicher**
und muss per `Sensor.fifoMaxEventCount` geprüft werden. Ab targetSdk 31 ist die Rate auf
200 Hz begrenzt, was hier irrelevant ist. Die Heuristik: Bewegungsenergie (|a| minus
Gleitmittel) pro Minute, dann „ruhig“ wenn N Minuten unter einer Schwelle **und** keine
Interaktion. Das ist plausibel, wenn das Handy im Bett liegt oder in der Hand gehalten wird.
Auf dem Nachttisch gibt es falsch-positive Treffer, daher darf das Signal nur verkürzen
(Fade früher) und der Nutzer kann es per Schütteln zurücknehmen. Licht, Näherung und
Significant Motion bringen keinen Mehrwert.

**Google Sleep API** (`play-services-location`, `SleepClassifyEvent`/`SleepSegmentEvent`,
`ACTIVITY_RECOGNITION`, minSdk 29) ist proprietär und an Play Services gebunden. Das bestätigt
den **Ausschluss**, ebenso für die Activity Recognition API.

## Stufenplan

- **Stufe 0 (mit dem Timer bzw. direkt danach):** T−2 min vor dem Fade eine Notification-Aktion
  „Noch wach? +15 min“ anzeigen, optional als Overlay im Player. Eine Lautstärketaste,
  ein Touch im Player oder die Aktion zählen als `onAwake()` und verlängern den Timer.
  Ohne Reaktion läuft der Timer normal ab. Optional: Schütteln zum Verlängern während des
  Fade-outs (Accelerometer nur in diesem 30-s-Fenster aktiv).
- **Stufe 1 (opt-in, Experiment):** Ruhe-Heuristik über den Accelerometer, nur solange der
  Timer läuft. Bei `onLikelyAsleep(confidence ≥ 0,7)` springt der Timer auf
  `min(Rest, 2 min)` und dann in den Fade. Akku und Trefferquote am Defy messen
  (Logcat und `dumpsys batterystats`), bevor das Feature sichtbar wird.
- **Stufe 2 (opt-in):** Adapter für Sleep-as-Android-Broadcasts. LIGHT/DEEP_SLEEP wirkt wie
  `onLikelyAsleep(0,9)`. Wearables über Gadgetbridge sind damit indirekt abgedeckt. Es kommt
  keine Lib dazu, nur Strings.
- **Stufe 3 (eher nein):** Health Connect nur für eine Rückblick-Statistik. Das widerspricht
  „local only“ und dem F-Droid-Ziel. Erst angehen, wenn Nutzer danach fragen.

## Hook-Skizze

Umgesetzt ist nur das Interface (`zen/ZenSleep.kt`) plus `ZenSleepTimer.shortenTo(ms)` (nie verlängern) und
`ZenSleepTimer.extend(ms)`; keine Quelle ist angebunden, `ZenSleep` ruft das Interface noch nicht auf.

```kotlin
package org.schabi.newpipe.zen

/** A source of hints whether the user is still awake while the sleep timer runs. */
interface ZenSleepSignal {
    fun start(listener: Listener)   // called when the timer is armed (player service context)
    fun stop()                      // called on cancel/expiry; must release sensors/receivers

    interface Listener {
        /** confidence in 0.0..1.0; the timer decides what to do with it */
        fun onLikelyAsleep(confidence: Float)
        /** explicit sign of wakefulness (interaction, shake, tracking stopped) */
        fun onAwake()
    }
}
```

Reaktion des Timers (`ZenSleepTimer`, reine Logik und testbar mit `ZenClock`/fake time):

- `onLikelyAsleep(c)` mit `c ≥ threshold` (Default 0,7): `remaining = min(remaining, 2 min)`.
  Der Timer wird nur verkürzt, nie verlängert. Mehrfache Signale sind idempotent.
- `onAwake()` während des Fades: Fade abbrechen, Lautstärke wiederherstellen, `+15 min`.
  `onAwake()` vor dem Fade: kein Effekt, nur der „Noch da?“-Check-in wird zurückgesetzt.
- Mehrere Quellen (`InteractionSignal`, `MotionStillnessSignal`, `SleepAsAndroidSignal`)
  werden über ein `CompositeZenSleepSignal` gebündelt. Der Timer kennt nur das Interface.
- Grenzen: Höchstens eine Verlängerung pro Check-in, damit keine Endlosschleife entsteht.
  Ohne Signalquelle verhält sich der Timer exakt wie der feste Timer.

## Quellen

- Health Connect, Get started (Android 14 Framework-Modul, ≤13 APK aus Play Store, SDK API 26, App API 28):
  https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started
- Health Connect Datentypen (`SleepSessionRecord`, READ_SLEEP/WRITE_SLEEP):
  https://developer.android.com/health-and-fitness/health-connect/data-types
- Health Connect Sleep sessions: https://developer.android.com/health-and-fitness/health-connect/features/sleep-sessions
- connect-client Lizenz (Apache 2.0, Maven-Metadaten): https://mvnrepository.com/artifact/androidx.health.connect
- Sleep as Android Intent API (Event-Broadcasts, nur Runtime-Receiver):
  https://sleep.urbandroid.org/docs/devs/intent_api.html
- Gadgetbridge und Sleep as Android: https://gadgetbridge.org/basics/integrations/sleep-as-android
- Gadgetbridge Intent API: https://gadgetbridge.org/internals/automations/intents/
- Bangle.js und Gadgetbridge (Intents): https://espruino.com/Gadgetbridge
- Android-Bewegungssensoren (Significant Motion, Accelerometer-Energie, Rate-Limit):
  https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion
- Android 9, Sensoren im Hintergrund: https://developer.android.com/about/versions/pie/android-9.0-changes-all
- Google Sleep API (Play Services, ACTIVITY_RECOGNITION, minSdk 29):
  https://developers.google.com/location-context/sleep
