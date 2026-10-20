package org.schabi.newpipe.zen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.zen.ZenPraxisTheme.BREATH
import org.schabi.newpipe.zen.ZenPraxisTheme.FITNESS
import org.schabi.newpipe.zen.ZenPraxisTheme.MEDITATION
import org.schabi.newpipe.zen.ZenPraxisTheme.SLEEP
import org.schabi.newpipe.zen.ZenPraxisTheme.YOGA
import org.schabi.newpipe.zen.ZenPraxisThemes.Source
import org.schabi.newpipe.zen.classify.ClassEntry
import org.schabi.newpipe.zen.classify.MemoryClassStore
import org.schabi.newpipe.zen.classify.PraxisThemeLabel
import org.schabi.newpipe.zen.classify.ZenClassCache

/** Practice themes (D61): dictionary, model thresholds, order of the sources, masks, storage. */
class ZenPraxisThemesTest {
    private fun t(title: String) = ZenPraxisThemes.themes(ZenPraxisThemes.titleMask(title)).toSet()

    @Test
    fun `German titles`() {
        assertEquals(setOf(YOGA), t("Morgenyoga für Anfänger | 20 Minuten"))
        assertEquals(setOf(BREATH), t("Atemübungen gegen Stress"))
        assertEquals(setOf(BREATH, MEDITATION), t("Geführte Atemmeditation zum Ankommen"))
        assertEquals(setOf(SLEEP, MEDITATION), t("Einschlafmeditation – tief entspannt einschlafen"))
        assertEquals(setOf(FITNESS), t("Ganzkörper Krafttraining ohne Geräte"))
        assertEquals(setOf(FITNESS), t("Bauch Beine Po in 15 Minuten"))
        assertEquals(setOf(YOGA, FITNESS), t("Fatburning Yoga Flow | Intensives Ganzkörperworkout"))
        assertEquals(setOf(YOGA), t("Sonnengruß Schritt für Schritt"))
        assertEquals(setOf(BREATH), t("Wechselatmung (Nadi Shodhana) lernen"))
    }

    @Test
    fun `English titles`() {
        assertEquals(setOf(YOGA), t("Vinyasa Flow for Hips"))
        assertEquals(setOf(BREATH), t("Pranayama for beginners"))
        assertEquals(setOf(BREATH), t("Box Breathing to calm down"))
        assertEquals(setOf(MEDITATION), t("10 Minute Body Scan"))
        assertEquals(setOf(SLEEP), t("Music to fall asleep fast"))
        assertEquals(setOf(FITNESS), t("20 Min HIIT for beginners"))
        assertEquals(setOf(FITNESS), t("Tabata 4 minutes"))
        assertEquals(setOf(FITNESS), t("Pilates Core 20 Minutes"))
        assertEquals(setOf(YOGA, MEDITATION, SLEEP), t("Yoga Nidra for deep sleep"))
    }

    @Test
    fun `strict - unclear words give nothing`() {
        assertEquals(emptySet<ZenPraxisTheme>(), t("Q&A: my morning routine"))
        assertEquals(emptySet<ZenPraxisTheme>(), t("Atemberaubende Aussicht in den Alpen")) // "atem" alone is no rule
        assertEquals(emptySet<ZenPraxisTheme>(), t("Training für das Gedächtnis"))
        assertEquals(emptySet<ZenPraxisTheme>(), t("Stretching tips"))
        assertEquals(emptySet<ZenPraxisTheme>(), t("Schlafzimmer einrichten")) // prefix "schlaf" alone is no rule
        assertEquals(emptySet<ZenPraxisTheme>(), t(""))
    }

    @Test
    fun `hashtags do not count`() {
        assertEquals(emptySet<ZenPraxisTheme>(), t("Why rest matters #yoganidra #meditation"))
        assertEquals(listOf("yoga", "nidra", "for", "sleep"), ZenPraxisThemes.tokens("Yoga Nidra #yoganidra #nsdr for sleep"))
    }

    @Test
    fun `model thresholds per level`() {
        val p = mapOf("yoga" to 0.85, "meditation_hypnose" to 0.75, "mobilitaet_kraft_training" to 0.95, "schlaf_klang" to 0.79)
        // level T: fitness never from the model, meditation needs 0.8
        assertEquals(listOf(YOGA), ZenPraxisThemes.modelThemes(p, "T"))
        // level D: fitness from 0.9, meditation from 0.7, strongest first
        assertEquals(listOf(FITNESS, YOGA, MEDITATION), ZenPraxisThemes.modelThemes(p, "D"))
        assertEquals(emptyList<ZenPraxisTheme>(), ZenPraxisThemes.modelThemes(null, "D"))
        assertEquals(emptyList<ZenPraxisTheme>(), ZenPraxisThemes.modelThemes(mapOf("yoga" to 0.79), "D"))
    }

