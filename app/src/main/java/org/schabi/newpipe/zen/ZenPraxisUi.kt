package org.schabi.newpipe.zen

import android.app.Activity
import android.content.Context
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.DateFormat
import java.util.Date
import org.schabi.newpipe.R
import org.schabi.newpipe.zen.classify.ZenPlausibilityCheck

/**
 * Marking a channel or playlist as practice (page menu, long press in lists). Adding is a loosening (confirmation
 * with a short wait, pending for 24 h, see [ZenSettingsLock.addPraxis]); removing applies at once.
 */
object ZenPraxisUi {
    @JvmStatic
    fun fmtTime(remainingMs: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(System.currentTimeMillis() + remainingMs))

    @JvmStatic
    fun menuTitle(context: Context, serviceId: Int, url: String): String = menuTitle(context, serviceId, url, false)

    @JvmStatic
    fun menuTitle(context: Context, serviceId: Int, url: String, playlist: Boolean): String = context.getString(
        when (ZenPraxis.state(context, serviceId, url)) {
            ZenPraxis.State.ACTIVE -> if (playlist) R.string.zen_praxis_pl_unmark else R.string.zen_praxis_unmark
            ZenPraxis.State.PENDING -> if (playlist) R.string.zen_praxis_pl_cancel_pending else R.string.zen_praxis_cancel_pending
            ZenPraxis.State.NONE -> if (playlist) R.string.zen_praxis_pl_mark else R.string.zen_praxis_mark
        }
    )

    /** Menu action on a channel page; [changed] runs after a change (to refresh the menu title). */
    @JvmStatic
    fun onMenu(activity: Activity, serviceId: Int, url: String, name: String, changed: Runnable) = onMenu(activity, serviceId, url, name, false, changed)

    /**
     * Mark/unmark a channel or (remote) playlist ([playlist]) as practice. [sample]: an already
     * finished plausibility sample (channel import sheet), evaluated without loading again.
     */
    @JvmStatic
    @JvmOverloads
    fun onMenu(
        activity: Activity,
        serviceId: Int,
        url: String,
        name: String,
        playlist: Boolean,
        changed: Runnable,
        sample: ZenPlausibilityCheck.Sample? = null
    ) {
        val lock = Zen.settingsLock(activity)
        when (ZenPraxis.state(activity, serviceId, url)) {
            ZenPraxis.State.ACTIVE, ZenPraxis.State.PENDING -> {
                val pending = ZenPraxis.state(activity, serviceId, url) == ZenPraxis.State.PENDING
                lock.removePraxis(ZenPraxisChannel.keyOf(serviceId, url))
                toast(
                    activity,
                    activity.getString(if (pending) R.string.zen_praxis_pending_cancelled else R.string.zen_praxis_removed, name)
                )
                changed.run()
            }

            ZenPraxis.State.NONE -> {
                val snap = lock.snapshot()
                val entry = ZenPraxisChannel(serviceId, url, name, playlist)
                when {
                    // the setup applies at once, but the check runs there too (spec 4.4 step 5)
                    snap.setupMode -> ZenPlausibilityCheck.run(activity, entry, sample) { note ->
                        lock.addPraxis(entry)
                        toast(activity, activity.getString(R.string.zen_praxis_added_setup, name) + (note?.let { "\n$it" } ?: ""))
                        changed.run()
                    }

                    snap.lockRemainingMs > 0 ->
                        toast(activity, activity.getString(R.string.zen_praxis_locked, fmtTime(snap.lockRemainingMs)))

                    // content check first (only warns), then the unchanged confirmation with the lock
                    else -> ZenPlausibilityCheck.run(activity, entry, sample) { note -> confirmAdd(activity, entry, changed, note) }
                }
            }
        }
    }

    // ---------------------------------------------------------------- single practice videos (D61)

