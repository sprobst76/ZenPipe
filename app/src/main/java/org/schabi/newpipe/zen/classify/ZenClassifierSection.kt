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
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.zen.Zen
import org.schabi.newpipe.zen.ZenSettingKeys

/**
 * Settings > Zen, section "Content recognition (local)": status, opt-in download (not locked),
 * delete and the license notices. Deleting a working model needs both content switches off
 * (spec 4.7: "delete only without active switches"; turning them off is a loosening).
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
        // spec 4.7: deleting a working model while a content switch is on would be a loosening
        // without the lock, so it needs both switches off first (a broken model has no effect anyway)
        val deleteBlocked = status == ZenClassifierModel.Status.INSTALLED && switchesOn()
        if (status == ZenClassifierModel.Status.INSTALLED || status == ZenClassifierModel.Status.BROKEN) {
            buttons.addView(button(activity.getString(R.string.zen_class_delete), enabled = !deleteBlocked) { confirmDelete() })
        }
        buttons.addView(button(activity.getString(R.string.zen_class_licenses)) { showNotices(R.raw.zen_classifier_notices) })
        content.addView(buttons)
        if (deleteBlocked) content.addView(text(activity.getString(R.string.zen_class_delete_blocked), 12f))
        if (status != ZenClassifierModel.Status.UNSUPPORTED) content.addView(text(activity.getString(R.string.zen_class_switches_hint), 12f))
        if (status != ZenClassifierModel.Status.UNSUPPORTED) org.schabi.newpipe.zen.ZenClassExemptUi.render(activity, content, rerender)
        if (status == ZenClassifierModel.Status.INSTALLED) {
            val extra = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            extra.addView(button(activity.getString(R.string.zen_class_review)) { showReview() })
            if (BuildConfig.DEBUG) extra.addView(button(activity.getString(R.string.zen_class_selftest)) { runSelfTest() })
            content.addView(extra)
        }
    }

    /** Review list (spec 6.3): the latest classifications with categories, tone, weight, level and "why". */
    private fun showReview() = ZenClassReview(activity).show()

    private fun runSelfTest() {
        val wait = AlertDialog.Builder(activity).setMessage(R.string.zen_class_selftest_running).setCancelable(false).show()
        Thread {
            val r = try {
                ZenClassifierSelfTest.run(activity)
            } catch (e: Exception) {
                null
            }
            activity.runOnUiThread {
                wait.dismiss()
                if (activity.isFinishing) return@runOnUiThread
                val body = r?.let {
                    "load (tokenizer/map+heads/encoder): ${it.loadMs.joinToString(" / ")} ms\n" +
                        "per video: median ${it.medianMs} ms, max ${it.maxMs} ms\n${it.file}\n\n" + it.lines.joinToString("\n")
                } ?: "no model / error"
                showText(activity.getString(R.string.zen_class_selftest), body)
                rerender()
            }
        }.start()
    }

    private fun showText(title: String, body: String) {
        val tv = text(body, 12f).apply {
            setTextIsSelectable(true)
            val p = dp(20)
            setPadding(p, dp(8), p, 0)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(ScrollView(activity).apply { addView(tv) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

    /** "on"/"off" of both content switches (effective values), for the download dialog and the result. */
    private fun switchStates(): Pair<String, String> {
        val lock = Zen.settingsLock(activity)
        fun s(key: String) = activity.getString(if (lock.effective(key) == 1) R.string.zen_on else R.string.zen_off)
        return s(ZenSettingKeys.CLASS_FEED) to s(ZenSettingKeys.CLASS_HEAVY)
    }

    private fun confirmDownload() {
        // The switches are not changed by the download, but they only take effect with a model:
        // say so before (dialog) and after (toast), so nothing starts working unnoticed (spec 4.7).
        val (feed, heavy) = switchStates()
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_class_title)
            .setMessage(
                activity.getString(R.string.zen_class_download_confirm, mb(ZenClassifierModel.DOWNLOAD_BYTES)) + "\n\n" +
                    activity.getString(R.string.zen_class_download_switches, feed, heavy)
            )
            .setPositiveButton(R.string.zen_class_download_short) { _, _ ->
                ZenClassifierModel.download.start(activity) {
                    if (!activity.isFinishing && ZenClassifierModel.isReady(activity)) {
                        val (f, h) = switchStates()
                        android.widget.Toast.makeText(activity, activity.getString(R.string.zen_class_installed_switches, f, h), android.widget.Toast.LENGTH_LONG).show()
                    }
                    rerender()
                }
                rerender()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun switchesOn(): Boolean {
        val lock = Zen.settingsLock(activity)
        return ZenSettingKeys.CLASSIFIER.any { lock.effective(it) == 1 }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_class_title)
            .setMessage(R.string.zen_class_delete_confirm)
            .setPositiveButton(R.string.zen_class_delete) { _, _ ->
                if (ZenClassifierModel.isReady(activity) && switchesOn()) return@setPositiveButton
                ZenClassifierModel.delete(activity)
                rerender()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNotices(res: Int) {
        val body = ZenNoticeText.reflow(activity.resources.openRawResource(res).bufferedReader().use { it.readText() })
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
