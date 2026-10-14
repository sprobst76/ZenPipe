package org.schabi.newpipe.zen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.zen.classify.LangEntry
import org.schabi.newpipe.zen.classify.MemoryClassStore
import org.schabi.newpipe.zen.classify.ZenClassCache

/**
 * Feed language filter: detection on invented titles (no real data), the "unsure = keep" rule,
 * the channel majority and the setting (mask, stricter/looser). The detection rates on this set
 * are printed (see zen-docs/spec-klassifikator-integration.md, section 14).
 */
class ZenLanguageTest {
    private val allowed = setOf("de", "en")

    private val german = listOf(
        "Rust für Anfänger", "Es ist vorbei", "Warum wir schlecht schlafen – und was hilft", "Die besten Tools für dein Homelab",
        "Ich habe 30 Tage keinen Zucker gegessen", "So funktioniert eine Wärmepumpe wirklich", "Raspberry Pi 5 im Test: lohnt sich das Upgrade?",
        "Wie KI unsere Arbeit verändert", "10 Fehler, die jeder Anfänger macht", "Das Ende der Glühbirne", "Mein erstes Mal Bouldern",
        "Linux Mint vs. Ubuntu – was ist besser?", "Wir bauen ein Gartenhaus (Teil 3)", "Neue Studie: Kaffee und Gesundheit",
        "Warum Züge in Deutschland zu spät sind", "Brot backen ohne Hefe", "Yoga am Morgen – 20 Minuten", "Excel Tricks, die du kennen musst",
        "Der große Smartphone-Vergleich 2026", "Heute koche ich Linsensuppe", "Elektroauto im Winter: Reichweite im Test",
        "Home Assistant Dashboard erklärt", "Kann man das noch reparieren?", "Gute Nacht Geschichte für Erwachsene",
        "Was passiert im Gehirn beim Meditieren?", "Steuererklärung 2026 einfach erklärt", "Abnehmen ohne Hunger?",
        "Ich teste den billigsten Laptop", "Bundestag: Haushalt beschlossen", "Unterwegs in Norwegen mit dem Fahrrad"
    )

    private val english = listOf(
        "Our Favorite ML Classifier vs. Jev", "How to Build a Home Server in 2026", "Why Sleep Matters More Than You Think",
        "I Tried Every Budget Keyboard", "The Truth About Intermittent Fasting", "10 Minute Morning Stretch", "Rust in 100 Seconds",
        "What Nobody Tells You About Solar Panels", "Building a Raspberry Pi Cluster", "This Changed Everything",
        "Apple Vision Pro Review: Six Months Later", "Linux Kernel Explained", "We Need to Talk About AI Agents", "Learn Python in One Video",
        "My Favorite Books of the Year", "The Science of Habit Formation", "Breaking News: Storm Hits Coast", "Why Are Trains So Expensive?",
        "Easy Sourdough Bread for Beginners", "Can You Survive on \$5 a Day?", "Inside the Largest Data Center", "Gaming PC Build Guide",
        "Quiet Morning Routine", "The History of the Internet", "Is Coffee Bad for You?", "Tesla Model Y Long Term Review",
        "Meditation for Anxiety", "Everything Wrong with Smart Homes", "How Batteries Actually Work", "Final Thoughts on Remote Work"
    )

    /** Brand names, mixed titles: must never be hidden (an allowed or unknown result is fine). */
    private val mixed = listOf(
        "iPhone 17 Pro Max Unboxing", "Tesla Cybertruck", "Minecraft Let's Play #42", "Sony WH-1000XM6", "Café del Mar Chillout Mix",
        "La Casa de Papel – Staffel 5 Review", "Despacito – Reaktion", "Paris vlog: our first day", "Le Mans 24h Highlights",
        "Coq au Vin Rezept", "Dolce Vita in Rom – unser Roadtrip", "Pasta Carbonara wie in Italien", "Tour de France 2026 Recap",
        "Fiesta Mexicana – Party Vlog", "Hasta la vista, Windows 10"
    )

