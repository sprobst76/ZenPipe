package org.schabi.newpipe.zen

import android.os.SystemClock

/**
 * Trusted time for time-locks. Compares the wall clock with the monotonic clock
 * (elapsedRealtime) against an anchor stored in the Zen preferences:
 *  - Same boot: wall time must advance like the monotonic clock. A deviation beyond
 *    [TOLERANCE_MS] means the clock was changed; the extrapolated time is used instead.
 *  - After a reboot the monotonic clock restarts and cannot be compared; then the wall clock
 *    is only rejected when it is earlier than the last known trusted time (set back).
 *    A forward jump across a reboot cannot be detected.
 */
class ZenClock(
    private val store: ZenKeyValueStore,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val monotonic: () -> Long = SystemClock::elapsedRealtime,
    private val log: ((String, String) -> Unit)? = null
) {
    data class Reading(val trustedMillis: Long, val tampered: Boolean)

    @Synchronized
    fun now(): Reading {
        val wall = wallClock()
        val mono = monotonic()
        val anchorWall = store.getLong(KEY_ANCHOR_WALL, -1)
        val anchorMono = store.getLong(KEY_ANCHOR_MONO, -1)

        if (anchorWall < 0 || anchorMono < 0) {
            setAnchor(wall, mono)
            return Reading(wall, false)
        }

        val trusted: Long
        var tampered = false
        if (mono >= anchorMono) {
            val expected = anchorWall + (mono - anchorMono)
            if (Math.abs(wall - expected) > TOLERANCE_MS) {
                tampered = true
                trusted = expected
            } else {
                trusted = wall
            }
        } else {
            // reboot: monotonic clock restarted
            if (wall < anchorWall - TOLERANCE_MS) {
                tampered = true
                trusted = anchorWall
            } else {
                trusted = wall
            }
        }
        if (tampered) {
            log?.invoke("clock_tamper", "wall=$wall trusted=$trusted")
        }
        // re-anchor with the trusted value so a manipulated wall time never becomes the baseline
        setAnchor(trusted, mono)
        return Reading(trusted, tampered)
    }

    private fun setAnchor(wall: Long, mono: Long) {
        store.put(KEY_ANCHOR_WALL to wall, KEY_ANCHOR_MONO to mono)
    }

    companion object {
        const val TOLERANCE_MS = 2 * 60 * 1000L
        private const val KEY_ANCHOR_WALL = "clock_anchor_wall"
        private const val KEY_ANCHOR_MONO = "clock_anchor_mono"
    }
}
