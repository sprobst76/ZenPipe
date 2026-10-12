package org.schabi.newpipe.zen

/**
 * Process-wide view on ZenPipe's OWN playback (the PlayerService). Unlike
 * AudioManager.isMusicActive() it ignores audio from other apps.
 */
object ZenPlayback {
    @Volatile
    private var playing = false

    @JvmStatic
    fun isPlaying(): Boolean = playing

    @JvmStatic
    fun setPlaying(value: Boolean) {
        playing = value
        if (value) ZenSettings.lock()?.onPlaybackStarted()
    }
}