    private val foreign: Map<String, List<String>> = mapOf(
        "es" to listOf(
            "Cómo hacer pan casero en casa",
            "Los mejores trucos para ahorrar dinero",
            "¿Por qué el cielo es azul?",
            "Mi primera vez en Japón",
            "La verdad sobre la inteligencia artificial",
            "Receta fácil de tortilla de patatas",
            "Así es la vida en un pueblo de montaña",
            "El futuro de los coches eléctricos"
        ),
        "fr" to listOf(
            "Comment faire du pain maison",
            "Pourquoi le ciel est bleu ?",
            "Les meilleurs conseils pour bien dormir",
            "J'ai testé le régime cétogène pendant un mois",
            "La vérité sur l'intelligence artificielle",
            "Une journée à Paris avec mon chien",
            "C'est la fin d'une époque",
            "Le futur des voitures électriques"
        ),
        "it" to listOf(
            "Come fare la pasta fresca in casa",
            "Perché il cielo è blu?",
            "I migliori consigli per dormire bene",
            "La verità sull'intelligenza artificiale",
            "Una giornata a Roma con il mio cane",
            "Ricetta facile della nonna",
            "Questo è il futuro delle auto elettriche",
            "Ho provato la dieta mediterranea per un mese"
        ),
        "pt" to listOf(
            "Como fazer pão caseiro",
            "Por que o céu é azul?",
            "As melhores dicas para dormir bem",
            "A verdade sobre a inteligência artificial",
            "Um dia em Lisboa com o meu cão",
            "Receita fácil de bolo de cenoura",
            "Isso mudou tudo para mim",
            "O futuro dos carros elétricos"
        ),
        "ca" to listOf(
            "No soc adoptada, vaig ser adoptada. I això em condiciona",
            "Abans d'emmalaltir: L'hospital del futur comença abans de l'hospital",
            "Com fer pa a casa",
            "Per què el cel és blau?",
            "Els millors consells per dormir bé",
            "Un dia a Barcelona amb el meu gos",
            "La veritat sobre la intel·ligència artificial",
            "El futur dels cotxes elèctrics"
        ),
        "nl" to listOf(
            "Hoe maak je zelf brood",
            "Waarom is de lucht blauw?",
            "De beste tips om goed te slapen",
            "De waarheid over kunstmatige intelligentie",
            "Een dag in Amsterdam met mijn hond",
            "Makkelijk recept voor appeltaart",
            "Dit heeft alles veranderd",
            "De toekomst van elektrische auto's"
        ),
        "tr" to listOf(
            "Evde ekmek nasıl yapılır",
            "Gökyüzü neden mavi?",
            "İyi uyumak için en iyi ipuçları",
            "Yapay zeka hakkında gerçekler",
            "İstanbul'da bir gün",
            "Kolay kek tarifi",
            "Bu her şeyi değiştirdi",
            "Elektrikli arabaların geleceği"
        ),
        "pl" to listOf(
            "Jak zrobić domowy chleb",
            "Dlaczego niebo jest niebieskie?",
            "Najlepsze sposoby na dobry sen",
            "Prawda o sztucznej inteligencji",
            "Jeden dzień w Krakowie z moim psem",
            "Łatwy przepis na sernik",
            "To zmieniło wszystko",
            "Przyszłość samochodów elektrycznych"
        ),
        "ru" to listOf("Как испечь хлеб дома", "Почему небо голубое?", "Лучшие советы для сна"),
        "ar" to listOf("كيف تصنع الخبز في المنزل", "لماذا السماء زرقاء؟", "أفضل النصائح للنوم"),
        "hi" to listOf("घर पर रोटी कैसे बनाएं", "आसमान नीला क्यों है", "अच्छी नींद के लिए टिप्स"),
        "ja" to listOf("家でパンを作る方法", "なぜ空は青いのか", "よく眠るためのコツ"),
        "ko" to listOf("집에서 빵 만드는 법", "하늘은 왜 파란가요", "잠을 잘 자는 방법"),
        "zh" to listOf("如何在家做面包", "为什么天空是蓝色的", "睡个好觉的技巧")
    )

    private fun hidden(title: String, channel: String = "Kanal") = ZenLangFilter.decide(ZenLangDetect.detect(title, channel), emptyList(), allowed).hidden

    @Test
    fun `german, english and mixed titles are never hidden`() {
        (german + english + mixed).forEach { assertFalse(it, hidden(it)) }
        // and the clear ones are recognised as their language
        val deRight = german.count { ZenLangDetect.detect(it, null).lang == "de" }
        val enRight = english.count { ZenLangDetect.detect(it, null).lang == "en" }
        println("lang-test de recognised $deRight/${german.size}, en recognised $enRight/${english.size}")
        mixed.forEach { println("lang-test mixed '$it' -> ${ZenLangDetect.detect(it, null).lang}") }
    }

