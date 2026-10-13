package org.schabi.newpipe.zen

import android.content.Context
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.ZenIntentPrompt

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
        val view = state.view(reading.trustedMillis, allowCreate) {
            ZenWatchLater.purgeExpired(appContext, reading.trustedMillis)
            candidates()
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

    /**
     * Feed candidates for the content classifier (same eligibility as an edition), newest first,
     * as (cache key, title + channel). Only data the feed already has; no network.
     */
    fun classifyCandidates(nowMillis: Long): List<Pair<String, org.schabi.newpipe.zen.classify.VideoText>> = candidateRows()
        .filter { ZenFeedPlanner.isEligible(it.second, nowMillis) }
        .sortedByDescending { it.second.uploadMillis }
        .map { (serviceId, c) ->
            org.schabi.newpipe.zen.classify.ZenClassCache.keyOf(serviceId, c.url) to
                org.schabi.newpipe.zen.classify.VideoText(c.title, c.channelName)
        }

    private fun candidates(): List<ZenFeedPlanner.Candidate> = candidateRows().map { it.second }

    private fun candidateRows(): List<Pair<Int, ZenFeedPlanner.Candidate>> {
        val ranks = db.database().feedGroupDAO().getZenGroupRanks().associateBy { it.streamId }
        val praxis = ZenPraxis.activeKeys()
        val praxisStreams = ZenPraxis.activePlaylistStreamKeys()
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
                    } == true || ZenPraxisChannel.keyOf(s.serviceId, s.url) in praxisStreams
                )
            }
    }
}
