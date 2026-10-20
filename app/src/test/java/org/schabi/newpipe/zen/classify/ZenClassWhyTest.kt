package org.schabi.newpipe.zen.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.zen.ZenFeedPlanner
import org.schabi.newpipe.zen.classify.ZenClassWhy.FeedPhrase

/** Every key of the taxonomy (v5) has a short display name; unknown keys fall back to the raw key. */
class ZenClassWhyTest {
    private val themes = listOf(
        "yoga", "mobilitaet_kraft_training", "atmen_breathwork", "meditation_hypnose", "schlaf_klang",
        "gesundheit_medizin", "ernaehrung_abnehmen", "psyche_therapie", "selbstentwicklung_produktivitaet",
        "beziehungen_dating", "sexualitaet_aufklaerung", "tantra_erotik", "spiritualitaet_esoterik", "programmieren",
        "linux_maker_smarthome", "ki", "ki_gesellschaft_ethik", "office_pkm_software", "xr_vr", "foto_video_design",
        "tech_gadgets_hardware", "it_sicherheit_datenschutz", "rad_mtb", "reisen_outdoor", "wissenschaft",
        "philosophie_gesellschaft_geschichte", "politik_nachrichten", "film_serien", "gaming", "familie_kinder",
        "haushalt_diy_vorsorge", "hobby_sammeln_spiele", "musik", "mystery_internetkultur", "sport",
        "finanzen_wirtschaft", "sonstiges"
    )

    @Test
    fun `all 37 themes and 10 formats have a name`() {
        assertEquals(37, themes.size)
        themes.forEach { assertNotNull(it, ZenClassWhy.themeRes(it)) }
        assertEquals(10, ZenClassWhy.FORMAT_KEYS.size)
        ZenClassWhy.FORMAT_KEYS.forEach { assertNotNull(it, ZenClassWhy.formatRes(it)) }
        // themes and formats do not overlap
        themes.forEach { assertNull(it, ZenClassWhy.formatRes(it)) }
    }

    @Test
    fun `unknown keys have no name`() {
        assertNull(ZenClassWhy.themeRes("new_theme_v6"))
        assertNull(ZenClassWhy.formatRes("new_format_v6"))
    }

    @Test
    fun `the feed says one short phrase, without numbers`() {
        fun f(c: ZenFeedPlanner.Candidate) = ZenClassWhy.feedPhrase(ZenFeedPlanner.whyOf(c, ZenFeedPlanner.ContentRules(weighting = true, heavyLimit = true)))
        val base = ZenFeedPlanner.Candidate(url = "u", channel = "c", uploadMillis = 0, durationSec = 600, isLive = false, groupRank = null, groupName = null)
        // strongest category only; title + channel only is said as "likely"
        assertEquals(
            FeedPhrase(FeedPhrase.Kind.CATEGORY, "aktuelles", likely = true, heavy = true),
            f(base.copy(zenCats = mapOf("bildung" to 0.61, "aktuelles" to 0.82), tone = "belastend", classLevel = "T"))
        )
        assertEquals(
            FeedPhrase(FeedPhrase.Kind.CATEGORY, "bildung", likely = false),
            f(base.copy(zenCats = mapOf("bildung" to 0.91), tone = "neutral", classLevel = "D"))
        )
        // no category: a clear theme is named, a weak one is not
        assertEquals(
            FeedPhrase(FeedPhrase.Kind.THEME, "ki", likely = true),
            f(base.copy(zenCats = emptyMap(), classLevel = "T", classTheme = "ki" to 0.8))
        )
        assertNull(f(base.copy(zenCats = emptyMap(), classLevel = "T", classTheme = "ki" to 0.42)))
        assertEquals(FeedPhrase(FeedPhrase.Kind.NONE, heavy = true), f(base.copy(zenCats = emptyMap(), tone = "belastend", classLevel = "D")))
        // exception list and not classified yet
        assertEquals(FeedPhrase(FeedPhrase.Kind.EXEMPT), f(base.copy(classExempt = true)))
        assertEquals(FeedPhrase(FeedPhrase.Kind.UNCLASSIFIED), f(base))
        // nothing stored: nothing to say; older 4-field values stay readable
        assertNull(ZenClassWhy.feedPhrase(null))
        assertEquals(FeedPhrase(FeedPhrase.Kind.CATEGORY, "zerstreuung", likely = true), ZenClassWhy.feedPhrase("T||0.50|zerstreuung=0.70"))
    }
}
