package org.schabi.newpipe.local.feed.item

import android.content.Context
import android.text.TextUtils
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.xwray.groupie.viewbinding.BindableItem
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.ListStreamItemBinding
import org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
import org.schabi.newpipe.extractor.stream.StreamType.AUDIO_STREAM
import org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
import org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_AUDIO_STREAM
import org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM
import org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.StreamTypeUtil
import org.schabi.newpipe.util.image.CoilHelper

data class StreamItem(
    val streamWithState: StreamWithState,
    var itemVersion: ItemVersion = ItemVersion.NORMAL
) : BindableItem<ListStreamItemBinding>() {
    companion object {
        const val UPDATE_RELATIVE_TIME = 1
    }

    private val stream: StreamEntity = streamWithState.stream
    private val stateProgressTime: Long? = streamWithState.stateProgressMillis

    /** ZenPipe: why this item is in the edition (length, group). */
    var zenPick: org.schabi.newpipe.zen.ZenFeedPlanner.Pick? = null

    /**
     * Will be executed at the end of the [StreamItem.bind] (with (ListStreamItemBinding,Int)).
     * Can be used e.g. for highlighting a item.
     */
    var execBindEnd: Consumer<ListStreamItemBinding>? = null

    override fun getId(): Long = stream.uid

    // ZenPipe: a changed "why" (details loaded later) must rebind the item
    override fun hasSameContentAs(other: com.xwray.groupie.Item<*>): Boolean = super.hasSameContentAs(other) && (other as? StreamItem)?.zenPick == zenPick

    enum class ItemVersion { NORMAL, MINI, GRID, CARD }

    override fun getLayout(): Int = when (itemVersion) {
        ItemVersion.NORMAL -> R.layout.list_stream_item
        ItemVersion.MINI -> R.layout.list_stream_mini_item
        ItemVersion.GRID -> R.layout.list_stream_grid_item
        ItemVersion.CARD -> R.layout.list_stream_card_item
    }

    override fun initializeViewBinding(view: View) = ListStreamItemBinding.bind(view)

    override fun bind(viewBinding: ListStreamItemBinding, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(UPDATE_RELATIVE_TIME)) {
            if (itemVersion != ItemVersion.MINI) {
                viewBinding.itemAdditionalDetails.text =
                    getStreamInfoDetailLine(viewBinding.itemAdditionalDetails.context)
            }
            return
        }

        super.bind(viewBinding, position, payloads)
    }

    override fun bind(viewBinding: ListStreamItemBinding, position: Int) {
        viewBinding.itemVideoTitleView.text = stream.title
        viewBinding.itemUploaderView.text = stream.uploader

        if (stream.duration > 0) {
            viewBinding.itemDurationView.text = Localization.getDurationString(stream.duration)
            viewBinding.itemDurationView.setBackgroundColor(
                ContextCompat.getColor(
                    viewBinding.itemDurationView.context,
                    R.color.duration_background_color
                )
            )
            viewBinding.itemDurationView.visibility = View.VISIBLE

            if (stateProgressTime != null) {
                viewBinding.itemProgressView.visibility = View.VISIBLE
                viewBinding.itemProgressView.max = stream.duration.toInt()
                viewBinding.itemProgressView.progress = TimeUnit.MILLISECONDS.toSeconds(stateProgressTime).toInt()
            } else {
                viewBinding.itemProgressView.visibility = View.GONE
            }
        } else if (StreamTypeUtil.isLiveStream(stream.streamType)) {
            viewBinding.itemDurationView.setText(R.string.duration_live)
            viewBinding.itemDurationView.setBackgroundColor(
                ContextCompat.getColor(
                    viewBinding.itemDurationView.context,
                    R.color.live_duration_background_color
                )
            )
            viewBinding.itemDurationView.visibility = View.VISIBLE
            viewBinding.itemProgressView.visibility = View.GONE
        } else {
            viewBinding.itemDurationView.visibility = View.GONE
            viewBinding.itemProgressView.visibility = View.GONE
        }

        CoilHelper.loadThumbnail(viewBinding.itemThumbnailView, stream.thumbnailUrl)

        if (itemVersion != ItemVersion.MINI) {
            viewBinding.itemAdditionalDetails.text =
                getStreamInfoDetailLine(viewBinding.itemAdditionalDetails.context)
            // zen: "why" of the content recognition below the details, may wrap once (strongest
            // topic without a category); views are recycled: always reset
            if (zenWhy(viewBinding.itemAdditionalDetails.context) != null) {
                viewBinding.itemAdditionalDetails.minLines = 2
                viewBinding.itemAdditionalDetails.maxLines = 3
            } else {
                viewBinding.itemAdditionalDetails.setLines(1)
            }
        }

        execBindEnd?.accept(viewBinding)
    }

    override fun isLongClickable() = when (stream.streamType) {
        AUDIO_STREAM, VIDEO_STREAM, LIVE_STREAM, AUDIO_LIVE_STREAM, POST_LIVE_STREAM, POST_LIVE_AUDIO_STREAM -> true
        else -> false
    }

    private fun getStreamInfoDetailLine(context: Context): String {
        val detail = getStreamInfoDetailLineBase(context)
        val pick = zenPick ?: return detail
        val reason = if (pick.groupName != null) {
            context.getString(R.string.zen_reason_group, pick.minutes, pick.groupName)
        } else {
            context.getString(R.string.zen_reason_minutes, pick.minutes)
        }
        val line = if (detail.isEmpty()) reason else Localization.concatenateStrings(reason, detail)
        return zenWhy(context)?.let { "$line\n$it" } ?: line
    }

    private fun zenWhy(context: Context): String? = zenPick?.why?.let { org.schabi.newpipe.zen.classify.ZenClassWhy.feedLine(context, it) }

    private fun getStreamInfoDetailLineBase(context: Context): String {
        var viewsAndDate = ""
        val viewCount = stream.viewCount
        // zen: no view counts with reduced video details (as on the detail page)
        if (viewCount != null && viewCount >= 0 && !org.schabi.newpipe.zen.ZenSettings.reducedVideoDetails()) {
            viewsAndDate = when (stream.streamType) {
                AUDIO_LIVE_STREAM -> Localization.listeningCount(context, viewCount)
                LIVE_STREAM -> Localization.shortWatchingCount(context, viewCount)
                else -> Localization.shortViewCount(context, viewCount)
            }
        }
        val uploadDate = getFormattedRelativeUploadDate(context)
        return when {
            !TextUtils.isEmpty(uploadDate) -> when {
                viewsAndDate.isEmpty() -> uploadDate!!
                else -> Localization.concatenateStrings(viewsAndDate, uploadDate)
            }

            else -> viewsAndDate
        }
    }

    private fun getFormattedRelativeUploadDate(context: Context): String? {
        val uploadDate = stream.uploadDate
        return if (uploadDate != null) {
            var formattedRelativeTime = Localization.relativeTime(uploadDate)

            if (MainActivity.DEBUG) {
                val key = context.getString(R.string.show_original_time_ago_key)
                if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, false)) {
                    formattedRelativeTime += " (" + stream.textualUploadDate + ")"
                }
            }

            formattedRelativeTime
        } else {
            stream.textualUploadDate
        }
    }

    override fun getSpanSize(spanCount: Int, position: Int): Int {
        return if (itemVersion == ItemVersion.GRID) 1 else spanCount
    }
}
