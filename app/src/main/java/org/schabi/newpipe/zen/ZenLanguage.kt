/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen

import java.util.Locale

/**
 * Result of a language detection. [lang] = ISO 639-1 code or null (unknown); [confidence] = how
 * clearly [lang] beats the second best language (0..1, for display); [scores] = the evidence per
 * language (the filter compares it with the allowed languages); [source] = where [lang] comes from
 * (see [ZenLangDetect.combine]): "T" title + channel heuristic, "D" the service's language field,
 * "A" original audio track, "TS" title confirmed by the automatic captions, "S" automatic captions
 * only (weak, never hides). [note] = a detail signal the title overruled ("A:hi", "S:hi"; display).
 */
data class ZenLangGuess(
    val lang: String?,
    val confidence: Double,
    val scores: Map<String, Double>,
    val source: String,
    val note: String? = null
) {
    /** A source that may hide a video on its own (besides the title heuristic). */
    val isHardDetails get() = source in HARD_DETAILS

    companion object {
        @JvmField
        val UNKNOWN = ZenLangGuess(null, 0.0, emptyMap(), "T")

        /** Detail sources with full strength ("D" also covers entries of older versions). */
        val HARD_DETAILS = setOf("D", "A", "TS")

        fun encodeScores(m: Map<String, Double>) = m.entries.sortedByDescending { it.value }.take(4)
            .joinToString(",") { "${it.key}=${String.format(Locale.ROOT, "%.2f", it.value)}" }

        fun decodeScores(s: String?): Map<String, Double> = LinkedHashMap<String, Double>().apply {
            s.orEmpty().split(',').forEach {
                val i = it.indexOf('=')
                if (i > 0) it.substring(i + 1).toDoubleOrNull()?.let { v -> put(it.substring(0, i), v) }
            }
        }
    }
}

/**
 * The raw language signals of the video details (normalized ISO codes): the service's own language
 * [field] (YouTube never fills it), the [audio] locale of the ORIGINAL track of a multi-language
 * video and the language of the single automatic speech-recognition caption track ([asr]). Stored
 * with the language entry, so a changed rule re-evaluates them without a new request.
 */
data class ZenLangSignals(val field: String?, val audio: String?, val asr: String?) {
    fun encode() = "f=${field.orEmpty()};a=${audio.orEmpty()};s=${asr.orEmpty()}"

    companion object {
        /** Signals from raw tags, or null when none of them names a language. */
        @JvmStatic
        fun of(field: String?, audio: String?, asr: String?): ZenLangSignals? {
            val s = ZenLangSignals(ZenLangDetect.normalize(field), ZenLangDetect.normalize(audio), ZenLangDetect.normalize(asr))
            return if (s.field == null && s.audio == null && s.asr == null) null else s
        }

        @JvmStatic
        fun decode(s: String?): ZenLangSignals? {
            if (s.isNullOrEmpty()) return null
            val m = s.split(';').mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()
            return of(m["f"], m["a"], m["s"])
        }
    }
}

/**
 * Local language detection of a video from its title (and, weakly, the channel name): no library,
 * no network, no Google services. Non-Latin scripts decide directly (Japanese kana, Hangul, Han,
 * Arabic, Devanagari, Cyrillic, Greek, Hebrew, Thai). Latin-script titles are scored by frequent
 * short words, elisions (l', d'), letters that belong to few languages (ß, ñ, ã, ł, ğ, l·l …) and a
 * few endings; a word shared by n languages counts 1/n for each. Short or ambiguous titles stay
 * unknown, and the filter never hides a video for an unclear result.
 */
object ZenLangDetect {
    /** Evidence (score points) for a full-strength decision. */
    const val FULL_EVIDENCE = 2.5

    /** Below this score the title says nothing about its language. */
    const val MIN_SCORE = 1.0

    /** Channel-name words count this much. */
    private const val CHANNEL_WEIGHT = 0.5

    /** Share of non-Latin letters from which the script decides. */
    private const val SCRIPT_SHARE = 0.25

