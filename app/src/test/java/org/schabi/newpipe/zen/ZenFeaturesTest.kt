package org.schabi.newpipe.zen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class ZenFeaturesTest {
    private val download = "download"
    private val popup = "popup_player"
    private val info = "show_info"
    private val video = "video_player"
    private val background = "background_player"

    private fun effective(choice: String) = ZenFeatures.effectiveOpenAction(choice, download, popup, info, video)

    @Test
    fun popupPlayerAndMinimizeAreOff() {
        assertFalse(ZenFeatures.popupPlayerEnabled())
        assertFalse(ZenFeatures.minimizeOnExitEnabled())
        assertFalse(ZenFeatures.downloadsEnabled())
        assertFalse(ZenFeatures.upstreamAnnouncementsEnabled())
    }

    @Test
    fun mediaBrowserSwitchIsPreparedButStillOn() {
        assertFalse(ZenFeatures.mediaBrowserEnabled())
    }

    @Test
    fun mediaBrowserSearchDropsShortsOnly() {
        fun stream(name: String, short: Boolean) = StreamInfoItem(0, "https://y/$name", name, StreamType.VIDEO_STREAM).also { it.isShortFormContent = short }
        val channel = ChannelInfoItem(0, "https://y/c", "chan")
        val items = listOf(stream("a", false), stream("s", true), channel, stream("b", false))
        assertEquals(listOf("a", "chan", "b"), ZenMediaBrowser.withoutShorts(items).map { it.name })
    }

    @Test
    fun storedPopupChoiceBecomesVideoPlayer() {
        assertEquals(video, effective(popup))
    }

    @Test
    fun storedDownloadChoiceShowsInfo() {
        assertEquals(info, effective(download))
    }

    @Test
    fun otherChoicesStay() {
        assertEquals(background, effective(background))
        assertEquals(video, effective(video))
        assertEquals(info, effective(info))
    }

    @Test
    fun disabledActionsAreNotOffered() {
        assertFalse(ZenFeatures.isOpenActionOffered(popup, download, popup))
        assertFalse(ZenFeatures.isOpenActionOffered(download, download, popup))
        assertTrue(ZenFeatures.isOpenActionOffered(background, download, popup))
        assertTrue(ZenFeatures.isOpenActionOffered(video, download, popup))
    }
}
