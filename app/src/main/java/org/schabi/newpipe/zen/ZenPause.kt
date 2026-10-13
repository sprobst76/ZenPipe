package org.schabi.newpipe.zen

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.time.ZoneId
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.player.playqueue.PlayQueue

/**
 * What kind of content a stream is, for the mandatory pause. Music and PRAXIS (videos of a
 * user-maintained practice channel: yoga, breathwork, meditation, sleep aids) are exempt.
 */
enum class ZenKind {
    VIDEO,
    PODCAST,
    MUSIC,
    PRAXIS;

    /** An abort of this kind never starts a pause. */
    val pauseExempt: Boolean get() = this == MUSIC || this == PRAXIS
}

/**
 * Rules of the mandatory pause after an aborted video (see zen-docs/rules.md). All numbers are
 * named constants so they can be tuned (and shortened for device tests) in one place.
 */
object ZenPauseRules {
    /** Playing time of ONE video session needed before an abort triggers a pause. */
    @JvmStatic
    fun minWatchMs() = ZenSettings.pauseTriggerMs()

    /** "Watched to the end": at least this fraction played or at most this much left. */
    const val FINISHED_FRACTION = 0.90
    const val FINISHED_REMAINING_MS = 60 * 1000L

    /** "Resume later" (no pause) allowed this many times per Zen day. */
    const val LATER_PER_DAY = 2

    /** The running session is written to the Zen preferences at most this often. */
    const val PERSIST_INTERVAL_MS = 10 * 1000L

    @JvmStatic
    fun pauseLengthMs(playedMs: Long): Long = (playedMs * ZenSettings.pauseFraction()).toLong().coerceIn(ZenSettings.minPauseMs(), ZenSettings.maxPauseMs())

    @JvmStatic
    fun isFinished(positionMs: Long, durationMs: Long): Boolean = durationMs > 0 && positionMs >= 0 &&
        (positionMs >= durationMs * FINISHED_FRACTION || durationMs - positionMs <= FINISHED_REMAINING_MS)

    @JvmStatic
    fun qualifies(playedMs: Long, kind: ZenKind): Boolean = ZenSettings.pauseEnabled() && !kind.pauseExempt && playedMs >= minWatchMs()

    /**
     * PlayerService safety net: does a player intent start a playback? Enqueueing into a player
     * that already has a queue does not (the running video keeps playing; the new item is checked
     * when it is reached, see [ZenPauseEngine.mayChain]); a fresh start or an enqueue that opens
     * the player does.
     */
    @JvmStatic
    fun intentStartsPlayback(enqueue: Boolean, playerHasQueue: Boolean): Boolean = !enqueue || !playerHasQueue
}

/**
 * Music/podcast heuristic on what the extractor delivers for a stream (StreamInfo.category is the
 * YouTube category from the player response, e.g. "Music"; tags are the uploader's keywords).
 * In this order: category "Music" -> music; "- Topic"/VEVO channel -> music; strict music title
 * words -> music; with NO category: tag "music" -> music; podcast hints -> podcast; audio-only
 * and >= 20 min -> podcast; everything else is a video. Unsure = strict (video/podcast = pause).
 */
object ZenContentKind {
    private val MUSIC_CHANNEL = Regex("(- topic$|vevo$)", RegexOption.IGNORE_CASE)
    private val MUSIC_TITLE = Regex(
        "\\b(official (music )?video|official audio|lyrics?|lyric video|music video|full album)\\b",
        RegexOption.IGNORE_CASE
    )
    private val PODCAST_HINT = Regex("\\b(podcast|episode|folge|ep\\.? ?\\d+|#\\d+)\\b", RegexOption.IGNORE_CASE)
    const val PODCAST_MIN_AUDIO_SEC = 20 * 60L

