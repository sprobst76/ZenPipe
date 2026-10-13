/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.util.Log

/**
 * Detail requests (description, tags, category via StreamInfo) for the content recognition, shared
 * by the plausibility check and the feed detail run (spec 2.5): at most one request per
 * [ZenPlausibilityCheck.REQUEST_INTERVAL_MS] (retries included); a failure is classified by
 * [ZenPlausibilityCheck.failKind]: permanent (not available, members only) = no retry, transient =
 * up to [ZenPlausibilityCheck.RETRY_BACKOFF_MS] retries, blocked (bot check, 429) = no further
 * request through this instance. One instance per run; not thread-safe.
 *
 * [load] performs one request (blocking), [elapsed]/[sleep] are injectable for tests.
 */
class ZenDetailRequests(
    private val tag: String,
    private val load: (serviceId: Int, url: String) -> VideoText,
    private val elapsed: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) }
) {
    var requests = 0
        private set
    var retries = 0
        private set

    /** A bot check / rate limit happened: nothing more is requested. */
    var blocked = false
        private set

    /** Kind of the last failed [fetch] (null after a success). */
    var lastFail: ZenPlausibilityCheck.FailKind? = null
        private set

    /** Failed videos by innermost cause ("PaidContentException" -> 3), plus "blocked". */
    val errors = LinkedHashMap<String, Int>()

    private var lastRequest = Long.MIN_VALUE / 2

    private fun throttle(minWait: Long) {
        val now = elapsed()
        val wait = maxOf(lastRequest + ZenPlausibilityCheck.REQUEST_INTERVAL_MS, now + minWait) - now
        if (wait > 0) sleep(wait)
        lastRequest = elapsed()
    }

    /**
     * Loads the details of one video; null when it failed (then it is judged by title + channel),
     * when blocked or when [stopped] became true before a request.
     */
    fun fetch(serviceId: Int, url: String, stopped: () -> Boolean): VideoText? {
        var attempt = 0
        while (!blocked && !stopped()) {
            throttle(if (attempt == 0) 0 else ZenPlausibilityCheck.RETRY_BACKOFF_MS[attempt - 1])
            if (stopped()) return null
            requests++
            if (attempt > 0) retries++
            try {
                return load(serviceId, url).also { lastFail = null }
            } catch (e: Exception) {
                val kind = ZenPlausibilityCheck.failKind(e)
                lastFail = kind
                try {
                    Log.w(
                        tag,
                        "details failed (${kind.name.lowercase()}, attempt ${attempt + 1}) for $url: " +
                            generateSequence(e as Throwable) { it.cause }.take(10)
                                .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message?.take(120)}" }
                    )
                } catch (ignored: RuntimeException) {
                    // android.util.Log is not mocked in JVM tests
                }
                if (kind == ZenPlausibilityCheck.FailKind.BLOCKED) {
                    blocked = true
                    errors["blocked"] = (errors["blocked"] ?: 0) + 1
                }
                if (kind != ZenPlausibilityCheck.FailKind.TRANSIENT || attempt >= ZenPlausibilityCheck.RETRY_BACKOFF_MS.size) {
                    val name = ZenPlausibilityCheck.rootName(e)
                    errors[name] = (errors[name] ?: 0) + 1
                    return null
                }
                attempt++
            }
        }
        return null
    }

    /** "requests=15 retries=1 errors=IOException:2" for the ZenLog. */
    fun logDetail(): String = "requests=$requests retries=$retries" +
        if (errors.isEmpty()) "" else " errors=" + errors.entries.joinToString(",") { "${it.key}:${it.value}" }
}
