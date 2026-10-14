/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.zen.Zen
import org.schabi.newpipe.zen.ZenPraxisChannel

/**
 * Runs the plausibility check before a channel/playlist is marked as practice (spec 4.4): loads
 * its newest [ZenPlausibility.SAMPLE] videos (one list request), then their details one by one,
 * throttled to [REQUEST_INTERVAL_MS] (cached details of the same model are reused, no request),
 * classifies them with the full text and shows the result. It never blocks: "Mark anyway" and
 * every other path lead into the unchanged marking flow; without a model there is only a note.
 */
object ZenPlausibilityCheck {
    /** Spec 2.5: at most one detail request per second, abortable. */
    const val REQUEST_INTERVAL_MS = 1_000L

    private const val TAG = "ZenPlausibility"

    /** Retries of a failed detail request (transient errors only), waiting [RETRY_BACKOFF_MS] before each. */
    val RETRY_BACKOFF_MS = longArrayOf(2_000L, 5_000L)

    /**
     * One checked video with its cache entry (null = could not be classified); [detailsFailed] =
     * its details could not be loaded, it was judged by title + channel only.
     */
    data class Checked(val title: String, val entry: ClassEntry?, val detailsFailed: Boolean = false)

    /** Result of [sample]: the checked videos, failed detail requests and their error kinds. */
    data class Sample(val checked: List<Checked>, val requests: Int, val retries: Int, val errors: Map<String, Int>) {
        val failed: Int get() = checked.count { it.detailsFailed }
    }

    /** What a failed detail request means for the next ones. */
    enum class FailKind {
        /** Content not available (private, age, region, …): no retry. */
        PERMANENT,

        /** Network, parsing, server: retry with back-off. */
        TRANSIENT,

        /** Bot check / rate limit: no retry and no further detail requests in this check. */
        BLOCKED
    }

    /** Classifies a failure by the cause chain (RxJava wraps checked exceptions in RuntimeException). */
    @JvmStatic
    fun failKind(e: Throwable): FailKind {
        val chain = generateSequence(e) { it.cause }.take(10).toList()
        return when {
            chain.any {
                it is org.schabi.newpipe.extractor.exceptions.ReCaptchaException ||
                    it is org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException ||
                    (it is org.schabi.newpipe.extractor.exceptions.HttpResponseException && it.message?.contains("429") == true)
            } -> FailKind.BLOCKED

            chain.any { it is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException } -> FailKind.PERMANENT

            else -> FailKind.TRANSIENT
        }
    }

    /** Innermost cause, for logs ("SocketTimeoutException"). */
    @JvmStatic
    fun rootName(e: Throwable): String = generateSequence(e) { it.cause }.take(10).last().javaClass.simpleName

