package org.schabi.newpipe.zen.classify

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Details loaded after an edition was built: throttling, failure kinds, budget, cache reuse. */
class ZenFeedDetailsTest {
    private var clock = 1_000_000L
    private val sleeps = ArrayList<Long>()

    private fun requests(load: (Int, String) -> VideoText) = ZenDetailRequests("test", load, { clock }, {
        sleeps.add(it)
        clock += it
    })

    private fun entry(key: String, level: String) = ClassEntry(key, "t", "c", level, "m", mapOf("bildung" to 0.8), "neutral", emptyList(), 1.0, 1, 1, 0, 0)

    private fun job(n: Int) = ZenFeedDetails.Job("0:u$n", 0, "u$n", VideoText("t$n", "c")) { e -> "${e.level}|why$n" }

    private fun run(
        jobs: List<ZenFeedDetails.Job>,
        req: ZenDetailRequests,
        cached: Set<String> = emptySet(),
        budget: Int = 30,
        off: () -> Boolean = { false }
    ) = ZenFeedDetails { it.run() }.process(
        jobs,
        req,
        cachedD = { j -> if (j.key in cached) entry(j.key, "D") else null },
        classify = { j, _ -> entry(j.key, "D") },
        off = off,
        budgetLeft = { budget - req.requests }
    )

    @Test
    fun `all picks refined at one request per second`() {
        val req = requests { _, url -> VideoText(url, "c", description = "d") }
        val out = run((1..3).map { job(it) }, req)
        assertEquals(mapOf("u1" to "D|why1", "u2" to "D|why2", "u3" to "D|why3"), out.whys)
        assertEquals(3, out.ok)
        assertEquals("complete", out.reason)
        assertEquals(3, req.requests)
        // the first request at once, then one per second
        assertEquals(listOf(1_000L, 1_000L), sleeps)
    }

    @Test
    fun `cached details cost no request`() {
        val req = requests { _, url -> VideoText(url, "c") }
        val out = run((1..2).map { job(it) }, req, cached = setOf("0:u1"))
        assertEquals(1, out.cached)
        assertEquals(1, out.ok)
        assertEquals(1, req.requests)
    }

    @Test
    fun `members only is permanent, network errors are retried twice`() {
        var calls = 0
        val req = requests { _, url ->
            calls++
            when (url) {
                "u1" -> throw RuntimeException(PaidContentException("members only"))
                "u2" -> throw IOException("timeout")
                else -> VideoText(url, "c")
            }
        }
        val out = run((1..3).map { job(it) }, req)
        assertEquals(2, out.failed)
        assertEquals(1, out.ok)
        assertEquals(mapOf("u3" to "D|why3"), out.whys) // failed picks keep their title-only why
        assertEquals(1 + 3 + 1, calls)
        assertEquals(2, req.retries)
        assertTrue(sleeps.containsAll(listOf(2_000L, 5_000L)))
        assertEquals(1, req.errors["PaidContentException"])
        assertEquals(1, req.errors["IOException"])
    }

    @Test
    fun `bot check stops the run`() {
        var calls = 0
        val req = requests { _, url ->
            calls++
            if (url == "u2") throw ReCaptchaException("captcha", url)
            VideoText(url, "c")
        }
        val out = run((1..5).map { job(it) }, req)
        assertEquals("blocked", out.reason)
        assertEquals(2, calls)
        assertTrue(req.blocked)
        assertEquals(1, out.ok)
    }

    @Test
    fun `daily budget and switching off stop the run`() {
        val req = requests { _, url -> VideoText(url, "c") }
        val out = run((1..5).map { job(it) }, req, budget = 2)
        assertEquals("budget", out.reason)
        assertEquals(2, req.requests)
        var off = false
        val req2 = requests { _, url ->
            off = true // switched off while the first request ran
            VideoText(url, "c")
        }
        val out2 = run((1..5).map { job(it) }, req2, off = { off })
        assertEquals("off", out2.reason)
        assertEquals(1, req2.requests)
    }
}
