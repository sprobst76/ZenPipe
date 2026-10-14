package org.schabi.newpipe.zen.classify

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Debug export (JSON/CSV) and the storage of the user's corrections. */
class ZenClassExportTest {
    private fun entry(key: String, level: String = "D", prevT: String? = null) = ClassEntry(
        key, "Titel, mit \"Komma\"", "Kanal", level, "e5s-1", mapOf("bildung" to 0.8512), "neutral",
        listOf("wissenschaft" to 0.9, "erklaerung_analyse" to 0.7), 1.0, 20, 300, 1_791_000_000_000, 1_791_000_000_000, prevT
    )

    private val label = UserLabel("0:https://www.youtube.com/watch?v=a", false, listOf("aktuelles"), "Titel", "Kanal", 1_791_000_100_000)

    private fun export(): JsonObject {
        val tPrev = ClassEntry.encodeSnapshot(entry("x", "T").copy(cats = mapOf("aktuelles" to 0.6), weight = 0.6))
        val videos = listOf(
            ZenClassExport.Video(entry("0:https://www.youtube.com/watch?v=a", prevT = tPrev), "https://www.youtube.com/channel/UC1", 1234, "Bildung 0,85", label),
            ZenClassExport.Video(entry("0:https://www.youtube.com/watch?v=b", "T"), null, null, "Bildung 0,85", null, ZenClassExport.Language("ca", 0.9, "T", true))
        )
        val orphan = UserLabel("0:https://www.youtube.com/watch?v=gone", true, emptyList(), "Alt", "Kanal", 1)
        val edition = ZenClassExport.Edition(
            1_791_000_000_000,
            "2026-10-14",
            3,
            listOf(ZenClassExport.Pick("https://www.youtube.com/watch?v=a", 34, "Tech", "D||1.00|bildung=0.85", videos[0].entry))
        )
        val json = ZenClassExport.json(ZenClassExport.Meta(1_791_000_200_000, "0.1.0-zen.4", "e5s-1"), videos, listOf(orphan), edition)
        return JsonParser.`object`().from(json)
    }

    @Test
    fun `json has the documented structure`() {
        val o = export()
        assertEquals(ZenClassExport.FORMAT, o.getString("format"))
        assertEquals(1, o.getInt("version"))
        val v = o.getArray("videos")
        assertEquals(2, v.size)
        val a = v.getObject(0)
        assertEquals(0, a.getInt("service_id"))
        assertEquals("https://www.youtube.com/watch?v=a", a.getString("url"))
        assertEquals("Titel, mit \"Komma\"", a.getString("title"))
        assertEquals("https://www.youtube.com/channel/UC1", a.getString("channel_url"))
        assertEquals(1234, a.getInt("duration_s"))
        assertEquals("D", a.getString("level"))
        assertEquals(0.851, a.getObject("categories").getDouble("bildung"), 1e-9)
        assertEquals("theme", a.getArray("top").getObject(0).getString("kind"))
        assertEquals("format", a.getArray("top").getObject(1).getString("kind"))
        assertEquals("wrong", a.getObject("user_label").getString("verdict"))
        assertEquals("aktuelles", a.getObject("user_label").getArray("categories").getString(0))
        assertEquals(0.6, a.getObject("previous_title_only").getDouble("weight"), 1e-9)
        assertTrue(a.isNull("language"))
        val b = v.getObject(1)
        assertTrue(b.isNull("user_label"))
        assertTrue(b.isNull("channel_url"))
        assertEquals("ca", b.getObject("language").getString("code"))
        assertTrue(b.getObject("language").getBoolean("hidden_in_feed"))
        assertEquals(1, o.getArray("labels_without_video").size)
        val pick = o.getObject("feed_edition").getArray("picks").getObject(0)
        assertEquals(0.6, pick.getObject("title_only").getDouble("weight"), 1e-9)
        assertEquals(0.851, pick.getObject("with_details").getObject("categories").getDouble("bildung"), 1e-9)
    }

    private fun keysOf(x: Any?, out: MutableSet<String>) {
        when (x) {
            is JsonObject -> x.forEach { (k, v) ->
                out.add(k)
                keysOf(v, out)
            }

            is JsonArray -> x.forEach { keysOf(it, out) }
        }
    }

    @Test
    fun `export contains no forbidden fields`() {
        val o = export()
        assertEquals(ZenClassExport.TOP_FIELDS, o.keys)
        o.getArray("videos").forEach { assertEquals(ZenClassExport.VIDEO_FIELDS, (it as JsonObject).keys) }
        val all = HashSet<String>()
        keysOf(o, all)
        val forbidden = listOf("history", "subscri", "setting", "lock", "pause", "log", "watch", "praxis_", "exempt", "zs_", "zp_", "cd_", "state")
        all.forEach { k -> forbidden.forEach { f -> assertFalse("key $k contains $f", k.lowercase().contains(f)) } }
    }

    @Test
    fun `csv has one quoted row per video`() {
        val videos = listOf(ZenClassExport.Video(entry("0:https://www.youtube.com/watch?v=a"), null, 60, "why, text", label))
        val lines = ZenClassExport.csv(videos).trimEnd().split("\r\n")
        assertEquals(2, lines.size)
        assertEquals(ZenClassExport.CSV_COLUMNS.joinToString(","), lines[0])
        assertTrue(lines[1].contains("\"Titel, mit \"\"Komma\"\"\""))
        assertTrue(lines[1].contains("\"why, text\""))
        assertTrue(lines[1].endsWith(",wrong,aktuelles"))
    }

    @Test
    fun `labels are stored, replaced and removed`() {
        val c = ZenClassCache(MemoryClassStore())
        c.put(entry("k"))
        c.putLabel(label.copy(key = "k", atMillis = 1))
        c.putLabel(label.copy(key = "j", correct = true, cats = listOf("bildung"), atMillis = 2))
        assertEquals(listOf("j", "k"), c.labels().map { it.key })
        c.putLabel(label.copy(key = "k", cats = emptyList(), atMillis = 3))
        assertEquals(emptyList<String>(), c.label("k")!!.cats)
        c.removeLabel("k")
        assertNull(c.label("k"))
        // a label never changes the classification itself
        assertEquals(mapOf("bildung" to 0.8512), c.get("k")!!.cats)
    }

    @Test
    fun `title-only result is kept when details replace it`() {
        val t = entry("k", "T")
        val kept = ZenClassCache.previousTFor(t, "D", "e5s-1")
        assertEquals(ClassEntry.Snapshot("neutral", 1.0, mapOf("bildung" to 0.851), t.top), ClassEntry.decodeSnapshot(kept))
        assertNull(ZenClassCache.previousTFor(t, "D", "other-model"))
        assertNull(ZenClassCache.previousTFor(null, "D", "e5s-1"))
        assertEquals("x", ZenClassCache.previousTFor(entry("k", "D", prevT = "x"), "D", "e5s-1"))
        assertNull(ClassEntry.decodeSnapshot("broken"))
    }
}