    @Test
    fun `order - user, then title rules and model, then default, then none`() {
        val p = mapOf("meditation_hypnose" to 0.95)
        // the correction counts alone, also "none"
        assertEquals(ZenPraxisThemes.Resolved(BREATH.bit, Source.USER), ZenPraxisThemes.resolve(BREATH.bit, "Yoga Flow", p, "D", YOGA.bit))
        assertEquals(ZenPraxisThemes.Resolved(0, Source.USER), ZenPraxisThemes.resolve(0, "Yoga Flow", p, "D", YOGA.bit))
        // union of title rules and model, title first
        assertEquals(ZenPraxisThemes.Resolved(YOGA.bit or MEDITATION.bit, Source.TITLE), ZenPraxisThemes.resolve(null, "Yoga Flow", p, "D", SLEEP.bit))
        assertEquals(ZenPraxisThemes.Resolved(MEDITATION.bit, Source.MODEL), ZenPraxisThemes.resolve(null, "Ankommen", p, "D", SLEEP.bit))
        // default of the channel/playlist only without any hit
        assertEquals(ZenPraxisThemes.Resolved(SLEEP.bit, Source.DEFAULT), ZenPraxisThemes.resolve(null, "Ankommen", null, null, SLEEP.bit))
        assertEquals(ZenPraxisThemes.Resolved(0, Source.NONE), ZenPraxisThemes.resolve(null, "Ankommen", null, null, 0))
    }

    @Test
    fun `at most three themes, title hits first`() {
        val p = mapOf("atmen_breathwork" to 0.99)
        val r = ZenPraxisThemes.resolve(null, "Yoga Nidra Workout for sleep", p, "D", 0)
        assertEquals(3, Integer.bitCount(r.mask))
        assertFalse(ZenPraxisThemes.has(r.mask, BREATH))
    }

    @Test
    fun `masks`() {
        assertEquals(31, ZenPraxisThemes.ALL_MASK)
        assertEquals(YOGA.bit or BREATH.bit, ZenPraxisThemes.mask(listOf(BREATH, YOGA)))
        assertEquals(listOf(YOGA, BREATH), ZenPraxisThemes.themes(YOGA.bit or BREATH.bit))
        assertEquals("yoga,breath", ZenPraxisThemes.keys(YOGA.bit or BREATH.bit))
        assertEquals(0, ZenPraxisThemes.sanitize(64)) // unknown bit
        assertEquals(0, ZenPraxisThemes.sanitize(15)) // four themes
        assertEquals(7, ZenPraxisThemes.sanitize(7))
        assertEquals(listOf("fitness", "yoga", "sleep", "meditation", "breath"), ZenPraxisTheme.entries.map { it.key })
    }

    @Test
    fun `entries keep themes and the video flag, old lines stay readable`() {
        val list = listOf(
            ZenPraxisChannel(0, "https://youtube.com/channel/UCa", "A"),
            ZenPraxisChannel(0, "https://youtube.com/channel/UCb", "B", themes = YOGA.bit),
            ZenPraxisChannel(0, "https://youtube.com/playlist?list=PL", "P", playlist = true, themes = SLEEP.bit or BREATH.bit),
            ZenPraxisChannel(0, "https://youtube.com/watch?v=x", "V", video = true)
        )
        assertEquals(list, ZenPraxisChannel.decodeList(ZenPraxisChannel.encodeList(list)))
        // format of older versions
        val old = ZenPraxisChannel.decodeList("0\thttps://youtube.com/channel/UCa\tA\n0\thttps://youtube.com/playlist?list=PL\tP\tp")
        assertEquals(listOf(false, true), old.map { it.playlist })
        assertEquals(listOf(0, 0), old.map { it.themes })
        // a broken themes field is dropped, the entry stays
        assertEquals(0, ZenPraxisChannel.decodeList("0\tu\tA\t\t999").single().themes)
    }