    private val WORDS: Map<String, String> = mapOf(
        "de" to "nie rezept machen mache macht gemacht habe hast bin bist seid kannst möchte gehen nichts etwas jemand jeder jede andere anderen viele wenig gut ja nein morgen gestern nacht woche monat jahr jahre vor während tipps dinge leute mann frau kinder geschichte ende neu reise essen stadt wasser auto schlafen wann ganz echt zukunft unterwegs test vergleich lohnt besser schnell deutschland minuten stunden tage tagen zeit leben welt mensch menschen frage antwort richtig falsch sollten würde wäre könnte der die das und ist in nicht ein eine einen einem einer mit für auf ich du er wir ihr sie es wie was warum wer wo " +
            "zu zum zur im den dem des von vom bei aus über unter nach noch nur auch mein meine dein deine sein seine " +
            "unser unsere kein keine mehr sind wird werden wurde hat haben hatte waren einfach jetzt heute alle alles gibt " +
            "gegen ohne oder aber wenn weil dass doch schon sehr viel immer wieder hier mal sich uns euch neue neuen neues " +
            "besten erklärt geht kann können muss müssen will wollen soll sollte vorbei endlich wirklich richtig warum " +
            "anfänger teil folge diese dieser dieses dies selbst zwischen damit dabei davon darum gut schlecht groß klein",
        "en" to "ways tips made use using did got going go see look know think say said take come back down off only most much many some any no yes very too also still even again actually finally better worst good bad big small long short free real true work works working build built inside behind without through under between against during while until since tried tested test versus update news today tomorrow yesterday night morning week month matters things thing truth story history science expensive cheap easy hard fast slow nobody everyone someone anyone survive largest biggest wrong right quiet simple minute minutes hours seconds thoughts final beginner routine talk tells tell changed change habit matter happened happens the and is are of to in for with on you your how what why when this that it its my we be not from at by an " +
            "was will can do does don't i'm i've it's best new vs top more about all just get make one first after before " +
            "into out up over than these those they their there here his her our who which would should could has have " +
            "had been every never ever really need want like here's what's isn't doesn't can't won't we're you're they're " +
            "favorite favourite review tutorial beginners guide explained official full video episode part day week life " +
            "world people things everything nothing something time way year years home learn",
        "es" to "mejores peor gran grande pequeño casa dinero verdad fácil receta hice hago hacemos tener tengo puedo puede pueden quiero voy vamos nuestra nuestro nuestros ellos ellas usted ustedes allí nada algo alguien cada otro otra otros otras mucho mucha muchos muchas poco tan tanto bien mal sí hoy mañana ayer noche semana mes año después antes mientras durante contra según hacia trucos consejos gente hombre mujer niños historia parte final nuevos nuevas viaje comida ciudad país agua coche coches casero casera dormir el la los las de del que y en es por para con una un su sus lo como más pero porque qué cómo cuál muy " +
            "sin sobre este esta estos esto ese eso hay ser fue son está están tiene tienen hacer todo todos nuevo nueva " +
            "mejor mi tu yo nos le se también cuando donde ya ni hasta desde entre así aquí ahora siempre nunca vez mundo " +
            "vida primera primero años día cosas tiempo",
        "fr" to "meilleurs meilleur meilleure meilleures pire grand grande petit petite maison argent vérité facile recette fais font faites ai as avons avez ont suis es sommes êtes peut peux peuvent veux veut voici voilà rien quelque chacun autre autres beaucoup peu si bien mal oui non aujourd'hui demain hier nuit semaine mois année après avant pendant contre selon conseils gens homme femme enfants histoire partie fin nouveau nouvelle nouveaux voyage ville pays eau voiture voitures dormir quand où le la les des du de et est un une pour avec dans sur que qui pas ce cette ces il elle ils elles nous vous " +
            "je tu on au aux son sa ses mon ma mes ton ne plus mais ou comment pourquoi quoi tout tous très faire fait " +
            "être avoir sont l' d' qu' j' n' s' c' m' leur leurs été chez sans vers aussi déjà encore jamais toujours " +
            "futur monde vie première premier ans jour choses temps",
        "it" to "migliori migliore peggiore grande piccolo piccola casa soldi verità facile ricetta fare faccio fatto avete hanno sei siamo siete può posso possono voglio vuole ecco niente qualcosa ognuno altro altra altri altre molti molte poco così bene male sì no domani ieri notte settimana mese anno dopo prima durante contro senza verso consigli cose gente uomo donna bambini storia parte fine nuovo nuova nuovi viaggio città paese acqua macchina auto giornata provato dormire il lo la gli le di del della dei delle degli che è e un una uno per con non come perché sono anche questo " +
            "questa quello più ma se ci si mi ti ho hai ha abbiamo nel nella nei al alla ai sul sulla dal dalla tra fra " +
            "molto tutto tutti cosa quando dove sempre mai oggi ancora l' dell' all' un' nell' sull' mondo vita anni giorno",
        "pt" to "melhores pior grande pequeno pequena casa dinheiro verdade fácil receita faço fiz fazemos tenho temos posso pode podem quero vou vamos nossa nosso nossos isso ali nada algo alguém cada outro outra outros outras muitos muitas pouco tão bem mal sim hoje amanhã ontem noite semana mês ano depois antes durante contra sem dicas gente homem mulher crianças história parte final novos novas viagem comida cidade país água carro carros caseiro dormir o a os as de do da dos das que e é em um uma para com não por como mais mas se seu sua seus suas no na " +
            "nos nas ao aos pelo pela este esta isso isto muito também quando onde já até sobre você vocês ele ela eles " +
            "elas eu nós foi são está estão tem ter fazer tudo todos novo nova melhor porque então aqui agora sempre " +
            "nunca vez vida mundo anos dia coisas tempo",
        "ca" to "millors pitjor gran petit petita casa diners veritat fàcil recepta faig fet fem tenir tinc tenim puc pot poden vull anem nostra nostre nostres allà res alguna algú cada altre altra altres molts moltes poc tan bé mal sí nit setmana mes any durant contra sense consells gent home dona nens història part final nous noves viatge menjar ciutat país aigua cotxe cotxes dormir què quan el la els les de del dels que i és en un una per amb no com més però perquè això aquest aquesta aquests " +
            "aquestes molt també quan on ja fins sobre jo tu ell ella nosaltres vosaltres ells elles va vaig van vam " +
            "soc sóc som són ser estar fer tot tots totes nou nova millor aquí ara sempre mai vegada vida món l' d' s' " +
            "n' m' t' abans després avui demà dir hi ho ens us seu seva seus seves meu meva teu teva cap anys dia " +
            "futur coses temps",
        "nl" to "betere slechtste groot grote klein kleine huis geld waarheid makkelijk recept maken maak maakt gemaakt heb hebt ben bent ga gaan gaat niets iets iemand elke ander andere veel weinig zo goed slecht ja nee vandaag gisteren nacht maand jaar tijdens tegen zonder dingen mensen vrouw kinderen geschiedenis deel einde nieuw reis eten stad slapen wanneer waar alles heel echt toekomst de het een en van in is dat op te met voor niet zijn je jij ik we wij wat hoe waarom dit die ook maar " +
            "naar bij uit over als dan nog al er om tot hij zij ze hun mijn jouw onze geen meer wordt worden heeft " +
            "hebben was waren kan kunnen moet moeten wil willen zal zou nieuwe beste waarom",
        "tr" to "en iyi kötü büyük küçük ev para gerçek gerçekler kolay tarif yapmak yaptım yapılır niçin zaman nerede hiç herkes başka az evet hayır bugün yarın dün gece hafta ay yıl sırasında karşı olmadan ipuçları şeyler insanlar adam kadın çocuklar tarih bölüm son yolculuk yemek şehir ülke su araba arabalar uyumak ve bir bu için ile da de ne çok nasıl neden ben sen biz siz onlar değil var yok gibi daha ama mı mi mu mü " +
            "şey her kadar sonra önce olan oldu olarak ki diye bunu şu yeni",
        "pl" to "najlepsze najlepszy najlepsza gorszy duży mały dom pieniądze prawda łatwy przepis zrobić robię zrobiłem mieć mogę mogą chcę idę idziemy nasza nasze oni one nic coś ktoś każdy inny inna inne dużo mało dobrze źle dzisiaj jutro wczoraj noc tydzień miesiąc rok przed podczas przeciw bez porady rzeczy ludzie mężczyzna kobieta dzieci historia część koniec nowa nowe podróż jedzenie miasto kraj woda samochód samochody spać wszystko i w na z do się nie to jest że jak co ale po o od za dla przez czy jego jej ich mój moja twój nasz tak " +
            "już jeszcze tylko bardzo może być był była są będzie mam ma mamy nowy najlepszy dlaczego kiedy gdzie tego " +
            "tym ten ta te"
    )

