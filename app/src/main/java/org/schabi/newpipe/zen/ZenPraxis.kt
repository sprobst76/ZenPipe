package org.schabi.newpipe.zen

import android.content.Context

/**
 * A "practice" channel (yoga, breathwork, meditation, sleep aids), maintained by the user (see
 * zen-docs/spec-praxis.md). The assignment is per channel, never per video, and never automatic.
 * Identity is [key] = service id + normalised channel URL, so http/https, "www."/"m." and a
 * trailing slash do not matter.
 */
data class ZenPraxisChannel(val serviceId: Int, val url: String, val name: String) {
    val key: String get() = keyOf(serviceId, url)

    companion object {
        @JvmStatic
        fun keyOf(serviceId: Int, url: String): String = "$serviceId:${normalize(url)}"

        /** Scheme and "www."/"m." removed, host lower-cased (channel ids are case-sensitive), no trailing slash. */
        @JvmStatic
        fun normalize(url: String): String {
            var s = url.trim()
            s = s.substringAfter("://", s)
            val slash = s.indexOf('/')
            var host = if (slash >= 0) s.substring(0, slash) else s
            val path = if (slash >= 0) s.substring(slash) else ""
            host = host.lowercase().removePrefix("www.").removePrefix("m.")
            return (host + path).trimEnd('/')
        }

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        @JvmStatic
        fun encodeList(list: List<ZenPraxisChannel>): String = list.joinToString("\n") { "${it.serviceId}\t${clean(it.url)}\t${clean(it.name)}" }

        @JvmStatic
        fun decodeList(raw: String?): List<ZenPraxisChannel> = (raw ?: "").split('\n').filter { it.isNotEmpty() }.mapNotNull {
            val p = it.split('\t', limit = 3)
            val id = p.getOrNull(0)?.toIntOrNull()
            val url = p.getOrNull(1)
            if (id == null || url.isNullOrEmpty()) null else ZenPraxisChannel(id, url, p.getOrElse(2) { "" })
        }
    }
}

/**
 * Practice minutes per Zen day (04:00 rollover), kept for [KEEP_DAYS] days. No limit is attached
 * (decision); a later optional soft limit would read [msOn] (hook, see spec-praxis.md).
 */
class ZenPraxisTime(private val store: ZenKeyValueStore) {
    @Synchronized
    fun add(day: String, ms: Long) {
        if (ms <= 0) return
        val map = read()
        map[day] = (map[day] ?: 0L) + ms
        val kept = map.entries.sortedBy { it.key }.takeLast(KEEP_DAYS)
        store.put(KEY to kept.joinToString("\n") { "${it.key}\t${it.value}" })
    }

    @Synchronized
    fun msOn(day: String): Long = read()[day] ?: 0L

    private fun read(): LinkedHashMap<String, Long> {
        val m = LinkedHashMap<String, Long>()
        (store.getString(KEY, "") ?: "").split('\n').filter { it.isNotEmpty() }.forEach {
            val p = it.split('\t')
            val v = p.getOrNull(1)?.toLongOrNull()
            if (p[0].isNotEmpty() && v != null) m[p[0]] = v
        }
        return m
    }

    companion object {
        const val KEEP_DAYS = 30
        private const val KEY = "px_time"
    }
}

/** Process-wide access to the practice channels (they live in [ZenSettingsLock]). */
object ZenPraxis {
    /** Newest videos shown per practice channel in the practice tab (no paging). */
    const val MAX_PER_CHANNEL = 15

    enum class State { NONE, PENDING, ACTIVE }

    /** Keys of the ACTIVE practice channels (read once per decision, e.g. per feed edition). */
    @JvmStatic
    fun activeKeys(): Set<String> = ZenSettings.lock()?.praxisChannels()?.map { it.key }?.toHashSet() ?: emptySet()

    @JvmStatic
    fun isPraxis(serviceId: Int, channelUrl: String?): Boolean = !channelUrl.isNullOrEmpty() && ZenPraxisChannel.keyOf(serviceId, channelUrl) in activeKeys()

    @JvmStatic
    fun state(context: Context, serviceId: Int, channelUrl: String): State {
        val lock = Zen.settingsLock(context)
        val key = ZenPraxisChannel.keyOf(serviceId, channelUrl)
        return when {
            lock.praxisChannels().any { it.key == key } -> State.ACTIVE
            lock.pendingPraxis().any { it.key == key } -> State.PENDING
            else -> State.NONE
        }
    }

    @JvmStatic
    fun todayMs(context: Context): Long {
        val store = Zen.store(context)
        val day = ZenDay.dayOf(Zen.clock(context).now().trustedMillis, ZenZone.fixed(store)).toString()
        return ZenPraxisTime(store).msOn(day)
    }
}
