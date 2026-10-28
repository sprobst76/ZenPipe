/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import com.grack.nanojson.JsonWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Debug export of the local classifications (review list > "Export"): one JSON file and one CSV
 * file for training/evaluation elsewhere. Pure data building, no Android, no network.
 *
 * Contains per video only what the classifier saw and said plus the user's own correction:
 * service id, URL, title, channel name/URL, duration, time of the classification, input level,
 * model, categories, top themes/formats, tone, weight, the "why" text, language and `user_label`, and the
 * practice themes (D61: model values, title rules + model, the user's own correction).
 * It never contains watch history, subscriptions, Zen settings, lock/pause state or the Zen log
 * ([VIDEO_FIELDS] / [TOP_FIELDS] are the complete lists, checked by a unit test).
 */
object ZenClassExport {
    const val FORMAT = "zenpipe-classifications"
    const val FORMAT_VERSION = 1

    /** Exports stay in the cache only this long (they contain real titles). */
    const val MAX_FILE_AGE_MS = 60 * 60 * 1000L

    /** Deletes files in [dir] that are older than [maxAgeMs]; returns how many were deleted. */
    @JvmStatic
    fun deleteStale(dir: java.io.File, nowMillis: Long, maxAgeMs: Long = MAX_FILE_AGE_MS): Int = dir.listFiles()?.count { f ->
        f.isFile && nowMillis - f.lastModified() > maxAgeMs && f.delete()
    } ?: 0

    /** Every key an exported video may have. */
    val VIDEO_FIELDS = setOf(
        "key", "service_id", "url", "title", "channel", "channel_url", "duration_s", "classified_at", "level", "model",
        "categories", "top", "tone", "weight", "why", "language", "user_label", "previous_title_only",
        "practice_theme_values", "practice_themes", "user_practice_themes"
    )

    /** Every top-level key of the JSON file. */
    val TOP_FIELDS = setOf(
        "format", "version", "exported_at", "app_version", "model", "note", "videos", "labels_without_video", "feed_edition", "languages",
        "practice_themes_without_video"
    )

    /** Keys of one entry of "languages" (every video the feed language filter has judged). */
    val LANGUAGE_FIELDS = setOf("key", "title", "channel", "code", "confidence", "source", "hidden_in_feed", "scores")

    /** Language of a video (filled once the language filter knows it; null = unknown). */
    data class Language(val code: String?, val confidence: Double, val source: String, val hiddenInFeed: Boolean?)

    /** One exported video: the cache entry and what the export adds to it. */
    data class Video(
        val entry: ClassEntry,
        val channelUrl: String?,
        val durationSec: Long?,
        /** Readable "why" summary in the app language (same text as the review list). */
        val why: String,
        val label: UserLabel?,
        val language: Language? = null,
        /** Practice themes from title rules + model alone (no correction, no channel default), D61. */
        val practiceAuto: org.schabi.newpipe.zen.ZenPraxisThemes.Resolved? = null,
        /** The user's practice theme correction. */
        val practiceUser: PraxisThemeLabel? = null
    )

    /** One pick of the current feed edition with its classification(s) (T and D side by side). */
    data class Pick(val url: String, val minutes: Int, val group: String?, val storedWhy: String?, val entry: ClassEntry?)

    data class Edition(val createdAtMillis: Long, val day: String, val leftover: Int, val picks: List<Pick>)

    data class Meta(val exportedAtMillis: Long, val appVersion: String, val model: String)

    /** One judged video of the feed language filter (also videos that were never classified). */
    data class LangVideo(val key: String, val title: String, val channel: String, val language: Language, val scores: Map<String, Double>)

    /** "serviceId:url" -> (serviceId, url); null for a malformed key. */
    @JvmStatic
    fun splitKey(key: String): Pair<Int, String>? {
        val i = key.indexOf(':')
        if (i <= 0) return null
        val sid = key.substring(0, i).toIntOrNull() ?: return null
        return sid to key.substring(i + 1)
    }

    private fun r3(v: Double): Double = if (v.isNaN() || v.isInfinite()) 0.0 else BigDecimal(v).setScale(3, RoundingMode.HALF_EVEN).toDouble()

    private fun iso(millis: Long) = Instant.ofEpochMilli(millis).toString()

    private fun catsMap(m: Map<String, Double>): Map<String, Any?> = LinkedHashMap<String, Any?>().apply { m.forEach { (k, v) -> put(k, r3(v)) } }

    private fun topList(top: List<Pair<String, Double>>): List<Any?> = top.map {
        linkedMapOf<String, Any?>("key" to it.first, "kind" to if (it.first in ZenClassWhy.FORMAT_KEYS) "format" else "theme", "p" to r3(it.second))
    }

    private fun snapshotMap(s: ClassEntry.Snapshot): Map<String, Any?> = linkedMapOf(
        "categories" to catsMap(s.cats),
        "top" to topList(s.top),
        "tone" to s.tone,
        "weight" to r3(s.weight)
    )

    private fun labelMap(l: UserLabel): Map<String, Any?> = linkedMapOf(
        "verdict" to if (l.correct) "correct" else "wrong",
        "categories" to l.cats,
        "at" to iso(l.atMillis)
    )

    private fun themeKeys(mask: Int): List<String> = org.schabi.newpipe.zen.ZenPraxisThemes.themes(mask).map { it.key }

    private fun practiceUserMap(l: PraxisThemeLabel): Map<String, Any?> = linkedMapOf("themes" to themeKeys(l.mask), "at" to iso(l.atMillis))

    private fun languageMap(l: Language): Map<String, Any?> = linkedMapOf(
        "code" to l.code,
        "confidence" to r3(l.confidence),
        "source" to l.source,
        "hidden_in_feed" to l.hiddenInFeed
    )

    /** The JSON object of one video (only [VIDEO_FIELDS]). */
    @JvmStatic
    fun videoMap(v: Video): Map<String, Any?> {
        val e = v.entry
        val (sid, url) = splitKey(e.key) ?: (-1 to e.key)
        return linkedMapOf(
            "key" to e.key,
            "service_id" to sid,
            "url" to url,
            "title" to e.title,
            "channel" to e.channel,
            "channel_url" to v.channelUrl,
            "duration_s" to v.durationSec,
            "classified_at" to iso(e.createdMillis),
            "level" to e.level,
            "model" to e.model,
            "categories" to catsMap(e.cats),
            "top" to topList(e.top),
            "tone" to e.tone,
            "weight" to r3(if (e.cats.isEmpty()) 1.0 else e.weight),
            "why" to v.why,
            "language" to v.language?.let { languageMap(it) },
            "user_label" to v.label?.let { labelMap(it) },
            "previous_title_only" to ClassEntry.decodeSnapshot(e.previousT)?.let { snapshotMap(it) },
            "practice_theme_values" to e.praxisThemes?.let { catsMap(it) },
            "practice_themes" to v.practiceAuto?.let { linkedMapOf<String, Any?>("themes" to themeKeys(it.mask), "source" to it.source.name.lowercase()) },
            "user_practice_themes" to v.practiceUser?.let { practiceUserMap(it) }
        )
    }

    private fun pickMap(p: Pick): Map<String, Any?> {
        val e = p.entry
        val d = e?.takeIf { it.level == "D" }
        val t = if (e?.level == "T") ClassEntry.encodeSnapshot(e) else e?.previousT
        return linkedMapOf(
            "url" to p.url,
            "minutes" to p.minutes,
            "group" to p.group,
            "why_stored" to p.storedWhy,
            "key" to e?.key,
            "title" to e?.title,
            "channel" to e?.channel,
            "title_only" to ClassEntry.decodeSnapshot(t)?.let { snapshotMap(it) },
            "with_details" to d?.let { snapshotMap(ClassEntry.Snapshot(it.tone, it.weight, it.cats, it.top)) }
        )
    }

    /**
     * The JSON file. [orphanLabels] = labels whose cache entry is gone (30-day retention); they are
     * kept with the title/channel stored with the label.
     */
    @JvmStatic
    @JvmOverloads
    fun json(
        meta: Meta,
        videos: List<Video>,
        orphanLabels: List<UserLabel>,
        edition: Edition?,
        languages: List<LangVideo> = emptyList(),
        orphanPractice: List<PraxisThemeLabel> = emptyList()
    ): String {
        val root = linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "version" to FORMAT_VERSION,
            "exported_at" to iso(meta.exportedAtMillis),
            "app_version" to meta.appVersion,
            "model" to meta.model,
            "note" to NOTE,
            "videos" to videos.map { videoMap(it) },
            "labels_without_video" to orphanLabels.map {
                linkedMapOf<String, Any?>("key" to it.key, "title" to it.title, "channel" to it.channel) + labelMap(it)
            },
            "feed_edition" to edition?.let { ed ->
                linkedMapOf<String, Any?>(
                    "created_at" to iso(ed.createdAtMillis),
                    "day" to ed.day,
                    "leftover" to ed.leftover,
                    "picks" to ed.picks.map { pickMap(it) }
                )
            },
            "languages" to languages.map { l ->
                linkedMapOf<String, Any?>("key" to l.key, "title" to l.title, "channel" to l.channel) + languageMap(l.language) +
                    ("scores" to catsMap(l.scores))
            },
            "practice_themes_without_video" to orphanPractice.map {
                linkedMapOf<String, Any?>("key" to it.key, "title" to it.title, "channel" to it.channel) + practiceUserMap(it)
            }
        )
        return JsonWriter.indent("  ").string().value(root).done()
    }

    const val NOTE = "Debug export of ZenPipe's local content recognition. Contains real video titles and channel names: " +
        "do not put it into a public repository. No watch history, subscriptions or settings."

    val CSV_COLUMNS = listOf(
        "key", "service_id", "url", "title", "channel", "channel_url", "duration_s", "classified_at", "level", "model",
        "categories", "top", "tone", "weight", "why", "language", "language_confidence", "language_hidden",
        "user_verdict", "user_categories", "practice_themes", "user_practice_themes"
    )

    private fun csvCell(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** The CSV file (one row per video, same content as the JSON videos, flattened). */
    @JvmStatic
    fun csv(videos: List<Video>): String {
        val sb = StringBuilder(CSV_COLUMNS.joinToString(",")).append("\r\n")
        videos.forEach { v ->
            val m = videoMap(v)
            val lang = v.language
            val row = listOf(
                m["key"], m["service_id"], m["url"], m["title"], m["channel"], m["channel_url"], m["duration_s"],
                m["classified_at"], m["level"], m["model"],
                v.entry.cats.entries.joinToString(";") { "${it.key}=${r3(it.value)}" },
                v.entry.top.joinToString(";") { "${it.first}=${r3(it.second)}" },
                m["tone"], m["weight"], m["why"],
                lang?.code, lang?.let { r3(it.confidence) }, lang?.hiddenInFeed,
                v.label?.let { if (it.correct) "correct" else "wrong" }, v.label?.cats?.joinToString(";"),
                v.practiceAuto?.let { themeKeys(it.mask).joinToString(";") }, v.practiceUser?.let { themeKeys(it.mask).joinToString(";") }
            )
            sb.append(row.joinToString(",") { csvCell(it) }).append("\r\n")
        }
        return sb.toString()
    }
}