    @JvmStatic
    fun classify(
        category: String?,
        tags: List<String>?,
        uploader: String?,
        title: String?,
        durationSec: Long,
        audioOnly: Boolean,
        /** The uploader is an active practice channel (decided by the user, wins over everything). */
        praxis: Boolean = false
    ): ZenKind {
        if (praxis) return ZenKind.PRAXIS
        val cat = category?.trim().orEmpty()
        if (cat.equals("Music", ignoreCase = true)) return ZenKind.MUSIC
        if (uploader != null && MUSIC_CHANNEL.containsMatchIn(uploader.trim())) return ZenKind.MUSIC
        if (title != null && MUSIC_TITLE.containsMatchIn(title)) return ZenKind.MUSIC
        if (cat.isEmpty() && tags != null && tags.any { it.trim().equals("music", ignoreCase = true) }) {
            return ZenKind.MUSIC
        }
        if (title != null && PODCAST_HINT.containsMatchIn(title) ||
            uploader != null && uploader.contains("podcast", ignoreCase = true) ||
            (audioOnly && durationSec >= PODCAST_MIN_AUDIO_SEC)
        ) {
            return ZenKind.PODCAST
        }
        return ZenKind.VIDEO
    }
}

/**
 * Counts the playing time of one video session, persists it while it runs (a process kill must not
 * bypass the pause) and decides at the end whether a pause follows. Pure logic, no Android.
 *
 * Session = one playback of one video (url) in the player. It ends by
 *  - finish (state ended, or [ZenPauseRules.isFinished] when it is closed): no abort;
 *  - close (player destroyed: close button, notification swipe, task removed, also while paused),
 *    switch to another video, or process kill (detected at the next start): abort;
 *  - "resume later" ([later]): no abort, at most [ZenPauseRules.LATER_PER_DAY] per Zen day.
 * Pausing in the player does not end the session. Only time while really playing counts; seeking
 * does not add time. An abort after >= [ZenPauseRules.minWatchMs()] of non-music playing starts
 * a pause of 20 % of the playing time (min 1, max 15 min) that only runs down with device uptime.
 */
