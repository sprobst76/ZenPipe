/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.app.Activity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.R

/**
 * Settings > Zen, section "Content recognition (local)": status, opt-in download (not locked),
 * delete and the license notices. Deleting is a loosening only once classifier switches exist
 * (spec 4.7: "delete only without active switches"); until then it is free.
 */
class ZenClassifierSection(private val activity: Activity, private val rerender: () -> Unit) {
    private var progressView: TextView? = null
    private var lastStatus: ZenClassifierModel.Status? = null

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private fun text(s: CharSequence, size: Float = 14f, bold: Boolean = false) = TextView(activity).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(label: CharSequence, enabled: Boolean = true, onClick: () -> Unit) = Button(activity).apply {
        text = label
        isEnabled = enabled
        setOnClickListener { onClick() }
    }

    private fun mb(bytes: Long) = ((bytes + 500_000) / 1_000_000).toInt()

    fun render(content: LinearLayout) {
        val status = ZenClassifierModel.status(activity)
        lastStatus = status
        val dl = ZenClassifierModel.download
        content.addView(
            text(activity.getString(R.string.zen_class_title), 16f, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(24) }
        )
        content.addView(text(activity.getString(R.string.zen_class_hint), 12f))
        val source = ZenClassifierModel.source(activity)
        val statusText = when (status) {
            ZenClassifierModel.Status.UNSUPPORTED -> activity.getString(R.string.zen_class_status_unsupported)

            ZenClassifierModel.Status.NOT_INSTALLED -> activity.getString(
                if (source == null) R.string.zen_class_status_not_published else R.string.zen_class_status_not_installed
            )

            ZenClassifierModel.Status.DOWNLOADING -> progressText()

            ZenClassifierModel.Status.INSTALLED -> activity.getString(R.string.zen_class_status_installed, ZenClassifierModel.VERSION)

            ZenClassifierModel.Status.BROKEN -> activity.getString(R.string.zen_class_status_broken)
        }
        val statusView = text(statusText, 15f)
        progressView = if (status == ZenClassifierModel.Status.DOWNLOADING) statusView else null
        content.addView(statusView)
        dl.lastError?.takeIf { status == ZenClassifierModel.Status.NOT_INSTALLED || status == ZenClassifierModel.Status.BROKEN }?.let {
            content.addView(text(activity.getString(R.string.zen_class_failed, it), 12f))
        }

        val buttons = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        when (status) {
            ZenClassifierModel.Status.UNSUPPORTED -> Unit

            ZenClassifierModel.Status.DOWNLOADING -> buttons.addView(button(activity.getString(R.string.zen_class_cancel)) { dl.cancel() })

            ZenClassifierModel.Status.NOT_INSTALLED, ZenClassifierModel.Status.BROKEN -> buttons.addView(
                button(
                    if (status == ZenClassifierModel.Status.BROKEN) {
                        activity.getString(R.string.zen_class_download_again)
                    } else {
                        activity.getString(R.string.zen_class_download, mb(ZenClassifierModel.DOWNLOAD_BYTES))
                    },
                    enabled = source != null
                ) { confirmDownload() }
            )

            ZenClassifierModel.Status.INSTALLED -> Unit
        }
        if (status == ZenClassifierModel.Status.INSTALLED || status == ZenClassifierModel.Status.BROKEN) {
            buttons.addView(button(activity.getString(R.string.zen_class_delete)) { confirmDelete() })
        }
        buttons.addView(button(activity.getString(R.string.zen_class_licenses)) { showNotices(R.raw.zen_classifier_notices) })
        content.addView(buttons)
    }

    /** Called every second by the settings screen: live progress, re-render on status change. */
    fun tick() {
        val status = ZenClassifierModel.status(activity)
        if (status != lastStatus) {
            rerender()
        } else {
            progressView?.text = progressText()
        }
    }

    private fun progressText(): String {
        val dl = ZenClassifierModel.download
        return activity.getString(R.string.zen_class_status_downloading, mb(dl.bytes), mb(dl.total))
    }

    private fun confirmDownload() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_class_title)
            .setMessage(activity.getString(R.string.zen_class_download_confirm, mb(ZenClassifierModel.DOWNLOAD_BYTES)))
            .setPositiveButton(R.string.zen_class_download_short) { _, _ ->
                ZenClassifierModel.download.start(activity) { rerender() }
                rerender()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_class_title)
            .setMessage(R.string.zen_class_delete_confirm)
            .setPositiveButton(R.string.zen_class_delete) { _, _ ->
                ZenClassifierModel.delete(activity)
                rerender()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNotices(res: Int) {
        val body = activity.resources.openRawResource(res).bufferedReader().use { it.readText() }
        val tv = text(body, 12f).apply {
            setTextIsSelectable(true)
            val p = dp(20)
            setPadding(p, dp(8), p, 0)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.zen_class_licenses)
            .setView(ScrollView(activity).apply { addView(tv) })
            .setPositiveButton(android.R.string.ok, null)
        if (res == R.raw.zen_classifier_notices) {
            dialog.setNeutralButton(R.string.zen_class_ort_notices) { _, _ -> showNotices(R.raw.zen_onnxruntime_third_party_notices) }
        }
        dialog.show()
    }
}
