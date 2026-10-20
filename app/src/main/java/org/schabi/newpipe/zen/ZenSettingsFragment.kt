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
    private var classifier: org.schabi.newpipe.zen.classify.ZenClassifierSection? = null

    private val tick = object : Runnable {
        override fun run() {
            val snap = lock.snapshot()
            val sig = signatureOf(snap)
            if (sig != signature) {
                render()
            } else {
                updateUndoLabel(snap)
                classifier?.tick()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun signatureOf(s: ZenSettingsLock.Snapshot) = "${s.setupMode}|${s.lockRemainingMs > 0}|${s.undoRemainingMs > 0}|${s.pending}|" +
        "${s.praxis.map { it.key + it.themes }}|${s.praxisPending.map { it.key + it.themes }}|${s.exempt.map { it.key }}|${s.exemptPending.map { it.key }}"

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

    private fun langNames(mask: Int): String {
        val locale = androidx.core.os.ConfigurationCompat.getLocales(resources.configuration)[0] ?: java.util.Locale.getDefault()
        return ZenLangOptions.codes(mask).joinToString(", ") { ZenLangOptions.name(it, locale) }
    }

    private fun fmtValue(def: ZenSettingDef, v: Int): String = when {
        def.isMask -> langNames(v)
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
        ZenSettingKeys.REDUCED_DETAILS -> R.string.zen_set_reduced_details
        ZenSettingKeys.CLASS_FEED -> R.string.zen_set_class_feed
        ZenSettingKeys.CLASS_HEAVY -> R.string.zen_set_class_heavy
        ZenSettingKeys.FEED_LANGS -> R.string.zen_set_feed_langs
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
                    setOnClickListener { finishSetup() }
                }
            )
        }
        if (snap.pending.isNotEmpty() || snap.praxisPending.isNotEmpty() || snap.exemptPending.isNotEmpty()) {
            val pendingText = (
                snap.pending.entries.map { (k, v) ->
                    val d = ZenSettingDefs.get(k)!!
                    "${getString(labelRes(k))}: ${fmtValue(d, eff.getValue(k))} → ${fmtValue(d, v)}"
                } + snap.praxisPending.map { getString(R.string.zen_praxis_pending_line, it.name) } +
                    snap.exemptPending.map { getString(R.string.zen_exempt_pending_line, it.label) }
                ).joinToString("\n")
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
        val classifierOk = org.schabi.newpipe.zen.classify.ZenClassifierModel.isSupported()
        ZenSettingDefs.all.filter { classifierOk || it.key !in ZenSettingKeys.CLASSIFIER }
            .forEach { def -> content.addView(row(def, eff, snap, locked)) }

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
        renderLanguages()
        renderPraxis(snap)
        renderSleep()
        renderWatchTime()
        renderQuotes()
        classifier = org.schabi.newpipe.zen.classify.ZenClassifierSection(requireActivity()) { render() }
            .also { it.render(content) }
    }

    /**
     * "Done" of the first-time setup. Changes not applied yet would be lost and from now on every
     * loosening takes 24 h: then ask first and name them; without open changes finish at once.
     */
    private fun finishSetup() {
        val eff = lock.effectiveAll()
        val changed = changedMap(eff)
        val done = {
            lock.completeSetup()
            draft.clear()
            render()
        }
        if (changed.isEmpty()) {
            done()
            return
        }
        val list = changed.entries.joinToString("\n") { (k, v) ->
            val d = ZenSettingDefs.get(k)!!
            "${getString(labelRes(k))}: ${fmtValue(d, eff.getValue(k))} → ${fmtValue(d, v)}"
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.zen_setup_done_confirm_title)
            .setMessage(getString(R.string.zen_setup_done_confirm_message, list))
            .setNegativeButton(R.string.zen_setup_done_confirm_back, null)
            .setPositiveButton(R.string.zen_setup_done_confirm_discard) { _, _ -> done() }
            .show()
    }

    /** Own pause quotes: add/remove and "only own quotes" (comfort, no lock). */
    private fun renderQuotes() {
        val quotes = ZenQuotes.of(requireContext())
        val own = quotes.custom()
        content.addView(
            text(getString(R.string.zen_quotes_title), 16f, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(24) }
        )
        content.addView(text(getString(R.string.zen_quotes_hint), 12f))
        if (own.isEmpty()) content.addView(text(getString(R.string.zen_quotes_none)))
        own.forEach { q ->
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(4))
            }
            box.addView(text(q, 15f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(
                Button(requireContext()).apply {
                    setText(R.string.zen_quotes_remove)
                    setOnClickListener {
                        quotes.removeCustom(q)
                        render()
                    }
                }
            )
            content.addView(box)
        }
        val buttons = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(
            Button(requireContext()).apply {
                setText(R.string.zen_quotes_add)
                isEnabled = own.size < ZenQuotes.MAX_CUSTOM
                setOnClickListener { addQuoteDialog(quotes) }
            }
        )
        buttons.addView(
            Button(requireContext()).apply {
                setText(if (quotes.onlyCustom()) R.string.zen_quotes_only_own_on else R.string.zen_quotes_only_own_off)
                isEnabled = own.isNotEmpty() || quotes.onlyCustom()
                setOnClickListener {
                    quotes.setOnlyCustom(!quotes.onlyCustom())
                    render()
                }
            }
        )
        content.addView(buttons)
    }

    private fun addQuoteDialog(quotes: ZenQuotes) {
        val input = android.widget.EditText(requireContext()).apply {
            setHint(R.string.zen_quotes_add_hint)
            minLines = 2
            filters = arrayOf(android.text.InputFilter.LengthFilter(ZenQuotes.MAX_LENGTH))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val frame = android.widget.FrameLayout(requireContext()).apply {
            val p = dp(20)
            setPadding(p, dp(8), p, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.zen_quotes_add)
            .setView(frame)
            .setPositiveButton(R.string.zen_quotes_save) { _, _ ->
                if (!quotes.addCustom(input.text.toString())) {
                    Toast.makeText(requireContext(), R.string.zen_quotes_not_added, Toast.LENGTH_SHORT).show()
                }
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Sleep timer: the duration offered first in the player (comfort, no lock). */
    private fun renderSleep() {
        content.addView(
            text(getString(R.string.zen_sleep_timer), 16f, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(24) }
        )
        content.addView(text(getString(R.string.zen_sleep_settings_hint), 12f))
        content.addView(
            Button(requireContext()).apply {
                text = ZenSleepUi.defaultLabel(requireContext())
                setOnClickListener { ZenSleepUi.showDefaultDialog(requireActivity()) { render() } }
            }
        )
    }

    /** Playing time of today (picture/sound/music/practice), display only, no limit. */
    private fun renderWatchTime() {
        content.addView(
            text(getString(R.string.zen_watch_title), 16f, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(24) }
        )
        val d = ZenPause.watchToday(requireContext())
        fun m(ms: Long) = (ms / 60_000L).toInt()
        content.addView(text(getString(R.string.zen_watch_today, m(d.pictureMs), m(d.soundMs), m(d.musicMs), m(d.praxisMs))))
        content.addView(text(getString(R.string.zen_watch_hint), 12f))
    }

    /** Practice channels: list with state, remove/cancel (both immediate), minutes of today. */
    private fun renderPraxis(snap: ZenSettingsLock.Snapshot) {
        content.addView(
            text(getString(R.string.zen_praxis_settings_title), 16f, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(24) }
        )
        content.addView(text(getString(R.string.zen_praxis_today, (ZenPraxis.todayMs(requireContext()) / 60_000L).toInt())))
        content.addView(text(getString(R.string.zen_praxis_settings_hint), 12f))
        if (snap.praxis.isEmpty() && snap.praxisPending.isEmpty()) {
            content.addView(text(getString(R.string.zen_praxis_settings_none)))
        }
        fun channelRow(ch: ZenPraxisChannel, pending: Boolean) {
            val box = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(4))
            }
            val labels = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(ch.name.ifEmpty { ch.url }, 16f))
            labels.addView(
                text(
                    (
                        when {
                            ch.video -> getString(R.string.zen_praxis_state_video) + " · "
                            ch.playlist -> getString(R.string.zen_praxis_state_playlist) + " · "
                            else -> ""
                        }
                        ) +
                        if (pending) {
                            getString(R.string.zen_praxis_state_pending, fmtTime(snap.lockRemainingMs))
                        } else {
                            getString(R.string.zen_praxis_state_active)
                        },
                    12f
                )
            )
            // default practice theme(s) (D61): neutral, changeable at once by tapping the entry
            labels.addView(text(getString(R.string.zen_praxis_theme_default_line, ZenPraxisUi.themesText(requireContext(), ch.themes)), 12f))
            labels.setOnClickListener { ZenPraxisUi.pickDefaultThemes(requireActivity(), ch) { render() } }
            box.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            box.addView(
                Button(requireContext()).apply {
                    setText(if (pending) R.string.zen_praxis_cancel else R.string.zen_praxis_remove)
                    setOnClickListener {
                        lock.removePraxis(ch.key)
                        render()
                    }
                }
            )
            content.addView(box)
        }
        snap.praxis.forEach { channelRow(it, false) }
        snap.praxisPending.forEach { channelRow(it, true) }
    }

    private companion object {
        /** Language entries scanned for the "hidden" list, and its time window (feed: 7 days). */
        const val HIDDEN_SCAN = 2000
        const val HIDDEN_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
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
        // content switches without a working model: visible that they do nothing yet (spec 4.7)
        if (def.key in ZenSettingKeys.CLASSIFIER && !org.schabi.newpipe.zen.classify.ZenClassifierModel.isReady(requireContext())) {
            labels.addView(text(getString(R.string.zen_class_switch_no_model), 12f))
        }
        snap.pending[def.key]?.let {
            labels.addView(text(getString(R.string.zen_row_pending, fmtValue(def, it), fmtTime(snap.lockRemainingMs)), 12f))
        }
        box.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        fun stepButton(label: String, delta: Int): Button = Button(requireContext()).apply {
            text = label
            // TalkBack: "Decrease videos per edition" instead of "minus"
            contentDescription = getString(if (delta < 0) R.string.zen_step_decrease else R.string.zen_step_increase, getString(labelRes(def.key)))
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
        if (def.isMask) {
            box.addView(
                Button(requireContext()).apply {
                    setText(R.string.zen_lang_change)
                    setOnClickListener { chooseLanguages(def, current, shown, locked) }
                }
            )
        } else if (def.isBool) {
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

    /**
     * "Languages in the feed": choose the allowed languages. Removing one is stricter (at once);
     * adding one is a loosening and not possible while the lock runs. At least one stays.
     */
    private fun chooseLanguages(def: ZenSettingDef, current: Int, shown: Int, locked: Boolean) {
        val locale = androidx.core.os.ConfigurationCompat.getLocales(resources.configuration)[0] ?: java.util.Locale.getDefault()
        val codes = ZenLangOptions.CODES
        val names = codes.map { ZenLangOptions.name(it, locale) }.toTypedArray()
        val checked = BooleanArray(codes.size) { shown and (1 shl it) != 0 }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.zen_set_feed_langs)
            .setMultiChoiceItems(names, checked) { d, which, isChecked ->
                if (isChecked && locked && current and (1 shl which) == 0) {
                    // adding a language is a loosening: not while the lock runs
                    checked[which] = false
                    (d as AlertDialog).listView.setItemChecked(which, false)
                    Toast.makeText(requireContext(), getString(R.string.zen_lang_locked, fmtTime(lock.snapshot().lockRemainingMs)), Toast.LENGTH_LONG).show()
                } else {
                    checked[which] = isChecked
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val mask = checked.withIndex().filter { it.value }.fold(0) { m, i -> m or (1 shl i.index) }
                if (!def.inRange(mask)) {
                    Toast.makeText(requireContext(), R.string.zen_lang_none, Toast.LENGTH_LONG).show()
                } else {
                    draft[def.key] = mask
                    render()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Feed languages: hint and the videos the filter keeps out of the feed right now. */
    private fun renderLanguages() {
        content.addView(
            text(getString(R.string.zen_lang_title), 16f, true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(24) }
        )
        content.addView(text(getString(R.string.zen_lang_hint), 12f))
        content.addView(
            Button(requireContext()).apply {
                setText(R.string.zen_lang_hidden_show)
                setOnClickListener { showHiddenLanguages() }
            }
        )
    }

    private fun showHiddenLanguages() {
        val app = requireContext().applicationContext
        Thread {
            val now = System.currentTimeMillis()
            val store = org.schabi.newpipe.zen.classify.ZenClassCache.get(app)
            val recent = store.recentLangs(HIDDEN_SCAN).filter { it.atMillis >= now - HIDDEN_WINDOW_MS }
            val results = ZenFeedLanguages.decide(store, recent, ZenSettings.feedLanguages()).values
                .filter { it.decision.verdict == ZenLangFilter.Verdict.HIDDEN || it.decision.verdict == ZenLangFilter.Verdict.UNCERTAIN }
                .sortedWith(compareBy<ZenFeedLanguages.Result> { !it.decision.hidden }.thenByDescending { it.entry.atMillis })
            val ctx = activity ?: return@Thread
            val body = if (results.isEmpty()) {
                ctx.getString(R.string.zen_lang_hidden_none)
            } else {
                ctx.getString(R.string.zen_lang_hidden_hint) + "\n\n" +
                    results.joinToString("\n\n") { "${it.entry.title} — ${it.entry.channelName}\n${ZenFeedLanguages.whyLine(ctx, it)}" }
            }
            ctx.runOnUiThread {
                if (!ctx.isFinishing) {
                    val tv = text(body, 12f).apply {
                        setTextIsSelectable(true)
                        setPadding(dp(20), dp(8), dp(20), 0)
                    }
                    AlertDialog.Builder(ctx)
                        .setTitle(R.string.zen_lang_hidden_show)
                        .setView(ScrollView(ctx).apply { addView(tv) })
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }.start()
    }

    private fun confirm(changed: Map<String, Int>, eff: Map<String, Int>, snap: ZenSettingsLock.Snapshot) {
        val setup = snap.setupMode
        val now = StringBuilder()
        val later = StringBuilder()
        var anyLooser = false
        changed.forEach { (k, v) ->
            val d = ZenSettingDefs.get(k)!!
            val line = "• ${getString(labelRes(k))}: ${fmtValue(d, eff.getValue(k))} → ${fmtValue(d, v)}\n"
            val inter = eff.getValue(k) and v
            if (!setup && d.isMask && lock.classify(k, v) == ZenSettingsLock.Kind.LOOSER && inter != eff.getValue(k) && d.inRange(inter)) {
                // languages added and removed: the removal applies now, the addition later
                anyLooser = true
                now.append("• ${getString(labelRes(k))}: ${fmtValue(d, eff.getValue(k))} → ${fmtValue(d, inter)}\n")
                later.append("• ${getString(labelRes(k))}: ${fmtValue(d, inter)} → ${fmtValue(d, v)}\n")
            } else if (!setup && lock.classify(k, v) == ZenSettingsLock.Kind.LOOSER) {
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
