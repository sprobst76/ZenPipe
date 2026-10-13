package org.schabi.newpipe.zen

import android.content.Context
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.ZenIntentPrompt
import org.schabi.newpipe.zen.classify.VideoText
import org.schabi.newpipe.zen.classify.ZenClassCache
import org.schabi.newpipe.zen.classify.ZenClassifier
import org.schabi.newpipe.zen.classify.ZenClassifierModel

/** Bridges the NewPipe feed database and the Zen edition logic ([ZenFeedState]). */
class ZenFeedProvider(context: Context, private val db: FeedDatabaseManager) {
    private val state = ZenFeedState(Zen.store(context), Zen.log(context))
    private val appContext = context.applicationContext
    private val clock = Zen.clock(context)

    /**
     * Closing card: next edition time and the videos waiting; [preparing] = (done, total) while a
     * due edition is being prepared by the content recognition ([ZenEditionPrep]).
     */
    data class End(val nextEditionMillis: Long, val leftover: Int, val dailyCapReached: Boolean, val preparing: Pair<Int, Int>? = null)

    data class Result(
        val items: List<StreamWithState>,
        val picks: Map<String, ZenFeedPlanner.Pick>,
        val end: End?
    )

    /** Blocking, call from a background thread. */
    fun compute(groupId: Long): Result {
        val reading = clock.now()
        val now = reading.trustedMillis
        // the edition is only built after the intention prompt was confirmed ("Never mind" costs nothing)
        var allowCreate = ZenIntentPrompt.isConfirmed()
        val rules = contentRules()
        var createdAt = now
        var preparing: Pair<Int, Int>? = null
        // with the content recognition, a due edition is built only after its candidates were
        // classified (or the limit passed); until then the running edition stays as it is
        if (allowCreate && rules.active && state.isDue(now)) {
            when (val d = prep.decide(now) { missingJobs(now) }) {
                is ZenEditionPrep.Decision.Build -> {
                    d.createdAt?.let { createdAt = it }
                    if (d.createdAt != null) Zen.log(appContext).add("class_feed_prep", prep.lastRun)
                }

                is ZenEditionPrep.Decision.Wait -> {
                    allowCreate = false
                    preparing = d.done to d.total
                }
            }
        }
        val view = state.view(now, allowCreate, rules, createdAt) {
            ZenWatchLater.purgeExpired(appContext, now)
            if (rules.active) classified(candidates(), now) else candidates()
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
        val end = when {
            preparing != null -> End(view.nextEditionMillis ?: now, view.edition?.leftover ?: 0, false, preparing)
            view.edition == null || view.nextEditionMillis == null -> null
            else -> End(view.nextEditionMillis, view.edition.leftover, view.dailyCapReached)
        }
        return Result(picks.map { available.getValue(it.url) }, picks.associateBy { it.url }, end)
    }

    private val prep get() = ZenEditionPrep.shared

    /** Eligible candidates without a classification of the current model, as preparation jobs. */
    private fun missingJobs(now: Long): List<ZenEditionPrep.Job> {
        val cache = ZenClassCache.get(appContext)
        val classifier = ZenClassifier.get(appContext)
        return candidateRows().filter { (serviceId, c) ->
            ZenFeedPlanner.isEligible(c, now) &&
                cache.get(ZenClassCache.keyOf(serviceId, c.url))?.model != ZenClassifierModel.VERSION
        }.map { (serviceId, c) ->
            val key = ZenClassCache.keyOf(serviceId, c.url)
            ZenEditionPrep.Job(key, ZenFeedPlanner.score(c, now)) { timeout ->
                classifier.classifyWithin(key, VideoText(c.title, c.channelName), timeout) != null
            }
        }
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
     * Adds the cached classification to the candidates (spec 2.4). Missing entries were classified
     * before (see [ZenEditionPrep]); what is still missing stays "not classified" (weight 1.0).
     */
    private fun classified(list: List<ZenFeedPlanner.Candidate>, now: Long): List<ZenFeedPlanner.Candidate> {
        val cache = ZenClassCache.get(appContext)
        return list.map { c ->
            if (!ZenFeedPlanner.isEligible(c, now)) return@map c
            val e = cache.get(ZenClassCache.keyOf(serviceIds[c.url] ?: 0, c.url))
            if (e == null || e.model != ZenClassifierModel.VERSION) c else c.copy(zenCats = e.cats, tone = e.tone, classLevel = e.level)
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
