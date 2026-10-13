/*
 * SPDX-FileCopyrightText: 2026 Stefan Probst-Schendzielorz
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.zen

import android.app.Application
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.atomic.AtomicBoolean
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.ZenChannelImportBinding
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.zen.classify.ZenChannelProfile
import org.schabi.newpipe.zen.classify.ZenClassWhy
import org.schabi.newpipe.zen.classify.ZenClassifierModel
import org.schabi.newpipe.zen.classify.ZenPlausibilityCheck
import org.schabi.newpipe.zen.classify.ZenRules

/**
 * "Take over channel": a shared or opened channel link (RouterActivity) first shows a small sheet
 * instead of the full channel page: name/avatar, subscription and practice status, and a rough
 * local classification of the newest videos (the same sample, throttling and cache as the
 * practice plausibility check). Actions: subscribe (at once, no lock: its videos are classified
 * and weighted in the feed anyway), mark as practice (the unchanged [ZenPraxisUi] flow with lock
 * and confirmation), just view (the normal channel page). The classification is only a
 * suggestion; nothing happens automatically.
 */
object ZenChannelImport {
    const val TAG = "ZenChannelImport"

    /** Link types that get the sheet: channels only (videos, playlists, shorts keep their ways). */
    @JvmStatic
    fun handles(linkType: LinkType?): Boolean = linkType == LinkType.CHANNEL

    /** Whether [url] is a channel link of a supported service (same detection as RouterActivity). */
    @JvmStatic
    fun isChannelLink(url: String?): Boolean = try {
        url != null && handles(NewPipe.getServiceByUrl(url).getLinkTypeByUrl(url))
    } catch (e: Exception) {
        false
    }

    /** Shows the sheet in [fm] (RouterActivity finishes when it is closed). */
    @JvmStatic
    fun show(fm: FragmentManager, serviceId: Int, url: String) {
        if (fm.findFragmentByTag(ZenChannelImportDialog.FRAGMENT_TAG) != null) return
        val dialog = ZenChannelImportDialog().apply {
            arguments = Bundle().apply {
                putInt(ARG_SERVICE, serviceId)
                putString(ARG_URL, url)
            }
        }
        // the link is resolved asynchronously: the activity may already have saved its state
        fm.beginTransaction().add(dialog, ZenChannelImportDialog.FRAGMENT_TAG).commitAllowingStateLoss()
    }

    internal const val ARG_SERVICE = "service_id"
    internal const val ARG_URL = "url"

    internal fun log(context: Context, type: String, detail: String) = Zen.log(context).add(type, detail)
}

/** State of the sheet; kept in [ZenChannelImportModel] so a rotation does not restart anything. */
data class ZenChannelImportState(
    val info: ChannelInfo? = null,
    val loadFailed: Boolean = false,
    val subscribed: Boolean? = null,
    val modelReady: Boolean = true,
    /** (done, total) while checking; (0, 0) while the video list loads. */
    val progress: Pair<Int, Int>? = null,
    val sample: ZenPlausibilityCheck.Sample? = null,
    val profile: ZenChannelProfile.Profile? = null,
    val checkFailed: Boolean = false,
    val stopped: Boolean = false
) {
    val checking get() = progress != null
}

/** Loads the channel and classifies its newest videos in the background; survives rotation. */
class ZenChannelImportModel(app: Application) : AndroidViewModel(app) {
    private val state = MutableLiveData(ZenChannelImportState())
    val live: LiveData<ZenChannelImportState> = state
    private val stop = AtomicBoolean(false)
    private var started = false
    private var serviceId = 0
    private var url = ""

    /** The last action chosen in the sheet (for the close log). */
    var action: String? = null

    // the newest state (postValue delivers later; updates must build on this one)
    private var latest = ZenChannelImportState()

    private fun update(f: (ZenChannelImportState) -> ZenChannelImportState) = synchronized(this) {
        latest = f(latest)
        state.postValue(latest)
    }

    private fun current() = synchronized(this) { latest }

    fun start(serviceId: Int, url: String) {
        if (started) return
        started = true
        this.serviceId = serviceId
        this.url = url
        val app = getApplication<Application>()
        ZenChannelImport.log(app, "channel_import", "action=open url=$url")
        Thread({ load(app) }, "ZenChannelImport").apply { isDaemon = true }.start()
    }