    @Test
    fun `required short titles`() {
        assertEquals("de", ZenLangDetect.detect("Rust für Anfänger", null).lang)
        assertEquals("de", ZenLangDetect.detect("Es ist vorbei", null).lang)
        assertEquals("en", ZenLangDetect.detect("Our Favorite ML Classifier vs. Jev", null).lang)
        assertNull(ZenLangDetect.detect("Tesla Cybertruck", null).lang)
        // the two Catalan TEDx titles: recognised and hidden without any channel history
        listOf(
            "No soc adoptada, vaig ser adoptada. I això em condiciona",
            "Abans d'emmalaltir: L'hospital del futur comença abans de l'hospital"
        ).forEach {
            assertEquals(it, "ca", ZenLangDetect.detect(it, "TEDx Talks").lang)
            assertTrue(it, hidden(it, "TEDx Talks"))
        }
    }

    @Test
    fun `foreign titles report`() {
        var total = 0
        var hiddenCount = 0
        var rightLang = 0
        foreign.forEach { (lang, titles) ->
            val h = titles.count { hidden(it) }
            val r = titles.count { ZenLangDetect.detect(it, null).lang == lang }
            total += titles.size
            hiddenCount += h
            rightLang += r
            println("lang-test $lang hidden $h/${titles.size} recognised $r/${titles.size}")
            titles.filter { !hidden(it) }.forEach { t ->
                val g = ZenLangDetect.detect(t, null)
                println("lang-test   kept: '$t' -> ${g.lang} ${"%.2f".format(ZenLangFilter.exclusionScore(g, allowed))} ${g.scores}")
            }
        }
        println("lang-test foreign hidden $hiddenCount/$total, language right $rightLang/$total")
        // non-Latin scripts are always clear
        listOf("ru", "ar", "hi", "ja", "ko", "zh").forEach { l -> foreign.getValue(l).forEach { assertTrue(it, hidden(it)) } }
        assertTrue("most foreign titles hidden", hiddenCount >= total * 0.75)
    }

    @Test
    fun `unsure stays, channel majority decides medium cases and protects allowed channels`() {
        val g = ZenLangGuess("es", 0.4, mapOf("es" to 1.5), "T") // medium evidence (exclusion 0.5)
        assertEquals(ZenLangFilter.Verdict.UNCERTAIN, ZenLangFilter.decide(g, emptyList(), allowed).verdict)
        val spanishChannel = List(4) { ZenLangGuess("es", 0.9, mapOf("es" to 4.0), "T") }
        assertTrue(ZenLangFilter.decide(g, spanishChannel, allowed).hidden)
        val strong = ZenLangGuess("ca", 0.8, mapOf("ca" to 4.0, "de" to 0.8), "T") // exclusion 0.8
        assertTrue(ZenLangFilter.decide(strong, emptyList(), allowed).hidden)
        val germanChannel = List(6) { ZenLangGuess("de", 0.9, mapOf("de" to 4.0), "T") }
        assertFalse("a German channel protects against one wrong judgement", ZenLangFilter.decide(strong, germanChannel, allowed).hidden)
        val details = ZenLangDetect.fromDetails("ca-ES")!!
        assertTrue("a hard detail language beats the channel", ZenLangFilter.decide(details, germanChannel, allowed).hidden)
        assertFalse(ZenLangFilter.decide(ZenLangDetect.fromDetails("de-DE")!!, emptyList(), allowed).hidden)
        assertFalse(ZenLangFilter.decide(ZenLangGuess.UNKNOWN, spanishChannel, allowed).hidden)
        // allowing the language shows it again
        assertFalse(ZenLangFilter.decide(strong, emptyList(), allowed + "ca").hidden)
    }

    @Test
    fun `stored results are reused, details are never replaced by the heuristic`() {
        val store = ZenClassCache(MemoryClassStore())
        val item = ZenFeedLanguages.Item("0:u1", "ch", "No soc adoptada, vaig ser adoptada. I això em condiciona", "TEDx Talks")
        val first = ZenFeedLanguages.annotate(store, listOf(item), allowed, 1)
        assertTrue(first.getValue("0:u1").decision.hidden)
        store.putLangs(listOf(LangEntry("0:u1", "ch", item.title, item.channelName, ZenLangDetect.fromDetails("en")!!, 2)))
        val second = ZenFeedLanguages.annotate(store, listOf(item), allowed, 3)
        assertEquals("D", second.getValue("0:u1").entry.guess.source)
        assertFalse(second.getValue("0:u1").decision.hidden)
        assertEquals("ca", ZenLangGuess.decodeScores(ZenLangGuess.encodeScores(first.getValue("0:u1").entry.guess.scores)).keys.first())
    }

