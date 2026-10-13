/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import kotlin.math.exp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One multi-label head: p = sigmoid(a * (x @ W + b) + c). W is stored as [dim][k] (row-major). */
class Head(
    val classes: List<String>,
    private val dim: Int,
    private val w: FloatArray,
    private val b: FloatArray,
    private val a: DoubleArray,
    private val c: DoubleArray
) {
    init {
        require(w.size == dim * classes.size && b.size == classes.size && a.size == classes.size && c.size == classes.size) {
            "inconsistent head (dim=$dim, k=${classes.size})"
        }
    }

    fun proba(x: FloatArray): Map<String, Double> {
        require(x.size == dim) { "embedding length ${x.size} != $dim" }
        val k = classes.size
        val out = LinkedHashMap<String, Double>(k * 2)
        for (j in 0 until k) {
            var z = b[j].toDouble()
            for (i in 0 until dim) z += x[i].toDouble() * w[i * k + j]
            out[classes[j]] = 1.0 / (1.0 + exp(-(a[j] * z + c[j])))
        }
        return out
    }
}

/** The three heads (theme 37, format 10, tone 3) from `heads.json`. */
class Heads(val theme: Head, val format: Head, val tone: Head) {
    companion object {
        fun fromJson(json: String): Heads {
            val heads = Json.parseToJsonElement(json).jsonObject["heads"]!!.jsonObject
            fun head(name: String): Head {
                val h = heads[name]!!.jsonObject
                return Head(
                    h["classes"]!!.jsonArray.map { it.jsonPrimitive.content },
                    h["dim"]!!.jsonPrimitive.int,
                    ZenBase64.floats(h["W"]!!.jsonPrimitive.content),
                    ZenBase64.floats(h["b"]!!.jsonPrimitive.content),
                    h["platt_a"]!!.jsonArray.map { it.jsonPrimitive.double }.toDoubleArray(),
                    h["platt_b"]!!.jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()
                )
            }
            return Heads(head("theme"), head("format"), head("tone"))
        }
    }
}