    @Test
    fun `default themes are neutral - at once, also locked, no lock`() {
        val store = MemoryZenStore()
        var mono = 1_000_000L
        fun cd(n: String) = ZenCountdown(store, n, { mono }, { 1 })
        val lock = ZenSettingsLock(store, cd("l"), cd("u"), cd("s"))
        lock.completeSetup() // starts the 24 h lock
        assertTrue(lock.isLocked())
        val ch = ZenPraxisChannel(0, "https://youtube.com/channel/UCa", "A")
        assertTrue(lock.addPraxis(ch) is ZenSettingsLock.Result.Rejected)
        mono += 25 * 60 * 60 * 1000L
        lock.addPraxis(ch) // pending, starts the lock
        assertTrue(lock.setPraxisThemes(ch.key, YOGA.bit))
        assertEquals(YOGA.bit, lock.pendingPraxis().single().themes)
        assertFalse(lock.setPraxisThemes(ch.key, YOGA.bit))
        assertFalse(lock.setPraxisThemes("0:unknown", YOGA.bit))
        mono += 25 * 60 * 60 * 1000L
        assertEquals(YOGA.bit, lock.praxisChannels().single().themes)
        lock.addPraxis(ZenPraxisChannel(0, "https://youtube.com/channel/UCb", "B")) // locked again
        assertTrue(lock.isLocked())
        assertTrue(lock.setPraxisThemes(ch.key, SLEEP.bit)) // still possible while locked
        assertEquals(SLEEP.bit, lock.praxisChannels().single().themes)
    }

    @Test
    fun `corrections and cached values from the store`() {
        val s = MemoryClassStore()
        fun e(key: String, level: String, px: Map<String, Double>?) = ClassEntry(key, "t", "c", level, "m", emptyMap(), null, emptyList(), 1.0, 1, 1, 0, 0, praxisThemes = px)
        s.put(e("0:a", "D", mapOf("yoga" to 0.9)))
        s.put(e("0:b", "D", mapOf("yoga" to 0.9)))
        s.putPraxisTheme(PraxisThemeLabel("0:b", BREATH.bit, "t", "c", 1))
        val videos = listOf(
            ZenPraxisThemeData.Video("0:a", "Ankommen", SLEEP.bit),
            ZenPraxisThemeData.Video("0:b", "Ankommen", SLEEP.bit),
            ZenPraxisThemeData.Video("0:c", "Ankommen", SLEEP.bit)
        )
        val r = ZenPraxisThemeData.resolveAll(s, videos, modelReady = true)
        assertEquals(YOGA.bit, r.getValue("0:a").mask)
        assertEquals(Source.USER, r.getValue("0:b").source)
        assertEquals(Source.DEFAULT, r.getValue("0:c").source)
        // without a working model the cached values do not count
        assertEquals(Source.DEFAULT, ZenPraxisThemeData.resolveAll(s, videos, modelReady = false).getValue("0:a").source)
        s.removePraxisTheme("0:b")
        assertNull(s.praxisTheme("0:b"))
    }

    @Test
    fun `cache recomputes old entries for the practice tab, never D to T`() {
        val c = ZenClassCache(MemoryClassStore())
        fun e(level: String, px: Map<String, Double>?) = ClassEntry("k", "t", "c", level, "m", emptyMap(), null, emptyList(), 1.0, 1, 1, 0, 0, praxisThemes = px)
        assertFalse(c.needsWork(e("T", null), "T", "m")) // the feed does not care
        assertTrue(c.needsWork(e("T", null), "T", "m", wantPraxis = true))
        assertFalse(c.needsWork(e("T", emptyMap()), "T", "m", wantPraxis = true)) // recorded: none
        assertFalse(c.needsWork(e("D", null), "T", "m", wantPraxis = true))
        assertTrue(c.needsWork(e("D", null), "D", "m", wantPraxis = true))
    }

    @Test
    fun `migration only adds, existing rows stay`() {
        val old = setOf("k", "title", "channel", "level", "model", "cats", "tone", "top", "weight", "tokens", "ms", "created", "accessed")
        val sql = ZenClassCache.additions(old)
        assertTrue(sql.contains("ALTER TABLE cls ADD COLUMN tprev TEXT"))
        assertTrue(sql.contains("ALTER TABLE cls ADD COLUMN pxt TEXT"))
        assertTrue(sql.any { it.startsWith("CREATE TABLE IF NOT EXISTS praxis_theme") })
        assertTrue(sql.none { it.contains("DROP") || it.contains("DELETE") })
        val now = ZenClassCache.additions(old + "tprev" + "pxt")
        assertTrue(now.none { it.startsWith("ALTER") })
        assertNull(ZenClassCache.decodePraxisThemes(null))
        assertEquals(mapOf("yoga" to 0.9), ZenClassCache.decodePraxisThemes(ClassEntry.encodeMap(mapOf("yoga" to 0.9))))
    }
}
