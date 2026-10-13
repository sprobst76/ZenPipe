/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.content.Context
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.zen.Zen
import org.schabi.newpipe.zen.ZenDay
import org.schabi.newpipe.zen.ZenKeyValueStore
import org.schabi.newpipe.zen.ZenSettings
import org.schabi.newpipe.zen.ZenZone

/**
 * After a feed edition was built, loads the details (description, tags, category) of its picks
 * that were classified from title + channel only (level T) and classifies them again with the full
 * text (level D). The edition itself never changes (order, selection, weights used): only the
 * stored "why" of the picks is replaced, so the feed shows the more precise classification.
 *
 * Network (spec 2.5, no mass loading): only on an unmetered network, one request per second,
 * at most [MAX_REQUESTS_PER_DAY] requests per Zen day (retries included), failures as in the
 * plausibility check ([ZenDetailRequests]); a bot check / rate limit stops the run and all further
 * runs of this Zen day. Turning the content recognition off stops a running run.
 */
class ZenFeedDetails(private val runner: (Runnable) -> Unit = { Thread(it, "ZenFeedDetails").apply { isDaemon = true }.start() }) {
    /** One pick to refine: [whyFor] builds its new stored "why" from the level-D entry. */
    data class Job(val key: String, val serviceId: Int, val url: String, val fallback: VideoText, val whyFor: (ClassEntry) -> String?)

    /** Result of [process]: new "why" per url and counters for the log. */
    data class Outcome(val whys: Map<String, String>, val ok: Int, val cached: Int, val failed: Int, val reason: String)

    private val running = AtomicBoolean(false)
    private val listeners = CopyOnWriteArraySet<Runnable>()

    /** Called (on the worker thread) after a run changed the stored "why" of an edition. */
    fun addListener(l: Runnable) {
        listeners.add(l)
    }

    fun removeListener(l: Runnable) {
        listeners.remove(l)
    }

    val isRunning: Boolean get() = running.get()

    /** Keys whose details are permanently unavailable (members only, private …), not requested again in this process. */
    private val permanent = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Starts a run for the picks of a freshly built edition (no-op without jobs or while a run is
     * active). [apply] stores the new "why" values (only if the edition is still the current one).
     */
    fun start(context: Context, jobs: List<Job>, apply: (Map<String, String>) -> Unit) {
        if (jobs.isEmpty()) return
        val app = context.applicationContext
        if (!running.compareAndSet(false, true)) {
            log(app, "run=${jobs.size} reason=busy")
            return
        }
        runner(
            Runnable {
                try {
                    runNow(app, jobs, apply)
                } catch (e: Exception) {
                    Log.w(TAG, "feed detail run failed", e)
                    log(app, "run=${jobs.size} reason=error ${e.javaClass.simpleName}")
                } finally {
                    running.set(false)
                }
            }
        )
    }

    private fun runNow(app: Context, jobs: List<Job>, apply: (Map<String, String>) -> Unit) {
        val store = Zen.store(app)
        val day = ZenDay.dayOf(Zen.clock(app).now().trustedMillis, ZenZone.fixed(store)).toString()
        val budget = Budget(store, day)
        val skip = when {
            !active(app) -> "off"
            budget.blockedToday() -> "blocked_today"
            budget.left() <= 0 -> "budget"
            !unmetered(app) -> "metered"
            else -> null
        }
        if (skip != null) {
            log(app, "run=${jobs.size} ok=0 failed=0 reason=$skip")
            return
        }
        val t0 = android.os.SystemClock.elapsedRealtime()
        val cache = ZenClassCache.get(app)
        val classifier = ZenClassifier.get(app)
        val req = ZenDetailRequests(TAG, { serviceId, url -> ZenPlausibilityCheck.loadText(serviceId, url) })
        val left = budget.left()
        val out = process(
            jobs.filter { it.key !in permanent },
            req,
            cachedD = { j -> cache.get(j.key)?.takeIf { it.level == "D" && it.model == ZenClassifierModel.VERSION } },
            classify = { j, text -> classifier.classifyBlocking(j.key, text) },
            off = { !active(app) },
            budgetLeft = { left - req.requests }
        )
        budget.use(req.requests)
        if (req.blocked) budget.blockToday()
        if (out.whys.isNotEmpty()) {
            apply(out.whys)
            listeners.forEach { it.run() }
        }
        log(
            app,
            "run=${jobs.size} ok=${out.ok} cached=${out.cached} failed=${out.failed} " +
                "${req.logDetail()} ms=${android.os.SystemClock.elapsedRealtime() - t0} reason=${out.reason}"
        )
    }

