package org.schabi.newpipe.zen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