    @Test
    fun `language setting is a mask, fewer languages are stricter`() {
        val def = ZenSettingDefs.get(ZenSettingKeys.FEED_LANGS)!!
        assertEquals(setOf("de", "en"), ZenLangOptions.codes(def.default))
        val de = ZenLangOptions.mask(listOf("de"))
        val deEnCa = ZenLangOptions.mask(listOf("de", "en", "ca"))
        val deCa = ZenLangOptions.mask(listOf("de", "ca"))
        assertTrue(def.isStricter(def.default, de))
        assertFalse(def.isStricter(def.default, deEnCa))
        assertFalse("adding and removing is a loosening", def.isStricter(def.default, deCa))
        assertFalse("no language at all is not allowed", def.inRange(0))
        assertTrue(def.inRange(ZenLangOptions.ALL_MASK))
    }

    // ---- held-out set: written after the word lists were tuned on the sets above, not tuned on ----

    private val germanB = listOf(
        "Endlich wieder Sommer im Garten", "Wie gefährlich ist Mikroplastik?", "Unser Umzug nach Leipzig", "Fahrradreparatur: Kette wechseln",
        "Darum sind Mieten so hoch", "Smart Home ohne Cloud", "Kochen mit Resten – drei Ideen", "Wir reagieren auf eure Kommentare",
        "Was taugt der neue Kindle?", "Gehirnjogging für Senioren", "Fünf Gewohnheiten, die mein Leben verändert haben",
        "Hörbuch: Der kleine Prinz", "Sicher im Netz: Passwörter verwalten", "Wanderung zum Königssee", "Grundsteuer verständlich",
        "Mathe für die Oberstufe: Integrale", "Waldbaden – was steckt dahinter?", "Ein Tag als Lokführer", "Frühstück für Faule",
        "Analyse: Die Wahl in Thüringen"
    )

    private val englishB = listOf(
        "Spring Garden Tour", "How Dangerous Are Microplastics?", "Moving to a New City", "Fixing My Bike Chain", "Why Rent Is So High",
        "Self-Hosted Smart Home Without the Cloud", "Cooking With Leftovers", "Reacting to Your Comments", "Kindle Paperwhite Honest Review",
        "Brain Training for Seniors", "Five Habits That Changed My Life", "Audiobook: The Little Prince", "Password Managers Compared",
        "Hiking the Swiss Alps", "Taxes Made Simple", "Calculus Basics: Integrals", "Forest Bathing Explained", "A Day as a Train Driver",
        "Breakfast Ideas for Lazy People", "Election Night Analysis"
    )

    private val mixedB = listOf(
        "Bella Italia: unser Urlaub am Gardasee", "Huevos Rancheros Recipe", "Mamma Mia Musical Reaktion", "Croissants selber backen",
        "Viva la Vida – Piano Cover", "Sushi Sunday", "El Clásico Highlights", "Déjà-vu explained", "Il Divo Live in Berlin",
        "Pão de Queijo – brasilianisches Rezept"
    )

