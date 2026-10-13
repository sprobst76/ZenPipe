/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen.classify

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.zen.ZenFeedProvider

/**
 * Classifies new feed candidates (title + channel only, spec E3: no extra network requests) in the
 * background, after a feed refresh and at app start. Every result is stored right away, so an
 * interrupted run loses nothing. Nothing happens without an installed model.
 */
class ZenClassifyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext
        if (!ZenClassifierModel.isReady(app)) {
            Log.d(ZenClassifier.TAG, "run: no usable model")
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val cache = ZenClassCache.get(app)
        cache.purgeAccessedBefore(now - ZenClassCache.RETENTION_MS)
        val candidates = ZenFeedProvider(app, FeedDatabaseManager(app)).classifyCandidates(now)
        cache.touch(candidates.map { it.first }, now)
        val todo = candidates.filter { (key, v) -> cache.needsWork(cache.get(key), v.level, ZenClassifierModel.VERSION) }
        if (todo.isEmpty()) {
            Log.d(ZenClassifier.TAG, "run: candidates ${candidates.size}, nothing to do")
            return Result.success()
        }
        val classifier = ZenClassifier.get(app)
        val t0 = System.nanoTime()
        var done = 0
        var computeMs = 0L
        outer@ for (batch in todo.chunked(BATCH)) {
            for ((key, v) in batch) {
                if (isStopped || !ZenClassifierModel.isReady(app)) break@outer
                val e = classifier.classifyBlocking(key, v) ?: break@outer
                done++
                computeMs += e.ms
            }
        }
        val wallMs = (System.nanoTime() - t0) / 1_000_000
        val avg = if (done > 0) computeMs / done else 0
        Log.d(ZenClassifier.TAG, "run: candidates ${candidates.size}, todo ${todo.size}, done $done, $wallMs ms, avg $avg ms/video")
        ZenClassifier.log(app, "class_run", "candidates=${candidates.size} todo=${todo.size} done=$done ms=$wallMs avgMs=$avg")
        return Result.success()
    }

    companion object {
        private const val BATCH = 50
        private const val NAME = "zen_classify"

        @JvmStatic
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<ZenClassifyWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req)
        }

        /** App start: only if a model is installed (one file check, WorkManager stays untouched otherwise). */
        @JvmStatic
        fun enqueueIfInstalled(context: Context) {
            try {
                val marker = File(ZenClassifierModel.baseDir(context), "${ZenModelInstaller.MODEL_DIR}/${ZenModelInstaller.MARKER}")
                if (ZenClassifierModel.isSupported() && marker.isFile) enqueue(context)
            } catch (e: Exception) {
                Log.w(ZenClassifier.TAG, "enqueue", e)
            }
        }
    }
}