    /** Letters that belong to few languages (each occurrence counts 1/n). */
    private val LETTERS: Map<Char, String> = mapOf(
        'ß' to "de", 'ä' to "de", 'ö' to "de tr", 'ü' to "de tr",
        'ñ' to "es", '¿' to "es", '¡' to "es",
        'ã' to "pt", 'õ' to "pt", 'ê' to "pt fr", 'ô' to "pt fr", 'â' to "pt fr",
        'ç' to "pt fr ca tr",
        'è' to "fr it ca", 'à' to "fr it ca pt", 'ù' to "fr it", 'û' to "fr", 'î' to "fr", 'œ' to "fr", 'ë' to "fr nl",
        'ì' to "it", 'ò' to "it ca", 'ï' to "ca fr",
        'ğ' to "tr", 'ş' to "tr", 'ı' to "tr", 'İ' to "tr",
        'ą' to "pl", 'ę' to "pl", 'ł' to "pl", 'ś' to "pl", 'ź' to "pl", 'ż' to "pl", 'ć' to "pl", 'ń' to "pl"
    )

    /** Endings (word length >= 6) with their languages; each match counts 0.5/n. */
    private val ENDINGS: List<Pair<String, String>> = listOf(
        "ción" to "es", "ciones" to "es", "ção" to "pt", "ções" to "pt", "ció" to "ca", "cions" to "ca",
        "zione" to "it", "zioni" to "it", "ung" to "de", "heit" to "de", "keit" to "de", "schaft" to "de",
        "lich" to "de", "ing" to "en", "heid" to "nl", "lijk" to "nl", "ness" to "en", "dade" to "pt", "mente" to "es it pt",
        "ità" to "it", "ité" to "fr", "eaux" to "fr", "eux" to "fr"
    )

