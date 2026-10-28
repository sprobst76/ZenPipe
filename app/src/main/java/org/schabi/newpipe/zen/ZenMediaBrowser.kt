package org.schabi.newpipe.zen

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Zen rules for the media browser (Android Auto etc.), same as in the app lists. */
object ZenMediaBrowser {
    /** Shorts are never offered (same check as the info list adapter). */
    @JvmStatic
    fun withoutShorts(items: List<InfoItem>): List<InfoItem> = items.filterNot { it is StreamInfoItem && it.isShortFormContent }
}
