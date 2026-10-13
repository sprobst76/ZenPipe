package org.schabi.newpipe.zen

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R

/**
 * Sleep timer UI: moon button in the player's top bar (main and popup player, shows the countdown)
 * and a menu entry in the play queue screen (background player). Both offer the same choices.
 */
object ZenSleepUi {
    private const val MENU_GROUP = 7301

    @JvmStatic
    fun format(ms: Long): String {
        val s = (ms + 999) / 1000
        return if (s >= 3600) {
            "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
        } else {
            "%d:%02d".format(s / 60, s % 60)
        }
    }

    /** Button text: the countdown while the timer runs, nothing otherwise (icon only). */
    @JvmStatic
    fun updateButton(view: TextView) {
        val left = ZenSleep.remainingMs()
        view.text = if (left >= 0) format(left) else ""
    }

    /** Label + duration (ms); 0 = switch off. The default (Settings > Zen) comes first. */
    private fun entries(context: Context): List<Pair<String, Long>> {
        val list = ArrayList<Pair<String, Long>>()
        if (ZenSleep.isRunning()) list.add(context.getString(R.string.zen_sleep_off) to 0L)
        val def = ZenSleep.defaultMinutes(context)
        if (def > 0) list.add(context.getString(R.string.zen_sleep_default_entry, def) to def * 60_000L)
        ZenSleepTimer.CHOICES_MIN.filter { it != def }.forEach {
            list.add(context.getString(R.string.zen_sleep_minutes, it) to it * 60_000L)
        }
        if (BuildConfig.DEBUG) list.add("1 min (debug)" to ZenSleepTimer.DEBUG_CHOICE_MS)
        return list
    }

    private fun title(context: Context): String {
        val left = ZenSleep.remainingMs()
        return if (left >= 0) {
            context.getString(R.string.zen_sleep_running, format(left))
        } else {
            context.getString(R.string.zen_sleep_timer)
        }
    }

    private fun choose(context: Context, ms: Long) {
        val app = context.applicationContext
        if (ms <= 0) {
            ZenSleep.cancel()
            Toast.makeText(app, R.string.zen_sleep_cancelled, Toast.LENGTH_SHORT).show()
        } else {
            ZenSleep.start(ms)
            Toast.makeText(app, app.getString(R.string.zen_sleep_set, format(ms)), Toast.LENGTH_SHORT).show()
        }
    }

    /** Popup menu anchored at the player button (works with a service context, popup player). */
    @JvmStatic
    fun showMenu(context: Context, anchor: View) {
        val items = entries(context)
        val menu = PopupMenu(context, anchor)
        menu.menu.add(MENU_GROUP, -1, 0, title(context)).isEnabled = false
        items.forEachIndexed { i, (label, _) -> menu.menu.add(MENU_GROUP, i, i + 1, label) }
        menu.setOnMenuItemClickListener { item ->
            items.getOrNull(item.itemId)?.let { choose(context, it.second) }
            true
        }
        menu.show()
    }

    /** Dialog for screens with an activity (play queue / background player). */
    @JvmStatic
    fun showDialog(activity: Activity) {
        val items = entries(activity)
        AlertDialog.Builder(activity)
            .setTitle(title(activity))
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> choose(activity, items[which].second) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Settings > Zen: the duration offered first (no lock: comfort, not a Zen rule). */
    @JvmStatic
    fun showDefaultDialog(activity: Activity, changed: Runnable) {
        val values = intArrayOf(0) + ZenSleepTimer.CHOICES_MIN
        val labels = values.map {
            if (it == 0) activity.getString(R.string.zen_sleep_default_none) else activity.getString(R.string.zen_sleep_minutes, it)
        }.toTypedArray()
        val current = values.indexOf(ZenSleep.defaultMinutes(activity)).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(R.string.zen_sleep_default_title)
            .setSingleChoiceItems(labels, current) { d, which ->
                ZenSleep.setDefaultMinutes(activity, values[which])
                d.dismiss()
                changed.run()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @JvmStatic
    fun defaultLabel(context: Context): String {
        val def = ZenSleep.defaultMinutes(context)
        return context.getString(
            R.string.zen_sleep_default_row,
            if (def > 0) context.getString(R.string.zen_sleep_minutes, def) else context.getString(R.string.zen_sleep_default_none)
        )
    }
}
