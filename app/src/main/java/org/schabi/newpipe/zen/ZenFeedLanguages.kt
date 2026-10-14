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

    /** [guess] = the language under the current rule (re-evaluated from the stored signals). */
    data class Result(val entry: LangEntry, val decision: ZenLangFilter.Decision, val guess: ZenLangGuess = entry.guess)

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

    /**
     * The language of a stored entry under the current rule: entries with detail signals are
     * re-evaluated from title and signals ([ZenLangDetect.combine]), without a new request.
     */
    @JvmStatic
    fun guessOf(e: LangEntry): ZenLangGuess = e.signals?.let { ZenLangDetect.combine(ZenLangDetect.detect(e.title, e.channelName), it) } ?: e.guess

    /** Decisions for stored entries (with the history of their channels). */
    @JvmStatic
    fun decide(store: ClassStore, entries: Collection<LangEntry>, allowed: Set<String>): Map<String, Result> {
        val history = store.langsByChannel(entries.map { it.channel }.filter { it.isNotEmpty() }, CHANNEL_HISTORY)
        return entries.associate { e ->
            val others = history[e.channel].orEmpty().filter { it.key != e.key }.map { guessOf(it) }
            val g = guessOf(e)
            e.key to Result(e, ZenLangFilter.decide(g, others, allowed), g)
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

    /** Stored entry of a video whose details ([signals]) are known: title heuristic combined with them. */
    @JvmStatic
    fun detailsEntry(key: String, channel: String?, title: String?, channelName: String?, signals: ZenLangSignals, nowMillis: Long): LangEntry {
        val g = ZenLangDetect.combine(ZenLangDetect.detect(title, channelName), signals)
        return LangEntry(key, channel.orEmpty(), title.orEmpty(), channelName.orEmpty(), g, nowMillis, signals)
    }

    /**
     * The language signals of the video details (language field, original audio track, automatic
     * captions) are stored with the video and combined with the title ([ZenLangDetect.combine]).
     */
    @JvmStatic
    fun recordDetails(context: Context, key: String, channel: String?, title: String?, channelName: String?, signals: ZenLangSignals?) {
        if (signals == null) return
        try {
            ZenClassCache.get(context).putLangs(listOf(detailsEntry(key, channel, title, channelName, signals, System.currentTimeMillis())))
        } catch (e: Exception) {
            Log.w("ZenLanguage", "recordDetails", e)
        }
    }

    private fun num(v: Double) = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(v)

    private fun sourceName(context: Context, source: String) = context.getString(
        when (source) {
            "D" -> R.string.zen_lang_src_field
            "A" -> R.string.zen_lang_src_audio
            "TS" -> R.string.zen_lang_src_title_captions
            "S" -> R.string.zen_lang_src_captions
            else -> R.string.zen_lang_src_title
        }
    )

    /**
     * "Language: Catalan 0.95 (title) · hidden in the feed", "Language: Hindi (captions track only),
     * not hidden", "Language: English (title) · captions track: Hindi, the title wins" and the like.
     */
    @JvmStatic
    fun whyLine(context: Context, r: Result): String {
        val locale = androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: java.util.Locale.getDefault()
        val g = r.guess
        val d = r.decision
        val name = g.lang?.let { ZenLangOptions.name(it, locale) }
        val src = sourceName(context, g.source)
        val line = when {
            d.verdict == ZenLangFilter.Verdict.UNKNOWN -> context.getString(R.string.zen_lang_why_unknown)
            d.verdict == ZenLangFilter.Verdict.ALLOWED -> context.getString(R.string.zen_lang_why_allowed, name, src)
            g.source == "S" -> context.getString(R.string.zen_lang_why_captions_only, name)
            d.verdict == ZenLangFilter.Verdict.UNCERTAIN -> context.getString(R.string.zen_lang_why_uncertain, name, num(d.score), src)
            else -> context.getString(R.string.zen_lang_why_hidden, name, num(d.score), src)
        }
        val note = g.note?.split(':')?.takeIf { it.size == 2 } ?: return line
        return line + context.getString(R.string.zen_lang_why_overruled, sourceName(context, note[0]), ZenLangOptions.name(note[1], locale))
    }
}
