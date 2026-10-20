/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.zen.Zen
import org.schabi.newpipe.zen.ZenPlayback

/**
 * Runs the classifier: one background thread (all ONNX Runtime calls happen there), the model is
 * loaded lazily on first use and closed after [IDLE_CLOSE_MS] without work. ONNX Runtime uses 4
 * threads, 2 while ZenPipe plays something (so video/audio do not stutter); a change reloads only
 * the encoder. Errors (broken files, ORT failure, out of memory) close the session and mark the
 * model broken (`class_error`); callers then behave as without a model.
 *
 * Classification never happens in the moment a feed edition is selected: the selection only reads
 * [ZenClassCache] (filled by [ZenClassifyWorker] and [onStreamInfo]).
 */
class ZenClassifier private constructor(private val app: Context) {
    private val exec = ScheduledThreadPoolExecutor(1) { r ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                r.run()
            },
            "ZenClassifier"
        ).apply { isDaemon = true }
    }

    private class Parts(val tokenizer: UnigramTokenizer, val vocabMap: IntArray, val heads: Heads, val sha: String)

    // only touched on the executor thread
    private var parts: Parts? = null
    private var encoder: OrtEncoder? = null
    private var idle: ScheduledFuture<*>? = null

    /** Load times of the last load (ms), for the self-test. */
    @Volatile var lastLoadMs: LongArray = longArrayOf(0, 0, 0)
        private set

    private val cache get() = ZenClassCache.get(app)

    /** Classifies [v] now (blocking, on the classifier thread) and stores it in the cache. */
    fun classifyBlocking(key: String, v: VideoText): ClassEntry? = exec.submit(Callable { classifyNow(key, v) }).get()

    /**
     * Like [classifyBlocking], but waits at most [timeoutMs] (the work itself continues and lands
     * in the cache). Null on timeout, error or without a model.
     */
    fun classifyWithin(key: String, v: VideoText, timeoutMs: Long): ClassEntry? = try {
        exec.submit(Callable { classifyNow(key, v) }).get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: java.util.concurrent.TimeoutException) {
        null
    } catch (e: java.util.concurrent.ExecutionException) {
        null
    }

    /** Classifies [v] later in the background unless the cache already has it at this level. */
    fun submit(key: String, v: VideoText, delayMs: Long = 0) {
        exec.schedule({ classifyNow(key, v) }, delayMs, TimeUnit.MILLISECONDS)
    }

    /**
     * For the practice tab (D61): classifies [v] now (blocking, waits at most [timeoutMs]) when the
     * cache has no entry or one without practice theme values. Title + channel only, no network.
     */
    fun classifyForPraxis(key: String, v: VideoText, timeoutMs: Long): ClassEntry? = try {
        exec.submit(Callable { classifyNow(key, v, wantPraxis = true) }).get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: java.util.concurrent.TimeoutException) {
        null
    } catch (e: java.util.concurrent.ExecutionException) {
        null
    }

    /** Closes the session (e.g. before the model files are replaced or deleted). */
    fun closeNow() {
        exec.execute { close() }
    }

    private fun threadsNow() = if (ZenPlayback.isPlaying()) THREADS_PLAYING else THREADS_IDLE

    private fun pipeline(): ZenPipeline? {
        if (!ZenClassifierModel.isReady(app)) return null
        val dir = ZenClassifierModel.modelDir(app) ?: return null
        val sha = ZenClassifierModel.installer(app).installed()?.second ?: return null
        var p = parts
        if (p == null || p.sha != sha) {
            close()
            val t0 = System.nanoTime()
            val tok = UnigramTokenizer.load(File(dir, ZenPipeline.TOKENIZER_FILE).readBytes())
            val t1 = System.nanoTime()
            val vocab = ZenPipeline.vocabMapFromJson(File(dir, ZenPipeline.VOCAB_MAP_FILE).readText())
            val heads = Heads.fromJson(File(dir, ZenPipeline.HEADS_FILE).readText())
            val t2 = System.nanoTime()
            p = Parts(tok, vocab, heads, sha)
            parts = p
            lastLoadMs = longArrayOf((t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, lastLoadMs[2])
        }
        val threads = threadsNow()
        var enc = encoder
        if (enc == null || enc.threads != threads) {
            enc?.close()
            val t0 = System.nanoTime()
            enc = OrtEncoder(File(dir, ZenModelInstaller.ENCODER_FILE).path, threads)
            encoder = enc
            lastLoadMs = longArrayOf(lastLoadMs[0], lastLoadMs[1], (System.nanoTime() - t0) / 1_000_000)
            Log.d(TAG, "loaded: tokenizer ${lastLoadMs[0]} ms, map+heads ${lastLoadMs[1]} ms, encoder ${lastLoadMs[2]} ms, threads $threads")
        }
        return ZenPipeline(p.tokenizer, p.vocabMap, enc, p.heads)
    }

    private fun classifyNow(key: String, v: VideoText, wantPraxis: Boolean = false): ClassEntry? {
        idle?.cancel(false)
        try {
            val old = cache.get(key)
            if (!cache.needsWork(old, v.level, ZenClassifierModel.VERSION, wantPraxis)) return old
            val pipe = pipeline() ?: return null
            val t0 = System.nanoTime()
            val a = pipe.analyze(v)
            val ms = (System.nanoTime() - t0) / 1_000_000
            val now = System.currentTimeMillis()
            val e = ClassEntry(
                key = key,
                title = v.title.orEmpty(),
                channel = v.channel.orEmpty(),
                level = v.level,
                model = ZenClassifierModel.VERSION,
                cats = a.categories,
                tone = a.tone,
                top = a.top(),
                weight = a.weight,
                tokens = a.tokens,
                ms = ms,
                createdMillis = now,
                accessedMillis = now,
                previousT = ZenClassCache.previousTFor(old, v.level, ZenClassifierModel.VERSION),
                praxisThemes = praxisThemesOf(a)
            )
            cache.put(e)
            // the details also tell the language (feed language filter)
            if (v.langSignals != null) {
                org.schabi.newpipe.zen.ZenFeedLanguages.recordDetails(app, key, v.channelUrl ?: v.channel, v.title, v.channel, v.langSignals)
            }
            return e
        } catch (t: Throwable) {
            close()
            Log.e(TAG, "classification failed", t)
            ZenClassifierModel.markBroken(app, "${t.javaClass.simpleName}: ${t.message}")
            return null
        } finally {
            idle = exec.schedule({ close() }, IDLE_CLOSE_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun close() {
        try {
            encoder?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close", e)
        }
        if (encoder != null || parts != null) Log.d(TAG, "session closed")
        encoder = null
        parts = null
    }

    /** One self-test line: invented text, result and the embedding (for comparing with the reference). */
    data class SelfTestResult(val text: String, val tokens: Int, val ms: Long, val cats: Map<String, Double>, val tone: String?, val embedding: FloatArray)

    /** Debug self-test: fresh load, then each text twice (first = warm-up), times of the second run. */
    fun selfTest(texts: List<Pair<String, String>>): List<SelfTestResult> = exec.submit(
        Callable {
            idle?.cancel(false)
            try {
                close()
                val pipe = pipeline() ?: return@Callable emptyList<SelfTestResult>()
                texts.forEach { pipe.analyzeText(it.first, it.second) }
                texts.map { (text, title) ->
                    val t0 = System.nanoTime()
                    val a = pipe.analyzeText(text, title)
                    SelfTestResult(text, a.tokens, (System.nanoTime() - t0) / 1_000_000, a.categories, a.tone, a.embedding)
                }
            } catch (t: Throwable) {
                close()
                Log.e(TAG, "self-test failed", t)
                ZenClassifierModel.markBroken(app, "${t.javaClass.simpleName}: ${t.message}")
                emptyList()
            } finally {
                idle = exec.schedule({ close() }, IDLE_CLOSE_MS, TimeUnit.MILLISECONDS)
            }
        }
    ).get()

    companion object {
        const val TAG = "ZenClassify"
        const val THREADS_IDLE = 4
        const val THREADS_PLAYING = 2
        const val IDLE_CLOSE_MS = 60_000L

        /** Delay before classifying an opened video, so the model never loads during player start. */
        private const val OPEN_DELAY_MS = 5_000L

        @Volatile
        private var instance: ZenClassifier? = null

        @JvmStatic
        fun get(context: Context): ZenClassifier = instance ?: synchronized(this) {
            instance ?: ZenClassifier(context.applicationContext).also { instance = it }
        }

        /** Text of an opened/played video with its details (level "D"). */
        @JvmStatic
        fun textOf(info: StreamInfo): VideoText = VideoText(
            title = info.name,
            channel = info.uploaderName,
            description = info.description?.let { TextBuilder.plainDescription(it.content(), it.type() == Description.Type.HTML) },
            categoryId = TextBuilder.categoryIdOf(info.category),
            tags = info.tags?.takeIf { it.isNotEmpty() },
            langSignals = languageSignalsOf(info),
            channelUrl = info.uploaderUrl
        )

        /**
         * Language signals of the details: the service's own language field (not filled by
         * YouTube), the ORIGINAL audio track of a multi-language video and the single automatic
         * (speech recognition) caption track. How they count: [org.schabi.newpipe.zen.ZenLangDetect.combine].
         * Null if none of these is known.
         */
        @JvmStatic
        fun languageSignalsOf(info: StreamInfo): org.schabi.newpipe.zen.ZenLangSignals? = try {
            org.schabi.newpipe.zen.ZenLangSignals.of(
                info.languageInfo?.toLanguageTag(),
                info.audioStreams?.firstOrNull { it.audioTrackType == AudioTrackType.ORIGINAL && it.audioLocale != null }
                    ?.audioLocale?.toLanguageTag(),
                info.subtitles?.filter { it.isAutoGenerated }?.map { it.languageTag }?.distinct()?.singleOrNull()
            )
        } catch (e: Exception) {
            null
        }

        /**
         * A video was opened or is played: its details are known anyway (no extra request), so
         * classify it with the full text in the background (cache level "D").
         */
        @JvmStatic
        fun onStreamInfo(context: Context, info: StreamInfo) {
            try {
                // the language works without the model (feed language filter)
                languageSignalsOf(info)?.let { hint ->
                    get(context).exec.execute {
                        org.schabi.newpipe.zen.ZenFeedLanguages.recordDetails(
                            context.applicationContext,
                            ZenClassCache.keyOf(info.serviceId, info.url),
                            info.uploaderUrl ?: info.uploaderName,
                            info.name,
                            info.uploaderName,
                            hint
                        )
                    }
                }
                if (!ZenClassifierModel.isReady(context)) return
                get(context).submit(ZenClassCache.keyOf(info.serviceId, info.url), textOf(info), OPEN_DELAY_MS)
            } catch (e: Exception) {
                Log.w(TAG, "onStreamInfo", e)
            }
        }

        /**
         * Review list entry: the "why" summary (categories, or the strongest theme without one;
         * tone; level) with the feed weight, then the raw top themes/formats.
         */
        fun describe(context: Context, e: ClassEntry): String {
            val w = "w ${"%.1f".format(java.util.Locale.ROOT, if (e.cats.isEmpty()) 1.0 else e.weight)}"
            return "${ZenClassWhy.summary(context, e.cats, e.tone, e.level, e.top)} · $w\n${ZenClassWhy.topLine(context, e.top)}"
        }

        /** The practice theme values of an analysis (only the five heads of [org.schabi.newpipe.zen.ZenPraxisTheme]). */
        @JvmStatic
        fun praxisThemesOf(a: Analysis): Map<String, Double> {
            val keys = org.schabi.newpipe.zen.ZenPraxisTheme.entries.map { it.modelKey }.toSet()
            return a.themes.filterKeys { it in keys }
        }

        internal fun log(context: Context, type: String, detail: String) = Zen.log(context).add(type, detail)
    }
}