    /**
     * Checks [channel] and then calls [proceed] with a one-line note for the confirmation dialog
     * (null = nothing to add). Not called when the user cancels. A [presampled] result (channel
     * import sheet) is evaluated directly, without loading anything again.
     */
    @JvmStatic
    @JvmOverloads
    fun run(activity: Activity, channel: ZenPraxisChannel, presampled: Sample? = null, proceed: (String?) -> Unit) {
        if (!ZenClassifierModel.isReady(activity)) {
            proceed(activity.getString(R.string.zen_check_unavailable))
            return
        }
        if (presampled != null && presampled.checked.isNotEmpty()) {
            show(activity, channel, presampled, proceed)
            return
        }
        val progress = TextView(activity).apply {
            text = activity.getString(R.string.zen_check_loading_list)
            val p = (20 * activity.resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.zen_check_title)
            .setView(progress)
            .setCancelable(false)
            .setPositiveButton(R.string.zen_check_skip) { _, _ ->
                stop.set(true)
                Zen.log(activity).add("class_check_skipped", "url=${channel.url}")
                proceed(activity.getString(R.string.zen_check_skipped))
            }
            .setNegativeButton(R.string.cancel) { _, _ -> stop.set(true) }
            .show()
        val app = activity.applicationContext
        Thread {
            val sample = try {
                sample(app, channel, { stop.get() }) { done, total ->
                    activity.runOnUiThread { progress.text = activity.getString(R.string.zen_check_progress, done, total) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "check failed", e)
                null
            }
            activity.runOnUiThread {
                if (stop.get() || activity.isFinishing) return@runOnUiThread
                dialog.dismiss()
                if (sample == null || sample.checked.isEmpty()) {
                    Zen.log(app).add("class_check", "kind=PRAXIS url=${channel.url} result=failed")
                    proceed(activity.getString(R.string.zen_check_failed))
                } else {
                    show(activity, channel, sample, proceed)
                }
            }
        }.start()
    }

    /**
     * Loads and classifies the newest videos of [ch]; blocking (background thread). [onProgress]
     * gets (done, total). Detail requests are throttled to one per [REQUEST_INTERVAL_MS] (retries
     * included); details already in the cache (level D, same model) cost no request. A failed
     * request is retried after [RETRY_BACKOFF_MS] unless it is permanent; a bot check / rate limit
     * stops all further detail requests of this check. Failed videos are judged by title + channel.
     */
    @JvmStatic
    fun sample(
        app: android.content.Context,
        ch: ZenPraxisChannel,
        stopped: () -> Boolean,
        onProgress: (Int, Int) -> Unit
    ): Sample {
        val items = newest(ch)
        val cache = ZenClassCache.get(app)
        val classifier = ZenClassifier.get(app)
        val out = ArrayList<Checked>()
        val req = ZenDetailRequests(TAG, { serviceId, url -> loadText(serviceId, url) })
        val t0 = SystemClock.elapsedRealtime()
        for ((i, item) in items.withIndex()) {
            if (stopped()) break
            onProgress(i + 1, items.size)
            val key = ZenClassCache.keyOf(item.serviceId, item.url)
            val cached = cache.get(key)?.takeIf { it.level == "D" && it.model == ZenClassifierModel.VERSION }
            if (cached != null) {
                out.add(Checked(item.name, cached))
                continue
            }
            val text = req.fetch(item.serviceId, item.url, stopped)
            if (stopped()) break
            // no network / blocked / not available: title + channel only (marked as such)
            val failed = text == null
            val entry = classifier.classifyBlocking(key, text ?: VideoText(item.name, item.uploaderName ?: ch.name))
            out.add(Checked(item.name, entry, failed))
        }
        Log.d(
            TAG,
            "checked ${out.size} videos, ${req.requests} detail requests (${req.retries} retries), " +
                "${out.count { it.detailsFailed }} failed ${req.errors}, ${SystemClock.elapsedRealtime() - t0} ms"
        )
        return Sample(out, req.requests, req.retries, LinkedHashMap(req.errors))
    }

    /** One detail request: the video's StreamInfo as classifier text (level D). */
    @JvmStatic
    fun loadText(serviceId: Int, url: String): VideoText = ZenClassifier.textOf(ExtractorHelper.getStreamInfo(serviceId, url, false).blockingGet())

    /** Newest videos of a channel (videos tab) or the first ones of a playlist, without shorts/live. */
    private fun newest(ch: ZenPraxisChannel): List<StreamInfoItem> {
        val related = if (ch.playlist) {
            ExtractorHelper.getPlaylistInfo(ch.serviceId, ch.url, false).blockingGet().relatedItems
        } else {
            val info = ExtractorHelper.getChannelInfo(ch.serviceId, ch.url, false).blockingGet()
            val tab = info.tabs.firstOrNull { it.contentFilters.firstOrNull() == ChannelTabs.VIDEOS } ?: return emptyList()
            ExtractorHelper.getChannelTab(ch.serviceId, tab, false).blockingGet().relatedItems
        }
        return related.filterIsInstance<StreamInfoItem>()
            .filter { !it.isShortFormContent && it.streamType != StreamType.LIVE_STREAM && it.streamType != StreamType.AUDIO_LIVE_STREAM }
            .take(ZenPlausibility.SAMPLE)
    }

    /** "n of m videos could not be loaded …" or null. */
    @JvmStatic
    fun failedNote(context: android.content.Context, sample: Sample): String? {
        if (sample.failed == 0) return null
        // members-only videos are no error (PaidContentException): say so instead of "not loadable"
        val members = minOf(sample.errors[MEMBERS_ONLY] ?: 0, sample.failed)
        val other = sample.failed - members
        return listOfNotNull(
            if (members > 0) context.getString(R.string.zen_check_members_only, members, sample.checked.size) else null,
            if (other > 0) context.getString(R.string.zen_check_level_t, other, sample.checked.size) else null
        ).joinToString("\n")
    }

    private const val MEMBERS_ONLY = "PaidContentException"

    /** ZenLog detail of the detail requests ("requests=15 retries=1 failed=2 errors=IOException:2"). */
    @JvmStatic
    fun logRequests(sample: Sample): String = "requests=${sample.requests} retries=${sample.retries} failed=${sample.failed}" +
        if (sample.errors.isEmpty()) "" else " errors=" + sample.errors.entries.joinToString(",") { "${it.key}:${it.value}" }

    /**
     * The evaluated check without any dialog: [note] = the line for the confirmation (also after
     * "mark anyway"), [warning] = the warning text for WARN/STRONG_WARN (null otherwise),
     * [verdict] = null when there was nothing to evaluate (no model, failed, skipped).
     */
    data class Assessment(val verdict: ZenPlausibility.Verdict?, val note: String, val warning: String?)

    /**
     * Evaluates [sample] for [channel] ([skipped] = the user skipped the check); logs `class_check`
     * unless [log] is false (the texts are in the language of [context]).
     */
    @JvmStatic
    @JvmOverloads
    fun assess(context: android.content.Context, channel: ZenPraxisChannel, sample: Sample?, skipped: Boolean = false, log: Boolean = true): Assessment {
        if (!ZenClassifierModel.isReady(context)) return Assessment(null, context.getString(R.string.zen_check_unavailable), null)
        if (skipped) return Assessment(null, context.getString(R.string.zen_check_skipped), null)
        if (sample == null || sample.checked.isEmpty()) return Assessment(null, context.getString(R.string.zen_check_failed), null)
        val v = ZenPlausibility.evaluate(sample.checked.map { ZenPlausibility.Item(it.title, it.entry?.cats, it.entry?.level) })
        if (log) Zen.log(context).add("class_check", "url=${channel.url} " + ZenPlausibility.logDetail(v, channel.playlist) + " " + logRequests(sample))
        val failedNote = failedNote(context, sample)
        fun withFailed(s: String) = if (failedNote == null) s else "$s\n$failedNote"
        return when (v.result) {
            ZenPlausibility.Result.FITS -> Assessment(v, withFailed(context.getString(R.string.zen_check_fits, v.hit, v.n)), null)

            ZenPlausibility.Result.UNCLEAR -> Assessment(v, withFailed(context.getString(R.string.zen_check_unclear, v.none, v.n)), null)

            ZenPlausibility.Result.WARN, ZenPlausibility.Result.STRONG_WARN -> {
                val msg = StringBuilder()
                if (v.result == ZenPlausibility.Result.STRONG_WARN) msg.append(context.getString(R.string.zen_check_strong)).append("\n\n")
                msg.append(
                    if (v.example != null && v.distraction > 0) {
                        context.getString(R.string.zen_check_warn, v.hit, v.n, channel.name, v.distraction, v.example)
                    } else {
                        context.getString(R.string.zen_check_warn_no_example, v.hit, v.n, channel.name)
                    }
                )
                if (failedNote != null) msg.append("\n\n").append(failedNote)
                Assessment(v, context.getString(R.string.zen_check_warned, v.hit, v.n), msg.toString())
            }
        }
    }

    /** "Mark anyway" after a warning (dialog or channel import sheet). */
    @JvmStatic
    fun logOverride(context: android.content.Context, channel: ZenPraxisChannel, v: ZenPlausibility.Verdict) = Zen.log(context).add("class_check_override", "url=${channel.url} hit=${v.hit} n=${v.n} result=${v.result.name.lowercase()}")

    private fun show(activity: Activity, channel: ZenPraxisChannel, sample: Sample, proceed: (String?) -> Unit) {
        val a = assess(activity, channel, sample)
        val v = a.verdict
        if (a.warning == null || v == null) proceed(a.note) else warn(activity, channel, sample.checked, v, a.warning, proceed)
    }

    private fun warn(
        activity: Activity,
        channel: ZenPraxisChannel,
        checked: List<Checked>,
        v: ZenPlausibility.Verdict,
        message: String,
        proceed: (String?) -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_check_title)
            .setMessage(message)
            .setPositiveButton(R.string.zen_check_override) { _, _ ->
                logOverride(activity, channel, v)
                proceed(activity.getString(R.string.zen_check_warned, v.hit, v.n))
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.zen_check_videos) { _, _ ->
                showList(activity, checked) { warn(activity, channel, checked, v, message, proceed) }
            }
            .show()
    }

    /** List of the checked videos with their classification ("why"); back to the warning afterwards. */
    @JvmStatic
    fun showList(activity: Activity, checked: List<Checked>, back: () -> Unit) {
        val body = checked.joinToString("\n\n") { c ->
            val e = c.entry
            if (e == null) {
                "${c.title}\n${activity.getString(R.string.zen_check_list_failed)}"
            } else {
                val top = ZenClassWhy.topLine(activity, e.top)
                "${c.title}\n${ZenClassWhy.summary(activity, e.cats, e.tone, e.level, e.top)}" +
                    (if (c.detailsFailed) " · " + activity.getString(R.string.zen_check_list_not_loaded) else "") + "\n$top"
            }
        }
        val tv = TextView(activity).apply {
            text = body
            textSize = 13f
            setTextIsSelectable(true)
            val p = (20 * activity.resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_check_list_title)
            .setView(ScrollView(activity).apply { addView(tv) })
            .setPositiveButton(android.R.string.ok) { _, _ -> back() }
            .setOnCancelListener { back() }
            .show()
    }
}
