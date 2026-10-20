package org.schabi.newpipe.zen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.function.Supplier
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.fragments.list.BaseListFragment
import org.schabi.newpipe.util.ExtractorHelper

/**
 * The "Practice" tab: the newest videos of the user's ACTIVE practice channels (yoga, breathwork,
 * meditation, sleep aids), at most [ZenPraxis.MAX_PER_CHANNEL] per channel, newest first, then each
 * ACTIVE practice playlist as an entry (open it to play it as a chain) followed by its first
 * [ZenPraxis.MAX_PER_CHANNEL] videos in playlist order; no paging and no edition budget. Loaded from the network like a channel page (the channels do not
 * have to be subscribed). Playback rules stay the same (no autoplay, no related videos, intention
 * prompt), only the mandatory pause and the feed limits do not apply to these channels.
 */
class ZenPraxisFragment : BaseListFragment<ZenPraxisFragment.Loaded, Void>() {
    data class Loaded(val items: List<InfoItem>, val channels: Int, val failed: Int)

    data class ChannelItems(val items: List<StreamInfoItem>, val ok: Boolean, val head: InfoItem? = null)

    private var disposable: Disposable? = null
    private var loadedSignature: String? = null
    private var header: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUseDefaultStateSaving(false)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = inflater.inflate(R.layout.fragment_kiosk, container, false)

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        // The kiosk layout's empty state is made for one short line (24 sp, no side margin); the
        // practice hint is a sentence, so it gets the size and margins of the other empty pages.
        val d = resources.displayMetrics.density
        rootView.findViewById<View>(R.id.empty_state_view)?.let { box ->
            box.layoutParams = box.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
        }
        rootView.findViewById<TextView>(R.id.empty_state_message)?.apply {
            textSize = EMPTY_TEXT_SP
            gravity = android.view.Gravity.CENTER
            val side = (EMPTY_SIDE_DP * d).toInt()
            setPadding(side, paddingTop, side, paddingBottom)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        activity.supportActionBar?.setDisplayShowTitleEnabled(true)
        activity.supportActionBar?.setTitle(R.string.zen_praxis_title)
        activity.supportActionBar?.subtitle = null
    }

    override fun onResume() {
        super.onResume()
        updateHeader()
        // a channel was marked/removed (or became active) since the last load
        if (loadedSignature != null && loadedSignature != signature() && !isLoading.get()) {
            startLoading(true)
        }
    }

    override fun onDestroy() {
        disposable?.dispose()
        super.onDestroy()
    }

    override fun getListHeaderSupplier(): Supplier<View> = Supplier {
        TextView(requireContext()).apply {
            val p = (12 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p / 2)
            header = this
            updateHeader()
        }
    }

    private fun signature(): String = ZenPraxis.activeKeys().sorted().joinToString("|")

    private fun updateHeader() {
        val h = header ?: return
        val ctx = context ?: return
        val minutes = (ZenPraxis.todayMs(ctx) / 60_000L).toInt()
        val pending = Zen.settingsLock(ctx).pendingPraxis().size
        val waiting = ZenPraxis.waitingPlaylistItems()
        h.text = buildString {
            append(ctx.getString(R.string.zen_praxis_today, minutes))
            if (pending > 0) append('\n').append(ctx.getString(R.string.zen_praxis_pending_count, pending))
            // D63: videos that are new in an active practice playlist count after 24 h
            if (waiting != null) {
                append('\n').append(
                    ctx.resources.getQuantityString(
                        R.plurals.zen_praxis_waiting_items,
                        waiting.count,
                        waiting.count,
                        ZenPraxisUi.fmtTime(waiting.nextReadyMs)
                    )
                )
            }
        }
    }

