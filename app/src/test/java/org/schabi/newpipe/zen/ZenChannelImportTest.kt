package org.schabi.newpipe.zen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task A: only channel links get the "take over channel" sheet. */
class ZenChannelImportTest {
    @Test
    fun `channel links of all forms are recognised`() {
        listOf(
            "https://www.youtube.com/@yogawithadriene",
            "https://youtube.com/@tagesschau?si=abc",
            "https://m.youtube.com/@KurzgesagtDE",
            "https://www.youtube.com/channel/UCFKE7WVJfvaHW5q283SxchA",
            "https://www.youtube.com/c/YogaWithAdriene",
            "https://www.youtube.com/user/yogawithadriene",
            "http://youtube.com/@tagesschau/videos",
            "youtube.com/@tagesschau"
        ).forEach { assertTrue(it, ZenChannelImport.isChannelLink(it)) }
    }

    @Test
    fun `videos, playlists, shorts and other links are not`() {
        listOf(
            "https://www.youtube.com/watch?v=v7AYKMP6rOE",
            "https://youtu.be/v7AYKMP6rOE",
            "https://m.youtube.com/watch?v=v7AYKMP6rOE",
            "https://www.youtube.com/playlist?list=PLui6Eyny-UzwxbWCWDbTzEwsZnnROBTIL",
            "https://www.youtube.com/shorts/abcdefghijk",
            "https://www.youtube.com/embed/v7AYKMP6rOE",
            "https://example.com/@someone",
            "not a link",
            null
        ).forEach { assertFalse(it ?: "null", ZenChannelImport.isChannelLink(it)) }
    }

    @Test
    fun `link type decides`() {
        assertTrue(ZenChannelImport.handles(org.schabi.newpipe.extractor.StreamingService.LinkType.CHANNEL))
        assertFalse(ZenChannelImport.handles(org.schabi.newpipe.extractor.StreamingService.LinkType.STREAM))
        assertFalse(ZenChannelImport.handles(org.schabi.newpipe.extractor.StreamingService.LinkType.PLAYLIST))
        assertFalse(ZenChannelImport.handles(null))
    }
}
