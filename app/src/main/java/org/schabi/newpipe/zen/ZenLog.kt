package org.schabi.newpipe.zen

/**
 * Simple event log (basis for a later weekly summary). One line per event:
 * `timestamp<TAB>type<TAB>detail`, capped at [MAX_ENTRIES]. Time is the trusted time.
 */
class ZenLog(private val store: ZenKeyValueStore, private val clock: ZenClock) {
    data class Entry(val timestamp: Long, val type: String, val detail: String)

    @Synchronized
    fun add(type: String, detail: String = "") {
        val line = "${clock.now().trustedMillis}\t${clean(type)}\t${clean(detail)}"
        val lines = (store.getString(KEY, "") ?: "").split('\n').filter { it.isNotEmpty() }
        val kept = (lines + line).takeLast(MAX_ENTRIES)
        store.put(KEY to kept.joinToString("\n"))
    }

    @Synchronized
    fun entries(): List<Entry> = (store.getString(KEY, "") ?: "").split('\n').filter { it.isNotEmpty() }.mapNotNull {
        val p = it.split('\t', limit = 3)
        val ts = p.getOrNull(0)?.toLongOrNull()
        if (ts == null || p.size < 2) null else Entry(ts, p[1], p.getOrElse(2) { "" })
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    companion object {
        const val MAX_ENTRIES = 500
        private const val KEY = "log"
    }
}
