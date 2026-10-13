package org.schabi.newpipe.local.feed.item

import android.text.format.DateFormat
import android.view.View
import com.xwray.groupie.viewbinding.BindableItem
import java.util.Date
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ZenFeedEndCardBinding
import org.schabi.newpipe.zen.ZenFeedProvider

/** ZenPipe: closing card of a feed edition ("You're done.") or the note that the next one is being prepared. */
class ZenEndCardItem(private val end: ZenFeedProvider.End) : BindableItem<ZenFeedEndCardBinding>() {
    override fun getId(): Long = Long.MIN_VALUE

    override fun getLayout() = R.layout.zen_feed_end_card

    override fun initializeViewBinding(view: View) = ZenFeedEndCardBinding.bind(view)

    override fun getSpanSize(spanCount: Int, position: Int) = spanCount

    override fun bind(viewBinding: ZenFeedEndCardBinding, position: Int) {
        val context = viewBinding.root.context
        end.preparing?.let { (done, total) ->
            viewBinding.zenEndTitle.setText(R.string.zen_feed_preparing)
            viewBinding.zenEndNext.text = context.getString(R.string.zen_feed_preparing_progress, done, total)
            viewBinding.zenEndLeftover.visibility = View.GONE
            return
        }
        val time = DateFormat.getTimeFormat(context).format(Date(end.nextEditionMillis))
        viewBinding.zenEndTitle.setText(R.string.zen_feed_done)
        viewBinding.zenEndNext.text = context.getString(
            if (end.dailyCapReached) R.string.zen_feed_next_tomorrow else R.string.zen_feed_next,
            time
        )
        viewBinding.zenEndLeftover.visibility = if (end.leftover > 0) View.VISIBLE else View.GONE
        viewBinding.zenEndLeftover.text =
            context.resources.getQuantityString(R.plurals.zen_feed_leftover, end.leftover, end.leftover)
    }
}
