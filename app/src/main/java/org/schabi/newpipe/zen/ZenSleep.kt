package org.schabi.newpipe.zen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sleep timer logic (pure, testable). Runs on device uptime ([monotonic] = elapsedRealtime, so the
 * wall clock and screen-off do not matter). During the last [FADE_MS] the volume falls linearly
 * from 1 to [MIN_VOLUME]; when it is due, the player pauses (see [ZenSleep]).
 *
 * The timer belongs to the player, not to a video: it keeps running across items of the queue
 * (practice chains, see rules.md) and is cancelled when the player closes.
 */
class ZenSleepTimer(private val monotonic: () -> Long) {
    private var endAt = -1L
    private var lengthMs = 0L

    @Synchronized
    fun start(durationMs: Long) {
        require(durationMs > 0)
        lengthMs = durationMs
        endAt = monotonic() + durationMs
    }

    @Synchronized
    fun cancel() {
        endAt = -1L
        lengthMs = 0L
    }

    @Synchronized
    fun isRunning() = endAt >= 0

    /** Remaining time, 0 when due, -1 when not running. */
    @Synchronized
    fun remainingMs(): Long = if (endAt < 0) -1L else (endAt - monotonic()).coerceAtLeast(0L)

    @Synchronized
    fun isDue() = endAt >= 0 && monotonic() >= endAt

    /** Volume factor for the player: 1 outside the fade, linear down to [MIN_VOLUME] (never 0 = "muted"). */
    @Synchronized
    fun volume(): Float {
        val left = remainingMs()
        if (left < 0 || left >= FADE_MS) return 1f
        val f = left.toFloat() / FADE_MS
        return (MIN_VOLUME + (1f - MIN_VOLUME) * f).coerceIn(MIN_VOLUME, 1f)
    }

    /**
     * Hook for a later sleep detection ([ZenSleepSignal]): "likely asleep" brings the end forward
     * to at most [remainingMs] from now (never extends).
     */
    @Synchronized
    fun shortenTo(remainingMs: Long) {
        if (endAt < 0) return
        endAt = minOf(endAt, monotonic() + remainingMs.coerceAtLeast(0L))
    }

    /** Hook: "awake" (or the user) extends a running timer by [ms]. */
    @Synchronized
    fun extend(ms: Long) {
        if (endAt < 0 || ms <= 0) return
        endAt = maxOf(endAt, monotonic()) + ms
    }

    companion object {
        const val FADE_MS = 30_000L
        const val MIN_VOLUME = 0.05f

        /** Offered durations in minutes. */
        @JvmField
        val CHOICES_MIN = intArrayOf(15, 30, 45, 60, 90)

        /** Debug builds only: a short duration for device tests (no effect on any Zen rule). */
        const val DEBUG_CHOICE_MS = 60_000L
    }
}

/**
 * Hook for a later falling-asleep detection (see zen-docs/research-einschlaferkennung.md); no
 * source is implemented yet. A source reports to the running timer: likely asleep -> shorten
 * ([ZenSleepTimer.shortenTo]), awake -> extend ([ZenSleepTimer.extend]).
 */
interface ZenSleepSignal {
    interface Listener {
        fun onLikelyAsleep(confidence: Float)
        fun onAwake()
    }

    fun start(listener: Listener)
    fun stop()
}

/** Android glue: one timer for the one player of the process, ticking once a second while it runs. */
object ZenSleep {
    /** Implemented by the player. */
    interface Target {
        fun zenSleepVolume(volume: Float)
        fun zenSleepEnd()
    }

    private const val TICK_MS = 1000L
    private const val K_DEFAULT = "sleep_default_min"

    private val timer = ZenSleepTimer(SystemClock::elapsedRealtime)
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Runnable>()
    private var target: Target? = null
    private var app: Context? = null
    private val tick = Runnable { onTick() }

    @JvmStatic
    fun bind(context: Context, t: Target) {
        app = context.applicationContext
        target = t
    }

    /** The player closes: the timer ends with it. */
    @JvmStatic
    fun unbind(t: Target) {
        if (target !== t) return
        if (timer.isRunning()) log("sleep_timer_cancel", "reason=player_closed")
        timer.cancel()
        handler.removeCallbacks(tick)
        target = null
        notifyListeners()
    }

    @JvmStatic
    fun hasPlayer() = target != null

    @JvmStatic
    fun isRunning() = timer.isRunning()

    @JvmStatic
    fun remainingMs() = timer.remainingMs()

    @JvmStatic
    fun start(durationMs: Long) {
        if (target == null) return
        val wasFading = timer.volume() < 1f
        timer.start(durationMs)
        if (wasFading) target?.zenSleepVolume(1f)
        log("sleep_timer_start", "ms=$durationMs")
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
        notifyListeners()
    }

    @JvmStatic
    fun cancel() {
        if (!timer.isRunning()) return
        timer.cancel()
        handler.removeCallbacks(tick)
        target?.zenSleepVolume(1f)
        log("sleep_timer_cancel", "reason=user")
        notifyListeners()
    }

    /** Called with the main looper about once a second while the timer runs (UI countdown). */
    @JvmStatic
    fun addListener(r: Runnable) {
        listeners.add(r)
    }

    @JvmStatic
    fun removeListener(r: Runnable) {
        listeners.remove(r)
    }

    /** Duration (min) offered first in the menu, 0 = none (Settings > Zen). Not a Zen rule, no lock. */
    @JvmStatic
    fun defaultMinutes(context: Context): Int = Zen.store(context).getInt(K_DEFAULT, 0)

    @JvmStatic
    fun setDefaultMinutes(context: Context, minutes: Int) = Zen.store(context).put(K_DEFAULT to if (minutes > 0) minutes else null)

    private fun onTick() {
        if (!timer.isRunning()) return
        val t = target
        if (t == null) {
            timer.cancel()
            return
        }
        t.zenSleepVolume(timer.volume())
        if (timer.isDue()) {
            timer.cancel()
            log("sleep_timer_end", "")
            t.zenSleepEnd()
            notifyListeners()
            return
        }
        notifyListeners()
        handler.postDelayed(tick, TICK_MS)
    }

    private fun notifyListeners() = listeners.forEach { it.run() }

    private fun log(type: String, detail: String) {
        app?.let { Zen.log(it).add(type, detail) }
    }
}
