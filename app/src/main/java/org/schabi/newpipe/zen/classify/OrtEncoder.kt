/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer

/**
 * ONNX Runtime encoder (`encoder_int8.onnx`, input `input_ids`/`attention_mask` [1, S] int64,
 * output `embedding` [1, 384]). Telemetry is switched off before the session is created; the
 * telemetry provider is removed from the manifest as well (no telemetry HTTP client exists).
 */
class OrtEncoder(modelPath: String, val threads: Int) :
    Encoder,
    AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        try {
            env.setTelemetry(false)
        } catch (e: Exception) {
            // not available in this build of the runtime: nothing to switch off
        }
        session = env.createSession(
            modelPath,
            OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                setInterOpNumThreads(1)
            }
        )
    }

    override fun embed(ids: LongArray): FloatArray {
        val shape = longArrayOf(1, ids.size.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { input ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape).use { mask ->
                session.run(mapOf("input_ids" to input, "attention_mask" to mask)).use { res ->
                    @Suppress("UNCHECKED_CAST")
                    return (res[0].value as Array<FloatArray>)[0]
                }
            }
        }
    }

    override fun close() = session.close()
}
