package org.schabi.newpipe.zen

import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Soft practice cap (D61, spec-praxis-themen.md 1): once the practice time of the Zen day passes
 * the threshold ([ZenSettings.praxisCapMin], default 90 min) while practice plays, a calm question
 * "still practice or already watching?" ([ZenPraxisCapActivity]). No hard stop, no streaks, no
 * shame; at most once per Zen day and threshold. Practice time = [ZenPraxisTime.msOn] (the same
 * minutes as in the practice tab). The threshold is a Zen setting: lower = at once, higher or off =
 * loosening (24 h lock).
 */
object ZenPraxisCap {
    /** Setting value for "off" (above the largest threshold, so "off" is the loosest value). */
    const val OFF = 255

    /** How often a playing practice session checks the threshold. */
    const val CHECK_INTERVAL_MS = 30_000L

    private const val K_ASKED = "px_cap_asked"

    /** Whether the question is due: [capMin] reached on [day] and not asked yet for this day and threshold. */
    @JvmStatic
    fun due(todayMs: Long, capMin: Int, day: String, asked: String?): Boolean = capMin in 1 until OFF && todayMs >= capMin * 60_000L && asked != askedValue(day, capMin)

    @JvmStatic
    fun askedValue(day: String, capMin: Int) = "$day|$capMin"

    /** Today's Zen day and practice time. */
    private fun today(context: Context): Pair<String, Long> {
        val store = Zen.store(context)
        val day = ZenDay.dayOf(Zen.clock(context).now().trustedMillis, ZenZone.fixed(store)).toString()
        return day to ZenPraxisTime(store).msOn(day)
    }

    @Volatile
    private var lastCheck = Long.MIN_VALUE / 2

    /** Called while practice plays (player progress); checks at most every [CHECK_INTERVAL_MS]. */
    @JvmStatic
    fun onPraxisProgress(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now
        try {
            val cap = ZenSettings.praxisCapMin()
            val (day, ms) = today(context)
            if (!due(ms, cap, day, Zen.store(context).getString(K_ASKED, null))) return
            val intent = Intent(context, ZenPraxisCapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // a start from the background may be dropped by Android: then it is asked at the next check
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.w("ZenPraxisCap", "check", e)
        }
    }

    /** The question was shown: remembered for this day and threshold. Returns the minutes of today. */
    @JvmStatic
    fun markShown(context: Context): Int {
        val (day, ms) = today(context)
        val cap = ZenSettings.praxisCapMin()
        Zen.store(context).put(K_ASKED to askedValue(day, cap))
        Zen.log(context).add("praxis_cap_ask", "cap=$cap minutes=${ms / 60_000L}")
        return (ms / 60_000L).toInt()
    }

    @JvmStatic
    fun onAnswer(context: Context, pause: Boolean) {
        Zen.log(context).add("praxis_cap_answer", if (pause) "pause" else "continue")
    }
}