    /** The content recognition is in use: one of its switches on and a working model. */
    private fun active(app: Context) = (ZenSettings.classFeedWeighting() || ZenSettings.classHeavyLimit()) && ZenClassifierModel.isReady(app)

    private fun unmetered(app: Context): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false

        @Suppress("DEPRECATION")
        val connected = cm.activeNetworkInfo?.isConnected == true
        return connected && !androidx.core.net.ConnectivityManagerCompat.isActiveNetworkMetered(cm)
    }

    /**
     * The run itself (testable): for each job a level-D cache entry is reused without a request;
     * otherwise the details are requested ([req], throttled) and classified. A failed request keeps
     * level T (no new "why"). Stops when [off], when the request budget is used up or when blocked.
     */
    fun process(
        jobs: List<Job>,
        req: ZenDetailRequests,
        cachedD: (Job) -> ClassEntry?,
        classify: (Job, VideoText) -> ClassEntry?,
        off: () -> Boolean,
        budgetLeft: () -> Int
    ): Outcome {
        val whys = LinkedHashMap<String, String>()
        var ok = 0
        var cached = 0
        var failed = 0
        var reason = "complete"
        for (j in jobs) {
            if (off()) {
                reason = "off"
                break
            }
            val have = cachedD(j)
            if (have != null) {
                j.whyFor(have)?.let { whys[j.url] = it }
                cached++
                continue
            }
            if (req.blocked) {
                reason = "blocked"
                break
            }
            if (budgetLeft() <= 0) {
                reason = "budget"
                break
            }
            val text = req.fetch(j.serviceId, j.url) { off() || budgetLeft() <= 0 }
            if (text == null) {
                if (off()) {
                    reason = "off"
                    break
                }
                failed++
                if (req.lastFail == ZenPlausibilityCheck.FailKind.PERMANENT) permanent.add(j.key)
                if (req.blocked) {
                    reason = "blocked"
                    break
                }
                continue
            }
            val e = classify(j, text)
            if (e != null && e.level == "D") {
                j.whyFor(e)?.let { whys[j.url] = it }
                ok++
            } else {
                failed++
            }
        }
        return Outcome(whys, ok, cached, failed, reason)
    }

    /** Requests per Zen day and the "blocked today" mark, in `zen_state`. */
    private class Budget(private val store: ZenKeyValueStore, private val day: String) {
        fun left(): Int = MAX_REQUESTS_PER_DAY - if (store.getString(K_DAY, null) == day) store.getInt(K_COUNT, 0) else 0

        fun use(n: Int) {
            if (n <= 0) return
            store.put(K_DAY to day, K_COUNT to (MAX_REQUESTS_PER_DAY - left()) + n)
        }

        fun blockedToday() = store.getString(K_BLOCK_DAY, null) == day

        fun blockToday() = store.put(K_BLOCK_DAY to day)
    }

    companion object {
        private const val TAG = "ZenFeedDetails"

        /** Spec 2.5 ("max. 30 per day"): two full editions. */
        const val MAX_REQUESTS_PER_DAY = 30

        private const val K_DAY = "cfd_day"
        private const val K_COUNT = "cfd_requests"
        private const val K_BLOCK_DAY = "cfd_block_day"

        @JvmStatic
        val shared: ZenFeedDetails by lazy { ZenFeedDetails() }

        private fun log(app: Context, detail: String) = Zen.log(app).add("class_feed_detail", detail)
    }
}
