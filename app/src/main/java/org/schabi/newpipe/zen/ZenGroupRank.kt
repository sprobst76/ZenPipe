package org.schabi.newpipe.zen

/** Row of the feed-group priority query ([org.schabi.newpipe.database.feed.dao.FeedGroupDAO]). */
data class ZenGroupRank(val streamId: Long, val groupName: String, val sortOrder: Long)
