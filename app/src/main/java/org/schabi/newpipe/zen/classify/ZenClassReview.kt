/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.zen.Zen
import org.schabi.newpipe.zen.ZenFeedLanguages
import org.schabi.newpipe.zen.ZenFeedState
import org.schabi.newpipe.zen.ZenSettings

/**
 * Review list "Classified videos" (Settings > Zen): the latest classifications, the user's own
 * correction per entry ("correct" / "not correct" + the right categories, stored locally, no effect
 * on the feed, weights or locks) and the debug export (JSON + CSV via the share sheet).
 */
class ZenClassReview(private val activity: Activity) {
    private class Row(val entry: ClassEntry, var label: UserLabel?, val language: ZenClassExport.Language?)

    private val app = activity.applicationContext
    private val cache get() = ZenClassCache.get(app)

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    fun show() {
        Thread {
            val entries = cache.recent(REVIEW_LIMIT)
            val langs = languages(entries.map { it.key })
            langResults = langs
            val rows = entries.map { Row(it, cache.label(it.key), langs[it.key]?.let { r -> exportLanguage(r) }) }
            activity.runOnUiThread { if (!activity.isFinishing) showList(rows) }
        }.start()
    }

    // language results of the rows shown (for their "why" line)
    private var langResults: Map<String, ZenFeedLanguages.Result> = emptyMap()

    /** Stored languages of [keys] with the current decision of the feed language filter. */
    private fun languages(keys: List<String>): Map<String, ZenFeedLanguages.Result> = try {
        ZenFeedLanguages.decide(cache, cache.langs(keys).values, ZenSettings.feedLanguages())
    } catch (e: Exception) {
        Log.w(ZenClassifier.TAG, "languages", e)
        emptyMap()
    }

    private fun exportLanguage(r: ZenFeedLanguages.Result) = ZenClassExport.Language(
        r.guess.lang,
        r.guess.confidence,
        r.guess.source,
        r.decision.hidden
    )

