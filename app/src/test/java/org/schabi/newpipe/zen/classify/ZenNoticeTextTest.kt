package org.schabi.newpipe.zen.classify

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Display reflow of the license notices: joined paragraphs, short rules, no words lost. */
class ZenNoticeTextTest {
    @Test
    fun `hard wrapped paragraph is joined, lists and short lines stay`() {
        val text = """
            Title of the notice
            =========================================================

            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal
            in the Software without restriction.

            send a check or money order for US ${'$'}5.00, including the product name, to:
            Source Code Compliance Team
            Microsoft Corporation
            _____
            * Redistributions must reproduce the above copyright notice and the following
            terms of use.
            * Neither the name of Intel nor the names of its suppliers may be used to endorse
            https://example.org/license
        """.trimIndent()
        val r = ZenNoticeText.reflow(text).split('\n')
        assertEquals("Title of the notice", r[0])
        assertEquals(ZenNoticeText.RULE, r[1])
        assertTrue(r.any { it.startsWith("Permission is hereby granted") && it.endsWith("without restriction.") })
        assertTrue("address lines stay", r.contains("Source Code Compliance Team") && r.contains("Microsoft Corporation"))
        assertTrue(r.contains("* Redistributions must reproduce the above copyright notice and the following terms of use."))
        assertTrue(r.contains("https://example.org/license"))
        assertFalse(r.any { it.contains("=====") || it.contains("_____") })
    }

    @Test
    fun `shipped notices keep every word`() {
        listOf("zen_classifier_notices.txt", "zen_onnxruntime_third_party_notices.txt").forEach { name ->
            val f = listOf(File("src/main/res/raw/$name"), File("app/src/main/res/raw/$name")).first { it.exists() }
            val raw = f.readText()
            val words = { s: String -> s.split(Regex("\\s+")).filter { w -> w.isNotEmpty() && !Regex("([=\\-_*~#])\\1{3,}").matches(w) } }
            assertEquals(name, words(raw), words(ZenNoticeText.reflow(raw)).filter { it != ZenNoticeText.RULE })
        }
    }
}
