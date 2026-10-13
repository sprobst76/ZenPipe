package org.schabi.newpipe.zen

import android.app.Activity
import android.content.Context
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.DateFormat
import java.util.Date
import org.schabi.newpipe.R

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

    /** Mark/unmark a channel or (remote) playlist ([playlist]) as practice. */
    @JvmStatic
    fun onMenu(activity: Activity, serviceId: Int, url: String, name: String, playlist: Boolean, changed: Runnable) {
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
                    snap.setupMode -> {
                        lock.addPraxis(entry)
                        toast(activity, activity.getString(R.string.zen_praxis_added_setup, name))
                        changed.run()
                    }

                    snap.lockRemainingMs > 0 ->
                        toast(activity, activity.getString(R.string.zen_praxis_locked, fmtTime(snap.lockRemainingMs)))

                    else -> confirmAdd(activity, entry, changed)
                }
            }
        }
    }

    /**
     * Long press on a channel or playlist entry of a list (search, subscriptions, channel tabs):
     * a small dialog with the practice action.
     */
    @JvmStatic
    fun showLongPress(activity: Activity, serviceId: Int, url: String, name: String, playlist: Boolean) {
        AlertDialog.Builder(activity)
            .setTitle(name)
            .setItems(arrayOf(menuTitle(activity, serviceId, url, playlist))) { _, _ ->
                onMenu(activity, serviceId, url, name, playlist) { }
            }
            .show()
    }

    private fun confirmAdd(activity: Activity, channel: ZenPraxisChannel, changed: Runnable) {
        val lock = Zen.settingsLock(activity)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (channel.playlist) R.string.zen_praxis_pl_confirm_title else R.string.zen_praxis_confirm_title)
            .setMessage(
                activity.getString(
                    if (channel.playlist) R.string.zen_praxis_pl_confirm_message else R.string.zen_praxis_confirm_message,
                    channel.name,
                    fmtTime(ZenSettingsRules.LOCK_MS)
                ) +
                    "\n\n" + activity.getString(R.string.zen_confirm_footer_lock) + " " +
                    activity.getString(R.string.zen_confirm_footer_undo)
            )
            .setPositiveButton(R.string.zen_confirm_ok) { _, _ ->
                when (val r = lock.addPraxis(channel)) {
                    is ZenSettingsLock.Result.Applied -> toast(
                        activity,
                        activity.getString(
                            if (channel.playlist) R.string.zen_praxis_pl_added_pending else R.string.zen_praxis_added_pending,
                            channel.name,
                            fmtTime(lock.snapshot().lockRemainingMs)
                        )
                    )

                    is ZenSettingsLock.Result.Rejected -> toast(
                        activity,
                        activity.getString(R.string.zen_praxis_locked, fmtTime(lock.snapshot().lockRemainingMs))
                    )

                    ZenSettingsLock.Result.NoChange -> {}
                }
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
