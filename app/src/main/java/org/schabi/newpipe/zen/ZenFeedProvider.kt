package org.schabi.newpipe.zen

import android.content.Context
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.ZenIntentPrompt
import org.schabi.newpipe.zen.classify.ClassEntry
import org.schabi.newpipe.zen.classify.VideoText
import org.schabi.newpipe.zen.classify.ZenClassCache
import org.schabi.newpipe.zen.classify.ZenClassifier
import org.schabi.newpipe.zen.classify.ZenClassifierModel

/** Bridges the NewPipe feed database and the Zen edition logic ([ZenFeedState]). */
class ZenFeedProvider(context: Context, private val db: FeedDatabaseManager) {
    private val state = ZenFeedState(Zen.store(context), Zen.log(context))
    private val appContext = context.applicationContext
    private val clock = Zen.clock(context)

    data class End(val nextEditionMillis: Long, val leftover: Int, val dailyCapReached: Boolean)

    data class Result(
        val items: List<StreamWithState>,
        val picks: Map<String, ZenFeedPlanner.Pick>,
        val end: End?
    )

    /** Blocking, call from a background thread. */
    fun compute(groupId: Long): Result {
        val reading = clock.now()
        // the edition is only built after the intention prompt was confirmed ("Never mind" costs nothing)
        val allowCreate = ZenIntentPrompt.isConfirmed()
        val rules = contentRules()
        val view = state.view(reading.trustedMillis, allowCreate, rules) {
            ZenWatchLater.purgeExpired(appContext, reading.trustedMillis)
            if (rules.active) classified(candidates(), reading.trustedMillis) else candidates()
        }

        // not finished streams (partially watched ones stay visible with their progress)
        val available = db.getStreams(FeedGroupEntity.GROUP_ALL_ID, false, true, false)
            .blockingGet(arrayListOf()).associateBy { it.stream.url }
        var picks = view.open.filter { it.url in available }
        if (groupId != FeedGroupEntity.GROUP_ALL_ID) {
            val inGroup = db.getStreams(groupId, false, true, false)
                .blockingGet(arrayListOf()).map { it.stream.url }.toHashSet()
            picks = picks.filter { it.url in inGroup }
        }
        val end = if (view.edition == null || view.nextEditionMillis == null) {
            null
        } else {
            End(view.nextEditionMillis, view.edition.leftover, view.dailyCapReached)
        }
        return Result(picks.map { available.getValue(it.url) }, picks.associateBy { it.url }, end)
    }

    fun markHandled(url: String, reason: String) = state.markHandled(url, reason)

    /** Content rules for the next edition: switches on and a working model (otherwise as without). */
    private fun contentRules(): ZenFeedPlanner.ContentRules {
        val weighting = ZenSettings.classFeedWeighting()
        val heavy = ZenSettings.classHeavyLimit()
        if (!weighting && !heavy) return ZenFeedPlanner.ContentRules.OFF
        if (!ZenClassifierModel.isReady(appContext)) return ZenFeedPlanner.ContentRules.OFF
        return ZenFeedPlanner.ContentRules(weighting, heavy)
    }

    /**
     * Adds the cached classification to the candidates (spec 2.4). Eligible candidates without a
     * cache entry are classified now for at most [RECLASSIFY_BUDGET_MS] (best base score first);
     * the rest stays "not classified" (weight 1.0). The edition never waits longer.
     */
    private fun classified(list: List<ZenFeedPlanner.Candidate>, now: Long): List<ZenFeedPlanner.Candidate> {
        val cache = ZenClassCache.get(appContext)
        val keys = HashMap<String, String>()
        list.forEach { keys[it.url] = ZenClassCache.keyOf(serviceIds[it.url] ?: 0, it.url) }
        val found = HashMap<String, ClassEntry>()
        val missing = ArrayList<ZenFeedPlanner.Candidate>()
        for (c in list) {
            if (!ZenFeedPlanner.isEligible(c, now)) continue
            val e = cache.get(keys.getValue(c.url))
            if (e != null && e.model == ZenClassifierModel.VERSION) found[c.url] = e else missing.add(c)
        }
        if (missing.isNotEmpty()) {
            val t0 = System.nanoTime()
            val deadline = t0 + RECLASSIFY_BUDGET_MS * 1_000_000
            val classifier = ZenClassifier.get(appContext)
            var done = 0
            for (c in missing.sortedByDescending { ZenFeedPlanner.score(it, now) }) {
                val left = (deadline - System.nanoTime()) / 1_000_000
                if (left <= 0) break
                val e = classifier.classifyWithin(keys.getValue(c.url), VideoText(c.title, c.channelName), left) ?: break
                found[c.url] = e
                done++
            }
            val ms = (System.nanoTime() - t0) / 1_000_000
            Zen.log(appContext).add("class_feed_late", "missing=${missing.size} done=$done ms=$ms")
        }
        return list.map { c ->
            val e = found[c.url] ?: return@map c
            c.copy(zenCats = e.cats, tone = e.tone, classLevel = e.level)
        }
    }

    /**
     * Feed candidates for the content classifier (same eligibility as an edition), newest first,
     * as (cache key, title + channel). Only data the feed already has; no network.
     */
    fun classifyCandidates(nowMillis: Long): List<Pair<String, VideoText>> = candidateRows()
        .filter { ZenFeedPlanner.isEligible(it.second, nowMillis) }
        .sortedByDescending { it.second.uploadMillis }
        .map { (serviceId, c) -> ZenClassCache.keyOf(serviceId, c.url) to VideoText(c.title, c.channelName) }

    // service id per video URL of the last candidate query (cache keys need it)
    private val serviceIds = HashMap<String, Int>()

    private fun candidates(): List<ZenFeedPlanner.Candidate> = candidateRows().map {
        serviceIds[it.second.url] = it.first
        it.second
    }

    companion object {
        /** Spec 2.4: at most this long classifying missing candidates when an edition is built. */
        const val RECLASSIFY_BUDGET_MS = 3_000L
    }

    private fun candidateRows(): List<Pair<Int, ZenFeedPlanner.Candidate>> {
        val ranks = db.database().feedGroupDAO().getZenGroupRanks().associateBy { it.streamId }
        val praxis = ZenPraxis.activeKeys()
        val praxisStreams = ZenPraxis.activePlaylistStreamKeys()
        val exempt = ZenClassExemptions(ZenSettings.lock()?.exemptions() ?: emptyList())
        return db.getStreams(FeedGroupEntity.GROUP_ALL_ID, false, false, false)
            .blockingGet(arrayListOf()).mapNotNull {
                val s = it.stream
                val upload = s.uploadDate?.toInstant()?.toEpochMilli() ?: return@mapNotNull null
                val rank = ranks[s.uid]
                s.serviceId to ZenFeedPlanner.Candidate(
                    url = s.url,
                    channel = s.uploaderUrl ?: s.uploader,
                    title = s.title,
                    channelName = s.uploader,
                    uploadMillis = upload,
                    durationSec = s.duration,
                    isLive = StreamTypeUtil.isLiveStream(s.streamType),
                    groupRank = rank?.sortOrder?.toInt()?.coerceAtLeast(0),
                    groupName = rank?.groupName,
                    praxis = praxis.isNotEmpty() && s.uploaderUrl?.let {
                        ZenPraxisChannel.keyOf(s.serviceId, it) in praxis
                    } == true || ZenPraxisChannel.keyOf(s.serviceId, s.url) in praxisStreams,
                    classExempt = !exempt.isEmpty() && exempt.matches(s.serviceId, s.uploaderUrl, s.title)
                )
            }
    }
}
