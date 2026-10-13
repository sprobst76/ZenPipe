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

    /** One checked video with its cache entry (null = could not be classified). */
    data class Checked(val title: String, val entry: ClassEntry?)

    /**
     * Checks [channel] and then calls [proceed] with a one-line note for the confirmation dialog
     * (null = nothing to add). Not called when the user cancels.
     */
    @JvmStatic
    fun run(activity: Activity, channel: ZenPraxisChannel, proceed: (String?) -> Unit) {
        if (!ZenClassifierModel.isReady(activity)) {
            proceed(activity.getString(R.string.zen_check_unavailable))
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
            val checked = try {
                check(app, channel, { stop.get() }) { done, total ->
                    activity.runOnUiThread { progress.text = activity.getString(R.string.zen_check_progress, done, total) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "check failed", e)
                null
            }
            activity.runOnUiThread {
                if (stop.get() || activity.isFinishing) return@runOnUiThread
                dialog.dismiss()
                if (checked.isNullOrEmpty()) {
                    Zen.log(app).add("class_check", "kind=PRAXIS url=${channel.url} result=failed")
                    proceed(activity.getString(R.string.zen_check_failed))
                } else {
                    show(activity, channel, checked, proceed)
                }
            }
        }.start()
    }

    /** Loads and classifies; blocking (background thread). [onProgress] gets (done, total). */
    private fun check(
        app: android.content.Context,
        ch: ZenPraxisChannel,
        stopped: () -> Boolean,
        onProgress: (Int, Int) -> Unit
    ): List<Checked> {
        val items = newest(ch)
        val cache = ZenClassCache.get(app)
        val classifier = ZenClassifier.get(app)
        val out = ArrayList<Checked>()
        var lastRequest = 0L
        var requests = 0
        val t0 = SystemClock.elapsedRealtime()
        for ((i, item) in items.withIndex()) {
            if (stopped()) break
            onProgress(i + 1, items.size)
            val key = ZenClassCache.keyOf(item.serviceId, item.url)
            val cached = cache.get(key)?.takeIf { it.level == "D" && it.model == ZenClassifierModel.VERSION }
            val entry = cached ?: run {
                val wait = lastRequest + REQUEST_INTERVAL_MS - SystemClock.elapsedRealtime()
                if (wait > 0) Thread.sleep(wait)
                if (stopped()) return@run null
                lastRequest = SystemClock.elapsedRealtime()
                requests++
                val text = try {
                    ZenClassifier.textOf(ExtractorHelper.getStreamInfo(item.serviceId, item.url, false).blockingGet())
                } catch (e: Exception) {
                    // no network / blocked: title + channel only (marked as such)
                    Log.w(TAG, "details failed for ${item.url}: ${generateSequence(e as Throwable) { it.cause }.joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message?.take(120)}" }}")
                    VideoText(item.name, item.uploaderName ?: ch.name)
                }
                classifier.classifyBlocking(key, text)
            }
            out.add(Checked(item.name, entry))
        }
        Log.d(TAG, "checked ${out.size} videos, $requests detail requests, ${SystemClock.elapsedRealtime() - t0} ms")
        return out
    }

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

    private fun show(activity: Activity, channel: ZenPraxisChannel, checked: List<Checked>, proceed: (String?) -> Unit) {
        val v = ZenPlausibility.evaluate(checked.map { ZenPlausibility.Item(it.title, it.entry?.cats, it.entry?.level) })
        val log = Zen.log(activity)
        log.add("class_check", "url=${channel.url} " + ZenPlausibility.logDetail(v, channel.playlist))
        when (v.result) {
            ZenPlausibility.Result.FITS -> proceed(activity.getString(R.string.zen_check_fits, v.hit, v.n))

            ZenPlausibility.Result.UNCLEAR -> proceed(activity.getString(R.string.zen_check_unclear, v.none, v.n))

            ZenPlausibility.Result.WARN, ZenPlausibility.Result.STRONG_WARN -> {
                val titleOnly = checked.count { it.entry?.level == "T" }
                val msg = StringBuilder()
                if (v.result == ZenPlausibility.Result.STRONG_WARN) msg.append(activity.getString(R.string.zen_check_strong)).append("\n\n")
                msg.append(
                    if (v.example != null && v.distraction > 0) {
                        activity.getString(R.string.zen_check_warn, v.hit, v.n, channel.name, v.distraction, v.example)
                    } else {
                        activity.getString(R.string.zen_check_warn_no_example, v.hit, v.n, channel.name)
                    }
                )
                if (titleOnly > 0) msg.append("\n\n").append(activity.getString(R.string.zen_check_level_t, titleOnly))
                warn(activity, channel, checked, v, msg.toString(), proceed)
            }
        }
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
                Zen.log(activity).add("class_check_override", "url=${channel.url} hit=${v.hit} n=${v.n} result=${v.result.name.lowercase()}")
                proceed(activity.getString(R.string.zen_check_warned, v.hit, v.n))
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.zen_check_videos) { _, _ ->
                showList(activity, checked) { warn(activity, channel, checked, v, message, proceed) }
            }
            .show()
    }

    /** List of the checked videos with their classification ("why"); back to the warning afterwards. */
    private fun showList(activity: Activity, checked: List<Checked>, back: () -> Unit) {
        val body = checked.joinToString("\n\n") { c ->
            val e = c.entry
            if (e == null) {
                "${c.title}\n${activity.getString(R.string.zen_check_list_failed)}"
            } else {
                val top = e.top.joinToString(", ") { "${it.first} ${"%.2f".format(java.util.Locale.ROOT, it.second)}" }
                "${c.title}\n${ZenClassWhy.summary(activity, e.cats, e.tone, e.level)}\n$top"
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
