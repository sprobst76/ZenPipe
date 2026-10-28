package org.schabi.newpipe.zen

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.UUID
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity

/**
 * Reset protection: "clear data" or a reinstall followed by a restore of the NewPipe data (ZIP
 * import, restored newpipe.db, auto-backup) must not give a free first-time setup. zen_state is
 * deliberately never exported or backed up, so a restore is recognised by what comes back:
 *  - [Reason.DATA]: on the very first start of the Zen settings the database already holds
 *    subscriptions, history or playlists (probe off the main thread, see [ZenRestore]);
 *  - [Reason.IMPORT]: a NewPipe data import finished while the setup runs;
 *  - [Reason.MARKER]: the default preferences carry the install marker of another (older)
 *    install; the marker travels in the NewPipe settings export and in auto-backup.
 * Then [ZenSettingsLock.endSetupByRestore] ends the setup like "Done": stricter stays immediate,
 * loosening takes the 24 h way. Subscribing during the setup changes nothing (only the first probe
 * looks at the database). Pure logic, testable through [ZenKeyValueStore].
 */
class ZenRestoreGuard(
    private val store: ZenKeyValueStore,
    private val lock: ZenSettingsLock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val log: ((String, String) -> Unit)? = null
) {
    enum class Reason(val key: String) { DATA("data"), IMPORT("import"), MARKER("marker") }

    /** [marker]: value to keep in the default preferences; [probe]: count the database rows now. */
    data class Start(val marker: String, val probe: Boolean)

    /** At every app start (fast, no database). [prefsMarker] = marker found in the default preferences. */
    @Synchronized
    fun onAppStart(prefsMarker: String?): Start {
        val fresh = lock.isSetupUntouched() && store.getString(K_ID, null) == null
        val id = store.getString(K_ID, null) ?: newId().also { store.put(K_ID to it) }
        if (fresh) store.put(K_PROBE to 1)
        if (!prefsMarker.isNullOrEmpty() && prefsMarker != id) detect(Reason.MARKER)
        return Start(id, store.getInt(K_PROBE, 0) == 1)
    }

    /**
     * Result of the first-start probe: number of rows in subscriptions, history and playlists.
     * Only the first successful probe counts (a probe that never finished runs again next start).
     */
    @Synchronized
    fun onProbeResult(rows: Long) {
        if (store.getInt(K_PROBE, 0) != 1) return
        store.put(K_PROBE to null)
        if (rows > 0) detect(Reason.DATA)
    }

    /** A NewPipe data import finished (database replaced), right before the app restarts. */
    @Synchronized
    fun onImportFinished() {
        detect(Reason.IMPORT)
        store.flush()
    }

    /** Show the hint "restore detected" (while the lock started by the restore runs). */
    @Synchronized
    fun showNotice(lockRemainingMs: Long): Boolean {
        if (store.getInt(K_NOTICE, 0) != 1) return false
        if (lockRemainingMs > 0) return true
        store.put(K_NOTICE to null)
        return false
    }

    private fun detect(reason: Reason) {
        store.put(K_PROBE to null)
        if (!lock.endSetupByRestore()) return
        store.put(K_NOTICE to 1)
        log?.invoke("settings_restore_detected", "reason=${reason.key}")
    }

    companion object {
        /** Random id of this install; also stored as [ZenRestore.PREF_MARKER] in the default preferences. */
        const val K_ID = "zr_install_id"

        /** 1 = the first-start database probe is still due. */
        const val K_PROBE = "zr_probe"

        /** 1 = a restore ended the setup (hint in Settings > Zen). */
        const val K_NOTICE = "zr_notice"
    }
}

/** Android side of [ZenRestoreGuard]: default preferences, database probe, import hook. */
object ZenRestore {
    /** Harmless marker in NewPipe's default preferences (travels in the settings export). */
    const val PREF_MARKER = "zen_install_marker"

    @Volatile
    private var guard: ZenRestoreGuard? = null

    @JvmStatic
    fun guard(context: Context): ZenRestoreGuard {
        return guard ?: synchronized(this) {
            guard ?: run {
                val app = context.applicationContext
                ZenRestoreGuard(Zen.store(app), Zen.settingsLock(app), log = { t, d -> Zen.log(app).add(t, d) })
            }.also { guard = it }
        }
    }

    /** From [ZenSettings.init]: marker check and, on the very first start, the database probe. */
    @JvmStatic
    fun onAppStart(context: Context) {
        val app = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val found = prefs.getString(PREF_MARKER, null)
        val start = guard(app).onAppStart(found)
        if (found != start.marker) prefs.edit().putString(PREF_MARKER, start.marker).apply()
        if (!start.probe) return
        Thread({
            // unknown on error: the probe stays due and runs again at the next start
            val rows = try {
                countRows(app)
            } catch (e: Exception) {
                -1L
            }
            if (rows >= 0) guard(app).onProbeResult(rows)
        }, "zen-restore-probe").start()
    }

    /** Hook after a NewPipe data import (Settings > Backup and restore). */
    @JvmStatic
    fun onImportFinished(context: Context) = guard(context).onImportFinished()

    private fun countRows(context: Context): Long {
        val tables = listOf(
            SubscriptionEntity.SUBSCRIPTION_TABLE,
            StreamHistoryEntity.STREAM_HISTORY_TABLE,
            PlaylistEntity.PLAYLIST_TABLE,
            PlaylistRemoteEntity.REMOTE_PLAYLIST_TABLE
        )
        val sql = "SELECT " + tables.joinToString(" + ") { "(SELECT COUNT(*) FROM $it)" }
        NewPipeDatabase.getInstance(context).query(sql, null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else -1L
        }
    }
}