    private fun load(app: Application) {
        val info = try {
            ExtractorHelper.getChannelInfo(serviceId, url, false).blockingGet()
        } catch (e: Exception) {
            Log.w(ZenChannelImport.TAG, "channel failed: ${ZenPlausibilityCheck.rootName(e)}", e)
            ZenChannelImport.log(app, "channel_import", "action=load_failed url=$url error=${ZenPlausibilityCheck.rootName(e)}")
            update { it.copy(loadFailed = true) }
            return
        }
        val subscribed = try {
            SubscriptionManager(app).subscriptionTable().getSubscription(info.serviceId, info.url).blockingGet() != null
        } catch (e: Exception) {
            Log.w(ZenChannelImport.TAG, "subscription state", e)
            null
        }
        val ready = ZenClassifierModel.isReady(app)
        if (stop.get()) return
        update { it.copy(info = info, subscribed = subscribed, modelReady = ready, progress = if (ready) 0 to 0 else null) }
        if (!ready) {
            ZenChannelImport.log(app, "channel_import_check", "url=${info.url} result=no_model")
            return
        }
        val channel = ZenPraxisChannel(info.serviceId, info.url, info.name)
        val sample = try {
            ZenPlausibilityCheck.sample(app, channel, { stop.get() }) { done, total -> update { it.copy(progress = done to total) } }
        } catch (e: Exception) {
            Log.w(ZenChannelImport.TAG, "check failed", e)
            null
        }
        if (stop.get()) return
        if (sample == null || sample.checked.isEmpty()) {
            ZenChannelImport.log(app, "channel_import_check", "url=${info.url} result=failed")
            update { it.copy(progress = null, checkFailed = sample == null, sample = sample, profile = sample?.let { s -> profileOf(s, info) }) }
            return
        }
        val profile = profileOf(sample, info)
        ZenChannelImport.log(
            app,
            "channel_import_check",
            "url=${info.url} ${ZenChannelProfile.logDetail(profile)} ${ZenPlausibilityCheck.logRequests(sample)}"
        )
        update { it.copy(progress = null, sample = sample, profile = profile) }
    }

    private fun profileOf(sample: ZenPlausibilityCheck.Sample, info: ChannelInfo): ZenChannelProfile.Profile {
        val exempt = ZenClassExemptions(ZenSettings.lock()?.exemptions() ?: emptyList()).matches(info.serviceId, info.url, null)
        return ZenChannelProfile.evaluate(
            sample.checked.map { ZenChannelProfile.Item(it.title, it.entry?.cats, it.entry?.tone, it.entry?.level) },
            ZenSettings.classFeedWeighting(),
            exempt
        )
    }

    /** Stops a running classification (the practice flow then runs its own check, reusing the cache). */
    fun stopCheck() {
        if (current().checking) {
            stop.set(true)
            update { it.copy(progress = null, stopped = true) }
        }
    }

    fun subscribe(done: (Boolean) -> Unit) {
        val info = current().info ?: return
        val app = getApplication<Application>()
        Thread {
            val ok = try {
                SubscriptionManager(app).insertSubscription(SubscriptionEntity.from(info))
                true
            } catch (e: Exception) {
                Log.w(ZenChannelImport.TAG, "subscribe", e)
                false
            }
            if (ok) {
                action = "subscribe"
                ZenChannelImport.log(app, "channel_import", "action=subscribe url=${info.url}")
                update { it.copy(subscribed = true) }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(ok) }
        }.start()
    }

    override fun onCleared() {
        stop.set(true)
        ZenChannelImport.log(getApplication(), "channel_import", "action=close url=${current().info?.url ?: url} last=${action ?: "-"}")
    }
}

/** The sheet itself (a dialog, recreated by the FragmentManager on rotation). */
class ZenChannelImportDialog : AppCompatDialogFragment() {
    private var binding: ZenChannelImportBinding? = null
    private lateinit var model: ZenChannelImportModel
    private val serviceId get() = requireArguments().getInt(ZenChannelImport.ARG_SERVICE)
    private val url get() = requireArguments().getString(ZenChannelImport.ARG_URL).orEmpty()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = ContextThemeWrapper(
            requireContext(),
            if (ThemeHelper.isLightThemeSelected(requireContext())) R.style.LightTheme else R.style.DarkTheme
        )
        val b = ZenChannelImportBinding.inflate(LayoutInflater.from(ctx))
        binding = b
        model = ViewModelProvider(this)[ZenChannelImportModel::class.java]
        model.start(serviceId, url)
        b.zenImportSubscribe.setOnClickListener { onSubscribe() }
        b.zenImportPraxis.setOnClickListener { onPraxis() }
        b.zenImportView.setOnClickListener { onView() }
        model.live.observe(this) { render(it) }
        return AlertDialog.Builder(ctx)
            .setView(b.root)
            .setNegativeButton(R.string.zen_import_close, null)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun channelUrl() = model.live.value?.info?.url ?: url