    @JvmStatic
    fun videoMenuTitle(context: Context, serviceId: Int, url: String): String = context.getString(
        when (ZenPraxis.state(context, serviceId, url)) {
            ZenPraxis.State.ACTIVE -> R.string.zen_praxis_video_unmark
            ZenPraxis.State.PENDING -> R.string.zen_praxis_video_cancel_pending
            ZenPraxis.State.NONE -> R.string.zen_praxis_video_mark
        }
    )

    /**
     * Mark/unmark one video as practice. Adding is a loosening exactly like a channel (3 s wait,
     * pending 24 h, starts the lock, 5 min undo; rejected while locked; at once in the first-time
     * setup), also when the content recognition agrees (decision Stefan). Removing applies at once.
     */
    @JvmStatic
    fun onVideoMenu(activity: Activity, serviceId: Int, url: String, name: String, changed: Runnable) {
        val lock = Zen.settingsLock(activity)
        when (ZenPraxis.state(activity, serviceId, url)) {
            ZenPraxis.State.ACTIVE, ZenPraxis.State.PENDING -> {
                val pending = ZenPraxis.state(activity, serviceId, url) == ZenPraxis.State.PENDING
                lock.removePraxis(ZenPraxisChannel.keyOf(serviceId, url))
                toast(activity, activity.getString(if (pending) R.string.zen_praxis_pending_cancelled else R.string.zen_praxis_removed, name))
                changed.run()
            }

            ZenPraxis.State.NONE -> {
                val snap = lock.snapshot()
                val entry = ZenPraxisChannel(serviceId, url, name, video = true)
                when {
                    snap.setupMode -> {
                        commitAdd(activity, entry)?.let { toast(activity, it) }
                        changed.run()
                    }

                    snap.lockRemainingMs > 0 -> toast(activity, activity.getString(R.string.zen_praxis_locked, fmtTime(snap.lockRemainingMs)))

                    else -> confirmAdd(activity, entry, changed)
                }
            }
        }
    }

    /** Long press on a video's title (detail page): copy the title, or the practice action. */
    @JvmStatic
    fun showVideoLongPress(activity: Activity, serviceId: Int, url: String, name: String, copyTitle: Runnable) {
        AlertDialog.Builder(activity)
            .setTitle(name)
            .setItems(arrayOf(activity.getString(R.string.zen_copy_title), videoMenuTitle(activity, serviceId, url))) { _, which ->
                if (which == 0) copyTitle.run() else onVideoMenu(activity, serviceId, url, name) {}
            }
            .show()
    }