    private val foreignB: Map<String, List<String>> = mapOf(
        "es" to listOf(
            "Qué hacer si te duele la espalda",
            "Viajamos al desierto de Atacama",
            "Las noticias de la semana",
            "Cinco libros que debes leer",
            "Probamos la comida más picante",
            "Cómo aprender inglés rápido"
        ),
        "fr" to listOf(
            "Que faire quand on a mal au dos",
            "Nous sommes partis dans le désert",
            "Les nouvelles de la semaine",
            "Cinq livres à lire absolument",
            "On a goûté la nourriture la plus épicée",
            "Apprendre l'anglais rapidement"
        ),
        "it" to listOf(
            "Cosa fare quando fa male la schiena",
            "Siamo andati nel deserto",
            "Le notizie della settimana",
            "Cinque libri da leggere",
            "Abbiamo assaggiato il cibo più piccante",
            "Imparare l'inglese velocemente"
        ),
        "pt" to listOf(
            "O que fazer quando dói as costas",
            "Fomos para o deserto",
            "As notícias da semana",
            "Cinco livros que você precisa ler",
            "Provamos a comida mais picante",
            "Como aprender inglês rápido"
        ),
        "ca" to listOf(
            "Què fer quan et fa mal l'esquena",
            "Hem anat al desert",
            "Les notícies de la setmana",
            "Cinc llibres que has de llegir",
            "Hem tastat el menjar més picant",
            "Aprendre anglès ràpidament"
        ),
        "nl" to listOf(
            "Wat te doen bij rugpijn",
            "We gingen naar de woestijn",
            "Het nieuws van de week",
            "Vijf boeken die je moet lezen",
            "We proefden het pittigste eten",
            "Snel Engels leren"
        ),
        "tr" to listOf(
            "Sırt ağrısı için ne yapmalı",
            "Çöle gittik",
            "Haftanın haberleri",
            "Okumanız gereken beş kitap",
            "En acı yemeği denedik",
            "Hızlı İngilizce öğrenmek"
        ),
        "pl" to listOf(
            "Co robić, gdy bolą plecy",
            "Pojechaliśmy na pustynię",
            "Wiadomości tygodnia",
            "Pięć książek, które musisz przeczytać",
            "Spróbowaliśmy najostrzejszego jedzenia",
            "Szybka nauka angielskiego"
        ),
        "ru" to listOf("Что делать, если болит спина", "Новости недели"),
        "uk" to listOf("Що робити, якщо болить спина", "Новини тижня"),
        "ar" to listOf("أخبار الأسبوع", "خمسة كتب يجب قراءتها"),
        "ja" to listOf("腰が痛いときにすること", "今週のニュース"),
        "ko" to listOf("허리가 아플 때 할 일", "이번 주 뉴스"),
        "zh" to listOf("背痛时该怎么办", "本周新闻"),
        "hi" to listOf("पीठ दर्द में क्या करें", "इस हफ्ते की खबरें")
    )

    @Test
    fun `held-out set report`() {
        val falseHidden = (germanB + englishB + mixedB).filter { hidden(it) }
        println("lang-testB de/en/mixed wrongly hidden ${falseHidden.size}/${germanB.size + englishB.size + mixedB.size} $falseHidden")
        println(
            "lang-testB de recognised ${germanB.count { ZenLangDetect.detect(it, null).lang == "de" }}/${germanB.size}, " +
                "en recognised ${englishB.count { ZenLangDetect.detect(it, null).lang == "en" }}/${englishB.size}"
        )
        var total = 0
        var hiddenCount = 0
        var right = 0
        foreignB.forEach { (lang, titles) ->
            val h = titles.count { hidden(it) }
            val r = titles.count { ZenLangDetect.detect(it, null).lang == lang }
            total += titles.size
            hiddenCount += h
            right += r
            println("lang-testB $lang hidden $h/${titles.size} recognised $r/${titles.size}")
            titles.filter { !hidden(it) }.forEach { t ->
                val g = ZenLangDetect.detect(t, null)
                println("lang-testB   kept: '$t' -> ${g.lang} ${"%.2f".format(ZenLangFilter.exclusionScore(g, allowed))}")
            }
        }
        println("lang-testB foreign hidden $hiddenCount/$total, language right $right/$total")
        // with a channel of the same language (its other titles as history) the medium cases go too
        var withChannel = 0
        foreignB.values.forEach { titles ->
            titles.forEach { t ->
                val history = titles.filter { it != t }.map { ZenLangDetect.detect(it, null) }
                if (ZenLangFilter.decide(ZenLangDetect.detect(t, null), history, allowed).hidden) withChannel++
            }
        }
        println("lang-testB foreign hidden with channel history $withChannel/$total")
        assertTrue("never hide German/English: $falseHidden", falseHidden.isEmpty())
    }

    /** Decision for a title with detail signals (no channel history). */
    private fun withDetails(title: String, field: String? = null, audio: String? = null, asr: String? = null): ZenLangFilter.Decision = ZenLangFilter.decide(ZenLangDetect.combine(ZenLangDetect.detect(title, "TEDxBocconiUMumbai"), ZenLangSignals.of(field, audio, asr)), emptyList(), allowed)