    private fun channelName() = model.live.value?.info?.name ?: url

    private fun render(s: ZenChannelImportState) {
        val b = binding ?: return
        val ctx = b.root.context
        val info = s.info
        b.zenImportName.text = info?.name ?: if (s.loadFailed) url else ctx.getString(R.string.zen_import_loading)
        if (info != null) CoilHelper.loadAvatar(b.zenImportAvatar, info.avatars)

        // status line: subscription, practice, exception
        val status = ArrayList<String>()
        when (s.subscribed) {
            true -> status.add(ctx.getString(R.string.zen_import_subscribed))
            false -> status.add(ctx.getString(R.string.zen_import_not_subscribed))
            null -> {}
        }
        if (info != null) {
            when (ZenPraxis.state(ctx, info.serviceId, info.url)) {
                ZenPraxis.State.ACTIVE -> status.add(ctx.getString(R.string.zen_import_praxis_active))
                ZenPraxis.State.PENDING -> status.add(ctx.getString(R.string.zen_import_praxis_pending))
                ZenPraxis.State.NONE -> {}
            }
            val exempt = ZenClassExemptions(ZenSettings.lock()?.exemptions() ?: emptyList()).matches(info.serviceId, info.url, null)
            if (exempt) status.add(ctx.getString(R.string.zen_import_exempt))
        }
        b.zenImportStatus.text = status.joinToString(" · ")
        b.zenImportStatus.visibility = if (status.isEmpty()) View.GONE else View.VISIBLE

        val loading = !s.loadFailed && (info == null || s.checking)
        b.zenImportProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        b.zenImportProgress.visibility = if (s.checking) View.VISIBLE else View.GONE
        s.progress?.let { (done, total) ->
            b.zenImportProgress.text = if (total == 0) {
                ctx.getString(R.string.zen_check_loading_list)
            } else {
                ctx.getString(R.string.zen_check_progress, done, total)
            }
        }

        val p = s.profile
        b.zenImportSummary.text = when {
            s.loadFailed -> ctx.getString(R.string.zen_import_load_failed)
            info != null && !s.modelReady -> ctx.getString(R.string.zen_import_no_model)
            s.checkFailed -> ctx.getString(R.string.zen_import_check_failed)
            s.stopped -> ctx.getString(R.string.zen_import_stopped)
            p != null -> summary(ctx, p)
            else -> ""
        }
        b.zenImportSummary.visibility = if (b.zenImportSummary.text.isEmpty()) View.GONE else View.VISIBLE

        val details = ArrayList<String>()
        if (p != null && p.n > 0) {
            val line = listOfNotNull(
                if (p.news > 0) ctx.getString(R.string.zen_import_news, p.news, p.n) else null,
                if (p.heavy > 0) ctx.getString(R.string.zen_import_heavy, p.heavy, p.n) else null
            ).joinToString(" · ")
            if (line.isNotEmpty()) details.add(line)
            if (p.lowFeedWeight) details.add(ctx.getString(R.string.zen_import_low_feed))
        }
        b.zenImportDetails.text = details.joinToString("\n")
        b.zenImportDetails.visibility = if (details.isEmpty()) View.GONE else View.VISIBLE

        b.zenImportExamples.text = if (p != null && p.examples.isNotEmpty()) {
            ctx.getString(R.string.zen_import_examples) + "\n" + p.examples.joinToString("\n") { "• ${it.title} — ${label(ctx, it)}" }
        } else {
            ""
        }
        b.zenImportExamples.visibility = if (b.zenImportExamples.text.isEmpty()) View.GONE else View.VISIBLE

        val notes = ArrayList<String>()
        if (p != null && p.n > 0) notes.add(ctx.getString(R.string.zen_import_uncertain))
        s.sample?.let { ZenPlausibilityCheck.failedNote(ctx, it) }?.let { notes.add(it) }
        b.zenImportNote.text = notes.joinToString("\n")
        b.zenImportNote.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE

        // actions: subscribing only when not subscribed yet (and the channel is known)
        b.zenImportSubscribe.visibility = if (info != null && s.subscribed == false) View.VISIBLE else View.GONE
        b.zenImportPraxis.visibility = if (info != null) View.VISIBLE else View.GONE
        if (info != null) b.zenImportPraxis.text = ZenPraxisUi.menuTitle(ctx, info.serviceId, info.url)
    }