class ZenPauseEngine(
    private val store: ZenKeyValueStore,
    private val countdown: ZenCountdown,
    private val monotonic: () -> Long,
    private val trustedNow: () -> Long,
    private val zone: () -> ZoneId,
    private val log: (String, String) -> Unit
) {
    enum class Reason { COMPLETED, CLOSED, SWITCHED, KILLED }

    sealed class Outcome {
        object None : Outcome()
        object Finished : Outcome()
        data class Short(val playedMs: Long) : Outcome()
        data class Exempt(val playedMs: Long, val kind: ZenKind) : Outcome()
        data class Paused(val playedMs: Long, val lengthMs: Long) : Outcome()
        object Later : Outcome()
        data class Slept(val playedMs: Long) : Outcome()
    }

    private var open = false
    private var url = ""
    private var played = 0L
    private var pos = 0L
    private var dur = 0L
    private var kind: ZenKind? = null
    private var playingSince = -1L
    private var lastPersist = 0L
    private var chainUrl: String? = null
    private var parkedUrl: String? = null
    private var parkedDur = 0L
    private var chained = false
    private val praxisTime = ZenPraxisTime(store)

    @Synchronized
    fun hasOpenSession() = open

    @Synchronized
    fun sessionUrl(): String? = if (open) url else null

    /** True while the kind of the running session is still unknown (classification pending). */
    @Synchronized
    fun needsKind() = open && kind == null

    @Synchronized
    fun playedMs(): Long {
        accumulate()
        return played
    }

    /** A video (url) became the current item of the player. */
    @Synchronized
    fun onItem(newUrl: String, durationMs: Long) {
        if (open && newUrl != url) finish(Reason.SWITCHED, pos, dur)
        parkedUrl = null
        if (!open) {
            chained = newUrl == chainUrl
            chainUrl = null
            open = true
            url = newUrl
            played = 0
            pos = 0
            dur = durationMs
            kind = null
            playingSince = -1
            persist()
        } else if (durationMs > 0) {
            dur = durationMs
        }
    }

    @Synchronized
    fun onPlaying(playing: Boolean, positionMs: Long) {
        val parked = parkedUrl
        if (!open && playing && parked != null) onItem(parked, parkedDur) // resumed after the sleep timer
        if (!open) return
        accumulate()
        playingSince = if (playing) monotonic() else -1
        if (positionMs >= 0) pos = positionMs
        persist()
    }

    @Synchronized
    fun onProgress(positionMs: Long, durationMs: Long, playing: Boolean) {
        if (!open) return
        accumulate()
        // resynchronise: the "playing" event may have arrived before the item was known
        if (playing && playingSince < 0) playingSince = monotonic()
        if (!playing) playingSince = -1
        if (positionMs >= 0) pos = positionMs
        if (durationMs > 0) dur = durationMs
        if (monotonic() - lastPersist >= ZenPauseRules.PERSIST_INTERVAL_MS) persist()
    }

    @Synchronized
    fun setKind(newKind: ZenKind) {
        if (open && kind == null) {
            kind = newKind
            persist()
        }
    }

    @Synchronized
    fun onCompleted(): Outcome = if (open) finish(Reason.COMPLETED, dur, dur) else Outcome.None

    /** The player was destroyed. [positionMs]/[durationMs] < 0 mean unknown (last tick is used). */
    @Synchronized
    fun onClosed(positionMs: Long, durationMs: Long): Outcome = if (open) finish(Reason.CLOSED, if (positionMs >= 0) positionMs else pos, if (durationMs > 0) durationMs else dur) else Outcome.None

    /** At process start: a persisted open session means the previous process died while playing. */
    @Synchronized
    fun recover(): Outcome {
        if (open) return Outcome.None
        if (store.getInt(K_OPEN, 0) != 1) return Outcome.None
        open = true
        url = store.getString(K_URL, "") ?: ""
        played = store.getLong(K_PLAYED, 0)
        pos = store.getLong(K_POS, 0)
        dur = store.getLong(K_DUR, 0)
        kind = store.getString(K_KIND, null)?.let { runCatching { ZenKind.valueOf(it) }.getOrNull() }
        playingSince = -1
        return finish(Reason.KILLED, pos, dur)
    }

    /**
     * The user wants to start [newUrl] while another video is still open: that is an abort of the
     * old one. Returns true if this started a pause (the caller must stop the player and block).
     */
    @Synchronized
    fun abandonForNewStart(newUrl: String): Boolean {
        if (!open || newUrl == url) return false
        accumulate()
        if (!ZenPauseRules.qualifies(played, kind ?: ZenKind.VIDEO) || ZenPauseRules.isFinished(pos, dur)) {
            return false // harmless: the normal switch handling decides when the new item arrives
        }
        return finish(Reason.SWITCHED, pos, dur) is Outcome.Paused
    }

    /**
     * The sleep timer ran out and paused the player: the session ends WITHOUT a pause (falling
     * asleep is no abort) and without using a "resume later". Practice minutes count. If the user
     * resumes the same item later, a new session starts (so the time is counted again).
     */
    @Synchronized
    fun endBySleepTimer(): Outcome {
        if (!open) return Outcome.None
        accumulate()
        val playedMs = played
        val k = kind ?: ZenKind.VIDEO
        val u = url
        val d = dur
        log("sleep_end", "url=$u kind=$k playedMs=$playedMs posMs=$pos durMs=$d")
        clearSession()
        if (k == ZenKind.PRAXIS) praxisTime.add(ZenDay.dayOf(trustedNow(), zone()).toString(), playedMs)
        parkedUrl = u
        parkedDur = d
        return Outcome.Slept(playedMs)
    }

    // ---------------------------------------------------------------- resume later (F6)

    @Synchronized
    fun laterLeft(): Int = (ZenPauseRules.LATER_PER_DAY - laterUsedToday()).coerceAtLeast(0)

    /**
     * Ends the open session without a pause. A session that would not cause a pause anyway (short,
     * music, nearly finished) is simply closed and costs no allowance. False if no session is
     * open or a pause would follow and no allowance is left today.
     */
    @Synchronized
    fun later(): Boolean {
        if (!open) return false
        accumulate()
        val wouldPause = ZenPauseRules.qualifies(played, kind ?: ZenKind.VIDEO) && !ZenPauseRules.isFinished(pos, dur)
        if (!wouldPause) {
            finish(Reason.CLOSED, pos, dur)
            return true
        }
        if (laterLeft() <= 0) return false
        val day = ZenDay.dayOf(trustedNow(), zone()).toString()
        store.put(K_LATER_DAY to day, K_LATER_COUNT to laterUsedToday() + 1)
        log("later", "url=$url playedMs=$played posMs=$pos left=${laterLeft()}")
        clearSession()
        return true
    }

    /** What closing the running session right now would cost; null if it would cost nothing. */
    data class ClosePreview(val playedMs: Long, val pauseMs: Long, val laterLeft: Int)

    @Synchronized
    fun previewClose(): ClosePreview? {
        if (!open) return null
        accumulate()
        val wouldPause = ZenPauseRules.qualifies(played, kind ?: ZenKind.VIDEO) && !ZenPauseRules.isFinished(pos, dur)
        return if (wouldPause) ClosePreview(played, ZenPauseRules.pauseLengthMs(played), laterLeft()) else null
    }

    private fun laterUsedToday(): Int {
        val today = ZenDay.dayOf(trustedNow(), zone()).toString()
        return if (store.getString(K_LATER_DAY, "") == today) store.getInt(K_LATER_COUNT, 0) else 0
    }

    // ---------------------------------------------------------------- pause state

    @Synchronized
    fun remainingMs(): Long {
        val left = countdown.remainingMs()
        if (left == 0L && store.getInt(K_PAUSE_ON, 0) == 1) {
            store.put(K_PAUSE_ON to 0)
            log("pause_end", "")
        }
        return left
    }

    @Synchronized
    fun isActive() = remainingMs() > 0

    /**
     * May a new playback start now? During a running pause only practice may start ([praxis] is
     * decided by the user's channel/playlist assignment). Starting practice never shortens the pause.
     */
    @Synchronized
    fun allowsStart(praxis: Boolean) = praxis || !isActive()

    /** A running session of [kind] must be stopped: a pause runs and it is not practice. */
    @Synchronized
    fun blocksRunning(kind: ZenKind) = kind != ZenKind.PRAXIS && isActive()

    // ---------------------------------------------------------------- practice chain

    /**
     * The current item has played to its end and the queue would continue with the next item.
     * Nothing ever starts by itself in ZenPipe, with one exception: a chain of practice videos
     * (the running session is practice and the next item is practice by its list data) may
     * continue. Returns true if it may; the next item is then remembered as "chained".
     */
    @Synchronized
    fun mayChain(nextUrl: String?, nextPraxis: Boolean): Boolean {
        val ok = open && kind == ZenKind.PRAXIS && nextPraxis && nextUrl != null
        chainUrl = if (ok) nextUrl else null
        if (!ok) log("chain_stop", "url=$url next=$nextUrl kind=$kind nextPraxis=$nextPraxis")
        return ok
    }

    /** The running session was reached by [mayChain] and turned out not to be practice: stop it. */
    @Synchronized
    fun chainBreaks(kind: ZenKind) = open && chained && kind != ZenKind.PRAXIS

    @Synchronized
    fun lastPausePlayedMs() = store.getLong(K_PAUSE_PLAYED, 0)

    /** Counts started pauses (identifies "this pause", e.g. for its quote). */
    @Synchronized
    fun pauseSeq() = store.getLong(K_PAUSE_SEQ, 0)

    // ---------------------------------------------------------------- internals

    private fun accumulate() {
        if (playingSince >= 0) {
            val now = monotonic()
            played += (now - playingSince).coerceAtLeast(0)
            playingSince = now
        }
    }

    private fun persist() {
        lastPersist = monotonic()
        store.put(
            K_OPEN to 1,
            K_URL to url,
            K_PLAYED to played,
            K_POS to pos,
            K_DUR to dur,
            K_KIND to kind?.name
        )
    }

    private fun clearSession() {
        open = false
        playingSince = -1
        store.put(K_OPEN to null, K_URL to null, K_PLAYED to null, K_POS to null, K_DUR to null, K_KIND to null)
    }

    private fun finish(reason: Reason, endPos: Long, endDur: Long): Outcome {
        accumulate()
        val playedMs = played
        val k = kind ?: ZenKind.VIDEO
        val finished = reason == Reason.COMPLETED || ZenPauseRules.isFinished(endPos, endDur)
        val u = url
        clearSession()
        if (k == ZenKind.PRAXIS) praxisTime.add(ZenDay.dayOf(trustedNow(), zone()).toString(), playedMs)
        val detail = "url=$u reason=$reason kind=$k playedMs=$playedMs posMs=$endPos durMs=$endDur"
        if (finished) {
            log("watch_end", detail)
            return Outcome.Finished
        }
        if (k.pauseExempt) {
            log("abort_exempt", detail)
            return Outcome.Exempt(playedMs, k)
        }
        if (!ZenSettings.pauseEnabled()) {
            log("abort_pause_off", detail)
            return Outcome.Short(playedMs)
        }
        if (playedMs < ZenPauseRules.minWatchMs()) {
            log("abort_short", detail)
            return Outcome.Short(playedMs)
        }
        val length = ZenPauseRules.pauseLengthMs(playedMs)
        val running = countdown.remainingMs()
        countdown.start(maxOf(length, running))
        store.put(K_PAUSE_ON to 1, K_PAUSE_PLAYED to playedMs, K_PAUSE_SEQ to pauseSeq() + 1)
        log("pause_start", "$detail lengthMs=$length")
        return Outcome.Paused(playedMs, length)
    }

    companion object {
        private const val K_OPEN = "ps_open"
        private const val K_URL = "ps_url"
        private const val K_PLAYED = "ps_played"
        private const val K_POS = "ps_pos"
        private const val K_DUR = "ps_dur"
        private const val K_KIND = "ps_kind"
        private const val K_PAUSE_ON = "pause_on"
        private const val K_PAUSE_PLAYED = "pause_played"
        private const val K_PAUSE_SEQ = "pause_seq"
        private const val K_LATER_DAY = "later_day"
        private const val K_LATER_COUNT = "later_count"
    }
}

