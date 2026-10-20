package org.schabi.newpipe.zen

/**
 * The five fixed practice themes (D61, zen-docs/spec-praxis-themen.md). Only grouping and display:
 * a theme never changes a lock, a limit or a pause. Stored as a bit mask ([bit]); the order of the
 * entries is the order of the chips in the practice tab.
 *
 * [modelKey] is the theme of the classifier head that stands for it; [minT]/[minD] are the
 * probabilities from which the model alone assigns it at level T (title + channel) or D (with
 * details). Measured before the build (spec 3.1, 2026-10-20): the model mixes calm yoga stretching
 * up with training and misses cardio/HIIT, so fitness comes from the model only at level D and only
 * from 0.9 (the title rules carry it); sleep and breath had few examples, so they got the stricter 0.8.
 */
enum class ZenPraxisTheme(val key: String, val bit: Int, val modelKey: String, val minT: Double?, val minD: Double) {
    FITNESS("fitness", 1, "mobilitaet_kraft_training", null, 0.9),
    YOGA("yoga", 2, "yoga", 0.8, 0.8),
    SLEEP("sleep", 4, "schlaf_klang", 0.8, 0.8),
    MEDITATION("meditation", 8, "meditation_hypnose", 0.8, 0.7),
    BREATH("breath", 16, "atmen_breathwork", 0.8, 0.8);

    companion object {
        @JvmStatic
        fun ofKey(key: String?): ZenPraxisTheme? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Which practice themes a video has. Order of the sources (spec 3):
 *  1. the user's own correction (alone, also "none");
 *  2. title rules (small strict DE/EN dictionary) and the model's theme values ([ZenPraxisTheme.minT]/
 *     [ZenPraxisTheme.minD]); the union counts, title hits first, then the model by probability,
 *     at most [MAX] themes;
 *  3. the default theme(s) of the practice channel/playlist the video comes from;
 *  4. otherwise none ("More").
 * Without an installed model only 1, the title rules and 3 work. Doubt means "More" (strict).
 */
object ZenPraxisThemes {
    const val MAX = 3
    val ALL_MASK: Int = ZenPraxisTheme.entries.fold(0) { m, t -> m or t.bit }

    enum class Source { USER, TITLE, MODEL, DEFAULT, NONE }

    data class Resolved(val mask: Int, val source: Source)

    @JvmStatic
    fun themes(mask: Int): List<ZenPraxisTheme> = ZenPraxisTheme.entries.filter { mask and it.bit != 0 }

    @JvmStatic
    fun mask(themes: Collection<ZenPraxisTheme>): Int = themes.fold(0) { m, t -> m or t.bit }

    /** Valid mask: only known bits and at most [MAX] themes; anything else is dropped (0). */
    @JvmStatic
    fun sanitize(mask: Int): Int = if (mask and ALL_MASK.inv() != 0 || Integer.bitCount(mask) > MAX) 0 else mask

    @JvmStatic
    fun has(mask: Int, theme: ZenPraxisTheme) = mask and theme.bit != 0

    /** Comma separated keys ("yoga,breath"), for logs and the export. */
    @JvmStatic
    fun keys(mask: Int): String = themes(mask).joinToString(",") { it.key }

    // ------------------------------------------------------------------ title rules

    private class Rule(
        val contains: List<String> = emptyList(),
        val words: List<String> = emptyList(),
        val prefixes: List<String> = emptyList(),
        val phrases: List<List<String>> = emptyList()
    )

