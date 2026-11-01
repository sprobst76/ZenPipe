package org.schabi.newpipe.zen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.R
import org.schabi.newpipe.zen.classify.ZenClassCache
import org.schabi.newpipe.zen.classify.ZenClassWhy
import org.schabi.newpipe.zen.classify.ZenClassifierModel

/**
 * UI of the local rating "More of this" / "Less of this" and the like list
 * (zen-docs/spec-bewertung.md). Entry points: long-press menu of every video
 * (`StreamDialogDefaultEntry.ZEN_RATING`), long press on the title of the video page
 * ([ZenPraxisUi.showVideoLongPress]) and Settings > Zen (lists).
 */
object ZenRatingUi {
    /** Official YouTube app: the like list opens videos there first, otherwise in a browser. */
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    private fun ratings(context: Context) = ZenRatings(Zen.store(context))

    private data class Row(val label: String, val icon: Int, val action: () -> Unit)

    /** A plain dialog list whose rows carry an icon in front of the text (no thumbs, D79). */
    private fun adapter(context: Context, rows: List<Row>) = object : ArrayAdapter<Row>(context, android.R.layout.select_dialog_item, android.R.id.text1, rows) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getView(position, convertView, parent) as TextView
            val row = rows[position]
            v.text = row.label
            v.setCompoundDrawablesRelativeWithIntrinsicBounds(row.icon, 0, 0, 0)
            v.compoundDrawablePadding = (16 * context.resources.displayMetrics.density).toInt()
            return v
        }
    }

    /**
     * "More or less of this…": two rows with symbols, the chosen one marked; tapping it again
     * (or "Remove rating") takes it back. Takes effect from the next feed edition.
     */
    @JvmStatic
    fun onVideoMenu(activity: Activity, serviceId: Int, url: String, title: String, uploaderUrl: String?, uploaderName: String?) {
        val current = ratings(activity).valueOf(serviceId, url)
        fun mark(label: Int, value: Int) = activity.getString(label) + if (current == value) " ✓" else ""
        val rows = mutableListOf(
            Row(mark(R.string.zen_rating_more, ZenRating.MORE), R.drawable.zen_ic_more) {
                rate(activity, serviceId, url, title, uploaderUrl, uploaderName, ZenRating.MORE)
            },
            Row(mark(R.string.zen_rating_less, ZenRating.LESS), R.drawable.zen_ic_less) {
                rate(activity, serviceId, url, title, uploaderUrl, uploaderName, ZenRating.LESS)
            }
        )
        if (current != 0) {
            rows.add(
                Row(activity.getString(R.string.zen_rating_remove), R.drawable.zen_ic_rating_clear) {
                    rate(activity, serviceId, url, title, uploaderUrl, uploaderName, current)
                }
            )
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setAdapter(adapter(activity, rows)) { _, which -> rows[which].action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Strongest classified theme of a video (above the model threshold), null without model/entry. */
    private fun themeOf(context: Context, serviceId: Int, url: String): String? = try {
        if (!ZenClassifierModel.isSupported() || !ZenClassifierModel.isReady(context)) {
            null
        } else {
            ZenClassCache.get(context).get(ZenClassCache.keyOf(serviceId, url))
                ?.let { ZenClassWhy.strongestTheme(it.top) }
                ?.takeIf { !ZenClassWhy.belowThreshold(it.second) }?.first
        }
    } catch (e: Exception) {
        null
    }

    private fun rate(activity: Activity, serviceId: Int, url: String, title: String, uploaderUrl: String?, uploaderName: String?, value: Int) {
        val r = ZenRating(
            serviceId,
            url,
            value,
            uploaderUrl?.takeIf { it.isNotEmpty() }?.let { ZenPraxisChannel.keyOf(serviceId, it) } ?: "",
            uploaderName.orEmpty(),
            themeOf(activity, serviceId, url),
            title,
            System.currentTimeMillis()
        )
        val change = ratings(activity).rate(r)
        val word = if (value == ZenRating.MORE) "more" else "less"
        Zen.log(activity).add(
            "rating",
            "action=${if (change == ZenRatings.Change.REMOVED) "remove" else word} change=${change.name.lowercase()} theme=${r.theme ?: "-"}"
        )
        toast(
            activity,
            activity.getString(
                when {
                    change == ZenRatings.Change.REMOVED -> R.string.zen_rating_removed
                    value == ZenRating.MORE -> R.string.zen_rating_more_done
                    else -> R.string.zen_rating_less_done
                }
            )
        )
    }

    /** Settings > Zen: the like list (calm, one entry at a time). */
    @JvmStatic
    fun showLikeList(activity: Activity, changed: Runnable) {
        val likes = ratings(activity).likes().reversed()
        val b = AlertDialog.Builder(activity).setTitle(R.string.zen_like_list_title)
        if (likes.isEmpty()) {
            b.setMessage(R.string.zen_like_list_empty)
        } else {
            val rows = likes.map { e ->
                Row(listOf(e.title, e.channelName).filter { it.isNotEmpty() }.joinToString("\n"), R.drawable.zen_ic_more) {
                    showLikeEntry(activity, e, changed)
                }
            }
            b.setAdapter(adapter(activity, rows)) { _, which -> rows[which].action() }
        }
        b.setNegativeButton(R.string.zen_like_close, null).show()
    }

    private fun showLikeEntry(activity: Activity, e: ZenLikeEntry, changed: Runnable) {
        AlertDialog.Builder(activity)
            .setTitle(e.title.ifEmpty { e.url })
            .setMessage(R.string.zen_like_entry_message)
            .setPositiveButton(R.string.zen_like_open) { _, _ ->
                Zen.log(activity).add("like_open", "")
                openOutside(activity, e.url)
            }
            .setNeutralButton(R.string.zen_like_done) { _, _ ->
                ratings(activity).likeDone(e.serviceId, e.url)
                Zen.log(activity).add("like_done", "")
                changed.run()
            }
            .setNegativeButton(R.string.zen_like_close, null)
            .show()
    }

    /** Settings > Zen: all ratings, newest first; tap = remove (immediately, no lock). */
    @JvmStatic
    fun showRatings(activity: Activity, changed: Runnable) {
        val list = ratings(activity).all().reversed()
        val b = AlertDialog.Builder(activity).setTitle(R.string.zen_rating_list_title)
        if (list.isEmpty()) {
            b.setMessage(R.string.zen_rating_list_empty)
        } else {
            val rows = list.map { r ->
                Row(
                    listOf(r.title, r.channelName).filter { it.isNotEmpty() }.joinToString("\n"),
                    if (r.value == ZenRating.MORE) R.drawable.zen_ic_more else R.drawable.zen_ic_less
                ) {
                    AlertDialog.Builder(activity)
                        .setTitle(r.title.ifEmpty { r.url })
                        .setMessage(if (r.value == ZenRating.MORE) R.string.zen_rating_more else R.string.zen_rating_less)
                        .setPositiveButton(R.string.zen_rating_remove) { _, _ ->
                            ratings(activity).remove(r.serviceId, r.url)
                            Zen.log(activity).add("rating", "action=remove change=removed theme=${r.theme ?: "-"}")
                            changed.run()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
            b.setAdapter(adapter(activity, rows)) { _, which -> rows[which].action() }
        }
        b.setNegativeButton(R.string.zen_like_close, null).show()
    }

    /**
     * The one place where ZenPipe hands a YouTube link to another app on purpose (D80): the
     * YouTube app if installed, else the default browser (as upstream's "open in browser").
     * ZenPipe itself sends nothing.
     */
    @JvmStatic
    fun openOutside(context: Context, url: String) {
        val app = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(YOUTUBE_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tryStart(context, app)) return
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        browser.selector = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        if (!tryStart(context, browser)) toast(context, context.getString(R.string.no_app_to_open_intent))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun toast(context: Context, msg: String) = Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
}