/** Android glue: the engine singleton, hooks for the player and the pause screen. */
object ZenPause {
    @Volatile
    private var engine: ZenPauseEngine? = null

    @JvmStatic
    fun engine(context: Context): ZenPauseEngine {
        return engine ?: synchronized(this) {
            val app = context.applicationContext
            engine ?: ZenPauseEngine(
                store = Zen.store(app),
                countdown = Zen.countdown(app, "pause"),
                monotonic = android.os.SystemClock::elapsedRealtime,
                trustedNow = { Zen.clock(app).now().trustedMillis },
                zone = { ZenZone.fixed(Zen.store(app)) },
                log = { type, detail -> Zen.log(app).add(type, detail) }
            ).also { engine = it }
        }
    }

    // ---- hooks called by the player (main thread)

    @JvmStatic
    fun onItem(context: Context, url: String, durationMs: Long) = engine(context).onItem(url, durationMs)

    @JvmStatic
    fun onPlaying(context: Context, playing: Boolean, positionMs: Long) = engine(context).onPlaying(playing, positionMs)

    @JvmStatic
    fun onProgress(context: Context, positionMs: Long, durationMs: Long, playing: Boolean) = engine(context).onProgress(positionMs, durationMs, playing)

    @JvmStatic
    fun needsKind(context: Context) = engine(context).needsKind()