    /** Letter groups inside words that belong to few languages; each word with one counts 0.3/n. */
    private val GROUPS: List<Pair<String, String>> = listOf(
        "ij" to "nl", "uu" to "nl", "sz" to "pl", "cz" to "pl", "rz" to "pl", "nh" to "pt", "lh" to "pt", "cch" to "it", "tx" to "ca"
    )

    private val ELISION = Regex("^(l|d|s|n|m|t|j|c|qu|dell|all|un|nell|sull)['’](?=\\p{L})")

    private val wordLangs: Map<String, List<String>> by lazy {
        val m = HashMap<String, MutableList<String>>()
        WORDS.forEach { (lang, list) -> list.split(' ').filter { it.isNotEmpty() }.toSet().forEach { m.getOrPut(it) { ArrayList() }.add(lang) } }
        m
    }

    /** All languages the Latin scoring knows (also the order of the setting). */
    val LATIN_LANGS: List<String> = WORDS.keys.toList()

    private enum class Script(val lang: String?) { LATIN(null), KANA("ja"), HANGUL("ko"), HAN("zh"), ARABIC("ar"), DEVANAGARI("hi"), CYRILLIC("ru"), GREEK("el"), HEBREW("he"), THAI("th"), OTHER(null) }

    private fun scriptOf(cp: Int): Script = when (cp) {
        in 0x3040..0x30FF, in 0x31F0..0x31FF -> Script.KANA

        in 0xAC00..0xD7AF, in 0x1100..0x11FF, in 0x3130..0x318F -> Script.HANGUL

        in 0x4E00..0x9FFF, in 0x3400..0x4DBF -> Script.HAN

        in 0x0600..0x06FF, in 0x0750..0x077F, in 0xFB50..0xFDFF, in 0xFE70..0xFEFF -> Script.ARABIC

        in 0x0900..0x097F -> Script.DEVANAGARI

        in 0x0400..0x04FF -> Script.CYRILLIC

        in 0x0370..0x03FF -> Script.GREEK

        in 0x0590..0x05FF -> Script.HEBREW

        in 0x0E00..0x0E7F -> Script.THAI

        else -> if (Character.isLetter(cp)) {
            if (cp < 0x0250 || cp in 0x1E00..0x1EFF) Script.LATIN else Script.OTHER
        } else {
            Script.OTHER
        }
    }

