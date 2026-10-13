package org.schabi.newpipe.zen

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/**
 * One-time adjustments of upstream preferences for the calm design (zen-docs/spec-design.md).
 * New installs get the calm defaults from the preference XML; existing installs (where
 * NewPipe already stored the old default) are switched once. The user can change them again.
 */
object ZenDesign {
    private const val KEY = "design_prefs_version"
    private const val VERSION = 1

    @JvmStatic
    fun migrate(context: Context) {
        val store = Zen.store(context)
        if (store.getInt(KEY, 0) >= VERSION) return
        // version 1: the media notification is no longer colorized after the thumbnail
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.notification_colorize_key), false)
            .apply()
        store.put(KEY to VERSION)
    }
}
