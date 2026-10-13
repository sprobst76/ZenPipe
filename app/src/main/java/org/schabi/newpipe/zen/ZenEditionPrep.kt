/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Prepares a due feed edition while the content recognition is in use: before the edition is
 * built, the eligible candidates without a classification are classified (best base score first)
 * in the background, until all are done or [LIMIT_MS] has passed. Meanwhile the feed keeps
 * showing the running edition (it is never changed) with a "being prepared" note; then the
 * edition is built once, with the time the preparation started as its creation time, so the
 * hourly rhythm does not move.
 *
 * Process-wide, shared by all feed views. The time source and the work runner are injectable
 * for tests.
 */
class ZenEditionPrep(
    private val elapsed: () -> Long,
    private val runner: (Runnable) -> Unit = { Thread(it, "ZenEditionPrep").apply { isDaemon = true }.start() }
) {
    /** One video to classify: [classify] returns false when classification failed (stop). */
    data class Job(val key: String, val score: Int, val classify: (timeoutMs: Long) -> Boolean)

    enum class Phase { IDLE, RUNNING, FINISHED }

    /** What the feed should do now. */
    sealed class Decision {
        /** Build the edition now; [createdAt] = trusted time to use as its creation time. */
        data class Build(val createdAt: Long?) : Decision()

        /** Keep the running edition and show the progress. */
        data class Wait(val done: Int, val total: Int) : Decision()
    }

    private var phase = Phase.IDLE
    private var startTrusted = 0L
    private var finishedElapsed = 0L
    private var total = 0

    @Volatile private var done = 0

    private val listeners = CopyOnWriteArraySet<Runnable>()

    /** Called (on the preparation thread) on progress and when the preparation ends. */
    fun addListener(l: Runnable) {
        listeners.add(l)
    }

    fun removeListener(l: Runnable) {
        listeners.remove(l)
    }

    @get:Synchronized
    val currentPhase: Phase get() = phase

    /**
     * Decides for a due edition. [missing] = eligible candidates without a classification (only
     * evaluated when no preparation is running), [nowTrusted] = trusted wall time.
     */
    @Synchronized
    fun decide(nowTrusted: Long, missing: () -> List<Job>): Decision {
        when (phase) {
            Phase.RUNNING -> return Decision.Wait(done, total)

            Phase.FINISHED -> {
                val fresh = elapsed() - finishedElapsed <= STALE_MS
                val at = startTrusted
                phase = Phase.IDLE
                // a preparation nobody picked up for long is outdated (new feed data): start again
                if (fresh) return Decision.Build(at)
            }

            Phase.IDLE -> {}
        }
        val jobs = missing()
        if (jobs.isEmpty()) return Decision.Build(null)
        phase = Phase.RUNNING
        startTrusted = nowTrusted
        total = jobs.size
        done = 0
        val ordered = jobs.sortedByDescending { it.score }
        runner(Runnable { work(ordered) })
        return Decision.Wait(0, total)
    }

    /** Statistics of the last finished run, for the log. */
    @Volatile var lastRun: String = ""
        private set

    private fun work(jobs: List<Job>) {
        val t0 = elapsed()
        val deadline = t0 + LIMIT_MS
        var reason = "complete"
        try {
            for ((i, j) in jobs.withIndex()) {
                val left = deadline - elapsed()
                if (left <= 0) {
                    reason = "limit"
                    break
                }
                if (!j.classify(left)) {
                    reason = if (deadline - elapsed() <= 0) "limit" else "error"
                    break
                }
                done = i + 1
                if (done % PROGRESS_EVERY == 0) listeners.forEach { it.run() }
            }
        } catch (e: Exception) {
            reason = "error"
        }
        lastRun = "missing=${jobs.size} done=$done ms=${elapsed() - t0} reason=$reason"
        synchronized(this) {
            phase = Phase.FINISHED
            finishedElapsed = elapsed()
        }
        listeners.forEach { it.run() }
    }

    companion object {
        /** At most this long before the edition is built anyway (the rest stays "not classified"). */
        const val LIMIT_MS = 20_000L

        /** A finished preparation older than this is not used (the next due view starts again). */
        const val STALE_MS = 60_000L

        /** Progress callback every n videos. */
        const val PROGRESS_EVERY = 10

        @JvmStatic
        val shared: ZenEditionPrep by lazy { ZenEditionPrep({ android.os.SystemClock.elapsedRealtime() }) }
    }
}
