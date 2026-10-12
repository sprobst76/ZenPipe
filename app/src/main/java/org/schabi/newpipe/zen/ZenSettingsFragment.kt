package org.schabi.newpipe.zen

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import java.text.DateFormat
import java.util.Date
import org.schabi.newpipe.R

/**
 * The "Zen" settings area (Settings > Zen): list of the Zen settings with their current value,
 * lock state, pending loosenings, a draft with one explicit "Apply" (one confirmation = one group),
 * confirmation dialog with a short wait, and the undo window. All logic is in [ZenSettingsLock].
 */
class ZenSettingsFragment : Fragment() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var lock: ZenSettingsLock
    private lateinit var content: LinearLayout
    private val draft = HashMap<String, Int>()
    private var statusView: TextView? = null
    private var undoButton: Button? = null
    private var signature = ""

    private val tick = object : Runnable {
        override fun run() {
            val snap = lock.snapshot()
            val sig = signatureOf(snap)
            if (sig != signature) {
                render()
            } else {
                updateUndoLabel(snap)
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun signatureOf(s: ZenSettingsLock.Snapshot) = "${s.setupMode}|${s.lockRemainingMs > 0}|${s.undoRemainingMs > 0}|${s.pending}"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        lock = Zen.settingsLock(requireContext())
        val scroll = ScrollView(requireContext())
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(16)
            setPadding(p, p, p, p)
        }
        scroll.addView(content)
        render()
        return scroll
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.setTitle(R.string.zen_settings_title)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fmtTime(remainingMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(System.currentTimeMillis() + remainingMs))

    private fun fmtValue(def: ZenSettingDef, v: Int): String = when {
        def.isBool -> getString(if (v == 1) R.string.zen_on else R.string.zen_off)
        else -> getString(unitRes(def.key), v)
    }

    private fun unitRes(key: String) = when (key) {
        ZenSettingKeys.FEED_BUDGET, ZenSettingKeys.DAILY_CAP -> R.string.zen_unit_videos
        ZenSettingKeys.PAUSE_PERCENT -> R.string.zen_unit_percent
        ZenSettingKeys.INTENT_WAIT_S -> R.string.zen_unit_sec
        else -> R.string.zen_unit_min
    }

    private fun labelRes(key: String) = when (key) {
        ZenSettingKeys.FEED_BUDGET -> R.string.zen_set_feed_budget
        ZenSettingKeys.DAILY_CAP -> R.string.zen_set_daily_cap
        ZenSettingKeys.MIN_LENGTH_MIN -> R.string.zen_set_min_length
        ZenSettingKeys.PAUSE_ENABLED -> R.string.zen_set_pause_enabled
        ZenSettingKeys.PAUSE_PERCENT -> R.string.zen_set_pause_percent
        ZenSettingKeys.PAUSE_MIN_MIN -> R.string.zen_set_pause_min
        ZenSettingKeys.PAUSE_MAX_MIN -> R.string.zen_set_pause_max
        ZenSettingKeys.PAUSE_TRIGGER_MIN -> R.string.zen_set_pause_trigger
        ZenSettingKeys.INTENT_WAIT_S -> R.string.zen_set_intent_wait
        else -> R.string.zen_set_intent_bg
    }

    private fun text(s: CharSequence, size: Float = 14f, bold: Boolean = false) = TextView(requireContext()).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun render() {
        val snap = lock.snapshot()
        signature = signatureOf(snap)
        val eff = lock.effectiveAll()
        draft.keys.retainAll { it in eff }
        content.removeAllViews()

        // status block
        val status = text("", 14f)
        statusView = status
        status.text = when {
            snap.setupMode -> getString(R.string.zen_lock_setup)
            snap.lockRemainingMs > 0 -> getString(R.string.zen_lock_active, fmtTime(snap.lockRemainingMs))
            else -> getString(R.string.zen_lock_free)
        }
        content.addView(status)
        if (snap.setupMode) {
            content.addView(
                Button(requireContext()).apply {
                    setText(R.string.zen_setup_done)
                    setOnClickListener {
                        lock.completeSetup()
                        draft.clear()
                        render()
                    }
                }
            )
        }
        if (snap.pending.isNotEmpty()) {
            val pendingText = snap.pending.entries.joinToString("\n") { (k, v) ->
                val d = ZenSettingDefs.get(k)!!
                "${getString(labelRes(k))}: ${fmtValue(d, eff.getValue(k))} → ${fmtValue(d, v)}"
            }
            content.addView(text(getString(R.string.zen_pending_title, fmtTime(snap.lockRemainingMs)), 14f, true))
            content.addView(text(pendingText))
            content.addView(
                Button(requireContext()).apply {
                    setText(R.string.zen_pending_cancel)
                    setOnClickListener {
                        lock.cancelPending()
                        render()
                    }
                }
            )
        }
        undoButton = null
        if (snap.undoRemainingMs > 0) {
            undoButton = Button(requireContext()).apply {
                setOnClickListener {
                    if (lock.undo()) {
                        Toast.makeText(requireContext(), R.string.zen_undo_done, Toast.LENGTH_SHORT).show()
                        draft.clear()
                    }
                    render()
                }
            }
            content.addView(undoButton)
            updateUndoLabel(snap)
        }

        // setting rows
        val locked = !snap.setupMode && snap.lockRemainingMs > 0
        ZenSettingDefs.all.forEach { def -> content.addView(row(def, eff, snap, locked)) }

        val changed = changedMap(eff)
        content.addView(
            Button(requireContext()).apply {
                setText(R.string.zen_apply)
                isEnabled = changed.isNotEmpty()
                setOnClickListener { confirm(changed, eff, snap) }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(16) }
        )
    }

    private fun updateUndoLabel(snap: ZenSettingsLock.Snapshot) {
        val s = (snap.undoRemainingMs + 999) / 1000
        undoButton?.text = getString(R.string.zen_undo, String.format("%d:%02d", s / 60, s % 60))
    }

    private fun changedMap(eff: Map<String, Int>): Map<String, Int> = draft.filter { (k, v) -> eff[k] != v }

    private fun row(def: ZenSettingDef, eff: Map<String, Int>, snap: ZenSettingsLock.Snapshot, locked: Boolean): View {
        val current = eff.getValue(def.key)
        val shown = draft[def.key] ?: current
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(4))
        }
        val labels = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(getString(labelRes(def.key)), 16f))
        val valueLine = fmtValue(def, shown) + if (shown != current) "  (${fmtValue(def, current)})" else ""
        labels.addView(text(valueLine, 14f, true))
        snap.pending[def.key]?.let {
            labels.addView(text(getString(R.string.zen_row_pending, fmtValue(def, it), fmtTime(snap.lockRemainingMs)), 12f))
        }
        box.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        fun stepButton(label: String, delta: Int): Button = Button(requireContext()).apply {
            text = label
            val target = shown + delta * def.step
            val inRange = def.inRange(target)
            // while locked the loosening direction is greyed out
            val allowed = inRange && (!locked || def.isStricter(current, target) || target == current)
            isEnabled = allowed
            setOnClickListener {
                draft[def.key] = target
                render()
            }
        }
        if (def.isBool) {
            val other = 1 - shown
            box.addView(
                Button(requireContext()).apply {
                    text = getString(if (other == 1) R.string.zen_turn_on else R.string.zen_turn_off)
                    isEnabled = !locked || def.isStricter(current, other) || other == current
                    setOnClickListener {
                        draft[def.key] = other
                        render()
                    }
                }
            )
        } else {
            box.addView(stepButton("−", -1))
            box.addView(stepButton("+", 1))
        }
        return box
    }

    private fun confirm(changed: Map<String, Int>, eff: Map<String, Int>, snap: ZenSettingsLock.Snapshot) {
        val setup = snap.setupMode
        val now = StringBuilder()
        val later = StringBuilder()
        var anyLooser = false
        changed.forEach { (k, v) ->
            val d = ZenSettingDefs.get(k)!!
            val line = "• ${getString(labelRes(k))}: ${fmtValue(d, eff.getValue(k))} → ${fmtValue(d, v)}\n"
            if (!setup && lock.classify(k, v) == ZenSettingsLock.Kind.LOOSER) {
                anyLooser = true
                later.append(line)
            } else {
                now.append(line)
            }
        }
        val msg = StringBuilder()
        if (now.isNotEmpty()) msg.append(getString(R.string.zen_confirm_now)).append('\n').append(now).append('\n')
        if (later.isNotEmpty()) {
            msg.append(getString(R.string.zen_confirm_later, fmtTime(ZenSettingsRules.LOCK_MS))).append('\n')
                .append(later).append('\n')
        }
        when {
            setup -> msg.append(getString(R.string.zen_confirm_setup))
            anyLooser -> msg.append(getString(R.string.zen_confirm_footer_lock)).append(' ')
            else -> {}
        }
        if (!setup) msg.append(getString(R.string.zen_confirm_footer_undo))

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.zen_confirm_title)
            .setMessage(msg.toString().trim())
            .setPositiveButton(R.string.zen_confirm_ok) { _, _ -> doApply(changed) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = false
        object : android.os.CountDownTimer(ZenSettingsRules.CONFIRM_WAIT_S * 1000L, 500L) {
            override fun onTick(left: Long) {
                ok.text = getString(R.string.zen_confirm_wait, (left + 999) / 1000)
            }

            override fun onFinish() {
                ok.setText(R.string.zen_confirm_ok)
                ok.isEnabled = true
            }
        }.start()
    }

    private fun doApply(changed: Map<String, Int>) {
        when (val r = lock.apply(changed)) {
            is ZenSettingsLock.Result.Rejected -> {
                val snap = lock.snapshot()
                val msg = when (r.reason) {
                    ZenSettingsLock.Reason.LOCKED -> getString(R.string.zen_rejected_locked, fmtTime(snap.lockRemainingMs))
                    ZenSettingsLock.Reason.INVALID_COMBINATION -> getString(R.string.zen_rejected_combo)
                    else -> getString(R.string.zen_rejected_range)
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }

            is ZenSettingsLock.Result.Applied -> {
                draft.clear()
                val msg = if (r.pending.isNotEmpty()) {
                    getString(R.string.zen_applied_pending, fmtTime(lock.snapshot().lockRemainingMs))
                } else {
                    getString(R.string.zen_applied_now)
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }

            ZenSettingsLock.Result.NoChange -> draft.clear()
        }
        render()
    }
}