    /**
     * The dictionary: only terms that are clear on their own (measured on 450 videos of the 30
     * practice channels, spec 3.1). "contains" also catches German compounds (Atemübungen,
     * Schlafmeditation, Ganzkörperworkout). Mirrors `praxis-themen-messung/rules.py`.
     */
    private val RULES: Map<ZenPraxisTheme, Rule> = mapOf(
        ZenPraxisTheme.YOGA to Rule(
            contains = listOf("yoga"),
            words = listOf("vinyasa", "asana", "asanas", "hatha", "ashtanga", "sonnengruß", "sonnengruss", "nidra"),
            phrases = listOf(listOf("sun", "salutation"), listOf("sun", "salutations"))
        ),
        ZenPraxisTheme.BREATH to Rule(
            contains = listOf("pranayama", "breathwork", "atemübung", "atemtechnik", "atemarbeit", "atemmeditation", "wechselatmung"),
            words = listOf("kapalabhati"),
            phrases = listOf(
                listOf("breath", "work"),
                listOf("breathing", "exercise"),
                listOf("breathing", "exercises"),
                listOf("breathing", "technique"),
                listOf("breathing", "techniques"),
                listOf("wim", "hof"),
                listOf("box", "breathing"),
                listOf("nadi", "shodhana")
            )
        ),
        ZenPraxisTheme.MEDITATION to Rule(
            contains = listOf("meditation", "achtsamkeitsübung", "bodyscan"),
            words = listOf("meditate", "nidra"),
            prefixes = listOf("meditier"),
            phrases = listOf(listOf("body", "scan"), listOf("loving", "kindness"))
        ),
        ZenPraxisTheme.SLEEP to Rule(
            words = listOf("sleep", "asleep", "sleeping", "bedtime", "schlaf", "schlafen", "insomnia"),
            prefixes = listOf("einschlaf", "schlafmed", "schlafmusik", "schlafyoga", "schlafhilfe", "schlaflos", "durchschlaf")
        ),
        ZenPraxisTheme.FITNESS to Rule(
            contains = listOf("workout", "hiit", "krafttraining", "intervalltraining", "rückentraining", "ganzkörpertraining"),
            words = listOf("cardio", "tabata", "pilates", "aerobic", "aerobics", "zumba", "kettlebell", "dumbbell", "dumbbells", "bbp"),
            phrases = listOf(listOf("strength", "training"), listOf("bauch", "beine", "po"))
        )
    )

    /**
     * Lower-case runs of letters/digits; hashtags (`#yoganidra`) are skipped, they are tags of the
     * channel rather than what the video is. No regex (ICU and the JVM differ in word boundaries).
     */
    @JvmStatic
    fun tokens(title: String?): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var tag = false
        val t = (title ?: "").lowercase(java.util.Locale.ROOT)
        var i = 0
        while (i < t.length) {
            val cp = t.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isLetterOrDigit(cp)) {
                cur.appendCodePoint(cp)
                continue
            }
            if (cur.isNotEmpty() && !tag) out.add(cur.toString())
            if (cur.isNotEmpty() || cp != '#'.code) tag = false
            cur.setLength(0)
            if (cp == '#'.code) tag = true
        }
        if (cur.isNotEmpty() && !tag) out.add(cur.toString())
        return out
    }

    /** Themes the title names clearly (bit mask, can have more than [MAX] bits). */
    @JvmStatic
    fun titleMask(title: String?): Int {
        val tk = tokens(title)
        if (tk.isEmpty()) return 0
        var mask = 0
        for ((theme, r) in RULES) {
            val hit = r.contains.any { c -> tk.any { c in it } } ||
                tk.any { it in r.words } ||
                r.prefixes.any { p -> tk.any { it.startsWith(p) } } ||
                r.phrases.any { ph -> (0..tk.size - ph.size).any { i -> tk.subList(i, i + ph.size) == ph } }
            if (hit) mask = mask or theme.bit
        }
        return mask
    }

    // ------------------------------------------------------------------ model

    /** Themes the model assigns at [level] ("T"/"D"), strongest first. */
    @JvmStatic
    fun modelThemes(probs: Map<String, Double>?, level: String?): List<ZenPraxisTheme> {
        if (probs.isNullOrEmpty()) return emptyList()
        return ZenPraxisTheme.entries.mapNotNull { t ->
            val p = probs[t.modelKey] ?: return@mapNotNull null
            val min = if (level == "D") t.minD else t.minT ?: return@mapNotNull null
            if (p >= min) t to p else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    // ------------------------------------------------------------------ resolution

    /**
     * [user] = the user's correction (null = none; 0 = "none of them"), [modelProbs]/[level] = the
     * cached theme values (null without model or not classified yet), [defaultMask] = default of the
     * source channel/playlist (0 = none).
     */
    @JvmStatic
    fun resolve(user: Int?, title: String?, modelProbs: Map<String, Double>?, level: String?, defaultMask: Int): Resolved {
        if (user != null) return Resolved(sanitize(user), Source.USER)
        val rules = themes(titleMask(title))
        val model = modelThemes(modelProbs, level).filter { it !in rules }
        val picked = (rules + model).take(MAX)
        if (picked.isNotEmpty()) return Resolved(mask(picked), if (rules.isNotEmpty()) Source.TITLE else Source.MODEL)
        val def = themes(defaultMask).take(MAX)
        if (def.isNotEmpty()) return Resolved(mask(def), Source.DEFAULT)
        return Resolved(0, Source.NONE)
    }
}