    private const val UKRAINIAN = "іїєґІЇЄҐ"
    private const val PERSIAN = "پچژگ"
    private const val URDU = "ٹڈڑںے"

    /** Detection from the non-Latin script of [text], or null when the text is mainly Latin. */
    private fun byScript(text: String): ZenLangGuess? {
        val counts = HashMap<Script, Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val s = scriptOf(cp)
            // CJK characters carry about a word each: weigh them like a short word in letters
            val w = if (s == Script.KANA || s == Script.HAN || s == Script.HANGUL) 3 else 1
            if (s != Script.OTHER) counts[s] = (counts[s] ?: 0) + w
            i += Character.charCount(cp)
        }
        val total = counts.values.sum()
        if (total == 0) return null
        val nonLatin = counts.filterKeys { it != Script.LATIN }
        val best = nonLatin.maxByOrNull { it.value } ?: return null
        val share = nonLatin.values.sum().toDouble() / total
        if (share < SCRIPT_SHARE) return null
        val lang = when {
            (counts[Script.KANA] ?: 0) > 0 -> "ja"
            best.key == Script.CYRILLIC && text.any { it in UKRAINIAN } -> "uk"
            best.key == Script.ARABIC && text.any { it in URDU } -> "ur"
            best.key == Script.ARABIC && text.any { it in PERSIAN } -> "fa"
            else -> best.key.lang ?: return null
        }
        val strength = FULL_EVIDENCE * 2 * share
        return ZenLangGuess(lang, if (share >= 0.5) 0.95 else 0.7, mapOf(lang to strength), "T")
    }

    private fun tokens(text: String): List<String> {
        val out = ArrayList<String>()
        text.lowercase(Locale.ROOT).replace("l·l", "l\u0001l").split(Regex("[^\\p{L}'’\u0001]+")).forEach { raw ->
            var w = raw.trim('\'', '’')
            if (w.isEmpty()) return@forEach
            ELISION.find(w)?.let { m ->
                out.add(m.groupValues[1] + "'")
                w = w.substring(m.range.last + 1)
            }
            if (w.isNotEmpty()) out.add(w.replace('’', '\''))
        }
        return out
    }

    private fun scoreLatin(text: String, weight: Double, scores: HashMap<String, Double>) {
        fun add(langs: List<String>, w: Double) = langs.forEach { scores[it] = (scores[it] ?: 0.0) + w / langs.size }
        if (text.lowercase(Locale.ROOT).contains("l·l")) add(listOf("ca"), 1.0 * weight)
        for (t in tokens(text)) {
            val word = t.replace('\u0001', '·')
            wordLangs[word]?.let { add(it, weight) }
            if (word.length >= 6) {
                ENDINGS.firstOrNull { word.endsWith(it.first) }?.let { add(it.second.split(' '), 0.5 * weight) }
            }
            if (word.length >= 4) {
                GROUPS.filter { word.contains(it.first) }.forEach { add(it.second.split(' '), 0.3 * weight) }
            }
        }
        text.forEach { ch -> LETTERS[ch.lowercaseChar()]?.let { add(it.split(' '), weight) } ?: LETTERS[ch]?.let { add(it.split(' '), weight) } }
    }

    /** Language of a video from its [title] and (weakly) its [channel] name. */
    @JvmStatic
    fun detect(title: String?, channel: String?): ZenLangGuess {
        val t = title.orEmpty()
        byScript(t)?.let { return it }
        val scores = HashMap<String, Double>()
        scoreLatin(t, 1.0, scores)
        channel?.takeIf { it.isNotBlank() }?.let { c ->
            // a non-Latin channel name is a hint as well (e.g. a Japanese channel with an English title)
            byScript(c)?.let { g -> g.lang?.let { scores[it] = (scores[it] ?: 0.0) + CHANNEL_WEIGHT * FULL_EVIDENCE } } ?: scoreLatin(c, CHANNEL_WEIGHT, scores)
        }
        val sorted = scores.entries.sortedByDescending { it.value }
        val best = sorted.firstOrNull() ?: return ZenLangGuess.UNKNOWN
        if (best.value < MIN_SCORE) return ZenLangGuess(null, 0.0, scores, "T")
        val second = sorted.getOrNull(1)?.value ?: 0.0
        if (best.value - second < 1e-9) return ZenLangGuess(null, 0.0, scores, "T")
        val conf = (1 - second / best.value) * minOf(1.0, best.value / FULL_EVIDENCE)
        return ZenLangGuess(best.key, conf, scores, "T")
    }

    /** ISO 639-1/2 code of a BCP 47 tag or ISO code ("pt-BR" -> "pt"), or null. */
    @JvmStatic
    fun normalize(code: String?): String? {
        val lang = code?.trim()?.takeIf { it.isNotEmpty() }?.let { Locale.forLanguageTag(it.replace('_', '-')).language }
            ?.takeIf { it.length in 2..3 && it != "und" } ?: return null
        return when (lang) {
            "iw" -> "he"
            "in" -> "id"
            else -> lang
        }
    }

    /** A hard detail language ([source] "D" field, "A" original audio, "TS" title + captions); null if none. */
    @JvmStatic
    @JvmOverloads
    fun fromDetails(code: String?, source: String = "D"): ZenLangGuess? {
        val norm = normalize(code) ?: return null
        return ZenLangGuess(norm, DETAILS_CONFIDENCE, mapOf(norm to FULL_EVIDENCE * 2), source)
    }

    /** From this confidence on the title clearly names its language (it then wins against a detail signal). */
    const val CLEAR_TITLE = 0.45

    /**
     * The language of a video from its [title] heuristic ([detect]) and the detail [signals].
     * Hierarchy (a video is hidden only for a foreign language from a hard signal, never for an
     * unclear or contradicted one):
     * 1. Hard signals: the language field, else the original audio track. They decide, unless the
     *    title clearly ([CLEAR_TITLE]) names another language: then the title wins and the signal
     *    is only noted. A multi-language video whose title is clearly English is usually offered to
     *    an English audience (often with a dubbed track), so "unsure = keep" applies.
     * 2. The title heuristic itself (a clear foreign script, foreign words) – unchanged.
     * 3. The automatic caption track (ASR) is weak: YouTube labels it with the language the uploader
     *    set for the channel, not the one spoken (four English TEDxBocconiUMumbai talks carried "hi").
     *    It only confirms the title: same language -> hard ("TS"); another language -> the title
     *    wins; no title language -> "S" (shown as "captions only", never hidden, not counted for the
     *    channel majority).
     */
    @JvmStatic
    fun combine(title: ZenLangGuess, signals: ZenLangSignals?): ZenLangGuess {
        if (signals == null) return title
        val titleClear = title.lang != null && title.confidence >= CLEAR_TITLE
        val hard = signals.field?.let { it to "D" } ?: signals.audio?.let { it to "A" }
        if (hard != null) {
            val (code, src) = hard
            return if (titleClear && title.lang != code) title.copy(note = "$src:$code") else fromDetails(code, src)!!
        }
        val asr = signals.asr ?: return title
        return when (title.lang) {
            asr -> fromDetails(asr, "TS")!!
            null -> ZenLangGuess(asr, 0.0, emptyMap(), "S")
            else -> title.copy(note = "S:$asr")
        }
    }

    const val DETAILS_CONFIDENCE = 0.95
}

