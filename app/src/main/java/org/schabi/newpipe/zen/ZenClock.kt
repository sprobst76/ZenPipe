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
    private val log: ((String, String) -> Unit)? = null,
    /** Reboot counter of the device (Settings.Global.BOOT_COUNT), -1 if unknown. */
    private val bootId: () -> Int = { -1 }
) {
    /**
     * @param rebooted the device was restarted since the previous reading. A forward clock
     *  change across a reboot cannot be told from a long power-off, so everything that must not
     *  be shortened by a clock trick (pause, lock) counts down with [ZenCountdown] instead.
     */
    data class Reading(val trustedMillis: Long, val tampered: Boolean, val rebooted: Boolean = false)

    @Synchronized
    fun now(): Reading {
        val wall = wallClock()
        val mono = monotonic()
        val anchorWall = store.getLong(KEY_ANCHOR_WALL, -1)
        val anchorMono = store.getLong(KEY_ANCHOR_MONO, -1)
        val anchorBoot = store.getInt(KEY_ANCHOR_BOOT, -1)
        val boot = bootId()

        if (anchorWall < 0 || anchorMono < 0) {
            setAnchor(wall, mono, boot)
            return Reading(wall, false)
        }

        // The boot counter is authoritative: after a restart the uptime may already be larger than
        // the stored one, which must not be mistaken for "same boot, clock changed".
        val rebooted = if (boot >= 0 && anchorBoot >= 0) boot != anchorBoot else mono < anchorMono
        val trusted: Long
        var tampered = false
        if (!rebooted) {
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
                if (wall - anchorWall > REBOOT_GAP_LOG_MS) {
                    log?.invoke("clock_reboot_gap", "gapMs=${wall - anchorWall}")
                }
            }
        }
        if (tampered) {
            log?.invoke("clock_tamper", "wall=$wall trusted=$trusted")
        }
        // re-anchor with the trusted value so a manipulated wall time never becomes the baseline
        setAnchor(trusted, mono, boot)
        return Reading(trusted, tampered, rebooted)
    }

    private fun setAnchor(wall: Long, mono: Long, boot: Int) {
        store.put(KEY_ANCHOR_WALL to wall, KEY_ANCHOR_MONO to mono, KEY_ANCHOR_BOOT to boot)
    }

    companion object {
        const val TOLERANCE_MS = 2 * 60 * 1000L
        private const val KEY_ANCHOR_WALL = "clock_anchor_wall"
        private const val KEY_ANCHOR_MONO = "clock_anchor_mono"
        private const val KEY_ANCHOR_BOOT = "clock_anchor_boot"

        /** Forward gaps across a reboot above this are written to the log (visibility only). */
        const val REBOOT_GAP_LOG_MS = 12 * 60 * 60 * 1000L
    }
}

/**
 * A persisted countdown that does not depend on the wall clock at all: it only shrinks with the
 * device uptime (monotonic clock, includes deep sleep). Within one boot it subtracts the monotonic
 * time since the last look; after a reboot it subtracts only the uptime of the new boot, so the
 * time the device was switched off does not count (fail-closed). Setting the clock forward and
 * restarting therefore never shortens it. Used for the mandatory pause (and later the settings lock).
 */
class ZenCountdown(
    private val store: ZenKeyValueStore,
    private val name: String,
    private val monotonic: () -> Long = SystemClock::elapsedRealtime,
    private val bootId: () -> Int = { -1 }
) {
    private val kRemaining = "cd_${name}_rem"
    private val kMono = "cd_${name}_mono"
    private val kBoot = "cd_${name}_boot"

    /** Start (or restart) with [durationMs] remaining from now. */
    @Synchronized
    fun start(durationMs: Long) {
        store.put(kRemaining to durationMs, kMono to monotonic(), kBoot to bootId())
    }

    @Synchronized
    fun clear() {
        store.put(kRemaining to null, kMono to null, kBoot to null)
    }

    /** Remaining milliseconds (0 when none is running); also persists the progress. */
    @Synchronized
    fun remainingMs(): Long {
        val rem = store.getLong(kRemaining, -1)
        if (rem < 0) return 0
        val refMono = store.getLong(kMono, 0)
        val refBoot = store.getInt(kBoot, -1)
        val mono = monotonic()
        val boot = bootId()
        val sameBoot = if (boot >= 0 && refBoot >= 0) boot == refBoot else mono >= refMono
        val elapsed = if (sameBoot) mono - refMono else mono
        val left = (rem - maxOf(0L, elapsed)).coerceAtLeast(0)
        if (left == 0L) {
            clear()
        } else {
            store.put(kRemaining to left, kMono to mono, kBoot to boot)
        }
        return left
    }

    fun isRunning() = remainingMs() > 0
}
