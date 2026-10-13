/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen

/**
 * Playing time per Zen day (spec `spec-bildung-und-hintergrundzeit.md` 2.1): real playing time
 * (`elapsedRealtime` while the player is playing), booked continuously onto the Zen day of the
 * moment it is booked, split by playback mode — **picture** (video player) and **sound**
 * (background player) — and by content: music and practice are kept apart (both modes), so
 * picture/sound hold only the time that a later daily limit would count.
 *
 * Stored in `zen_state` as `wt_<day>` = `picture,sound,music,practice` (ms); [KEEP_DAYS] days are
 * kept (index `wt_days`). Not part of export/import/reset/auto-backup (rule for `zen_state`).
 * Booked time is never taken back. There is no limit: [zenMinutesMs] is only a hook (spec 2.4).
 */
class ZenWatchTime(private val store: ZenKeyValueStore) {
    data class Day(val pictureMs: Long = 0, val soundMs: Long = 0, val musicMs: Long = 0, val praxisMs: Long = 0) {
        val totalMs: Long get() = pictureMs + soundMs + musicMs + praxisMs

        /** Zen minutes of a later daily limit (spec 2.4, not enforced): picture 1:1, sound 1:2, music/practice 0. */
        val zenMinutesMs: Long get() = pictureMs + soundMs * AUDIO_WEIGHT_PERCENT / 100

        fun encode() = "$pictureMs,$soundMs,$musicMs,$praxisMs"

        companion object {
            fun decode(s: String?): Day {
                val p = s.orEmpty().split(',').map { it.trim().toLongOrNull() ?: 0L }
                return Day(p.getOrElse(0) { 0 }, p.getOrElse(1) { 0 }, p.getOrElse(2) { 0 }, p.getOrElse(3) { 0 })
            }
        }
    }

    /**
     * Books [ms] of playing time on [day]. [audio] = background player (sound), otherwise video
     * player (picture). Unknown [kind] counts as video (fail-closed), never as music.
     */
    @Synchronized
    fun add(day: String, audio: Boolean, kind: ZenKind?, ms: Long) {
        if (ms <= 0) return
        val d = get(day)
        val n = when (kind) {
            ZenKind.PRAXIS -> d.copy(praxisMs = d.praxisMs + ms)
            ZenKind.MUSIC -> d.copy(musicMs = d.musicMs + ms)
            else -> if (audio) d.copy(soundMs = d.soundMs + ms) else d.copy(pictureMs = d.pictureMs + ms)
        }
        val days = (days() + day).distinct().sorted()
        val drop = days.dropLast(KEEP_DAYS)
        val kept = days.takeLast(KEEP_DAYS)
        store.put(
            *(drop.map { key(it) to null } + listOf(key(day) to n.encode(), K_DAYS to kept.joinToString(","))).toTypedArray()
        )
    }

    @Synchronized
    fun get(day: String): Day = Day.decode(store.getString(key(day), null))

    private fun days(): List<String> = store.getString(K_DAYS, "").orEmpty().split(',').filter { it.isNotEmpty() }

    companion object {
        const val KEEP_DAYS = 30

        /** Spec 2.4 (recommended a): sound counts half towards a later daily limit. Fixed, no setting. */
        const val AUDIO_WEIGHT_PERCENT = 50

        private const val K_DAYS = "wt_days"

        fun key(day: String) = "wt_$day"
    }
}