    /**
     * Long press on a channel or playlist entry of a list (search, subscriptions, channel tabs):
     * a small dialog with the practice action.
     */
    @JvmStatic
    fun showLongPress(activity: Activity, serviceId: Int, url: String, name: String, playlist: Boolean) {
        // channels: also "exception for the content recognition" (spec 4.6), where it is available
        val exempt = !playlist && org.schabi.newpipe.zen.classify.ZenClassifierModel.isSupported()
        val items = listOfNotNull(
            menuTitle(activity, serviceId, url, playlist),
            if (exempt) activity.getString(R.string.zen_exempt_long_press) else null
        )
        AlertDialog.Builder(activity)
            .setTitle(name)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) onMenu(activity, serviceId, url, name, playlist, {}) else ZenClassExemptUi.addChannel(activity, serviceId, url, name)
            }
            .show()
    }

    /** What adding a practice entry would do right now (the lock decides, see [ZenSettingsLock.addPraxis]). */
    enum class AddPlan {
        /** First-time setup: active at once, no confirmation wait. */
        NOW_SETUP,

        /** Normal case: pending for 24 h, starts the lock, needs the confirmation wait. */
        PENDING,

        /** A lock runs: adding (a loosening) is not possible now. */
        LOCKED,

        /** Already active or pending. */
        ALREADY
    }

    @JvmStatic
    fun addPlan(state: ZenPraxis.State, setupMode: Boolean, lockRemainingMs: Long): AddPlan = when {
        state != ZenPraxis.State.NONE -> AddPlan.ALREADY
        setupMode -> AddPlan.NOW_SETUP
        lockRemainingMs > 0 -> AddPlan.LOCKED
        else -> AddPlan.PENDING
    }

    @JvmStatic
    fun addPlan(context: Context, serviceId: Int, url: String): AddPlan {
        val snap = Zen.settingsLock(context).snapshot()
        return addPlan(ZenPraxis.state(context, serviceId, url), snap.setupMode, snap.lockRemainingMs)
    }

    /**
     * The text that explains [plan] for [channel] (the same text as the confirmation dialog):
     * pending 24 h + lock + undo, active at once in the setup, or locked until.
     */
    @JvmStatic
    fun addText(context: Context, channel: ZenPraxisChannel, plan: AddPlan): String = when (plan) {
        AddPlan.NOW_SETUP -> context.getString(R.string.zen_praxis_add_setup_info, channel.name)

        AddPlan.LOCKED -> context.getString(R.string.zen_praxis_locked, fmtTime(Zen.settingsLock(context).snapshot().lockRemainingMs))

        AddPlan.ALREADY -> ""

        AddPlan.PENDING -> context.getString(
            when {
                channel.video -> R.string.zen_praxis_video_confirm_message
                channel.playlist -> R.string.zen_praxis_pl_confirm_message
                else -> R.string.zen_praxis_confirm_message
            },
            channel.name,
            fmtTime(ZenSettingsRules.LOCK_MS)
        ) + "\n\n" + context.getString(R.string.zen_confirm_footer_lock) + " " + context.getString(R.string.zen_confirm_footer_undo)
    }

    /**
     * Adds [channel] through the lock (unchanged rules) and returns the message to show, null if
     * nothing changed. Used by the confirmation dialog and by the channel import sheet.
     */
    @JvmStatic
    fun commitAdd(context: Context, channel: ZenPraxisChannel): String? {
        val lock = Zen.settingsLock(context)
        return when (val r = lock.addPraxis(channel)) {
            is ZenSettingsLock.Result.Applied -> if (r.setupMode) {
                context.getString(R.string.zen_praxis_added_setup, channel.name)
            } else {
                context.getString(
                    when {
                        channel.video -> R.string.zen_praxis_video_added_pending
                        channel.playlist -> R.string.zen_praxis_pl_added_pending
                        else -> R.string.zen_praxis_added_pending
                    },
                    channel.name,
                    fmtTime(lock.snapshot().lockRemainingMs)
                )
            }

            is ZenSettingsLock.Result.Rejected -> context.getString(R.string.zen_praxis_locked, fmtTime(lock.snapshot().lockRemainingMs))

            ZenSettingsLock.Result.NoChange -> null
        }
    }

    // ---------------------------------------------------------------- practice themes (D61)

    @JvmStatic
    fun themeName(context: Context, t: ZenPraxisTheme): String = context.getString(
        when (t) {
            ZenPraxisTheme.FITNESS -> R.string.zen_praxis_theme_fitness
            ZenPraxisTheme.YOGA -> R.string.zen_praxis_theme_yoga
            ZenPraxisTheme.SLEEP -> R.string.zen_praxis_theme_sleep
            ZenPraxisTheme.MEDITATION -> R.string.zen_praxis_theme_meditation
            ZenPraxisTheme.BREATH -> R.string.zen_praxis_theme_breath
        }
    )

    /** "Yoga, Breathwork", or [none] for no theme. */
    @JvmStatic
    fun themesText(context: Context, mask: Int, none: String = context.getString(R.string.zen_praxis_theme_none)): String = ZenPraxisThemes.themes(mask).joinToString(", ") { themeName(context, it) }.ifEmpty { none }

    /**
     * Checkboxes of the five themes (at most [ZenPraxisThemes.MAX] can be ticked) under [message]
     * and a caption; [current] returns the ticked mask.
     */
    private class ThemeBoxes(val view: android.view.View, val current: () -> Int)

    private fun themeBoxes(activity: Activity, message: String, caption: String, initial: Int): ThemeBoxes {
        val d = activity.resources.displayMetrics.density
        val pad = (20 * d).toInt()
        val box = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, (12 * d).toInt(), pad, 0)
        }
        if (message.isNotEmpty()) box.addView(android.widget.TextView(activity).apply { text = message })
        box.addView(
            android.widget.TextView(activity).apply {
                text = caption
                setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        )
        val checks = ZenPraxisTheme.entries.map { t ->
            android.widget.CheckBox(activity).apply {
                text = themeName(activity, t)
                isChecked = ZenPraxisThemes.has(initial, t)
            }
        }
        checks.forEach { c ->
            c.setOnCheckedChangeListener { b, on ->
                if (on && checks.count { it.isChecked } > ZenPraxisThemes.MAX) {
                    b.isChecked = false
                    toast(activity, activity.getString(R.string.zen_praxis_theme_max, ZenPraxisThemes.MAX))
                }
            }
            box.addView(c)
        }
        val scroll = android.widget.ScrollView(activity).apply { addView(box) }
        return ThemeBoxes(scroll) { ZenPraxisThemes.mask(ZenPraxisTheme.entries.filterIndexed { i, _ -> checks[i].isChecked }) }
    }

    /**
     * Multi-choice of the practice themes ([initial] ticked). [onPick] gets the mask (0 = none);
     * [onRemove] (optional, e.g. "remove correction") adds a neutral button.
     */
    @JvmStatic
    @JvmOverloads
    fun pickThemes(activity: Activity, title: String, message: String, initial: Int, onPick: (Int) -> Unit, onRemove: (() -> Unit)? = null) {
        if (activity.isFinishing) return
        val boxes = themeBoxes(activity, message, activity.getString(R.string.zen_praxis_theme_pick_caption), initial)
        val b = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(boxes.view)
            .setPositiveButton(R.string.zen_review_save) { _, _ -> onPick(boxes.current()) }
            .setNegativeButton(R.string.cancel, null)
        if (onRemove != null) b.setNeutralButton(R.string.zen_review_remove) { _, _ -> onRemove() }
        b.show()
    }

    /** Default theme(s) of an active or pending entry: neutral, at once ([ZenSettingsLock.setPraxisThemes]). */
    @JvmStatic
    fun pickDefaultThemes(activity: Activity, entry: ZenPraxisChannel, changed: Runnable) {
        pickThemes(activity, entry.name.ifEmpty { entry.url }, activity.getString(R.string.zen_praxis_theme_default_info), entry.themes, { mask ->
            if (Zen.settingsLock(activity).setPraxisThemes(entry.key, mask)) changed.run()
        })
    }

    private fun confirmAdd(activity: Activity, channel: ZenPraxisChannel, changed: Runnable, note: String? = null) {
        if (activity.isFinishing) return
        // optional default theme(s) in the same dialog (D61); only grouping, the lock is unchanged
        val boxes = themeBoxes(
            activity,
            (note?.let { "$it\n\n" } ?: "") + addText(activity, channel, AddPlan.PENDING),
            activity.getString(R.string.zen_praxis_theme_default_caption),
            channel.themes
        )
        val dialog = AlertDialog.Builder(activity)
            .setTitle(
                when {
                    channel.video -> R.string.zen_praxis_video_confirm_title
                    channel.playlist -> R.string.zen_praxis_pl_confirm_title
                    else -> R.string.zen_praxis_confirm_title
                }
            )
            .setView(boxes.view)
            .setPositiveButton(R.string.zen_confirm_ok) { _, _ ->
                commitAdd(activity, channel.copy(themes = boxes.current()))?.let { toast(activity, it) }
                changed.run()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = false
        object : CountDownTimer(ZenSettingsRules.CONFIRM_WAIT_S * 1000L, 500L) {
            override fun onTick(left: Long) {
                ok.text = activity.getString(R.string.zen_confirm_wait, (left + 999) / 1000)
            }

            override fun onFinish() {
                ok.setText(R.string.zen_confirm_ok)
                ok.isEnabled = true
            }
        }.start()
    }

    private fun toast(context: Context, msg: String) = Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show()
}