/**
 * The feed's language rule: a video in a language that is not allowed is not offered (never
 * deleted). Never because of an unclear detection: the evidence for the foreign language must be
 * strong compared with the allowed languages ([HIDE]), or medium ([MEDIUM]) and backed by the
 * channel's majority language. A clear allowed majority of the channel protects against single
 * wrong judgements below [OVERRIDE].
 */
object ZenLangFilter {
    /** Hide from this exclusion score on, whatever the channel says (unless protected, see [OVERRIDE]). */
    const val HIDE = 0.75

    /** From this score on, the channel's majority language decides. */
    const val MEDIUM = 0.45

    /** A video of a clearly allowed channel is hidden only from this score on. */
    const val OVERRIDE = 0.9

    /** Channel majority: at least this many judged videos and this share. */
    const val CHANNEL_MIN = 3
    const val CHANNEL_SHARE = 0.6

    /** Clearly allowed channel: at least this many judged videos and this share. */
    const val CHANNEL_SAFE_MIN = 5
    const val CHANNEL_SAFE_SHARE = 0.8

    enum class Verdict { ALLOWED, UNKNOWN, UNCERTAIN, HIDDEN }

    data class Decision(val verdict: Verdict, val lang: String?, val score: Double) {
        val hidden get() = verdict == Verdict.HIDDEN
    }