    /** [onStreamInfo] results. */
    const val STREAM_OK = 0
    const val STREAM_BLOCK_PAUSE = 1
    const val STREAM_CHAIN_STOP = 2

    /**
     * Classifies the running session once its StreamInfo is known. Returns [STREAM_BLOCK_PAUSE]
     * if the player must close: a mandatory pause is running and this stream is not practice
     * (practice is the only playback allowed during a pause; this is the authoritative check, the
     * start gates only pre-check with list data). Returns [STREAM_CHAIN_STOP] if the player must
     * pause: the item was reached by a practice chain ([ZenPauseEngine.mayChain]) but is not
     * practice. Decided by the channel/playlist assignment only, never by title.
     */
    @JvmStatic
    fun onStreamInfo(context: Context, info: StreamInfo, audioOnly: Boolean): Int {
        val e = engine(context)
        val open = e.sessionUrl() ?: return STREAM_OK
        if (open != info.url && open != info.originalUrl) return STREAM_OK // info of another item
        val praxis = ZenPraxis.isPraxisStream(info.serviceId, info.url, info.uploaderUrl) ||
            ZenPraxis.isPraxisStream(info.serviceId, info.originalUrl, null)
        val kind = ZenContentKind.classify(
            info.category,
            info.tags,
            info.uploaderName,
            info.name,
            info.duration,
            audioOnly,
            praxis = praxis
        )
        e.setKind(kind)
        if (e.blocksRunning(kind)) {
            Zen.log(context).add("pause_block_running", "url=$open kind=$kind")
            return STREAM_BLOCK_PAUSE
        }
        if (e.chainBreaks(kind)) {
            Zen.log(context).add("chain_stop", "url=$open kind=$kind reason=stream_info")
            return STREAM_CHAIN_STOP
        }
        return STREAM_OK
    }

