/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen

import android.content.Context
import android.util.Log
import java.text.NumberFormat
import org.schabi.newpipe.R
import org.schabi.newpipe.zen.classify.ClassStore
import org.schabi.newpipe.zen.classify.LangEntry
import org.schabi.newpipe.zen.classify.ZenClassCache

/**
 * Feed language filter (setting "Languages in the feed"): detects the language of feed candidates
 * ([ZenLangDetect], stored in `zen_class_cache.db`, table `lang`) and decides with the channel's
 * recent videos ([ZenLangFilter]) whether a video is offered. Subscriptions, search and channel
 * pages are not affected; nothing is deleted.
 */
object ZenFeedLanguages {
    /** Videos per channel that form its majority language. */
    const val CHANNEL_HISTORY = 20

    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    data class Item(val key: String, val channel: String, val title: String, val channelName: String)

    data class Result(val entry: LangEntry, val decision: ZenLangFilter.Decision)

    @Volatile
    private var lastPurge = 0L

    /**
     * Language and decision for each feed candidate: stored entries are reused (a language from the
     * video details is never replaced by the title heuristic), missing ones are detected and stored.
     */
    @JvmStatic
    fun annotate(store: ClassStore, items: List<Item>, allowed: Set<String>, nowMillis: Long): Map<String, Result> {
        if (items.isEmpty()) return emptyMap()
        val have = store.langs(items.map { it.key })
        val fresh = items.filter { it.key !in have }.distinctBy { it.key }.map {
            LangEntry(it.key, it.channel, it.title, it.channelName, ZenLangDetect.detect(it.title, it.channelName), nowMillis)
        }
        store.putLangs(fresh)
        val all = have + fresh.associateBy { it.key }
        return decide(store, all.values, allowed)
    }

    /** Decisions for stored entries (with the history of their channels). */
    @JvmStatic
    fun decide(store: ClassStore, entries: Collection<LangEntry>, allowed: Set<String>): Map<String, Result> {
        val history = store.langsByChannel(entries.map { it.channel }.filter { it.isNotEmpty() }, CHANNEL_HISTORY)
        return entries.associate { e ->
            val others = history[e.channel].orEmpty().filter { it.key != e.key }.map { it.guess }
            e.key to Result(e, ZenLangFilter.decide(e.guess, others, allowed))
        }
    }

    /** Feed side: annotate and purge entries older than [RETENTION_MS] once a day. */
    @JvmStatic
    fun forFeed(context: Context, items: List<Item>, nowMillis: Long): Map<String, Result> {
        val store = ZenClassCache.get(context)
        if (nowMillis - lastPurge > 24 * 60 * 60 * 1000L) {
            lastPurge = nowMillis
            store.purgeLangsBefore(nowMillis - RETENTION_MS)
        }
        return annotate(store, items, ZenSettings.feedLanguages(), nowMillis)
    }

    /**
     * The language reported with the video details ([code], e.g. original audio track or automatic
     * captions) replaces the title heuristic for this video.
     */
    @JvmStatic
    fun recordDetails(context: Context, key: String, channel: String?, title: String?, channelName: String?, code: String?) {
        val g = ZenLangDetect.fromDetails(code) ?: return
        try {
            ZenClassCache.get(context).putLangs(
                listOf(LangEntry(key, channel.orEmpty(), title.orEmpty(), channelName.orEmpty(), g, System.currentTimeMillis()))
            )
        } catch (e: Exception) {
            Log.w("ZenLanguage", "recordDetails", e)
        }
    }

    private fun num(v: Double) = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(v)

    /** "Language: Catalan 0.95 (title) · hidden in the feed" and the like. */
    @JvmStatic
    fun whyLine(context: Context, r: Result): String {
        val locale = androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: java.util.Locale.getDefault()
        val g = r.entry.guess
        val d = r.decision
        val name = g.lang?.let { ZenLangOptions.name(it, locale) }
        val src = context.getString(if (g.source == "D") R.string.zen_lang_src_details else R.string.zen_lang_src_title)
        return when (d.verdict) {
            ZenLangFilter.Verdict.UNKNOWN -> context.getString(R.string.zen_lang_why_unknown)
            ZenLangFilter.Verdict.ALLOWED -> context.getString(R.string.zen_lang_why_allowed, name, src)
            ZenLangFilter.Verdict.UNCERTAIN -> context.getString(R.string.zen_lang_why_uncertain, name, num(d.score), src)
            ZenLangFilter.Verdict.HIDDEN -> context.getString(R.string.zen_lang_why_hidden, name, num(d.score), src)
        }
    }
}
