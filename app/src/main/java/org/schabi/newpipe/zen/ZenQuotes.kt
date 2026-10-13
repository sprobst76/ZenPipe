package org.schabi.newpipe.zen

import android.content.Context
import kotlin.random.Random
import org.schabi.newpipe.R

/**
 * Quotes for the pause screen (`zen-docs/sprueche.md`, resource `R.array.zen_quotes` in the app
 * language) plus the user's own quotes. One quote per pause, a different one for every pause and
 * no repetition until the whole pool has been shown ("round"). Pure logic over [ZenKeyValueStore];
 * the state lives in `zen_state` (`qt_*`), so it is not part of export/import/reset/auto-backup.
 *
 * Own quotes are a comfort setting, not a loosening: no settings lock. With "only own quotes" the
 * pool is just the own quotes (falls back to the built-in ones while the own list is empty).
 *
 * Quotes are identified by their text, so a language switch simply forms a new pool; the seen
 * marks of the other language are dropped (the round starts over for that language).
 */
class ZenQuotes(private val store: ZenKeyValueStore, private val random: Random = Random.Default) {

    fun custom(): List<String> = store.getString(K_CUSTOM, null)
        ?.split(SEP)?.filter { it.isNotBlank() } ?: emptyList()

    /** Adds an own quote (trimmed, max [MAX_LENGTH] chars). False if empty, a duplicate or the list is full. */
    fun addCustom(text: String): Boolean {
        val t = text.replace(SEP, ' ').trim().take(MAX_LENGTH)
        val list = custom()
        if (t.isEmpty() || t in list || list.size >= MAX_CUSTOM) return false
        saveCustom(list + t)
        return true
    }

    fun removeCustom(text: String) = saveCustom(custom() - text)

    fun onlyCustom(): Boolean = store.getInt(K_ONLY_CUSTOM, 0) == 1

    fun setOnlyCustom(on: Boolean) = store.put(K_ONLY_CUSTOM to if (on) 1 else null)

    /** The quotes to draw from: own only (if chosen and not empty), else built-in plus own. */
    fun pool(builtIn: List<String>): List<String> {
        val own = custom()
        val all = if (onlyCustom() && own.isNotEmpty()) own else builtIn + own
        return all.filter { it.isNotBlank() }.distinct()
    }

    /**
     * The quote of the pause [pauseId]: the same one for every look at the same pause, a new one
     * (by [next]) for a new pause. Null if the pool is empty.
     */
    fun forPause(pauseId: Long, builtIn: List<String>): String? {
        val pool = pool(builtIn)
        val stored = store.getString(K_PAUSE_TEXT, null)
        if (store.getLong(K_PAUSE_ID, -1L) == pauseId && stored != null && stored in pool) return stored
        val q = next(builtIn) ?: return null
        store.put(K_PAUSE_ID to pauseId, K_PAUSE_TEXT to q)
        return q
    }

    /** Draws the next quote: random among those not yet shown in this round; never the last one twice in a row. */
    fun next(builtIn: List<String>): String? {
        val pool = pool(builtIn)
        if (pool.isEmpty()) return null
        val ids = pool.associateBy { idOf(it) }
        val seen = (store.getString(K_SEEN, null)?.split(',')?.filter { it in ids } ?: emptyList()).toMutableSet()
        val last = store.getString(K_LAST, null)
        var candidates = pool.filter { idOf(it) !in seen }
        if (candidates.isEmpty()) {
            seen.clear()
            candidates = pool.filter { pool.size == 1 || idOf(it) != last }
        }
        val pick = candidates[random.nextInt(candidates.size)]
        seen += idOf(pick)
        store.put(K_SEEN to seen.joinToString(","), K_LAST to idOf(pick))
        return pick
    }

    private fun saveCustom(list: List<String>) = store.put(K_CUSTOM to list.joinToString(SEP.toString()).ifEmpty { null })

    companion object {
        const val MAX_LENGTH = 300
        const val MAX_CUSTOM = 100
        private const val SEP = '\u001E'
        private const val K_CUSTOM = "qt_custom"
        private const val K_ONLY_CUSTOM = "qt_only_custom"
        private const val K_SEEN = "qt_seen"
        private const val K_LAST = "qt_last"
        private const val K_PAUSE_ID = "qt_pause_id"
        private const val K_PAUSE_TEXT = "qt_pause_text"

        /** Short stable id of a quote text (collisions only cost a skipped quote in one round). */
        fun idOf(text: String): String = Integer.toHexString(text.hashCode())

        private const val ATTRIBUTION = "\n— "

        /**
         * Splits a quote into its text and the attribution line ("<text>\n— <source>", see
         * zen-docs/gen_sprueche.py). The attribution is null for anonymous and own quotes.
         */
        fun splitAttribution(quote: String): Pair<String, String?> {
            val i = quote.lastIndexOf(ATTRIBUTION)
            if (i < 0) return quote to null
            val attr = quote.substring(i + ATTRIBUTION.length).trim()
            return quote.substring(0, i).trim() to attr.ifEmpty { null }
        }

        @JvmStatic
        fun of(context: Context) = ZenQuotes(Zen.store(context))

        @JvmStatic
        fun builtIn(context: Context): List<String> = context.resources.getStringArray(R.array.zen_quotes).toList()
    }
}
