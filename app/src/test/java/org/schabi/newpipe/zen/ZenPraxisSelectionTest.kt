package org.schabi.newpipe.zen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.zen.ZenPraxisSelection.Candidate
import org.schabi.newpipe.zen.ZenPraxisTheme.BREATH
import org.schabi.newpipe.zen.ZenPraxisTheme.YOGA

/** Theme view of the practice tab: fixed number, at most one popular video, multi-theme videos under each theme. */
class ZenPraxisSelectionTest {
    private val y = YOGA.bit
    private val yb = YOGA.bit or BREATH.bit

    @Test
    fun `chip filter - multi-theme videos under each of their themes, none under More`() {
        val all = listOf(Candidate("a", yb, "c", -1), Candidate("b", 0, "c", -1), Candidate("c", y, "c", -1))
        assertEquals(listOf("a", "c"), ZenPraxisSelection.select(all, YOGA))
        assertEquals(listOf("a"), ZenPraxisSelection.select(all, BREATH))
        assertEquals(listOf("b"), ZenPraxisSelection.select(all, null))
    }

    @Test
    fun `without view counts the popular place stays empty`() {
        val all = (1..8).map { Candidate("v$it", y, "c", -1) }
        assertEquals(listOf("v1", "v2", "v3", "v4"), ZenPraxisSelection.select(all, YOGA))
    }

    @Test
    fun `popular = highest views over the channel median among the rest, in tab order`() {
        val views = listOf(100L, 100, 100, 100, 120, 900, 50, 100)
        val all = views.mapIndexed { i, v -> Candidate("v$i", y, "c", v) }
        // first four + v5 (9x the median)
        assertEquals(listOf("v0", "v1", "v2", "v3", "v5"), ZenPraxisSelection.select(all, YOGA))
    }

    @Test
    fun `small channels are measured against their own median`() {
        val big = (0..5).map { Candidate("b$it", y, "big", 100_000) }
        val small = listOf(Candidate("s0", 0, "small", 10), Candidate("s1", 0, "small", 10), Candidate("s2", y, "small", 50))
        val r = ZenPraxisSelection.ratios(big + small)
        assertEquals(5.0, r.getValue("s2"), 1e-9)
        assertEquals(1.0, r.getValue("b0"), 1e-9)
        assertEquals(listOf("b0", "b1", "b2", "b3", "s2"), ZenPraxisSelection.select(big + small, YOGA))
    }

    @Test
    fun `no outlier above the median - no popular video`() {
        val all = (0..6).map { Candidate("v$it", y, "c", 100) }
        assertEquals(4, ZenPraxisSelection.select(all, YOGA).size)
    }

    @Test
    fun `median needs three known counts, duplicates count once`() {
        val all = listOf(Candidate("a", y, "c", 10), Candidate("b", y, "c", 1000), Candidate("b", y, "c", 1000))
        assertNull(ZenPraxisSelection.ratios(all)["b"])
        assertEquals(listOf("a", "b"), ZenPraxisSelection.select(all, YOGA))
    }
}