    private fun rowText(r: Row): CharSequence {
        val e = r.entry
        val sb = SpannableStringBuilder()
        sb.append("${e.title} — ${e.channel}", StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append('\n').append(ZenClassifier.describe(activity, e))
        langResults[e.key]?.let { sb.append('\n').append(ZenFeedLanguages.whyLine(activity, it)) }
        val l = r.label
        sb.append('\n').append(
            when {
                l == null -> activity.getString(R.string.zen_review_label_none)
                l.correct -> activity.getString(R.string.zen_review_label_correct)
                else -> activity.getString(R.string.zen_review_label_wrong, catsText(l.cats))
            }
        )
        return sb
    }

    private fun catsText(cats: List<String>) = if (cats.isEmpty()) {
        activity.getString(R.string.zen_review_cat_none)
    } else {
        cats.joinToString(", ") { ZenClassWhy.catName(activity, it) }
    }

    private fun showList(rows: List<Row>) {
        if (rows.isEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.zen_class_review)
                .setMessage(R.string.zen_class_review_empty)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.zen_review_export) { _, _ -> confirmExport() }
                .show()
            return
        }
        val list = ListView(activity)
        val adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val tv = (convertView as? TextView) ?: TextView(activity).apply {
                    textSize = 12f
                    setPadding(dp(20), dp(8), dp(20), dp(8))
                }
                tv.text = rowText(rows[position])
                return tv
            }
        }
        list.addHeaderView(
            TextView(activity).apply {
                textSize = 12f
                setPadding(dp(20), dp(8), dp(20), dp(8))
                text = activity.getString(R.string.zen_class_review_hint) + "\n\n" + activity.getString(R.string.zen_review_label_hint)
            },
            null,
            false
        )
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            val r = rows.getOrNull(position - list.headerViewsCount) ?: return@setOnItemClickListener
            chooseLabel(r) { adapter.notifyDataSetChanged() }
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_class_review)
            .setView(list)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.zen_review_export) { _, _ -> confirmExport() }
            .show()
    }

    private fun chooseLabel(r: Row, changed: () -> Unit) {
        val items = arrayListOf(activity.getString(R.string.zen_review_correct), activity.getString(R.string.zen_review_wrong))
        if (r.label != null) items.add(activity.getString(R.string.zen_review_remove))
        AlertDialog.Builder(activity)
            .setTitle(r.entry.title)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> save(r, true, r.entry.cats.keys.toList(), changed)

                    1 -> chooseCategories(r, changed)

                    else -> {
                        r.label = null
                        Thread { cache.removeLabel(r.entry.key) }.start()
                        changed()
                    }
                }
            }
            .show()
    }

    /** "Not correct": the right categories (the seven of the rules, or "none"). */
    private fun chooseCategories(r: Row, changed: () -> Unit) {
        val cats = UserLabel.CATEGORIES
        val names = (cats.map { ZenClassWhy.catName(activity, it) } + activity.getString(R.string.zen_review_cat_none)).toTypedArray()
        val start = r.label?.takeIf { !it.correct }?.cats ?: r.entry.cats.keys.toList()
        val checked = BooleanArray(names.size) { i -> if (i < cats.size) cats[i] in start else start.isEmpty() && r.label != null }
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_review_wrong_title)
            .setMultiChoiceItems(names, checked) { d, which, isChecked ->
                checked[which] = isChecked
                val lv = (d as AlertDialog).listView
                // "none" excludes the categories and the other way round
                if (isChecked && which == cats.size) {
                    for (i in cats.indices) {
                        checked[i] = false
                        lv.setItemChecked(i, false)
                    }
                } else if (isChecked) {
                    checked[cats.size] = false
                    lv.setItemChecked(cats.size, false)
                }
            }
            .setPositiveButton(R.string.zen_review_save) { _, _ ->
                save(r, false, cats.filterIndexed { i, _ -> checked[i] }, changed)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun save(r: Row, correct: Boolean, cats: List<String>, changed: () -> Unit) {
        val l = UserLabel(r.entry.key, correct, cats, r.entry.title, r.entry.channel, System.currentTimeMillis())
        r.label = l
        Thread { cache.putLabel(l) }.start()
        Zen.log(app).add("class_label", "verdict=${if (correct) "correct" else "wrong"} cats=${cats.joinToString(",")}")
        changed()
    }

    // ------------------------------------------------------------------ export

    private fun confirmExport() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_review_export_title)
            .setMessage(R.string.zen_review_export_message)
            .setPositiveButton(R.string.zen_review_export_share) { _, _ -> export() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun export() {
        Thread {
            val files = try {
                writeExport()
            } catch (e: Exception) {
                Log.w(ZenClassifier.TAG, "export", e)
                null
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                if (files == null) {
                    Toast.makeText(activity, R.string.zen_review_export_failed, Toast.LENGTH_LONG).show()
                } else {
                    share(files)
                }
            }
        }.start()
    }

    /** Builds and writes both files into the app cache (older exports are deleted first). */
    private fun writeExport(): List<File> {
        val now = System.currentTimeMillis()
        val entries = cache.recent(EXPORT_LIMIT)
        val labels = cache.labels()
        val labelByKey = labels.associateBy { it.key }
        val streams = NewPipeDatabase.getInstance(app).streamDAO()
        val allLangs = ZenFeedLanguages.decide(cache, cache.recentLangs(EXPORT_LIMIT), ZenSettings.feedLanguages())
        val videos = entries.map { e ->
            val (sid, url) = ZenClassExport.splitKey(e.key) ?: (0 to e.key)
            val s = try {
                streams.getStream(sid.toLong(), url).blockingFirst().firstOrNull()
            } catch (ex: Exception) {
                null
            }
            ZenClassExport.Video(
                entry = e,
                channelUrl = s?.uploaderUrl,
                durationSec = s?.duration?.takeIf { it > 0 },
                why = ZenClassWhy.summary(activity, e.cats, e.tone, e.level, e.top),
                label = labelByKey[e.key],
                language = (allLangs[e.key] ?: languages(listOf(e.key))[e.key])?.let { exportLanguage(it) }
            )
        }
        val inExport = entries.map { it.key }.toHashSet()
        val edition = ZenFeedState(Zen.store(app)).current()?.let { ed ->
            ZenClassExport.Edition(
                ed.createdAt,
                ed.day,
                ed.leftover,
                ed.picks.map { p ->
                    val entry = (0..MAX_SERVICE_ID).asSequence().mapNotNull { cache.get(ZenClassCache.keyOf(it, p.url)) }.firstOrNull()
                    ZenClassExport.Pick(p.url, p.minutes, p.groupName, p.why, entry)
                }
            )
        }
        val meta = ZenClassExport.Meta(now, BuildConfig.VERSION_NAME, ZenClassifierModel.VERSION)
        val dir = File(app.cacheDir, EXPORT_DIR)
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now))
        val json = File(dir, "zenpipe-classifications-$stamp.json")
        val csv = File(dir, "zenpipe-classifications-$stamp.csv")
        val langVideos = allLangs.values.map { r ->
            ZenClassExport.LangVideo(r.entry.key, r.entry.title, r.entry.channelName, exportLanguage(r), r.guess.scores)
        }
        json.writeText(ZenClassExport.json(meta, videos, labels.filter { it.key !in inExport }, edition, langVideos))
        csv.writeText(ZenClassExport.csv(videos))
        Zen.log(app).add(
            "class_export",
            "videos=${videos.size} labels=${labels.size} edition=${edition?.picks?.size ?: 0} languages=${langVideos.size}"
        )
        return listOf(json, csv)
    }

    private fun share(files: List<File>) {
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(app, BuildConfig.APPLICATION_ID + ".provider", it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, files.first().nameWithoutExtension)
            clipData = ClipData.newUri(app.contentResolver, files.first().name, uris[0]).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.zen_review_export_title)))
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.zen_review_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REVIEW_LIMIT = 100
        private const val EXPORT_LIMIT = 5000
        private const val EXPORT_DIR = "zen_export"

        /** NewPipe service ids 0..4 (YouTube, SoundCloud, media.ccc.de, PeerTube, Bandcamp). */
        private const val MAX_SERVICE_ID = 4
    }
}