    /**
     * How strongly [g] points to a language outside [allowed] (0..1): the best foreign score
     * against the best allowed score, scaled by the amount of evidence.
     */
    @JvmStatic
    fun exclusionScore(g: ZenLangGuess, allowed: Set<String>): Double {
        val lang = g.lang ?: return 0.0
        if (lang in allowed) return 0.0
        if (g.isHardDetails) return g.confidence
        if (g.source == "S") return 0.0 // captions only: never hides
        val foreign = g.scores.filterKeys { it !in allowed }.values.maxOrNull() ?: return 0.0
        val ok = g.scores.filterKeys { it in allowed }.values.maxOrNull() ?: 0.0
        if (foreign <= 0) return 0.0
        return (1 - ok / foreign).coerceAtLeast(0.0) * minOf(1.0, foreign / ZenLangDetect.FULL_EVIDENCE)
    }

    /**
     * Majority language of a channel from its judged videos (hard details or title [MEDIUM] or
     * more; a caption track alone is no evidence), or null.
     */
    @JvmStatic
    fun channelMajority(history: List<ZenLangGuess>, minCount: Int, minShare: Double): String? {
        val judged = history.filter { it.lang != null && it.source != "S" && (it.isHardDetails || it.confidence >= MEDIUM) }
        if (judged.size < minCount) return null
        val top = judged.groupingBy { it.lang!! }.eachCount().maxByOrNull { it.value } ?: return null
        return if (top.value.toDouble() / judged.size >= minShare) top.key else null
    }

    @JvmStatic
    fun decide(g: ZenLangGuess, channelHistory: List<ZenLangGuess>, allowed: Set<String>): Decision {
        val lang = g.lang ?: return Decision(Verdict.UNKNOWN, null, 0.0)
        if (lang in allowed) return Decision(Verdict.ALLOWED, lang, 0.0)
        val s = exclusionScore(g, allowed)
        val safe = channelMajority(channelHistory, CHANNEL_SAFE_MIN, CHANNEL_SAFE_SHARE)?.let { it in allowed } == true
        val major = channelMajority(channelHistory, CHANNEL_MIN, CHANNEL_SHARE)
        val hidden = when {
            safe -> s >= OVERRIDE
            s >= HIDE -> true
            s >= MEDIUM -> major != null && major !in allowed
            else -> false
        }
        return Decision(if (hidden) Verdict.HIDDEN else Verdict.UNCERTAIN, lang, s)
    }
}

/** The languages offered in the setting "Languages in the feed" (bit i of the mask = [CODES][i]). */
object ZenLangOptions {
    @JvmField
    val CODES = listOf("de", "en", "es", "fr", "it", "pt", "ca", "nl", "tr", "pl", "ru", "uk", "ar", "hi", "ja", "ko", "zh", "el", "he", "th")

    /** German + English. */
    const val DEFAULT_MASK = 0b11

    val ALL_MASK = (1 shl CODES.size) - 1

    @JvmStatic
    fun codes(mask: Int): Set<String> = CODES.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()

    @JvmStatic
    fun mask(codes: Collection<String>): Int = codes.fold(0) { m, c -> CODES.indexOf(c).takeIf { it >= 0 }?.let { m or (1 shl it) } ?: m }

    /** Display name of a language code in [locale] ("Katalanisch" / "Catalan"). */
    @JvmStatic
    fun name(code: String, locale: Locale): String = Locale.forLanguageTag(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }.ifEmpty { code }
}
