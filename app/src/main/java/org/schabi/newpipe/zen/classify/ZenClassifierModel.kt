/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.zen.Zen

/**
 * The downloadable classifier model: where it lives, whether it is usable, and the opt-in download.
 *
 * The model is never part of the APK or the repository (spec E1): it is a fixed GitHub release
 * asset, fetched only after an explicit tap, checked against [DOWNLOAD_SHA256] and stored in
 * `noBackupFilesDir` (no auto-backup, not in NewPipe's export). This is the only network request of
 * the classifier; it goes to GitHub, never to YouTube or NewPipe.
 */
object ZenClassifierModel {
    /** Version of the bundle the code expects (heads, tokenizer and encoder belong together). */
    const val VERSION = "e5s-1"

    // Published as release asset of the tag `model-e5s-1` (built with zen-docs/tools/make_classifier_bundle.py);
    // debug builds can use a local test source (see debugSource).
    const val DOWNLOAD_URL = "https://github.com/sprobst76/ZenPipe/releases/download/model-e5s-1/zen-classifier-e5s-1.zip"
    const val DOWNLOAD_SHA256 = "f9d0bbceeb59cad23752bfbec1535ff2f80d74e50e7eb66eb3367c81563dd379"
    const val DOWNLOAD_BYTES = 35_432_191L // display only; the e5s-1 bundle is 35.4 MB

    /** zen_state keys (debug builds only): test download source instead of the GitHub asset. */
    const val KEY_DEBUG_URL = "cls_debug_url"
    const val KEY_DEBUG_SHA = "cls_debug_sha"

    /** zen_state key: the loaded model failed (reason); cleared by a new install or delete. */
    const val KEY_BROKEN = "cls_broken"

    enum class Status { UNSUPPORTED, NOT_INSTALLED, DOWNLOADING, INSTALLED, BROKEN }

    /** ABIs the APK ships native code for (see `abiFilters` in app/build.gradle.kts). */
    private val PACKAGED_ABIS = setOf("arm64-v8a", "x86_64")

    /** ONNX Runtime needs API 24 and one of the packaged ABIs. */
    @JvmStatic
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        Build.SUPPORTED_ABIS.any { it in PACKAGED_ABIS }

    fun baseDir(context: Context) = File(context.noBackupFilesDir, "zen_classifier")

    fun installer(context: Context) = ZenModelInstaller(baseDir(context))

    /** Directory with the model files, or null if no complete model of [VERSION] is installed. */
    fun modelDir(context: Context): File? {
        val inst = installer(context)
        val v = inst.installed() ?: return null
        return if (v.first == VERSION) inst.modelDir else null
    }

    @JvmStatic
    fun status(context: Context): Status = when {
        !isSupported() -> Status.UNSUPPORTED
        download.running -> Status.DOWNLOADING
        modelDir(context) == null -> Status.NOT_INSTALLED
        !Zen.store(context).getString(KEY_BROKEN, null).isNullOrEmpty() -> Status.BROKEN
        else -> Status.INSTALLED
    }

    /** Usable right now (installed, not broken, supported). */
    @JvmStatic
    fun isReady(context: Context) = status(context) == Status.INSTALLED

    fun markBroken(context: Context, reason: String) {
        Zen.store(context).put(KEY_BROKEN to reason.take(200).ifEmpty { "error" })
        Zen.log(context).add("class_error", "model=$VERSION reason=${reason.take(200)}")
    }

    /** URL and SHA-256 to download from, or null while the asset is not published. */
    fun source(context: Context): Pair<String, String>? {
        debugSource(context)?.let { return it }
        return if (DOWNLOAD_SHA256.length == 64) DOWNLOAD_URL to DOWNLOAD_SHA256 else null
    }

    /** Debug builds only: a local test source (e.g. `adb reverse` + a local HTTP server). */
    private fun debugSource(context: Context): Pair<String, String>? {
        if (!BuildConfig.DEBUG) return null
        val s = Zen.store(context)
        val url = s.getString(KEY_DEBUG_URL, null) ?: return null
        val sha = s.getString(KEY_DEBUG_SHA, null) ?: return null
        return if (sha.length == 64) url to sha else null
    }

    /** Deletes the model. Allowed only while no classifier setting depends on it (spec 4.7). */
    fun delete(context: Context) {
        ZenClassifier.get(context).closeNow()
        installer(context).delete()
        ZenClassCache.get(context).clear()
        Zen.store(context).put(KEY_BROKEN to null)
        Zen.log(context).add("class_model_deleted", "model=$VERSION")
    }

    val download = Download()

    /** The one running download of this process (survives leaving the settings screen). */
    class Download {
        @Volatile var running = false
            private set

        @Volatile var bytes = 0L
            private set

        @Volatile var total = DOWNLOAD_BYTES
            private set

        /** Error of the last attempt (null = none or success). */
        @Volatile var lastError: String? = null
            private set

        @Volatile private var cancelled = false

        @Volatile private var call: Call? = null

        private val main = Handler(Looper.getMainLooper())

        fun start(context: Context, onDone: () -> Unit) {
            val app = context.applicationContext
            val src = source(app) ?: return
            synchronized(this) {
                if (running) return
                running = true
                cancelled = false
                bytes = 0L
                lastError = null
            }
            Thread({ run(app, src.first, src.second, onDone) }, "ZenModelDownload").start()
        }

        fun cancel() {
            cancelled = true
            call?.cancel()
        }

        private fun run(app: Context, url: String, sha: String, onDone: () -> Unit) {
            val log = Zen.log(app)
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val c = client.newCall(Request.Builder().url(url).build())
                call = c
                c.execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body
                    body.contentLength().takeIf { it > 0 }?.let { total = it }
                    ZenClassifier.get(app).closeNow()
                    installer(app).install(
                        body.byteStream(),
                        sha,
                        VERSION,
                        progress = { bytes = it },
                        cancelled = { cancelled }
                    )
                }
                Zen.store(app).put(KEY_BROKEN to null)
                log.add("class_model_installed", "model=$VERSION sha=${sha.take(12)} bytes=$bytes")
                ZenClassifyWorker.enqueue(app)
            } catch (e: Exception) {
                lastError = if (cancelled) null else (e.message ?: e.javaClass.simpleName)
                if (!cancelled) log.add("class_model_failed", "model=$VERSION reason=${lastError?.take(160)}")
            } finally {
                call = null
                running = false
                main.post(onDone)
            }
        }
    }
}
