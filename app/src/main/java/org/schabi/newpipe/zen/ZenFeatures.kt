package org.schabi.newpipe.zen

/**
 * Central switches for upstream NewPipe features that ZenPipe hides. Every entry point of a
 * feature checks the matching function here, so a later setting only has to change the
 * return value (see zen-docs/rules.md, section "Funktionen").
 */
object ZenFeatures {
    /**
     * Downloads (download button, long-press/queue menu entry, share target "Download",
     * download dialog, Downloads screen, download settings, download service).
     * Off in ZenPipe; streaming incl. background audio is not affected.
     */
    private const val DOWNLOADS_ENABLED = false

    @JvmStatic
    fun downloadsEnabled(): Boolean = DOWNLOADS_ENABLED
}
