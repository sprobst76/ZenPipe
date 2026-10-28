package org.schabi.newpipe.zen

import java.time.ZoneId

/** Outcome of an intent prompt (stored in `ia_day_<day>`, logged as `intent_prompt result=...`). */
enum class ZenIntentResult(val key: String) {
    CONTINUE("continue"),
    NOT_NOW("not_now"),
    SHEET("sheet")
}

/**
 * Per Zen day (04:00 rollover) counters of prompt outcomes, kept for [KEEP_DAYS] days.
 * `ia_day_<yyyy-MM-dd>` = `continue=2,not_now=1`, `ia_days` = comma separated day list.
 */
class ZenIntentStats(
    private val store: ZenKeyValueStore,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    @Synchronized
    fun record(result: ZenIntentResult, nowMillis: Long) {
        val day = ZenDay.dayOf(nowMillis, zone).toString()
        val counts = parse(store.getString(DAY_PREFIX + day, null)).toMutableMap()
        counts[result.key] = (counts[result.key] ?: 0) + 1
        val days = (store.getString(DAYS_KEY, "") ?: "").split(',').filter { it.isNotEmpty() }
            .filter { it != day } + day
        val keep = days.takeLast(KEEP_DAYS)
        val dropped = days - keep.toSet()
        val entries = ArrayList<Pair<String, Any?>>()
        entries.add(DAY_PREFIX + day to counts.entries.joinToString(",") { "${it.key}=${it.value}" })
        entries.add(DAYS_KEY to keep.joinToString(","))
        dropped.forEach { entries.add(DAY_PREFIX + it to null) }
        store.put(*entries.toTypedArray())
    }

    /** Counts of the Zen day containing [nowMillis]. */
    fun countsOn(nowMillis: Long): Map<ZenIntentResult, Int> {
        val raw = parse(store.getString(DAY_PREFIX + ZenDay.dayOf(nowMillis, zone), null))
        return ZenIntentResult.values().mapNotNull { r -> raw[r.key]?.let { r to it } }.toMap()
    }

    private fun parse(s: String?): Map<String, Int> = (s ?: "").split(',').mapNotNull {
        val p = it.split('=')
        val n = p.getOrNull(1)?.toIntOrNull()
        if (p.size == 2 && n != null) p[0] to n else null
    }.toMap()

    companion object {
        const val DAY_PREFIX = "ia_day_"
        const val DAYS_KEY = "ia_days"
        const val KEEP_DAYS = 30
    }
}

/**
 * Prompt state of the process: whether the latest prompt was decided and when (cooldown).
 * Pure logic with an injectable clock; [org.schabi.newpipe.util.ZenIntentPrompt] holds one instance.
 */
class ZenIntentGate(private val now: () -> Long) {
    var confirmed = false
        private set
    private var lastAskedAt = -1L

    /** A prompt is about to be shown: the previous decision no longer counts. */
    fun onAsked() {
        confirmed = false
        lastAskedAt = -1
    }

    /** "Continue" or an equivalent conscious decision elsewhere (channel sheet): cooldown + feed release. */
    fun decide() {
        confirmed = true
        lastAskedAt = now()
    }

    /** "Not now" costs nothing (D20). */
    fun decline() = onAsked()

    /** true: no (new) prompt needed right now. */
    fun isSkippable(ownPlayback: Boolean): Boolean = ownPlayback ||
        (confirmed && lastAskedAt >= 0 && now() - lastAskedAt < MIN_INTERVAL_MS)

    companion object {
        const val MIN_INTERVAL_MS = 5 * 60 * 1000L
    }
}