    /**
     * The player paused at the end of the current item and [next] would follow. True if the queue
     * may continue (practice chain), false if it must stay paused (see [ZenPauseEngine.mayChain]).
     */
    @JvmStatic
    fun mayChain(context: Context, next: org.schabi.newpipe.player.playqueue.PlayQueueItem?): Boolean = engine(context).mayChain(next?.url, next != null && ZenPraxis.isPraxisStream(next.serviceId, next.url, next.uploaderUrl))

    @JvmStatic
    fun onCompleted(context: Context) {
        engine(context).onCompleted()
    }

    @JvmStatic
    fun onClosed(context: Context, positionMs: Long, durationMs: Long) {
        engine(context).onClosed(positionMs, durationMs)
    }

    /** The sleep timer paused the player: end the session without a pause. */
    @JvmStatic
    fun onSleepTimerEnd(context: Context) {
        engine(context).endBySleepTimer()
    }

    /** Process start: a session left open by a killed process counts as aborted. */
    @JvmStatic
    fun recover(context: Context) {
        engine(context).recover()
    }

    // ---- gate

    @JvmStatic
    fun isActive(context: Context) = engine(context).isActive()

    @JvmStatic
    fun remainingMs(context: Context) = engine(context).remainingMs()

    @JvmStatic
    fun playedMs(context: Context) = engine(context).lastPausePlayedMs()

    /** Identifies the current (or last) pause. */
    @JvmStatic
    fun pauseSeq(context: Context) = engine(context).pauseSeq()

    /**
     * Call before a video/audio playback is started by the user. Returns true if it must NOT start:
     * a pause is running (the pause screen is shown). If another video is still open and switching
     * to [url] aborts it, that abort is evaluated first (and the old player is stopped).
     * Without stream data the start is treated as non-practice (strict).
     */
    @JvmStatic
    fun blockStart(context: Context, url: String?): Boolean = blockStart(context, url, false)

