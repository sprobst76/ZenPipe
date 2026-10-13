package org.schabi.newpipe.zen

import android.app.Activity
import android.os.CountDownTimer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R

/**
 * Exception list of the content recognition (Settings > Zen, long press on a channel): channels
 * and title keywords whose videos keep weight 1.0 and skip the heavy rule. Adding is a loosening
 * (confirmation with a short wait, pending for 24 h); removing applies at once.
 */
object ZenClassExemptUi {
    private fun dp(a: Activity, v: Int) = (v * a.resources.displayMetrics.density).toInt()

    private fun text(a: Activity, s: CharSequence, size: Float = 14f, bold: Boolean = false) = TextView(a).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    /** List with state and remove/cancel, plus "add keyword" / "add channel". */
    @JvmStatic
    fun render(a: Activity, content: LinearLayout, rerender: () -> Unit) {
        val lock = Zen.settingsLock(a)
        val snap = lock.snapshot()
        content.addView(
            text(a, a.getString(R.string.zen_exempt_title), 15f, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(a, 16) }
        )
        content.addView(text(a, a.getString(R.string.zen_exempt_hint), 12f))
        if (snap.exempt.isEmpty() && snap.exemptPending.isEmpty()) content.addView(text(a, a.getString(R.string.zen_exempt_none)))
        fun row(e: ZenClassExempt, pending: Boolean) {
            val box = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(a, 6), 0, dp(a, 2))
            }
            val labels = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(a, e.label, 15f))
            labels.addView(
                text(
                    a,
                    a.getString(if (e.keyword) R.string.zen_exempt_kind_keyword else R.string.zen_exempt_kind_channel) + " · " +
                        if (pending) {
                            a.getString(R.string.zen_praxis_state_pending, ZenPraxisUi.fmtTime(snap.lockRemainingMs))
                        } else {
                            a.getString(R.string.zen_praxis_state_active)
                        },
                    12f
                )
            )
            box.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(
                Button(a).apply {
                    setText(if (pending) R.string.zen_praxis_cancel else R.string.zen_praxis_remove)
                    setOnClickListener {
                        lock.removeExempt(e.key)
                        rerender()
                    }
                }
            )
            content.addView(box)
        }
        snap.exempt.forEach { row(it, false) }
        snap.exemptPending.forEach { row(it, true) }
        val locked = !snap.setupMode && snap.lockRemainingMs > 0
        val buttons = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            Button(a).apply {
                setText(R.string.zen_exempt_add_keyword)
                isEnabled = !locked
                setOnClickListener { keywordDialog(a, rerender) }
            }
        )
        buttons.addView(
            Button(a).apply {
                setText(R.string.zen_exempt_add_channel)
                isEnabled = !locked
                setOnClickListener { channelDialog(a, rerender) }
            }
        )
        content.addView(buttons)
    }

    private fun keywordDialog(a: Activity, done: () -> Unit) {
        val input = EditText(a).apply {
            setHint(R.string.zen_exempt_keyword_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(ZenClassExempt.MAX_KEYWORD))
            isSingleLine = true
        }
        val frame = FrameLayout(a).apply {
            val p = dp(a, 20)
            setPadding(p, dp(a, 8), p, 0)
            addView(input)
        }
        AlertDialog.Builder(a)
            .setTitle(R.string.zen_exempt_add_keyword)
            .setView(frame)
            .setPositiveButton(R.string.zen_exempt_next) { _, _ ->
                val t = input.text.toString()
                if (ZenClassExempt.validKeyword(t)) {
                    confirmAdd(a, ZenClassExempt.keyword(t), done)
                } else {
                    Toast.makeText(a, a.getString(R.string.zen_exempt_keyword_invalid, ZenClassExempt.MIN_KEYWORD), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Pick one of the subscribed channels. */
    private fun channelDialog(a: Activity, done: () -> Unit) {
        Thread {
            val subs = try {
                NewPipeDatabase.getInstance(a).subscriptionDAO().getAll().blockingFirst()
                    .filter { !it.url.isNullOrEmpty() }
            } catch (e: Exception) {
                emptyList()
            }
            a.runOnUiThread {
                if (a.isFinishing) return@runOnUiThread
                if (subs.isEmpty()) {
                    Toast.makeText(a, R.string.zen_exempt_no_subscriptions, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                AlertDialog.Builder(a)
                    .setTitle(R.string.zen_exempt_add_channel)
                    .setItems(subs.map { it.name ?: it.url }.toTypedArray()) { _, i ->
                        val s = subs[i]
                        confirmAdd(a, ZenClassExempt.channel(s.serviceId, s.url!!, s.name ?: ""), done)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }.start()
    }

    /** Long press on a channel entry: add it to the exception list (same confirmation). */
    @JvmStatic
    fun addChannel(a: Activity, serviceId: Int, url: String, name: String) = confirmAdd(a, ZenClassExempt.channel(serviceId, url, name)) { }

    /** Confirmation with a short wait; in the first-time setup the entry applies at once. */
    @JvmStatic
    fun confirmAdd(a: Activity, e: ZenClassExempt, done: () -> Unit) {
        val lock = Zen.settingsLock(a)
        val snap = lock.snapshot()
        if (snap.setupMode) {
            lock.addExempt(e)
            Toast.makeText(a, a.getString(R.string.zen_exempt_added_setup, e.label), Toast.LENGTH_LONG).show()
            done()
            return
        }
        if (snap.lockRemainingMs > 0) {
            Toast.makeText(a, a.getString(R.string.zen_exempt_locked, ZenPraxisUi.fmtTime(snap.lockRemainingMs)), Toast.LENGTH_LONG).show()
            return
        }
        val dialog = AlertDialog.Builder(a)
            .setTitle(R.string.zen_exempt_confirm_title)
            .setMessage(
                a.getString(R.string.zen_exempt_confirm_message, e.label, ZenPraxisUi.fmtTime(ZenSettingsRules.LOCK_MS)) + "\n\n" +
                    a.getString(R.string.zen_confirm_footer_lock) + " " + a.getString(R.string.zen_confirm_footer_undo)
            )
            .setPositiveButton(R.string.zen_confirm_ok) { _, _ ->
                val msg = when (lock.addExempt(e)) {
                    is ZenSettingsLock.Result.Applied ->
                        a.getString(R.string.zen_exempt_added_pending, e.label, ZenPraxisUi.fmtTime(lock.snapshot().lockRemainingMs))

                    is ZenSettingsLock.Result.Rejected -> a.getString(R.string.zen_exempt_locked, ZenPraxisUi.fmtTime(lock.snapshot().lockRemainingMs))

                    ZenSettingsLock.Result.NoChange -> null
                }
                msg?.let { Toast.makeText(a.applicationContext, it, Toast.LENGTH_LONG).show() }
                done()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = false
        object : CountDownTimer(ZenSettingsRules.CONFIRM_WAIT_S * 1000L, 500L) {
            override fun onTick(left: Long) {
                ok.text = a.getString(R.string.zen_confirm_wait, (left + 999) / 1000)
            }

            override fun onFinish() {
                ok.setText(R.string.zen_confirm_ok)
                ok.isEnabled = true
            }
        }.start()
    }
}
