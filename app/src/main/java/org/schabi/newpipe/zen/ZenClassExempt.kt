package org.schabi.newpipe.zen

import java.util.Locale

/**
 * One entry of the content recognition exception list (spec 4.6): a channel ([keyword] = false,
 * [value] = channel URL) or a keyword ([value] matched case-insensitively in the title, e.g.
 * "gute Nachrichten"). Its videos keep weight 1.0 and are not subject to the heavy rule.
 * Adding is a loosening ([ZenSettingsLock.addExempt]), removing applies at once.
 */
data class ZenClassExempt(val keyword: Boolean, val serviceId: Int, val value: String, val name: String = "") {
    val key: String
        get() = if (keyword) "k:${normKeyword(value)}" else "c:${ZenPraxisChannel.keyOf(serviceId, value)}"

    /** What the settings list shows. */
    val label: String get() = if (keyword) "\"${value.trim()}\"" else name.ifEmpty { value }

    companion object {
        const val MIN_KEYWORD = 3
        const val MAX_KEYWORD = 60
        const val MAX_ENTRIES = 100

        @JvmStatic
        fun channel(serviceId: Int, url: String, name: String) = ZenClassExempt(false, serviceId, url, name)

        @JvmStatic
        fun keyword(text: String) = ZenClassExempt(true, -1, text.trim().replace(Regex("\\s+"), " "))

        fun normKeyword(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

        @JvmStatic
        fun validKeyword(s: String) = normKeyword(s).length in MIN_KEYWORD..MAX_KEYWORD

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

        @JvmStatic
        fun encodeList(list: List<ZenClassExempt>): String = list.joinToString("\n") {
            "${if (it.keyword) "k" else "c"}\t${it.serviceId}\t${clean(it.value)}\t${clean(it.name)}"
        }

        @JvmStatic
        fun decodeList(raw: String?): List<ZenClassExempt> = (raw ?: "").split('\n').filter { it.isNotEmpty() }.mapNotNull {
            val p = it.split('\t', limit = 4)
            val kind = p.getOrNull(0)
            val id = p.getOrNull(1)?.toIntOrNull()
            val value = p.getOrNull(2)
            if ((kind != "k" && kind != "c") || id == null || value.isNullOrEmpty()) {
                null
            } else {
                ZenClassExempt(kind == "k", id, value, p.getOrElse(3) { "" })
            }
        }
    }
}

/** Fast lookup of the active exceptions for many feed candidates. */
class ZenClassExemptions(list: List<ZenClassExempt>) {
    private val channels = list.filter { !it.keyword }.map { it.key }.toHashSet()
    private val keywords = list.filter { it.keyword }.map { ZenClassExempt.normKeyword(it.value) }

    fun isEmpty() = channels.isEmpty() && keywords.isEmpty()

    /** True if the channel ([serviceId] + [channelUrl]) is listed or the [title] contains a keyword. */
    fun matches(serviceId: Int, channelUrl: String?, title: String?): Boolean {
        if (channelUrl != null && "c:${ZenPraxisChannel.keyOf(serviceId, channelUrl)}" in channels) return true
        if (title.isNullOrEmpty() || keywords.isEmpty()) return false
        val t = title.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        return keywords.any { it in t }
    }
}