    override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        disposable?.dispose()
        val lock = Zen.settingsLock(requireContext())
        val all = lock.praxisChannels()
        loadedSignature = all.map { it.key }.sorted().joinToString("|")
        ZenPraxis.playlistItems(requireContext()).prune((all + lock.pendingPraxis()).map { it.key }.toHashSet())
        val ctx = requireContext().applicationContext
        // pending playlists are loaded only to remember their content (frozen base, D63), not shown
        val pendingLists = lock.pendingPraxis().filter { it.playlist }
        disposable = Observable.fromIterable(all + pendingLists)
            .concatMapSingle { ch ->
                when {
                    ch in pendingLists -> playlistOf(ctx, ch, forceLoad).map { ChannelItems(emptyList(), true, HIDDEN) }
                    ch.playlist -> playlistOf(ctx, ch, forceLoad)
                    else -> newestOf(ch, forceLoad)
                }
            }
            .toList()
            .map { loaded ->
                val perEntry = loaded.filter { it.head !== HIDDEN }
                val channelItems = perEntry.filter { it.head == null }.flatMap { it.items }
                    .sortedByDescending { it.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE }
                val playlistItems = perEntry.filter { it.head != null }.flatMap { listOf(it.head!!) + it.items }
                Loaded(channelItems + playlistItems, all.size, perEntry.count { !it.ok })
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { handleResult(it) },
                { showError(ErrorInfo(it, UserAction.REQUESTED_CHANNEL, "Practice tab")) }
            )
    }

    /** Newest videos of one channel (videos tab); ok = false if the channel could not be loaded. */
    private fun newestOf(ch: ZenPraxisChannel, forceLoad: Boolean): Single<ChannelItems> = ExtractorHelper.getChannelInfo(ch.serviceId, ch.url, forceLoad)
        .flatMap { info ->
            val tab = info.tabs.firstOrNull { it.contentFilters.firstOrNull() == ChannelTabs.VIDEOS }
            if (tab == null) {
                Single.just(ChannelItems(emptyList(), true))
            } else {
                ExtractorHelper.getChannelTab(ch.serviceId, tab, forceLoad).map { tabInfo ->
                    val items = tabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                        .filter {
                            !it.isShortFormContent && it.streamType != StreamType.LIVE_STREAM &&
                                it.streamType != StreamType.AUDIO_LIVE_STREAM
                        }
                        .take(ZenPraxis.MAX_PER_CHANNEL)
                    ChannelItems(items, true)
                }
            }
        }
        .onErrorReturnItem(ChannelItems(emptyList(), false))

    /**
     * First page of a practice playlist: all its videos are registered as practice
     * ([ZenPraxis.registerPlaylistItems]), the entry itself and the first videos are shown.
     */
    private fun playlistOf(ctx: android.content.Context, pl: ZenPraxisChannel, forceLoad: Boolean): Single<ChannelItems> = ExtractorHelper.getPlaylistInfo(pl.serviceId, pl.url, forceLoad)
        .map { info ->
            ZenPraxis.registerPlaylistItems(ctx, pl.serviceId, pl.url, info.relatedItems, false)
            val head = PlaylistInfoItem(info.serviceId, info.url, info.name).apply {
                thumbnails = info.thumbnails
                uploaderName = info.uploaderName
                streamCount = info.streamCount
            }
            // new videos that do not count yet (D63) are not offered as practice
            val waiting = ZenPraxis.waitingPlaylistItems()?.streamKeys.orEmpty()
            val items = info.relatedItems.filterIsInstance<StreamInfoItem>()
                .filter {
                    !it.isShortFormContent && it.streamType != StreamType.LIVE_STREAM &&
                        it.streamType != StreamType.AUDIO_LIVE_STREAM &&
                        ZenPraxisChannel.keyOf(it.serviceId, it.url) !in waiting
                }
                .take(ZenPraxis.MAX_PER_CHANNEL)
            ChannelItems(items, true, head)
        }
        .onErrorReturnItem(ChannelItems(emptyList(), false, null))

    override fun handleResult(result: Loaded) {
        super.handleResult(result)
        infoListAdapter.clearStreamItemList()
        updateHeader()
        if (result.items.isEmpty()) {
            setEmptyStateMessage(
                when {
                    result.channels > 0 -> R.string.empty_view_no_videos
                    Zen.settingsLock(requireContext()).pendingPraxis().isNotEmpty() -> R.string.zen_praxis_empty_pending
                    else -> R.string.zen_praxis_empty
                }
            )
            showEmptyState()
            return
        }
        infoListAdapter.addInfoItemList(result.items)
        showListFooter(false)
        if (result.failed > 0) {
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.zen_praxis_failed, result.failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun loadMoreItems() {
        // finite list: no paging
    }

    override fun hasMoreItems(): Boolean = false

    private companion object {
        /** Marker head of a pending playlist that was only loaded to register its content. */
        val HIDDEN: InfoItem = PlaylistInfoItem(-1, "", "")

        /** Empty-state text like the other empty pages (`list_empty_view`: default size, 16 dp sides). */
        const val EMPTY_TEXT_SP = 16f
        const val EMPTY_SIDE_DP = 24
    }
}