    /**
     * Like [blockStart], for a stream whose practice assignment is known: [praxis] streams (active
     * practice channel or practice playlist) may start during a running pause.
     */
    @JvmStatic
    fun blockStart(context: Context, url: String?, praxis: Boolean): Boolean {
        val e = engine(context)
        if (url != null && e.abandonForNewStart(url)) {
            PlayerHolder.getInstance().stopService()
        }
        if (!e.isActive()) return false
        if (e.allowsStart(praxis)) {
            Zen.log(context).add("pause_praxis_start", "url=$url")
            return false
        }
        showPauseScreen(context)
        return true
    }

    /** [blockStart] for a stream given by its list data (service, url, uploader url). */
    @JvmStatic
    fun blockStart(context: Context, serviceId: Int, url: String?, uploaderUrl: String?): Boolean = blockStart(context, url, ZenPraxis.isPraxisStream(serviceId, url, uploaderUrl))

    /** [blockStart] for a play queue: decided by its current item. */
    @JvmStatic
    fun blockStart(context: Context, queue: PlayQueue?): Boolean {
        val item = queue?.item
        return if (item == null) {
            blockStart(context, null as String?)
        } else {
            blockStart(context, item.serviceId, item.url, item.uploaderUrl)
        }
    }

    /**
     * Safety net inside PlayerService for play intents that bypassed [blockStart]; [queue] is the
     * queue of the intent (practice may pass, the player re-checks once the stream is loaded).
     */
    @JvmStatic
    fun blockForService(context: Context, queue: PlayQueue?): Boolean {
        val item = queue?.item
        if (engine(context).allowsStart(item != null && ZenPraxis.isPraxisStream(item.serviceId, item.url, item.uploaderUrl))) return false
        showPauseScreen(context)
        return true
    }

    @JvmStatic
    fun showPauseScreen(context: Context) {
        val intent = Intent(context, ZenPauseActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // background activity start can be denied; the caller still blocks the playback
            android.widget.Toast.makeText(
                context.applicationContext,
                context.getString(org.schabi.newpipe.R.string.zen_pause_toast),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // ---- "resume later"

    /**
     * Asks before the user closes the player by hand (mini-player X): if closing now would start a
     * pause, offers "resume later" (position kept, no pause, limited per Zen day), "close anyway"
     * or "keep watching". Otherwise [closeAction] runs at once.
     */
    @JvmStatic
    fun confirmClose(activity: Activity, closeAction: Runnable) {
        val e = engine(activity)
        val preview = e.previewClose()
        if (preview == null) {
            closeAction.run()
            return
        }
        val res = activity.resources
        val minutes = (preview.pauseMs + 59_999) / 60_000L
        val builder = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(org.schabi.newpipe.R.string.zen_close_title)
            .setMessage(
                activity.getString(
                    org.schabi.newpipe.R.string.zen_close_message,
                    preview.playedMs / 60_000L,
                    res.getQuantityString(org.schabi.newpipe.R.plurals.zen_minutes, minutes.toInt(), minutes.toInt())
                ) + if (preview.laterLeft > 0) {
                    "\n" + activity.getString(org.schabi.newpipe.R.string.zen_close_later_left, preview.laterLeft)
                } else {
                    ""
                }
            )
            .setNeutralButton(org.schabi.newpipe.R.string.zen_close_keep, null)
            .setNegativeButton(org.schabi.newpipe.R.string.zen_close_anyway) { _, _ -> closeAction.run() }
        if (preview.laterLeft > 0) {
            builder.setPositiveButton(org.schabi.newpipe.R.string.zen_later_resume_action) { _, _ ->
                if (later(activity)) closeAction.run()
            }
        }
        builder.show()
    }

    /** Returns true if the session was parked (no pause) and the player should be closed. */
    @JvmStatic
    fun later(context: Context): Boolean {
        val e = engine(context)
        val ok = e.later()
        val app = context.applicationContext
        val msg = if (ok) {
            app.getString(org.schabi.newpipe.R.string.zen_later_resume_saved, e.laterLeft())
        } else {
            app.getString(org.schabi.newpipe.R.string.zen_later_resume_none)
        }
        android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_LONG).show()
        return ok
    }
}