    private fun groupName(ctx: Context, g: ZenChannelProfile.Group): String = ZenClassWhy.catName(
        ctx,
        when (g) {
            ZenChannelProfile.Group.PRAXIS -> "praxis"
            ZenChannelProfile.Group.BILDUNG -> "bildung"
            ZenChannelProfile.Group.AKTUELLES -> "aktuelles"
            ZenChannelProfile.Group.UNTERHALTUNG -> "zerstreuung"
            ZenChannelProfile.Group.KOERPER -> "koerper"
            ZenChannelProfile.Group.PSYCHE -> "psyche"
        }
    )

    private fun summary(ctx: Context, p: ZenChannelProfile.Profile): String = when (p.kind) {
        ZenChannelProfile.Kind.EMPTY -> ctx.getString(R.string.zen_import_empty)

        ZenChannelProfile.Kind.UNCLEAR -> ctx.getString(R.string.zen_import_unclear, p.none, p.n)

        ZenChannelProfile.Kind.MOSTLY -> ctx.getString(R.string.zen_import_mostly, groupName(ctx, p.main!!), p.mainCount, p.n)

        ZenChannelProfile.Kind.MIXED -> ctx.getString(
            R.string.zen_import_mixed,
            p.counts.take(3).joinToString(", ") { "${groupName(ctx, it.first)} ${it.second}" },
            p.n
        )
    }

    private fun label(ctx: Context, i: ZenChannelProfile.Item): String {
        val cats = i.cats
        val main = if (cats.isNullOrEmpty()) {
            ctx.getString(R.string.zen_why_no_category)
        } else {
            cats.entries.sortedByDescending { it.value }.joinToString(", ") { ZenClassWhy.catName(ctx, it.key) }
        }
        return if (i.tone == ZenRules.TONE_HEAVY && !cats.isNullOrEmpty()) main + " · " + ctx.getString(R.string.zen_tone_belastend) else main
    }

    private fun onSubscribe() {
        val name = channelName()
        model.subscribe { ok ->
            val ctx = context ?: return@subscribe
            Toast.makeText(
                ctx.applicationContext,
                if (ok) ctx.getString(R.string.zen_import_subscribed_toast, name) else ctx.getString(R.string.zen_import_subscribe_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun onPraxis() {
        val activity = activity ?: return
        val s = model.live.value ?: return
        val info = s.info ?: return
        // a finished sample is reused (no second check); a running one is stopped, the practice
        // flow then checks itself and reuses what is already in the cache
        val sample = s.sample?.takeIf { !s.checking && it.checked.isNotEmpty() }
        // only when the practice flow will check itself (not when it just rejects because of the lock)
        val snap = Zen.settingsLock(activity).snapshot()
        val willCheck = ZenPraxis.state(activity, info.serviceId, info.url) == ZenPraxis.State.NONE &&
            (snap.setupMode || snap.lockRemainingMs <= 0)
        if (sample == null && willCheck) model.stopCheck()
        model.action = "praxis"
        ZenChannelImport.log(activity, "channel_import", "action=praxis url=${info.url} presampled=${sample != null}")
        ZenPraxisUi.onMenu(activity, info.serviceId, info.url, info.name, false, { binding?.let { render(model.live.value ?: s) } }, sample)
    }

    private fun onView() {
        val activity = activity ?: return
        val target = channelUrl()
        model.action = "view"
        ZenChannelImport.log(activity, "channel_import", "action=view url=$target")
        Thread {
            val intent = try {
                NavigationHelper.getIntentByLink(activity, target)
            } catch (e: Exception) {
                Log.w(ZenChannelImport.TAG, "view", e)
                null
            }
            activity.runOnUiThread {
                if (intent != null && !activity.isFinishing) activity.startActivity(intent)
                if (isAdded) dismissAllowingStateLoss()
            }
        }.start()
    }

    companion object {
        const val FRAGMENT_TAG = "zen_channel_import"
    }
}
