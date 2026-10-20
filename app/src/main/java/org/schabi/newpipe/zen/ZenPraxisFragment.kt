package org.schabi.newpipe.zen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
 *
 * Practice themes (D61): chips "All · Fitness · Yoga · Sleep · Meditation · Breathwork · More".
 * "All" is the list above; a theme shows [ZenPraxisSelection.PER_THEME] videos of that theme (at most
 * one popular one), a video with several themes appears under each. Themes come from
 * [ZenPraxisThemeData]; videos the classifier has not seen yet are classified in the background
 * (title + channel, no network) and the view is updated. Only grouping, no rule changes.
 */
class ZenPraxisFragment : BaseListFragment<ZenPraxisFragment.Loaded, Void>() {
    data class Loaded(
        val items: List<InfoItem>,
        val channels: Int,
        val failed: Int,
        val entries: List<Entry> = emptyList(),
        val themes: Map<String, ZenPraxisThemes.Resolved> = emptyMap()
    )

    data class ChannelItems(val items: List<StreamInfoItem>, val ok: Boolean, val head: InfoItem? = null, val source: ZenPraxisChannel? = null)

    /** One practice video of the tab with the default themes of the entry it was loaded from. */
    data class Entry(val item: StreamInfoItem, val key: String, val group: String, val defaultMask: Int)

    private var disposable: Disposable? = null
    private var refine: Disposable? = null
    private var loadedSignature: String? = null
    private var header: TextView? = null
    private var themeNote: TextView? = null
    private var loaded: Loaded? = null

