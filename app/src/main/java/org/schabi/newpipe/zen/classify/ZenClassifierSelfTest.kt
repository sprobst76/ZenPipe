/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Debug builds only (Settings > Zen): classifies invented, neutral example videos with the
 * installed model, measures load and compute time, and writes everything (inputs, built texts,
 * results, embeddings) to `files/zen_selftest.json` for comparing with the Python reference.
 */
object ZenClassifierSelfTest {
    val SAMPLES = listOf(
        VideoText("Sanftes Yoga am Morgen – 20 Minuten zum Mitmachen", "Ruhige Matte"),
        VideoText(
            "Wie funktionieren Wärmepumpen? Einfach erklärt",
            "Technik verstehen",
            description = "In diesem Video erklären wir Schritt für Schritt, wie eine Wärmepumpe Wärme aus der Umgebung " +
                "holt, warum sie mit Strom effizient heizt und worauf man beim Einbau achten sollte.",
            categoryId = "28",
            tags = listOf("wärmepumpe", "energie", "heizung", "erklärung")
        ),
        VideoText("Bundestag streitet über den Haushalt – die Debatte im Überblick", "Nachrichten am Abend"),
        VideoText("Wir testen 10 verrückte Gadgets aus dem Internet!!", "Spaßkanal"),
        VideoText(
            "Geführte Meditation zum Einschlafen – Regen und ruhiger Atem",
            "Stille Stunde",
            description = "Leg dich bequem hin, schließe die Augen und folge meiner Stimme. Mit sanftem Regen im Hintergrund.",
            categoryId = "22"
        ),
        VideoText("Python Tutorial für Anfänger: Listen und Schleifen", "Code Werkstatt", categoryId = "27"),
        VideoText("Die fünf besten Filme des Jahres – unsere Kritik", "Kinoabend"),
        VideoText("Atemübung gegen Stress: die 4-7-8-Methode zum Mitmachen", "Atemraum"),
        VideoText("Krieg und Krise: was die neue Eskalation bedeutet", "Weltlage"),
        VideoText(
            "Beginner woodworking: building a simple bookshelf",
            "Weekend Workshop",
            description = "We cut, sand and assemble a pine bookshelf with basic hand tools. Measurements in the description.",
            categoryId = "26",
            tags = listOf("woodworking", "diy", "bookshelf")
        )
    )

    data class Summary(val loadMs: LongArray, val medianMs: Long, val maxMs: Long, val lines: List<String>, val file: File)

    /** Blocking; call from a background thread. Null without a usable model. */
    fun run(context: Context): Summary? {
        val inputs = SAMPLES.map { TextBuilder.build(it) to (it.title ?: "") }
        val c = ZenClassifier.get(context)
        val res = c.selfTest(inputs)
        if (res.isEmpty()) return null
        val load = c.lastLoadMs
        val times = res.map { it.ms }.sorted()
        val lines = res.mapIndexed { i, r ->
            "${SAMPLES[i].title}: ${r.cats.keys.ifEmpty { setOf("–") }.joinToString(",")} · ${r.tone ?: "–"} · ${r.tokens} tok · ${r.ms} ms"
        }
        lines.forEach { Log.i(ZenClassifier.TAG, "selftest $it") }
        Log.i(ZenClassifier.TAG, "selftest load ${load.joinToString("/")} ms, median ${times[times.size / 2]} ms, max ${times.last()} ms")
        val json = JsonObject(
            mapOf(
                "model" to JsonPrimitive(ZenClassifierModel.VERSION),
                "loadMs" to JsonArray(load.map { JsonPrimitive(it) }),
                "cases" to JsonArray(
                    res.mapIndexed { i, r ->
                        val v = SAMPLES[i]
                        JsonObject(
                            mapOf(
                                "title" to JsonPrimitive(v.title),
                                "channel" to JsonPrimitive(v.channel),
                                "description" to (v.description?.let { JsonPrimitive(it) } ?: JsonNull),
                                "category" to (v.categoryId?.let { JsonPrimitive(it) } ?: JsonNull),
                                "tags" to (v.tags?.let { t -> JsonArray(t.map { JsonPrimitive(it) }) } ?: JsonNull),
                                "text" to JsonPrimitive(r.text),
                                "tokens" to JsonPrimitive(r.tokens),
                                "ms" to JsonPrimitive(r.ms),
                                "cats" to JsonObject(r.cats.mapValues { JsonPrimitive(it.value) }),
                                "tone" to (r.tone?.let { JsonPrimitive(it) } ?: JsonNull),
                                "embedding" to JsonArray(r.embedding.map { JsonPrimitive(it) })
                            )
                        )
                    }
                )
            )
        )
        val file = File(context.filesDir, "zen_selftest.json")
        file.writeText(json.toString())
        return Summary(load, times[times.size / 2], times.last(), lines, file)
    }
}