    /**
     * Hierarchy of the detail signals (ZenLangDetect.combine): field/original audio are hard but
     * lose against a clearly different title language; the automatic caption track (ASR) only
     * confirms the title and never hides on its own (device test: English TEDx talks with ASR "hi").
     */
    @Test
    fun `automatic captions alone never hide, they only confirm the title`() {
        val english = "How I learned to listen to my body | Priya Mehta | TEDxBocconiUMumbai"
        assertEquals("en", ZenLangDetect.detect(english, "TEDxBocconiUMumbai").lang)
        // English title + ASR hi -> the title wins, stays
        val en = withDetails(english, asr = "hi")
        assertFalse(en.hidden)
        assertEquals(ZenLangFilter.Verdict.ALLOWED, en.verdict)
        assertEquals("S:hi", ZenLangDetect.combine(ZenLangDetect.detect(english, null), ZenLangSignals.of(null, null, "hi")).note)
        // Catalan title + ASR ca -> confirmed, hidden (even where the title alone is unsure)
        val catalan = "Sobre assassins silenciosos i àngels de la guarda"
        val ca = withDetails(catalan, asr = "ca")
        assertTrue(ca.hidden)
        assertEquals("ca", ca.lang)
        assertEquals("TS", ZenLangDetect.combine(ZenLangDetect.detect(catalan, null), ZenLangSignals.of(null, null, "ca-ES")).source)
        // unsure title + ASR hi -> "captions only", stays
        val unsure = "Ganesh Chaturthi 2026"
        assertNull(ZenLangDetect.detect(unsure, "TEDxBocconiUMumbai").lang)
        val hiOnly = withDetails(unsure, asr = "hi")
        assertFalse(hiOnly.hidden)
        assertEquals(ZenLangFilter.Verdict.UNCERTAIN, hiOnly.verdict)
        assertEquals("hi", hiOnly.lang)
        // Devanagari title -> hidden by the script, with or without contradicting captions
        val devanagari = "मन की शांति के लिए ध्यान"
        assertTrue(withDetails(devanagari).hidden)
        assertTrue(withDetails(devanagari, asr = "en").hidden)
        assertTrue(withDetails(devanagari, asr = "hi").hidden)
    }

    @Test
    fun `original audio track is hard unless the title clearly says otherwise`() {
        val english = "How I learned to listen to my body | Priya Mehta | TEDxBocconiUMumbai"
        // contradiction (multi-language video with a clearly English title, often dubbed): stays
        val contra = withDetails(english, audio = "hi")
        assertFalse(contra.hidden)
        assertEquals("A:hi", ZenLangDetect.combine(ZenLangDetect.detect(english, null), ZenLangSignals.of(null, "hi-IN", null)).note)
        // unsure title: the original audio track decides
        assertTrue(withDetails("Ganesh Chaturthi 2026", audio = "hi").hidden)
        // agreeing title: hidden; the language field counts like the audio track
        assertTrue(withDetails("Sobre assassins silenciosos i àngels de la guarda", audio = "ca").hidden)
        assertTrue(withDetails("Ganesh Chaturthi 2026", field = "es").hidden)
        assertFalse(withDetails("Ganesh Chaturthi 2026", audio = "de").hidden)
    }

    @Test
    fun `captions-only languages are no channel evidence`() {
        val captionsOnly = List(6) { ZenLangDetect.combine(ZenLangGuess.UNKNOWN, ZenLangSignals.of(null, null, "hi")) }
        assertNull(ZenLangFilter.channelMajority(captionsOnly, ZenLangFilter.CHANNEL_MIN, ZenLangFilter.CHANNEL_SHARE))
        val g = ZenLangGuess("hi", 0.5, mapOf("hi" to 1.5), "T") // medium title evidence
        assertFalse(ZenLangFilter.decide(g, captionsOnly, allowed).hidden)
        val hardHindi = List(4) { ZenLangDetect.fromDetails("hi", "A")!! }
        assertTrue(ZenLangFilter.decide(g, hardHindi, allowed).hidden)
    }

    @Test
    fun `stored signals are re-evaluated without a new request`() {
        val store = ZenClassCache(MemoryClassStore())
        val title = "How I learned to listen to my body | Priya Mehta | TEDxBocconiUMumbai"
        val signals = ZenLangSignals.of(null, null, "hi")!!
        assertEquals(signals, ZenLangSignals.decode(signals.encode()))
        assertNull(ZenLangSignals.decode("f=;a=;s="))
        // an entry stored with an older (stricter) result: the rule re-evaluates the raw signals
        val stale = LangEntry("0:t1", "ch", title, "TEDxBocconiUMumbai", ZenLangDetect.fromDetails("hi")!!, 1, signals)
        store.putLangs(listOf(stale))
        val r = ZenFeedLanguages.decide(store, store.langs(listOf("0:t1")).values, allowed).getValue("0:t1")
        assertFalse(r.decision.hidden)
        assertEquals("en", r.guess.lang)
        val fresh = ZenFeedLanguages.detailsEntry("0:t2", "ch", "No soc adoptada, vaig ser adoptada. I això em condiciona", "TEDx Talks", ZenLangSignals.of(null, null, "ca")!!, 2)
        assertEquals("TS", fresh.guess.source)
    }
}
