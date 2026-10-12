package org.schabi.newpipe.zen

import android.content.Context
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager

/**
 * Budgeted "watch later" list: a normal local playlist, at most [MAX_ITEMS] entries.
 * Entries added through the feed expire after [EXPIRY_MS] (30 days); the add time is kept in
 * the Zen preferences, keyed by video URL.
 */
object ZenWatchLater {
    const val MAX_ITEMS = 10
    const val EXPIRY_MS = 30L * 24 * 60 * 60 * 1000
    private const val KEY_PLAYLIST_ID = "later_playlist_id"
    private const val KEY_ADDED = "later_added"

    sealed class Result {
        object Added : Result()
        data class Full(val max: Int) : Result()
    }

    /** Blocking, call from a background thread. */
    fun add(context: Context, stream: StreamEntity): Result {
        val store = Zen.store(context)
        val now = Zen.clock(context).now().trustedMillis
        purgeExpired(context, now)
        val manager = LocalPlaylistManager(NewPipeDatabase.getInstance(context))
        val knownId = store.getLong(KEY_PLAYLIST_ID, -1)
        val existing = manager.playlists.blockingFirst().firstOrNull { it.uid == knownId }

        if (existing != null) {
            if (existing.streamCount >= MAX_ITEMS) return Result.Full(MAX_ITEMS)
            manager.appendToPlaylist(existing.uid, listOf(stream)).blockingGet()
        } else {
            val name = context.getString(R.string.zen_later_playlist_name)
            manager.createPlaylist(name, listOf(stream)).blockingGet()
            val created = manager.playlists.blockingFirst().firstOrNull { it.orderingName == name }
            created?.let { store.put(KEY_PLAYLIST_ID to it.uid) }
        }
        writeAdded(store, readAdded(store) + (stream.url to now))
        return Result.Added
    }

    /** Removes entries older than [EXPIRY_MS] from the list. Blocking. */
    fun purgeExpired(context: Context, nowMillis: Long) {
        val store = Zen.store(context)
        val added = readAdded(store)
        val expired = added.filterValues { nowMillis - it > EXPIRY_MS }.keys
        if (expired.isEmpty()) return
        val playlistId = store.getLong(KEY_PLAYLIST_ID, -1)
        if (playlistId >= 0) {
            val manager = LocalPlaylistManager(NewPipeDatabase.getInstance(context))
            val entries = manager.getPlaylistStreams(playlistId).blockingFirst()
            val keep = entries.filter { it.streamEntity.url !in expired }.map { it.streamId }
            if (keep.size != entries.size) {
                manager.updateJoin(playlistId, keep).blockingAwait()
            }
        }
        writeAdded(store, added - expired)
        expired.forEach { Zen.log(context).add("later_expired", it) }
    }

    private fun readAdded(store: ZenKeyValueStore): Map<String, Long> = (store.getString(KEY_ADDED, "") ?: "").split('\n').mapNotNull {
        val p = it.split('\t')
        val t = p.getOrNull(1)?.toLongOrNull()
        if (p[0].isEmpty() || t == null) null else p[0] to t
    }.toMap()

    private fun writeAdded(store: ZenKeyValueStore, map: Map<String, Long>) {
        store.put(KEY_ADDED to map.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }
}