    /** Selected chip: [CHIP_ALL], an index of [ZenPraxisTheme.entries], or [CHIP_MORE]. */
    private var chip = CHIP_ALL

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
        refine?.dispose()
        super.onDestroy()
    }

    override fun getListHeaderSupplier(): Supplier<View> = Supplier {
        val ctx = requireContext()
        val p = (12 * resources.displayMetrics.density).toInt()
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(ctx).apply {
                    setPadding(p, p, p, p / 2)
                    header = this
                    updateHeader()
                }
            )
            addView(chipRow(ctx, p))
            addView(
                TextView(ctx).apply {
                    setPadding(p, p / 2, p, p / 2)
                    visibility = View.GONE
                    themeNote = this
                }
            )
            updateThemeNote()
        }
    }

    /** "All · Fitness · Yoga · Sleep · Meditation · Breathwork · More" (Material chips need a Material theme). */
    private fun chipRow(ctx: android.content.Context, pad: Int): View {
        val themed = android.view.ContextThemeWrapper(ctx, com.google.android.material.R.style.Theme_MaterialComponents_DayNight_Bridge)
        val group = ChipGroup(themed).apply {
            isSingleLine = true
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(pad, 0, pad, 0)
        }
        val labels = listOf(CHIP_ALL to R.string.zen_praxis_chip_all) +
            ZenPraxisTheme.entries.mapIndexed { i, t -> i to themeLabel(t) } +
            listOf(CHIP_MORE to R.string.zen_praxis_chip_more)
        for ((id, label) in labels) {
            val c = LayoutInflater.from(themed).inflate(R.layout.chip, group, false) as Chip
            c.id = View.generateViewId()
            c.setText(label)
            c.isCheckable = true
            c.isCheckedIconVisible = false
            c.isChecked = id == chip
            c.setOnCheckedChangeListener { _, checked ->
                if (checked && chip != id) {
                    chip = id
                    showSelection()
                }
            }
            group.addView(c)
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(group)
        }
    }

    private fun signature(): String = ZenPraxis.activeKeys().sorted().joinToString("|")

    private fun selectedTheme(): ZenPraxisTheme? = ZenPraxisTheme.entries.getOrNull(chip)

    private fun updateThemeNote() {
        val note = themeNote ?: return
        val l = loaded
        val empty = chip != CHIP_ALL && l != null && l.items.isNotEmpty() && visibleItems(l).isEmpty()
        note.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) note.setText(R.string.zen_praxis_theme_empty)
    }

    /** The items of the selected chip: "All" = the whole tab, a theme or "More" = [ZenPraxisSelection]. */
    private fun visibleItems(l: Loaded): List<InfoItem> {
        if (chip == CHIP_ALL) return l.items
        val theme = selectedTheme()
        val cands = l.entries.map { e ->
            ZenPraxisSelection.Candidate(e.key, l.themes[e.key]?.mask ?: 0, e.group, e.item.viewCount)
        }
        val keys = ZenPraxisSelection.select(cands, theme)
        val byKey = l.entries.associateBy { it.key }
        return keys.mapNotNull { byKey[it]?.item }
    }

    private fun showSelection() {
        val l = loaded ?: return
        infoListAdapter.clearStreamItemList()
        infoListAdapter.addInfoItemList(visibleItems(l))
        updateThemeNote()
    }

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
                val channelLists = perEntry.filter { it.head == null }
                val channelItems = channelLists.flatMap { it.items }
                    .sortedByDescending { it.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE }
                val playlistLists = perEntry.filter { it.head != null }
                val playlistItems = playlistLists.flatMap { listOf(it.head!!) + it.items }
                // the theme view: channel videos newest first, then the playlists' videos in order
                val source = HashMap<StreamInfoItem, ZenPraxisChannel?>()
                perEntry.forEach { ce -> ce.items.forEach { source[it] = ce.source } }
                val entries = (channelItems + playlistLists.flatMap { it.items }).map { it.toEntry(source[it]) }
                    .let { list -> mergeDefaults(list) }
                Loaded(channelItems + playlistItems, all.size, perEntry.count { !it.ok }, entries, ZenPraxisThemeData.resolveAll(ctx, themeVideos(entries)))
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
                    ChannelItems(items, true, source = ch)
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
            ChannelItems(items, true, head, pl)
        }
        .onErrorReturnItem(ChannelItems(emptyList(), false, null))

    override fun handleResult(result: Loaded) {
        super.handleResult(result)
        infoListAdapter.clearStreamItemList()
        loaded = result
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
        infoListAdapter.addInfoItemList(visibleItems(result))
        updateThemeNote()
        showListFooter(false)
        refineThemes(result)
        if (result.failed > 0) {
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.zen_praxis_failed, result.failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Videos the classifier has not seen (or saw before the themes were stored) are classified now
     * in the background, title + channel only, then the themes are resolved again. Without a
     * working model nothing happens (title rules, corrections and defaults already apply).
     */
    private fun refineThemes(result: Loaded) {
        refine?.dispose()
        val ctx = requireContext().applicationContext
        if (!org.schabi.newpipe.zen.classify.ZenClassifierModel.isReady(ctx) || result.entries.isEmpty()) return
        refine = Single.fromCallable {
            val cache = org.schabi.newpipe.zen.classify.ZenClassCache.get(ctx)
            val classifier = org.schabi.newpipe.zen.classify.ZenClassifier.get(ctx)
            val model = org.schabi.newpipe.zen.classify.ZenClassifierModel.VERSION
            val started = System.currentTimeMillis()
            var done = 0
            for (e in result.entries.distinctBy { it.key }) {
                if (System.currentTimeMillis() - started > REFINE_BUDGET_MS) break
                if (!cache.needsWork(cache.get(e.key), "T", model, wantPraxis = true)) continue
                classifier.classifyForPraxis(e.key, org.schabi.newpipe.zen.classify.VideoText(e.item.name, e.item.uploaderName), REFINE_ITEM_MS)
                done++
            }
            if (done == 0) result.themes else ZenPraxisThemeData.resolveAll(ctx, themeVideos(result.entries))
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { themes ->
                    if (loaded === result && themes != result.themes) {
                        loaded = result.copy(themes = themes)
                        if (chip != CHIP_ALL) showSelection()
                    }
                },
                { android.util.Log.w("ZenPraxis", "theme refine", it) }
            )
    }

    override fun loadMoreItems() {
        // finite list: no paging
    }

    override fun hasMoreItems(): Boolean = false

    private companion object {
        const val CHIP_ALL = -1
        val CHIP_MORE = ZenPraxisTheme.entries.size

        /** Background classification of the tab's unseen videos: at most this long in total / per video. */
        const val REFINE_BUDGET_MS = 60_000L
        const val REFINE_ITEM_MS = 10_000L

        @JvmStatic
        fun themeLabel(t: ZenPraxisTheme): Int = when (t) {
            ZenPraxisTheme.FITNESS -> R.string.zen_praxis_theme_fitness
            ZenPraxisTheme.YOGA -> R.string.zen_praxis_theme_yoga
            ZenPraxisTheme.SLEEP -> R.string.zen_praxis_theme_sleep
            ZenPraxisTheme.MEDITATION -> R.string.zen_praxis_theme_meditation
            ZenPraxisTheme.BREATH -> R.string.zen_praxis_theme_breath
        }

        fun StreamInfoItem.toEntry(source: ZenPraxisChannel?) = Entry(
            this,
            org.schabi.newpipe.zen.classify.ZenClassCache.keyOf(serviceId, url),
            uploaderUrl?.takeIf { it.isNotEmpty() }?.let { ZenPraxisChannel.keyOf(serviceId, it) } ?: source?.key ?: "",
            source?.themes ?: 0
        )

        /** A video loaded from several entries (channel and playlist): one entry, defaults united (at most 3). */
        fun mergeDefaults(list: List<Entry>): List<Entry> {
            val union = HashMap<String, Int>()
            list.forEach { union[it.key] = (union[it.key] ?: 0) or it.defaultMask }
            return list.distinctBy { it.key }.map { e ->
                e.copy(defaultMask = ZenPraxisThemes.mask(ZenPraxisThemes.themes(union.getValue(e.key)).take(ZenPraxisThemes.MAX)))
            }
        }

        fun themeVideos(entries: List<Entry>) = entries.map { ZenPraxisThemeData.Video(it.key, it.item.name, it.defaultMask) }

        /** Marker head of a pending playlist that was only loaded to register its content. */
        val HIDDEN: InfoItem = PlaylistInfoItem(-1, "", "")

        /** Empty-state text like the other empty pages (`list_empty_view`: default size, 16 dp sides). */
        const val EMPTY_TEXT_SP = 16f
        const val EMPTY_SIDE_DP = 24
    }
}
