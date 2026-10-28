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

    /**
     * Pop-up (floating window) player: detail-page button, long-press "Start here on popup",
     * playlist/channel "Popup" control, share target "Popup player", queue menu "Switch to
     * popup", minimize-on-exit "Popup", popup settings and any player intent asking for it.
     * Off in ZenPipe: the picture never runs alongside other apps.
     */
    private const val POPUP_PLAYER_ENABLED = false

    /**
     * Minimize-on-exit of the video player ("continue as background audio" or "popup" when the
     * app is left). Off in ZenPipe: leaving the app or turning the screen off pauses the video
     * player (mode "none"); the setting is hidden. Audio started via the "Background" action is
     * a separate player and keeps playing (screen on or off).
     */
    private const val MINIMIZE_ON_EXIT_ENABLED = false

    /**
     * Upstream announcement dialogs on app start ("Keep Android Open" every 30 days with links
     * to keepandroidopen.org and github.com/woheller69, "NewPipe is dropping support for
     * Android 5" with a newpipe.net blog link). They speak for NewPipe and point to outside
     * sites, so ZenPipe never shows them.
     */
    private const val UPSTREAM_ANNOUNCEMENTS_ENABLED = false

    /**
     * Media browser entry for external clients (Android Auto, Assistant). Off (D77): no entry
     * point without the feed; the code stays so it can be switched on again in one place.
     */
    private const val MEDIA_BROWSER_ENABLED = false

    @JvmStatic
    fun mediaBrowserEnabled(): Boolean = MEDIA_BROWSER_ENABLED

    @JvmStatic
    fun downloadsEnabled(): Boolean = DOWNLOADS_ENABLED

    @JvmStatic
    fun upstreamAnnouncementsEnabled(): Boolean = UPSTREAM_ANNOUNCEMENTS_ENABLED

    @JvmStatic
    fun popupPlayerEnabled(): Boolean = POPUP_PLAYER_ENABLED

    @JvmStatic
    fun minimizeOnExitEnabled(): Boolean = MINIMIZE_ON_EXIT_ENABLED

    /**
     * The open action that is actually used for a stored or chosen [choice] (share targets and
     * the "preferred open action" setting): disabled downloads show the info page, a disabled
     * popup player becomes the normal video player.
     */
    @JvmStatic
    fun effectiveOpenAction(
        choice: String,
        downloadKey: String,
        popupKey: String,
        showInfoKey: String,
        videoPlayerKey: String
    ): String = when {
        choice == downloadKey && !downloadsEnabled() -> showInfoKey
        choice == popupKey && !popupPlayerEnabled() -> videoPlayerKey
        else -> choice
    }

    /** Whether an open action may be offered in lists (share dialog, settings). */
    @JvmStatic
    fun isOpenActionOffered(choice: String, downloadKey: String, popupKey: String): Boolean = !(choice == downloadKey && !downloadsEnabled()) &&
        !(choice == popupKey && !popupPlayerEnabled())
}
